package com.itproject.metadata.controller;

import com.itproject.metadata.dto.MetadataAnalysisResponse;
import com.itproject.metadata.service.MetadataAnalysisService;
import com.itproject.upload.entity.MediaFile;
import com.itproject.upload.repository.MediaFileRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Controller for metadata analysis operations
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/metadata")
@CrossOrigin(origins = "*", maxAge = 3600)
//@PreAuthorize("hasRole('USER')")
public class MetadataController {
    
    @Autowired
    private MetadataAnalysisService metadataAnalysisService;
    
    @Autowired
    private MediaFileRepository mediaFileRepository;
    
    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;
    
    @Autowired
    private String metadataAnalysisTopic;
    
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
    
    /**
     * Start metadata analysis for a file
     */
    @PostMapping("/analysis/{fileMd5}/start")
    public ResponseEntity<Map<String, Object>> startAnalysis(@PathVariable String fileMd5) {
        try {
            log.info("Starting metadata analysis for file: {}", fileMd5);
            
            // Check if file exists and is completed
            Optional<MediaFile> mediaFileOpt = mediaFileRepository.findByFileMd5(fileMd5);
            if (mediaFileOpt.isEmpty()) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("message", "文件不存在: " + fileMd5);
                return ResponseEntity.notFound().build();
            }
            
            MediaFile mediaFile = mediaFileOpt.get();
            if (mediaFile.getUploadStatus() != MediaFile.UploadStatus.COMPLETED) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("message", "文件上传未完成，无法进行分析");
                return ResponseEntity.badRequest().body(errorResponse);
            }
            
            // Send Kafka message to start analysis
            Map<String, Object> analysisMessage = new HashMap<>();
            analysisMessage.put("fileMd5", mediaFile.getFileMd5());
            analysisMessage.put("fileName", mediaFile.getOriginalFileName());
            analysisMessage.put("fileType", mediaFile.getMediaType().name());
            analysisMessage.put("filePath", mediaFile.getFilePath());
            analysisMessage.put("userId", mediaFile.getUser().getId()); // 添加用户ID
            analysisMessage.put("projectId", mediaFile.getProject().getId()); // 添加项目ID
            analysisMessage.put("forceReAnalysis", true); // 强制重新分析
            
            kafkaTemplate.send(metadataAnalysisTopic, mediaFile.getFileMd5(), analysisMessage);
            
            log.info("Metadata analysis task sent to Kafka for file: {}", fileMd5);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "元数据分析已启动");
            response.put("fileMd5", fileMd5);
            response.put("status", "PROCESSING");
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error starting metadata analysis for file: {}", fileMd5, e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "启动分析失败: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }
    
    /**
     * Get analysis status for a file
     */
    @GetMapping("/analysis/{fileMd5}/status")
    public ResponseEntity<Map<String, Object>> getAnalysisStatus(@PathVariable String fileMd5) {
        try {
            log.info("Getting analysis status for file: {}", fileMd5);
            
            // Check if analysis exists
            MetadataAnalysisResponse analysis = metadataAnalysisService.getMetadataAnalysis(fileMd5);
            
            Map<String, Object> response = new HashMap<>();
            if (analysis.isSuccess()) {
                response.put("hasAnalysis", true);
                response.put("status", analysis.getExtractionStatus());
                response.put("analysisTime", analysis.getAnalysisTime());
                response.put("message", analysis.getMessage());
            } else {
                response.put("hasAnalysis", false);
                response.put("status", "NOT_FOUND");
                response.put("message", "暂无分析结果");
            }
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error getting analysis status for file: {}", fileMd5, e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("hasAnalysis", false);
            errorResponse.put("status", "ERROR");
            errorResponse.put("message", "获取状态失败: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }
    
    /**
     * Fix existing metadata records by re-extracting from raw metadata
     */
    @PostMapping("/fix/{fileMd5}")
    public ResponseEntity<Map<String, Object>> fixMetadata(@PathVariable String fileMd5) {
        try {
            log.info("Attempting to fix metadata for file: {}", fileMd5);
            metadataAnalysisService.fixExistingMetadata(fileMd5);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Metadata fixed successfully for file: " + fileMd5);
            response.put("fileMd5", fileMd5);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error fixing metadata for file: {}", fileMd5, e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "修复元数据失败: " + e.getMessage());
            response.put("fileMd5", fileMd5);
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
}
