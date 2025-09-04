package com.itproject.auth.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for email notification preference
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EmailNotificationPreferenceDTO {
    
    @NotNull(message = "Email notification preference is required")
    private boolean enabled;
}
