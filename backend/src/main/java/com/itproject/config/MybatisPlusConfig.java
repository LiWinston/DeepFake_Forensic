package com.itproject.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus Configuration
 * Compatible with JPA - use separate mapper package
 */
@Configuration
@MapperScan("com.itproject.upload.mapper")
public class MybatisPlusConfig {

    /**
     * Add pagination plugin
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        PaginationInnerInterceptor paginationInterceptor = new PaginationInnerInterceptor(DbType.MYSQL);
        // Set max page size to prevent large queries
        paginationInterceptor.setMaxLimit(1000L);
        interceptor.addInnerInterceptor(paginationInterceptor);
        return interceptor;
    }
    
    /**
     * Global configuration
     */
    @Bean
    public GlobalConfig globalConfig() {
        GlobalConfig globalConfig = new GlobalConfig();
        // Disable banner to reduce startup logs
        globalConfig.setBanner(false);
        return globalConfig;
    }
}
