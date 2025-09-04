package com.itproject.upload.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.itproject.upload.entity.MediaFile;
import org.apache.ibatis.annotations.Mapper;

/**
 * MyBatis-Plus Mapper for MediaFile
 * Used for dynamic queries with LambdaQueryWrapper
 */
@Mapper
public interface MediaFileMapper extends BaseMapper<MediaFile> {
    // BaseMapper provides basic CRUD operations
    // Additional custom methods can be defined here if needed
}
