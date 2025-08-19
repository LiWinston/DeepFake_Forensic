package com.itproject.upload.entity;

import com.itproject.auth.entity.User;
import com.itproject.project.entity.Project;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Media file entity for storing uploaded media information
 */
@Entity
@Table(name = "media_files")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MediaFile {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, unique = true, length = 32)
    private String fileMd5;
    
    @Column(nullable = false, length = 255)
    private String fileName;
    
    @Column(nullable = false, length = 255)
    private String originalFileName;
    
    @Column(nullable = false, length = 50)
    private String fileType;
    
    @Column(nullable = false)
    private Long fileSize;
    
    @Column(length = 500)
    private String filePath;
    
    @Column(length = 500)
    private String storageUrl;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UploadStatus uploadStatus;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MediaType mediaType;
    
    @Column
    private Integer totalChunks;
    
    @Column
    private Integer uploadedChunks;
    
    @Column(length = 1000)
    private String uploadNote;
    
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    
    @Column(name = "uploaded_by", length = 100)
    private String uploadedBy;
    
    // User relationship
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // Project relationship
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;
    
    public enum UploadStatus {
        UPLOADING,
        COMPLETED,
        FAILED,
        CANCELLED
    }
    
    public enum MediaType {
        IMAGE,
        VIDEO,
        UNKNOWN
    }
}
