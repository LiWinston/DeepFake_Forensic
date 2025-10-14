package com.itproject.traditional.repository;

import com.itproject.traditional.entity.VideoTraditionalAnalysisResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface VideoTraditionalAnalysisResultRepository extends JpaRepository<VideoTraditionalAnalysisResult, Long> {
    List<VideoTraditionalAnalysisResult> findByFileMd5(String fileMd5);

    /**
     * Delete all video traditional analysis results by file MD5
     */
    void deleteByFileMd5(String fileMd5);
}
