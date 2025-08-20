package com.itproject.upload.controller;

import com.itproject.upload.dto.ChunkUploadRequest;
import com.itproject.upload.dto.UploadResponse;
import com.itproject.upload.service.UploadServiceEnhanced;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * Enhanced upload controller with retry and pause/resume support
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/upload-enhanced")
@CrossOrigin(origins = "*", maxAge = 3600)
public class UploadEnhancedController {
    
    @Autowired
    private UploadServiceEnhanced uploadServiceEnhanced;
    
    /**
     * Upload file chunk with automatic retry
     */
    @PostMapping("/chunk")
    public ResponseEntity<UploadResponse> uploadChunkWithRetry(
            @RequestParam("fileMd5") String fileMd5,
            @RequestParam("fileName") String fileName,
            @RequestParam("chunkIndex") Integer chunkIndex,
            @RequestParam("totalChunks") Integer totalChunks,
            @RequestParam("totalSize") Long totalSize,
            @RequestParam("projectId") Long projectId,
            @RequestParam(value = "chunkMd5", required = false) String chunkMd5,
            @RequestParam(value = "uploadedBy", required = false, defaultValue = "anonymous") String uploadedBy,
            @RequestParam("file") MultipartFile file) {
        
        try {
            log.info("Received enhanced chunk upload request: file={}, chunk={}/{}, size={}", 
                    fileName, chunkIndex + 1, totalChunks, file.getSize());
            
            ChunkUploadRequest request = new ChunkUploadRequest();
            request.setFileMd5(fileMd5);
            request.setFileName(fileName);
            request.setChunkIndex(chunkIndex);
            request.setTotalChunks(totalChunks);
            request.setTotalSize(totalSize);
            request.setChunkSize(file.getSize());
            request.setChunkMd5(chunkMd5);
            request.setUploadedBy(uploadedBy);
            request.setProjectId(projectId);
            
            UploadResponse response = uploadServiceEnhanced.uploadChunkWithRetry(request, file);
            
            if (response.isSuccess()) {
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
            }
            
        } catch (Exception e) {
            log.error("Error in enhanced chunk upload", e);
            UploadResponse errorResponse = UploadResponse.error("Upload failed: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }
    
    /**
     * Get enhanced upload progress with bitmap data
     */
    @GetMapping("/progress/{fileMd5}")
    public ResponseEntity<UploadResponse> getEnhancedProgress(@PathVariable String fileMd5) {
        try {
            log.debug("Getting enhanced upload progress for file: {}", fileMd5);
            UploadResponse response = uploadServiceEnhanced.getEnhancedUploadProgress(fileMd5);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error getting enhanced upload progress", e);
            UploadResponse errorResponse = UploadResponse.error("Failed to get progress: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }
    
    /**
     * Pause upload
     */
    @PostMapping("/pause/{fileMd5}")
    public ResponseEntity<UploadResponse> pauseUpload(@PathVariable String fileMd5) {
        try {
            log.info("Pausing upload for file: {}", fileMd5);
            UploadResponse response = uploadServiceEnhanced.pauseUpload(fileMd5);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error pausing upload", e);
            UploadResponse errorResponse = UploadResponse.error("Failed to pause upload: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }
    
    /**
     * Resume upload
     */
    @PostMapping("/resume/{fileMd5}")
    public ResponseEntity<UploadResponse> resumeUpload(@PathVariable String fileMd5) {
        try {
            log.info("Resuming upload for file: {}", fileMd5);
            UploadResponse response = uploadServiceEnhanced.resumeUpload(fileMd5);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error resuming upload", e);
            UploadResponse errorResponse = UploadResponse.error("Failed to resume upload: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }
    
    /**
     * Cancel upload
     */
    @PostMapping("/cancel/{fileMd5}")
    public ResponseEntity<UploadResponse> cancelUpload(@PathVariable String fileMd5) {
        try {
            log.info("Cancelling upload for file: {}", fileMd5);
            UploadResponse response = uploadServiceEnhanced.cancelUpload(fileMd5);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error cancelling upload", e);
            UploadResponse errorResponse = UploadResponse.error("Failed to cancel upload: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }
    
    /**
     * Retry failed chunks
     */
    @PostMapping("/retry/{fileMd5}")
    public ResponseEntity<UploadResponse> retryFailedChunks(@PathVariable String fileMd5) {
        try {
            log.info("Retrying failed chunks for file: {}", fileMd5);
            UploadResponse response = uploadServiceEnhanced.retryFailedChunks(fileMd5);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error retrying failed chunks", e);
            UploadResponse errorResponse = UploadResponse.error("Failed to retry: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }
}
