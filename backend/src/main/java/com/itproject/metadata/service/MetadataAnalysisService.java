package com.itproject.metadata.service;

import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.metadata.Directory;
import com.drew.metadata.Metadata;
import com.drew.metadata.Tag;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.drew.metadata.exif.GpsDirectory;
import com.itproject.metadata.dto.MetadataAnalysisResponse;
import com.itproject.metadata.entity.MediaMetadata;
import com.itproject.metadata.repository.MediaMetadataRepository;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Service for extracting and analyzing media metadata for forensic purposes
 */
@Slf4j
@Service
public class MetadataAnalysisService {
    
    @Autowired
    private MediaMetadataRepository metadataRepository;
    
    @Autowired
    private MinioClient minioClient;
    
    @Autowired
    private String minioBucketName;
    
    /**
     * Kafka listener for processing metadata analysis requests
     */
    @KafkaListener(topics = "metadata-analysis", groupId = "forensic-group")
    @Transactional
    public void processMetadataAnalysis(Map<String, Object> message) {
        try {
            String fileMd5 = (String) message.get("fileMd5");
            String fileName = (String) message.get("fileName");
            String fileType = (String) message.get("fileType");
            String filePath = (String) message.get("filePath");
            
            log.info("Processing metadata analysis for file: {} (MD5: {})", fileName, fileMd5);
            
            // Check if metadata already exists
            Optional<MediaMetadata> existingMetadata = metadataRepository.findByFileMd5(fileMd5);
            if (existingMetadata.isPresent()) {
                log.info("Metadata already exists for file: {}", fileMd5);
                return;
            }
            
            // Analyze metadata based on file type
            MediaMetadata metadata = new MediaMetadata();
            metadata.setFileMd5(fileMd5);
            metadata.setExtractionStatus(MediaMetadata.ExtractionStatus.PENDING);
            
            try (InputStream fileStream = getFileStream(filePath)) {
                // Generate hash values
                generateHashValues(metadata, fileStream);
                
                // Reset stream for metadata extraction
                try (InputStream metadataStream = getFileStream(filePath)) {
                    if ("IMAGE".equals(fileType)) {
                        extractImageMetadata(metadata, metadataStream);
                    } else if ("VIDEO".equals(fileType)) {
                        extractVideoMetadata(metadata, filePath);
                    }
                }
                
                // Perform forensic analysis
                performForensicAnalysis(metadata);
                
                metadata.setExtractionStatus(MediaMetadata.ExtractionStatus.SUCCESS);
                log.info("Successfully extracted metadata for file: {}", fileMd5);
                
            } catch (Exception e) {
                log.error("Partial failure in metadata extraction for file: {}", fileMd5, e);
                metadata.setExtractionStatus(MediaMetadata.ExtractionStatus.PARTIAL);
                metadata.setAnalysisNotes("Partial extraction failure: " + e.getMessage());
            }
            
            metadataRepository.save(metadata);
            
        } catch (Exception e) {
            log.error("Failed to process metadata analysis", e);
            // Save failed status if possible
            try {
                String fileMd5 = (String) message.get("fileMd5");
                if (fileMd5 != null) {
                    MediaMetadata failedMetadata = new MediaMetadata();
                    failedMetadata.setFileMd5(fileMd5);
                    failedMetadata.setExtractionStatus(MediaMetadata.ExtractionStatus.FAILED);
                    failedMetadata.setAnalysisNotes("Extraction failed: " + e.getMessage());
                    metadataRepository.save(failedMetadata);
                }
            } catch (Exception saveError) {
                log.error("Failed to save error status", saveError);
            }
        }
    }
    
    /**
     * Get metadata analysis for a file
     */
    public MetadataAnalysisResponse getMetadataAnalysis(String fileMd5) {
        try {
            Optional<MediaMetadata> metadataOpt = metadataRepository.findByFileMd5(fileMd5);
            
            if (metadataOpt.isEmpty()) {
                return MetadataAnalysisResponse.error("Metadata not found for file: " + fileMd5);
            }
            
            MediaMetadata metadata = metadataOpt.get();
            
            MetadataAnalysisResponse response = MetadataAnalysisResponse.success("Metadata retrieved successfully");
            response.setFileMd5(fileMd5);
            response.setExtractionStatus(metadata.getExtractionStatus().name());
            response.setAnalysisTime(metadata.getCreatedAt());
            
            // Build response data
            response.setBasicMetadata(buildBasicMetadata(metadata));
            response.setExifData(buildExifData(metadata));
            response.setVideoMetadata(buildVideoMetadata(metadata));
            response.setHashValues(buildHashValues(metadata));
            response.setSuspiciousIndicators(buildSuspiciousIndicators(metadata));
            
            return response;
            
        } catch (Exception e) {
            log.error("Error retrieving metadata analysis for file: {}", fileMd5, e);
            return MetadataAnalysisResponse.error("Failed to retrieve metadata: " + e.getMessage());
        }
    }
    
    private InputStream getFileStream(String filePath) throws Exception {
        return minioClient.getObject(GetObjectArgs.builder()
                .bucket(minioBucketName)
                .object(filePath)
                .build());
    }
    
    private void generateHashValues(MediaMetadata metadata, InputStream fileStream) throws Exception {
        // Create multiple hash digests
        MessageDigest md5Digest = MessageDigest.getInstance("MD5");
        MessageDigest sha1Digest = MessageDigest.getInstance("SHA-1");
        MessageDigest sha256Digest = MessageDigest.getInstance("SHA-256");
        
        byte[] buffer = new byte[8192];
        int bytesRead;
        
        while ((bytesRead = fileStream.read(buffer)) != -1) {
            md5Digest.update(buffer, 0, bytesRead);
            sha1Digest.update(buffer, 0, bytesRead);
            sha256Digest.update(buffer, 0, bytesRead);
        }
        
        metadata.setSha1Hash(bytesToHex(sha1Digest.digest()));
        metadata.setSha256Hash(bytesToHex(sha256Digest.digest()));
        
        log.debug("Generated hash values for file: MD5={}, SHA1={}, SHA256={}", 
                 metadata.getFileMd5(), metadata.getSha1Hash(), metadata.getSha256Hash());
    }
    
    private void extractImageMetadata(MediaMetadata metadata, InputStream imageStream) {
        try {
            Metadata imageMetadata = ImageMetadataReader.readMetadata(imageStream);
            
            // Extract EXIF data
            ExifIFD0Directory exifDirectory = imageMetadata.getFirstDirectoryOfType(ExifIFD0Directory.class);
            if (exifDirectory != null) {
                extractExifData(metadata, exifDirectory);
            }
            
            // Extract GPS data
            GpsDirectory gpsDirectory = imageMetadata.getFirstDirectoryOfType(GpsDirectory.class);
            if (gpsDirectory != null) {
                extractGpsData(metadata, gpsDirectory);
            }
            
            // Extract technical metadata
            extractTechnicalImageMetadata(metadata, imageMetadata);
            
            // Store raw metadata for forensic analysis
            metadata.setRawMetadata(buildRawMetadataString(imageMetadata));
            
        } catch (ImageProcessingException | IOException e) {
            log.error("Error extracting image metadata", e);
            metadata.setAnalysisNotes("Image metadata extraction failed: " + e.getMessage());
        }
    }
    
    private void extractExifData(MediaMetadata metadata, ExifIFD0Directory exifDirectory) {
        try {
            // Camera information
            if (exifDirectory.hasTagName(ExifIFD0Directory.TAG_MAKE)) {
                metadata.setCameraMake(exifDirectory.getString(ExifIFD0Directory.TAG_MAKE));
            }
            if (exifDirectory.hasTagName(ExifIFD0Directory.TAG_MODEL)) {
                metadata.setCameraModel(exifDirectory.getString(ExifIFD0Directory.TAG_MODEL));
            }
            
            // Date taken
            if (exifDirectory.hasTagName(ExifIFD0Directory.TAG_DATETIME)) {
                String dateTimeStr = exifDirectory.getString(ExifIFD0Directory.TAG_DATETIME);
                try {
                    metadata.setDateTaken(LocalDateTime.parse(dateTimeStr, 
                            DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss")));
                } catch (Exception e) {
                    log.warn("Failed to parse date taken: {}", dateTimeStr);
                }
            }
            
            // Image dimensions
            if (exifDirectory.hasTagName(ExifIFD0Directory.TAG_IMAGE_WIDTH)) {
                metadata.setImageWidth(exifDirectory.getInteger(ExifIFD0Directory.TAG_IMAGE_WIDTH));
            }
            if (exifDirectory.hasTagName(ExifIFD0Directory.TAG_IMAGE_HEIGHT)) {
                metadata.setImageHeight(exifDirectory.getInteger(ExifIFD0Directory.TAG_IMAGE_HEIGHT));
            }
            
            // Orientation
            if (exifDirectory.hasTagName(ExifIFD0Directory.TAG_ORIENTATION)) {
                metadata.setOrientation(exifDirectory.getInteger(ExifIFD0Directory.TAG_ORIENTATION));
            }
            
        } catch (Exception e) {
            log.error("Error extracting EXIF data", e);
        }
    }
    
    private void extractGpsData(MediaMetadata metadata, GpsDirectory gpsDirectory) {
        try {
            if (gpsDirectory.getGeoLocation() != null) {
                metadata.setGpsLatitude(gpsDirectory.getGeoLocation().getLatitude());
                metadata.setGpsLongitude(gpsDirectory.getGeoLocation().getLongitude());
                metadata.setGpsLocation(String.format("%.6f,%.6f", 
                        metadata.getGpsLatitude(), metadata.getGpsLongitude()));
            }
        } catch (Exception e) {
            log.error("Error extracting GPS data", e);
        }
    }
    
    private void extractTechnicalImageMetadata(MediaMetadata metadata, Metadata imageMetadata) {
        // Extract technical details from various directories
        for (Directory directory : imageMetadata.getDirectories()) {
            for (Tag tag : directory.getTags()) {
                String tagName = tag.getTagName().toLowerCase();
                
                // Look for compression information
                if (tagName.contains("compression")) {
                    // Store compression details
                }
                
                // Look for color space information
                if (tagName.contains("color") && tagName.contains("space")) {
                    metadata.setColorSpace(tag.getDescription());
                }
            }
        }
    }
    
    private void extractVideoMetadata(MediaMetadata metadata, String filePath) {
        FFmpegFrameGrabber grabber = null;
        try {
            // Use temporary local file approach or direct stream
            // For MinIO, we might need to download temporarily or use direct URL
            
            grabber = new FFmpegFrameGrabber(getVideoFilePath(filePath));
            grabber.start();
            
            // Extract video properties
            metadata.setVideoDuration((int) (grabber.getLengthInTime() / 1000000)); // Convert to seconds
            metadata.setFrameRate(grabber.getVideoFrameRate());
            metadata.setVideoCodec(grabber.getVideoCodecName());
            metadata.setAudioCodec(grabber.getAudioCodecName());
            metadata.setBitRate(grabber.getVideoBitrate());
            metadata.setImageWidth(grabber.getImageWidth());
            metadata.setImageHeight(grabber.getImageHeight());
            
            log.debug("Extracted video metadata: duration={}s, fps={}, codec={}", 
                     metadata.getVideoDuration(), metadata.getFrameRate(), metadata.getVideoCodec());
            
        } catch (Exception e) {
            log.error("Error extracting video metadata", e);
            metadata.setAnalysisNotes("Video metadata extraction failed: " + e.getMessage());
        } finally {
            if (grabber != null) {
                try {
                    grabber.stop();
                    grabber.release();
                } catch (Exception e) {
                    log.warn("Error releasing video grabber", e);
                }
            }
        }
    }
    
    private void performForensicAnalysis(MediaMetadata metadata) {
        List<String> suspiciousIndicators = new ArrayList<>();
        
        // Check for missing EXIF data (potential manipulation)
        if (metadata.getCameraMake() == null && metadata.getCameraModel() == null) {
            suspiciousIndicators.add("Missing camera information in EXIF data");
        }
        
        // Check for suspicious date/time
        if (metadata.getDateTaken() != null) {
            LocalDateTime now = LocalDateTime.now();
            if (metadata.getDateTaken().isAfter(now)) {
                suspiciousIndicators.add("Future date in EXIF data");
            }
            if (metadata.getDateTaken().isBefore(LocalDateTime.of(1990, 1, 1, 0, 0))) {
                suspiciousIndicators.add("Unusually old date in EXIF data");
            }
        }
        
        // Check for unusual image dimensions (common in AI-generated content)
        if (metadata.getImageWidth() != null && metadata.getImageHeight() != null) {
            int width = metadata.getImageWidth();
            int height = metadata.getImageHeight();
            
            // Check for common AI generation resolutions
            if ((width == 512 && height == 512) || 
                (width == 1024 && height == 1024) ||
                (width == 256 && height == 256)) {
                suspiciousIndicators.add("Image dimensions common in AI-generated content");
            }
        }
        
        // Check for missing GPS data when camera typically includes it
        if (metadata.getCameraMake() != null && 
            (metadata.getCameraMake().toLowerCase().contains("iphone") || 
             metadata.getCameraMake().toLowerCase().contains("samsung")) &&
            metadata.getGpsLatitude() == null) {
            suspiciousIndicators.add("Missing GPS data from device that typically includes location");
        }
        
        if (!suspiciousIndicators.isEmpty()) {
            metadata.setSuspiciousIndicators(String.join("; ", suspiciousIndicators));
            log.info("Found suspicious indicators for file {}: {}", 
                    metadata.getFileMd5(), metadata.getSuspiciousIndicators());
        }
    }
    
    private String getVideoFilePath(String filePath) {
        // For production, implement proper MinIO URL handling or temporary file download
        return filePath;
    }
    
    private String buildRawMetadataString(Metadata metadata) {
        StringBuilder sb = new StringBuilder();
        for (Directory directory : metadata.getDirectories()) {
            sb.append(directory.getName()).append(":\n");
            for (Tag tag : directory.getTags()) {
                sb.append("  ").append(tag.getTagName()).append(": ").append(tag.getDescription()).append("\n");
            }
            sb.append("\n");
        }
        return sb.toString();
    }
    
    private String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }
    
    // Response building methods
    private Map<String, Object> buildBasicMetadata(MediaMetadata metadata) {
        Map<String, Object> basic = new HashMap<>();
        basic.put("fileFormat", metadata.getFileFormat());
        basic.put("mimeType", metadata.getMimeType());
        basic.put("imageWidth", metadata.getImageWidth());
        basic.put("imageHeight", metadata.getImageHeight());
        basic.put("compressionLevel", metadata.getCompressionLevel());
        return basic;
    }
    
    private Map<String, Object> buildExifData(MediaMetadata metadata) {
        Map<String, Object> exif = new HashMap<>();
        exif.put("cameraMake", metadata.getCameraMake());
        exif.put("cameraModel", metadata.getCameraModel());
        exif.put("dateTaken", metadata.getDateTaken());
        exif.put("orientation", metadata.getOrientation());
        exif.put("colorSpace", metadata.getColorSpace());
        return exif;
    }
    
    private Map<String, Object> buildVideoMetadata(MediaMetadata metadata) {
        Map<String, Object> video = new HashMap<>();
        video.put("duration", metadata.getVideoDuration());
        video.put("frameRate", metadata.getFrameRate());
        video.put("videoCodec", metadata.getVideoCodec());
        video.put("audioCodec", metadata.getAudioCodec());
        video.put("bitRate", metadata.getBitRate());
        return video;
    }
    
    private Map<String, Object> buildHashValues(MediaMetadata metadata) {
        Map<String, Object> hashes = new HashMap<>();
        hashes.put("md5", metadata.getFileMd5());
        hashes.put("sha1", metadata.getSha1Hash());
        hashes.put("sha256", metadata.getSha256Hash());
        return hashes;
    }
    
    private Map<String, Object> buildSuspiciousIndicators(MediaMetadata metadata) {
        Map<String, Object> indicators = new HashMap<>();
        indicators.put("hasIndicators", metadata.getSuspiciousIndicators() != null);
        indicators.put("indicators", metadata.getSuspiciousIndicators());
        indicators.put("analysisNotes", metadata.getAnalysisNotes());
        return indicators;
    }
}
