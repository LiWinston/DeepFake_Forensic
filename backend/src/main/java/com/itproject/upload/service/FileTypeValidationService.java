package com.itproject.upload.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FilenameUtils;
import org.apache.tika.Tika;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Service for validating file types for forensic analysis
 */
@Slf4j
@Service
public class FileTypeValidationService {
    
    // Supported image formats for forensic analysis
    private static final Set<String> SUPPORTED_IMAGE_EXTENSIONS = new HashSet<>(Arrays.asList(
        "jpg", "jpeg", "png", "gif", "tiff", "tif", "bmp", "webp"
    ));
    
    // Supported video formats for forensic analysis
    private static final Set<String> SUPPORTED_VIDEO_EXTENSIONS = new HashSet<>(Arrays.asList(
        "mp4", "avi", "mov", "wmv", "flv", "mkv", "webm", "m4v", "3gp", "mts"
    ));
    
    // MIME types for images
    private static final Set<String> SUPPORTED_IMAGE_MIME_TYPES = new HashSet<>(Arrays.asList(
        "image/jpeg", "image/png", "image/gif", "image/tiff", "image/bmp", "image/webp"
    ));
    
    // MIME types for videos
    private static final Set<String> SUPPORTED_VIDEO_MIME_TYPES = new HashSet<>(Arrays.asList(
        "video/mp4", "video/avi", "video/quicktime", "video/x-msvideo", "video/x-flv",
        "video/x-matroska", "video/webm", "video/3gpp"
    ));
    
    private final Tika tika = new Tika();
    
    /**
     * Validate file type based on filename and content
     */
    public FileTypeValidationResult validateFileType(String fileName, MultipartFile file) {
        try {
            // Extract file extension
            String extension = FilenameUtils.getExtension(fileName).toLowerCase();
            
            // Detect MIME type using Tika
            String detectedMimeType = tika.detect(file.getInputStream(), fileName);
            
            log.debug("File validation - Name: {}, Extension: {}, Detected MIME: {}", 
                     fileName, extension, detectedMimeType);
            
            // Validate based on extension and MIME type
            if (isImageFile(extension, detectedMimeType)) {
                return new FileTypeValidationResult(true, "IMAGE", "Valid image file", detectedMimeType);
            } else if (isVideoFile(extension, detectedMimeType)) {
                return new FileTypeValidationResult(true, "VIDEO", "Valid video file", detectedMimeType);
            } else {
                return new FileTypeValidationResult(false, "UNKNOWN", 
                    "Unsupported file type. Only images and videos are supported for forensic analysis.", 
                    detectedMimeType);
            }
            
        } catch (IOException e) {
            log.error("Error validating file type for file: {}", fileName, e);
            return new FileTypeValidationResult(false, "ERROR", 
                "Error reading file content: " + e.getMessage(), null);
        }
    }
    
    /**
     * Validate file type based on filename only
     */
    public FileTypeValidationResult validateFileType(String fileName) {
        String extension = FilenameUtils.getExtension(fileName).toLowerCase();
        
        if (SUPPORTED_IMAGE_EXTENSIONS.contains(extension)) {
            return new FileTypeValidationResult(true, "IMAGE", "Valid image file extension", null);
        } else if (SUPPORTED_VIDEO_EXTENSIONS.contains(extension)) {
            return new FileTypeValidationResult(true, "VIDEO", "Valid video file extension", null);
        } else {
            return new FileTypeValidationResult(false, "UNKNOWN", 
                "Unsupported file extension. Only images and videos are supported for forensic analysis.", null);
        }
    }
    
    private boolean isImageFile(String extension, String mimeType) {
        return SUPPORTED_IMAGE_EXTENSIONS.contains(extension) || 
               SUPPORTED_IMAGE_MIME_TYPES.contains(mimeType);
    }
    
    private boolean isVideoFile(String extension, String mimeType) {
        return SUPPORTED_VIDEO_EXTENSIONS.contains(extension) || 
               SUPPORTED_VIDEO_MIME_TYPES.contains(mimeType);
    }
    
    public Set<String> getSupportedImageExtensions() {
        return new HashSet<>(SUPPORTED_IMAGE_EXTENSIONS);
    }
    
    public Set<String> getSupportedVideoExtensions() {
        return new HashSet<>(SUPPORTED_VIDEO_EXTENSIONS);
    }
    
    public Set<String> getAllSupportedExtensions() {
        Set<String> allExtensions = new HashSet<>();
        allExtensions.addAll(SUPPORTED_IMAGE_EXTENSIONS);
        allExtensions.addAll(SUPPORTED_VIDEO_EXTENSIONS);
        return allExtensions;
    }
    
    /**
     * Result class for file type validation
     */
    public static class FileTypeValidationResult {
        private final boolean valid;
        private final String fileType;
        private final String message;
        private final String detectedMimeType;
        
        public FileTypeValidationResult(boolean valid, String fileType, String message, String detectedMimeType) {
            this.valid = valid;
            this.fileType = fileType;
            this.message = message;
            this.detectedMimeType = detectedMimeType;
        }
        
        public boolean isValid() { return valid; }
        public String getFileType() { return fileType; }
        public String getMessage() { return message; }
        public String getDetectedMimeType() { return detectedMimeType; }
    }
}
