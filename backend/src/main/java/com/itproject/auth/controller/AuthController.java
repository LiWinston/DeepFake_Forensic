package com.itproject.auth.controller;

import com.itproject.auth.dto.AuthResponse;
import com.itproject.auth.dto.UserLoginRequest;
import com.itproject.auth.dto.UserRegistrationRequest;
import com.itproject.auth.service.AuthService;
import com.itproject.common.dto.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Authentication controller for user registration and login
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class AuthController {
    
    private final AuthService authService;
      /**
     * User registration endpoint
     */
    @PostMapping("/register")
    public Result<AuthResponse> register(@Valid @RequestBody UserRegistrationRequest request) {
        try {
            AuthResponse response = authService.register(request);
            return Result.success(response, "User registered successfully");
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }
    
    /**
     * User login endpoint
     */
    @PostMapping("/login")
    public Result<AuthResponse> login(@Valid @RequestBody UserLoginRequest request) {
        try {
            AuthResponse response = authService.login(request);
            return Result.success(response, "Login successful");
        } catch (RuntimeException e) {
            return Result.error("Invalid username or password");
        }
    }
    
    /**
     * Token refresh endpoint
     */
    @PostMapping("/refresh")
    public Result<AuthResponse> refreshToken(@RequestBody Map<String, String> request) {
        try {
            String refreshToken = request.get("refreshToken");
            if (refreshToken == null || refreshToken.isEmpty()) {
                return Result.error("Refresh token is required");
            }
            
            AuthResponse response = authService.refreshToken(refreshToken);
            return Result.success(response, "Token refreshed successfully");
        } catch (RuntimeException e) {
            return Result.error("Invalid refresh token");
        }
    }
    
    /**
     * Logout endpoint (client-side token removal)
     */
    @PostMapping("/logout")
    public Result<String> logout() {
        return Result.success("Logout successful", "Logout successful");
    }
}
