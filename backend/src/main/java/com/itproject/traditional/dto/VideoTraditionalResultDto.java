package com.itproject.traditional.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class VideoTraditionalResultDto {
    private Long id;
    private String fileMd5;
    private String method; // NOISE/FLOW/FREQ/TEMPORAL/COPYMOVE
    private Map<String, String> artifacts; // name -> url
    private Object result; // metrics map
    private Boolean success;
    private String errorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
