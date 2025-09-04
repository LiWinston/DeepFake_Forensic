package com.itproject.traditional.controller;

import com.itproject.common.dto.Result;
import com.itproject.traditional.dto.TraditionalAnalysisRequest;
import com.itproject.traditional.dto.TraditionalAnalysisResponse;
import com.itproject.traditional.service.TraditionalAnalysisService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

/**
 * Controller for traditional forensic analysis operations
 */
@Slf4j
@RestController
@RequestMapping("/api/traditional-analysis")
@Tag(name = "Traditional Analysis", description = "Traditional forensic analysis operations")
public class TraditionalAnalysisController {
    
    @Autowired
    private TraditionalAnalysisService traditionalAnalysisService;
    
    /**
     * Get traditional analysis result by file MD5
     */
    @GetMapping("/result/{fileMd5}")
    @PreAuthorize("hasRole('USER')")
    @Operation(summary = "Get traditional analysis result", 
              description = "Retrieve traditional forensic analysis result for a specific file")
    public ResponseEntity<Result<TraditionalAnalysisResponse>> getAnalysisResult(
            @Parameter(description = "File MD5 hash") @PathVariable String fileMd5) {
        
        try {
            Optional<TraditionalAnalysisResponse> result = traditionalAnalysisService.getAnalysisResult(fileMd5);
            
            if (result.isPresent()) {
                return ResponseEntity.ok(Result.success(result.get()));
            } else {
                return ResponseEntity.ok(Result.error(404, "Traditional analysis result not found for file: " + fileMd5));
            }
            
        } catch (Exception e) {
            log.error("Error retrieving traditional analysis result for file: {}", fileMd5, e);
            return ResponseEntity.ok(Result.error(500, "Failed to retrieve analysis result: " + e.getMessage()));
        }
    }
    
    /**
     * Get traditional analysis results for a project with pagination
     */
    @GetMapping("/project/{projectId}")
    @PreAuthorize("hasRole('USER')")
    @Operation(summary = "Get project traditional analysis results", 
              description = "Retrieve all traditional analysis results for a specific project with pagination")
    public ResponseEntity<Result<Page<TraditionalAnalysisResponse>>> getProjectAnalysisResults(
            @Parameter(description = "Project ID") @PathVariable Long projectId,
            @Parameter(description = "Page number (0-based)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "10") int size) {
        
        try {
            Pageable pageable = PageRequest.of(page, size);
            Page<TraditionalAnalysisResponse> results = traditionalAnalysisService.getProjectAnalysisResults(projectId, pageable);
            
            return ResponseEntity.ok(Result.success(results));
            
        } catch (Exception e) {
            log.error("Error retrieving traditional analysis results for project: {}", projectId, e);
            return ResponseEntity.ok(Result.error(500, "Failed to retrieve analysis results: " + e.getMessage()));
        }
    }
    
    /**
     * Check analysis status for a file
     */
    @GetMapping("/status/{fileMd5}")
    @PreAuthorize("hasRole('USER')")
    @Operation(summary = "Check analysis status", 
              description = "Check the status of traditional analysis for a specific file")
    public ResponseEntity<Result<String>> getAnalysisStatus(
            @Parameter(description = "File MD5 hash") @PathVariable String fileMd5) {
        
        try {
            Optional<TraditionalAnalysisResponse> result = traditionalAnalysisService.getAnalysisResult(fileMd5);
            
            if (result.isPresent()) {
                String status = result.get().getAnalysisStatus().toString();
                return ResponseEntity.ok(Result.success(status));
            } else {
                return ResponseEntity.ok(Result.success("NOT_FOUND"));
            }
            
        } catch (Exception e) {
            log.error("Error checking traditional analysis status for file: {}", fileMd5, e);
            return ResponseEntity.ok(Result.error(500, "Failed to check analysis status: " + e.getMessage()));
        }
    }
    
    /**
     * Get analysis summary for a file (lightweight version)
     */
    @GetMapping("/summary/{fileMd5}")
    @PreAuthorize("hasRole('USER')")
    @Operation(summary = "Get analysis summary", 
              description = "Get a lightweight summary of traditional analysis results")
    public ResponseEntity<Result<AnalysisSummaryDto>> getAnalysisSummary(
            @Parameter(description = "File MD5 hash") @PathVariable String fileMd5) {
        
        try {
            Optional<TraditionalAnalysisResponse> result = traditionalAnalysisService.getAnalysisResult(fileMd5);
            
            if (result.isPresent()) {
                TraditionalAnalysisResponse analysis = result.get();
                
                AnalysisSummaryDto summary = new AnalysisSummaryDto();
                summary.setFileMd5(analysis.getFileMd5());
                summary.setAnalysisStatus(analysis.getAnalysisStatus().toString());
                summary.setOverallConfidenceScore(analysis.getOverallConfidenceScore());
                summary.setAuthenticityAssessment(analysis.getAuthenticityAssessment() != null ? 
                    analysis.getAuthenticityAssessment().toString() : null);
                summary.setAnalysisSummary(analysis.getAnalysisSummary());
                summary.setProcessingTimeMs(analysis.getProcessingTimeMs());
                summary.setCreatedAt(analysis.getCreatedAt());
                
                return ResponseEntity.ok(Result.success(summary));
            } else {
                return ResponseEntity.ok(Result.error(404, "Traditional analysis result not found for file: " + fileMd5));
            }
            
        } catch (Exception e) {
            log.error("Error retrieving traditional analysis summary for file: {}", fileMd5, e);
            return ResponseEntity.ok(Result.error(500, "Failed to retrieve analysis summary: " + e.getMessage()));
        }
    }
    
    /**
     * Get analysis comparison between multiple files
     */
    @PostMapping("/compare")
    @PreAuthorize("hasRole('USER')")
    @Operation(summary = "Compare analysis results", 
              description = "Compare traditional analysis results between multiple files")
    public ResponseEntity<Result<AnalysisComparisonDto>> compareAnalysisResults(
            @Parameter(description = "List of file MD5 hashes to compare") 
            @RequestBody CompareAnalysisRequest request) {
        
        try {
            if (request.getFileMd5List() == null || request.getFileMd5List().size() < 2) {
                return ResponseEntity.ok(Result.error(400, "At least 2 files required for comparison"));
            }
            
            if (request.getFileMd5List().size() > 10) {
                return ResponseEntity.ok(Result.error(400, "Maximum 10 files allowed for comparison"));
            }
            
            AnalysisComparisonDto comparison = new AnalysisComparisonDto();
            // Implementation for comparison logic would go here
            // For now, return a placeholder
            
            return ResponseEntity.ok(Result.success(comparison));
            
        } catch (Exception e) {
            log.error("Error comparing traditional analysis results", e);
            return ResponseEntity.ok(Result.error(500, "Failed to compare analysis results: " + e.getMessage()));
        }
    }
    
    /**
     * Data Transfer Objects for API responses
     */
    public static class AnalysisSummaryDto {
        private String fileMd5;
        private String analysisStatus;
        private Double overallConfidenceScore;
        private String authenticityAssessment;
        private String analysisSummary;
        private Long processingTimeMs;
        private java.time.LocalDateTime createdAt;
        
        // Getters and setters
        public String getFileMd5() { return fileMd5; }
        public void setFileMd5(String fileMd5) { this.fileMd5 = fileMd5; }
        
        public String getAnalysisStatus() { return analysisStatus; }
        public void setAnalysisStatus(String analysisStatus) { this.analysisStatus = analysisStatus; }
        
        public Double getOverallConfidenceScore() { return overallConfidenceScore; }
        public void setOverallConfidenceScore(Double overallConfidenceScore) { this.overallConfidenceScore = overallConfidenceScore; }
        
        public String getAuthenticityAssessment() { return authenticityAssessment; }
        public void setAuthenticityAssessment(String authenticityAssessment) { this.authenticityAssessment = authenticityAssessment; }
        
        public String getAnalysisSummary() { return analysisSummary; }
        public void setAnalysisSummary(String analysisSummary) { this.analysisSummary = analysisSummary; }
        
        public Long getProcessingTimeMs() { return processingTimeMs; }
        public void setProcessingTimeMs(Long processingTimeMs) { this.processingTimeMs = processingTimeMs; }
        
        public java.time.LocalDateTime getCreatedAt() { return createdAt; }
        public void setCreatedAt(java.time.LocalDateTime createdAt) { this.createdAt = createdAt; }
    }
    
    public static class CompareAnalysisRequest {
        private java.util.List<String> fileMd5List;
        
        public java.util.List<String> getFileMd5List() { return fileMd5List; }
        public void setFileMd5List(java.util.List<String> fileMd5List) { this.fileMd5List = fileMd5List; }
    }
    
    public static class AnalysisComparisonDto {
        private java.util.List<String> comparedFiles;
        private String comparisonSummary;
        private java.util.Map<String, Object> comparisonMetrics;
        
        public java.util.List<String> getComparedFiles() { return comparedFiles; }
        public void setComparedFiles(java.util.List<String> comparedFiles) { this.comparedFiles = comparedFiles; }
        
        public String getComparisonSummary() { return comparisonSummary; }
        public void setComparisonSummary(String comparisonSummary) { this.comparisonSummary = comparisonSummary; }
        
        public java.util.Map<String, Object> getComparisonMetrics() { return comparisonMetrics; }
        public void setComparisonMetrics(java.util.Map<String, Object> comparisonMetrics) { this.comparisonMetrics = comparisonMetrics; }
    }
}
