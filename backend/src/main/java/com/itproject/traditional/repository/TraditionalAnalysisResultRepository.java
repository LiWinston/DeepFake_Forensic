package com.itproject.traditional.repository;

import com.itproject.traditional.entity.TraditionalAnalysisResult;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository for TraditionalAnalysisResult entities
 */
@Repository
public interface TraditionalAnalysisResultRepository extends JpaRepository<TraditionalAnalysisResult, Long> {
    
    /**
     * Find analysis result by file MD5 hash
     */
    Optional<TraditionalAnalysisResult> findByFileMd5(String fileMd5);
    
    /**
     * Find all analysis results for a specific project
     */
    Page<TraditionalAnalysisResult> findByProjectIdOrderByCreatedAtDesc(Long projectId, Pageable pageable);
    
    /**
     * Find all analysis results for a specific user
     */
    Page<TraditionalAnalysisResult> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);
    
    /**
     * Find all analysis results for a project and user
     */
    Page<TraditionalAnalysisResult> findByProjectIdAndUserIdOrderByCreatedAtDesc(
            Long projectId, Long userId, Pageable pageable);
    
    /**
     * Find analysis results by status
     */
    List<TraditionalAnalysisResult> findByAnalysisStatus(TraditionalAnalysisResult.AnalysisStatus status);
    
    /**
     * Find analysis results by authenticity assessment
     */
    Page<TraditionalAnalysisResult> findByAuthenticityAssessmentOrderByCreatedAtDesc(
            TraditionalAnalysisResult.AuthenticityAssessment assessment, Pageable pageable);
    
    /**
     * Find analysis results created after a specific date
     */
    List<TraditionalAnalysisResult> findByCreatedAtAfter(LocalDateTime datetime);
    
    /**
     * Count analysis results by status for a user
     */
    @Query("SELECT COUNT(tar) FROM TraditionalAnalysisResult tar WHERE tar.user.id = :userId AND tar.analysisStatus = :status")
    long countByUserIdAndStatus(@Param("userId") Long userId, @Param("status") TraditionalAnalysisResult.AnalysisStatus status);
    
    /**
     * Find analysis results with high confidence scores (potential manipulations)
     */
    @Query("SELECT tar FROM TraditionalAnalysisResult tar WHERE tar.overallConfidenceScore > :threshold ORDER BY tar.overallConfidenceScore DESC")
    List<TraditionalAnalysisResult> findHighConfidenceResults(@Param("threshold") Double threshold);
    
    /**
     * Check if analysis exists for a specific file
     */
    boolean existsByFileMd5(String fileMd5);
    
    /**
     * Get analysis statistics for a project
     */
    @Query("SELECT " +
           "COUNT(tar) as totalAnalyses, " +
           "COUNT(CASE WHEN tar.authenticityAssessment = 'AUTHENTIC' OR tar.authenticityAssessment = 'LIKELY_AUTHENTIC' THEN 1 END) as authenticCount, " +
           "COUNT(CASE WHEN tar.authenticityAssessment = 'MANIPULATED' OR tar.authenticityAssessment = 'LIKELY_MANIPULATED' THEN 1 END) as manipulatedCount, " +
           "COUNT(CASE WHEN tar.authenticityAssessment = 'SUSPICIOUS' THEN 1 END) as suspiciousCount, " +
           "AVG(tar.overallConfidenceScore) as avgConfidenceScore " +
           "FROM TraditionalAnalysisResult tar WHERE tar.project.id = :projectId")
    Object[] getProjectAnalysisStatistics(@Param("projectId") Long projectId);
}
