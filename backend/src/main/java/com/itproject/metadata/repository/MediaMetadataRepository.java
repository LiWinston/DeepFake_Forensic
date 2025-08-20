package com.itproject.metadata.repository;

import com.itproject.metadata.entity.MediaMetadata;
import com.itproject.auth.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for MediaMetadata entity operations
 */
@Repository
public interface MediaMetadataRepository extends JpaRepository<MediaMetadata, Long> {
    
    /**
     * Find metadata by file MD5 and user
     */
    Optional<MediaMetadata> findByFileMd5AndUser(String fileMd5, User user);
    
    /**
     * Find metadata by file MD5
     */
    Optional<MediaMetadata> findByFileMd5(String fileMd5);
    
    /**
     * Find metadata by user
     */
    List<MediaMetadata> findByUserOrderByCreatedAtDesc(User user);
    
    /**
     * Find metadata by extraction status and user
     */
    List<MediaMetadata> findByExtractionStatusAndUser(MediaMetadata.ExtractionStatus status, User user);
    
    /**
     * Find metadata by extraction status
     */
    List<MediaMetadata> findByExtractionStatus(MediaMetadata.ExtractionStatus status);
    
    /**
     * Find metadata with suspicious indicators for user
     */
    @Query("SELECT m FROM MediaMetadata m WHERE m.user = :user AND m.suspiciousIndicators IS NOT NULL AND m.suspiciousIndicators != ''")
    List<MediaMetadata> findWithSuspiciousIndicatorsByUser(@Param("user") User user);
    
    /**
     * Find metadata with suspicious indicators
     */
    @Query("SELECT m FROM MediaMetadata m WHERE m.suspiciousIndicators IS NOT NULL AND m.suspiciousIndicators != ''")
    List<MediaMetadata> findWithSuspiciousIndicators();
    
    /**
     * Find metadata by camera model
     */
    List<MediaMetadata> findByCameraModel(String cameraModel);
    
    /**
     * Find metadata by GPS location within radius
     */
    @Query("SELECT m FROM MediaMetadata m WHERE m.gpsLatitude IS NOT NULL AND m.gpsLongitude IS NOT NULL " +
           "AND (6371 * acos(cos(radians(:lat)) * cos(radians(m.gpsLatitude)) * " +
           "cos(radians(m.gpsLongitude) - radians(:lng)) + sin(radians(:lat)) * " +
           "sin(radians(m.gpsLatitude)))) < :radius")
    List<MediaMetadata> findByLocationWithinRadius(@Param("lat") Double latitude, 
                                                   @Param("lng") Double longitude, 
                                                   @Param("radius") Double radiusKm);
    
    /**
     * Check if metadata exists for file
     */
    boolean existsByFileMd5(String fileMd5);
    
    /**
     * Find metadata by hash values
     */
    List<MediaMetadata> findBySha256Hash(String sha256Hash);
    
    /**
     * Get metadata extraction statistics
     */
    @Query("SELECT m.extractionStatus, COUNT(m) FROM MediaMetadata m GROUP BY m.extractionStatus")
    List<Object[]> getExtractionStatistics();
}
