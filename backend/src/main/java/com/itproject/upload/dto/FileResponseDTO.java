package com.itproject.upload.dto;

import lombok.Data;

/**
 * DTO for individual file response
 */
@Data
public class FileResponseDTO {
    private String id;
    private String filename;
    private String originalName;
    private String fileType;
    private long fileSize;
    private String filePath;
    private String uploadTime;
    private String status;
    private Integer chunkTotal;
    private Integer chunkUploaded;
    private String md5Hash;
    private String uploadedBy;
    private String projectName;
    private Long projectId;
}
