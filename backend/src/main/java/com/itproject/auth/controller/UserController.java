package com.itproject.auth.controller;

import com.itproject.auth.entity.User;
import com.itproject.auth.security.SecurityUtils;
import com.itproject.auth.dto.AuthResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Controller for user management operations
 */
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
@PreAuthorize("hasRole('USER')")
public class UserController {
    
    /**
     * Get current user information
     */
    @GetMapping("/me")
    public ResponseEntity<AuthResponse.UserInfo> getCurrentUser() {
        User currentUser = SecurityUtils.getCurrentUser();
        if (currentUser == null) {
            return ResponseEntity.badRequest().build();
        }
        
        return ResponseEntity.ok(AuthResponse.UserInfo.fromUser(currentUser));
    }
}
