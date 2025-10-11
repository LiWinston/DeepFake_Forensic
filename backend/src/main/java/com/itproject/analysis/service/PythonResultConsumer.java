package com.itproject.analysis.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.itproject.analysis.entity.AnalysisTask;
import com.itproject.project.repository.AnalysisTaskRepository;
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
                    // Try to find task by ID (numeric) if applicable
                    try {
                        Long id = Long.valueOf(taskId);
                        analysisTaskRepository.findById(id).ifPresent(task -> {
                            task.setStatus(success ? AnalysisTask.AnalysisStatus.COMPLETED : AnalysisTask.AnalysisStatus.FAILED);
                            try {
                                task.setResults(mapper.writeValueAsString(data));
                            } catch (Exception ignore) {}
                            task.setCompletedAt(LocalDateTime.now());
                            analysisTaskRepository.save(task);
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
