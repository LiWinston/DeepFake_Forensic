package com.itproject.upload.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.itproject.auth.entity.User;
import com.itproject.project.entity.Project;
import jakarta.persistence.*;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Media file entity for storing uploaded media information
 * Compatible with both JPA and MyBatis-Plus
 */
@Entity  // JPA annotation
@Table(name = "media_files")  // JPA annotation
@TableName("media_files")  // MyBatis-Plus annotation
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MediaFile {
    
    @Id  // JPA annotation
    @GeneratedValue(strategy = GenerationType.IDENTITY)  // JPA annotation
    @TableId(type = IdType.AUTO)  // MyBatis-Plus annotation
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
    @ManyToOne(fetch = FetchType.LAZY)  // JPA annotation
    @JoinColumn(name = "user_id", nullable = false)  // JPA annotation
    @TableField(exist = false)  // MyBatis-Plus annotation - exclude from MP operations
    private User user;

    // Project relationship
    @ManyToOne(fetch = FetchType.LAZY)  // JPA annotation
    @JoinColumn(name = "project_id", nullable = false)  // JPA annotation
    @TableField(exist = false)  // MyBatis-Plus annotation - exclude from MP operations
    private Project project;
    
    // Foreign key fields for MyBatis-Plus queries
    @Column(name = "user_id", insertable = false, updatable = false)  // JPA read-only
    @TableField("user_id")  // MyBatis-Plus annotation
    private Long userId;
    
    @Column(name = "project_id", insertable = false, updatable = false)  // JPA read-only
    @TableField("project_id")  // MyBatis-Plus annotation  
    private Long projectId;
    
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
