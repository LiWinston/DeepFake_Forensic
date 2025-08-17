package com.itproject.metadata.controller;

import com.itproject.metadata.dto.MetadataAnalysisResponse;
import com.itproject.metadata.service.MetadataAnalysisService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Controller for metadata analysis operations
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/metadata")
@CrossOrigin(origins = "*", maxAge = 3600)
public class MetadataController {
    
    @Autowired
    private MetadataAnalysisService metadataAnalysisService;
    
    /**
     * Get metadata analysis for a file
     */
    @GetMapping("/analysis/{fileMd5}")
    public ResponseEntity<MetadataAnalysisResponse> getMetadataAnalysis(@PathVariable String fileMd5) {
        try {
            log.info("Retrieving metadata analysis for file: {}", fileMd5);
            
            MetadataAnalysisResponse response = metadataAnalysisService.getMetadataAnalysis(fileMd5);
            
            if (response.isSuccess()) {
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.notFound().build();
            }
            
        } catch (Exception e) {
            log.error("Error retrieving metadata analysis for file: {}", fileMd5, e);
            MetadataAnalysisResponse errorResponse = MetadataAnalysisResponse.error(
                "Failed to retrieve metadata analysis: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }
    
    /**
     * Get basic metadata information
     */
    @GetMapping("/basic/{fileMd5}")
    public ResponseEntity<Map<String, Object>> getBasicMetadata(@PathVariable String fileMd5) {
        try {
            MetadataAnalysisResponse analysis = metadataAnalysisService.getMetadataAnalysis(fileMd5);
            
            if (analysis.isSuccess()) {
                Map<String, Object> basicInfo = new HashMap<>();
                basicInfo.put("fileMd5", fileMd5);
                basicInfo.put("extractionStatus", analysis.getExtractionStatus());
                basicInfo.put("basicMetadata", analysis.getBasicMetadata());
                basicInfo.put("hashValues", analysis.getHashValues());
                basicInfo.put("analysisTime", analysis.getAnalysisTime());
                
                return ResponseEntity.ok(basicInfo);
            } else {
                return ResponseEntity.notFound().build();
            }
            
        } catch (Exception e) {
            log.error("Error retrieving basic metadata for file: {}", fileMd5, e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "Failed to retrieve basic metadata: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }
    
    /**
     * Get EXIF data for image files
     */
    @GetMapping("/exif/{fileMd5}")
    public ResponseEntity<Map<String, Object>> getExifData(@PathVariable String fileMd5) {
        try {
            MetadataAnalysisResponse analysis = metadataAnalysisService.getMetadataAnalysis(fileMd5);
            
            if (analysis.isSuccess()) {
                Map<String, Object> exifInfo = new HashMap<>();
                exifInfo.put("fileMd5", fileMd5);
                exifInfo.put("exifData", analysis.getExifData());
                
                return ResponseEntity.ok(exifInfo);
            } else {
                return ResponseEntity.notFound().build();
            }
            
        } catch (Exception e) {
            log.error("Error retrieving EXIF data for file: {}", fileMd5, e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "Failed to retrieve EXIF data: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }
    
    /**
     * Get video metadata
     */
    @GetMapping("/video/{fileMd5}")
    public ResponseEntity<Map<String, Object>> getVideoMetadata(@PathVariable String fileMd5) {
        try {
            MetadataAnalysisResponse analysis = metadataAnalysisService.getMetadataAnalysis(fileMd5);
            
            if (analysis.isSuccess()) {
                Map<String, Object> videoInfo = new HashMap<>();
                videoInfo.put("fileMd5", fileMd5);
                videoInfo.put("videoMetadata", analysis.getVideoMetadata());
                
                return ResponseEntity.ok(videoInfo);
            } else {
                return ResponseEntity.notFound().build();
            }
            
        } catch (Exception e) {
            log.error("Error retrieving video metadata for file: {}", fileMd5, e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "Failed to retrieve video metadata: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }
    
    /**
     * Get suspicious indicators from forensic analysis
     */
    @GetMapping("/suspicious/{fileMd5}")
    public ResponseEntity<Map<String, Object>> getSuspiciousIndicators(@PathVariable String fileMd5) {
        try {
            MetadataAnalysisResponse analysis = metadataAnalysisService.getMetadataAnalysis(fileMd5);
            
            if (analysis.isSuccess()) {
                Map<String, Object> suspiciousInfo = new HashMap<>();
                suspiciousInfo.put("fileMd5", fileMd5);
                suspiciousInfo.put("suspiciousIndicators", analysis.getSuspiciousIndicators());
                
                return ResponseEntity.ok(suspiciousInfo);
            } else {
                return ResponseEntity.notFound().build();
            }
            
        } catch (Exception e) {
            log.error("Error retrieving suspicious indicators for file: {}", fileMd5, e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "Failed to retrieve suspicious indicators: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }
    
    /**
     * Get hash verification information
     */
    @GetMapping("/hashes/{fileMd5}")
    public ResponseEntity<Map<String, Object>> getHashVerification(@PathVariable String fileMd5) {
        try {
            MetadataAnalysisResponse analysis = metadataAnalysisService.getMetadataAnalysis(fileMd5);
            
            if (analysis.isSuccess()) {
                Map<String, Object> hashInfo = new HashMap<>();
                hashInfo.put("fileMd5", fileMd5);
                hashInfo.put("hashValues", analysis.getHashValues());
                hashInfo.put("extractionStatus", analysis.getExtractionStatus());
                
                return ResponseEntity.ok(hashInfo);
            } else {
                return ResponseEntity.notFound().build();
            }
            
        } catch (Exception e) {
            log.error("Error retrieving hash verification for file: {}", fileMd5, e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "Failed to retrieve hash verification: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }
}
