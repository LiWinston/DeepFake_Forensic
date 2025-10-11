package com.itproject.traditional.entity;

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
 * Entity for storing traditional forensic analysis results
 */
@Entity
@Table(name = "traditional_analysis_results")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TraditionalAnalysisResult {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, length = 32)
    private String fileMd5;
    
    @Column(nullable = false, length = 255)
    private String originalFilePath;
    
    // Analysis Status
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AnalysisStatus analysisStatus;
    
    // ELA Analysis Results
    @Column(name = "ela_confidence_score")
    private Double elaConfidenceScore;
    
    @Column(name = "ela_result_path")
    private String elaResultPath;
    
    @Column(name = "ela_suspicious_regions")
    private Integer elaSuspiciousRegions;
    
    // CFA Analysis Results
    @Column(name = "cfa_confidence_score")
    private Double cfaConfidenceScore;
    
    @Column(name = "cfa_heatmap_path")
    private String cfaHeatmapPath;
    
    @Column(name = "cfa_interpolation_anomalies")
    private Integer cfaInterpolationAnomalies;
    
    // Copy-Move Detection Results
    @Column(name = "copymove_confidence_score")
    private Double copyMoveConfidenceScore;
    
    @Column(name = "copymove_result_path")
    private String copyMoveResultPath;
    
    @Column(name = "copymove_suspicious_blocks")
    private Integer copyMoveSuspiciousBlocks;
    
    // Lighting Analysis Results
    @Column(name = "lighting_confidence_score")
    private Double lightingConfidenceScore;
    
    @Column(name = "lighting_analysis_path")
    private String lightingAnalysisPath;
    
    @Column(name = "lighting_inconsistencies")
    private Integer lightingInconsistencies;
    
    // Noise Residual Analysis Results
    @Column(name = "noise_confidence_score")
    private Double noiseConfidenceScore;
    
    @Column(name = "noise_result_path")
    private String noiseResultPath;
    
    @Column(name = "noise_suspicious_regions")
    private Integer noiseSuspiciousRegions;
    
    // Overall Analysis Results
    @Column(name = "overall_confidence_score")
    private Double overallConfidenceScore;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "authenticity_assessment")
    private AuthenticityAssessment authenticityAssessment;
    
    @Column(name = "analysis_summary", columnDefinition = "TEXT")
    private String analysisSummary;
    
    @Column(name = "detailed_findings", columnDefinition = "TEXT")
    private String detailedFindings;
    
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;
    
    // Processing Metadata
    @Column(name = "processing_time_ms")
    private Long processingTimeMs;
    
    @Column(name = "image_width")
    private Integer imageWidth;
    
    @Column(name = "image_height")
    private Integer imageHeight;
    
    @Column(name = "file_size_bytes")
    private Long fileSizeBytes;
    
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
    
    // Analysis task relationship
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "analysis_task_id")
    private AnalysisTask analysisTask;
    
    public enum AnalysisStatus {
        PENDING,
        IN_PROGRESS,
        COMPLETED,
        FAILED,
        PARTIAL_SUCCESS
    }
    
    public enum AuthenticityAssessment {
        AUTHENTIC,
        LIKELY_AUTHENTIC,
        SUSPICIOUS,
        LIKELY_MANIPULATED,
        MANIPULATED,
        INCONCLUSIVE
    }
}
