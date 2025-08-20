package com.itproject.auth.service;

import com.itproject.auth.entity.EmailVerificationToken;
import com.itproject.auth.entity.PasswordResetToken;
import com.itproject.auth.entity.User;
import com.itproject.auth.repository.EmailVerificationTokenRepository;
import com.itproject.auth.repository.PasswordResetTokenRepository;
import com.itproject.auth.repository.UserRepository;
import com.itproject.common.config.MailProperties;
import com.itproject.common.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * User account management service
 */
@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class UserAccountService {
    
    private final UserRepository userRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final EmailVerificationTokenRepository emailVerificationTokenRepository;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;
    private final MailProperties mailProperties;
    
    @Value("${server.port:8082}")
    private int serverPort;
    
    @Value("${app.frontend.url:http://localhost:3000}")
    private String frontendUrl;
    
    /**
     * Send password reset email
     */
    public boolean sendPasswordResetEmail(String email) {
        try {
            Optional<User> userOpt = userRepository.findByEmail(email);
            if (userOpt.isEmpty()) {
                log.warn("Password reset requested for non-existent email: {}", email);
                // For security reasons, we don't reveal if email exists
                return true;
            }
            
            User user = userOpt.get();
            
            // Delete any existing tokens for this user
            passwordResetTokenRepository.deleteAllByUser(user);
            
            // Generate new token
            String token = UUID.randomUUID().toString();
            LocalDateTime expiryDate = LocalDateTime.now()
                    .plusSeconds(mailProperties.getResetPasswordExpiration() / 1000);
            
            PasswordResetToken resetToken = new PasswordResetToken();
            resetToken.setToken(token);
            resetToken.setUser(user);
            resetToken.setExpiryDate(expiryDate);
            
            passwordResetTokenRepository.save(resetToken);
            
            // Send email
            String resetUrl = frontendUrl + "/reset-password?token=" + token;
            emailService.sendPasswordResetEmail(user.getEmail(), user.getUsername(), resetUrl);
            
            log.info("Password reset email sent to user: {}", user.getUsername());
            return true;
            
        } catch (Exception e) {
            log.error("Failed to send password reset email for: {}", email, e);
            return false;
        }
    }
    
    /**
     * Send password reset email by username
     */
    public boolean sendPasswordResetEmailByUsername(String username) {
        try {
            Optional<User> userOpt = userRepository.findByUsername(username);
            if (userOpt.isEmpty()) {
                log.warn("Password reset requested for non-existent username: {}", username);
                // For security reasons, we don't reveal if username exists
                return true;
            }
            
            User user = userOpt.get();
            
            // Delete any existing tokens for this user
            passwordResetTokenRepository.deleteAllByUser(user);
            
            // Generate new token
            String token = UUID.randomUUID().toString();
            LocalDateTime expiryDate = LocalDateTime.now()
                    .plusSeconds(mailProperties.getResetPasswordExpiration() / 1000);
            
            PasswordResetToken resetToken = new PasswordResetToken();
            resetToken.setToken(token);
            resetToken.setUser(user);
            resetToken.setExpiryDate(expiryDate);
            
            passwordResetTokenRepository.save(resetToken);
            
            // Send email
            String resetUrl = frontendUrl + "/reset-password?token=" + token;
            emailService.sendPasswordResetEmail(user.getEmail(), user.getUsername(), resetUrl);
            
            log.info("Password reset email sent to user: {}", user.getUsername());
            return true;
            
        } catch (Exception e) {
            log.error("Failed to send password reset email for username: {}", username, e);
            return false;
        }
    }
    
    /**
     * Reset password using token
     */
    public boolean resetPassword(String token, String newPassword) {
        try {
            Optional<PasswordResetToken> tokenOpt = passwordResetTokenRepository.findByTokenAndUsedFalse(token);
            if (tokenOpt.isEmpty()) {
                log.warn("Invalid or used password reset token: {}", token);
                return false;
            }
            
            PasswordResetToken resetToken = tokenOpt.get();
            if (!resetToken.isValid()) {
                log.warn("Expired password reset token: {}", token);
                return false;
            }
            
            User user = resetToken.getUser();
            user.setPassword(passwordEncoder.encode(newPassword));
            userRepository.save(user);
            
            // Mark token as used
            resetToken.setUsed(true);
            passwordResetTokenRepository.save(resetToken);
            
            log.info("Password reset successful for user: {}", user.getUsername());
            return true;
            
        } catch (Exception e) {
            log.error("Failed to reset password with token: {}", token, e);
            return false;
        }
    }
    
    /**
     * Send email verification
     */
    public boolean sendEmailVerification(String email) {
        try {
            Optional<User> userOpt = userRepository.findByEmail(email);
            if (userOpt.isEmpty()) {
                log.warn("Email verification requested for non-existent email: {}", email);
                return false;
            }
            
            User user = userOpt.get();
            if (user.isEmailVerified()) {
                log.info("Email already verified for user: {}", user.getUsername());
                return true;
            }
            
            // Delete any existing tokens for this user
            emailVerificationTokenRepository.deleteAllByUser(user);
            
            // Generate new token
            String token = UUID.randomUUID().toString();
            LocalDateTime expiryDate = LocalDateTime.now()
                    .plusSeconds(mailProperties.getVerificationExpiration() / 1000);
            
            EmailVerificationToken verificationToken = new EmailVerificationToken();
            verificationToken.setToken(token);
            verificationToken.setUser(user);
            verificationToken.setExpiryDate(expiryDate);
            
            emailVerificationTokenRepository.save(verificationToken);
            
            // Send email
            String verificationUrl = frontendUrl + "/verify-email?token=" + token;
            emailService.sendEmailVerificationEmail(user.getEmail(), user.getUsername(), verificationUrl);
            
            log.info("Email verification sent to user: {}", user.getUsername());
            return true;
            
        } catch (Exception e) {
            log.error("Failed to send email verification for: {}", email, e);
            return false;
        }
    }
      /**
     * Verify email using token
     */
    public boolean verifyEmail(String token) {
        try {
            Optional<EmailVerificationToken> tokenOpt = emailVerificationTokenRepository.findByTokenAndUsedFalse(token);
            if (tokenOpt.isEmpty()) {
                log.warn("Invalid or used email verification token: {}", token);
                return false;
            }
            
            EmailVerificationToken verificationToken = tokenOpt.get();
            if (!verificationToken.isValid()) {
                log.warn("Expired email verification token: {}", token);
                return false;
            }
            
            User user = verificationToken.getUser();
            user.setEmailVerified(true);
            user.setEmailVerifiedAt(LocalDateTime.now());
            userRepository.save(user);
            
            // Mark token as used
            verificationToken.setUsed(true);
            emailVerificationTokenRepository.save(verificationToken);
            
            log.info("Email verification successful for user: {}", user.getUsername());
            return true;
            
        } catch (Exception e) {
            log.error("Failed to verify email with token: {}", token, e);
            return false;
        }
    }
    
    /**
     * Validate password reset token
     */
    public boolean validatePasswordResetToken(String token) {
        try {
            Optional<PasswordResetToken> tokenOpt = passwordResetTokenRepository.findByTokenAndUsedFalse(token);
            if (tokenOpt.isEmpty()) {
                log.warn("Invalid or used password reset token: {}", token);
                return false;
            }
            
            PasswordResetToken resetToken = tokenOpt.get();
            return resetToken.isValid();
            
        } catch (Exception e) {
            log.error("Failed to validate password reset token: {}", token, e);
            return false;
        }
    }
    
    /**
     * Change password for authenticated user
     */
    public boolean changePassword(Long userId, String currentPassword, String newPassword) {
        try {
            Optional<User> userOpt = userRepository.findById(userId);
            if (userOpt.isEmpty()) {
                return false;
            }
            
            User user = userOpt.get();
            if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
                log.warn("Invalid current password for user: {}", user.getUsername());
                return false;
            }
            
            user.setPassword(passwordEncoder.encode(newPassword));
            userRepository.save(user);
            
            log.info("Password changed successfully for user: {}", user.getUsername());
            return true;
            
        } catch (Exception e) {
            log.error("Failed to change password for user ID: {}", userId, e);
            return false;
        }
    }
    
    /**
     * Update user profile
     */
    public boolean updateProfile(Long userId, String firstName, String lastName) {
        try {
            Optional<User> userOpt = userRepository.findById(userId);
            if (userOpt.isEmpty()) {
                return false;
            }
            
            User user = userOpt.get();
            user.setFirstName(firstName);
            user.setLastName(lastName);
            userRepository.save(user);
            
            log.info("Profile updated successfully for user: {}", user.getUsername());
            return true;
            
        } catch (Exception e) {
            log.error("Failed to update profile for user ID: {}", userId, e);
            return false;
        }
    }
    
    /**
     * Clean up expired tokens
     */
    public void cleanupExpiredTokens() {
        LocalDateTime now = LocalDateTime.now();
        passwordResetTokenRepository.deleteExpiredTokens(now);
        emailVerificationTokenRepository.deleteExpiredTokens(now);
        log.info("Cleaned up expired tokens");
    }
}
