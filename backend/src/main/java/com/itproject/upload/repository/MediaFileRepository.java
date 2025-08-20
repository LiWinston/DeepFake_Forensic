package com.itproject.upload.repository;

import com.itproject.upload.entity.MediaFile;
import com.itproject.auth.entity.User;
import com.itproject.project.entity.Project;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for MediaFile entity operations
 */
@Repository
public interface MediaFileRepository extends JpaRepository<MediaFile, Long> {
    
    /**
     * Find media file by MD5 hash and user
     */
    Optional<MediaFile> findByFileMd5AndUser(String fileMd5, User user);
    
    /**
     * Find media file by MD5 hash
     */
    Optional<MediaFile> findByFileMd5(String fileMd5);
    
    /**
     * Find media files by user
     */
    List<MediaFile> findByUserOrderByCreatedAtDesc(User user);
    
    /**
     * Find media files by user with pagination
     */
    Page<MediaFile> findByUser(User user, Pageable pageable);
    
    /**
     * Find media files by upload status and user
     */
    List<MediaFile> findByUploadStatusAndUser(MediaFile.UploadStatus uploadStatus, User user);
    
    /**
     * Find media files by upload status and user with pagination
     */
    Page<MediaFile> findByUploadStatusAndUser(MediaFile.UploadStatus uploadStatus, User user, Pageable pageable);
    
    /**
     * Find media files by upload status
     */
    List<MediaFile> findByUploadStatus(MediaFile.UploadStatus uploadStatus);
    
    /**
     * Find media files by upload status with pagination
     */
    Page<MediaFile> findByUploadStatus(MediaFile.UploadStatus uploadStatus, Pageable pageable);
    
    /**
     * Find media files by media type and user
     */
    List<MediaFile> findByMediaTypeAndUser(MediaFile.MediaType mediaType, User user);
    
    /**
     * Find media files by media type and user with pagination
     */
    Page<MediaFile> findByMediaTypeAndUser(MediaFile.MediaType mediaType, User user, Pageable pageable);
    
    /**
     * Find media files by media type
     */
    List<MediaFile> findByMediaType(MediaFile.MediaType mediaType);
    
    /**
     * Find media files by media type with pagination
     */
    Page<MediaFile> findByMediaType(MediaFile.MediaType mediaType, Pageable pageable);
    
    /**
     * Find media files by upload status and media type and user with pagination
     */
    Page<MediaFile> findByUploadStatusAndMediaTypeAndUser(MediaFile.UploadStatus uploadStatus, MediaFile.MediaType mediaType, User user, Pageable pageable);
      /**
     * Find media files by upload status and media type with pagination
     */
    Page<MediaFile> findByUploadStatusAndMediaType(MediaFile.UploadStatus uploadStatus, MediaFile.MediaType mediaType, Pageable pageable);
    
    // Project-based queries
    /**
     * Find media files by project with pagination
     */
    Page<MediaFile> findByProject(Project project, Pageable pageable);
    
    /**
     * Find media files by project and upload status with pagination
     */
    Page<MediaFile> findByProjectAndUploadStatus(Project project, MediaFile.UploadStatus uploadStatus, Pageable pageable);
    
    /**
     * Find media files by project and media type with pagination
     */
    Page<MediaFile> findByProjectAndMediaType(Project project, MediaFile.MediaType mediaType, Pageable pageable);
    
    /**
     * Find media files by project, upload status and media type with pagination
     */
    Page<MediaFile> findByProjectAndUploadStatusAndMediaType(Project project, MediaFile.UploadStatus uploadStatus, MediaFile.MediaType mediaType, Pageable pageable);
    
    /**
     * Check if file exists by MD5 and user
     */
    boolean existsByFileMd5AndUser(String fileMd5, User user);
    
    /**
     * Find media files by uploader
     */
    List<MediaFile> findByUploadedByOrderByCreatedAtDesc(String uploadedBy);
    
    /**
     * Check if file exists by MD5
     */
    boolean existsByFileMd5(String fileMd5);
    
    /**
     * Find files by file type
     */
    List<MediaFile> findByFileType(String fileType);
    
    /**
     * Get upload statistics
     */
    @Query("SELECT m.uploadStatus, COUNT(m) FROM MediaFile m GROUP BY m.uploadStatus")
    List<Object[]> getUploadStatistics();
    
    /**
     * Find incomplete uploads older than specified time
     */
    @Query("SELECT m FROM MediaFile m WHERE m.uploadStatus = :status AND m.updatedAt < :beforeTime")
    List<MediaFile> findIncompleteUploadsOlderThan(@Param("status") MediaFile.UploadStatus status, 
                                                   @Param("beforeTime") java.time.LocalDateTime beforeTime);
}
