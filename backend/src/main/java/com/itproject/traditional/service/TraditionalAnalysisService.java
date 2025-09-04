package com.itproject.traditional.service;

import com.itproject.auth.entity.User;
import com.itproject.auth.repository.UserRepository;
import com.itproject.auth.security.SecurityUtils;
import com.itproject.project.entity.Project;
import com.itproject.project.repository.ProjectRepository;
import com.itproject.upload.entity.MediaFile;
import com.itproject.upload.repository.MediaFileRepository;
import com.itproject.traditional.dto.TraditionalAnalysisResponse;
import com.itproject.traditional.dto.TraditionalAnalysisTaskDto;
import com.itproject.traditional.entity.TraditionalAnalysisResult;
import com.itproject.traditional.repository.TraditionalAnalysisResultRepository;
import com.itproject.traditional.util.CFAAnalysisUtil;
import com.itproject.traditional.util.CopyMoveDetectionUtil;
import com.itproject.traditional.util.ErrorLevelAnalysisUtil;
import com.itproject.traditional.util.LightingAnalysisUtil;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.Optional;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;

/**
 * Service for traditional forensic analysis
 */
@Slf4j
@Service
public class TraditionalAnalysisService {
    
    @Autowired
    private TraditionalAnalysisResultRepository analysisRepository;
    
    @Autowired
    private MediaFileRepository mediaFileRepository;
    
    @Autowired
    private ProjectRepository projectRepository;
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private MinioClient minioClient;
    
    @Autowired
    private ErrorLevelAnalysisUtil elaUtil;
    
    @Autowired
    private CFAAnalysisUtil cfaUtil;
    
    @Autowired
    private CopyMoveDetectionUtil copyMoveUtil;
    
    @Autowired
    private LightingAnalysisUtil lightingUtil;
    
    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;
    
    @Value("${minio.bucket-name}")
    private String bucketName;
    
    @Value("${minio.traditional-analysis-folder:traditional-analysis}")
    private String traditionalAnalysisFolder;
    
    @Value("${kafka.topics.traditional-analysis:traditional-analysis-tasks}")
    private String traditionalAnalysisTopic;
    
    /**
     * Manually trigger traditional analysis for a file
     */
    public boolean triggerTraditionalAnalysis(String fileMd5) {
        return triggerTraditionalAnalysis(fileMd5, false);
    }
    
    /**
     * Manually trigger traditional analysis for a file with force option
     */
    public boolean triggerTraditionalAnalysis(String fileMd5, boolean force) {
        try {
            // Check if file exists
            Optional<MediaFile> mediaFileOpt = mediaFileRepository.findByFileMd5(fileMd5);
            if (mediaFileOpt.isEmpty()) {
                log.error("Media file not found for MD5: {}", fileMd5);
                return false;
            }
            
            // Create task DTO
            TraditionalAnalysisTaskDto task = new TraditionalAnalysisTaskDto(fileMd5, force);
            
            // Send Kafka message to trigger analysis
            kafkaTemplate.send(traditionalAnalysisTopic, task);
            log.info("Traditional analysis task triggered for file: {} (force: {})", fileMd5, force);
            return true;
            
        } catch (Exception e) {
            log.error("Error triggering traditional analysis for file: {}", fileMd5, e);
            return false;
        }
    }
    
    /**
     * Kafka listener for traditional analysis tasks
     */
    @KafkaListener(topics = "traditional-analysis-tasks", groupId = "traditional-analysis-group")
    @Transactional
    public void processTraditionalAnalysisTask(TraditionalAnalysisTaskDto task) {
        String fileMd5 = null;
        try {
            log.info("Received traditional analysis task: {} (force: {})", task.getFileMd5(), task.isForce());
            
            fileMd5 = task.getFileMd5();
            if (fileMd5 == null || fileMd5.trim().isEmpty()) {
                log.error("Invalid task: fileMd5 is null or empty");
                return;
            }
            
            // Find media file
            Optional<MediaFile> mediaFileOpt = mediaFileRepository.findByFileMd5(fileMd5);
            if (mediaFileOpt.isEmpty()) {
                log.error("Media file not found for MD5: {}", fileMd5);
                // Create a failed record to prevent reprocessing
                createFailedAnalysisRecord(fileMd5, "Media file not found");
                return;
            }
            
            MediaFile mediaFile = mediaFileOpt.get();
            
            // Check if analysis already exists (skip check if force is true)
            if (!task.isForce() && analysisRepository.existsByFileMd5(fileMd5)) {
                log.info("Traditional analysis already exists for file: {} (use force=true to re-analyze)", fileMd5);
                return;
            }
            
            // If force is true, delete existing analysis
            if (task.isForce()) {
                Optional<TraditionalAnalysisResult> existingAnalysis = analysisRepository.findByFileMd5(fileMd5);
                if (existingAnalysis.isPresent()) {
                    analysisRepository.delete(existingAnalysis.get());
                    log.info("Deleted existing analysis for file: {} due to force re-analysis", fileMd5);
                }
            }
            
            // Perform analysis
            performTraditionalAnalysis(mediaFile);
            
        } catch (Exception e) {
            log.error("Error processing traditional analysis task: {}", task, e);
            // Create failed record to prevent infinite retries
            if (fileMd5 != null) {
                try {
                    createFailedAnalysisRecord(fileMd5, "Processing error: " + e.getMessage());
                } catch (Exception saveException) {
                    log.error("Failed to save error record for file: {}", fileMd5, saveException);
                }
            }
        }
    }
    
    /**
     * Perform comprehensive traditional analysis
     */
    @Transactional
    public void performTraditionalAnalysis(MediaFile mediaFile) {
        long startTime = System.currentTimeMillis();
        
        TraditionalAnalysisResult result = new TraditionalAnalysisResult();
        result.setFileMd5(mediaFile.getFileMd5());
        result.setOriginalFilePath(mediaFile.getFilePath());
        result.setAnalysisStatus(TraditionalAnalysisResult.AnalysisStatus.IN_PROGRESS);
        result.setUser(mediaFile.getUser());
        result.setProject(mediaFile.getProject());
        result.setFileSizeBytes(mediaFile.getFileSize());
        
        try {
            // Download image data once and store in memory
            InputStream imageStream = downloadImageFromMinio(mediaFile.getFilePath());
            byte[] imageData = imageStream.readAllBytes();
            imageStream.close();
            
            // Detect image dimensions from the data
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(imageData));
            if (image != null) {
                result.setImageWidth(image.getWidth());
                result.setImageHeight(image.getHeight());
                log.debug("Detected image dimensions: {}x{} for file: {}", 
                         image.getWidth(), image.getHeight(), mediaFile.getFileMd5());
            } else {
                log.warn("Could not read image dimensions for file: {}", mediaFile.getFileMd5());
                result.setImageWidth(0);
                result.setImageHeight(0);
            }
            
            // Save initial record
            result = analysisRepository.save(result);
            
            // Perform ELA Analysis
            performELAAnalysis(result, new ByteArrayInputStream(imageData));
            
            // Perform CFA Analysis
            performCFAAnalysis(result, new ByteArrayInputStream(imageData));
            
            // Perform Copy-Move Detection
            performCopyMoveAnalysis(result, new ByteArrayInputStream(imageData));
            
            // Perform Lighting Analysis
            performLightingAnalysis(result, new ByteArrayInputStream(imageData));
            
            // Calculate overall results
            calculateOverallResults(result);
            
            // Update processing time and status
            result.setProcessingTimeMs(System.currentTimeMillis() - startTime);
            result.setAnalysisStatus(TraditionalAnalysisResult.AnalysisStatus.COMPLETED);
            
            log.info("Traditional analysis completed for file: {} in {}ms", 
                    mediaFile.getFileMd5(), result.getProcessingTimeMs());
            
        } catch (Exception e) {
            log.error("Error during traditional analysis for file: {}", mediaFile.getFileMd5(), e);
            result.setAnalysisStatus(TraditionalAnalysisResult.AnalysisStatus.FAILED);
            result.setErrorMessage(e.getMessage());
            result.setProcessingTimeMs(System.currentTimeMillis() - startTime);
        } finally {
            analysisRepository.save(result);
        }
    }
    
    /**
     * Perform Error Level Analysis
     */
    private void performELAAnalysis(TraditionalAnalysisResult result, InputStream imageStream) {
        try {
            ErrorLevelAnalysisUtil.ElaResult elaResult = elaUtil.performELA(imageStream, 95, 20);
            
            // Upload result image to MinIO
            String elaImagePath = uploadAnalysisResult(
                result.getFileMd5(), "ela", "png", elaResult.getResultImageData());
            
            result.setElaResultPath(elaImagePath);
            result.setElaConfidenceScore(elaResult.getConfidenceScore());
            result.setElaSuspiciousRegions(elaResult.getSuspiciousRegions());
            
            log.debug("ELA analysis completed for file: {}, confidence: {}", 
                    result.getFileMd5(), elaResult.getConfidenceScore());
            
        } catch (Exception e) {
            log.error("Error in ELA analysis for file: {}", result.getFileMd5(), e);
            result.setElaConfidenceScore(0.0);
        }
    }
    
    /**
     * Perform CFA Analysis
     */
    private void performCFAAnalysis(TraditionalAnalysisResult result, InputStream imageStream) {
        try {
            CFAAnalysisUtil.CfaResult cfaResult = cfaUtil.performCFA(imageStream, "LAPLACIAN");
            
            // Upload heatmap image to MinIO
            String cfaImagePath = uploadAnalysisResult(
                result.getFileMd5(), "cfa", "png", cfaResult.getHeatmapData());
            
            result.setCfaHeatmapPath(cfaImagePath);
            result.setCfaConfidenceScore(cfaResult.getConfidenceScore());
            result.setCfaInterpolationAnomalies(cfaResult.getInterpolationAnomalies());
            
            log.debug("CFA analysis completed for file: {}, confidence: {}", 
                    result.getFileMd5(), cfaResult.getConfidenceScore());
            
        } catch (Exception e) {
            log.error("Error in CFA analysis for file: {}", result.getFileMd5(), e);
            result.setCfaConfidenceScore(0.0);
        }
    }
    
    /**
     * Perform Copy-Move Detection
     */
    private void performCopyMoveAnalysis(TraditionalAnalysisResult result, InputStream imageStream) {
        try {
            CopyMoveDetectionUtil.CopyMoveResult copyMoveResult = copyMoveUtil.performCopyMoveDetection(
                imageStream, 8, 10.0);
            
            // Upload result image to MinIO
            String copyMoveImagePath = uploadAnalysisResult(
                result.getFileMd5(), "copymove", "png", copyMoveResult.getResultImageData());
            
            result.setCopyMoveResultPath(copyMoveImagePath);
            result.setCopyMoveConfidenceScore(copyMoveResult.getConfidenceScore());
            result.setCopyMoveSuspiciousBlocks(copyMoveResult.getSuspiciousBlocks());
            
            log.debug("Copy-Move analysis completed for file: {}, confidence: {}", 
                    result.getFileMd5(), copyMoveResult.getConfidenceScore());
            
        } catch (Exception e) {
            log.error("Error in Copy-Move analysis for file: {}", result.getFileMd5(), e);
            result.setCopyMoveConfidenceScore(0.0);
        }
    }
    
    /**
     * Perform Lighting Analysis
     */
    private void performLightingAnalysis(TraditionalAnalysisResult result, InputStream imageStream) {
        try {
            LightingAnalysisUtil.LightingResult lightingResult = lightingUtil.performLightingAnalysis(
                imageStream, 5);
            
            // Upload analysis image to MinIO
            String lightingImagePath = uploadAnalysisResult(
                result.getFileMd5(), "lighting", "png", lightingResult.getAnalysisImageData());
            
            result.setLightingAnalysisPath(lightingImagePath);
            result.setLightingConfidenceScore(lightingResult.getConfidenceScore());
            result.setLightingInconsistencies(lightingResult.getInconsistencies());
            
            log.debug("Lighting analysis completed for file: {}, confidence: {}", 
                    result.getFileMd5(), lightingResult.getConfidenceScore());
            
        } catch (Exception e) {
            log.error("Error in Lighting analysis for file: {}", result.getFileMd5(), e);
            result.setLightingConfidenceScore(0.0);
        }
    }
    
    /**
     * Calculate overall analysis results
     */
    private void calculateOverallResults(TraditionalAnalysisResult result) {
        // Calculate weighted average confidence score
        double elaScore = result.getElaConfidenceScore() != null ? result.getElaConfidenceScore() : 0.0;
        double cfaScore = result.getCfaConfidenceScore() != null ? result.getCfaConfidenceScore() : 0.0;
        double copyMoveScore = result.getCopyMoveConfidenceScore() != null ? result.getCopyMoveConfidenceScore() : 0.0;
        double lightingScore = result.getLightingConfidenceScore() != null ? result.getLightingConfidenceScore() : 0.0;
        
        // Weighted average (ELA and Copy-Move are more reliable indicators)
        double overallScore = (elaScore * 0.35 + cfaScore * 0.25 + copyMoveScore * 0.30 + lightingScore * 0.10);
        result.setOverallConfidenceScore(overallScore);
        
        // Determine authenticity assessment
        TraditionalAnalysisResult.AuthenticityAssessment assessment;
        if (overallScore < 15) {
            assessment = TraditionalAnalysisResult.AuthenticityAssessment.AUTHENTIC;
        } else if (overallScore < 30) {
            assessment = TraditionalAnalysisResult.AuthenticityAssessment.LIKELY_AUTHENTIC;
        } else if (overallScore < 50) {
            assessment = TraditionalAnalysisResult.AuthenticityAssessment.SUSPICIOUS;
        } else if (overallScore < 75) {
            assessment = TraditionalAnalysisResult.AuthenticityAssessment.LIKELY_MANIPULATED;
        } else {
            assessment = TraditionalAnalysisResult.AuthenticityAssessment.MANIPULATED;
        }
        
        result.setAuthenticityAssessment(assessment);
        
        // Generate analysis summary
        StringBuilder summary = new StringBuilder();
        summary.append("Traditional Forensic Analysis Summary:\n");
        summary.append(String.format("Overall Confidence Score: %.2f/100\n", overallScore));
        summary.append(String.format("Authenticity Assessment: %s\n\n", assessment));
        summary.append(String.format("Individual Analysis Scores:\n"));
        summary.append(String.format("- Error Level Analysis: %.2f/100\n", elaScore));
        summary.append(String.format("- CFA Pattern Analysis: %.2f/100\n", cfaScore));
        summary.append(String.format("- Copy-Move Detection: %.2f/100\n", copyMoveScore));
        summary.append(String.format("- Lighting Consistency: %.2f/100\n", lightingScore));
        
        result.setAnalysisSummary(summary.toString());
        
        // Generate detailed findings
        StringBuilder findings = new StringBuilder();
        findings.append("Detailed Analysis Findings:\n\n");
        
        if (result.getElaSuspiciousRegions() != null && result.getElaSuspiciousRegions() > 0) {
            findings.append(String.format("ELA: %d suspicious regions detected with compression artifacts\n", 
                          result.getElaSuspiciousRegions()));
        }
        
        if (result.getCfaInterpolationAnomalies() != null && result.getCfaInterpolationAnomalies() > 0) {
            findings.append(String.format("CFA: %d interpolation anomalies detected\n", 
                          result.getCfaInterpolationAnomalies()));
        }
        
        if (result.getCopyMoveSuspiciousBlocks() != null && result.getCopyMoveSuspiciousBlocks() > 0) {
            findings.append(String.format("Copy-Move: %d suspicious duplicate regions detected\n", 
                          result.getCopyMoveSuspiciousBlocks()));
        }
        
        if (result.getLightingInconsistencies() != null && result.getLightingInconsistencies() > 0) {
            findings.append(String.format("Lighting: %d lighting inconsistencies detected\n", 
                          result.getLightingInconsistencies()));
        }
        
        result.setDetailedFindings(findings.toString());
    }
    
    /**
     * Download image from MinIO
     */
    private InputStream downloadImageFromMinio(String filePath) {
        try {
            return minioClient.getObject(
                GetObjectArgs.builder()
                    .bucket(bucketName)
                    .object(filePath)
                    .build()
            );
        } catch (Exception e) {
            throw new RuntimeException("Failed to download image from MinIO: " + filePath, e);
        }
    }
    
    /**
     * Upload analysis result image to MinIO
     */
    private String uploadAnalysisResult(String fileMd5, String analysisType, String extension, byte[] data) {
        try {
            String fileName = String.format("%s_%s_%s.%s", 
                fileMd5, analysisType, System.currentTimeMillis(), extension);
            String objectPath = traditionalAnalysisFolder + "/" + fileName;
            
            minioClient.putObject(
                PutObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectPath)
                    .stream(new ByteArrayInputStream(data), data.length, -1)
                    .contentType("image/" + extension)
                    .build()
            );
            
            return objectPath;
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to upload analysis result to MinIO", e);
        }
    }
    
    /**
     * Get analysis result by file MD5
     */
    public Optional<TraditionalAnalysisResponse> getAnalysisResult(String fileMd5) {
        return analysisRepository.findByFileMd5(fileMd5)
            .map(this::convertToResponse);
    }
    
    /**
     * Get analysis results for a project
     */
    public Page<TraditionalAnalysisResponse> getProjectAnalysisResults(Long projectId, Pageable pageable) {
        String currentUsername = SecurityUtils.getCurrentUsername();
        Optional<User> currentUser = userRepository.findByUsername(currentUsername);
        
        if (currentUser.isEmpty()) {
            throw new RuntimeException("User not found: " + currentUsername);
        }
        
        return analysisRepository.findByProjectIdAndUserIdOrderByCreatedAtDesc(
            projectId, currentUser.get().getId(), pageable)
            .map(this::convertToResponse);
    }
    
    /**
     * Convert entity to response DTO
     */
    private TraditionalAnalysisResponse convertToResponse(TraditionalAnalysisResult result) {
        TraditionalAnalysisResponse response = new TraditionalAnalysisResponse();
        
        response.setId(result.getId());
        response.setFileMd5(result.getFileMd5());
        response.setOriginalFilePath(result.getOriginalFilePath());
        response.setAnalysisStatus(result.getAnalysisStatus());
        response.setOverallConfidenceScore(result.getOverallConfidenceScore());
        response.setAuthenticityAssessment(result.getAuthenticityAssessment());
        response.setAnalysisSummary(result.getAnalysisSummary());
        response.setDetailedFindings(result.getDetailedFindings());
        response.setErrorMessage(result.getErrorMessage());
        response.setProcessingTimeMs(result.getProcessingTimeMs());
        response.setImageWidth(result.getImageWidth());
        response.setImageHeight(result.getImageHeight());
        response.setFileSizeBytes(result.getFileSizeBytes());
        response.setCreatedAt(result.getCreatedAt());
        response.setUpdatedAt(result.getUpdatedAt());
        
        // ELA Analysis
        if (result.getElaConfidenceScore() != null) {
            response.setElaAnalysis(new TraditionalAnalysisResponse.ElaAnalysisResult(
                result.getElaConfidenceScore(),
                generatePresignedUrl(result.getElaResultPath()),
                result.getElaSuspiciousRegions(),
                "ELA analysis completed successfully"
            ));
        }
        
        // CFA Analysis
        if (result.getCfaConfidenceScore() != null) {
            response.setCfaAnalysis(new TraditionalAnalysisResponse.CfaAnalysisResult(
                result.getCfaConfidenceScore(),
                generatePresignedUrl(result.getCfaHeatmapPath()),
                result.getCfaInterpolationAnomalies(),
                "CFA analysis completed successfully"
            ));
        }
        
        // Copy-Move Analysis
        if (result.getCopyMoveConfidenceScore() != null) {
            response.setCopyMoveAnalysis(new TraditionalAnalysisResponse.CopyMoveAnalysisResult(
                result.getCopyMoveConfidenceScore(),
                generatePresignedUrl(result.getCopyMoveResultPath()),
                result.getCopyMoveSuspiciousBlocks(),
                "Copy-Move detection completed successfully"
            ));
        }
        
        // Lighting Analysis
        if (result.getLightingConfidenceScore() != null) {
            response.setLightingAnalysis(new TraditionalAnalysisResponse.LightingAnalysisResult(
                result.getLightingConfidenceScore(),
                generatePresignedUrl(result.getLightingAnalysisPath()),
                result.getLightingInconsistencies(),
                "Lighting analysis completed successfully"
            ));
        }
        
        return response;
    }
    
    /**
     * Generate presigned URL for MinIO objects
     */
    private String generatePresignedUrl(String objectPath) {
        if (objectPath == null || objectPath.isEmpty()) {
            return null;
        }
        
        try {
            return minioClient.getPresignedObjectUrl(
                io.minio.GetPresignedObjectUrlArgs.builder()
                    .method(io.minio.http.Method.GET)
                    .bucket(bucketName)
                    .object(objectPath)
                    .expiry(60 * 60) // 1 hour
                    .build()
            );
        } catch (Exception e) {
            log.error("Failed to generate presigned URL for: {}", objectPath, e);
            return null;
        }
    }
    
    /**
     * Create a failed analysis record to prevent infinite retries
     */
    private void createFailedAnalysisRecord(String fileMd5, String errorMessage) {
        try {
            // Try to get media file info for context
            Optional<MediaFile> mediaFileOpt = mediaFileRepository.findByFileMd5(fileMd5);
            
            TraditionalAnalysisResult result = new TraditionalAnalysisResult();
            result.setFileMd5(fileMd5);
            result.setAnalysisStatus(TraditionalAnalysisResult.AnalysisStatus.FAILED);
            result.setErrorMessage(errorMessage);
            result.setProcessingTimeMs(0L);
            result.setOverallConfidenceScore(0.0);
            
            if (mediaFileOpt.isPresent()) {
                MediaFile mediaFile = mediaFileOpt.get();
                result.setOriginalFilePath(mediaFile.getFilePath());
                result.setUser(mediaFile.getUser());
                result.setProject(mediaFile.getProject());
                result.setFileSizeBytes(mediaFile.getFileSize());
                result.setImageWidth(0);
                result.setImageHeight(0);
            } else {
                // Minimal record for unknown files
                result.setOriginalFilePath("Unknown");
                result.setFileSizeBytes(0L);
                result.setImageWidth(0);
                result.setImageHeight(0);
            }
            
            analysisRepository.save(result);
            log.info("Created failed analysis record for file: {} with error: {}", fileMd5, errorMessage);
            
        } catch (Exception e) {
            log.error("Failed to create failed analysis record for file: {}", fileMd5, e);
        }
    }
}
