package com.itproject.auth.controller;

import com.itproject.auth.dto.*;
import com.itproject.auth.entity.User;
import com.itproject.auth.security.SecurityUtils;
import com.itproject.auth.service.UserAccountService;
import com.itproject.auth.service.UserPreferencesService;
import com.itproject.common.dto.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * User account management controller
 */
@RestController
@RequestMapping("/api/v1/account")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
@Slf4j
public class UserAccountController {
    
    private final UserAccountService userAccountService;
    private final UserPreferencesService userPreferencesService;
      /**
     * Request password reset
     */
    @PostMapping("/password/reset-request")
    public Result<Void> requestPasswordReset(@Valid @RequestBody PasswordResetRequestDTO request) {
        try {
            boolean success = userAccountService.sendPasswordResetEmailByUsername(request.getUsername());
            if (success) {
                return Result.success(null, "Password reset email sent successfully");
            } else {
                return Result.error("Failed to send password reset email");
            }
        } catch (Exception e) {
            log.error("Error requesting password reset", e);
            return Result.error("Internal server error");
        }
    }
      /**
     * Reset password with token
     */
    @PostMapping("/password/reset-confirm")
    public Result<Void> confirmPasswordReset(@Valid @RequestBody PasswordResetConfirmDTO request) {
        try {
            if (!request.getPassword().equals(request.getConfirmPassword())) {
                return Result.error("Passwords do not match");
            }
            
            boolean success = userAccountService.resetPassword(request.getToken(), request.getPassword());
            if (success) {
                return Result.success(null, "Password reset successfully");
            } else {
                return Result.error("Invalid or expired reset token");
            }
        } catch (Exception e) {
            log.error("Error confirming password reset", e);
            return Result.error("Internal server error");
        }
    }
    
    /**
     * Validate password reset token
     */
    @GetMapping("/password/validate-token")
    public Result<Void> validateResetToken(@RequestParam String token) {
        try {
            boolean valid = userAccountService.validatePasswordResetToken(token);
            if (valid) {
                return Result.success(null, "Token is valid");
            } else {
                return Result.error("Invalid or expired reset token");
            }
        } catch (Exception e) {
            log.error("Error validating reset token", e);
            return Result.error("Internal server error");
        }
    }
    
    /**
     * Change password for authenticated user
     */
    @PostMapping("/password/change")
    public Result<Void> changePassword(@Valid @RequestBody PasswordChangeDTO request) {
        try {
            User currentUser = SecurityUtils.getCurrentUser();
            if (currentUser == null) {
                return Result.error("User not authenticated");
            }
            
            if (!request.getNewPassword().equals(request.getConfirmPassword())) {
                return Result.error("New passwords do not match");
            }
            
            boolean success = userAccountService.changePassword(
                    currentUser.getId(), 
                    request.getCurrentPassword(), 
                    request.getNewPassword()
            );
            
            if (success) {
                return Result.success(null, "Password changed successfully");
            } else {
                return Result.error("Current password is incorrect");
            }
        } catch (Exception e) {
            log.error("Error changing password", e);
            return Result.error("Internal server error");
        }
    }
    
    /**
     * Request email verification
     */
    @PostMapping("/email/verify-request")
    public Result<Void> requestEmailVerification(@Valid @RequestBody EmailVerificationRequestDTO request) {
        try {
            boolean success = userAccountService.sendEmailVerification(request.getEmail());
            if (success) {
                return Result.success(null, "Verification email sent successfully");
            } else {
                return Result.error("Failed to send verification email");
            }
        } catch (Exception e) {
            log.error("Error requesting email verification", e);
            return Result.error("Internal server error");
        }
    }
    
    /**
     * Verify email with token
     */
    @PostMapping("/email/verify")
    public Result<Void> verifyEmail(@RequestParam String token) {
        try {
            boolean success = userAccountService.verifyEmail(token);
            if (success) {
                return Result.success(null, "Email verified successfully");
            } else {
                return Result.error("Invalid or expired verification token");
            }
        } catch (Exception e) {
            log.error("Error verifying email", e);
            return Result.error("Internal server error");
        }
    }
    
    /**
     * Update user profile
     */
    @PutMapping("/profile")
    public Result<AuthResponse.UserInfo> updateProfile(@Valid @RequestBody UserProfileUpdateDTO request) {
        try {
            User currentUser = SecurityUtils.getCurrentUser();
            if (currentUser == null) {
                return Result.error("User not authenticated");
            }
            
            boolean success = userAccountService.updateProfile(
                    currentUser.getId(), 
                    request.getFirstName(), 
                    request.getLastName()
            );
            
            if (success) {
                // Return updated user info
                User updatedUser = SecurityUtils.getCurrentUser();
                return Result.success(
                        AuthResponse.UserInfo.fromUser(updatedUser), 
                        "Profile updated successfully"
                );
            } else {
                return Result.error("Failed to update profile");
            }
        } catch (Exception e) {
            log.error("Error updating profile", e);
            return Result.error("Internal server error");
        }
    }
    
    /**
     * Get current user profile
     */
    @GetMapping("/profile")
    public Result<AuthResponse.UserInfo> getProfile() {
        try {
            User currentUser = SecurityUtils.getCurrentUser();
            if (currentUser == null) {
                return Result.error("User not authenticated");
            }
            
            return Result.success(AuthResponse.UserInfo.fromUser(currentUser));
        } catch (Exception e) {
            log.error("Error getting profile", e);
            return Result.error("Internal server error");
        }
    }
    
    /**
     * Get user email notification preferences
     */
    @GetMapping("/preferences/email-notifications")
    public Result<EmailNotificationPreferenceDTO> getEmailNotificationPreference() {
        try {
            User currentUser = SecurityUtils.getCurrentUser();
            if (currentUser == null) {
                return Result.error("User not authenticated");
            }
            
            boolean enabled = userPreferencesService.isEmailNotificationsEnabled(currentUser.getId());
            return Result.success(new EmailNotificationPreferenceDTO(enabled));
        } catch (Exception e) {
            log.error("Error getting email notification preference", e);
            return Result.error("Internal server error");
        }
    }
    
    /**
     * Update user email notification preferences
     */
    @PutMapping("/preferences/email-notifications")
    public Result<Void> updateEmailNotificationPreference(@Valid @RequestBody EmailNotificationPreferenceDTO request) {
        try {
            User currentUser = SecurityUtils.getCurrentUser();
            if (currentUser == null) {
                return Result.error("User not authenticated");
            }
            
            userPreferencesService.setEmailNotificationsEnabled(currentUser.getId(), request.isEnabled());
            
            // Also cache by username for quick lookup during email sending
            userPreferencesService.setEmailNotificationsEnabledByUsername(currentUser.getUsername(), request.isEnabled());
            
            String message = request.isEnabled() ? "Email notifications enabled" : "Email notifications disabled";
            return Result.success(null, message);
        } catch (Exception e) {
            log.error("Error updating email notification preference", e);
            return Result.error("Failed to update email notification preference");
        }
    }
}
