package com.itproject.analysis.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.itproject.analysis.controller.AnalysisOrchestrationController.StartAnalysisRequest;
import com.itproject.analysis.entity.AnalysisTask;
import com.itproject.auth.entity.User;
import com.itproject.auth.security.SecurityUtils;
import com.itproject.project.entity.Project;
import com.itproject.project.repository.AnalysisTaskRepository;
import com.itproject.project.repository.ProjectRepository;
import com.itproject.upload.entity.MediaFile;
import com.itproject.upload.repository.MediaFileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Orchestration service to start analyses based on user selection.
 * - Creates AnalysisTask records
 * - Dispatches tasks to Kafka topics for Python workers (image AI, video traditional/AI)
 * - Returns a taskId to track progress in Redis (we use fileMd5 by default)
 */
@Slf4j
@Service
public class AnalysisOrchestrationService {
    @org.springframework.beans.factory.annotation.Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @org.springframework.beans.factory.annotation.Autowired
    @org.springframework.beans.factory.annotation.Qualifier("imageAiAnalysisTopic")
    private String imageAiAnalysisTopic;

    @org.springframework.beans.factory.annotation.Autowired
    @org.springframework.beans.factory.annotation.Qualifier("videoTraditionalAnalysisTopic")
    private String videoTraditionalAnalysisTopic;

    @org.springframework.beans.factory.annotation.Autowired
    @org.springframework.beans.factory.annotation.Qualifier("videoAiAnalysisTopic")
    private String videoAiAnalysisTopic;

    @org.springframework.beans.factory.annotation.Autowired
    @org.springframework.beans.factory.annotation.Qualifier("traditionalAnalysisTopic")
    private String traditionalAnalysisTopic;

    @org.springframework.beans.factory.annotation.Autowired
    private MediaFileRepository mediaFileRepository;

    @org.springframework.beans.factory.annotation.Autowired
    private ProjectRepository projectRepository;

    @org.springframework.beans.factory.annotation.Autowired
    private AnalysisTaskRepository analysisTaskRepository;

    @Value("${minio.endpoint:http://localhost:9000}")
    private String minioEndpoint;

    @Value("${minio.bucket-name:forensic-media}")
    private String minioBucketName;

    private final ObjectMapper mapper = new ObjectMapper();

    public Map<String, Object> start(StartAnalysisRequest req) {
        User current = SecurityUtils.getCurrentUser();
        if (current == null) {
            throw new RuntimeException("User not logged in");
        }

        // Validate project (optional)
        Project project = null;
        if (req.getProjectId() != null) {
            project = projectRepository.findById(req.getProjectId())
                    .orElseThrow(() -> new RuntimeException("Project not found: " + req.getProjectId()));
            if (!project.getUser().getId().equals(current.getId())) {
                throw new RuntimeException("No permission to access this project");
            }
        }

        // Resolve media file by MD5
        MediaFile media = mediaFileRepository.findByFileMd5(req.getFileMd5())
                .orElseThrow(() -> new RuntimeException("File not found: " + req.getFileMd5()));

    // Use provided taskId or fallback to fileMd5 as a parent correlation id (not for child tasks)
    String parentCorrelationId = StringUtils.hasText(req.getTaskId()) ? req.getTaskId() : media.getFileMd5();

        // Create a parent AnalysisTask to reflect orchestration
        AnalysisTask parent = new AnalysisTask();
        parent.setTaskName("User-selected analysis");
        parent.setAnalysisType(AnalysisTask.AnalysisType.COMPREHENSIVE_REPORT);
        parent.setStatus(AnalysisTask.AnalysisStatus.QUEUED);
        parent.setDescription("Selected analyses to run after upload");
        try { parent.setParameters(mapper.writeValueAsString(req)); } catch (Exception ignore) {}
        parent.setProject(project != null ? project : media.getProject());
        parent.setMediaFile(media);
        parent.setUser(current);
        parent.setStartedAt(LocalDateTime.now());
        analysisTaskRepository.save(parent);

        // Compute MinIO object path and a simple accessible URL (assumes MinIO is reachable)
        String objectPath = media.getFileMd5() + "/" + media.getFileName();
        String minioUrl = buildMinioUrl(objectPath);

    // Dispatch selected tasks
    List<Map<String, Object>> dispatched = new ArrayList<>();
    List<Map<String, Object>> subTasks = new ArrayList<>();

        if (req.isRunImageAI() && media.getMediaType() == MediaFile.MediaType.IMAGE) {
            Map<String, Object> msg = new HashMap<>();
        msg.put("type", "IMAGE_AI");
        // Create child task to track image AI
        AnalysisTask child = persistChildTask(parent, media, current, AnalysisTask.AnalysisType.DEEPFAKE_DETECTION);
        msg.put("taskId", String.valueOf(child.getId()));
            msg.put("fileMd5", media.getFileMd5());
            msg.put("model", req.getSelectedImageModel());
            msg.put("imageUrl", minioUrl);
            msg.put("parentTaskId", parentCorrelationId);
            kafkaTemplate.send(imageAiAnalysisTopic, String.valueOf(child.getId()), msg);
            dispatched.add(Map.of("topic", imageAiAnalysisTopic, "payload", msg));
        subTasks.add(Map.of(
            "taskId", child.getId(),
            "type", child.getAnalysisType().name(),
            "method", "IMAGE_AI"
        ));
        }

        // Traditional image analysis via existing Java flow
        if (req.isRunTraditionalImage() && media.getMediaType() == MediaFile.MediaType.IMAGE) {
            try {
                com.itproject.traditional.dto.TraditionalAnalysisTaskDto dto =
                        new com.itproject.traditional.dto.TraditionalAnalysisTaskDto(media.getFileMd5(), false);
                kafkaTemplate.send(traditionalAnalysisTopic, media.getFileMd5(), dto);
                dispatched.add(Map.of("topic", traditionalAnalysisTopic, "payload", Map.of("fileMd5", media.getFileMd5(), "force", false)));
                persistChildTask(parent, media, current, AnalysisTask.AnalysisType.PIXEL_LEVEL_ANALYSIS);
            } catch (Exception e) {
                log.warn("Failed to dispatch traditional image analysis for {}: {}", media.getFileMd5(), e.getMessage());
            }
        }

        if (req.isRunVideoTraditional() && media.getMediaType() == MediaFile.MediaType.VIDEO) {
        List<String> methods = (req.getSelectedTraditionalMethods() != null && !req.getSelectedTraditionalMethods().isEmpty())
            ? req.getSelectedTraditionalMethods()
            : java.util.Arrays.asList("NOISE", "FLOW", "FREQ", "TEMPORAL", "COPYMOVE");

            for (String method : methods) {
                String normalized = method.trim().toUpperCase();
                String pyType;
                if ("FLOW".equals(normalized)) {
                    pyType = "VIDEO_TRADITIONAL_FLOW";
                } else if ("FREQ".equals(normalized) || "FREQUENCY".equals(normalized)) {
                    pyType = "VIDEO_TRADITIONAL_FREQ";
                } else if ("TEMPORAL".equals(normalized)) {
                    pyType = "VIDEO_TRADITIONAL_TEMPORAL";
                } else if ("COPYMOVE".equals(normalized) || "COPY_MOVE".equals(normalized)) {
                    pyType = "VIDEO_TRADITIONAL_COPYMOVE";
                } else {
                    pyType = "VIDEO_TRADITIONAL_NOISE";
                }
                AnalysisTask.AnalysisType analysisType = mapMethodToAnalysisType(normalized);

                AnalysisTask child = persistChildTask(parent, media, current, analysisType);

                Map<String, Object> msg = new HashMap<>();
                msg.put("type", pyType);
                msg.put("taskId", String.valueOf(child.getId()));
                msg.put("fileMd5", media.getFileMd5());
                msg.put("minioUrl", minioUrl);
                msg.put("parentTaskId", parentCorrelationId);
                // Optional params
                msg.put("sampleFrames", 30);
                msg.put("noiseSigma", 10.0);
                kafkaTemplate.send(videoTraditionalAnalysisTopic, String.valueOf(child.getId()), msg);
                dispatched.add(Map.of("topic", videoTraditionalAnalysisTopic, "payload", msg));
                subTasks.add(Map.of(
                        "taskId", child.getId(),
                        "type", analysisType.name(),
                        "method", normalized
                ));
            }
        }

        if (req.isRunVideoAI() && media.getMediaType() == MediaFile.MediaType.VIDEO) {
            // Create child task to track video AI
            AnalysisTask child = persistChildTask(parent, media, current, AnalysisTask.AnalysisType.DEEPFAKE_DETECTION);

            Map<String, Object> msg = new HashMap<>();
            msg.put("type", "VIDEO_AI");
            msg.put("taskId", String.valueOf(child.getId()));
            msg.put("fileMd5", media.getFileMd5());
            msg.put("minioUrl", minioUrl);
            msg.put("parentTaskId", parentCorrelationId);
            kafkaTemplate.send(videoAiAnalysisTopic, String.valueOf(child.getId()), msg);
            dispatched.add(Map.of("topic", videoAiAnalysisTopic, "payload", msg));

            subTasks.add(Map.of(
                    "taskId", child.getId(),
                    "type", child.getAnalysisType().name(),
                    "method", "VIDEO_AI"
            ));
        }

        Map<String, Object> resp = new HashMap<>();
        resp.put("taskId", parentCorrelationId);
        resp.put("dispatched", dispatched);
        resp.put("parentTaskId", parent.getId());
        resp.put("subTasks", subTasks);
        return resp;
    }

    private AnalysisTask persistChildTask(AnalysisTask parent, MediaFile media, User current, AnalysisTask.AnalysisType type) {
        AnalysisTask child = new AnalysisTask();
        child.setTaskName("Subtask - " + type.name());
        child.setAnalysisType(type);
        child.setStatus(AnalysisTask.AnalysisStatus.QUEUED);
        child.setProject(parent.getProject());
        child.setMediaFile(media);
        child.setUser(current);
        analysisTaskRepository.save(child);
        return child;
    }

    private AnalysisTask.AnalysisType mapMethodToAnalysisType(String method) {
        if ("NOISE".equals(method)) {
            return AnalysisTask.AnalysisType.NOISE_ANALYSIS;
        } else if ("FLOW".equals(method)) {
            return AnalysisTask.AnalysisType.GEOMETRIC_ANALYSIS;
        } else if ("FREQ".equals(method) || "FREQUENCY".equals(method)) {
            return AnalysisTask.AnalysisType.FREQUENCY_DOMAIN_ANALYSIS;
        } else if ("TEMPORAL".equals(method) || "COPYMOVE".equals(method) || "COPY_MOVE".equals(method)) {
            return AnalysisTask.AnalysisType.EDIT_DETECTION;
        } else {
            return AnalysisTask.AnalysisType.PIXEL_LEVEL_ANALYSIS;
        }
    }

    private String buildMinioUrl(String objectPath) {
        String endpoint = minioEndpoint;
        if (endpoint.endsWith("/")) endpoint = endpoint.substring(0, endpoint.length() - 1);
        return endpoint + "/" + minioBucketName + "/" + objectPath;
    }
}
