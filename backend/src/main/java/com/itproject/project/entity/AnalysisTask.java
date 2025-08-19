package com.itproject.project.entity;

import com.itproject.auth.entity.User;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Analysis task entity for representing different types of analysis performed on media files
 */
@Entity
@Table(name = "analysis_tasks")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AnalysisTask {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, length = 255)
    private String taskName;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AnalysisType analysisType;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TaskStatus status;
    
    @Column(length = 1000)
    private String description;
    
    @Column(columnDefinition = "TEXT")
    private String resultData;
    
    @Column
    private Double confidenceScore;
    
    @Column(length = 2000)
    private String notes;
    
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
    
    // Project relationship
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;
    
    // User relationship - task assigned to
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    
    public enum AnalysisType {
        METADATA_ANALYSIS,       // 元数据分析
        DEEPFAKE_DETECTION,      // 深度伪造检测
        EDIT_DETECTION,          // 编辑痕迹检测
        COMPRESSION_ANALYSIS,    // 压缩分析
        HASH_VERIFICATION,       // 哈希验证
        EXIF_ANALYSIS,          // EXIF数据分析
        STEGANOGRAPHY_DETECTION, // 隐写术检测
        SIMILARITY_ANALYSIS,     // 相似性分析
        TEMPORAL_ANALYSIS,       // 时间序列分析
        QUALITY_ASSESSMENT       // 质量评估
    }
    
    public enum TaskStatus {
        PENDING,      // 等待中
        RUNNING,      // 运行中
        COMPLETED,    // 已完成
        FAILED,       // 失败
        CANCELLED,    // 已取消
        PAUSED        // 已暂停
    }
}
