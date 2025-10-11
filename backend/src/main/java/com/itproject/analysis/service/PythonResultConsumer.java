package com.itproject.analysis.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.itproject.analysis.entity.AnalysisTask;
import com.itproject.project.repository.AnalysisTaskRepository;
import com.itproject.traditional.entity.VideoTraditionalAnalysisResult;
import com.itproject.traditional.repository.VideoTraditionalAnalysisResultRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;

@Slf4j
@Service
public class PythonResultConsumer {

    @Autowired
    private AnalysisTaskRepository analysisTaskRepository;

    @Autowired
    private VideoTraditionalAnalysisResultRepository videoTradRepo;

    private final ObjectMapper mapper = new ObjectMapper();

    @KafkaListener(topics = "analysis-results", groupId = "java-results-group")
    public void onPythonResult(Map<String, Object> payload) {
        try {
            log.info("Received analysis result from Python: {}", payload);
            Object successObj = payload.get("success");
            boolean success = successObj instanceof Boolean ? (Boolean) successObj : true;
            Map<String, Object> data = (Map<String, Object>) payload.get("data");
            if (data != null) {
                String taskId = String.valueOf(data.getOrDefault("taskId", ""));
                if (taskId != null && !taskId.isEmpty()) {
                    try {
                        Long id = Long.valueOf(taskId);
                        analysisTaskRepository.findById(id).ifPresent(task -> {
                            task.setStatus(success ? AnalysisTask.AnalysisStatus.COMPLETED : AnalysisTask.AnalysisStatus.FAILED);
                            try {
                                // Persist full payload data to results (includes artifacts with MinIO URLs)
                                task.setResults(mapper.writeValueAsString(data));
                            } catch (Exception ignore) {}
                            if (!success) {
                                Object err = payload.get("error");
                                task.setErrorMessage(err != null ? err.toString() : "Python worker error");
                            }
                            task.setCompletedAt(LocalDateTime.now());
                            analysisTaskRepository.save(task);

                            // Additionally, persist to video_traditional_analysis_results when applicable
                            try {
                                Object typeObj = data.get("type");
                                String type = typeObj != null ? String.valueOf(typeObj) : "";
                                if (type.startsWith("VIDEO_TRADITIONAL_")) {
                                    VideoTraditionalAnalysisResult row = new VideoTraditionalAnalysisResult();
                                    row.setAnalysisTask(task);
                                    row.setUser(task.getUser());
                                    row.setProject(task.getProject());
                                    row.setFileMd5(String.valueOf(data.getOrDefault("fileMd5", "")));
                                    row.setMethod(String.valueOf(data.getOrDefault("method", "")));
                                    try { row.setArtifactsJson(mapper.writeValueAsString(data.get("artifacts"))); } catch (Exception ignore2) {}
                                    try { row.setResultJson(mapper.writeValueAsString(data.get("result"))); } catch (Exception ignore3) {}
                                    row.setSuccess(success);
                                    if (!success) { row.setErrorMessage(task.getErrorMessage()); }
                                    videoTradRepo.save(row);
                                }
                            } catch (Exception er) {
                                log.warn("Failed to persist video traditional result row for task {}: {}", id, er.getMessage());
                            }
                        });
                    } catch (NumberFormatException nfe) {
                        // taskId is not numeric; skip linking for now
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error processing Python result payload", e);
        }
    }
}
