package com.itproject.upload.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class FileTypeValidationServiceTest {

    private FileTypeValidationService fileTypeValidationService;

    @BeforeEach
    void setUp() {
        fileTypeValidationService = new FileTypeValidationService();
    }

    @Test
    void testValidateFileType_ValidImageExtension() {
        String fileName = "test.jpg";
        
        FileTypeValidationService.FileTypeValidationResult result = 
            fileTypeValidationService.validateFileType(fileName);
        
        assertTrue(result.isValid());
        assertEquals("IMAGE", result.getFileType());
        assertEquals("Valid image file extension", result.getMessage());
    }

    @Test
    void testValidateFileType_ValidVideoExtension() {
        String fileName = "test.mp4";
        
        FileTypeValidationService.FileTypeValidationResult result = 
            fileTypeValidationService.validateFileType(fileName);
        
        assertTrue(result.isValid());
        assertEquals("VIDEO", result.getFileType());
        assertEquals("Valid video file extension", result.getMessage());
    }

    @Test
    void testValidateFileType_UnsupportedExtension() {
        String fileName = "test.txt";
        
        FileTypeValidationService.FileTypeValidationResult result = 
            fileTypeValidationService.validateFileType(fileName);
        
        assertFalse(result.isValid());
        assertEquals("UNKNOWN", result.getFileType());
        assertTrue(result.getMessage().contains("Unsupported file extension"));
    }

    @Test
    void testValidateFileType_EmptyFileName() {
        String fileName = "";
        
        FileTypeValidationService.FileTypeValidationResult result = 
            fileTypeValidationService.validateFileType(fileName);
        
        assertFalse(result.isValid());
        assertEquals("UNKNOWN", result.getFileType());
    }

    @Test
    void testValidateFileType_NoExtension() {
        String fileName = "test";
        
        FileTypeValidationService.FileTypeValidationResult result = 
            fileTypeValidationService.validateFileType(fileName);
        
        assertFalse(result.isValid());
        assertEquals("UNKNOWN", result.getFileType());
    }

    @Test
    void testValidateFileType_WithMultipartFile_ValidImage() throws IOException {
        String fileName = "test.jpg";
        MultipartFile file = new MockMultipartFile(
            "file", 
            fileName, 
            "image/jpeg", 
            "fake image content".getBytes()
        );
        
        FileTypeValidationService.FileTypeValidationResult result = 
            fileTypeValidationService.validateFileType(fileName, file);
        
        assertTrue(result.isValid());
        assertEquals("IMAGE", result.getFileType());
        assertTrue(result.getMessage().contains("Valid image file"));
    }

    @Test
    void testValidateFileType_WithMultipartFile_ValidVideo() throws IOException {
        String fileName = "test.mp4";
        MultipartFile file = new MockMultipartFile(
            "file", 
            fileName, 
            "video/mp4", 
            "fake video content".getBytes()
        );
        
        FileTypeValidationService.FileTypeValidationResult result = 
            fileTypeValidationService.validateFileType(fileName, file);
        
        assertTrue(result.isValid());
        assertEquals("VIDEO", result.getFileType());
        assertTrue(result.getMessage().contains("Valid video file"));
    }

    @Test
    void testValidateFileType_WithMultipartFile_InvalidContent() throws IOException {
        String fileName = "test.jpg";
        MultipartFile file = new MockMultipartFile(
            "file", 
            fileName, 
            "text/plain", 
            "fake text content".getBytes()
        );
        
        FileTypeValidationService.FileTypeValidationResult result = 
            fileTypeValidationService.validateFileType(fileName, file);
        
        // Should still be valid because extension matches
        assertTrue(result.isValid());
        assertEquals("IMAGE", result.getFileType());
    }

    @Test
    void testGetSupportedImageExtensions() {
        Set<String> imageExtensions = fileTypeValidationService.getSupportedImageExtensions();
        
        assertNotNull(imageExtensions);
        assertTrue(imageExtensions.contains("jpg"));
        assertTrue(imageExtensions.contains("png"));
        assertTrue(imageExtensions.contains("gif"));
        assertTrue(imageExtensions.contains("tiff"));
    }

    @Test
    void testGetSupportedVideoExtensions() {
        Set<String> videoExtensions = fileTypeValidationService.getSupportedVideoExtensions();
        
        assertNotNull(videoExtensions);
        assertTrue(videoExtensions.contains("mp4"));
        assertTrue(videoExtensions.contains("avi"));
        assertTrue(videoExtensions.contains("mov"));
        assertTrue(videoExtensions.contains("mkv"));
    }

    @Test
    void testGetAllSupportedExtensions() {
        Set<String> allExtensions = fileTypeValidationService.getAllSupportedExtensions();
        
        assertNotNull(allExtensions);
        assertTrue(allExtensions.contains("jpg"));
        assertTrue(allExtensions.contains("mp4"));
        assertTrue(allExtensions.size() > 0);
    }

    @Test
    void testFileTypeValidationResult_Constructor() {
        FileTypeValidationService.FileTypeValidationResult result = 
            new FileTypeValidationService.FileTypeValidationResult(
                true, "IMAGE", "Test message", "image/jpeg"
            );
        
        assertTrue(result.isValid());
        assertEquals("IMAGE", result.getFileType());
        assertEquals("Test message", result.getMessage());
        assertEquals("image/jpeg", result.getDetectedMimeType());
    }

    @Test
    void testFileTypeValidationResult_NullMimeType() {
        FileTypeValidationService.FileTypeValidationResult result = 
            new FileTypeValidationService.FileTypeValidationResult(
                false, "UNKNOWN", "Test message", null
            );
        
        assertFalse(result.isValid());
        assertEquals("UNKNOWN", result.getFileType());
        assertEquals("Test message", result.getMessage());
        assertNull(result.getDetectedMimeType());
    }

    @Test
    void testCaseInsensitiveExtensions() {
        // Test uppercase extensions
        FileTypeValidationService.FileTypeValidationResult result1 = 
            fileTypeValidationService.validateFileType("test.JPG");
        assertTrue(result1.isValid());
        assertEquals("IMAGE", result1.getFileType());

        // Test mixed case extensions
        FileTypeValidationService.FileTypeValidationResult result2 = 
            fileTypeValidationService.validateFileType("test.Mp4");
        assertTrue(result2.isValid());
        assertEquals("VIDEO", result2.getFileType());
    }
}
