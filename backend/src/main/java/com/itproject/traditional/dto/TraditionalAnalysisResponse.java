package com.itproject.traditional.dto;

import com.itproject.traditional.entity.TraditionalAnalysisResult;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO for traditional forensic analysis response
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TraditionalAnalysisResponse {
    
    private Long id;
    private String fileMd5;
    private String originalFilePath;
    
    // Analysis Status
    private TraditionalAnalysisResult.AnalysisStatus analysisStatus;
    
    // Analysis Results
    private ElaAnalysisResult elaAnalysis;
    private CfaAnalysisResult cfaAnalysis;
    private CopyMoveAnalysisResult copyMoveAnalysis;
    private LightingAnalysisResult lightingAnalysis;
    
    // Overall Results
    private Double overallConfidenceScore;
    private TraditionalAnalysisResult.AuthenticityAssessment authenticityAssessment;
    private String analysisSummary;
    private String detailedFindings;
    private String errorMessage;
    
    // Metadata
    private Long processingTimeMs;
    private Integer imageWidth;
    private Integer imageHeight;
    private Long fileSizeBytes;
    
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ElaAnalysisResult {
        private Double confidenceScore;
        private String resultImageUrl;
        private Integer suspiciousRegions;
        private String analysis;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CfaAnalysisResult {
        private Double confidenceScore;
        private String heatmapImageUrl;
        private Integer interpolationAnomalies;
        private String analysis;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CopyMoveAnalysisResult {
        private Double confidenceScore;
        private String resultImageUrl;
        private Integer suspiciousBlocks;
        private String analysis;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LightingAnalysisResult {
        private Double confidenceScore;
        private String analysisImageUrl;
        private Integer inconsistencies;
        private String analysis;
    }
}
