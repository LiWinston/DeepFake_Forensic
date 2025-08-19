package com.itproject.project.repository;

import com.itproject.project.entity.AnalysisTask;
import com.itproject.project.entity.Project;
import com.itproject.auth.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AnalysisTaskRepository extends JpaRepository<AnalysisTask, Long> {
    
    /**
     * Find all analysis tasks for a specific project
     */
    List<AnalysisTask> findByProjectOrderByCreatedAtDesc(Project project);
    
    /**
     * Find analysis tasks by project and status
     */
    List<AnalysisTask> findByProjectAndStatus(Project project, AnalysisTask.TaskStatus status);
    
    /**
     * Find analysis tasks by project and analysis type
     */
    List<AnalysisTask> findByProjectAndAnalysisType(Project project, AnalysisTask.AnalysisType analysisType);
    
    /**
     * Find analysis tasks assigned to a specific user
     */
    List<AnalysisTask> findByUserOrderByCreatedAtDesc(User user);
    
    /**
     * Find analysis tasks by user and status
     */
    List<AnalysisTask> findByUserAndStatus(User user, AnalysisTask.TaskStatus status);
    
    /**
     * Find running tasks for a user
     */
    @Query("SELECT at FROM AnalysisTask at WHERE at.user = :user AND at.status = 'RUNNING'")
    List<AnalysisTask> findRunningTasksByUser(@Param("user") User user);
    
    /**
     * Find pending tasks for a user
     */
    @Query("SELECT at FROM AnalysisTask at WHERE at.user = :user AND at.status = 'PENDING' ORDER BY at.createdAt ASC")
    List<AnalysisTask> findPendingTasksByUser(@Param("user") User user);
    
    /**
     * Find completed tasks in date range
     */
    @Query("SELECT at FROM AnalysisTask at WHERE at.user = :user AND at.status = 'COMPLETED' AND " +
           "at.completedAt BETWEEN :start AND :end")
    List<AnalysisTask> findCompletedTasksInDateRange(@Param("user") User user, 
                                                     @Param("start") LocalDateTime start, 
                                                     @Param("end") LocalDateTime end);
    
    /**
     * Count tasks by status for a project
     */
    long countByProjectAndStatus(Project project, AnalysisTask.TaskStatus status);
    
    /**
     * Count tasks by analysis type for a user
     */
    long countByUserAndAnalysisType(User user, AnalysisTask.AnalysisType analysisType);
    
    /**
     * Find tasks with confidence score above threshold
     */
    @Query("SELECT at FROM AnalysisTask at WHERE at.project = :project AND " +
           "at.confidenceScore >= :threshold AND at.status = 'COMPLETED'")
    List<AnalysisTask> findTasksWithHighConfidence(@Param("project") Project project, 
                                                   @Param("threshold") Double threshold);
    
    /**
     * Search tasks by keyword in task name or description
     */
    @Query("SELECT at FROM AnalysisTask at WHERE at.user = :user AND " +
           "(LOWER(at.taskName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(at.description) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(at.notes) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    List<AnalysisTask> searchByKeyword(@Param("user") User user, @Param("keyword") String keyword);
}
