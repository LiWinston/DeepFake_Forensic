package com.itproject.upload.repository;

import com.itproject.upload.entity.ChunkInfo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for ChunkInfo entity operations
 */
@Repository
public interface ChunkInfoRepository extends JpaRepository<ChunkInfo, Long> {
    
    /**
     * Find chunks by file MD5
     */
    List<ChunkInfo> findByFileMd5OrderByChunkIndex(String fileMd5);
    
    /**
     * Find specific chunk by file MD5 and chunk index
     */
    Optional<ChunkInfo> findByFileMd5AndChunkIndex(String fileMd5, Integer chunkIndex);
    
    /**
     * Count uploaded chunks for a file
     */
    long countByFileMd5AndStatus(String fileMd5, ChunkInfo.ChunkStatus status);
    
    /**
     * Find chunks by status
     */
    List<ChunkInfo> findByStatus(ChunkInfo.ChunkStatus status);
    
    /**
     * Delete chunks by file MD5
     */
    void deleteByFileMd5(String fileMd5);
    
    /**
     * Check if chunk exists
     */
    boolean existsByFileMd5AndChunkIndex(String fileMd5, Integer chunkIndex);
    
    /**
     * Get chunk indices for a file
     */
    @Query("SELECT c.chunkIndex FROM ChunkInfo c WHERE c.fileMd5 = :fileMd5 AND c.status = :status ORDER BY c.chunkIndex")
    List<Integer> getChunkIndicesByFileMd5AndStatus(@Param("fileMd5") String fileMd5, 
                                                   @Param("status") ChunkInfo.ChunkStatus status);
}
