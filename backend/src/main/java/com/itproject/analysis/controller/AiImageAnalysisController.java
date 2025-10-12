package com.itproject.analysis.controller;

import com.itproject.analysis.entity.AnalysisTask;
import com.itproject.auth.entity.User;
import com.itproject.common.dto.Result;
import com.itproject.project.repository.AnalysisTaskRepository;
import com.itproject.upload.repository.MediaFileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Image AI (Deepfake detection) result query APIs.
 * Provide lightweight endpoints to fetch the latest AI result by fileMd5 instead of project-wide listing.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
public class AiImageAnalysisController {

    private final AnalysisTaskRepository analysisTaskRepository;
    private final MediaFileRepository mediaFileRepository;

    /**
     * Get latest image AI (Deepfake detection) result by fileMd5 for current user.
     * Returns 200 with Result.success(null) if not found.
     */
    @GetMapping("/image/result/{fileMd5}")
    public ResponseEntity<Result<AnalysisTask>> getLatestImageAiResult(
            @PathVariable String fileMd5,
            @AuthenticationPrincipal User user
    ) {
        try {
            // Optional: verify the file belongs to the current user for stricter auth
            mediaFileRepository.findByFileMd5(fileMd5).ifPresent(media -> {
                if (!media.getUser().getId().equals(user.getId())) {
                    throw new RuntimeException("No permission to access this file");
                }
            });

            AnalysisTask task = analysisTaskRepository
                    .findTopByMediaFile_FileMd5AndUserAndAnalysisTypeOrderByCompletedAtDesc(
                            fileMd5,
                            user,
                            AnalysisTask.AnalysisType.DEEPFAKE_DETECTION
                    );
            return ResponseEntity.ok(Result.success(task));
        } catch (Exception e) {
            log.error("Failed to get AI image result for {}: {}", fileMd5, e.getMessage());
            return ResponseEntity.ok(Result.error("Failed to get AI image result: " + e.getMessage()));
        }
    }
}
