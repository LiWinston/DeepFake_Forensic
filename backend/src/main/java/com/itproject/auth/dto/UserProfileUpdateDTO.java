package com.itproject.auth.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * DTO for user profile update
 */
@Data
public class UserProfileUpdateDTO {
    
    @Size(max = 50, message = "First name must not exceed 50 characters")
    private String firstName;
    
    @Size(max = 50, message = "Last name must not exceed 50 characters")
    private String lastName;
}
