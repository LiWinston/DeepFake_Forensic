package com.itproject.traditional.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for traditional analysis task messages
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TraditionalAnalysisTaskDto {
    /**
     * MD5 hash of the file to analyze
     */
    private String fileMd5;
    
    /**
     * Whether to force re-analysis if result already exists
     */
    private boolean force = false;
    
    /**
     * Constructor for backward compatibility (force defaults to false)
     */
    public TraditionalAnalysisTaskDto(String fileMd5) {
        this.fileMd5 = fileMd5;
        this.force = false;
    }
}
