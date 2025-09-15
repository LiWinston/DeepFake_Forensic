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
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
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
                
                // Perform file header analysis first (most critical for detection)
                analyzeFileHeader(metadata, filePath);
                
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
        boolean isVideo = metadata.getVideoDuration() != null || metadata.getVideoCodec() != null;
        
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
        
        // Handle video-specific metadata extraction if this is a video file
        if (isVideo && (metadata.getFileFormat() == null || metadata.getMimeType() == null || metadata.getCompressionLevel() == null)) {
            // For videos, we need to infer from existing data if raw metadata didn't provide it
            
            // Infer file format from video codec if available
            if (metadata.getFileFormat() == null && metadata.getVideoCodec() != null) {
                String codec = metadata.getVideoCodec().toLowerCase();
                if (codec.contains("h264") || codec.contains("avc")) {
                    metadata.setFileFormat("MP4");
                } else if (codec.contains("hevc") || codec.contains("h265")) {
                    metadata.setFileFormat("MP4");
                } else if (codec.contains("vp8") || codec.contains("vp9")) {
                    metadata.setFileFormat("WEBM");
                } else {
                    metadata.setFileFormat("VIDEO"); // Generic
                }
            }
            
            // Set MIME type based on inferred format
            if (metadata.getMimeType() == null) {
                String format = metadata.getFileFormat();
                if (format != null) {
                    switch (format.toLowerCase()) {
                        case "mp4":
                        case "mov":
                            metadata.setMimeType("video/mp4");
                            break;
                        case "webm":
                            metadata.setMimeType("video/webm");
                            break;
                        case "avi":
                            metadata.setMimeType("video/avi");
                            break;
                        default:
                            metadata.setMimeType("video/unknown");
                    }
                }
            }
            
            // Estimate compression level for videos based on bitrate and resolution
            if (metadata.getCompressionLevel() == null && metadata.getBitRate() != null && 
                metadata.getImageWidth() != null && metadata.getImageHeight() != null && metadata.getFrameRate() != null) {
                
                int bitRate = metadata.getBitRate();
                int pixels = metadata.getImageWidth() * metadata.getImageHeight();
                double fps = metadata.getFrameRate();
                
                if (bitRate > 0 && pixels > 0 && fps > 0) {
                    double bitsPerPixelPerFrame = (double) bitRate / (pixels * fps);
                    
                    // Convert to a 1-100 scale for video quality
                    int compressionLevel;
                    if (bitsPerPixelPerFrame > 0.8) {
                        compressionLevel = 95; // Very high quality
                    } else if (bitsPerPixelPerFrame > 0.4) {
                        compressionLevel = 85; // High quality
                    } else if (bitsPerPixelPerFrame > 0.2) {
                        compressionLevel = 70; // Medium quality
                    } else if (bitsPerPixelPerFrame > 0.1) {
                        compressionLevel = 50; // Low quality
                    } else {
                        compressionLevel = 30; // Very low quality
                    }
                    
                    metadata.setCompressionLevel(compressionLevel);
                }
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
                return MetadataAnalysisResponse.error("User not logged in");
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
            response.setParsedMetadata(buildParsedMetadata(metadata));
            
            // Set missing critical fields
            response.setAnalysisNotes(metadata.getAnalysisNotes());
            response.setRawMetadata(metadata.getRawMetadata());
            
            // Set new Week 7 fields
            response.setFileHeaderAnalysis(buildFileHeaderAnalysis(metadata));
            response.setRiskScore(metadata.getRiskScore());
            response.setAssessmentConclusion(metadata.getAssessmentConclusion());
            response.setContainerAnalysis(buildContainerAnalysis(metadata));
            
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
    
    /**
     * Analyze file header/signature for forensic detection
     */
    private void analyzeFileHeader(MediaMetadata metadata, String filePath) {
        try (InputStream headerStream = getFileStream(filePath)) {
            // Read first 64 bytes for signature analysis
            byte[] header = new byte[64];
            int bytesRead = headerStream.read(header);
            
            if (bytesRead > 0) {
                FileHeaderAnalysisResult headerResult = analyzeFileSignature(header, filePath);
                
                // Store structured header analysis in dedicated fields
                metadata.setDetectedFileFormat(headerResult.getDetectedFormat());
                metadata.setExpectedFileFormat(headerResult.getExpectedFormat());
                metadata.setFileFormatMatch(headerResult.isFormatMatch());
                metadata.setFileSignatureHex(headerResult.getSignatureHex());
                metadata.setFileIntegrityStatus(headerResult.getIntegrityStatus());
                
                // Also append summary to analysis notes for human readability
                String headerSummary = buildHeaderAnalysisString(headerResult);
                appendToAnalysisNotes(metadata, "File header analysis: " + headerSummary);
                
                // Update raw metadata with detailed header information (for full transparency)
                String headerInfo = "\n\nFile Header Analysis:\n" +
                                  "  Detected Format: " + headerResult.getDetectedFormat() + "\n" +
                                  "  Expected Format: " + headerResult.getExpectedFormat() + "\n" +
                                  "  Format Match: " + headerResult.isFormatMatch() + "\n" +
                                  "  File Signature: " + headerResult.getSignatureHex() + "\n" +
                                  "  Integrity Status: " + headerResult.getIntegrityStatus() + "\n";
                
                String rawMetadata = metadata.getRawMetadata();
                if (rawMetadata != null) {
                    metadata.setRawMetadata(rawMetadata + headerInfo);
                } else {
                    metadata.setRawMetadata(headerInfo);
                }
                
                log.info("File header analysis completed - File: {}, Detected format: {}, Format match: {}, Integrity: {}", 
                        metadata.getFileMd5(), headerResult.getDetectedFormat(), 
                        headerResult.isFormatMatch(), headerResult.getIntegrityStatus());
            }
        } catch (Exception e) {
            log.error("File header analysis failed: {}", metadata.getFileMd5(), e);
            metadata.setFileIntegrityStatus("ANALYSIS_FAILED");
            appendToAnalysisNotes(metadata, "File header analysis failed: " + e.getMessage());
        }
    }
    
    /**
     * Helper method to append notes to analysis notes
     */
    private void appendToAnalysisNotes(MediaMetadata metadata, String newNote) {
        String existingNotes = metadata.getAnalysisNotes();
        if (existingNotes != null && !existingNotes.isEmpty()) {
            metadata.setAnalysisNotes(existingNotes + "; " + newNote);
        } else {
            metadata.setAnalysisNotes(newNote);
        }
    }
    
    /**
     * Analyze file signature and detect format
     */
    private FileHeaderAnalysisResult analyzeFileSignature(byte[] header, String filePath) {
        FileHeaderAnalysisResult result = new FileHeaderAnalysisResult();
        
        // Convert header to hex for analysis
        String headerHex = bytesToHex(Arrays.copyOf(header, Math.min(16, header.length))).toUpperCase();
        result.setSignatureHex(headerHex);
        
        // Extract file extension
        String extension = "";
        if (filePath != null) {
            int lastDot = filePath.lastIndexOf('.');
            if (lastDot > 0) {
                extension = filePath.substring(lastDot + 1).toLowerCase();
            }
        }
        result.setExpectedFormat(extension.toUpperCase());
        
        // Detect actual format based on file signature
        String detectedFormat = detectFormatFromSignature(header);
        result.setDetectedFormat(detectedFormat);
        
        // Check format consistency
        boolean formatMatch = isFormatConsistentWithExtension(detectedFormat, extension);
        result.setFormatMatch(formatMatch);
        
        // Determine integrity status
        if (!formatMatch) {
            result.setIntegrityStatus("FORMAT_MISMATCH");
        } else if (detectedFormat.equals("UNKNOWN")) {
            result.setIntegrityStatus("UNKNOWN_FORMAT");
        } else {
            result.setIntegrityStatus("INTACT");
        }
        
        return result;
    }
    
    /**
     * Detect file format based on signature bytes
     */
    private String detectFormatFromSignature(byte[] header) {
        if (header.length < 4) return "UNKNOWN";
        
        // Convert first few bytes to compare with known signatures
        String hex = bytesToHex(Arrays.copyOf(header, Math.min(16, header.length))).toUpperCase();
        
        // Image formats
        if (hex.startsWith("FFD8FF")) return "JPEG";
        if (hex.startsWith("89504E47")) return "PNG";
        if (hex.startsWith("474946")) return "GIF";
        if (hex.startsWith("424D")) return "BMP";
        if (hex.startsWith("49492A00") || hex.startsWith("4D4D002A")) return "TIFF";
        if (hex.startsWith("52494646") && hex.contains("57454250")) return "WEBP";
        
        // Video formats
        if (hex.startsWith("0000001")) return "MP4";
        if (hex.startsWith("00000020667479706D703432") || hex.startsWith("66747970")) return "MP4";
        if (hex.startsWith("1A45DFA3")) return "WEBM"; // Matroska/WebM
        if (hex.startsWith("52494646") && hex.contains("41564920")) return "AVI";
        if (hex.startsWith("464C5601")) return "FLV";
        if (hex.startsWith("30264DE1")) return "WMV";
        if (hex.startsWith("6D6F6F76") || hex.startsWith("66726565") || 
            hex.startsWith("6D646174") || hex.startsWith("77696465")) return "MOV";
        
        // Check for more specific MP4/MOV patterns
        if (header.length >= 8) {
            // Look for 'ftyp' box (File Type Box) in MP4/MOV files
            for (int i = 0; i <= header.length - 4; i++) {
                if (header[i] == 'f' && header[i+1] == 't' && 
                    header[i+2] == 'y' && header[i+3] == 'p') {
                    return "MP4";
                }
            }
        }
        
        // Document formats (for completeness)
        if (hex.startsWith("255044462D")) return "PDF";
        if (hex.startsWith("504B0304")) return "ZIP"; // Could be DOCX, etc.
        
        return "UNKNOWN";
    }
    
    /**
     * Check if detected format is consistent with file extension
     */
    private boolean isFormatConsistentWithExtension(String detectedFormat, String extension) {
        if (detectedFormat.equals("UNKNOWN")) return false;
        
        String ext = extension.toLowerCase();
        String detected = detectedFormat.toLowerCase();
        
        // Image format consistency
        if (detected.equals("jpeg") && (ext.equals("jpg") || ext.equals("jpeg"))) return true;
        if (detected.equals("png") && ext.equals("png")) return true;
        if (detected.equals("gif") && ext.equals("gif")) return true;
        if (detected.equals("bmp") && ext.equals("bmp")) return true;
        if (detected.equals("tiff") && (ext.equals("tif") || ext.equals("tiff"))) return true;
        if (detected.equals("webp") && ext.equals("webp")) return true;
        
        // Video format consistency
        if (detected.equals("mp4") && (ext.equals("mp4") || ext.equals("m4v"))) return true;
        if (detected.equals("mov") && ext.equals("mov")) return true;
        if (detected.equals("webm") && ext.equals("webm")) return true;
        if (detected.equals("avi") && ext.equals("avi")) return true;
        if (detected.equals("flv") && ext.equals("flv")) return true;
        if (detected.equals("wmv") && ext.equals("wmv")) return true;
        
        // Cross-compatible formats (MP4/MOV are often interchangeable)
        if ((detected.equals("mp4") || detected.equals("mov")) && 
            (ext.equals("mp4") || ext.equals("mov") || ext.equals("m4v"))) return true;
        
        return false;
    }
    
    /**
     * Build header analysis result string
     */
    private String buildHeaderAnalysisString(FileHeaderAnalysisResult result) {
        StringBuilder sb = new StringBuilder();
        
        if (!result.isFormatMatch()) {
            sb.append("Format mismatch warning - Detected ").append(result.getDetectedFormat())
              .append(" format, but file extension shows ").append(result.getExpectedFormat());
        } else {
            sb.append("File format verification normal - ").append(result.getDetectedFormat()).append(" format");
        }
        
        if (result.getIntegrityStatus().equals("FORMAT_MISMATCH")) {
            sb.append(" [Possible file spoofing or format tampering]");
        } else if (result.getIntegrityStatus().equals("UNKNOWN_FORMAT")) {
            sb.append(" [Unknown format or corrupted file]");
        } else {
            sb.append(" [File header intact]");
        }
        
        return sb.toString();
    }
    
    /**
     * File Header Analysis Result inner class
     */
    private static class FileHeaderAnalysisResult {
        private String detectedFormat;
        private String expectedFormat;
        private boolean formatMatch;
        private String signatureHex;
        private String integrityStatus;
        
        // Getters and setters
        public String getDetectedFormat() { return detectedFormat; }
        public void setDetectedFormat(String detectedFormat) { this.detectedFormat = detectedFormat; }
        
        public String getExpectedFormat() { return expectedFormat; }
        public void setExpectedFormat(String expectedFormat) { this.expectedFormat = expectedFormat; }
        
        public boolean isFormatMatch() { return formatMatch; }
        public void setFormatMatch(boolean formatMatch) { this.formatMatch = formatMatch; }
        
        public String getSignatureHex() { return signatureHex; }
        public void setSignatureHex(String signatureHex) { this.signatureHex = signatureHex; }
        
        public String getIntegrityStatus() { return integrityStatus; }
        public void setIntegrityStatus(String integrityStatus) { this.integrityStatus = integrityStatus; }
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
            
            // Normalize format/MIME (handles ffmpeg multi-format strings, MinIO contentType, file extension, codec)
            normalizeVideoFormatAndMime(metadata, filePath, grabber.getFormat());

            // Persist a human-readable raw metadata snapshot for videos as well
            if (metadata.getRawMetadata() == null || metadata.getRawMetadata().isEmpty()) {
                metadata.setRawMetadata(buildVideoRawMetadata(grabber, metadata));
            }
            
            // For video compression level, we can estimate based on bitrate and resolution
            if (metadata.getBitRate() != null && metadata.getImageWidth() != null && metadata.getImageHeight() != null) {
                int bitRate = metadata.getBitRate();
                int pixels = metadata.getImageWidth() * metadata.getImageHeight();
                
                // Calculate bits per pixel per frame as a compression indicator
                if (metadata.getFrameRate() != null && metadata.getFrameRate() > 0) {
                    double bitsPerPixelPerFrame = (double) bitRate / (pixels * metadata.getFrameRate());
                    
                    // Convert to a 1-100 scale (higher means less compression/better quality)
                    int compressionLevel;
                    if (bitsPerPixelPerFrame > 1.0) {
                        compressionLevel = 95; // Very high quality
                    } else if (bitsPerPixelPerFrame > 0.5) {
                        compressionLevel = 85; // High quality
                    } else if (bitsPerPixelPerFrame > 0.2) {
                        compressionLevel = 70; // Medium quality
                    } else if (bitsPerPixelPerFrame > 0.1) {
                        compressionLevel = 50; // Low quality
                    } else {
                        compressionLevel = 30; // Very low quality
                    }
                    
                    metadata.setCompressionLevel(compressionLevel);
                }
            }
            
            log.info("Extracted video metadata for {}: format={}, mimeType={}, duration={}s, fps={}, codec={}, dimensions={}x{}, compression={}", 
                     metadata.getFileMd5(), metadata.getFileFormat(), metadata.getMimeType(),
                     metadata.getVideoDuration(), metadata.getFrameRate(), metadata.getVideoCodec(),
                     metadata.getImageWidth(), metadata.getImageHeight(), metadata.getCompressionLevel());
            
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
        
        // 1. File Header/Signature Analysis (Critical for tampering detection)
        analyzeFileHeaderIntegrity(metadata, suspiciousIndicators, analysisNotes);
        
        // 2. EXIF Data Analysis
        analyzeExifData(metadata, suspiciousIndicators, analysisNotes);
        
        // 3. Image Dimension Analysis
        analyzeDimensions(metadata, suspiciousIndicators, analysisNotes);
        
        // 4. File Format and Technical Analysis
        analyzeFileFormat(metadata, suspiciousIndicators, analysisNotes);
        
        // 5. Temporal Inconsistency Analysis
        analyzeTemporalInconsistencies(metadata, suspiciousIndicators, analysisNotes);
        
        // 6. Device-Specific Analysis
        analyzeDeviceSpecificIndicators(metadata, suspiciousIndicators, analysisNotes);
        
        // 7. AI Generation Pattern Analysis
        analyzeAIGenerationPatterns(metadata, suspiciousIndicators, analysisNotes);
        
        // 8. Metadata Completeness Analysis
        analyzeMetadataCompleteness(metadata, suspiciousIndicators, analysisNotes);
        
        // 9. Generate overall tampering/AI generation probability
        generateTamperingProbabilityAssessment(metadata, suspiciousIndicators, analysisNotes);
        
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
    
    /**
     * Analyze file header integrity for tampering detection (using structured database fields)
     */
    private void analyzeFileHeaderIntegrity(MediaMetadata metadata, List<String> indicators, StringBuilder notes) {
        String integrityStatus = metadata.getFileIntegrityStatus();
        String detectedFormat = metadata.getDetectedFileFormat();
        String expectedFormat = metadata.getExpectedFileFormat();
        String signatureHex = metadata.getFileSignatureHex();
        
        if (integrityStatus != null) {
            switch (integrityStatus) {
                case "FORMAT_MISMATCH":
                    indicators.add("File header signature anomaly: Detected file format spoofing or tampering");
                    notes.append(String.format("File header analysis shows format mismatch (detected %s but expected %s), possible malicious tampering; ", 
                               detectedFormat, expectedFormat));
                    log.warn("Critical warning: File {} header signature does not match extension - Detected format: {}, Expected format: {}", 
                            metadata.getFileMd5(), detectedFormat, expectedFormat);
                    break;
                    
                case "UNKNOWN_FORMAT":
                    indicators.add("Unknown file format: May be corrupted file or hidden real format");
                    notes.append("Unable to identify file format, file may be corrupted or intentionally obfuscated; ");
                    break;
                    
                case "ANALYSIS_FAILED":
                    indicators.add("File header analysis failed: Unable to verify file integrity");
                    notes.append("File header analysis process failed; ");
                    break;
                    
                case "INTACT":
                    // Format is intact, but still perform advanced pattern analysis
                    notes.append(String.format("File header verification normal (%s format); ", detectedFormat));
                    break;
                    
                default:
                    notes.append("File header analysis status unknown; ");
            }
            
            // Perform advanced signature pattern analysis if signature is available
            if (signatureHex != null && !signatureHex.isEmpty()) {
                analyzeSignaturePatterns(signatureHex, metadata, indicators, notes);
            }
            
        } else {
            indicators.add("File header analysis data missing: Unable to verify file integrity");
            notes.append("Missing file header analysis data; ");
        }
    }
    
    
    /**
     * Analyze signature patterns for advanced tampering detection
     */
    private void analyzeSignaturePatterns(String signatureHex, MediaMetadata metadata, 
                                        List<String> indicators, StringBuilder notes) {
        
        // Check for truncated or corrupted signatures
        if (signatureHex.length() < 8) {
            indicators.add("File signature too short: May be truncated or corrupted file");
            notes.append("File signature length abnormal; ");
        }
        
        // Check for suspicious patterns indicating AI generation tools
        if (signatureHex.startsWith("FFD8FF") && signatureHex.length() >= 16) {
            // JPEG specific signature analysis
            String jpegMarker = signatureHex.substring(6, Math.min(16, signatureHex.length()));
            
            // Some AI generation tools leave specific JPEG markers
            if (jpegMarker.startsWith("E0") || jpegMarker.startsWith("E1")) {
                // Normal JPEG markers, but check for unusual patterns
                if (jpegMarker.equals("E000104A464946") || 
                    jpegMarker.equals("E1001845786966")) {
                    notes.append("Detected possible AI-generated JPEG marker pattern; ");
                }
            }
        }
        
        // Check for video container tampering
        if (signatureHex.startsWith("66747970")) { // 'ftyp' box
            // Check for unusual brand codes that might indicate manipulation
            if (signatureHex.contains("6D703432")) { // 'mp42'
                notes.append("Standard MP4 container; ");
            } else {
                notes.append("Detected non-standard container brand code; ");
            }
        }
    }
    
    /**
     * Generate overall tampering/AI generation probability assessment
     */
    private void generateTamperingProbabilityAssessment(MediaMetadata metadata, 
                                                       List<String> indicators, StringBuilder notes) {
        
        int totalRiskScore = 0;
        int maxPossibleScore = 100;
        
        // Calculate risk score based on different indicators
        for (String indicator : indicators) {
            String indicatorLower = indicator.toLowerCase();
            
            // High risk indicators (25-40 points each)
            if (indicatorLower.contains("File header signature anomaly") || 
                indicatorLower.contains("file format spoofing") || 
                indicatorLower.contains("tampering")) {
                totalRiskScore += 40;
            }
            // AI generation indicators (20-35 points each)
            else if (indicatorLower.contains("ai generated") || 
                     indicatorLower.contains("artificial intelligence") ||
                     indicatorLower.contains("high suspicion of ai")) {
                totalRiskScore += 35;
            }
            // Medium risk indicators (10-20 points each)
            else if (indicatorLower.contains("missing camera info") || 
                     indicatorLower.contains("format mismatch") ||
                     indicatorLower.contains("completely missing") ||
                     indicatorLower.contains("ai common dimensions")) {
                totalRiskScore += 15;
            }
            // Low risk indicators (5-10 points each)
            else if (indicatorLower.contains("compression quality") || 
                     indicatorLower.contains("time anomaly") ||
                     indicatorLower.contains("incomplete metadata")) {
                totalRiskScore += 8;
            }
            // Minor indicators (1-5 points each)
            else {
                totalRiskScore += 3;
            }
        }
        
        // Cap the score at maximum
        totalRiskScore = Math.min(totalRiskScore, maxPossibleScore);
        
        // Generate probability assessment conclusion
        String probabilityAssessment = generateProbabilityConclusion(totalRiskScore, metadata);
        
        // Store structured risk assessment in dedicated fields
        metadata.setRiskScore(totalRiskScore);
        metadata.setAssessmentConclusion(probabilityAssessment);
        
        // Also add to indicators and notes for backward compatibility and human readability
        indicators.add("Risk assessment conclusion: " + probabilityAssessment);
        notes.append("Risk score: ").append(totalRiskScore).append("/100; ");
        notes.append("Assessment conclusion: ").append(probabilityAssessment).append("; ");
        
        log.info("File {} risk assessment completed - Score: {}/100, Conclusion: {}", 
                metadata.getFileMd5(), totalRiskScore, probabilityAssessment);
    }
    
    /**
     * Generate probability-based conclusion
     */
    private String generateProbabilityConclusion(int riskScore, MediaMetadata metadata) {
        boolean isVideo = metadata.getVideoDuration() != null || metadata.getVideoCodec() != null;
        String mediaType = isVideo ? "video" : "image";
        
        if (riskScore >= 70) {
            return String.format("This %s has high suspicion of tampering or AI generation (confidence: %d%%)", mediaType, riskScore);
        } else if (riskScore >= 40) {
            return String.format("This %s may have been edited or is AI-generated content (confidence: %d%%)", mediaType, riskScore);
        } else if (riskScore >= 20) {
            return String.format("This %s has minor anomalies, further verification recommended (confidence: %d%%)", mediaType, riskScore);
        } else if (riskScore >= 10) {
            return String.format("This %s is basically normal, only minor suspicious indicators found (confidence: %d%%)", mediaType, riskScore);
        } else {
            return String.format("This %s shows no obvious anomalies, likely original content (confidence: %d%%)", mediaType, Math.max(100 - riskScore, 85));
        }
    }
    
    private void analyzeExifData(MediaMetadata metadata, List<String> indicators, StringBuilder notes) {
        // Distinguish between image and video analysis
        boolean isVideo = metadata.getVideoDuration() != null || metadata.getVideoCodec() != null;
        
        // For videos, missing camera info is completely normal
        if (metadata.getCameraMake() == null && metadata.getCameraModel() == null) {
            if (isVideo) {
                // For videos, this is extremely common and normal
                // Most video editing software, mobile apps, and even professional cameras
                // don't embed camera make/model in video metadata
                // Only flag if ALL technical metadata is missing (which would be very unusual)
                if (metadata.getDateTaken() == null && metadata.getFileFormat() == null && 
                    metadata.getVideoCodec() == null && metadata.getAudioCodec() == null) {
                    indicators.add("Video completely missing technical information: May have anomalies");
                    notes.append("Video file missing all technical identification information; ");
                }
            } else {
                // For images, missing camera info is more suspicious
                indicators.add("Image missing camera information: May be screenshot, edited, or AI-generated content");
                notes.append("EXIF data missing camera manufacturer and model information; ");
            }
        }
        
        // Check for incomplete EXIF data - only for images
        if (!isVideo) {
            int exifFields = 0;
            if (metadata.getCameraMake() != null) exifFields++;
            if (metadata.getCameraModel() != null) exifFields++;
            if (metadata.getDateTaken() != null) exifFields++;
            if (metadata.getOrientation() != null) exifFields++;
            if (metadata.getColorSpace() != null) exifFields++;
            
            if (exifFields <= 1 && exifFields > 0) {
                indicators.add("EXIF data incomplete: May have been processed or source suspicious");
            }
        }
    }
    
    private void analyzeDimensions(MediaMetadata metadata, List<String> indicators, StringBuilder notes) {
        if (metadata.getImageWidth() != null && metadata.getImageHeight() != null) {
            int width = metadata.getImageWidth();
            int height = metadata.getImageHeight();
            
            // Check for common AI generation resolutions
            if (isAICommonResolution(width, height)) {
                indicators.add("Image dimensions match common AI-generated content resolutions");
                notes.append(String.format("Detected AI common resolution: %dx%d; ", width, height));
            }
            
            // Check for unusual aspect ratios
            double aspectRatio = (double) width / height;
            if (aspectRatio == 1.0 && (width >= 256 && width <= 2048)) {
                indicators.add("Perfect square aspect ratio: May be AI-generated or cropped");
            }
            
            // Check for very high resolutions without corresponding quality metadata
            if ((width > 4000 || height > 4000) && metadata.getCameraMake() == null) {
                indicators.add("High resolution image missing camera info: May be result of upscaling");
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
                indicators.add("File format does not match MIME type: May have undergone format conversion");
                notes.append(String.format("Format inconsistency: %s vs %s; ", format, mimeType));
            }
        }
        
        // Check compression levels
        Integer compression = metadata.getCompressionLevel();
        if (compression != null && format != null) {
            if ("JPEG".equalsIgnoreCase(format) && compression < 50) {
                indicators.add("JPEG compression quality too low: May have been saved multiple times or processed");
            }
            if ("PNG".equalsIgnoreCase(format) && compression > 6) {
                indicators.add("PNG compression level abnormal: May have undergone special processing");
            }
        }
    }
    
    private void analyzeTemporalInconsistencies(MediaMetadata metadata, List<String> indicators, StringBuilder notes) {
        if (metadata.getDateTaken() != null) {
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime dateTaken = metadata.getDateTaken();
            
            // Check for future dates
            if (dateTaken.isAfter(now)) {
                indicators.add("Capture time shows future date: EXIF data may be tampered with");
                notes.append("Future date detected; ");
            }
            
            // Check for very old dates
            if (dateTaken.isBefore(LocalDateTime.of(1990, 1, 1, 0, 0))) {
                indicators.add("Capture time abnormally old: May be default or incorrect timestamp");
            }
            
            // Check for exact round times (suspicious for manual editing)
            if (dateTaken.getSecond() == 0 && dateTaken.getNano() == 0) {
                if (dateTaken.getMinute() == 0 || dateTaken.getMinute() % 5 == 0) {
                    notes.append("Round time detected, may have been manually set; ");
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
                indicators.add("Smartphone capture but missing GPS info: Location may be disabled or processed");
            }
            
            // Check for unusual camera combinations
            if (model != null && !isValidCameraModelForMake(make, model)) {
                indicators.add("Camera make does not match model: May be forged EXIF data");
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
                    indicators.add("Completely missing capture info and AI common dimensions: High suspicion of AI-generated content");
                    notes.append("No camera device info and matches AI generation dimension patterns; ");
                } else {
                    indicators.add("Image missing capture information: May be screenshot, edited, or AI-generated content");
                    notes.append("No camera device information; ");
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
                    indicators.add("Video missing device info and has abnormal technical parameters: May be generated or heavily processed");
                    notes.append("Video file has no device info and abnormal technical parameters; ");
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
                indicators.add("AI generation tool identifier detected: Confirmed as AI-generated content");
            }
        }
    }
    
    private void analyzeMetadataCompleteness(MediaMetadata metadata, List<String> indicators, StringBuilder notes) {
        boolean isVideo = metadata.getVideoDuration() != null || metadata.getVideoCodec() != null;
        
        int totalFields = 0;
        int populatedFields = 0;
        
        // Different field expectations for images vs videos
        if (isVideo) {
            // For videos, focus ONLY on essential technical metadata
            // Most video files normally lack camera info, date info, etc.
            
            // Essential video technical fields
            if (metadata.getVideoCodec() != null && !metadata.getVideoCodec().isEmpty()) populatedFields++;
            totalFields++;
            
            if (metadata.getVideoDuration() != null && metadata.getVideoDuration() > 0) populatedFields++;
            totalFields++;
            
            if (metadata.getImageWidth() != null && metadata.getImageHeight() != null && 
                metadata.getImageWidth() > 0 && metadata.getImageHeight() > 0) populatedFields++;
            totalFields++;
            
            // Optional but common fields (don't count as missing if absent)
            if (metadata.getFrameRate() != null && metadata.getFrameRate() > 0) {
                populatedFields++;
                totalFields++;
            }
            
            if (metadata.getBitRate() != null && metadata.getBitRate() > 0) {
                populatedFields++;
                totalFields++;
            }
            
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
        
        // Much more lenient thresholds - only flag severely broken files
        double threshold = isVideo ? 0.1 : 0.3;  // Only flag videos missing 90% of basic technical data
        
        if (completeness < threshold) {
            if (isVideo) {
                // Only flag if video is missing critical technical information
                indicators.add("Video missing core technical parameters: File may be corrupted or abnormal");
                notes.append(String.format("Core technical parameter completeness: %.1f%%; ", completeness * 100));
            } else {
                indicators.add("Image metadata completeness extremely low: May have been cleaned or source suspicious");
                notes.append(String.format("Image metadata completeness: %.1f%%; ", completeness * 100));
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

        String f = format.toLowerCase();
        String m = mimeType.toLowerCase();

        // If mime is video/*, accept common container synonyms
        if (m.startsWith("video/")) {
            // Normalize list-like formats (e.g., "mov,mp4,m4a,3gp,3g2,mj2")
            Set<String> tokens = new HashSet<>();
            for (String t : f.split(",")) { tokens.add(t.trim()); }
            if (tokens.isEmpty()) tokens.add(f.trim());

            if (m.contains("mp4") || m.contains("quicktime") || m.equals("video/mp4")) {
                return tokens.contains("mp4") || tokens.contains("mov") || f.contains("mp4") || f.contains("mov");
            }
            if (m.contains("webm")) {
                return tokens.contains("webm") || f.contains("webm");
            }
            if (m.contains("matroska") || m.contains("mkv")) {
                return tokens.contains("mkv") || f.contains("matroska") || f.contains("mkv");
            }
            if (m.contains("avi")) {
                return tokens.contains("avi") || f.contains("avi");
            }
            if (m.contains("wmv")) {
                return tokens.contains("wmv") || f.contains("wmv");
            }
            if (m.contains("flv")) {
                return tokens.contains("flv") || f.contains("flv");
            }
            // If unknown, be lenient
            return true;
        }

        // Images
        return (f.contains("jpeg") && m.contains("jpeg")) ||
               (f.contains("png") && m.contains("png")) ||
               (f.contains("gif") && m.contains("gif")) ||
               (f.contains("bmp") && m.contains("bmp")) ||
               (f.contains("tiff") && m.contains("tiff")) ||
               (f.contains("webp") && m.contains("webp"));
    }

    private void normalizeVideoFormatAndMime(MediaMetadata metadata, String filePath, String ffFormat) {
        String ext = null;
        if (filePath != null) {
            String lower = filePath.toLowerCase();
            int dot = lower.lastIndexOf('.');
            if (dot >= 0 && dot < lower.length() - 1) {
                ext = lower.substring(dot + 1);
            }
        }

        // 1) Prefer MinIO contentType when valid
        String contentType = null;
        try {
            StatObjectResponse stat = minioClient.statObject(
                StatObjectArgs.builder().bucket(minioBucketName).object(filePath).build()
            );
            if (stat != null) contentType = stat.contentType();
        } catch (Exception ignored) {}

        String canonicalFormat = null;
        if (ext != null) {
            switch (ext) {
                case "mp4":
                case "m4v":
                case "mov":
                    canonicalFormat = "MP4"; break;
                case "webm":
                    canonicalFormat = "WEBM"; break;
                case "mkv":
                    canonicalFormat = "MKV"; break;
                case "avi":
                    canonicalFormat = "AVI"; break;
                case "wmv":
                    canonicalFormat = "WMV"; break;
                case "flv":
                    canonicalFormat = "FLV"; break;
                default:
                    break;
            }
        }

        // 2) If still unknown, normalize ffmpeg's multi-format string
        if (canonicalFormat == null && ffFormat != null && !ffFormat.isBlank()) {
            String low = ffFormat.toLowerCase();
            if (low.contains("mp4") || low.contains("mov")) canonicalFormat = "MP4";
            else if (low.contains("webm")) canonicalFormat = "WEBM";
            else if (low.contains("matroska") || low.contains("mkv")) canonicalFormat = "MKV";
            else if (low.contains("avi")) canonicalFormat = "AVI";
            else if (low.contains("wmv")) canonicalFormat = "WMV";
            else if (low.contains("flv")) canonicalFormat = "FLV";
        }

        if (canonicalFormat == null) canonicalFormat = metadata.getFileFormat();
        if (canonicalFormat == null) canonicalFormat = "VIDEO";
        metadata.setFileFormat(canonicalFormat);

        // Derive canonical MIME
        String mime = null;
        if (contentType != null && !contentType.isBlank() && !contentType.contains(",")) {
            // Use valid single content-type
            mime = contentType;
        }
        if (mime == null) {
            switch (canonicalFormat) {
                case "MP4": mime = "video/mp4"; break;
                case "WEBM": mime = "video/webm"; break;
                case "MKV": mime = "video/x-matroska"; break;
                case "AVI": mime = "video/avi"; break;
                case "WMV": mime = "video/x-ms-wmv"; break;
                case "FLV": mime = "video/x-flv"; break;
                default: mime = "video/unknown";
            }
        }
        metadata.setMimeType(mime);
    }

    private String buildVideoRawMetadata(FFmpegFrameGrabber grabber, MediaMetadata metadata) {
        StringBuilder sb = new StringBuilder();
        sb.append("Container:\n");
        sb.append("  Format: ").append(grabber.getFormat()).append("\n");
        sb.append("  CanonicalFormat: ").append(metadata.getFileFormat()).append("\n\n");

        sb.append("Streams:\n");
        sb.append("  VideoCodec: ").append(grabber.getVideoCodecName()).append("\n");
        sb.append("  AudioCodec: ").append(grabber.getAudioCodecName()).append("\n");
        sb.append("  FrameRate: ").append(grabber.getVideoFrameRate()).append("\n");
        sb.append("  BitRate: ").append(grabber.getVideoBitrate()).append("\n");
        sb.append("  Width: ").append(grabber.getImageWidth()).append("\n");
        sb.append("  Height: ").append(grabber.getImageHeight()).append("\n");
        sb.append("  DurationSec: ").append(grabber.getLengthInTime() / 1_000_000).append("\n\n");

        sb.append("File Type:\n");
        sb.append("  Detected File Type Name: ").append(metadata.getFileFormat()).append("\n");
        sb.append("  Detected MIME Type: ").append(metadata.getMimeType()).append("\n");
        return sb.toString();
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
    
    /**
     * Parse raw metadata string into structured JSON format
     */
    private Map<String, Object> parseRawMetadataToJson(String rawMetadata) {
        Map<String, Object> parsedMetadata = new HashMap<>();
        
        if (rawMetadata == null || rawMetadata.isEmpty()) {
            return parsedMetadata;
        }
        
        try {
            // Add debug information first
            parsedMetadata.put("_debug_raw_preview", rawMetadata.length() > 200 ? rawMetadata.substring(0, 200) + "..." : rawMetadata);
            
            String[] lines = rawMetadata.split("\\r?\\n");
            String currentDirectory = null;
            Map<String, Object> currentDirData = new HashMap<>();
            
            for (String line : lines) {
                String originalLine = line;
                line = line.trim();
                
                if (line.isEmpty()) {
                    // Empty line, continue but don't save directory yet
                    continue;
                }
                
                // Check if this is a directory header (ends with colon and doesn't start with space)
                if (line.endsWith(":") && !originalLine.startsWith(" ") && !originalLine.startsWith("\t")) {
                    // Save previous directory if exists
                    if (currentDirectory != null && !currentDirData.isEmpty()) {
                        parsedMetadata.put(currentDirectory, new HashMap<>(currentDirData));
                    }
                    
                    // Start new directory
                    currentDirectory = line.substring(0, line.length() - 1).trim();
                    currentDirData = new HashMap<>();
                    
                } else if ((originalLine.startsWith("  ") || originalLine.startsWith("\t")) && currentDirectory != null) {
                    // This is a tag within a directory
                    String tagLine = line; // line is already trimmed
                    int colonIndex = tagLine.indexOf(':');
                    
                    if (colonIndex > 0 && colonIndex < tagLine.length()) {
                        String key = tagLine.substring(0, colonIndex).trim();
                        String value = colonIndex < tagLine.length() - 1 ? tagLine.substring(colonIndex + 1).trim() : "";
                        
                        // Try to parse numeric values
                        Object parsedValue = parseMetadataValue(value);
                        currentDirData.put(key, parsedValue);
                    } else if (!tagLine.contains(":")) {
                        // Handle lines without colon (just values or continuation)
                        currentDirData.put("_unparsed_line_" + currentDirData.size(), tagLine);
                    }
                }
            }
            
            // Don't forget the last directory
            if (currentDirectory != null && !currentDirData.isEmpty()) {
                parsedMetadata.put(currentDirectory, currentDirData);
            }
            
            // Remove debug info if we have actual data
            if (parsedMetadata.size() > 1) {
                parsedMetadata.remove("_debug_raw_preview");
            }
            
        } catch (Exception e) {
            log.warn("Error parsing raw metadata to JSON", e);
            parsedMetadata.put("_parsing_error", "Failed to parse raw metadata: " + e.getMessage());
        }
        
        return parsedMetadata;
    }
    
    /**
     * Parse metadata value to appropriate type (String, Integer, Double, etc.)
     */
    private Object parseMetadataValue(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        
        // Try to parse as integer
        try {
            // Look for pure numbers
            if (value.matches("^-?\\d+$")) {
                return Integer.parseInt(value);
            }
            
            // Look for numbers with units (extract the number part)
            if (value.matches("^-?\\d+\\s*(pixels|bits|dots per inch|mm|sec|EV|%).*")) {
                String numberPart = value.replaceAll("^(-?\\d+).*", "$1");
                return Integer.parseInt(numberPart);
            }
            
            // Look for fractions like "1/250"
            if (value.matches("^\\d+/\\d+.*")) {
                String[] parts = value.split("/");
                if (parts.length >= 2) {
                    double numerator = Double.parseDouble(parts[0]);
                    double denominator = Double.parseDouble(parts[1].replaceAll("[^0-9]", ""));
                    if (denominator != 0) {
                        return numerator / denominator;
                    }
                }
            }
            
            // Look for f-numbers like "f/8.0"
            if (value.matches("^f/[0-9.]+")) {
                String numberPart = value.substring(2);
                return Double.parseDouble(numberPart);
            }
            
        } catch (NumberFormatException e) {
            // Not a number, continue with other parsing
        }
        
        // Try to parse as double
        try {
            if (value.matches("^-?\\d*\\.\\d+.*")) {
                String numberPart = value.replaceAll("^(-?\\d*\\.\\d+).*", "$1");
                return Double.parseDouble(numberPart);
            }
        } catch (NumberFormatException e) {
            // Not a double, keep as string
        }
        
        // Parse boolean values
        if ("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value)) {
            return Boolean.parseBoolean(value);
        }
        
        // Return as string if no specific type detected
        return value;
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
        
        // Use dedicated database fields first, fallback to extraction for backward compatibility
        Integer riskScore = metadata.getRiskScore();
        if (riskScore == null) {
            riskScore = extractRiskScoreFromAnalysisNotes(metadata.getAnalysisNotes());
        }
        indicators.put("riskScore", riskScore != null ? riskScore : 0);
        
        String conclusion = metadata.getAssessmentConclusion();
        if (conclusion == null) {
            conclusion = extractConclusionFromSuspiciousIndicators(metadata.getSuspiciousIndicators());
        }
        indicators.put("assessmentConclusion", conclusion);
        
        return indicators;
    }
    
    /**
     * Build file header analysis data
     */
    private Map<String, Object> buildFileHeaderAnalysis(MediaMetadata metadata) {
        Map<String, Object> headerAnalysis = new HashMap<>();
        headerAnalysis.put("detectedFormat", metadata.getDetectedFileFormat());
        headerAnalysis.put("expectedFormat", metadata.getExpectedFileFormat());
        headerAnalysis.put("formatMatch", metadata.getFileFormatMatch());
        headerAnalysis.put("signatureHex", metadata.getFileSignatureHex());
        headerAnalysis.put("integrityStatus", metadata.getFileIntegrityStatus());
        
        // Add analysis summary
        if (metadata.getFileIntegrityStatus() != null) {
            switch (metadata.getFileIntegrityStatus()) {
                case "INTACT":
                    headerAnalysis.put("summary", "File header intact, format verification normal");
                    headerAnalysis.put("riskLevel", "LOW");
                    break;
                case "FORMAT_MISMATCH":
                    headerAnalysis.put("summary", "File format does not match extension, possible spoofing or tampering");
                    headerAnalysis.put("riskLevel", "HIGH");
                    break;
                case "UNKNOWN_FORMAT":
                    headerAnalysis.put("summary", "Unknown file format, may be corrupted or special file");
                    headerAnalysis.put("riskLevel", "MEDIUM");
                    break;
                case "ANALYSIS_FAILED":
                    headerAnalysis.put("summary", "File header analysis failed");
                    headerAnalysis.put("riskLevel", "UNKNOWN");
                    break;
                default:
                    headerAnalysis.put("summary", "Analysis status unknown");
                    headerAnalysis.put("riskLevel", "UNKNOWN");
            }
        }
        
        return headerAnalysis;
    }
    
    /**
     * Build container analysis data (placeholder for future implementation)
     */
    private Map<String, Object> buildContainerAnalysis(MediaMetadata metadata) {
        Map<String, Object> containerAnalysis = new HashMap<>();
        containerAnalysis.put("integrityVerified", metadata.getContainerIntegrityVerified());
        containerAnalysis.put("analysisResults", metadata.getContainerAnalysisResults());
        
        // Placeholder for future Week 7 requirements
        containerAnalysis.put("status", "PENDING_IMPLEMENTATION");
        containerAnalysis.put("message", "Container integrity analysis feature coming soon");
        
        return containerAnalysis;
    }
    
    /**
     * Extract risk score from analysis notes
     */
    private int extractRiskScoreFromAnalysisNotes(String analysisNotes) {
        if (analysisNotes == null || analysisNotes.isEmpty()) {
            return 0;
        }
        
        try {
            // Look for pattern "Risk Score: XX/100"
            String pattern = "Risk Score: ";
            int startIndex = analysisNotes.indexOf(pattern);
            if (startIndex >= 0) {
                int scoreStart = startIndex + pattern.length();
                int scoreEnd = analysisNotes.indexOf("/", scoreStart);
                if (scoreEnd > scoreStart) {
                    String scoreStr = analysisNotes.substring(scoreStart, scoreEnd).trim();
                    return Integer.parseInt(scoreStr);
                }
            }
        } catch (Exception e) {
            log.debug("Failed to extract risk score from analysis notes", e);
        }
        
        return 0;
    }
    
    /**
     * Extract assessment conclusion from suspicious indicators
     */
    private String extractConclusionFromSuspiciousIndicators(String suspiciousIndicators) {
        if (suspiciousIndicators == null || suspiciousIndicators.isEmpty()) {
            return null;
        }
        
        try {
            // Look for pattern "Risk assessment conclusion: ..."
            String pattern = "Risk assessment conclusion: ";
            int startIndex = suspiciousIndicators.indexOf(pattern);
            if (startIndex >= 0) {
                int conclusionStart = startIndex + pattern.length();
                int conclusionEnd = suspiciousIndicators.indexOf(";", conclusionStart);
                if (conclusionEnd > conclusionStart) {
                    return suspiciousIndicators.substring(conclusionStart, conclusionEnd).trim();
                } else {
                    // If no semicolon found, take the rest of the string
                    return suspiciousIndicators.substring(conclusionStart).trim();
                }
            }
        } catch (Exception e) {
            log.debug("Failed to extract conclusion from suspicious indicators", e);
        }
        
        return null;
    }
    
    private Map<String, Object> buildParsedMetadata(MediaMetadata metadata) {
        Map<String, Object> parsedData = new HashMap<>();
        
        // Parse raw metadata to structured JSON
        if (metadata.getRawMetadata() != null && !metadata.getRawMetadata().isEmpty()) {
            parsedData = parseRawMetadataToJson(metadata.getRawMetadata());
        }
        
        // Add some summary statistics
        Map<String, Object> summary = new HashMap<>();
        summary.put("totalDirectories", parsedData.size());
        summary.put("hasRawMetadata", metadata.getRawMetadata() != null);
        summary.put("rawMetadataLength", metadata.getRawMetadata() != null ? metadata.getRawMetadata().length() : 0);
        
        parsedData.put("_summary", summary);
        
        return parsedData;
    }
}

