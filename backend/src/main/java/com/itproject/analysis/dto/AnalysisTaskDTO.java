package com.itproject.analysis.dto;

import com.itproject.analysis.entity.AnalysisTask;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO for AnalysisTask to avoid Hibernate proxy serialization issues
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AnalysisTaskDTO {
    
    private Long id;
    private String taskName;
    private AnalysisTask.AnalysisType analysisType;
    private AnalysisTask.AnalysisStatus status;
    private String description;
    private String parameters;
    private String results;
    private String notes;
    private Double confidenceScore;
    private String errorMessage;
    private Integer priority;
    private Double progress;
    private Long estimatedDuration;
    private Long actualDuration;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    // Related entity IDs instead of full objects
    private Long projectId;
    private String projectName;
    private Long mediaFileId;
    private String mediaFileName;
    private String mediaFileMd5;
    private Long userId;
    private String username;
    
    /**
     * Convert entity to DTO
     */
    public static AnalysisTaskDTO fromEntity(AnalysisTask task) {
        if (task == null) {
            return null;
        }
        
        AnalysisTaskDTO dto = new AnalysisTaskDTO();
        dto.setId(task.getId());
        dto.setTaskName(task.getTaskName());
        dto.setAnalysisType(task.getAnalysisType());
        dto.setStatus(task.getStatus());
        dto.setDescription(task.getDescription());
        dto.setParameters(task.getParameters());
        dto.setResults(task.getResults());
        dto.setNotes(task.getNotes());
        dto.setConfidenceScore(task.getConfidenceScore());
        dto.setErrorMessage(task.getErrorMessage());
        dto.setPriority(task.getPriority());
        dto.setProgress(task.getProgress());
        dto.setEstimatedDuration(task.getEstimatedDuration());
        dto.setActualDuration(task.getActualDuration());
        dto.setStartedAt(task.getStartedAt());
        dto.setCompletedAt(task.getCompletedAt());
        dto.setCreatedAt(task.getCreatedAt());
        dto.setUpdatedAt(task.getUpdatedAt());
        
        // Safely extract related entity info without triggering lazy loading if not needed
        if (task.getProject() != null) {
            dto.setProjectId(task.getProject().getId());
            dto.setProjectName(task.getProject().getName());
        }
        
        if (task.getMediaFile() != null) {
            dto.setMediaFileId(task.getMediaFile().getId());
            dto.setMediaFileName(task.getMediaFile().getFileName());
            dto.setMediaFileMd5(task.getMediaFile().getFileMd5());
        }
        
        if (task.getUser() != null) {
            dto.setUserId(task.getUser().getId());
            dto.setUsername(task.getUser().getUsername());
        }
        
        return dto;
    }
}
