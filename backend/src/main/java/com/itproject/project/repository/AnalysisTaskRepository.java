package com.itproject.project.repository;

import com.itproject.analysis.entity.AnalysisTask;
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
    List<AnalysisTask> findByProjectAndStatus(Project project, AnalysisTask.AnalysisStatus status);
    
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
    List<AnalysisTask> findByUserAndStatus(User user, AnalysisTask.AnalysisStatus status);
    
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
    long countByProjectAndStatus(Project project, AnalysisTask.AnalysisStatus status);
    
    /**
     * Count tasks by analysis type for a user
     */
    long countByUserAndAnalysisType(User user, AnalysisTask.AnalysisType analysisType);
    
    /**
     * Search tasks by keyword in description or parameters
     */
    @Query("SELECT at FROM AnalysisTask at WHERE at.user = :user AND " +
           "(LOWER(at.description) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(at.parameters) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    List<AnalysisTask> searchByKeyword(@Param("user") User user, @Param("keyword") String keyword);

       /**
        * Find the latest analysis task by media file MD5, user and analysis type.
        * Used for quickly retrieving the most recent AI (deepfake) result for a file.
        * Orders by ID desc to get the most recently created task (handles null completedAt properly)
        */
       AnalysisTask findTopByMediaFile_FileMd5AndUserAndAnalysisTypeOrderByIdDesc(
                     String fileMd5,
                     User user,
                     AnalysisTask.AnalysisType analysisType
       );

       /**
        * Delete all analysis tasks that reference a specific media file.
        */
       void deleteByMediaFile(com.itproject.upload.entity.MediaFile mediaFile);

       /**
        * Delete all analysis tasks by media file MD5 for safety when entity not available.
        */
       void deleteByMediaFile_FileMd5(String fileMd5);
}
