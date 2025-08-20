package com.itproject.project.service;

import com.itproject.project.entity.Project;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Service for handling project permission checks based on status
 */
@Service
@Slf4j
public class ProjectPermissionService {
    
    /**
     * Check if project allows file upload
     */
    public boolean canUploadFiles(Project project) {
        return project.getStatus() == Project.ProjectStatus.ACTIVE;
    }
    
    /**
     * Check if project allows analysis
     */
    public boolean canAnalyze(Project project) {
        return project.getStatus() == Project.ProjectStatus.ACTIVE;
    }
    
    /**
     * Check if project allows file deletion
     */
    public boolean canDeleteFiles(Project project) {
        return project.getStatus() == Project.ProjectStatus.ACTIVE || 
               project.getStatus() == Project.ProjectStatus.COMPLETED;
    }
    
    /**
     * Check if project allows viewing files
     */
    public boolean canViewFiles(Project project) {
        return project.getStatus() != Project.ProjectStatus.ARCHIVED;
    }
    
    /**
     * Check if project allows editing
     */
    public boolean canEditProject(Project project) {
        return project.getStatus() != Project.ProjectStatus.ARCHIVED;
    }
    
    /**
     * Check if project can be reactivated
     */
    public boolean canReactivate(Project project) {
        return project.getStatus() == Project.ProjectStatus.ARCHIVED;
    }
    
    /**
     * Check if project can be suspended
     */
    public boolean canSuspend(Project project) {
        return project.getStatus() == Project.ProjectStatus.ACTIVE;
    }
    
    /**
     * Check if project can be resumed
     */
    public boolean canResume(Project project) {
        return project.getStatus() == Project.ProjectStatus.SUSPENDED;
    }
    
    /**
     * Check if project can be completed
     */
    public boolean canComplete(Project project) {
        return project.getStatus() == Project.ProjectStatus.ACTIVE || 
               project.getStatus() == Project.ProjectStatus.SUSPENDED;
    }
    
    /**
     * Check if project can be archived
     */
    public boolean canArchive(Project project) {
        return project.getStatus() != Project.ProjectStatus.ARCHIVED;
    }
    
    /**
     * Get error message for permission denial
     */
    public String getPermissionDenialMessage(Project project, String action) {
        String status = project.getStatus().name().toLowerCase();
        return String.format("Cannot %s for %s project. Current project status: %s", 
                            action, status, project.getStatus().name());
    }
    
    /**
     * Validate permission and throw exception if denied
     */
    public void validatePermission(Project project, String action, boolean allowed) {
        if (!allowed) {
            String message = getPermissionDenialMessage(project, action);
            log.warn("Permission denied: {}", message);
            throw new RuntimeException(message);
        }
    }
}
