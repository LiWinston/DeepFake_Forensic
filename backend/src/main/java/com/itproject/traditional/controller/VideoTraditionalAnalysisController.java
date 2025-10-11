package com.itproject.traditional.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.itproject.common.dto.Result;
import com.itproject.traditional.dto.VideoTraditionalResultDto;
import com.itproject.traditional.entity.VideoTraditionalAnalysisResult;
import com.itproject.traditional.repository.VideoTraditionalAnalysisResultRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/v1/video-traditional-analysis")
@RequiredArgsConstructor
public class VideoTraditionalAnalysisController {

    private final VideoTraditionalAnalysisResultRepository repo;
    private final ObjectMapper mapper;

    /**
     * Get video traditional analysis results by file MD5
     */
    @GetMapping("/result/{fileMd5}")
    public ResponseEntity<Result<List<VideoTraditionalResultDto>>> getVideoTraditionalResults(@PathVariable String fileMd5) {
        try {
            List<VideoTraditionalAnalysisResult> rows = repo.findByFileMd5(fileMd5);
            List<VideoTraditionalResultDto> dtos = rows.stream().map(r -> {
                Map<String,String> artifacts = parseArtifacts(r.getArtifactsJson());
                Object result = parseResult(r.getResultJson());
                return new VideoTraditionalResultDto(
                        r.getId(),
                        r.getFileMd5(),
                        r.getMethod(),
                        artifacts,
                        result,
                        r.getSuccess(),
                        r.getErrorMessage(),
                        r.getCreatedAt(),
                        r.getUpdatedAt()
                );
            }).collect(Collectors.toList());
            return ResponseEntity.ok(Result.success(dtos));
        } catch (Exception e) {
            log.error("Error retrieving video traditional analysis results for {}", fileMd5, e);
            return ResponseEntity.ok(Result.error("Failed to retrieve video traditional analysis results: " + e.getMessage()));
        }
    }

    private Map<String,String> parseArtifacts(String json) {
        if (json == null || json.isEmpty()) return Collections.emptyMap();
        try {
            return mapper.readValue(json, new TypeReference<Map<String,String>>(){});
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }

    private Object parseResult(String json) {
        if (json == null || json.isEmpty()) return Collections.emptyMap();
        try {
            return mapper.readValue(json, new TypeReference<Map<String,Object>>(){});
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }
}
