package com.itproject.upload.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Service for managing chunk upload status using Redis BitMap
 * This reduces SQL pressure and provides faster chunk status queries
 */
@Slf4j
@Service
public class ChunkBitmapService {
    
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    
    private static final String CHUNK_BITMAP_PREFIX = "chunk_bitmap:";
    private static final long CACHE_EXPIRY_HOURS = 48; // 48 hours for upload completion
    
    /**
     * Mark chunk as uploaded
     */
    public void markChunkUploaded(String fileMd5, int chunkIndex) {
        String key = CHUNK_BITMAP_PREFIX + fileMd5;
        redisTemplate.opsForValue().setBit(key, chunkIndex, true);
        redisTemplate.expire(key, CACHE_EXPIRY_HOURS, TimeUnit.HOURS);
        log.debug("Marked chunk {} as uploaded for file {}", chunkIndex, fileMd5);
    }
    
    /**
     * Check if chunk is uploaded
     */
    public boolean isChunkUploaded(String fileMd5, int chunkIndex) {
        String key = CHUNK_BITMAP_PREFIX + fileMd5;
        Boolean result = redisTemplate.opsForValue().getBit(key, chunkIndex);
        return result != null && result;
    }
    
    /**
     * Get count of uploaded chunks
     */
    public long getUploadedChunkCount(String fileMd5) {
        String key = CHUNK_BITMAP_PREFIX + fileMd5;
        // Count uploaded chunks by iterating through bits
        // This is more compatible across Redis versions
        long count = 0;
        for (int i = 0; i < 10000; i++) { // Reasonable max chunks limit
            Boolean bit = redisTemplate.opsForValue().getBit(key, i);
            if (bit != null && bit) {
                count++;
            } else if (bit == null) {
                // If bit is null, we've reached the end of set bits
                break;
            }
        }
        return count;
    }
    
    /**
     * Get missing chunk indices
     */
    public List<Integer> getMissingChunks(String fileMd5, int totalChunks) {
        List<Integer> missingChunks = new ArrayList<>();
        String key = CHUNK_BITMAP_PREFIX + fileMd5;
        
        for (int i = 0; i < totalChunks; i++) {
            Boolean isUploaded = redisTemplate.opsForValue().getBit(key, i);
            if (isUploaded == null || !isUploaded) {
                missingChunks.add(i);
            }
        }
        
        return missingChunks;
    }
    
    /**
     * Check if all chunks are uploaded
     */
    public boolean isUploadComplete(String fileMd5, int totalChunks) {
        return getMissingChunks(fileMd5, totalChunks).isEmpty();
    }
    
    /**
     * Get upload progress percentage
     */
    public double getUploadProgress(String fileMd5, int totalChunks) {
        if (totalChunks <= 0) return 0.0;
        
        long uploadedCount = getUploadedChunkCount(fileMd5);
        return (double) uploadedCount / totalChunks * 100.0;
    }
    
    /**
     * Clear chunk bitmap (called after successful upload completion)
     */
    public void clearChunkBitmap(String fileMd5) {
        String key = CHUNK_BITMAP_PREFIX + fileMd5;
        redisTemplate.delete(key);
        log.debug("Cleared chunk bitmap for file {}", fileMd5);
    }
    
    /**
     * Initialize bitmap from existing database records
     * Used for recovery scenarios
     */
    public void initializeBitmapFromDatabase(String fileMd5, List<Integer> uploadedChunkIndices) {
        String key = CHUNK_BITMAP_PREFIX + fileMd5;
        
        for (Integer chunkIndex : uploadedChunkIndices) {
            redisTemplate.opsForValue().setBit(key, chunkIndex, true);
        }
        
        redisTemplate.expire(key, CACHE_EXPIRY_HOURS, TimeUnit.HOURS);
        log.info("Initialized bitmap for file {} with {} uploaded chunks", fileMd5, uploadedChunkIndices.size());
    }
}
