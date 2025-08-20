package com.itproject.metadata.entity;

import com.itproject.analysis.entity.AnalysisTask;
import com.itproject.auth.entity.User;
import com.itproject.project.entity.Project;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Media metadata entity for storing extracted metadata information
 */
@Entity
@Table(name = "media_metadata")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MediaMetadata {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, length = 32)
    private String fileMd5;
    
    @Column(length = 64)
    private String sha256Hash;
    
    @Column(length = 40)
    private String sha1Hash;
    
    // EXIF Data
    @Column(length = 100)
    private String cameraModel;
    
    @Column(length = 100)
    private String cameraMake;
    
    @Column
    private LocalDateTime dateTaken;
    
    @Column(length = 200)
    private String gpsLocation;
    
    @Column
    private Double gpsLatitude;
    
    @Column
    private Double gpsLongitude;
    
    @Column
    private Integer imageWidth;
    
    @Column
    private Integer imageHeight;
    
    @Column
    private Integer orientation;
    
    @Column(length = 50)
    private String colorSpace;
    
    // Video Metadata
    @Column
    private Integer videoDuration;
    
    @Column
    private Double frameRate;
    
    @Column(length = 50)
    private String videoCodec;
    
    @Column(length = 50)
    private String audioCodec;
    
    @Column
    private Integer bitRate;
    
    // Technical Metadata
    @Column(length = 100)
    private String mimeType;
    
    @Column(length = 50)
    private String fileFormat;
    
    @Column
    private Integer compressionLevel;
    
    @Column(columnDefinition = "TEXT")
    private String rawMetadata;
    
    // Analysis Results
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ExtractionStatus extractionStatus;
    
    @Column(columnDefinition = "TEXT")
    private String analysisNotes;
    
    @Column(columnDefinition = "TEXT")
    private String suspiciousIndicators;
    
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    
    // User relationship
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    
    // Project relationship
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;
    
    // Analysis task relationship - this metadata belongs to a specific analysis task
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "analysis_task_id")
    private AnalysisTask analysisTask;
    
    public enum ExtractionStatus {
        PENDING,
        SUCCESS,
        PARTIAL,
        FAILED
    }
}
