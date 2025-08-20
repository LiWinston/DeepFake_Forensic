package com.itproject.upload.dto;

import lombok.Data;
import java.util.List;

/**
 * DTO for file list response with pagination info
 */
@Data
public class FileListResponseDTO {
    private List<FileResponseDTO> files;
    private long total;
    private int current;
    private int pageSize;
    private int totalPages;

    public FileListResponseDTO() {}

    public FileListResponseDTO(List<FileResponseDTO> files, long total, int current, int pageSize, int totalPages) {
        this.files = files;
        this.total = total;
        this.current = current;
        this.pageSize = pageSize;
        this.totalPages = totalPages;
    }
}
