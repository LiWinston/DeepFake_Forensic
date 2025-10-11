package com.itproject.traditional.entity;

import com.itproject.analysis.entity.AnalysisTask;
import com.itproject.auth.entity.User;
import com.itproject.project.entity.Project;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "video_traditional_analysis_results")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class VideoTraditionalAnalysisResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 32)
    private String fileMd5;

    @Column(nullable = false, length = 32)
    private String method; // NOISE/FLOW/FREQ/TEMPORAL/COPYMOVE

    @Column(name = "artifacts_json", columnDefinition = "MEDIUMTEXT")
    private String artifactsJson; // JSON map of artifactName -> URL

    @Column(name = "result_json", columnDefinition = "MEDIUMTEXT")
    private String resultJson; // Raw metrics JSON

    @Column(name = "success")
    private Boolean success;

    @Column(name = "error_message", columnDefinition = "MEDIUMTEXT")
    private String errorMessage;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "analysis_task_id")
    private AnalysisTask analysisTask;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id")
    private Project project;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
