package com.itproject.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * Cache configuration using Caffeine for short-term request deduplication
 */
@Configuration
@EnableCaching
public class CacheConfig {

    /**
     * Cache manager for request deduplication
     * Short TTL to handle duplicate requests within seconds
     */
    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        cacheManager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(1000) // Maximum 1000 cached entries
                .expireAfterWrite(5, TimeUnit.SECONDS) // Cache expires after 5 seconds
                .recordStats()); // Enable statistics
        
        return cacheManager;
    }
}
