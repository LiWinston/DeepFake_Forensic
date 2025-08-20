package com.itproject.metadata.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * DTO for metadata analysis response
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MetadataAnalysisResponse {
    
    private boolean success;
    private String message;
    private String fileMd5;
    private String extractionStatus;
    private Map<String, Object> basicMetadata;
    private Map<String, Object> exifData;
    private Map<String, Object> videoMetadata;
    private Map<String, Object> hashValues;
    private Map<String, Object> suspiciousIndicators;
    private Map<String, Object> parsedMetadata;
    private LocalDateTime analysisTime;
    
    public static MetadataAnalysisResponse success(String message) {
        MetadataAnalysisResponse response = new MetadataAnalysisResponse();
        response.success = true;
        response.message = message;
        return response;
    }
    
    public static MetadataAnalysisResponse error(String message) {
        MetadataAnalysisResponse response = new MetadataAnalysisResponse();
        response.success = false;
        response.message = message;
        return response;
    }
}
