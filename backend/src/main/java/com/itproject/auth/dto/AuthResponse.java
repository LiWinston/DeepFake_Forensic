package com.itproject.auth.dto;

import com.itproject.auth.entity.User;
import lombok.Data;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

/**
 * Authentication response DTO
 */
@Data
@AllArgsConstructor
public class AuthResponse {
    
    private String token;
    private String refreshToken;
    private UserInfo user;
      @Data
    @AllArgsConstructor
    public static class UserInfo {
        private Long id;
        private String username;
        private String email;
        private String firstName;
        private String lastName;
        private User.UserRole role;
        private boolean emailVerified;
        private LocalDateTime emailVerifiedAt;
        private LocalDateTime lastLoginAt;
        
        public static UserInfo fromUser(User user) {
            return new UserInfo(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getRole(),
                user.isEmailVerified(),
                user.getEmailVerifiedAt(),
                user.getLastLoginAt()
            );
        }
    }
}
