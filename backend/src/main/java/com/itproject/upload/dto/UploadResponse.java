package com.itproject.upload.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.List;

/**
 * DTO for upload response
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UploadResponse {
    
    private boolean success;
    private String message;
    private String fileMd5;
    private String fileName;
    private Integer uploadedChunks;
    private Integer totalChunks;
    private Double uploadProgress;
    private List<Integer> missingChunks;
    private String uploadStatus;
    private Long fileId;
    
    public static UploadResponse success(String message) {
        UploadResponse response = new UploadResponse();
        response.success = true;
        response.message = message;
        return response;
    }
    
    public static UploadResponse error(String message) {
        UploadResponse response = new UploadResponse();
        response.success = false;
        response.message = message;
        return response;
    }
}
