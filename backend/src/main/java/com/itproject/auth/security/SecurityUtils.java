package com.itproject.auth.security;

import com.itproject.auth.entity.User;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Utility class for getting current authenticated user
 */
@Component
public class SecurityUtils {
    
    /**
     * Get current authenticated user
     * @return current user or null if not authenticated
     */
    public static User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof User) {
            return (User) authentication.getPrincipal();
        }
        return null;
    }
    
    /**
     * Get current user ID
     * @return current user ID or null if not authenticated
     */
    public static Long getCurrentUserId() {
        User user = getCurrentUser();
        return user != null ? user.getId() : null;
    }
    
    /**
     * Get current username
     * @return current username or null if not authenticated
     */
    public static String getCurrentUsername() {
        User user = getCurrentUser();
        return user != null ? user.getUsername() : null;
    }
    
    /**
     * Check if current user is authenticated
     * @return true if authenticated, false otherwise
     */
    public static boolean isAuthenticated() {
        return getCurrentUser() != null;
    }
}
