package com.itproject.analysis.controller;

import com.itproject.common.dto.Result;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Controller to orchestrate analysis selection and progress polling.
 * Provides:
 * - User-selected analysis start endpoint (post-upload)
 * - Progress polling that reads from Redis (updated by Java/Python workers)
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/analysis")
public class AnalysisOrchestrationController {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private com.itproject.analysis.service.AnalysisOrchestrationService orchestrationService;

    /**
     * Start analysis with user-selected methods/models.
     */
    @PostMapping("/start")
    @PreAuthorize("hasRole('USER')")
    public Result<Map<String, Object>> startAnalysis(@RequestBody StartAnalysisRequest req) {
        Map<String, Object> data = orchestrationService.start(req);
        return Result.success(data);
    }

    /**
     * Poll progress by taskId. Reads from Redis where workers write progress.
     */
    @GetMapping("/progress/{taskId}")
    public Result<Map<String, Object>> getProgress(@PathVariable String taskId) {
        String key = "analysis:progress:" + taskId;
        Map<Object, Object> map = stringRedisTemplate.opsForHash().entries(key);
        Map<String, Object> ret = new HashMap<>();
        for (Map.Entry<Object, Object> e : map.entrySet()) {
            ret.put(String.valueOf(e.getKey()), e.getValue());
        }
        return Result.success(ret);
    }

    @Data
    public static class StartAnalysisRequest {
        private String taskId; // optional
        private String fileMd5;
        private Long projectId;
        private boolean runMetadata = true;
        private boolean runTraditionalImage = false; // ELA/CFA/CopyMove/Lighting/Noise
        private boolean runImageAI = false;
        private boolean runVideoTraditional = false;
        private boolean runVideoAI = false;
        private String selectedImageModel; // for image AI
        private java.util.List<String> selectedTraditionalMethods; // if subset selection is supported
    }
}
