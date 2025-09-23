package com.itproject.project.entity;

import com.itproject.auth.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class ProjectTest {

    private Project project;

    @BeforeEach
    void setUp() {
        project = new Project();
    }

    @Test
    void testDefaultConstructor() {
        Project newProject = new Project();
        
        assertNull(newProject.getId());
        assertNull(newProject.getName());
        assertNull(newProject.getDescription());
        assertEquals(Project.ProjectStatus.ACTIVE, newProject.getStatus());
        assertEquals(Project.ProjectType.GENERAL, newProject.getProjectType());
        assertNull(newProject.getCaseNumber());
        assertNull(newProject.getClientName());
        assertNull(newProject.getClientContact());
        assertNull(newProject.getTags());
        assertNull(newProject.getDeadline());
        assertNull(newProject.getCaseDate());
        assertNull(newProject.getEvidenceDescription());
        assertNull(newProject.getNotes());
        assertNull(newProject.getCreatedAt());
        assertNull(newProject.getUpdatedAt());
        assertNull(newProject.getUser());
    }

    @Test
    void testAllArgsConstructor() {
        LocalDateTime now = LocalDateTime.now();
        User user = new User();
        user.setId(1L);
        
        Project projectWithArgs = new Project(
            1L, "Test Project", "Test Description", 
            Project.ProjectStatus.ACTIVE, Project.ProjectType.CRIMINAL,
            "CASE-001", "Test Client", "client@test.com", "tag1,tag2",
            now.plusDays(30), now.minusDays(1), "Evidence desc", 
            "Notes", now, now.plusHours(1), user
        );
        
        assertEquals(1L, projectWithArgs.getId());
        assertEquals("Test Project", projectWithArgs.getName());
        assertEquals("Test Description", projectWithArgs.getDescription());
        assertEquals(Project.ProjectStatus.ACTIVE, projectWithArgs.getStatus());
        assertEquals(Project.ProjectType.CRIMINAL, projectWithArgs.getProjectType());
        assertEquals("CASE-001", projectWithArgs.getCaseNumber());
        assertEquals("Test Client", projectWithArgs.getClientName());
        assertEquals("client@test.com", projectWithArgs.getClientContact());
        assertEquals("tag1,tag2", projectWithArgs.getTags());
        assertEquals(now.plusDays(30), projectWithArgs.getDeadline());
        assertEquals(now.minusDays(1), projectWithArgs.getCaseDate());
        assertEquals("Evidence desc", projectWithArgs.getEvidenceDescription());
        assertEquals("Notes", projectWithArgs.getNotes());
        assertEquals(now, projectWithArgs.getCreatedAt());
        assertEquals(now.plusHours(1), projectWithArgs.getUpdatedAt());
        assertEquals(user, projectWithArgs.getUser());
    }

    @Test
    void testSettersAndGetters() {
        LocalDateTime now = LocalDateTime.now();
        User user = new User();
        user.setId(1L);
        
        // Test setters
        project.setId(1L);
        project.setName("Test Project");
        project.setDescription("Test Description");
        project.setStatus(Project.ProjectStatus.COMPLETED);
        project.setProjectType(Project.ProjectType.CIVIL);
        project.setCaseNumber("CASE-002");
        project.setClientName("Test Client 2");
        project.setClientContact("client2@test.com");
        project.setTags("tag3,tag4");
        project.setDeadline(now.plusDays(60));
        project.setCaseDate(now.minusDays(2));
        project.setEvidenceDescription("Evidence description 2");
        project.setNotes("Notes 2");
        project.setCreatedAt(now);
        project.setUpdatedAt(now.plusHours(2));
        project.setUser(user);
        
        // Test getters
        assertEquals(1L, project.getId());
        assertEquals("Test Project", project.getName());
        assertEquals("Test Description", project.getDescription());
        assertEquals(Project.ProjectStatus.COMPLETED, project.getStatus());
        assertEquals(Project.ProjectType.CIVIL, project.getProjectType());
        assertEquals("CASE-002", project.getCaseNumber());
        assertEquals("Test Client 2", project.getClientName());
        assertEquals("client2@test.com", project.getClientContact());
        assertEquals("tag3,tag4", project.getTags());
        assertEquals(now.plusDays(60), project.getDeadline());
        assertEquals(now.minusDays(2), project.getCaseDate());
        assertEquals("Evidence description 2", project.getEvidenceDescription());
        assertEquals("Notes 2", project.getNotes());
        assertEquals(now, project.getCreatedAt());
        assertEquals(now.plusHours(2), project.getUpdatedAt());
        assertEquals(user, project.getUser());
    }

    @Test
    void testProjectStatusEnum() {
        Project.ProjectStatus[] statuses = Project.ProjectStatus.values();
        
        assertEquals(4, statuses.length);
        assertEquals(Project.ProjectStatus.ACTIVE, Project.ProjectStatus.valueOf("ACTIVE"));
        assertEquals(Project.ProjectStatus.COMPLETED, Project.ProjectStatus.valueOf("COMPLETED"));
        assertEquals(Project.ProjectStatus.SUSPENDED, Project.ProjectStatus.valueOf("SUSPENDED"));
        assertEquals(Project.ProjectStatus.ARCHIVED, Project.ProjectStatus.valueOf("ARCHIVED"));
    }

    @Test
    void testProjectTypeEnum() {
        Project.ProjectType[] types = Project.ProjectType.values();
        
        assertEquals(5, types.length);
        assertEquals(Project.ProjectType.GENERAL, Project.ProjectType.valueOf("GENERAL"));
        assertEquals(Project.ProjectType.CRIMINAL, Project.ProjectType.valueOf("CRIMINAL"));
        assertEquals(Project.ProjectType.CIVIL, Project.ProjectType.valueOf("CIVIL"));
        assertEquals(Project.ProjectType.CORPORATE, Project.ProjectType.valueOf("CORPORATE"));
        assertEquals(Project.ProjectType.ACADEMIC_RESEARCH, Project.ProjectType.valueOf("ACADEMIC_RESEARCH"));
    }

    @Test
    void testDefaultStatusAndType() {
        Project newProject = new Project();
        
        // Test default values
        assertEquals(Project.ProjectStatus.ACTIVE, newProject.getStatus());
        assertEquals(Project.ProjectType.GENERAL, newProject.getProjectType());
    }

    @Test
    void testNullValues() {
        project.setName(null);
        project.setDescription(null);
        project.setCaseNumber(null);
        project.setClientName(null);
        project.setClientContact(null);
        project.setTags(null);
        project.setDeadline(null);
        project.setCaseDate(null);
        project.setEvidenceDescription(null);
        project.setNotes(null);
        project.setUser(null);
        
        assertNull(project.getName());
        assertNull(project.getDescription());
        assertNull(project.getCaseNumber());
        assertNull(project.getClientName());
        assertNull(project.getClientContact());
        assertNull(project.getTags());
        assertNull(project.getDeadline());
        assertNull(project.getCaseDate());
        assertNull(project.getEvidenceDescription());
        assertNull(project.getNotes());
        assertNull(project.getUser());
    }

    @Test
    void testStatusTransitions() {
        project.setStatus(Project.ProjectStatus.ACTIVE);
        assertEquals(Project.ProjectStatus.ACTIVE, project.getStatus());
        
        project.setStatus(Project.ProjectStatus.SUSPENDED);
        assertEquals(Project.ProjectStatus.SUSPENDED, project.getStatus());
        
        project.setStatus(Project.ProjectStatus.COMPLETED);
        assertEquals(Project.ProjectStatus.COMPLETED, project.getStatus());
        
        project.setStatus(Project.ProjectStatus.ARCHIVED);
        assertEquals(Project.ProjectStatus.ARCHIVED, project.getStatus());
    }

    @Test
    void testProjectTypeChanges() {
        project.setProjectType(Project.ProjectType.GENERAL);
        assertEquals(Project.ProjectType.GENERAL, project.getProjectType());
        
        project.setProjectType(Project.ProjectType.CRIMINAL);
        assertEquals(Project.ProjectType.CRIMINAL, project.getProjectType());
        
        project.setProjectType(Project.ProjectType.CIVIL);
        assertEquals(Project.ProjectType.CIVIL, project.getProjectType());
        
        project.setProjectType(Project.ProjectType.CORPORATE);
        assertEquals(Project.ProjectType.CORPORATE, project.getProjectType());
        
        project.setProjectType(Project.ProjectType.ACADEMIC_RESEARCH);
        assertEquals(Project.ProjectType.ACADEMIC_RESEARCH, project.getProjectType());
    }

    @Test
    void testLongStringFields() {
        String longName = "A".repeat(200); // Max length for name
        String longDescription = "B".repeat(1000); // Max length for description
        String longCaseNumber = "C".repeat(100); // Max length for case number
        String longClientName = "D".repeat(200); // Max length for client name
        String longClientContact = "E".repeat(255); // Max length for client contact
        String longTags = "F".repeat(500); // Max length for tags
        String longEvidenceDescription = "G".repeat(1000); // Max length for evidence description
        String longNotes = "H".repeat(2000); // Max length for notes
        
        project.setName(longName);
        project.setDescription(longDescription);
        project.setCaseNumber(longCaseNumber);
        project.setClientName(longClientName);
        project.setClientContact(longClientContact);
        project.setTags(longTags);
        project.setEvidenceDescription(longEvidenceDescription);
        project.setNotes(longNotes);
        
        assertEquals(longName, project.getName());
        assertEquals(longDescription, project.getDescription());
        assertEquals(longCaseNumber, project.getCaseNumber());
        assertEquals(longClientName, project.getClientName());
        assertEquals(longClientContact, project.getClientContact());
        assertEquals(longTags, project.getTags());
        assertEquals(longEvidenceDescription, project.getEvidenceDescription());
        assertEquals(longNotes, project.getNotes());
    }

    @Test
    void testDateTimeFields() {
        LocalDateTime pastDate = LocalDateTime.now().minusDays(10);
        LocalDateTime futureDate = LocalDateTime.now().plusDays(10);
        LocalDateTime now = LocalDateTime.now();
        
        project.setDeadline(futureDate);
        project.setCaseDate(pastDate);
        project.setCreatedAt(now);
        project.setUpdatedAt(now.plusHours(1));
        
        assertEquals(futureDate, project.getDeadline());
        assertEquals(pastDate, project.getCaseDate());
        assertEquals(now, project.getCreatedAt());
        assertEquals(now.plusHours(1), project.getUpdatedAt());
    }
}
