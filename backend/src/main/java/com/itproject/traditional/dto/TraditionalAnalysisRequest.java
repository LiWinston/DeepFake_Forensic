package com.itproject.traditional.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * DTO for requesting traditional analysis
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TraditionalAnalysisRequest {
    
    private String fileMd5;
    private Long projectId;
    private String analysisType; // ALL, ELA, CFA, COPY_MOVE, LIGHTING
    private AnalysisOptions options;
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AnalysisOptions {
        // ELA options
        private Integer elaQuality = 95; // JPEG quality for ELA (0-100)
        private Integer elaScale = 20;   // Scale factor for difference amplification
        
        // CFA options
        private String cfaMethod = "LAPLACIAN"; // LAPLACIAN, GRADIENT
        
        // Copy-Move options
        private Integer blockSize = 8;           // Block size for copy-move detection
        private Double similarityThreshold = 10.0; // Similarity threshold
        
        // Lighting options
        private Boolean enableLightingAnalysis = true;
        private Integer lightingSensitivity = 5; // 1-10 scale
    }
}
