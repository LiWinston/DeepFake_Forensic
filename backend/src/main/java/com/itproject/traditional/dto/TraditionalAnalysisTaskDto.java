package com.itproject.traditional.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO for traditional analysis task messages
 */
@Data
@NoArgsConstructor
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
     * Specific traditional methods to execute (ELA, CFA, COPY_MOVE, LIGHTING). Null/empty means run all.
     */
    private List<String> methods;
    
    /**
     * Constructor for backward compatibility (force defaults to false)
     */
    public TraditionalAnalysisTaskDto(String fileMd5) {
        this.fileMd5 = fileMd5;
        this.force = false;
    }

    public TraditionalAnalysisTaskDto(String fileMd5, boolean force) {
        this.fileMd5 = fileMd5;
        this.force = force;
    }

    public TraditionalAnalysisTaskDto(String fileMd5, boolean force, List<String> methods) {
        this.fileMd5 = fileMd5;
        this.force = force;
        this.methods = methods;
    }
}
