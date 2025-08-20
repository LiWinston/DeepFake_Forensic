package com.itproject.common.scheduler;

import com.itproject.auth.service.UserAccountService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled tasks for system maintenance
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SystemScheduler {
    
    private final UserAccountService userAccountService;
    
    /**
     * Clean up expired tokens every hour
     */
    @Scheduled(fixedRate = 3600000) // 1 hour
    public void cleanupExpiredTokens() {
        log.info("Starting cleanup of expired tokens");
        userAccountService.cleanupExpiredTokens();
        log.info("Completed cleanup of expired tokens");
    }
}
