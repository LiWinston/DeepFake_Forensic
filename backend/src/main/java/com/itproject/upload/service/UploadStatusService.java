package com.itproject.upload.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * Service for managing upload status (pause/resume/cancel)
 */
@Slf4j
@Service
public class UploadStatusService {
    
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    
    private static final String UPLOAD_STATUS_PREFIX = "upload_status:";
    private static final String FAILED_CHUNKS_PREFIX = "failed_chunks:";
    private static final long CACHE_EXPIRY_HOURS = 48;
    
    // Upload status constants
    public static final String STATUS_UPLOADING = "UPLOADING";
    public static final String STATUS_PAUSED = "PAUSED";
    public static final String STATUS_CANCELLED = "CANCELLED";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_FAILED = "FAILED";
    
    /**
     * Set upload status
     */
    public void setUploadStatus(String fileMd5, String status) {
        String key = UPLOAD_STATUS_PREFIX + fileMd5;
        redisTemplate.opsForValue().set(key, status);
        redisTemplate.expire(key, CACHE_EXPIRY_HOURS, TimeUnit.HOURS);
        log.debug("Set upload status for file {} to {}", fileMd5, status);
    }
    
    /**
     * Get upload status
     */
    public String getUploadStatus(String fileMd5) {
        String key = UPLOAD_STATUS_PREFIX + fileMd5;
        Object status = redisTemplate.opsForValue().get(key);
        return status != null ? status.toString() : STATUS_UPLOADING;
    }
    
    /**
     * Check if upload is paused
     */
    public boolean isUploadPaused(String fileMd5) {
        return STATUS_PAUSED.equals(getUploadStatus(fileMd5));
    }
    
    /**
     * Check if upload is cancelled
     */
    public boolean isUploadCancelled(String fileMd5) {
        return STATUS_CANCELLED.equals(getUploadStatus(fileMd5));
    }
    
    /**
     * Pause upload
     */
    public void pauseUpload(String fileMd5) {
        setUploadStatus(fileMd5, STATUS_PAUSED);
        log.info("Upload paused for file: {}", fileMd5);
    }
    
    /**
     * Resume upload
     */
    public void resumeUpload(String fileMd5) {
        setUploadStatus(fileMd5, STATUS_UPLOADING);
        log.info("Upload resumed for file: {}", fileMd5);
    }
    
    /**
     * Cancel upload
     */
    public void cancelUpload(String fileMd5) {
        setUploadStatus(fileMd5, STATUS_CANCELLED);
        clearFailedChunks(fileMd5);
        log.info("Upload cancelled for file: {}", fileMd5);
    }
    
    /**
     * Mark upload as completed
     */
    public void markUploadCompleted(String fileMd5) {
        setUploadStatus(fileMd5, STATUS_COMPLETED);
        clearFailedChunks(fileMd5);
        log.info("Upload completed for file: {}", fileMd5);
    }
    
    /**
     * Mark chunk as failed
     */
    public void markChunkFailed(String fileMd5, int chunkIndex) {
        String key = FAILED_CHUNKS_PREFIX + fileMd5;
        redisTemplate.opsForSet().add(key, chunkIndex);
        redisTemplate.expire(key, CACHE_EXPIRY_HOURS, TimeUnit.HOURS);
        log.warn("Marked chunk {} as failed for file: {}", chunkIndex, fileMd5);
    }
    
    /**
     * Check if chunk has failed
     */
    public boolean isChunkFailed(String fileMd5, int chunkIndex) {
        String key = FAILED_CHUNKS_PREFIX + fileMd5;
        return redisTemplate.opsForSet().isMember(key, chunkIndex);
    }
    
    /**
     * Reset failed chunks for retry
     */
    public void resetFailedChunks(String fileMd5) {
        String key = FAILED_CHUNKS_PREFIX + fileMd5;
        redisTemplate.delete(key);
        log.info("Reset failed chunks for file: {}", fileMd5);
    }
    
    /**
     * Clear failed chunks
     */
    public void clearFailedChunks(String fileMd5) {
        String key = FAILED_CHUNKS_PREFIX + fileMd5;
        redisTemplate.delete(key);
    }
    
    /**
     * Get failed chunk count
     */
    public long getFailedChunkCount(String fileMd5) {
        String key = FAILED_CHUNKS_PREFIX + fileMd5;
        Long size = redisTemplate.opsForSet().size(key);
        return size != null ? size : 0;
    }
    
    /**
     * Clear all upload status data
     */
    public void clearUploadData(String fileMd5) {
        String statusKey = UPLOAD_STATUS_PREFIX + fileMd5;
        String failedKey = FAILED_CHUNKS_PREFIX + fileMd5;
        
        redisTemplate.delete(statusKey);
        redisTemplate.delete(failedKey);
        
        log.debug("Cleared upload data for file: {}", fileMd5);
    }
}
