package com.itproject.upload.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * DTO for upload chunk request
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChunkUploadRequest {
    
    private String fileMd5;
    private String fileName;
    private Integer chunkIndex;
    private Integer totalChunks;
    private Long totalSize;
    private Long chunkSize;
    private String chunkMd5;
    private String uploadedBy;
}
