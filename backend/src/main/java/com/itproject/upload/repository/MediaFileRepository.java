package com.itproject.upload.repository;

import com.itproject.upload.entity.MediaFile;
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
     * Find media file by MD5 hash
     */
    Optional<MediaFile> findByFileMd5(String fileMd5);
    
    /**
     * Find media files by upload status
     */
    List<MediaFile> findByUploadStatus(MediaFile.UploadStatus uploadStatus);
    
    /**
     * Find media files by upload status with pagination
     */
    Page<MediaFile> findByUploadStatus(MediaFile.UploadStatus uploadStatus, Pageable pageable);
    
    /**
     * Find media files by media type
     */
    List<MediaFile> findByMediaType(MediaFile.MediaType mediaType);
    
    /**
     * Find media files by media type with pagination
     */
    Page<MediaFile> findByMediaType(MediaFile.MediaType mediaType, Pageable pageable);
    
    /**
     * Find media files by upload status and media type with pagination
     */
    Page<MediaFile> findByUploadStatusAndMediaType(MediaFile.UploadStatus uploadStatus, MediaFile.MediaType mediaType, Pageable pageable);
    
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
