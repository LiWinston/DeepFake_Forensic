package com.itproject.project.service;

import com.itproject.project.entity.AnalysisTask;
import com.itproject.project.entity.Project;
import com.itproject.project.repository.AnalysisTaskRepository;
import com.itproject.auth.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class AnalysisTaskService {
    
    private final AnalysisTaskRepository analysisTaskRepository;
    private final ProjectService projectService;
    
    /**
     * Create a new analysis task
     */
    public AnalysisTask createAnalysisTask(AnalysisTask task, Long projectId, User user) {
        Project project = projectService.getProjectById(projectId, user);
        
        task.setProject(project);
        task.setUser(user);
        task.setStatus(AnalysisTask.TaskStatus.PENDING);
        
        if (task.getTaskName() == null || task.getTaskName().isEmpty()) {
            task.setTaskName(generateTaskName(task.getAnalysisType()));
        }
        
        log.info("Creating new analysis task: {} for project: {} user: {}", 
                task.getTaskName(), projectId, user.getUsername());
        return analysisTaskRepository.save(task);
    }
    
    /**
     * Update an existing analysis task
     */
    public AnalysisTask updateAnalysisTask(Long taskId, AnalysisTask updatedTask, User user) {
        AnalysisTask existingTask = getAnalysisTaskById(taskId, user);
        
        // Update fields
        existingTask.setTaskName(updatedTask.getTaskName());
        existingTask.setDescription(updatedTask.getDescription());
        existingTask.setNotes(updatedTask.getNotes());
        
        // Only allow status updates if not completed
        if (existingTask.getStatus() != AnalysisTask.TaskStatus.COMPLETED) {
            existingTask.setStatus(updatedTask.getStatus());
        }
        
        log.info("Updating analysis task: {} for user: {}", taskId, user.getUsername());
        return analysisTaskRepository.save(existingTask);
    }
    
    /**
     * Start an analysis task
     */
    public AnalysisTask startAnalysisTask(Long taskId, User user) {
        AnalysisTask task = getAnalysisTaskById(taskId, user);
        
        if (task.getStatus() != AnalysisTask.TaskStatus.PENDING) {
            throw new RuntimeException("Task can only be started from PENDING status");
        }
        
        task.setStatus(AnalysisTask.TaskStatus.RUNNING);
        task.setStartedAt(LocalDateTime.now());
        
        log.info("Starting analysis task: {} for user: {}", taskId, user.getUsername());
        return analysisTaskRepository.save(task);
    }
    
    /**
     * Complete an analysis task
     */
    public AnalysisTask completeAnalysisTask(Long taskId, String resultData, Double confidenceScore, User user) {
        AnalysisTask task = getAnalysisTaskById(taskId, user);
        
        if (task.getStatus() != AnalysisTask.TaskStatus.RUNNING) {
            throw new RuntimeException("Task must be in RUNNING status to complete");
        }
        
        task.setStatus(AnalysisTask.TaskStatus.COMPLETED);
        task.setCompletedAt(LocalDateTime.now());
        task.setResultData(resultData);
        task.setConfidenceScore(confidenceScore);
        
        log.info("Completing analysis task: {} for user: {}", taskId, user.getUsername());
        return analysisTaskRepository.save(task);
    }
    
    /**
     * Fail an analysis task
     */
    public AnalysisTask failAnalysisTask(Long taskId, String errorMessage, User user) {
        AnalysisTask task = getAnalysisTaskById(taskId, user);
        
        task.setStatus(AnalysisTask.TaskStatus.FAILED);
        task.setCompletedAt(LocalDateTime.now());
        task.setNotes((task.getNotes() != null ? task.getNotes() + "\n" : "") + 
                     "Error: " + errorMessage);
        
        log.warn("Failing analysis task: {} for user: {} - Error: {}", taskId, user.getUsername(), errorMessage);
        return analysisTaskRepository.save(task);
    }
    
    /**
     * Cancel an analysis task
     */
    public AnalysisTask cancelAnalysisTask(Long taskId, User user) {
        AnalysisTask task = getAnalysisTaskById(taskId, user);
        
        if (task.getStatus() == AnalysisTask.TaskStatus.COMPLETED) {
            throw new RuntimeException("Cannot cancel completed task");
        }
        
        task.setStatus(AnalysisTask.TaskStatus.CANCELLED);
        
        log.info("Cancelling analysis task: {} for user: {}", taskId, user.getUsername());
        return analysisTaskRepository.save(task);
    }
    
    /**
     * Get analysis task by ID (with user authorization check)
     */
    @Transactional(readOnly = true)
    public AnalysisTask getAnalysisTaskById(Long taskId, User user) {
        return analysisTaskRepository.findById(taskId)
                .filter(task -> task.getUser().getId().equals(user.getId()))
                .orElseThrow(() -> new RuntimeException("Analysis task not found or access denied"));
    }
    
    /**
     * Get all analysis tasks for a project
     */
    @Transactional(readOnly = true)
    public List<AnalysisTask> getProjectAnalysisTasks(Long projectId, User user) {
        Project project = projectService.getProjectById(projectId, user);
        return analysisTaskRepository.findByProjectOrderByCreatedAtDesc(project);
    }
    
    /**
     * Get analysis tasks for a user
     */
    @Transactional(readOnly = true)
    public List<AnalysisTask> getUserAnalysisTasks(User user) {
        return analysisTaskRepository.findByUserOrderByCreatedAtDesc(user);
    }
    
    /**
     * Get analysis tasks by status
     */
    @Transactional(readOnly = true)
    public List<AnalysisTask> getAnalysisTasksByStatus(User user, AnalysisTask.TaskStatus status) {
        return analysisTaskRepository.findByUserAndStatus(user, status);
    }
    
    /**
     * Get analysis tasks by type
     */
    @Transactional(readOnly = true)
    public List<AnalysisTask> getAnalysisTasksByType(Long projectId, AnalysisTask.AnalysisType analysisType, User user) {
        Project project = projectService.getProjectById(projectId, user);
        return analysisTaskRepository.findByProjectAndAnalysisType(project, analysisType);
    }
    
    /**
     * Get running tasks for a user
     */
    @Transactional(readOnly = true)
    public List<AnalysisTask> getRunningTasks(User user) {
        return analysisTaskRepository.findRunningTasksByUser(user);
    }
    
    /**
     * Get pending tasks for a user
     */
    @Transactional(readOnly = true)
    public List<AnalysisTask> getPendingTasks(User user) {
        return analysisTaskRepository.findPendingTasksByUser(user);
    }
    
    /**
     * Search analysis tasks by keyword
     */
    @Transactional(readOnly = true)
    public List<AnalysisTask> searchAnalysisTasks(User user, String keyword) {
        return analysisTaskRepository.searchByKeyword(user, keyword);
    }
    
    /**
     * Get analysis task statistics for a project
     */
    @Transactional(readOnly = true)
    public AnalysisTaskStatistics getAnalysisTaskStatistics(Long projectId, User user) {
        Project project = projectService.getProjectById(projectId, user);
        
        long totalTasks = analysisTaskRepository.findByProjectOrderByCreatedAtDesc(project).size();
        long pendingTasks = analysisTaskRepository.countByProjectAndStatus(project, AnalysisTask.TaskStatus.PENDING);
        long runningTasks = analysisTaskRepository.countByProjectAndStatus(project, AnalysisTask.TaskStatus.RUNNING);
        long completedTasks = analysisTaskRepository.countByProjectAndStatus(project, AnalysisTask.TaskStatus.COMPLETED);
        long failedTasks = analysisTaskRepository.countByProjectAndStatus(project, AnalysisTask.TaskStatus.FAILED);
        
        return new AnalysisTaskStatistics(totalTasks, pendingTasks, runningTasks, completedTasks, failedTasks);
    }
    
    /**
     * Delete an analysis task
     */
    public void deleteAnalysisTask(Long taskId, User user) {
        AnalysisTask task = getAnalysisTaskById(taskId, user);
        
        if (task.getStatus() == AnalysisTask.TaskStatus.RUNNING) {
            throw new RuntimeException("Cannot delete running analysis task");
        }
        
        analysisTaskRepository.delete(task);
        log.info("Deleted analysis task: {} for user: {}", taskId, user.getUsername());
    }
    
    private String generateTaskName(AnalysisTask.AnalysisType analysisType) {
        String timestamp = String.valueOf(System.currentTimeMillis());
        return analysisType.name() + "_" + timestamp.substring(timestamp.length() - 6);
    }
    
    public static class AnalysisTaskStatistics {
        private final long totalTasks;
        private final long pendingTasks;
        private final long runningTasks;
        private final long completedTasks;
        private final long failedTasks;
        
        public AnalysisTaskStatistics(long totalTasks, long pendingTasks, long runningTasks, 
                                    long completedTasks, long failedTasks) {
            this.totalTasks = totalTasks;
            this.pendingTasks = pendingTasks;
            this.runningTasks = runningTasks;
            this.completedTasks = completedTasks;
            this.failedTasks = failedTasks;
        }
        
        // Getters
        public long getTotalTasks() { return totalTasks; }
        public long getPendingTasks() { return pendingTasks; }
        public long getRunningTasks() { return runningTasks; }
        public long getCompletedTasks() { return completedTasks; }
        public long getFailedTasks() { return failedTasks; }
    }
}
