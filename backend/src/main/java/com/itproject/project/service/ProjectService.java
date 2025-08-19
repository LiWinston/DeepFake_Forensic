package com.itproject.project.service;

import com.itproject.project.entity.Project;
import com.itproject.project.repository.ProjectRepository;
import com.itproject.auth.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class ProjectService {
    
    private final ProjectRepository projectRepository;
    
    /**
     * Create a new project
     */
    public Project createProject(Project project, User user) {
        project.setUser(user);
        project.setStatus(Project.ProjectStatus.ACTIVE);
        
        // Generate case number if not provided
        if (project.getCaseNumber() == null || project.getCaseNumber().isEmpty()) {
            project.setCaseNumber(generateCaseNumber());
        }
        
        log.info("Creating new project: {} for user: {}", project.getName(), user.getUsername());
        return projectRepository.save(project);
    }
    
    /**
     * Update an existing project
     */
    public Project updateProject(Long projectId, Project updatedProject, User user) {
        Project existingProject = getProjectById(projectId, user);
        
        // Update fields
        existingProject.setName(updatedProject.getName());
        existingProject.setDescription(updatedProject.getDescription());
        existingProject.setClientName(updatedProject.getClientName());
        existingProject.setClientContact(updatedProject.getClientContact());
        existingProject.setProjectType(updatedProject.getProjectType());
        existingProject.setDeadline(updatedProject.getDeadline());
        existingProject.setCaseDate(updatedProject.getCaseDate());
        existingProject.setEvidenceDescription(updatedProject.getEvidenceDescription());
        existingProject.setNotes(updatedProject.getNotes());
        
        if (updatedProject.getStatus() != null) {
            existingProject.setStatus(updatedProject.getStatus());
        }
        
        log.info("Updating project: {} for user: {}", projectId, user.getUsername());
        return projectRepository.save(existingProject);
    }
    
    /**
     * Get project by ID (with user authorization check)
     */
    @Transactional(readOnly = true)
    public Project getProjectById(Long projectId, User user) {
        return projectRepository.findById(projectId)
                .filter(project -> project.getUser().getId().equals(user.getId()))
                .orElseThrow(() -> new RuntimeException("Project not found or access denied"));
    }
    
    /**
     * Get all projects for a user
     */
    @Transactional(readOnly = true)
    public List<Project> getUserProjects(User user) {
        return projectRepository.findByUserOrderByCreatedAtDesc(user);
    }
    
    /**
     * Get projects by status
     */
    @Transactional(readOnly = true)
    public List<Project> getProjectsByStatus(User user, Project.ProjectStatus status) {
        return projectRepository.findByUserAndStatus(user, status);
    }
    
    /**
     * Get projects by type
     */
    @Transactional(readOnly = true)
    public List<Project> getProjectsByType(User user, Project.ProjectType projectType) {
        return projectRepository.findByUserAndProjectType(user, projectType);
    }
    
    /**
     * Search projects by keyword
     */
    @Transactional(readOnly = true)
    public List<Project> searchProjects(User user, String keyword) {
        return projectRepository.searchByKeyword(user, keyword);
    }
    
    /**
     * Get project by case number
     */
    @Transactional(readOnly = true)
    public Optional<Project> getProjectByCaseNumber(String caseNumber, User user) {
        return projectRepository.findByCaseNumberAndUser(caseNumber, user);
    }
    
    /**
     * Archive a project
     */
    public Project archiveProject(Long projectId, User user) {
        Project project = getProjectById(projectId, user);
        project.setStatus(Project.ProjectStatus.ARCHIVED);
        
        log.info("Archiving project: {} for user: {}", projectId, user.getUsername());
        return projectRepository.save(project);
    }
    
    /**
     * Delete a project (soft delete by archiving)
     */
    public void deleteProject(Long projectId, User user) {
        Project project = getProjectById(projectId, user);
        
        // Check if project can be deleted (no active analysis tasks)
        if (hasActiveAnalysisTasks(project)) {
            throw new RuntimeException("Cannot delete project with active analysis tasks");
        }
        
        project.setStatus(Project.ProjectStatus.ARCHIVED);
        projectRepository.save(project);
        
        log.info("Deleted (archived) project: {} for user: {}", projectId, user.getUsername());
    }
    
    /**
     * Get projects with approaching deadlines
     */
    @Transactional(readOnly = true)
    public List<Project> getProjectsWithUpcomingDeadlines(User user, int daysAhead) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime future = now.plusDays(daysAhead);
        return projectRepository.findProjectsWithDeadlineBetween(user, now, future);
    }
    
    /**
     * Get active projects for a user
     */
    @Transactional(readOnly = true)
    public List<Project> getActiveProjects(User user) {
        return projectRepository.findActiveProjectsByUser(user);
    }
    
    /**
     * Get project statistics for a user
     */
    @Transactional(readOnly = true)
    public ProjectStatistics getProjectStatistics(User user) {
        List<Project> allProjects = projectRepository.findByUserOrderByCreatedAtDesc(user);
        long totalProjects = allProjects.size();
        long activeProjects = projectRepository.countByUserAndStatus(user, Project.ProjectStatus.ACTIVE);
        long completedProjects = projectRepository.countByUserAndStatus(user, Project.ProjectStatus.COMPLETED);
        long archivedProjects = projectRepository.countByUserAndStatus(user, Project.ProjectStatus.ARCHIVED);
        
        return new ProjectStatistics(totalProjects, activeProjects, completedProjects, archivedProjects);
    }
    
    private String generateCaseNumber() {
        // Generate a unique case number
        String timestamp = String.valueOf(System.currentTimeMillis());
        return "CASE-" + timestamp.substring(timestamp.length() - 8);
    }
    
    private boolean hasActiveAnalysisTasks(Project project) {
        // This would check if there are any running analysis tasks
        // Implementation depends on AnalysisTaskService
        return false; // Placeholder
    }
    
    public static class ProjectStatistics {
        private final long totalProjects;
        private final long activeProjects;
        private final long completedProjects;
        private final long archivedProjects;
        
        public ProjectStatistics(long totalProjects, long activeProjects, long completedProjects, long archivedProjects) {
            this.totalProjects = totalProjects;
            this.activeProjects = activeProjects;
            this.completedProjects = completedProjects;
            this.archivedProjects = archivedProjects;
        }
        
        // Getters
        public long getTotalProjects() { return totalProjects; }
        public long getActiveProjects() { return activeProjects; }
        public long getCompletedProjects() { return completedProjects; }
        public long getArchivedProjects() { return archivedProjects; }
    }
}
