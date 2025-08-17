package com.itproject.upload.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Chunk information entity for tracking upload chunks
 */
@Entity
@Table(name = "chunk_info")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChunkInfo {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, length = 32)
    private String fileMd5;
    
    @Column(nullable = false)
    private Integer chunkIndex;
    
    @Column(nullable = false)
    private Long chunkSize;
    
    @Column(length = 32)
    private String chunkMd5;
    
    @Column(length = 500)
    private String chunkPath;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ChunkStatus status;
    
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    public enum ChunkStatus {
        UPLOADED,
        VERIFIED,
        MERGED,
        FAILED
    }
}
