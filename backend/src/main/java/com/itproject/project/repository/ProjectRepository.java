package com.itproject.project.repository;

import com.itproject.project.entity.Project;
import com.itproject.auth.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProjectRepository extends JpaRepository<Project, Long> {
    
    /**
     * Find all projects for a specific user
     */
    List<Project> findByUserOrderByCreatedAtDesc(User user);
    
    /**
     * Find projects by user and status
     */
    List<Project> findByUserAndStatus(User user, Project.ProjectStatus status);
    
    /**
     * Find projects by user and type
     */
    List<Project> findByUserAndProjectType(User user, Project.ProjectType projectType);
    
    /**
     * Find project by case number (unique identifier)
     */
    Optional<Project> findByCaseNumber(String caseNumber);
    
    /**
     * Find project by case number and user (for security)
     */
    Optional<Project> findByCaseNumberAndUser(String caseNumber, User user);
    
    /**
     * Search projects by name containing keyword
     */
    @Query("SELECT p FROM Project p WHERE p.user = :user AND " +
           "(LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(p.description) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(p.caseNumber) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    List<Project> searchByKeyword(@Param("user") User user, @Param("keyword") String keyword);
    
    /**
     * Find projects with deadline approaching
     */
    @Query("SELECT p FROM Project p WHERE p.user = :user AND p.deadline BETWEEN :start AND :end")
    List<Project> findProjectsWithDeadlineBetween(@Param("user") User user, 
                                                  @Param("start") LocalDateTime start, 
                                                  @Param("end") LocalDateTime end);
    
    /**
     * Count projects by status for a user
     */
    long countByUserAndStatus(User user, Project.ProjectStatus status);
    
    /**
     * Find active projects (not archived or completed)
     */
    @Query("SELECT p FROM Project p WHERE p.user = :user AND p.status NOT IN ('ARCHIVED', 'COMPLETED')")
    List<Project> findActiveProjectsByUser(@Param("user") User user);
}
