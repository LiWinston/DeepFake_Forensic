package com.itproject.project.service;

import com.itproject.project.entity.Project;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class ProjectPermissionServiceTest {

    private ProjectPermissionService projectPermissionService;

    @BeforeEach
    void setUp() {
        projectPermissionService = new ProjectPermissionService();
    }

    @Test
    void testCanUploadFiles_ActiveProject() {
        Project project = createProject(Project.ProjectStatus.ACTIVE);
        
        boolean canUpload = projectPermissionService.canUploadFiles(project);
        
        assertTrue(canUpload);
    }

    @Test
    void testCanUploadFiles_CompletedProject() {
        Project project = createProject(Project.ProjectStatus.COMPLETED);
        
        boolean canUpload = projectPermissionService.canUploadFiles(project);
        
        assertFalse(canUpload);
    }

    @Test
    void testCanUploadFiles_SuspendedProject() {
        Project project = createProject(Project.ProjectStatus.SUSPENDED);
        
        boolean canUpload = projectPermissionService.canUploadFiles(project);
        
        assertFalse(canUpload);
    }

    @Test
    void testCanUploadFiles_ArchivedProject() {
        Project project = createProject(Project.ProjectStatus.ARCHIVED);
        
        boolean canUpload = projectPermissionService.canUploadFiles(project);
        
        assertFalse(canUpload);
    }

    @Test
    void testCanAnalyze_ActiveProject() {
        Project project = createProject(Project.ProjectStatus.ACTIVE);
        
        boolean canAnalyze = projectPermissionService.canAnalyze(project);
        
        assertTrue(canAnalyze);
    }

    @Test
    void testCanAnalyze_NonActiveProject() {
        Project project = createProject(Project.ProjectStatus.COMPLETED);
        
        boolean canAnalyze = projectPermissionService.canAnalyze(project);
        
        assertFalse(canAnalyze);
    }

    @Test
    void testCanDeleteFiles_ActiveProject() {
        Project project = createProject(Project.ProjectStatus.ACTIVE);
        
        boolean canDelete = projectPermissionService.canDeleteFiles(project);
        
        assertTrue(canDelete);
    }

    @Test
    void testCanDeleteFiles_CompletedProject() {
        Project project = createProject(Project.ProjectStatus.COMPLETED);
        
        boolean canDelete = projectPermissionService.canDeleteFiles(project);
        
        assertTrue(canDelete);
    }

    @Test
    void testCanDeleteFiles_SuspendedProject() {
        Project project = createProject(Project.ProjectStatus.SUSPENDED);
        
        boolean canDelete = projectPermissionService.canDeleteFiles(project);
        
        assertFalse(canDelete);
    }

    @Test
    void testCanViewFiles_ActiveProject() {
        Project project = createProject(Project.ProjectStatus.ACTIVE);
        
        boolean canView = projectPermissionService.canViewFiles(project);
        
        assertTrue(canView);
    }

    @Test
    void testCanViewFiles_ArchivedProject() {
        Project project = createProject(Project.ProjectStatus.ARCHIVED);
        
        boolean canView = projectPermissionService.canViewFiles(project);
        
        assertFalse(canView);
    }

    @Test
    void testCanEditProject_ActiveProject() {
        Project project = createProject(Project.ProjectStatus.ACTIVE);
        
        boolean canEdit = projectPermissionService.canEditProject(project);
        
        assertTrue(canEdit);
    }

    @Test
    void testCanEditProject_ArchivedProject() {
        Project project = createProject(Project.ProjectStatus.ARCHIVED);
        
        boolean canEdit = projectPermissionService.canEditProject(project);
        
        assertFalse(canEdit);
    }

    @Test
    void testCanReactivate_ArchivedProject() {
        Project project = createProject(Project.ProjectStatus.ARCHIVED);
        
        boolean canReactivate = projectPermissionService.canReactivate(project);
        
        assertTrue(canReactivate);
    }

    @Test
    void testCanReactivate_ActiveProject() {
        Project project = createProject(Project.ProjectStatus.ACTIVE);
        
        boolean canReactivate = projectPermissionService.canReactivate(project);
        
        assertFalse(canReactivate);
    }

    @Test
    void testCanSuspend_ActiveProject() {
        Project project = createProject(Project.ProjectStatus.ACTIVE);
        
        boolean canSuspend = projectPermissionService.canSuspend(project);
        
        assertTrue(canSuspend);
    }

    @Test
    void testCanSuspend_SuspendedProject() {
        Project project = createProject(Project.ProjectStatus.SUSPENDED);
        
        boolean canSuspend = projectPermissionService.canSuspend(project);
        
        assertFalse(canSuspend);
    }

    @Test
    void testCanResume_SuspendedProject() {
        Project project = createProject(Project.ProjectStatus.SUSPENDED);
        
        boolean canResume = projectPermissionService.canResume(project);
        
        assertTrue(canResume);
    }

    @Test
    void testCanResume_ActiveProject() {
        Project project = createProject(Project.ProjectStatus.ACTIVE);
        
        boolean canResume = projectPermissionService.canResume(project);
        
        assertFalse(canResume);
    }

    @Test
    void testCanComplete_ActiveProject() {
        Project project = createProject(Project.ProjectStatus.ACTIVE);
        
        boolean canComplete = projectPermissionService.canComplete(project);
        
        assertTrue(canComplete);
    }

    @Test
    void testCanComplete_SuspendedProject() {
        Project project = createProject(Project.ProjectStatus.SUSPENDED);
        
        boolean canComplete = projectPermissionService.canComplete(project);
        
        assertTrue(canComplete);
    }

    @Test
    void testCanComplete_CompletedProject() {
        Project project = createProject(Project.ProjectStatus.COMPLETED);
        
        boolean canComplete = projectPermissionService.canComplete(project);
        
        assertFalse(canComplete);
    }

    @Test
    void testCanArchive_ActiveProject() {
        Project project = createProject(Project.ProjectStatus.ACTIVE);
        
        boolean canArchive = projectPermissionService.canArchive(project);
        
        assertTrue(canArchive);
    }

    @Test
    void testCanArchive_ArchivedProject() {
        Project project = createProject(Project.ProjectStatus.ARCHIVED);
        
        boolean canArchive = projectPermissionService.canArchive(project);
        
        assertFalse(canArchive);
    }

    @Test
    void testGetPermissionDenialMessage_ActiveProject() {
        Project project = createProject(Project.ProjectStatus.ACTIVE);
        
        String message = projectPermissionService.getPermissionDenialMessage(project, "upload");
        
        assertNotNull(message);
        assertTrue(message.contains("Cannot upload"));
        assertTrue(message.contains("active"));
        assertTrue(message.contains("ACTIVE"));
    }

    @Test
    void testGetPermissionDenialMessage_ArchivedProject() {
        Project project = createProject(Project.ProjectStatus.ARCHIVED);
        
        String message = projectPermissionService.getPermissionDenialMessage(project, "edit");
        
        assertNotNull(message);
        assertTrue(message.contains("Cannot edit"));
        assertTrue(message.contains("archived"));
        assertTrue(message.contains("ARCHIVED"));
    }

    @Test
    void testValidatePermission_Allowed() {
        Project project = createProject(Project.ProjectStatus.ACTIVE);
        
        // Should not throw exception
        assertDoesNotThrow(() -> {
            projectPermissionService.validatePermission(project, "upload", true);
        });
    }

    @Test
    void testValidatePermission_Denied() {
        Project project = createProject(Project.ProjectStatus.ARCHIVED);
        
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            projectPermissionService.validatePermission(project, "upload", false);
        });
        
        assertNotNull(exception.getMessage());
        assertTrue(exception.getMessage().contains("Cannot upload"));
    }

    private Project createProject(Project.ProjectStatus status) {
        Project project = new Project();
        project.setId(1L);
        project.setName("Test Project");
        project.setStatus(status);
        return project;
    }
}
