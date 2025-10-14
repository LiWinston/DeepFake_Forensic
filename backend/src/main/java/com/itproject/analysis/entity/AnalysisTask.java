package com.itproject.analysis.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.itproject.auth.entity.User;
import com.itproject.project.entity.Project;
import com.itproject.upload.entity.MediaFile;
import jakarta.persistence.*;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.ConstraintMode;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Analysis Task entity representing different types of analysis performed on media files
 */
@Entity
@Table(name = "analysis_tasks")
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class AnalysisTask {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, length = 255)
    private String taskName; // 任务名称
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AnalysisType analysisType;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AnalysisStatus status = AnalysisStatus.PENDING;
    
    @Column(length = 1000)
    private String description;
    
    @Column(columnDefinition = "LONGTEXT")
    private String parameters; // JSON format for analysis parameters
    
    @Column(columnDefinition = "LONGTEXT")
    private String results; // JSON format for analysis results
    
    @Column(columnDefinition = "TEXT")
    private String notes; // 备注信息
    
    @Column
    private Double confidenceScore; // 置信度分数
    
    @Column(columnDefinition = "LONGTEXT")
    private String errorMessage;
    
    @Column
    private Integer priority = 5; // 1-10, 1 is highest priority
    
    @Column
    private Double progress = 0.0; // 0.0 - 100.0
    
    @Column
    private Long estimatedDuration; // seconds
    
    @Column
    private Long actualDuration; // seconds
    
    @Column
    private LocalDateTime startedAt;
    
    @Column
    private LocalDateTime completedAt;
    
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    
    // Relationships
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false, foreignKey = @ForeignKey(value = ConstraintMode.NO_CONSTRAINT))
    private Project project;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "media_file_id", nullable = false, foreignKey = @ForeignKey(value = ConstraintMode.NO_CONSTRAINT))
    private MediaFile mediaFile;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(value = ConstraintMode.NO_CONSTRAINT))
    private User user;
    
    public enum AnalysisType {
        METADATA_ANALYSIS,        // 元数据分析
        DEEPFAKE_DETECTION,       // AI生成检测
        EDIT_DETECTION,           // 编辑痕迹检测
        AUTHENTICITY_VERIFICATION, // 真实性验证
        COMPRESSION_ANALYSIS,     // 压缩分析
        NOISE_ANALYSIS,          // 噪声分析
        GEOMETRIC_ANALYSIS,      // 几何分析
        LIGHTING_ANALYSIS,       // 光照分析
        SHADOW_ANALYSIS,         // 阴影分析
        PIXEL_LEVEL_ANALYSIS,    // 像素级分析
        FREQUENCY_DOMAIN_ANALYSIS, // 频域分析
        BLOCKCHAIN_VERIFICATION,  // 区块链验证
        COMPREHENSIVE_REPORT     // 综合报告
    }
    
    public enum AnalysisStatus {
        PENDING,      // 等待中
        QUEUED,       // 已排队
        RUNNING,      // 执行中
        COMPLETED,    // 已完成
        FAILED,       // 失败
        CANCELLED,    // 已取消
        PAUSED        // 已暂停
    }
}
