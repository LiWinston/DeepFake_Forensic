package com.itproject.auth.service;

import com.itproject.auth.dto.AuthResponse;
import com.itproject.auth.dto.UserLoginRequest;
import com.itproject.auth.dto.UserRegistrationRequest;
import com.itproject.auth.entity.User;
import com.itproject.auth.repository.UserRepository;
import com.itproject.auth.security.JwtTokenUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Authentication service for user registration and login
 */
@Service
@RequiredArgsConstructor
@Transactional
public class AuthService {
    
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtTokenUtil jwtTokenUtil;
    
    /**
     * Register a new user
     */
    public AuthResponse register(UserRegistrationRequest request) {
        // Check if username already exists
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new RuntimeException("用户名已存在");
        }
        
        // Check if email already exists
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("邮箱已被注册");
        }
        
        // Create new user
        User user = new User();
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setFirstName(request.getFirstName());
        user.setLastName(request.getLastName());
        user.setRole(User.UserRole.USER);
        user.setStatus(User.UserStatus.ACTIVE);
        
        User savedUser = userRepository.save(user);
        
        // Generate tokens
        String token = jwtTokenUtil.generateToken(savedUser);
        String refreshToken = jwtTokenUtil.generateRefreshToken(savedUser);
        
        return new AuthResponse(token, refreshToken, AuthResponse.UserInfo.fromUser(savedUser));
    }
    
    /**
     * Authenticate user login
     */
    public AuthResponse login(UserLoginRequest request) {
        // Authenticate user
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
        );
        
        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        User user = userRepository.findByUsername(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("用户不存在"));
        
        // Update last login time
        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);
        
        // Generate tokens
        String token = jwtTokenUtil.generateToken(userDetails);
        String refreshToken = jwtTokenUtil.generateRefreshToken(userDetails);
        
        return new AuthResponse(token, refreshToken, AuthResponse.UserInfo.fromUser(user));
    }
    
    /**
     * Refresh access token
     */
    public AuthResponse refreshToken(String refreshToken) {
        if (!jwtTokenUtil.isValidToken(refreshToken)) {
            throw new RuntimeException("无效的刷新令牌");
        }
        
        String username = jwtTokenUtil.extractUsername(refreshToken);
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("用户不存在"));
        
        String newToken = jwtTokenUtil.generateToken(user);
        String newRefreshToken = jwtTokenUtil.generateRefreshToken(user);
        
        return new AuthResponse(newToken, newRefreshToken, AuthResponse.UserInfo.fromUser(user));
    }
}
