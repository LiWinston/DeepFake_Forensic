package com.itproject.upload.service;

import com.itproject.auth.entity.User;
import com.itproject.auth.security.SecurityUtils;
import com.itproject.project.entity.Project;
import com.itproject.project.repository.ProjectRepository;
import com.itproject.project.service.ProjectPermissionService;
import com.itproject.traditional.dto.TraditionalAnalysisTaskDto;
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
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.TimeUnit;
import net.coobird.thumbnailator.Thumbnails;

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
    private ProjectRepository projectRepository;
    
    @Autowired
    private ProjectPermissionService projectPermissionService;
    
    @Autowired
    private MinioClient minioClient;
    
    @Autowired
    private String minioBucketName;
    
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private RedisTemplate<String, byte[]> thumbnailRedisTemplate;
    
    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;
    
    @Autowired
    private String metadataAnalysisTopic;
    
    @Autowired
    private String traditionalAnalysisTopic;
    
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
        User currentUser = SecurityUtils.getCurrentUser();
        if (currentUser == null) {
            throw new RuntimeException("用户未登录");
        }
          // Get project
        Project project = projectRepository.findById(request.getProjectId())
            .orElseThrow(() -> new RuntimeException("项目不存在: " + request.getProjectId()));
          // Check if user has access to the project
        if (!project.getUser().getId().equals(currentUser.getId())) {
            throw new RuntimeException("没有权限访问该项目");
        }
        
        // Check if project allows file upload
        projectPermissionService.validatePermission(project, "upload files", 
            projectPermissionService.canUploadFiles(project));
        
        Optional<MediaFile> existingFile = mediaFileRepository.findByFileMd5AndUser(request.getFileMd5(), currentUser);
        
        if (existingFile.isPresent()) {
            MediaFile existing = existingFile.get();
            // Update project if different
            if (!existing.getProject().getId().equals(project.getId())) {
                existing.setProject(project);
                return mediaFileRepository.save(existing);
            }
            return existing;
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
        mediaFile.setUploadedBy(currentUser.getUsername());
        mediaFile.setUser(currentUser); // Set user relationship
        mediaFile.setProject(project); // Set project relationship
        
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
            analysisMessage.put("userId", mediaFile.getUser().getId()); // Add user ID
            analysisMessage.put("projectId", mediaFile.getProject().getId()); // Add project ID
            analysisMessage.put("forceReAnalysis", false); // Default: not force re-analysis on first completion
            
            kafkaTemplate.send(metadataAnalysisTopic, analysisMessage);
            
            // Send message for traditional analysis (for images only)
            // Use consistent image type checking with FileTypeValidationService
            if (MediaFile.MediaType.IMAGE.equals(mediaFile.getMediaType()) && "IMAGE".equals(mediaFile.getFileType())) {
                try {
                    // Create task DTO for automatic analysis (force=false)
                    TraditionalAnalysisTaskDto task = new TraditionalAnalysisTaskDto(mediaFile.getFileMd5(), false);
                    kafkaTemplate.send(traditionalAnalysisTopic, task);
                    log.info("Traditional analysis task sent for file: {} (MD5: {})", 
                            mediaFile.getFileName(), mediaFile.getFileMd5());
                } catch (Exception e) {
                    log.warn("Failed to send traditional analysis task for file: {} (MD5: {})", 
                            mediaFile.getFileName(), mediaFile.getFileMd5(), e);
                }
            } else {
                log.debug("Skipping traditional analysis for non-image file: {} (MediaType: {}, FileType: {})", 
                         mediaFile.getFileName(), mediaFile.getMediaType(), mediaFile.getFileType());
            }
            
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
    }    /**
     * Get files list with pagination and filtering for current user
     * Uses short-term caching to prevent duplicate requests within seconds
     */
    @Cacheable(value = "filesList", key = "#pageable.pageNumber + '-' + #pageable.pageSize + '-' + (#status ?: 'null') + '-' + (#type ?: 'null') + '-' + (#projectId ?: 'null') + '-' + T(com.itproject.auth.security.SecurityUtils).getCurrentUser().id")
    public Page<MediaFile> getFilesList(Pageable pageable, String status, String type, Long projectId) {
        try {
            User currentUser = SecurityUtils.getCurrentUser();
            if (currentUser == null) {
                throw new RuntimeException("User not logged in");
            }
            
            log.debug("Getting files list for user {}: pageable={}, status={}, type={}, projectId={}", 
                    currentUser.getUsername(), pageable, status, type, projectId);
            
            // Apply filters if provided
            if (projectId != null) {
                // Query by project
                Project project = projectRepository.findById(projectId)
                    .filter(p -> p.getUser().getId().equals(currentUser.getId()))
                    .orElseThrow(() -> new RuntimeException("Project not found or access denied"));
                
                if (StringUtils.hasText(status) && StringUtils.hasText(type)) {
                    MediaFile.UploadStatus uploadStatus = MediaFile.UploadStatus.valueOf(status.toUpperCase());
                    MediaFile.MediaType mediaType = MediaFile.MediaType.valueOf(type.toUpperCase());
                    return mediaFileRepository.findByProjectAndUploadStatusAndMediaType(project, uploadStatus, mediaType, pageable);
                } else if (StringUtils.hasText(status)) {
                    MediaFile.UploadStatus uploadStatus = MediaFile.UploadStatus.valueOf(status.toUpperCase());
                    return mediaFileRepository.findByProjectAndUploadStatus(project, uploadStatus, pageable);
                } else if (StringUtils.hasText(type)) {
                    MediaFile.MediaType mediaType = MediaFile.MediaType.valueOf(type.toUpperCase());
                    return mediaFileRepository.findByProjectAndMediaType(project, mediaType, pageable);
                } else {
                    return mediaFileRepository.findByProject(project, pageable);
                }            } else {
                // Query all files for current user only - ensure security
                // Always filter by current user to prevent seeing other users' files
                if (StringUtils.hasText(status) && StringUtils.hasText(type)) {
                    MediaFile.UploadStatus uploadStatus = MediaFile.UploadStatus.valueOf(status.toUpperCase());
                    MediaFile.MediaType mediaType = MediaFile.MediaType.valueOf(type.toUpperCase());
                    return mediaFileRepository.findByUploadStatusAndMediaTypeAndUser(uploadStatus, mediaType, currentUser, pageable);
                } else if (StringUtils.hasText(status)) {
                    MediaFile.UploadStatus uploadStatus = MediaFile.UploadStatus.valueOf(status.toUpperCase());
                    return mediaFileRepository.findByUploadStatusAndUser(uploadStatus, currentUser, pageable);
                } else if (StringUtils.hasText(type)) {
                    MediaFile.MediaType mediaType = MediaFile.MediaType.valueOf(type.toUpperCase());
                    return mediaFileRepository.findByMediaTypeAndUser(mediaType, currentUser, pageable);
                } else {
                    return mediaFileRepository.findByUser(currentUser, pageable);
                }
            }
            
        } catch (Exception e) {
            log.error("Error getting files list", e);
            throw new RuntimeException("Failed to get files list: " + e.getMessage(), e);
        }
    }
    
    /**
     * Get files list with pagination and filtering for current user (backward compatibility)
     */
    public Page<MediaFile> getFilesList(Pageable pageable, String status, String type) {
        return getFilesList(pageable, status, type, null);
    }
    
    /**
     * Delete file by ID
     */
    @Transactional
    public boolean deleteFile(String fileId) {
        try {
            log.info("Deleting file with ID: {}", fileId);
            
            // Find the file
            Optional<MediaFile> mediaFileOpt = mediaFileRepository.findById(Long.valueOf(fileId));
            if (mediaFileOpt.isEmpty()) {
                log.warn("File not found with ID: {}", fileId);
                return false;
            }
            
            MediaFile mediaFile = mediaFileOpt.get();
            
            // Delete file from MinIO storage
            try {
                if (StringUtils.hasText(mediaFile.getFilePath())) {
                    minioClient.removeObject(RemoveObjectArgs.builder()
                            .bucket(minioBucketName)
                            .object(mediaFile.getFilePath())
                            .build());
                    log.info("Deleted file from MinIO: {}", mediaFile.getFilePath());
                }
            } catch (Exception minioException) {
                log.warn("Failed to delete file from MinIO: {}", mediaFile.getFilePath(), minioException);
                // Continue with database cleanup even if MinIO deletion fails
            }
            
            // Delete related chunk info records
            try {
                chunkInfoRepository.deleteByFileMd5(mediaFile.getFileMd5());
                log.info("Deleted chunk info for file: {}", mediaFile.getFileMd5());
            } catch (Exception chunkException) {
                log.warn("Failed to delete chunk info: {}", mediaFile.getFileMd5(), chunkException);
            }
            
            // Delete any remaining chunks from MinIO
            try {
                List<ChunkInfo> chunks = chunkInfoRepository.findByFileMd5OrderByChunkIndex(mediaFile.getFileMd5());
                for (ChunkInfo chunk : chunks) {
                    try {
                        minioClient.removeObject(RemoveObjectArgs.builder()
                                .bucket(minioBucketName)
                                .object(chunk.getChunkPath())
                                .build());
                    } catch (Exception e) {
                        log.warn("Failed to delete chunk: {}", chunk.getChunkPath(), e);
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to clean up chunks for file: {}", mediaFile.getFileMd5(), e);
            }
            
            // Delete file record from database
            mediaFileRepository.delete(mediaFile);
            
            // Clear cached progress
            String progressKey = UPLOAD_PROGRESS_PREFIX + mediaFile.getFileMd5();
            String chunkCacheKey = CHUNK_CACHE_PREFIX + mediaFile.getFileMd5();
            try {
                redisTemplate.delete(progressKey);
                redisTemplate.delete(chunkCacheKey);
            } catch (Exception redisException) {
                log.warn("Failed to clear Redis cache for file: {}", mediaFile.getFileMd5(), redisException);
            }
            
            log.info("Successfully deleted file: {} ({})", mediaFile.getOriginalFileName(), fileId);
            return true;
            
        } catch (NumberFormatException e) {
            log.error("Invalid file ID format: {}", fileId, e);
            return false;
        } catch (Exception e) {
            log.error("Error deleting file: {}", fileId, e);
            throw new RuntimeException("Failed to delete file: " + e.getMessage(), e);
        }
    }
    
    /**
     * Get file stream for preview
     */
    public InputStream getFileStream(String fileId) throws Exception {
        try {
            log.info("Getting file stream for ID: {}", fileId);
            
            MediaFile mediaFile = getMediaFileByIdOrMd5(fileId);
            
            if (mediaFile == null) {
                throw new RuntimeException("File not found: " + fileId);
            }
            
            log.info("Found media file: id={}, md5={}, filename={}, status={}", 
                    mediaFile.getId(), mediaFile.getFileMd5(), mediaFile.getFileName(), mediaFile.getUploadStatus());
            
            if (mediaFile.getUploadStatus() != MediaFile.UploadStatus.COMPLETED) {
                throw new RuntimeException("File not completed: " + fileId + ", status: " + mediaFile.getUploadStatus());
            }
            
            // Get file from MinIO
            String objectPath = String.format("%s/%s", mediaFile.getFileMd5(), mediaFile.getFileName());
            log.info("Getting object from MinIO: bucket={}, path={}", minioBucketName, objectPath);
            
            return minioClient.getObject(GetObjectArgs.builder()
                    .bucket(minioBucketName)
                    .object(objectPath)
                    .build());
                    
        } catch (Exception e) {
            log.error("Failed to get file stream for: {}", fileId, e);
            throw new RuntimeException("Failed to get file stream: " + e.getMessage());
        }
    }
    
    /**
     * Get file content type for preview
     */
    public String getFileContentType(String fileId) throws Exception {
        MediaFile mediaFile = getMediaFileByIdOrMd5(fileId);
        if (mediaFile == null) {
            throw new RuntimeException("File not found: " + fileId);
        }
        
        // Return MIME type based on file extension
        String fileName = mediaFile.getFileName().toLowerCase();
        if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg")) {
            return "image/jpeg";
        } else if (fileName.endsWith(".png")) {
            return "image/png";
        } else if (fileName.endsWith(".gif")) {
            return "image/gif";
        } else if (fileName.endsWith(".bmp")) {
            return "image/bmp";
        } else if (fileName.endsWith(".webp")) {
            return "image/webp";
        } else if (fileName.endsWith(".mp4")) {
            return "video/mp4";
        } else if (fileName.endsWith(".avi")) {
            return "video/x-msvideo";
        } else if (fileName.endsWith(".mov")) {
            return "video/quicktime";
        } else if (fileName.endsWith(".wmv")) {
            return "video/x-ms-wmv";
        } else if (fileName.endsWith(".webm")) {
            return "video/webm";
        } else if (fileName.endsWith(".mkv")) {
            return "video/x-matroska";
        } else {
            return "application/octet-stream";
        }
    }
    
    /**
     * Get file name for preview
     */
    public String getFileName(String fileId) throws Exception {
        MediaFile mediaFile = getMediaFileByIdOrMd5(fileId);
        if (mediaFile == null) {
            throw new RuntimeException("File not found: " + fileId);
        }
        return mediaFile.getFileName();
    }
    
    /**
     * Helper method to get MediaFile by ID or MD5
     */
    private MediaFile getMediaFileByIdOrMd5(String fileId) {
        log.debug("Looking for file with identifier: {}", fileId);
        
        // First check if it's a numeric ID (short string, only digits)
        if (fileId.matches("\\d+")) {
            try {
                Long id = Long.parseLong(fileId);
                log.debug("Searching by numeric ID: {}", id);
                MediaFile result = mediaFileRepository.findById(id).orElse(null);
                if (result != null) {
                    log.debug("Found file by ID: {} -> {}", id, result.getFileName());
                    return result;
                }
            } catch (NumberFormatException e) {
                log.warn("Failed to parse numeric ID: {}", fileId);
            }
        }
        
        // If not found by ID or not numeric, try MD5 hash
        log.debug("Searching by MD5 hash: {}", fileId);
        MediaFile result = mediaFileRepository.findByFileMd5(fileId).orElse(null);
        if (result != null) {
            log.debug("Found file by MD5: {} -> {}", fileId, result.getFileName());
            return result;
        }
        
        log.warn("File not found by ID or MD5: {}", fileId);
        return null;
    }
    
    /**
     * Get file thumbnail with Redis caching
     */
    public byte[] getFileThumbnail(String fileId) throws Exception {
        try {
            log.info("Getting thumbnail for file: {}", fileId);
            
            // Check Redis cache first
            String thumbnailKey = "thumbnail:" + fileId;
            String errorKey = "thumbnail_error:" + fileId;
            byte[] cachedThumbnail = thumbnailRedisTemplate.opsForValue().get(thumbnailKey);
            
            if (cachedThumbnail != null && cachedThumbnail.length > 0) {
                log.info("Returning cached thumbnail for file: {}", fileId);
                return cachedThumbnail;
            }

            // Backward compatibility: migrate from old JSON-serialized cache if present
            Object legacyCached = redisTemplate.opsForValue().get(thumbnailKey);
            if (legacyCached instanceof byte[] legacyBytes && legacyBytes.length > 0) {
                thumbnailRedisTemplate.opsForValue().set(thumbnailKey, legacyBytes, 96, TimeUnit.HOURS);
                log.info("Migrated legacy cached thumbnail for file: {}", fileId);
                return legacyBytes;
            } else if (legacyCached instanceof String legacyBase64 && !legacyBase64.isEmpty()) {
                try {
                    byte[] decoded = java.util.Base64.getDecoder().decode(legacyBase64);
                    if (decoded.length > 0) {
                        thumbnailRedisTemplate.opsForValue().set(thumbnailKey, decoded, 24, TimeUnit.HOURS);
                        log.info("Migrated legacy base64 cached thumbnail for file: {}", fileId);
                        return decoded;
                    }
                } catch (IllegalArgumentException ignore) {
                    // Not valid base64, proceed to generate
                }
            }
            
            // Check if we've already failed to generate thumbnail for this file
            if (Boolean.TRUE.equals(redisTemplate.hasKey(errorKey))) {
                log.info("Returning fallback thumbnail for previously failed file: {}", fileId);
                return generateFallbackThumbnail();
            }
            
            // Get media file info
            MediaFile mediaFile = getMediaFileByIdOrMd5(fileId);
            if (mediaFile == null) {
                throw new RuntimeException("File not found: " + fileId);
            }
            
            if (mediaFile.getUploadStatus() != MediaFile.UploadStatus.COMPLETED) {
                throw new RuntimeException("File not completed: " + fileId);
            }
            
            // Only generate thumbnails for images
            String fileName = mediaFile.getFileName().toLowerCase();
            if (!isImageFile(fileName)) {
                log.warn("Thumbnail not supported for file type: {}, returning fallback", fileName);
                byte[] fallbackThumbnail = generateFallbackThumbnail();
                // Cache the fallback for non-image files
                thumbnailRedisTemplate.opsForValue().set(thumbnailKey, fallbackThumbnail, 24, TimeUnit.HOURS);
                return fallbackThumbnail;
            }
            
            // Try to generate thumbnail with robust error handling
            return generateThumbnailWithFallback(fileId, mediaFile, thumbnailKey, errorKey);
            
        } catch (Exception e) {
            log.error("Failed to get thumbnail for file: {}", fileId, e);
            // Return fallback thumbnail instead of throwing exception
            return generateFallbackThumbnail();
        }
    }
    
    /**
     * Generate thumbnail with fallback mechanism
     */
    private byte[] generateThumbnailWithFallback(String fileId, MediaFile mediaFile, String thumbnailKey, String errorKey) {
        String objectPath = String.format("%s/%s", mediaFile.getFileMd5(), mediaFile.getFileName());
        
        try {
            log.info("Generating thumbnail for file: {} ({})", fileId, mediaFile.getFileName());
            
            try (InputStream originalStream = minioClient.getObject(GetObjectArgs.builder()
                    .bucket(minioBucketName)
                    .object(objectPath)
                    .build())) {
                
                // Generate thumbnail using Thumbnailator with error handling
                ByteArrayOutputStream thumbnailStream = new ByteArrayOutputStream();
                
                try {
                    Thumbnails.of(originalStream)
                            .size(150, 150) // Max size 150x150px
                            .keepAspectRatio(true)
                            .outputQuality(0.8) // 80% quality for smaller file size
                            .outputFormat("JPEG") // Always output as JPEG for smaller size
                            .toOutputStream(thumbnailStream);
                    
                    byte[] thumbnailData = thumbnailStream.toByteArray();
                    
                    // Validate generated thumbnail
                    if (thumbnailData.length == 0) {
                        throw new RuntimeException("Generated thumbnail is empty");
                    }
                    
                    // Cache in Redis for 24 hours
                    thumbnailRedisTemplate.opsForValue().set(thumbnailKey, thumbnailData, 24, TimeUnit.HOURS);
                    
                    log.info("Generated and cached thumbnail for file: {} (size: {} bytes)", fileId, thumbnailData.length);
                    return thumbnailData;
                    
                } catch (Exception thumbnailException) {
                    log.warn("Failed to generate thumbnail for file: {} ({}), reason: {}", 
                            fileId, mediaFile.getFileName(), thumbnailException.getMessage());
                    
                    // Mark this file as having thumbnail generation issues
                    redisTemplate.opsForValue().set(errorKey, "failed", 1, TimeUnit.HOURS);
                    
                    // Return fallback thumbnail
                    byte[] fallbackThumbnail = generateFallbackThumbnail();
                    thumbnailRedisTemplate.opsForValue().set(thumbnailKey, fallbackThumbnail, 1, TimeUnit.HOURS); // Cache fallback for 1 hour
                    
                    return fallbackThumbnail;
                }
            }
            
        } catch (Exception e) {
            log.error("Failed to access file for thumbnail generation: {} ({})", fileId, mediaFile.getFileName(), e);
            
            // Mark error and return fallback
            redisTemplate.opsForValue().set(errorKey, "access_failed", 1, TimeUnit.HOURS);
            return generateFallbackThumbnail();
        }
    }
    
    /**
     * Generate a fallback thumbnail for files that can't be processed
     */
    private byte[] generateFallbackThumbnail() {
        try {
            // Create a simple 150x150 placeholder image
            ByteArrayOutputStream fallbackStream = new ByteArrayOutputStream();
            
            // Generate a simple colored rectangle as fallback
            java.awt.image.BufferedImage fallbackImage = new java.awt.image.BufferedImage(150, 150, java.awt.image.BufferedImage.TYPE_INT_RGB);
            java.awt.Graphics2D g2d = fallbackImage.createGraphics();
            
            // Set background color (light gray)
            g2d.setColor(new java.awt.Color(240, 240, 240));
            g2d.fillRect(0, 0, 150, 150);
            
            // Draw border
            g2d.setColor(new java.awt.Color(200, 200, 200));
            g2d.drawRect(0, 0, 149, 149);
            
            // Draw icon/text indicating this is a placeholder
            g2d.setColor(new java.awt.Color(100, 100, 100));
            g2d.setFont(new java.awt.Font("Arial", java.awt.Font.BOLD, 12));
            java.awt.FontMetrics fm = g2d.getFontMetrics();
            String text = "IMAGE";
            int textWidth = fm.stringWidth(text);
            int textHeight = fm.getHeight();
            g2d.drawString(text, (150 - textWidth) / 2, (150 + textHeight) / 2 - 3);
            
            g2d.dispose();
            
            // Convert to JPEG
            javax.imageio.ImageIO.write(fallbackImage, "JPEG", fallbackStream);
            
            return fallbackStream.toByteArray();
            
        } catch (Exception e) {
            log.error("Failed to generate fallback thumbnail", e);
            // Return minimal byte array as last resort
            return new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xD9}; // Minimal JPEG header/footer
        }
    }
    
    /**
     * Get error thumbnail for controller fallback
     */
    public byte[] getErrorThumbnail() {
        try {
            // Create a red-tinted error thumbnail
            ByteArrayOutputStream errorStream = new ByteArrayOutputStream();
            
            java.awt.image.BufferedImage errorImage = new java.awt.image.BufferedImage(150, 150, java.awt.image.BufferedImage.TYPE_INT_RGB);
            java.awt.Graphics2D g2d = errorImage.createGraphics();
            
            // Set background color (light red)
            g2d.setColor(new java.awt.Color(255, 230, 230));
            g2d.fillRect(0, 0, 150, 150);
            
            // Draw border (red)
            g2d.setColor(new java.awt.Color(255, 100, 100));
            g2d.drawRect(0, 0, 149, 149);
            
            // Draw error text
            g2d.setColor(new java.awt.Color(150, 0, 0));
            g2d.setFont(new java.awt.Font("Arial", java.awt.Font.BOLD, 11));
            java.awt.FontMetrics fm = g2d.getFontMetrics();
            String text = "ERROR";
            int textWidth = fm.stringWidth(text);
            int textHeight = fm.getHeight();
            g2d.drawString(text, (150 - textWidth) / 2, (150 + textHeight) / 2 - 3);
            
            g2d.dispose();
            
            javax.imageio.ImageIO.write(errorImage, "JPEG", errorStream);
            return errorStream.toByteArray();
            
        } catch (Exception e) {
            log.error("Failed to generate error thumbnail", e);
            return new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xD9};
        }
    }
    
    /**
     * Check if file is an image
     */
    private boolean isImageFile(String fileName) {
        return fileName.endsWith(".jpg") || 
               fileName.endsWith(".jpeg") || 
               fileName.endsWith(".png") || 
               fileName.endsWith(".gif") || 
               fileName.endsWith(".bmp") || 
               fileName.endsWith(".webp");
    }
}
