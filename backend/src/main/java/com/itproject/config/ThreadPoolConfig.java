package com.itproject.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Thread pool configuration for traditional analysis parallelization
 */
@Configuration
@EnableAsync
public class ThreadPoolConfig {

    /**
     * Thread pool for traditional analysis tasks
     * Optimized for CPU-intensive image processing tasks
     */
    @Bean("traditionalAnalysisExecutor")
    public Executor traditionalAnalysisExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        
        // Core pool size: Number of CPU cores for parallel analysis
        int cores = Runtime.getRuntime().availableProcessors();
        executor.setCorePoolSize(Math.max(4, cores)); // At least 4, or number of cores
        
        // Max pool size: Allow some extra threads for I/O operations
        executor.setMaxPoolSize(cores * 2);
        
        // Queue capacity: Moderate queue to handle bursts
        executor.setQueueCapacity(50);
        
        // Thread naming
        executor.setThreadNamePrefix("TraditionalAnalysis-");
        
        // Rejection policy: Caller runs to prevent task loss
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        
        // Allow core threads to timeout when idle
        executor.setAllowCoreThreadTimeOut(true);
        executor.setKeepAliveSeconds(60);
        
        executor.initialize();
        return executor;
    }

    /**
     * Separate thread pool for I/O intensive operations (MinIO uploads)
     */
    @Bean("ioTaskExecutor")
    public Executor ioTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        
        // I/O tasks can have more threads since they're mostly waiting
        int cores = Runtime.getRuntime().availableProcessors();
        executor.setCorePoolSize(cores);
        executor.setMaxPoolSize(cores * 3);
        executor.setQueueCapacity(100);
        
        executor.setThreadNamePrefix("IOTask-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setAllowCoreThreadTimeOut(true);
        executor.setKeepAliveSeconds(60);
        
        executor.initialize();
        return executor;
    }
}
