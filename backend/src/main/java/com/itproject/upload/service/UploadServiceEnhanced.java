package com.itproject.upload.service;

import com.itproject.upload.dto.ChunkUploadRequest;
import com.itproject.upload.dto.UploadResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Enhanced upload service with retry mechanism and pause/resume support
 */
@Slf4j
@Service
public class UploadServiceEnhanced {
    
    @Autowired
    private UploadService uploadService;
    
    @Autowired
    private ChunkBitmapService chunkBitmapService;
    
    @Autowired
    private UploadStatusService uploadStatusService;
    
    private final ScheduledExecutorService retryExecutor = Executors.newScheduledThreadPool(5);
    
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final long RETRY_DELAY_SECONDS = 2;
    
    /**
     * Upload chunk with automatic retry mechanism
     */
    public UploadResponse uploadChunkWithRetry(ChunkUploadRequest request, MultipartFile file) {
        return uploadChunkWithRetry(request, file, 0);
    }
    
    private UploadResponse uploadChunkWithRetry(ChunkUploadRequest request, MultipartFile file, int attemptCount) {
        String fileMd5 = request.getFileMd5();
        int chunkIndex = request.getChunkIndex();
        
        // Check if upload is paused
        if (uploadStatusService.isUploadPaused(fileMd5)) {
            log.info("Upload is paused for file: {}, chunk: {}", fileMd5, chunkIndex);
            return UploadResponse.error("Upload is paused");
        }
        
        // Check if chunk already uploaded using bitmap
        if (chunkBitmapService.isChunkUploaded(fileMd5, chunkIndex)) {
            log.debug("Chunk {} already uploaded for file: {}", chunkIndex, fileMd5);
            return uploadService.getUploadProgress(fileMd5);
        }
        
        try {
            // Attempt upload
            UploadResponse response = uploadService.uploadChunk(request, file);
            
            if (response.isSuccess()) {
                // Update bitmap on successful upload
                chunkBitmapService.markChunkUploaded(fileMd5, chunkIndex);
                log.info("Successfully uploaded chunk {} for file: {} on attempt {}", 
                        chunkIndex, fileMd5, attemptCount + 1);
                return response;
            } else {
                // Handle upload failure
                return handleUploadFailure(request, file, attemptCount, response.getMessage());
            }
            
        } catch (Exception e) {
            log.error("Exception during chunk upload for file: {}, chunk: {}, attempt: {}", 
                    fileMd5, chunkIndex, attemptCount + 1, e);
            return handleUploadFailure(request, file, attemptCount, e.getMessage());
        }
    }
    
    private UploadResponse handleUploadFailure(ChunkUploadRequest request, MultipartFile file, 
                                             int attemptCount, String errorMessage) {
        String fileMd5 = request.getFileMd5();
        int chunkIndex = request.getChunkIndex();
        
        if (attemptCount < MAX_RETRY_ATTEMPTS - 1) {
            // Schedule retry
            long delay = RETRY_DELAY_SECONDS * (attemptCount + 1); // Exponential backoff
            
            log.warn("Chunk upload failed for file: {}, chunk: {}, attempt: {}. Retrying in {} seconds. Error: {}", 
                    fileMd5, chunkIndex, attemptCount + 1, delay, errorMessage);
            
            retryExecutor.schedule(() -> {
                uploadChunkWithRetry(request, file, attemptCount + 1);
            }, delay, TimeUnit.SECONDS);
            
            return UploadResponse.error("Upload failed, retrying... (attempt " + (attemptCount + 1) + "/" + MAX_RETRY_ATTEMPTS + ")");
            
        } else {
            // Max retries reached
            log.error("Max retry attempts reached for file: {}, chunk: {}. Final error: {}", 
                    fileMd5, chunkIndex, errorMessage);
            
            // Mark chunk as failed
            uploadStatusService.markChunkFailed(fileMd5, chunkIndex);
            
            return UploadResponse.error("Upload failed after " + MAX_RETRY_ATTEMPTS + " attempts: " + errorMessage);
        }
    }
    
    /**
     * Get enhanced upload progress with bitmap data
     */
    public UploadResponse getEnhancedUploadProgress(String fileMd5) {
        try {
            UploadResponse baseResponse = uploadService.getUploadProgress(fileMd5);
            
            if (baseResponse.isSuccess() && baseResponse.getTotalChunks() != null) {
                // Enhance with bitmap data
                int totalChunks = baseResponse.getTotalChunks();
                long uploadedCount = chunkBitmapService.getUploadedChunkCount(fileMd5);
                double progress = chunkBitmapService.getUploadProgress(fileMd5, totalChunks);
                
                baseResponse.setUploadedChunks((int) uploadedCount);
                baseResponse.setUploadProgress(progress);
                baseResponse.setMissingChunks(chunkBitmapService.getMissingChunks(fileMd5, totalChunks));
                
                // Add upload status
                String status = uploadStatusService.getUploadStatus(fileMd5);
                baseResponse.setUploadStatus(status);
            }
            
            return baseResponse;
            
        } catch (Exception e) {
            log.error("Error getting enhanced upload progress for file: {}", fileMd5, e);
            return UploadResponse.error("Failed to get progress: " + e.getMessage());
        }
    }
    
    /**
     * Pause upload
     */
    public UploadResponse pauseUpload(String fileMd5) {
        try {
            uploadStatusService.pauseUpload(fileMd5);
            log.info("Paused upload for file: {}", fileMd5);
            return UploadResponse.success("Upload paused");
        } catch (Exception e) {
            log.error("Error pausing upload for file: {}", fileMd5, e);
            return UploadResponse.error("Failed to pause upload: " + e.getMessage());
        }
    }
    
    /**
     * Resume upload
     */
    public UploadResponse resumeUpload(String fileMd5) {
        try {
            uploadStatusService.resumeUpload(fileMd5);
            log.info("Resumed upload for file: {}", fileMd5);
            return UploadResponse.success("Upload resumed");
        } catch (Exception e) {
            log.error("Error resuming upload for file: {}", fileMd5, e);
            return UploadResponse.error("Failed to resume upload: " + e.getMessage());
        }
    }
    
    /**
     * Cancel upload
     */
    public UploadResponse cancelUpload(String fileMd5) {
        try {
            uploadStatusService.cancelUpload(fileMd5);
            chunkBitmapService.clearChunkBitmap(fileMd5);
            log.info("Cancelled upload for file: {}", fileMd5);
            return UploadResponse.success("Upload cancelled");
        } catch (Exception e) {
            log.error("Error cancelling upload for file: {}", fileMd5, e);
            return UploadResponse.error("Failed to cancel upload: " + e.getMessage());
        }
    }
    
    /**
     * Retry failed chunks
     */
    public UploadResponse retryFailedChunks(String fileMd5) {
        try {
            // Get failed chunks and reset their status
            uploadStatusService.resetFailedChunks(fileMd5);
            log.info("Reset failed chunks for retry: {}", fileMd5);
            return UploadResponse.success("Failed chunks reset for retry");
        } catch (Exception e) {
            log.error("Error retrying failed chunks for file: {}", fileMd5, e);
            return UploadResponse.error("Failed to retry: " + e.getMessage());
        }
    }
    
    public void shutdown() {
        retryExecutor.shutdown();
        try {
            if (!retryExecutor.awaitTermination(60, TimeUnit.SECONDS)) {
                retryExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            retryExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
