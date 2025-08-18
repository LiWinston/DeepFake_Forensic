package com.itproject.upload.service;

import com.itproject.upload.dto.ChunkUploadRequest;
import com.itproject.upload.dto.UploadResponse;
import com.itproject.upload.entity.ChunkInfo;
import com.itproject.upload.entity.MediaFile;
import com.itproject.upload.repository.ChunkInfoRepository;
import com.itproject.upload.repository.MediaFileRepository;
import io.minio.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Service for handling file uploads with chunked upload support
 */
@Slf4j
@Service
public class UploadService {
    
    @Autowired
    private MediaFileRepository mediaFileRepository;
    
    @Autowired
    private ChunkInfoRepository chunkInfoRepository;
    
    @Autowired
    private MinioClient minioClient;
    
    @Autowired
    private String minioBucketName;
    
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    
    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;
    
    @Autowired
    private String metadataAnalysisTopic;
    
    @Autowired
    private FileTypeValidationService fileTypeValidationService;
    
    private static final String CHUNK_CACHE_PREFIX = "chunk:";
    private static final String UPLOAD_PROGRESS_PREFIX = "progress:";
    private static final long CACHE_EXPIRY_HOURS = 24;
    
    /**
     * Upload file chunk
     */
    @Transactional
    public UploadResponse uploadChunk(ChunkUploadRequest request, MultipartFile file) {
        try {
            log.info("Uploading chunk {} of {} for file: {} (MD5: {})", 
                    request.getChunkIndex(), request.getTotalChunks(), 
                    request.getFileName(), request.getFileMd5());
            
            // Validate chunk data
            if (!validateChunk(request, file)) {
                return UploadResponse.error("Invalid chunk data");
            }
            
            // Ensure bucket exists
            ensureBucketExists();
            
            // Check if file record exists, create if not
            MediaFile mediaFile = getOrCreateMediaFile(request);
            
            // Check if chunk already uploaded
            Optional<ChunkInfo> existingChunk = chunkInfoRepository
                .findByFileMd5AndChunkIndex(request.getFileMd5(), request.getChunkIndex());
            
            if (existingChunk.isPresent()) {
                log.debug("Chunk {} already exists for file: {}", request.getChunkIndex(), request.getFileMd5());
                return buildUploadResponse(request.getFileMd5(), "Chunk already uploaded");
            }
            
            // Upload chunk to MinIO
            String chunkPath = uploadChunkToStorage(request, file);
            
            // Save chunk info to database
            ChunkInfo chunkInfo = new ChunkInfo();
            chunkInfo.setFileMd5(request.getFileMd5());
            chunkInfo.setChunkIndex(request.getChunkIndex());
            chunkInfo.setChunkSize(file.getSize());
            chunkInfo.setChunkMd5(request.getChunkMd5());
            chunkInfo.setChunkPath(chunkPath);
            chunkInfo.setStatus(ChunkInfo.ChunkStatus.UPLOADED);
            chunkInfoRepository.save(chunkInfo);
            
            // Update cache
            updateChunkCache(request.getFileMd5(), request.getChunkIndex());
            
            // Update media file progress
            updateUploadProgress(mediaFile, request.getTotalChunks());
            
            // Check if upload is complete
            if (isUploadComplete(request.getFileMd5(), request.getTotalChunks())) {
                return completeUpload(mediaFile);
            }
            
            return buildUploadResponse(request.getFileMd5(), "Chunk uploaded successfully");
            
        } catch (Exception e) {
            log.error("Error uploading chunk {} for file: {}", request.getChunkIndex(), request.getFileMd5(), e);
            return UploadResponse.error("Failed to upload chunk: " + e.getMessage());
        }
    }
    
    /**
     * Get upload progress for a file
     */
    public UploadResponse getUploadProgress(String fileMd5) {
        try {
            Optional<MediaFile> mediaFileOpt = mediaFileRepository.findByFileMd5(fileMd5);
            if (mediaFileOpt.isEmpty()) {
                return UploadResponse.error("File not found");
            }
            
            return buildUploadResponse(fileMd5, "Upload progress retrieved");
            
        } catch (Exception e) {
            log.error("Error getting upload progress for file: {}", fileMd5, e);
            return UploadResponse.error("Failed to get progress: " + e.getMessage());
        }
    }
    
    private boolean validateChunk(ChunkUploadRequest request, MultipartFile file) {
        // Validate required fields
        if (request.getFileMd5() == null || request.getFileName() == null || 
            request.getChunkIndex() == null || request.getTotalChunks() == null) {
            log.warn("Missing required fields in chunk upload request");
            return false;
        }
        
        // Validate chunk index
        if (request.getChunkIndex() < 0 || request.getChunkIndex() >= request.getTotalChunks()) {
            log.warn("Invalid chunk index: {} for total chunks: {}", request.getChunkIndex(), request.getTotalChunks());
            return false;
        }
        
        // Validate file content
        if (file.isEmpty()) {
            log.warn("Empty file chunk received");
            return false;
        }
        
        // Validate chunk MD5 if provided
        if (request.getChunkMd5() != null) {
            try {
                String actualMd5 = DigestUtils.md5Hex(file.getInputStream());
                if (!request.getChunkMd5().equals(actualMd5)) {
                    log.warn("Chunk MD5 mismatch. Expected: {}, Actual: {}", request.getChunkMd5(), actualMd5);
                    return false;
                }
            } catch (IOException e) {
                log.error("Error validating chunk MD5", e);
                return false;
            }
        }
        
        return true;
    }
    
    private void ensureBucketExists() throws Exception {
        boolean bucketExists = minioClient.bucketExists(BucketExistsArgs.builder()
                .bucket(minioBucketName)
                .build());
        
        if (!bucketExists) {
            minioClient.makeBucket(MakeBucketArgs.builder()
                    .bucket(minioBucketName)
                    .build());
            log.info("Created MinIO bucket: {}", minioBucketName);
        }
    }
    
    private MediaFile getOrCreateMediaFile(ChunkUploadRequest request) {
        Optional<MediaFile> existingFile = mediaFileRepository.findByFileMd5(request.getFileMd5());
        
        if (existingFile.isPresent()) {
            return existingFile.get();
        }
        
        // Create new media file record
        MediaFile mediaFile = new MediaFile();
        mediaFile.setFileMd5(request.getFileMd5());
        mediaFile.setFileName(generateUniqueFileName(request.getFileName()));
        mediaFile.setOriginalFileName(request.getFileName());
        mediaFile.setFileSize(request.getTotalSize());
        mediaFile.setTotalChunks(request.getTotalChunks());
        mediaFile.setUploadedChunks(0);
        mediaFile.setUploadStatus(MediaFile.UploadStatus.UPLOADING);
        mediaFile.setUploadedBy(request.getUploadedBy());
        
        // Determine media type and file type
        FileTypeValidationService.FileTypeValidationResult validation = 
            fileTypeValidationService.validateFileType(request.getFileName());
        
        if (validation.isValid()) {
            if ("IMAGE".equals(validation.getFileType())) {
                mediaFile.setMediaType(MediaFile.MediaType.IMAGE);
            } else if ("VIDEO".equals(validation.getFileType())) {
                mediaFile.setMediaType(MediaFile.MediaType.VIDEO);
            }
            mediaFile.setFileType(validation.getFileType());
        } else {
            mediaFile.setMediaType(MediaFile.MediaType.UNKNOWN);
            mediaFile.setFileType("UNKNOWN");
        }
        
        return mediaFileRepository.save(mediaFile);
    }
    
    private String uploadChunkToStorage(ChunkUploadRequest request, MultipartFile file) throws Exception {
        String chunkPath = String.format("%s/chunks/%s_%d", 
                request.getFileMd5(), request.getFileMd5(), request.getChunkIndex());
        
        try (InputStream inputStream = file.getInputStream()) {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(minioBucketName)
                    .object(chunkPath)
                    .stream(inputStream, file.getSize(), -1)
                    .build());
        }
        
        log.debug("Uploaded chunk to storage: {}", chunkPath);
        return chunkPath;
    }
    
    private void updateChunkCache(String fileMd5, Integer chunkIndex) {
        String cacheKey = CHUNK_CACHE_PREFIX + fileMd5;
        redisTemplate.opsForSet().add(cacheKey, chunkIndex);
        redisTemplate.expire(cacheKey, CACHE_EXPIRY_HOURS, TimeUnit.HOURS);
    }
    
    private void updateUploadProgress(MediaFile mediaFile, Integer totalChunks) {
        long uploadedChunks = chunkInfoRepository.countByFileMd5AndStatus(
                mediaFile.getFileMd5(), ChunkInfo.ChunkStatus.UPLOADED);
        
        mediaFile.setUploadedChunks((int) uploadedChunks);
        mediaFileRepository.save(mediaFile);
        
        // Cache progress
        String progressKey = UPLOAD_PROGRESS_PREFIX + mediaFile.getFileMd5();
        Map<String, Object> progress = new HashMap<>();
        progress.put("uploadedChunks", uploadedChunks);
        progress.put("totalChunks", totalChunks);
        progress.put("progress", (double) uploadedChunks / totalChunks * 100);
        
        redisTemplate.opsForHash().putAll(progressKey, progress);
        redisTemplate.expire(progressKey, CACHE_EXPIRY_HOURS, TimeUnit.HOURS);
    }
    
    private boolean isUploadComplete(String fileMd5, Integer totalChunks) {
        long uploadedChunks = chunkInfoRepository.countByFileMd5AndStatus(
                fileMd5, ChunkInfo.ChunkStatus.UPLOADED);
        return uploadedChunks == totalChunks;
    }
    
    private UploadResponse completeUpload(MediaFile mediaFile) {
        try {
            // Merge chunks and create final file
            String finalPath = mergeChunks(mediaFile);
            
            // Update media file status
            mediaFile.setUploadStatus(MediaFile.UploadStatus.COMPLETED);
            mediaFile.setFilePath(finalPath);
            mediaFile.setStorageUrl(generateFileUrl(finalPath));
            mediaFileRepository.save(mediaFile);
            
            // Send message for metadata analysis
            Map<String, Object> analysisMessage = new HashMap<>();
            analysisMessage.put("fileMd5", mediaFile.getFileMd5());
            analysisMessage.put("fileName", mediaFile.getFileName());
            analysisMessage.put("fileType", mediaFile.getFileType());
            analysisMessage.put("filePath", finalPath);
            
            kafkaTemplate.send(metadataAnalysisTopic, analysisMessage);
            
            log.info("Upload completed for file: {} (MD5: {})", mediaFile.getFileName(), mediaFile.getFileMd5());
            
            UploadResponse response = buildUploadResponse(mediaFile.getFileMd5(), "Upload completed successfully");
            response.setFileId(mediaFile.getId());
            return response;
            
        } catch (Exception e) {
            log.error("Error completing upload for file: {}", mediaFile.getFileMd5(), e);
            mediaFile.setUploadStatus(MediaFile.UploadStatus.FAILED);
            mediaFile.setUploadNote("Failed to merge chunks: " + e.getMessage());
            mediaFileRepository.save(mediaFile);
            
            return UploadResponse.error("Failed to complete upload: " + e.getMessage());
        }
    }
    
    private String mergeChunks(MediaFile mediaFile) throws Exception {
        String finalPath = String.format("%s/%s", mediaFile.getFileMd5(), mediaFile.getFileName());
        
        // Get all chunks in order
        List<ChunkInfo> chunks = chunkInfoRepository.findByFileMd5OrderByChunkIndex(mediaFile.getFileMd5());
        
        log.info("Merging {} chunks for file: {}", chunks.size(), mediaFile.getFileName());
        
        // Validate all chunks are present
        if (chunks.size() != mediaFile.getTotalChunks()) {
            throw new IllegalStateException(String.format(
                "Missing chunks: expected %d, found %d", mediaFile.getTotalChunks(), chunks.size()));
        }
        
        // Create merged file by streaming chunks
        java.io.PipedInputStream mergedStream = null;
        java.io.PipedOutputStream outputStream = null;
        Thread mergeThread = null;
        boolean mergeSuccessful = false;
        
        try {
            // Use PipedInputStream/PipedOutputStream to create a streaming merge
            mergedStream = new java.io.PipedInputStream(1048576); // 1MB buffer
            outputStream = new java.io.PipedOutputStream(mergedStream);
            
            final java.io.PipedOutputStream finalOutputStream = outputStream;
            
            // Start a separate thread to write chunks to the piped stream
            mergeThread = new Thread(() -> {
                try {
                    log.debug("Starting chunk merge thread for {} chunks", chunks.size());
                    
                    for (int i = 0; i < chunks.size(); i++) {
                        ChunkInfo chunk = chunks.get(i);
                        log.debug("Processing chunk {} of {}: {}", i + 1, chunks.size(), chunk.getChunkPath());
                        
                        try (InputStream chunkStream = minioClient.getObject(GetObjectArgs.builder()
                                .bucket(minioBucketName)
                                .object(chunk.getChunkPath())
                                .build())) {
                            
                            byte[] buffer = new byte[16384]; // 16KB buffer
                            int bytesRead;
                            long totalBytesRead = 0;
                            
                            while ((bytesRead = chunkStream.read(buffer)) != -1) {
                                finalOutputStream.write(buffer, 0, bytesRead);
                                totalBytesRead += bytesRead;
                            }
                            
                            log.debug("Merged chunk {}: {} bytes", i + 1, totalBytesRead);
                        } catch (Exception e) {
                            log.error("Failed to read chunk {}: {}", chunk.getChunkPath(), e.getMessage());
                            throw e;
                        }
                    }
                    
                    log.info("All chunks merged successfully in thread");
                    
                } catch (Exception e) {
                    log.error("Error merging chunks in thread", e);
                    throw new RuntimeException(e);
                } finally {
                    try {
                        finalOutputStream.close();
                        log.debug("Closed output stream in merge thread");
                    } catch (IOException e) {
                        log.warn("Error closing output stream", e);
                    }
                }
            }, "chunk-merge-thread-" + mediaFile.getFileMd5().substring(0, 8));
            
            mergeThread.start();
            
            // Upload the merged stream to final location
            log.info("Uploading merged file to MinIO: {}", finalPath);
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(minioBucketName)
                    .object(finalPath)
                    .stream(mergedStream, -1, 10485760) // 10MB part size
                    .build());
            
            // Wait for merge thread to complete with timeout
            mergeThread.join(300000); // 5 minutes timeout
            
            if (mergeThread.isAlive()) {
                log.error("Merge thread timeout, interrupting...");
                mergeThread.interrupt();
                throw new Exception("Chunk merge timeout after 5 minutes");
            }
            
            mergeSuccessful = true;
            log.info("Successfully merged {} chunks into: {}", chunks.size(), finalPath);
            
        } catch (Exception e) {
            log.error("Failed to merge chunks for file: {}", mediaFile.getFileMd5(), e);
            
            // Clean up partial file if it exists
            if (!mergeSuccessful) {
                try {
                    minioClient.removeObject(RemoveObjectArgs.builder()
                            .bucket(minioBucketName)
                            .object(finalPath)
                            .build());
                    log.info("Cleaned up partial merged file: {}", finalPath);
                } catch (Exception cleanupException) {
                    log.warn("Failed to clean up partial file: {}", finalPath, cleanupException);
                }
            }
            
            throw new Exception("Chunk merge failed: " + e.getMessage(), e);
            
        } finally {
            // Clean up resources
            if (mergeThread != null && mergeThread.isAlive()) {
                mergeThread.interrupt();
            }
            
            try {
                if (outputStream != null) outputStream.close();
            } catch (IOException e) {
                log.warn("Error closing output stream in finally block", e);
            }
            
            try {
                if (mergedStream != null) mergedStream.close();
            } catch (IOException e) {
                log.warn("Error closing merged stream in finally block", e);
            }
        }
        
        // Clean up individual chunks only after successful merge
        if (mergeSuccessful) {
            int cleanedChunks = 0;
            for (ChunkInfo chunk : chunks) {
                try {
                    minioClient.removeObject(RemoveObjectArgs.builder()
                            .bucket(minioBucketName)
                            .object(chunk.getChunkPath())
                            .build());
                    
                    // Update chunk status
                    chunk.setStatus(ChunkInfo.ChunkStatus.MERGED);
                    chunkInfoRepository.save(chunk);
                    cleanedChunks++;
                    
                } catch (Exception e) {
                    log.warn("Failed to clean up chunk: {}", chunk.getChunkPath(), e);
                }
            }
            
            log.info("Cleaned up {} of {} chunk files", cleanedChunks, chunks.size());
        }
        
        return finalPath;
    }
    
    private String generateFileUrl(String filePath) {
        // Generate MinIO presigned URL or public URL
        return String.format("/%s/%s", minioBucketName, filePath);
    }
    
    private String generateUniqueFileName(String originalFileName) {
        String extension = "";
        int lastDot = originalFileName.lastIndexOf('.');
        if (lastDot > 0) {
            extension = originalFileName.substring(lastDot);
        }
        return System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 8) + extension;
    }
    
    private UploadResponse buildUploadResponse(String fileMd5, String message) {
        Optional<MediaFile> mediaFileOpt = mediaFileRepository.findByFileMd5(fileMd5);
        if (mediaFileOpt.isEmpty()) {
            return UploadResponse.error("File not found");
        }
        
        MediaFile mediaFile = mediaFileOpt.get();
        List<Integer> uploadedChunkIndices = chunkInfoRepository
                .getChunkIndicesByFileMd5AndStatus(fileMd5, ChunkInfo.ChunkStatus.UPLOADED);
        
        List<Integer> missingChunks = new ArrayList<>();
        if (mediaFile.getTotalChunks() != null) {
            for (int i = 0; i < mediaFile.getTotalChunks(); i++) {
                if (!uploadedChunkIndices.contains(i)) {
                    missingChunks.add(i);
                }
            }
        }
        
        double progress = mediaFile.getTotalChunks() != null && mediaFile.getTotalChunks() > 0 
            ? (double) mediaFile.getUploadedChunks() / mediaFile.getTotalChunks() * 100 
            : 0.0;
        
        UploadResponse response = UploadResponse.success(message);
        response.setFileMd5(fileMd5);
        response.setFileName(mediaFile.getOriginalFileName());
        response.setUploadedChunks(mediaFile.getUploadedChunks());
        response.setTotalChunks(mediaFile.getTotalChunks());
        response.setUploadProgress(progress);
        response.setMissingChunks(missingChunks);
        response.setUploadStatus(mediaFile.getUploadStatus().name());
        response.setFileId(mediaFile.getId());
        
        return response;
    }
}
