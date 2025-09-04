package com.itproject.upload.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.itproject.auth.entity.User;
import com.itproject.project.entity.Project;
import com.itproject.upload.entity.MediaFile;
import com.itproject.upload.mapper.MediaFileMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * File search service - uses MyBatis-Plus for dynamic queries
 */
@Slf4j
@Service
public class FileSearchService {

    @Autowired
    private MediaFileMapper mediaFileMapper;

    /**
     * Dynamic file search
     * @param page page number (starts from 1)
     * @param size page size
     * @param status status filter
     * @param type type filter
     * @param search search keyword
     * @param user current user
     * @param project project (optional)
     * @return paginated results
     */
    public Page<MediaFile> searchFiles(int page, int size, String status, String type, 
                                     String search, User user, Project project) {
        
        log.debug("Searching files: page={}, size={}, status={}, type={}, search={}, user={}, project={}", 
                page, size, status, type, search, user.getUsername(), 
                project != null ? project.getName() : "all");
        
        // Create pagination object (MyBatis-Plus starts from 1)
        Page<MediaFile> pageObj = new Page<>(page, size);
        
        // Build dynamic query conditions
        LambdaQueryWrapper<MediaFile> queryWrapper = new LambdaQueryWrapper<>();
        
        // Basic condition: can only view current user's files
        queryWrapper.eq(MediaFile::getUserId, user.getId());
        
        // Project filter
        if (project != null) {
            queryWrapper.eq(MediaFile::getProjectId, project.getId());
        }
        
        // Status filter
        if (StringUtils.hasText(status)) {
            try {
                MediaFile.UploadStatus uploadStatus = MediaFile.UploadStatus.valueOf(status.toUpperCase());
                queryWrapper.eq(MediaFile::getUploadStatus, uploadStatus);
            } catch (IllegalArgumentException e) {
                log.warn("Invalid upload status: {}", status);
            }
        }
        
        // Type filter
        if (StringUtils.hasText(type)) {
            try {
                MediaFile.MediaType mediaType = MediaFile.MediaType.valueOf(type.toUpperCase());
                queryWrapper.eq(MediaFile::getMediaType, mediaType);
            } catch (IllegalArgumentException e) {
                log.warn("Invalid media type: {}", type);
            }
        }
        
        // Filename search (fuzzy match, case insensitive)
        if (StringUtils.hasText(search)) {
            String searchPattern = search.trim();
            queryWrapper.and(wrapper -> wrapper
                .like(MediaFile::getFileName, searchPattern)
                .or()
                .like(MediaFile::getOriginalFileName, searchPattern)
            );
        }
        
        // Order by creation time descending
        queryWrapper.orderByDesc(MediaFile::getCreatedAt);
        
        // Execute query
        return mediaFileMapper.selectPage(pageObj, queryWrapper);
    }
}
