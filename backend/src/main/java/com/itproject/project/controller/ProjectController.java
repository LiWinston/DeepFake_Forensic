package com.itproject.project.controller;

import com.itproject.project.entity.Project;
import com.itproject.project.service.ProjectService;
import com.itproject.project.dto.CreateProjectRequest;
import com.itproject.auth.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/projects")
@RequiredArgsConstructor
@Slf4j
public class ProjectController {
    
    private final ProjectService projectService;
    
    /**
     * Create a new project
     */
    @PostMapping
    public ResponseEntity<Project> createProject(
            @Valid @RequestBody Project project,
            @AuthenticationPrincipal User user) {
        
        Project createdProject = projectService.createProject(project, user);
        return ResponseEntity.ok(createdProject);
    }
    
    /**
     * Get all projects for the current user
     */
    @GetMapping
    public ResponseEntity<List<Project>> getUserProjects(@AuthenticationPrincipal User user) {
        List<Project> projects = projectService.getUserProjects(user);
        return ResponseEntity.ok(projects);
    }
    
    /**
     * Get a specific project by ID
     */
    @GetMapping("/{projectId}")
    public ResponseEntity<Project> getProject(
            @PathVariable Long projectId,
            @AuthenticationPrincipal User user) {
        
        Project project = projectService.getProjectById(projectId, user);
        return ResponseEntity.ok(project);
    }
    
    /**
     * Update a project
     */
    @PutMapping("/{projectId}")
    public ResponseEntity<Project> updateProject(
            @PathVariable Long projectId,
            @Valid @RequestBody Project project,
            @AuthenticationPrincipal User user) {
        
        Project updatedProject = projectService.updateProject(projectId, project, user);
        return ResponseEntity.ok(updatedProject);
    }
    
    /**
     * Delete a project (archive it)
     */
    @DeleteMapping("/{projectId}")
    public ResponseEntity<Void> deleteProject(
            @PathVariable Long projectId,
            @AuthenticationPrincipal User user) {
        
        projectService.deleteProject(projectId, user);
        return ResponseEntity.noContent().build();
    }
      /**
     * Archive a project
     */
    @PutMapping("/{projectId}/archive")
    public ResponseEntity<Project> archiveProject(
            @PathVariable Long projectId,
            @AuthenticationPrincipal User user) {
        
        Project archivedProject = projectService.archiveProject(projectId, user);
        return ResponseEntity.ok(archivedProject);
    }
    
    /**
     * Reactivate an archived project
     */
    @PutMapping("/{projectId}/reactivate")
    public ResponseEntity<Project> reactivateProject(
            @PathVariable Long projectId,
            @AuthenticationPrincipal User user) {
        
        try {
            Project reactivatedProject = projectService.reactivateProject(projectId, user);
            return ResponseEntity.ok(reactivatedProject);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().build();
        }
    }
    
    /**
     * Suspend a project
     */
    @PutMapping("/{projectId}/suspend")
    public ResponseEntity<Project> suspendProject(
            @PathVariable Long projectId,
            @AuthenticationPrincipal User user) {
        
        try {
            Project suspendedProject = projectService.suspendProject(projectId, user);
            return ResponseEntity.ok(suspendedProject);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().build();
        }
    }
    
    /**
     * Resume a suspended project
     */
    @PutMapping("/{projectId}/resume")
    public ResponseEntity<Project> resumeProject(
            @PathVariable Long projectId,
            @AuthenticationPrincipal User user) {
        
        try {
            Project resumedProject = projectService.resumeProject(projectId, user);
            return ResponseEntity.ok(resumedProject);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().build();
        }
    }
    
    /**
     * Complete a project
     */
    @PutMapping("/{projectId}/complete")
    public ResponseEntity<Project> completeProject(
            @PathVariable Long projectId,
            @AuthenticationPrincipal User user) {
        
        try {
            Project completedProject = projectService.completeProject(projectId, user);
            return ResponseEntity.ok(completedProject);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().build();
        }
    }
    
    /**
     * Get archived projects
     */
    @GetMapping("/archived")
    public ResponseEntity<List<Project>> getArchivedProjects(
            @AuthenticationPrincipal User user) {
        
        List<Project> projects = projectService.getArchivedProjects(user);
        return ResponseEntity.ok(projects);
    }
    
    /**
     * Get projects filtered by creation date
     */
    @GetMapping("/filter/created")
    public ResponseEntity<List<Project>> getProjectsByCreatedDate(
            @RequestParam(required = false) String before,
            @RequestParam(required = false) String after,
            @AuthenticationPrincipal User user) {
        
        LocalDateTime beforeDate = before != null ? LocalDateTime.parse(before + "T23:59:59") : null;
        LocalDateTime afterDate = after != null ? LocalDateTime.parse(after + "T00:00:00") : null;
        
        List<Project> projects = projectService.getProjectsByCreatedDate(user, beforeDate, afterDate);
        return ResponseEntity.ok(projects);
    }
    
    /**
     * Get projects filtered by deadline
     */
    @GetMapping("/filter/deadline")
    public ResponseEntity<List<Project>> getProjectsByDeadline(
            @RequestParam(required = false) String before,
            @RequestParam(required = false) String after,
            @AuthenticationPrincipal User user) {
        
        LocalDateTime beforeDate = before != null ? LocalDateTime.parse(before + "T23:59:59") : null;
        LocalDateTime afterDate = after != null ? LocalDateTime.parse(after + "T00:00:00") : null;
        
        List<Project> projects = projectService.getProjectsByDeadline(user, beforeDate, afterDate);
        return ResponseEntity.ok(projects);
    }
    
    /**
     * Get projects by status
     */
    @GetMapping("/status/{status}")
    public ResponseEntity<List<Project>> getProjectsByStatus(
            @PathVariable Project.ProjectStatus status,
            @AuthenticationPrincipal User user) {
        
        List<Project> projects = projectService.getProjectsByStatus(user, status);
        return ResponseEntity.ok(projects);
    }
    
    /**
     * Get projects by type
     */
    @GetMapping("/type/{type}")
    public ResponseEntity<List<Project>> getProjectsByType(
            @PathVariable Project.ProjectType type,
            @AuthenticationPrincipal User user) {
        
        List<Project> projects = projectService.getProjectsByType(user, type);
        return ResponseEntity.ok(projects);
    }
    
    /**
     * Search projects by keyword
     */
    @GetMapping("/search")
    public ResponseEntity<List<Project>> searchProjects(
            @RequestParam String keyword,
            @AuthenticationPrincipal User user) {
        
        List<Project> projects = projectService.searchProjects(user, keyword);
        return ResponseEntity.ok(projects);
    }
    
    /**
     * Get project by case number
     */
    @GetMapping("/case/{caseNumber}")
    public ResponseEntity<Project> getProjectByCaseNumber(
            @PathVariable String caseNumber,
            @AuthenticationPrincipal User user) {
        
        Optional<Project> project = projectService.getProjectByCaseNumber(caseNumber, user);
        return project.map(ResponseEntity::ok)
                     .orElse(ResponseEntity.notFound().build());
    }
    
    /**
     * Get active projects
     */
    @GetMapping("/active")
    public ResponseEntity<List<Project>> getActiveProjects(@AuthenticationPrincipal User user) {
        List<Project> projects = projectService.getActiveProjects(user);
        return ResponseEntity.ok(projects);
    }
    
    /**
     * Get projects with upcoming deadlines
     */
    @GetMapping("/deadlines")
    public ResponseEntity<List<Project>> getProjectsWithUpcomingDeadlines(
            @RequestParam(defaultValue = "7") int daysAhead,
            @AuthenticationPrincipal User user) {
        
        List<Project> projects = projectService.getProjectsWithUpcomingDeadlines(user, daysAhead);
        return ResponseEntity.ok(projects);
    }
    
    /**
     * Get project statistics
     */
    @GetMapping("/statistics")
    public ResponseEntity<ProjectService.ProjectStatistics> getProjectStatistics(
            @AuthenticationPrincipal User user) {
        
        ProjectService.ProjectStatistics statistics = projectService.getProjectStatistics(user);
        return ResponseEntity.ok(statistics);
    }
}
