package com.itproject.project.controller;

import com.itproject.analysis.entity.AnalysisTask;
import com.itproject.project.service.AnalysisTaskService;
import com.itproject.project.dto.CreateAnalysisTaskRequest;
import com.itproject.auth.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/api/v1/analysis-tasks")
@RequiredArgsConstructor
@Slf4j
public class AnalysisTaskController {
    
    private final AnalysisTaskService analysisTaskService;
    
    /**
     * Create a new analysis task
     */
    @PostMapping
    public ResponseEntity<AnalysisTask> createAnalysisTask(
            @Valid @RequestBody CreateAnalysisTaskRequest request,
            @AuthenticationPrincipal User user) {
        
        AnalysisTask task = new AnalysisTask();
        task.setTaskName(request.getTaskName());
        task.setAnalysisType(request.getAnalysisType());
        task.setDescription(request.getDescription());
        task.setNotes(request.getNotes());
        
        AnalysisTask createdTask = analysisTaskService.createAnalysisTask(task, request.getProjectId(), user);
        return ResponseEntity.ok(createdTask);
    }
    
    /**
     * Get analysis task by ID
     */
    @GetMapping("/{taskId}")
    public ResponseEntity<AnalysisTask> getAnalysisTask(
            @PathVariable Long taskId,
            @AuthenticationPrincipal User user) {
        
        AnalysisTask task = analysisTaskService.getAnalysisTaskById(taskId, user);
        return ResponseEntity.ok(task);
    }
    
    /**
     * Update an analysis task
     */
    @PutMapping("/{taskId}")
    public ResponseEntity<AnalysisTask> updateAnalysisTask(
            @PathVariable Long taskId,
            @Valid @RequestBody AnalysisTask task,
            @AuthenticationPrincipal User user) {
        
        AnalysisTask updatedTask = analysisTaskService.updateAnalysisTask(taskId, task, user);
        return ResponseEntity.ok(updatedTask);
    }
    
    /**
     * Delete an analysis task
     */
    @DeleteMapping("/{taskId}")
    public ResponseEntity<Void> deleteAnalysisTask(
            @PathVariable Long taskId,
            @AuthenticationPrincipal User user) {
        
        analysisTaskService.deleteAnalysisTask(taskId, user);
        return ResponseEntity.noContent().build();
    }
    
    /**
     * Start an analysis task
     */
    @PutMapping("/{taskId}/start")
    public ResponseEntity<AnalysisTask> startAnalysisTask(
            @PathVariable Long taskId,
            @AuthenticationPrincipal User user) {
        
        AnalysisTask task = analysisTaskService.startAnalysisTask(taskId, user);
        return ResponseEntity.ok(task);
    }
    
    /**
     * Complete an analysis task
     */
    @PutMapping("/{taskId}/complete")
    public ResponseEntity<AnalysisTask> completeAnalysisTask(
            @PathVariable Long taskId,
            @Valid @RequestBody CompleteAnalysisTaskRequest request,
            @AuthenticationPrincipal User user) {
        
        AnalysisTask task = analysisTaskService.completeAnalysisTask(
                taskId, request.getResultData(), request.getConfidenceScore(), user);
        return ResponseEntity.ok(task);
    }
    
    /**
     * Fail an analysis task
     */
    @PutMapping("/{taskId}/fail")
    public ResponseEntity<AnalysisTask> failAnalysisTask(
            @PathVariable Long taskId,
            @RequestBody FailAnalysisTaskRequest request,
            @AuthenticationPrincipal User user) {
        
        AnalysisTask task = analysisTaskService.failAnalysisTask(taskId, request.getErrorMessage(), user);
        return ResponseEntity.ok(task);
    }
    
    /**
     * Cancel an analysis task
     */
    @PutMapping("/{taskId}/cancel")
    public ResponseEntity<AnalysisTask> cancelAnalysisTask(
            @PathVariable Long taskId,
            @AuthenticationPrincipal User user) {
        
        AnalysisTask task = analysisTaskService.cancelAnalysisTask(taskId, user);
        return ResponseEntity.ok(task);
    }
    
    /**
     * Get analysis tasks for a project
     */
    @GetMapping("/project/{projectId}")
    public ResponseEntity<List<AnalysisTask>> getProjectAnalysisTasks(
            @PathVariable Long projectId,
            @AuthenticationPrincipal User user) {
        
        List<AnalysisTask> tasks = analysisTaskService.getProjectAnalysisTasks(projectId, user);
        return ResponseEntity.ok(tasks);
    }
    
    /**
     * Get all analysis tasks for the current user
     */
    @GetMapping
    public ResponseEntity<List<AnalysisTask>> getUserAnalysisTasks(
            @AuthenticationPrincipal User user) {
        
        List<AnalysisTask> tasks = analysisTaskService.getUserAnalysisTasks(user);
        return ResponseEntity.ok(tasks);
    }
    
    /**
     * Get analysis tasks by status
     */
    @GetMapping("/status/{status}")
    public ResponseEntity<List<AnalysisTask>> getAnalysisTasksByStatus(
            @PathVariable AnalysisTask.AnalysisStatus status,
            @AuthenticationPrincipal User user) {
        
        List<AnalysisTask> tasks = analysisTaskService.getAnalysisTasksByStatus(user, status);
        return ResponseEntity.ok(tasks);
    }
    
    /**
     * Get analysis tasks by type for a project
     */
    @GetMapping("/project/{projectId}/type/{type}")
    public ResponseEntity<List<AnalysisTask>> getAnalysisTasksByType(
            @PathVariable Long projectId,
            @PathVariable AnalysisTask.AnalysisType type,
            @AuthenticationPrincipal User user) {
        
        List<AnalysisTask> tasks = analysisTaskService.getAnalysisTasksByType(projectId, type, user);
        return ResponseEntity.ok(tasks);
    }
    
    /**
     * Get running tasks
     */
    @GetMapping("/running")
    public ResponseEntity<List<AnalysisTask>> getRunningTasks(@AuthenticationPrincipal User user) {
        List<AnalysisTask> tasks = analysisTaskService.getRunningTasks(user);
        return ResponseEntity.ok(tasks);
    }
    
    /**
     * Get pending tasks
     */
    @GetMapping("/pending")
    public ResponseEntity<List<AnalysisTask>> getPendingTasks(@AuthenticationPrincipal User user) {
        List<AnalysisTask> tasks = analysisTaskService.getPendingTasks(user);
        return ResponseEntity.ok(tasks);
    }
    
    /**
     * Search analysis tasks by keyword
     */
    @GetMapping("/search")
    public ResponseEntity<List<AnalysisTask>> searchAnalysisTasks(
            @RequestParam String keyword,
            @AuthenticationPrincipal User user) {
        
        List<AnalysisTask> tasks = analysisTaskService.searchAnalysisTasks(user, keyword);
        return ResponseEntity.ok(tasks);
    }
    
    /**
     * Get analysis task statistics for a project
     */
    @GetMapping("/project/{projectId}/statistics")
    public ResponseEntity<AnalysisTaskService.AnalysisTaskStatistics> getAnalysisTaskStatistics(
            @PathVariable Long projectId,
            @AuthenticationPrincipal User user) {
        
        AnalysisTaskService.AnalysisTaskStatistics statistics = 
                analysisTaskService.getAnalysisTaskStatistics(projectId, user);
        return ResponseEntity.ok(statistics);
    }
    
    // DTOs for request bodies
    public static class CompleteAnalysisTaskRequest {
        private String resultData;
        private Double confidenceScore;
        
        public String getResultData() { return resultData; }
        public void setResultData(String resultData) { this.resultData = resultData; }
        
        public Double getConfidenceScore() { return confidenceScore; }
        public void setConfidenceScore(Double confidenceScore) { this.confidenceScore = confidenceScore; }
    }
    
    public static class FailAnalysisTaskRequest {
        private String errorMessage;
        
        public String getErrorMessage() { return errorMessage; }
        public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    }
}
