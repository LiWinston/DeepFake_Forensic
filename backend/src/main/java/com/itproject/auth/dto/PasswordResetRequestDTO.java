package com.itproject.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * DTO for password reset request
 */
@Data
public class PasswordResetRequestDTO {
    
    @NotBlank(message = "Username is required")
    private String username;
}
