package com.itproject.metadata.service;

import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.metadata.Directory;
import com.drew.metadata.Metadata;
import com.drew.metadata.Tag;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.drew.metadata.exif.GpsDirectory;
import com.drew.metadata.file.FileTypeDirectory;
import com.drew.metadata.jpeg.JpegDirectory;
import com.drew.metadata.png.PngDirectory;
import com.drew.metadata.gif.GifHeaderDirectory;
import com.itproject.auth.entity.User;
import com.itproject.auth.repository.UserRepository;
import com.itproject.auth.security.SecurityUtils;
import com.itproject.project.entity.Project;
import com.itproject.project.repository.ProjectRepository;
import com.itproject.upload.entity.MediaFile;
import com.itproject.upload.repository.MediaFileRepository;
import com.itproject.metadata.dto.MetadataAnalysisResponse;
import com.itproject.metadata.entity.MediaMetadata;
import com.itproject.metadata.repository.MediaMetadataRepository;
import io.minio.GetObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.http.Method;
import lombok.extern.slf4j.Slf4j;
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
    private UserRepository userRepository;
    
    @Autowired
    private ProjectRepository projectRepository;
    
    @Autowired
    private MinioClient minioClient;
    
    @Autowired
    private String minioBucketName;
    
    @Autowired
    private MediaFileRepository mediaFileRepository;
    
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
            Boolean forceReAnalysis = (Boolean) message.get("forceReAnalysis");
            Long userId = extractLong(message.get("userId"));
            Long projectId = extractLong(message.get("projectId"));
            
            // Fallback from DB when message misses fields (backward compatibility)
            if (userId == null || projectId == null || fileType == null || filePath == null || fileName == null) {
                if (fileMd5 != null) {
                    Optional<MediaFile> mfOpt = mediaFileRepository.findByFileMd5(fileMd5);
                    if (mfOpt.isPresent()) {
                        MediaFile mf = mfOpt.get();
                        if (userId == null && mf.getUser() != null) userId = mf.getUser().getId();
                        if (projectId == null && mf.getProject() != null) projectId = mf.getProject().getId();
                        if (fileType == null) {
                            fileType = mf.getMediaType() != null ? mf.getMediaType().name() : mf.getFileType();
                        }
                        if (filePath == null) filePath = mf.getFilePath();
                        if (fileName == null) fileName = mf.getOriginalFileName() != null ? mf.getOriginalFileName() : mf.getFileName();
                    }
                }
            }
            if (forceReAnalysis == null) {
                forceReAnalysis = Boolean.FALSE;
            }
            
            if (userId == null || projectId == null) {
                throw new IllegalArgumentException("Missing userId/projectId in message and fallback.");
            }
            
            log.info("Processing metadata analysis for file: {} (MD5: {}), user: {}, forceReAnalysis: {}", 
                    fileName, fileMd5, userId, forceReAnalysis);
            
            // Get user
            Long finalUserId = userId;
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("User not found: " + finalUserId));
            
            // Get project
            Long finalProjectId = projectId;
            Project project = projectRepository.findById(projectId)
                    .orElseThrow(() -> new RuntimeException("Project not found: " + finalProjectId));
            
            // Check if metadata already exists for this user
            Optional<MediaMetadata> existingMetadata = metadataRepository.findByFileMd5AndUser(fileMd5, user);
            if (existingMetadata.isPresent() && !Boolean.TRUE.equals(forceReAnalysis)) {
                log.info("Metadata already exists for file: {} and user: {}, skipping analysis", fileMd5, userId);
                return;
            }
            
            // If forcing re-analysis, delete existing metadata
            if (existingMetadata.isPresent() && Boolean.TRUE.equals(forceReAnalysis)) {
                log.info("Force re-analysis requested, deleting existing metadata for file: {} and user: {}", fileMd5, userId);
                metadataRepository.delete(existingMetadata.get());
            }
            
            // Analyze metadata based on file type
            MediaMetadata metadata = new MediaMetadata();
            metadata.setFileMd5(fileMd5);
            metadata.setUser(user); // Set user relationship
            metadata.setProject(project); // Set project relationship
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
            
            MediaMetadata savedMetadata = metadataRepository.save(metadata);
            log.info("Saved metadata for file {}: format={}, mimeType={}, dimensions={}x{}", 
                    savedMetadata.getFileMd5(), savedMetadata.getFileFormat(), savedMetadata.getMimeType(),
                    savedMetadata.getImageWidth(), savedMetadata.getImageHeight());
            
        } catch (Exception e) {
            log.error("Failed to process metadata analysis", e);
            // Save failed status if possible
            try {
                String fileMd5 = (String) message.get("fileMd5");
                Long userId = extractLong(message.get("userId"));
                Long projectId = extractLong(message.get("projectId"));
                if ((userId == null || projectId == null) && fileMd5 != null) {
                    Optional<MediaFile> mfOpt = mediaFileRepository.findByFileMd5(fileMd5);
                    if (mfOpt.isPresent()) {
                        MediaFile mf = mfOpt.get();
                        if (userId == null && mf.getUser() != null) userId = mf.getUser().getId();
                        if (projectId == null && mf.getProject() != null) projectId = mf.getProject().getId();
                    }
                }
                if (fileMd5 != null && userId != null && projectId != null) {
                    User user = userRepository.findById(userId).orElse(null);
                    Project project = projectRepository.findById(projectId).orElse(null);
                    if (user != null && project != null) {
                        MediaMetadata failedMetadata = new MediaMetadata();
                        failedMetadata.setFileMd5(fileMd5);
                        failedMetadata.setUser(user);
                        failedMetadata.setProject(project);
                        failedMetadata.setExtractionStatus(MediaMetadata.ExtractionStatus.FAILED);
                        failedMetadata.setAnalysisNotes("Extraction failed: " + e.getMessage());
                        metadataRepository.save(failedMetadata);
                    }
                }
            } catch (Exception saveError) {
                log.error("Failed to save error status", saveError);
            }
        }
    }

    private Long extractLong(Object value) {
        try {
            if (value == null) return null;
            if (value instanceof Number) {
                return ((Number) value).longValue();
            }
            if (value instanceof String) {
                String s = (String) value;
                if (s.trim().isEmpty()) return null;
                return Long.parseLong(s.trim());
            }
            return null;
        } catch (Exception ex) {
            return null;
        }
    }
    
    /**
     * Fix existing metadata records by re-extracting technical data from raw metadata
     */
    @Transactional
    public void fixExistingMetadata(String fileMd5) {
        try {
            Optional<MediaMetadata> metadataOpt = metadataRepository.findByFileMd5(fileMd5);
            if (metadataOpt.isPresent()) {
                MediaMetadata metadata = metadataOpt.get();
                if (metadata.getRawMetadata() != null && 
                    (metadata.getFileFormat() == null || metadata.getMimeType() == null)) {
                    
                    log.info("Fixing metadata for file: {}", fileMd5);
                    
                    // Parse raw metadata to extract structured fields
                    parseRawMetadataToStructuredFields(metadata);
                    
                    // Re-run forensic analysis with new data
                    performForensicAnalysis(metadata);
                    
                    metadataRepository.save(metadata);
                    log.info("Fixed metadata for file: {}", fileMd5);
                }
            }
        } catch (Exception e) {
            log.error("Failed to fix metadata for file: {}", fileMd5, e);
        }
    }
    
    /**
     * Parse raw metadata string to extract structured fields
     */
    private void parseRawMetadataToStructuredFields(MediaMetadata metadata) {
        String rawMetadata = metadata.getRawMetadata();
        if (rawMetadata == null) return;
        
        String[] lines = rawMetadata.split("\\n");
        
        for (String line : lines) {
            line = line.trim();
            
            // Extract file type information
            if (line.startsWith("Detected File Type Name:")) {
                String fileType = extractValue(line);
                if (fileType != null) {
                    metadata.setFileFormat(fileType.toUpperCase());
                }
            } else if (line.startsWith("Detected MIME Type:")) {
                String mimeType = extractValue(line);
                if (mimeType != null) {
                    metadata.setMimeType(mimeType);
                }
            }
            
            // Extract image dimensions
            else if (line.startsWith("Image Width:") || line.startsWith("Exif Image Width:")) {
                Integer width = extractIntValue(line);
                if (width != null && metadata.getImageWidth() == null) {
                    metadata.setImageWidth(width);
                }
            } else if (line.startsWith("Image Height:") || line.startsWith("Exif Image Height:")) {
                Integer height = extractIntValue(line);
                if (height != null && metadata.getImageHeight() == null) {
                    metadata.setImageHeight(height);
                }
            }
            
            // Extract compression information
            else if (line.contains("Compression") && line.contains("Quality")) {
                Integer quality = extractIntValue(line);
                if (quality != null && metadata.getCompressionLevel() == null) {
                    metadata.setCompressionLevel(quality);
                }
            }
            
            // Extract additional technical details if missing
            if (metadata.getColorSpace() == null && line.startsWith("Color Space:")) {
                String colorSpace = extractValue(line);
                metadata.setColorSpace(colorSpace);
            }
        }
        
        // Set default compression level for JPEG if not found
        if (metadata.getCompressionLevel() == null && "JPEG".equalsIgnoreCase(metadata.getFileFormat())) {
            metadata.setCompressionLevel(85); // Default high quality assumption
        }
    }
    
    private String extractValue(String line) {
        int colonIndex = line.indexOf(':');
        if (colonIndex > 0 && colonIndex < line.length() - 1) {
            return line.substring(colonIndex + 1).trim();
        }
        return null;
    }
    
    private Integer extractIntValue(String line) {
        String value = extractValue(line);
        if (value != null) {
            // Extract numbers from the value
            String numbers = value.replaceAll("[^0-9]", "");
            if (!numbers.isEmpty()) {
                try {
                    return Integer.parseInt(numbers);
                } catch (NumberFormatException e) {
                    return null;
                }
            }
        }
        return null;
    }
    
    /**
     * Get metadata analysis for a file (user-specific)
     */
    public MetadataAnalysisResponse getMetadataAnalysis(String fileMd5) {
        try {
            User currentUser = SecurityUtils.getCurrentUser();
            if (currentUser == null) {
                return MetadataAnalysisResponse.error("用户未登录");
            }
            
            Optional<MediaMetadata> metadataOpt = metadataRepository.findByFileMd5AndUser(fileMd5, currentUser);
            
            if (metadataOpt.isEmpty()) {
                return MetadataAnalysisResponse.error("Metadata not found for file: " + fileMd5);
            }
            
            MediaMetadata metadata = metadataOpt.get();
            
            // Check if technical metadata needs to be fixed
            if ((metadata.getFileFormat() == null || metadata.getMimeType() == null || 
                 metadata.getImageWidth() == null || metadata.getImageHeight() == null) && 
                metadata.getRawMetadata() != null) {
                log.info("Detected missing technical metadata for {}, attempting to fix...", fileMd5);
                fixExistingMetadata(fileMd5);
                
                // Re-fetch the updated metadata
                metadataOpt = metadataRepository.findByFileMd5AndUser(fileMd5, currentUser);
                if (metadataOpt.isPresent()) {
                    metadata = metadataOpt.get();
                    log.info("Metadata fixed for {}", fileMd5);
                }
            }
            
            // Debug log to check what we actually retrieved from database
            log.info("Retrieved metadata from DB for {}: format={}, mimeType={}, dimensions={}x{}, compression={}", 
                    metadata.getFileMd5(), metadata.getFileFormat(), metadata.getMimeType(),
                    metadata.getImageWidth(), metadata.getImageHeight(), metadata.getCompressionLevel());
            log.info("EXIF data: make={}, model={}, colorSpace={}", 
                    metadata.getCameraMake(), metadata.getCameraModel(), metadata.getColorSpace());
            
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
        // Extract file type and MIME type
        FileTypeDirectory fileTypeDirectory = imageMetadata.getFirstDirectoryOfType(FileTypeDirectory.class);
        if (fileTypeDirectory != null) {
            try {
                if (fileTypeDirectory.hasTagName(FileTypeDirectory.TAG_DETECTED_FILE_TYPE_NAME)) {
                    String fileType = fileTypeDirectory.getString(FileTypeDirectory.TAG_DETECTED_FILE_TYPE_NAME);
                    metadata.setFileFormat(fileType);
                    
                    // Set MIME type based on file format
                    if (fileType != null) {
                        switch (fileType.toLowerCase()) {
                            case "jpeg":
                                metadata.setMimeType("image/jpeg");
                                break;
                            case "png":
                                metadata.setMimeType("image/png");
                                break;
                            case "gif":
                                metadata.setMimeType("image/gif");
                                break;
                            case "bmp":
                                metadata.setMimeType("image/bmp");
                                break;
                            case "tiff":
                                metadata.setMimeType("image/tiff");
                                break;
                            case "webp":
                                metadata.setMimeType("image/webp");
                                break;
                            default:
                                metadata.setMimeType("image/" + fileType.toLowerCase());
                        }
                    }
                }
                
                if (fileTypeDirectory.hasTagName(FileTypeDirectory.TAG_DETECTED_FILE_MIME_TYPE)) {
                    String mimeType = fileTypeDirectory.getString(FileTypeDirectory.TAG_DETECTED_FILE_MIME_TYPE);
                    if (mimeType != null && metadata.getMimeType() == null) {
                        metadata.setMimeType(mimeType);
                    }
                }
            } catch (Exception e) {
                log.warn("Error extracting file type information", e);
            }
        }
        
        // Extract JPEG specific metadata
        JpegDirectory jpegDirectory = imageMetadata.getFirstDirectoryOfType(JpegDirectory.class);
        if (jpegDirectory != null) {
            try {
                // Extract image dimensions if not already set
                if (metadata.getImageWidth() == null && jpegDirectory.hasTagName(JpegDirectory.TAG_IMAGE_WIDTH)) {
                    metadata.setImageWidth(jpegDirectory.getInteger(JpegDirectory.TAG_IMAGE_WIDTH));
                }
                if (metadata.getImageHeight() == null && jpegDirectory.hasTagName(JpegDirectory.TAG_IMAGE_HEIGHT)) {
                    metadata.setImageHeight(jpegDirectory.getInteger(JpegDirectory.TAG_IMAGE_HEIGHT));
                }
                
                // Extract compression information
                if (jpegDirectory.hasTagName(JpegDirectory.TAG_COMPRESSION_TYPE)) {
                    String compressionType = jpegDirectory.getDescription(JpegDirectory.TAG_COMPRESSION_TYPE);
                    if (compressionType != null && compressionType.contains("Baseline")) {
                        // Estimate compression level based on data available
                        metadata.setCompressionLevel(estimateJpegQuality(jpegDirectory));
                    }
                }
            } catch (Exception e) {
                log.warn("Error extracting JPEG metadata", e);
            }
        }
        
        // Extract PNG specific metadata
        PngDirectory pngDirectory = imageMetadata.getFirstDirectoryOfType(PngDirectory.class);
        if (pngDirectory != null) {
            try {
                // Extract image dimensions if not already set
                if (metadata.getImageWidth() == null && pngDirectory.hasTagName(PngDirectory.TAG_IMAGE_WIDTH)) {
                    metadata.setImageWidth(pngDirectory.getInteger(PngDirectory.TAG_IMAGE_WIDTH));
                }
                if (metadata.getImageHeight() == null && pngDirectory.hasTagName(PngDirectory.TAG_IMAGE_HEIGHT)) {
                    metadata.setImageHeight(pngDirectory.getInteger(PngDirectory.TAG_IMAGE_HEIGHT));
                }
                
                // PNG compression level (0-9)
                if (pngDirectory.hasTagName(PngDirectory.TAG_COMPRESSION_TYPE)) {
                    Integer compressionType = pngDirectory.getInteger(PngDirectory.TAG_COMPRESSION_TYPE);
                    if (compressionType != null) {
                        metadata.setCompressionLevel(compressionType);
                    }
                }
            } catch (Exception e) {
                log.warn("Error extracting PNG metadata", e);
            }
        }
        
        // Extract GIF specific metadata
        GifHeaderDirectory gifDirectory = imageMetadata.getFirstDirectoryOfType(GifHeaderDirectory.class);
        if (gifDirectory != null) {
            try {
                if (metadata.getImageWidth() == null && gifDirectory.hasTagName(GifHeaderDirectory.TAG_IMAGE_WIDTH)) {
                    metadata.setImageWidth(gifDirectory.getInteger(GifHeaderDirectory.TAG_IMAGE_WIDTH));
                }
                if (metadata.getImageHeight() == null && gifDirectory.hasTagName(GifHeaderDirectory.TAG_IMAGE_HEIGHT)) {
                    metadata.setImageHeight(gifDirectory.getInteger(GifHeaderDirectory.TAG_IMAGE_HEIGHT));
                }
            } catch (Exception e) {
                log.warn("Error extracting GIF metadata", e);
            }
        }
        
        // Extract additional technical details from all directories
        for (Directory directory : imageMetadata.getDirectories()) {
            for (Tag tag : directory.getTags()) {
                String tagName = tag.getTagName().toLowerCase();
                
                // Look for color space information
                if (tagName.contains("color") && tagName.contains("space") && metadata.getColorSpace() == null) {
                    metadata.setColorSpace(tag.getDescription());
                }
                
                // Look for additional width/height information if not set
                if (metadata.getImageWidth() == null && (tagName.contains("width") || tagName.equals("image width"))) {
                    try {
                        Integer width = directory.getInteger(tag.getTagType());
                        if (width != null && width > 0) {
                            metadata.setImageWidth(width);
                        }
                    } catch (Exception e) {
                        // Ignore extraction errors for individual tags
                    }
                }
                
                if (metadata.getImageHeight() == null && (tagName.contains("height") || tagName.equals("image height"))) {
                    try {
                        Integer height = directory.getInteger(tag.getTagType());
                        if (height != null && height > 0) {
                            metadata.setImageHeight(height);
                        }
                    } catch (Exception e) {
                        // Ignore extraction errors for individual tags
                    }
                }
            }
        }
        
        log.info("Extracted technical metadata for {}: format={}, mimeType={}, dimensions={}x{}, compression={}",
                metadata.getFileMd5(), metadata.getFileFormat(), metadata.getMimeType(), metadata.getImageWidth(), 
                metadata.getImageHeight(), metadata.getCompressionLevel());
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
        StringBuilder analysisNotes = new StringBuilder();
        
        // 1. EXIF Data Analysis
        analyzeExifData(metadata, suspiciousIndicators, analysisNotes);
        
        // 2. Image Dimension Analysis
        analyzeDimensions(metadata, suspiciousIndicators, analysisNotes);
        
        // 3. File Format and Technical Analysis
        analyzeFileFormat(metadata, suspiciousIndicators, analysisNotes);
        
        // 4. Temporal Inconsistency Analysis
        analyzeTemporalInconsistencies(metadata, suspiciousIndicators, analysisNotes);
        
        // 5. Device-Specific Analysis
        analyzeDeviceSpecificIndicators(metadata, suspiciousIndicators, analysisNotes);
        
        // 6. AI Generation Pattern Analysis
        analyzeAIGenerationPatterns(metadata, suspiciousIndicators, analysisNotes);
        
        // 7. Metadata Completeness Analysis
        analyzeMetadataCompleteness(metadata, suspiciousIndicators, analysisNotes);
        
        // Set results
        if (!suspiciousIndicators.isEmpty()) {
            metadata.setSuspiciousIndicators(String.join("; ", suspiciousIndicators));
            log.info("Found {} suspicious indicators for file {}: {}", 
                    suspiciousIndicators.size(), metadata.getFileMd5(), metadata.getSuspiciousIndicators());
        }
        
        if (analysisNotes.length() > 0) {
            String existingNotes = metadata.getAnalysisNotes();
            String newNotes = analysisNotes.toString();
            if (existingNotes != null && !existingNotes.isEmpty()) {
                metadata.setAnalysisNotes(existingNotes + "; " + newNotes);
            } else {
                metadata.setAnalysisNotes(newNotes);
            }
        }
    }
    
    private void analyzeExifData(MediaMetadata metadata, List<String> indicators, StringBuilder notes) {
        // Distinguish between image and video analysis
        boolean isVideo = metadata.getVideoDuration() != null || metadata.getVideoCodec() != null;
        
        // For videos, missing camera info is more normal than for photos
        if (metadata.getCameraMake() == null && metadata.getCameraModel() == null) {
            if (isVideo) {
                // Videos often don't have camera info, especially from mobile devices or editing software
                // Only flag if other suspicious patterns exist
                if (metadata.getDateTaken() == null && metadata.getFileFormat() == null) {
                    indicators.add("视频缺少基本设备信息：可能经过处理或转换");
                    notes.append("视频文件缺少拍摄设备和时间信息; ");
                }
            } else {
                // For images, missing camera info is more suspicious
                indicators.add("图像缺少相机信息：可能是截图、编辑或AI生成内容");
                notes.append("EXIF数据中缺少相机制造商和型号信息; ");
            }
        }
        
        // Check for incomplete EXIF data
        int exifFields = 0;
        if (metadata.getCameraMake() != null) exifFields++;
        if (metadata.getCameraModel() != null) exifFields++;
        if (metadata.getDateTaken() != null) exifFields++;
        if (metadata.getOrientation() != null) exifFields++;
        if (metadata.getColorSpace() != null) exifFields++;
        
        // Be more lenient with video files
        int threshold = isVideo ? 0 : 1;  // Videos can have 0 EXIF fields normally
        if (exifFields <= threshold && exifFields > 0) {
            if (!isVideo) {  // Only flag for images
                indicators.add("EXIF数据不完整：可能经过处理或来源可疑");
            }
        }
    }
    
    private void analyzeDimensions(MediaMetadata metadata, List<String> indicators, StringBuilder notes) {
        if (metadata.getImageWidth() != null && metadata.getImageHeight() != null) {
            int width = metadata.getImageWidth();
            int height = metadata.getImageHeight();
            
            // Check for common AI generation resolutions
            if (isAICommonResolution(width, height)) {
                indicators.add("图像尺寸符合AI生成内容的常见分辨率");
                notes.append(String.format("检测到AI常用分辨率: %dx%d; ", width, height));
            }
            
            // Check for unusual aspect ratios
            double aspectRatio = (double) width / height;
            if (aspectRatio == 1.0 && (width >= 256 && width <= 2048)) {
                indicators.add("完美正方形比例：可能是AI生成或经过裁剪");
            }
            
            // Check for very high resolutions without corresponding quality metadata
            if ((width > 4000 || height > 4000) && metadata.getCameraMake() == null) {
                indicators.add("高分辨率图像缺少相机信息：可能是放大处理的结果");
            }
        }
    }
    
    private void analyzeFileFormat(MediaMetadata metadata, List<String> indicators, StringBuilder notes) {
        String format = metadata.getFileFormat();
        String mimeType = metadata.getMimeType();
        
        // Check for format inconsistencies
        if (format != null && mimeType != null) {
            boolean consistent = isFormatMimeConsistent(format, mimeType);
            if (!consistent) {
                indicators.add("文件格式与MIME类型不匹配：可能经过格式转换");
                notes.append(String.format("格式不一致: %s vs %s; ", format, mimeType));
            }
        }
        
        // Check compression levels
        Integer compression = metadata.getCompressionLevel();
        if (compression != null && format != null) {
            if ("JPEG".equalsIgnoreCase(format) && compression < 50) {
                indicators.add("JPEG压缩质量过低：可能多次保存或处理");
            }
            if ("PNG".equalsIgnoreCase(format) && compression > 6) {
                indicators.add("PNG压缩级别异常：可能经过特殊处理");
            }
        }
    }
    
    private void analyzeTemporalInconsistencies(MediaMetadata metadata, List<String> indicators, StringBuilder notes) {
        if (metadata.getDateTaken() != null) {
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime dateTaken = metadata.getDateTaken();
            
            // Check for future dates
            if (dateTaken.isAfter(now)) {
                indicators.add("拍摄时间显示为未来：EXIF数据可能被篡改");
                notes.append("检测到未来日期; ");
            }
            
            // Check for very old dates
            if (dateTaken.isBefore(LocalDateTime.of(1990, 1, 1, 0, 0))) {
                indicators.add("拍摄时间异常古老：可能是默认或错误的时间戳");
            }
            
            // Check for exact round times (suspicious for manual editing)
            if (dateTaken.getSecond() == 0 && dateTaken.getNano() == 0) {
                if (dateTaken.getMinute() == 0 || dateTaken.getMinute() % 5 == 0) {
                    notes.append("检测到整点时间，可能经过人工设置; ");
                }
            }
        }
    }
    
    private void analyzeDeviceSpecificIndicators(MediaMetadata metadata, List<String> indicators, StringBuilder notes) {
        String make = metadata.getCameraMake();
        String model = metadata.getCameraModel();
        
        if (make != null) {
            String makeLower = make.toLowerCase();
            
            // Check for missing GPS data from devices that typically include it
            if ((makeLower.contains("iphone") || makeLower.contains("samsung") || 
                 makeLower.contains("google") || makeLower.contains("huawei")) &&
                metadata.getGpsLatitude() == null && metadata.getGpsLongitude() == null) {
                indicators.add("智能手机拍摄但缺少GPS信息：可能关闭定位或经过处理");
            }
            
            // Check for unusual camera combinations
            if (model != null && !isValidCameraModelForMake(make, model)) {
                indicators.add("相机制造商与型号不匹配：可能是伪造的EXIF数据");
            }
        }
    }
    
    private void analyzeAIGenerationPatterns(MediaMetadata metadata, List<String> indicators, StringBuilder notes) {
        boolean isVideo = metadata.getVideoDuration() != null || metadata.getVideoCodec() != null;
        
        // Check for patterns common in AI-generated content
        if (metadata.getCameraMake() == null && metadata.getCameraModel() == null && 
            metadata.getDateTaken() == null && metadata.getGpsLatitude() == null) {
            
            // For images: missing metadata is more suspicious
            if (!isVideo && metadata.getImageWidth() != null && metadata.getImageHeight() != null) {
                // Check if it's common AI dimensions before flagging
                if (isAICommonResolution(metadata.getImageWidth(), metadata.getImageHeight())) {
                    indicators.add("完全缺少拍摄信息且为AI常见尺寸：高度疑似AI生成内容");
                    notes.append("无任何拍摄设备信息且符合AI生成尺寸特征; ");
                } else {
                    indicators.add("图像缺少拍摄信息：可能是截图、编辑或AI生成内容");
                    notes.append("无任何拍摄设备信息; ");
                }
            }
            
            // For videos: only flag if additional suspicious patterns exist
            else if (isVideo) {
                // Check for additional video-specific suspicious patterns
                boolean hasVideoSuspiciousPatterns = false;
                
                // Very low or very high frame rates can be suspicious
                if (metadata.getFrameRate() != null) {
                    double fps = metadata.getFrameRate();
                    if (fps < 15 || fps > 120) {
                        hasVideoSuspiciousPatterns = true;
                    }
                }
                
                // Unusual video dimensions for mobile content
                if (metadata.getImageWidth() != null && metadata.getImageHeight() != null) {
                    int width = metadata.getImageWidth();
                    int height = metadata.getImageHeight();
                    
                    // Check for perfect squares or very unusual ratios
                    if (width == height || Math.abs(width - height) / (double)Math.max(width, height) < 0.1) {
                        hasVideoSuspiciousPatterns = true;
                    }
                }
                
                if (hasVideoSuspiciousPatterns) {
                    indicators.add("视频缺少设备信息且具有异常技术参数：可能经过生成或大量处理");
                    notes.append("视频文件无设备信息且技术参数异常; ");
                }
            }
        }
        
        // Check for specific AI generation signatures in metadata
        String rawMetadata = metadata.getRawMetadata();
        if (rawMetadata != null) {
            String rawLower = rawMetadata.toLowerCase();
            if (rawLower.contains("stable diffusion") || rawLower.contains("midjourney") ||
                rawLower.contains("dalle") || rawLower.contains("generated") || 
                rawLower.contains("artificial") || rawLower.contains("synthetic")) {
                indicators.add("检测到AI生成工具标识：确认为人工智能生成内容");
            }
        }
    }
    
    private void analyzeMetadataCompleteness(MediaMetadata metadata, List<String> indicators, StringBuilder notes) {
        boolean isVideo = metadata.getVideoDuration() != null || metadata.getVideoCodec() != null;
        
        int totalFields = 0;
        int populatedFields = 0;
        
        // Different field expectations for images vs videos
        if (isVideo) {
            // For videos, focus on technical metadata that should be present
            String[] videoFields = {
                metadata.getVideoCodec(), metadata.getFileFormat(), 
                metadata.getMimeType()
            };
            
            for (String field : videoFields) {
                totalFields++;
                if (field != null && !field.isEmpty()) {
                    populatedFields++;
                }
            }
            
            // Add video-specific technical fields
            if (metadata.getVideoDuration() != null && metadata.getVideoDuration() > 0) populatedFields++;
            if (metadata.getFrameRate() != null && metadata.getFrameRate() > 0) populatedFields++;
            if (metadata.getImageWidth() != null && metadata.getImageHeight() != null) populatedFields++;
            totalFields += 3;
            
        } else {
            // For images, include camera and EXIF fields
            String[] imageFields = {
                metadata.getCameraMake(), metadata.getCameraModel(), 
                metadata.getFileFormat(), metadata.getMimeType(),
                metadata.getColorSpace()
            };
            
            for (String field : imageFields) {
                totalFields++;
                if (field != null && !field.isEmpty()) {
                    populatedFields++;
                }
            }
        }
        
        double completeness = totalFields > 0 ? (double) populatedFields / totalFields : 0;
        
        // Different thresholds for images vs videos
        double threshold = isVideo ? 0.2 : 0.3;  // Videos can have lower metadata completeness
        
        if (completeness < threshold) {
            if (isVideo) {
                indicators.add("视频技术元数据不完整：可能经过转换或处理");
                notes.append(String.format("视频元数据完整性: %.1f%%; ", completeness * 100));
            } else {
                indicators.add("图像元数据完整性极低：可能经过清理或来源可疑");
                notes.append(String.format("图像元数据完整性: %.1f%%; ", completeness * 100));
            }
        }
    }
    
    private boolean isAICommonResolution(int width, int height) {
        // Common AI generation resolutions
        int[][] aiResolutions = {
            {256, 256}, {512, 512}, {768, 768}, {1024, 1024},
            {512, 768}, {768, 512}, {1024, 768}, {768, 1024},
            {512, 256}, {256, 512}, {2048, 2048}
        };
        
        for (int[] res : aiResolutions) {
            if ((width == res[0] && height == res[1]) || (width == res[1] && height == res[0])) {
                return true;
            }
        }
        return false;
    }
    
    private boolean isFormatMimeConsistent(String format, String mimeType) {
        if (format == null || mimeType == null) return true;
        
        String formatLower = format.toLowerCase();
        String mimeTypeLower = mimeType.toLowerCase();
        
        return (formatLower.contains("jpeg") && mimeTypeLower.contains("jpeg")) ||
               (formatLower.contains("png") && mimeTypeLower.contains("png")) ||
               (formatLower.contains("gif") && mimeTypeLower.contains("gif")) ||
               (formatLower.contains("bmp") && mimeTypeLower.contains("bmp")) ||
               (formatLower.contains("tiff") && mimeTypeLower.contains("tiff")) ||
               (formatLower.contains("webp") && mimeTypeLower.contains("webp"));
    }
    
    private boolean isValidCameraModelForMake(String make, String model) {
        // Simplified validation - in a real system, this would be more comprehensive
        String makeLower = make.toLowerCase();
        String modelLower = model.toLowerCase();
        
        // Basic consistency checks
        if (makeLower.contains("canon") && !modelLower.contains("canon") && 
            !modelLower.contains("eos") && !modelLower.contains("powershot")) {
            return false;
        }
        if (makeLower.contains("nikon") && !modelLower.contains("nikon") && 
            !modelLower.contains("d") && !modelLower.contains("coolpix")) {
            return false;
        }
        if (makeLower.contains("sony") && !modelLower.contains("sony") && 
            !modelLower.contains("alpha") && !modelLower.contains("dsc")) {
            return false;
        }
        
        return true;
    }
      private String getVideoFilePath(String filePath) {
        try {
            // Generate presigned URL for MinIO object access
            String presignedUrl = minioClient.getPresignedObjectUrl(
                GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(minioBucketName)
                    .object(filePath)
                    .expiry(60 * 10) // 10 minutes expiry
                    .build()
            );
            log.debug("Generated presigned URL for video analysis: {}", presignedUrl);
            return presignedUrl;
        } catch (Exception e) {
            log.error("Failed to generate presigned URL for file: {}", filePath, e);
            // Fallback to original path (will likely fail but maintains original behavior)
            return filePath;
        }
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
    
    /**
     * Estimate JPEG quality/compression level based on available metadata
     */
    private Integer estimateJpegQuality(JpegDirectory jpegDirectory) {
        try {
            // Look for quality-related tags using the Tag collection
            for (Tag tag : jpegDirectory.getTags()) {
                String tagName = tag.getTagName().toLowerCase();
                String description = tag.getDescription();
                
                if (description != null) {
                    // Look for quality indicators in descriptions
                    if (tagName.contains("quality") || description.toLowerCase().contains("quality")) {
                        // Try to extract numeric quality value
                        String numStr = description.replaceAll("[^0-9]", "");
                        if (!numStr.isEmpty()) {
                            try {
                                int quality = Integer.parseInt(numStr);
                                if (quality >= 1 && quality <= 100) {
                                    return quality;
                                }
                            } catch (NumberFormatException e) {
                                // Continue searching
                            }
                        }
                    }
                }
            }
            
            // If no direct quality found, estimate based on component sampling
            // This is a simplified heuristic based on image characteristics
            // High compression typically results in smaller file sizes relative to dimensions
            return 85; // Default high quality assumption for forensic analysis
            
        } catch (Exception e) {
            log.debug("Error estimating JPEG quality", e);
            return null;
        }
    }
    
    // Response building methods
    private Map<String, Object> buildBasicMetadata(MediaMetadata metadata) {
        Map<String, Object> basic = new HashMap<>();
        
        // Debug logging to trace the issue
        log.debug("Building basic metadata for file: {}", metadata.getFileMd5());
        log.debug("FileFormat: {}", metadata.getFileFormat());
        log.debug("MimeType: {}", metadata.getMimeType());
        log.debug("ImageWidth: {}", metadata.getImageWidth());
        log.debug("ImageHeight: {}", metadata.getImageHeight());
        log.debug("CompressionLevel: {}", metadata.getCompressionLevel());
        
        basic.put("fileFormat", metadata.getFileFormat());
        basic.put("mimeType", metadata.getMimeType());
        basic.put("imageWidth", metadata.getImageWidth());
        basic.put("imageHeight", metadata.getImageHeight());
        basic.put("compressionLevel", metadata.getCompressionLevel());
        
        // Additional debug to see what's actually in the map
        log.debug("Built basic metadata map: {}", basic);
        
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

