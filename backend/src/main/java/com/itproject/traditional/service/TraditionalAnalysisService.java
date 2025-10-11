package com.itproject.traditional.service;

import com.itproject.auth.entity.User;
import com.itproject.auth.repository.UserRepository;
import com.itproject.auth.security.SecurityUtils;
import com.itproject.common.service.EmailService;
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
import com.itproject.traditional.util.NoiseAnalysisUtil;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
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
    private NoiseAnalysisUtil noiseAnalysisUtil;
    
    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;
    
    @Autowired
    private EmailService emailService;
    
    @Autowired
    @Qualifier("traditionalAnalysisExecutor")
    private Executor traditionalAnalysisExecutor;
    
    @Autowired
    @Qualifier("ioTaskExecutor")
    private Executor ioTaskExecutor;
    
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
            
            // Send Kafka message to trigger analysis with fileMd5 as key for compacted topic
            kafkaTemplate.send(traditionalAnalysisTopic, fileMd5, task);
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
            
            // Store image data for reuse across all analyses (make final for lambda)
            final byte[] finalImageData = imageData.clone();
            final TraditionalAnalysisResult finalResult = result;
            final MediaFile finalMediaFile = mediaFile;
            
            // Execute all four analyses in parallel using CompletableFuture with proper exception handling
            CompletableFuture<Void> elaFuture = CompletableFuture.runAsync(() -> {
                performELAAnalysis(finalResult, new ByteArrayInputStream(finalImageData));
            }, traditionalAnalysisExecutor).exceptionally(throwable -> {
                log.error("Error in ELA analysis for file: {}", finalMediaFile.getFileMd5(), throwable);
                finalResult.setElaConfidenceScore(0.0);
                return null;
            });
            
            CompletableFuture<Void> cfaFuture = CompletableFuture.runAsync(() -> {
                performCFAAnalysis(finalResult, new ByteArrayInputStream(finalImageData));
            }, traditionalAnalysisExecutor).exceptionally(throwable -> {
                log.error("Error in CFA analysis for file: {}", finalMediaFile.getFileMd5(), throwable);
                finalResult.setCfaConfidenceScore(0.0);
                return null;
            });
            
            CompletableFuture<Void> copyMoveFuture = CompletableFuture.runAsync(() -> {
                performCopyMoveAnalysis(finalResult, new ByteArrayInputStream(finalImageData));
            }, traditionalAnalysisExecutor).exceptionally(throwable -> {
                log.error("Error in Copy-Move analysis for file: {}", finalMediaFile.getFileMd5(), throwable);
                finalResult.setCopyMoveConfidenceScore(0.0);
                return null;
            });
            
            CompletableFuture<Void> lightingFuture = CompletableFuture.runAsync(() -> {
                performLightingAnalysis(finalResult, new ByteArrayInputStream(finalImageData));
            }, traditionalAnalysisExecutor).exceptionally(throwable -> {
                log.error("Error in Lighting analysis for file: {}", finalMediaFile.getFileMd5(), throwable);
                finalResult.setLightingConfidenceScore(0.0);
                return null;
            });
            
            CompletableFuture<Void> noiseFuture = CompletableFuture.runAsync(() -> {
                performNoiseAnalysis(finalResult, new ByteArrayInputStream(finalImageData));
            }, traditionalAnalysisExecutor).exceptionally(throwable -> {
                log.error("Error in Noise analysis for file: {}", finalMediaFile.getFileMd5(), throwable);
                finalResult.setNoiseConfidenceScore(0.0);
                return null;
            });
            
            // Wait for all analyses to complete
            CompletableFuture<Void> allAnalyses = CompletableFuture.allOf(
                elaFuture, cfaFuture, copyMoveFuture, lightingFuture, noiseFuture
            );
            
            // Block until all analyses are complete
            allAnalyses.join();
            
            log.info("All parallel analyses completed for file: {}", finalMediaFile.getFileMd5());
            
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
            
            // Send completion email notification
            sendAnalysisCompleteEmail(result, mediaFile);
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
            // Use lower sensitivity (3) for more conservative analysis
            LightingAnalysisUtil.LightingResult lightingResult = lightingUtil.performLightingAnalysis(
                imageStream, 3);
            
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
     * Perform Noise Residual Analysis
     */
    private void performNoiseAnalysis(TraditionalAnalysisResult result, InputStream imageStream) {
        try {
            NoiseAnalysisUtil.NoiseResult noise = noiseAnalysisUtil.performNoiseAnalysis(imageStream, 9, 10);
            String path = uploadAnalysisResult(result.getFileMd5(), "noise", "png", noise.getResidualImageData());
            result.setNoiseResultPath(path);
            result.setNoiseConfidenceScore(noise.getConfidenceScore());
            result.setNoiseSuspiciousRegions(noise.getSuspiciousRegions());
            log.debug("Noise analysis completed for file: {}, confidence: {}", result.getFileMd5(), noise.getConfidenceScore());
        } catch (Exception e) {
            log.error("Error in Noise analysis for file: {}", result.getFileMd5(), e);
            result.setNoiseConfidenceScore(0.0);
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
    double noiseScore = result.getNoiseConfidenceScore() != null ? result.getNoiseConfidenceScore() : 0.0;
        
    // Weighted average (ELA, Copy-Move and Noise are stronger)
    double overallScore = (elaScore * 0.3 + cfaScore * 0.2 + copyMoveScore * 0.25 + lightingScore * 0.1 + noiseScore * 0.15);
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
    summary.append(String.format("- Noise Residual Analysis: %.2f/100\n", noiseScore));
        
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
        if (result.getNoiseSuspiciousRegions() != null && result.getNoiseSuspiciousRegions() > 0) {
            findings.append(String.format("Noise Residual: %d suspicious regions detected\n", 
                          result.getNoiseSuspiciousRegions()));
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
        
        // Noise Analysis
        if (result.getNoiseConfidenceScore() != null) {
            response.setNoiseAnalysis(new TraditionalAnalysisResponse.NoiseAnalysisResult(
                result.getNoiseConfidenceScore(),
                generatePresignedUrl(result.getNoiseResultPath()),
                result.getNoiseSuspiciousRegions(),
                "Noise residual analysis completed successfully"
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
    
    /**
     * Send analysis completion email notification
     */
    private void sendAnalysisCompleteEmail(TraditionalAnalysisResult result, MediaFile mediaFile) {
        try {
            if (mediaFile.getUser() == null || mediaFile.getUser().getEmail() == null) {
                log.warn("Cannot send email notification - user or email is null for file: {}", result.getFileMd5());
                return;
            }
            
            Map<String, Object> emailVariables = new HashMap<>();
            
            // Basic information
            emailVariables.put("userName", mediaFile.getUser().getUsername());
            emailVariables.put("fileName", mediaFile.getOriginalFileName());
            emailVariables.put("projectName", mediaFile.getProject() != null ? mediaFile.getProject().getName() : "Unknown Project");
            emailVariables.put("analysisDate", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            
            // Analysis status and results
            String status = result.getAnalysisStatus().name();
            emailVariables.put("analysisStatus", status);
            emailVariables.put("isSuccessful", "COMPLETED".equals(status));
            
            if ("COMPLETED".equals(status)) {
                // Success case - include detailed results
                Double overallScore = result.getOverallConfidenceScore() != null ? result.getOverallConfidenceScore() : 0.0;
                emailVariables.put("overallScore", String.format("%.1f", overallScore));
                
                String authenticityAssessment = result.getAuthenticityAssessment() != null ? 
                    result.getAuthenticityAssessment().name() : "UNKNOWN";
                emailVariables.put("authenticityAssessment", authenticityAssessment);
                
                // Individual scores
                emailVariables.put("elaScore", String.format("%.1f", result.getElaConfidenceScore() != null ? result.getElaConfidenceScore() : 0.0));
                emailVariables.put("cfaScore", String.format("%.1f", result.getCfaConfidenceScore() != null ? result.getCfaConfidenceScore() : 0.0));
                emailVariables.put("copyMoveScore", String.format("%.1f", result.getCopyMoveConfidenceScore() != null ? result.getCopyMoveConfidenceScore() : 0.0));
                emailVariables.put("lightingScore", String.format("%.1f", result.getLightingConfidenceScore() != null ? result.getLightingConfidenceScore() : 0.0));
                
                // Processing information
                emailVariables.put("processingTime", String.format("%.2f", result.getProcessingTimeMs() / 1000.0));
                emailVariables.put("imageWidth", result.getImageWidth());
                emailVariables.put("imageHeight", result.getImageHeight());
                emailVariables.put("fileSize", String.format("%.2f", result.getFileSizeBytes() / (1024.0 * 1024.0))); // MB
                
                // Analysis summary
                emailVariables.put("analysisSummary", result.getAnalysisSummary());
                emailVariables.put("detailedFindings", result.getDetailedFindings());
                
            } else {
                // Failed case
                emailVariables.put("errorMessage", result.getErrorMessage() != null ? result.getErrorMessage() : "Unknown error occurred");
                emailVariables.put("processingTime", result.getProcessingTimeMs() != null ? 
                    String.format("%.2f", result.getProcessingTimeMs() / 1000.0) : "Unknown");
            }
            
            // Send email asynchronously
            emailService.sendTraditionalAnalysisCompleteEmail(
                    mediaFile.getUser().getEmail(), 
                    mediaFile.getUser().getUsername(), 
                    emailVariables)
                .thenAccept(success -> {
                    if (success) {
                        log.info("Analysis completion email sent successfully for file: {}", result.getFileMd5());
                    } else {
                        log.error("Failed to send analysis completion email for file: {}", result.getFileMd5());
                    }
                })
                .exceptionally(throwable -> {
                    log.error("Exception while sending analysis completion email for file: {}", result.getFileMd5(), throwable);
                    return null;
                });
                
        } catch (Exception e) {
            log.error("Error preparing analysis completion email for file: {}", result.getFileMd5(), e);
        }
    }
}
