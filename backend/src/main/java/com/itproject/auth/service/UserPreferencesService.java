package com.itproject.auth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * User preferences service for managing user settings in Redis
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserPreferencesService {
    
    private final RedisTemplate<String, Object> redisTemplate;
    
    private static final String USER_PREFERENCES_PREFIX = "user_preferences:";
    private static final String EMAIL_NOTIFICATIONS_KEY = "email_notifications";
    private static final long CACHE_EXPIRATION_DAYS = 365; // 1 year
    
    /**
     * Get user's email notification preference
     * @param userId User ID
     * @return true if notifications enabled (default), false if disabled
     */
    public boolean isEmailNotificationsEnabled(Long userId) {
        try {
            String key = USER_PREFERENCES_PREFIX + userId + ":" + EMAIL_NOTIFICATIONS_KEY;
            Boolean enabled = (Boolean) redisTemplate.opsForValue().get(key);
            
            // Default to true (enabled) if not set
            return enabled == null || enabled;
        } catch (Exception e) {
            log.error("Error retrieving email notification preference for user {}", userId, e);
            // Default to enabled on error
            return true;
        }
    }
    
    /**
     * Set user's email notification preference
     * @param userId User ID
     * @param enabled true to enable notifications, false to disable
     */
    public void setEmailNotificationsEnabled(Long userId, boolean enabled) {
        try {
            String key = USER_PREFERENCES_PREFIX + userId + ":" + EMAIL_NOTIFICATIONS_KEY;
            redisTemplate.opsForValue().set(key, enabled, CACHE_EXPIRATION_DAYS, TimeUnit.DAYS);
            
            log.info("Updated email notification preference for user {}: {}", userId, enabled);
        } catch (Exception e) {
            log.error("Error setting email notification preference for user {}", userId, e);
            throw new RuntimeException("Failed to update email notification preference", e);
        }
    }
    
    /**
     * Get user's email notification preference by username
     * @param username Username
     * @return true if notifications enabled (default), false if disabled
     */
    public boolean isEmailNotificationsEnabledByUsername(String username) {
        try {
            String key = USER_PREFERENCES_PREFIX + "username:" + username + ":" + EMAIL_NOTIFICATIONS_KEY;
            Boolean enabled = (Boolean) redisTemplate.opsForValue().get(key);
            
            // Default to true (enabled) if not set
            return enabled == null || enabled;
        } catch (Exception e) {
            log.error("Error retrieving email notification preference for username {}", username, e);
            // Default to enabled on error
            return true;
        }
    }
    
    /**
     * Set user's email notification preference by username (for caching)
     * @param username Username
     * @param enabled true to enable notifications, false to disable
     */
    public void setEmailNotificationsEnabledByUsername(String username, boolean enabled) {
        try {
            String key = USER_PREFERENCES_PREFIX + "username:" + username + ":" + EMAIL_NOTIFICATIONS_KEY;
            redisTemplate.opsForValue().set(key, enabled, CACHE_EXPIRATION_DAYS, TimeUnit.DAYS);
            
            log.debug("Cached email notification preference for username {}: {}", username, enabled);
        } catch (Exception e) {
            log.error("Error caching email notification preference for username {}", username, e);
            // Don't throw exception for caching failures
        }
    }
    
    /**
     * Remove user preferences from cache (e.g., when user is deleted)
     * @param userId User ID
     */
    public void clearUserPreferences(Long userId) {
        try {
            String pattern = USER_PREFERENCES_PREFIX + userId + ":*";
            redisTemplate.delete(redisTemplate.keys(pattern));
            
            log.info("Cleared preferences cache for user {}", userId);
        } catch (Exception e) {
            log.error("Error clearing preferences cache for user {}", userId, e);
        }
    }
    
    /**
     * Remove user preferences from cache by username
     * @param username Username
     */
    public void clearUserPreferencesByUsername(String username) {
        try {
            String pattern = USER_PREFERENCES_PREFIX + "username:" + username + ":*";
            redisTemplate.delete(redisTemplate.keys(pattern));
            
            log.debug("Cleared preferences cache for username {}", username);
        } catch (Exception e) {
            log.error("Error clearing preferences cache for username {}", username, e);
        }
    }
}
