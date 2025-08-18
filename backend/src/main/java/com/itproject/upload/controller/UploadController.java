package com.itproject.upload.controller;

import com.itproject.upload.dto.ChunkUploadRequest;
import com.itproject.upload.dto.UploadResponse;
import com.itproject.upload.entity.MediaFile;
import com.itproject.upload.service.FileTypeValidationService;
import com.itproject.upload.service.UploadService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Controller for handling file upload operations
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/upload")
@CrossOrigin(origins = "*", maxAge = 3600)
public class UploadController {
    
    @Autowired
    private UploadService uploadService;
    
    @Autowired
    private FileTypeValidationService fileTypeValidationService;
    
    /**
     * Upload file chunk
     */
    @PostMapping("/chunk")
    public ResponseEntity<UploadResponse> uploadChunk(
            @RequestParam("fileMd5") String fileMd5,
            @RequestParam("fileName") String fileName,
            @RequestParam("chunkIndex") Integer chunkIndex,
            @RequestParam("totalChunks") Integer totalChunks,
            @RequestParam("totalSize") Long totalSize,
            @RequestParam(value = "chunkMd5", required = false) String chunkMd5,
            @RequestParam(value = "uploadedBy", required = false, defaultValue = "anonymous") String uploadedBy,
            @RequestParam("file") MultipartFile file) {
        
        try {
            log.info("Received chunk upload request: file={}, chunk={}/{}, size={}", 
                    fileName, chunkIndex + 1, totalChunks, file.getSize());
            
            // Validate file type on first chunk
            if (chunkIndex == 0) {
                FileTypeValidationService.FileTypeValidationResult validation = 
                    fileTypeValidationService.validateFileType(fileName, file);
                
                if (!validation.isValid()) {
                    log.warn("File type validation failed for: {}", fileName);
                    UploadResponse errorResponse = UploadResponse.error(validation.getMessage());
                    return ResponseEntity.badRequest().body(errorResponse);
                }
            }
            
            // Create upload request
            ChunkUploadRequest request = new ChunkUploadRequest();
            request.setFileMd5(fileMd5);
            request.setFileName(fileName);
            request.setChunkIndex(chunkIndex);
            request.setTotalChunks(totalChunks);
            request.setTotalSize(totalSize);
            request.setChunkSize(file.getSize());
            request.setChunkMd5(chunkMd5);
            request.setUploadedBy(uploadedBy);
            
            // Process upload
            UploadResponse response = uploadService.uploadChunk(request, file);
            
            if (response.isSuccess()) {
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
            }
            
        } catch (Exception e) {
            log.error("Error in chunk upload", e);
            UploadResponse errorResponse = UploadResponse.error("Upload failed: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }
    
    /**
     * Get upload progress
     */
    @GetMapping("/progress/{fileMd5}")
    public ResponseEntity<UploadResponse> getUploadProgress(@PathVariable String fileMd5) {
        try {
            UploadResponse response = uploadService.getUploadProgress(fileMd5);
            
            if (response.isSuccess()) {
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.notFound().build();
            }
            
        } catch (Exception e) {
            log.error("Error getting upload progress", e);
            UploadResponse errorResponse = UploadResponse.error("Failed to get progress: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }
    
    /**
     * Check if file already exists
     */
    @GetMapping("/check/{fileMd5}")
    public ResponseEntity<Map<String, Object>> checkFileExists(@PathVariable String fileMd5) {
        try {
            UploadResponse response = uploadService.getUploadProgress(fileMd5);
            
            Map<String, Object> result = new HashMap<>();
            result.put("exists", response.isSuccess());
            result.put("uploadStatus", response.getUploadStatus());
            result.put("uploadProgress", response.getUploadProgress());
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            log.error("Error checking file existence", e);
            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("exists", false);
            errorResult.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResult);
        }
    }
    
    /**
     * Get supported file types
     */
    @GetMapping("/supported-types")
    public ResponseEntity<Map<String, Object>> getSupportedFileTypes() {
        try {
            Map<String, Object> types = new HashMap<>();
            types.put("images", fileTypeValidationService.getSupportedImageExtensions());
            types.put("videos", fileTypeValidationService.getSupportedVideoExtensions());
            types.put("all", fileTypeValidationService.getAllSupportedExtensions());
            
            return ResponseEntity.ok(types);
            
        } catch (Exception e) {
            log.error("Error getting supported file types", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * Validate file type before upload
     */
    @PostMapping("/validate")
    public ResponseEntity<Map<String, Object>> validateFile(
            @RequestParam("fileName") String fileName,
            @RequestParam(value = "file", required = false) MultipartFile file) {
        
        try {
            FileTypeValidationService.FileTypeValidationResult result;
            
            if (file != null && !file.isEmpty()) {
                result = fileTypeValidationService.validateFileType(fileName, file);
            } else {
                result = fileTypeValidationService.validateFileType(fileName);
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("valid", result.isValid());
            response.put("fileType", result.getFileType());
            response.put("message", result.getMessage());
            response.put("detectedMimeType", result.getDetectedMimeType());
            
            if (result.isValid()) {
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.badRequest().body(response);
            }
            
        } catch (Exception e) {
            log.error("Error validating file", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("valid", false);
            errorResponse.put("message", "Validation error: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }
    
    /**
     * Get files list with pagination
     */
    @GetMapping("/files")
    public ResponseEntity<Map<String, Object>> getFiles(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "type", required = false) String type) {
        
        try {
            log.info("Getting files list: page={}, size={}, status={}, type={}", page, size, status, type);
            
            Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
            Page<MediaFile> filesPage = uploadService.getFilesList(pageable, status, type);
            
            // Convert to response format
            List<Map<String, Object>> fileList = filesPage.getContent().stream()
                .map(this::convertToFileResponse)
                .toList();
            
            Map<String, Object> response = new HashMap<>();
            response.put("files", fileList);
            response.put("total", filesPage.getTotalElements());
            response.put("current", page + 1); // Frontend uses 1-based pagination
            response.put("pageSize", size);
            response.put("totalPages", filesPage.getTotalPages());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error getting files list", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "Failed to get files: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }
    
    /**
     * Delete file by ID
     */
    @DeleteMapping("/files/{fileId}")
    public ResponseEntity<Map<String, Object>> deleteFile(@PathVariable String fileId) {
        try {
            log.info("Deleting file: {}", fileId);
            
            boolean deleted = uploadService.deleteFile(fileId);
            
            Map<String, Object> response = new HashMap<>();
            if (deleted) {
                response.put("success", true);
                response.put("message", "File deleted successfully");
                return ResponseEntity.ok(response);
            } else {
                response.put("success", false);
                response.put("message", "File not found or already deleted");
                return ResponseEntity.notFound().build();
            }
            
        } catch (Exception e) {
            log.error("Error deleting file: {}", fileId, e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Failed to delete file: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }
    
    /**
     * Convert MediaFile entity to response format
     */
    private Map<String, Object> convertToFileResponse(MediaFile mediaFile) {
        Map<String, Object> response = new HashMap<>();
        response.put("id", mediaFile.getId().toString());
        response.put("filename", mediaFile.getFileName());
        response.put("originalName", mediaFile.getOriginalFileName());
        response.put("fileType", mediaFile.getFileType());
        response.put("fileSize", mediaFile.getFileSize());
        response.put("filePath", mediaFile.getFilePath());
        response.put("uploadTime", mediaFile.getCreatedAt().toString());
        response.put("status", mediaFile.getUploadStatus().name());
        response.put("chunkTotal", mediaFile.getTotalChunks());
        response.put("chunkUploaded", mediaFile.getUploadedChunks());
        response.put("md5Hash", mediaFile.getFileMd5());
        response.put("uploadedBy", mediaFile.getUploadedBy());
        return response;
    }
    
    /**
     * Preview file content
     */
    @GetMapping("/files/{fileId}/preview")
    public ResponseEntity<InputStreamResource> previewFile(@PathVariable String fileId) {
        try {
            log.info("Previewing file: {}", fileId);
            
            // Get file stream from service
            InputStream fileStream = uploadService.getFileStream(fileId);
            String contentType = uploadService.getFileContentType(fileId);
            String fileName = uploadService.getFileName(fileId);
            
            // Set appropriate headers
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType(contentType));
            headers.set(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + fileName + "\"");
            
            return ResponseEntity.ok()
                    .headers(headers)
                    .body(new InputStreamResource(fileStream));
                    
        } catch (Exception e) {
            log.error("Error previewing file: {}", fileId, e);
            return ResponseEntity.notFound().build();
        }
    }
}
