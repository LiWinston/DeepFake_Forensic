package com.itproject.auth.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Date;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class JwtTokenUtilTest {

    private JwtTokenUtil jwtTokenUtil;

    @BeforeEach
    void setUp() {
        jwtTokenUtil = new JwtTokenUtil();
        // Set test values using reflection
        ReflectionTestUtils.setField(jwtTokenUtil, "secret", "testsecretkeyforjwttestingpurposesonlyverylongkey");
        ReflectionTestUtils.setField(jwtTokenUtil, "jwtExpiration", 3600); // 1 hour
        ReflectionTestUtils.setField(jwtTokenUtil, "refreshExpiration", 86400); // 1 day
    }

    @Test
    void testGenerateToken_ValidUserDetails() {
        UserDetails userDetails = User.withUsername("testuser")
                .password("password")
                .authorities("ROLE_USER")
                .build();

        String token = jwtTokenUtil.generateToken(userDetails);

        assertNotNull(token);
        assertFalse(token.isEmpty());
        assertTrue(token.contains(".")); // JWT should contain dots
    }

    @Test
    void testGenerateRefreshToken_ValidUserDetails() {
        UserDetails userDetails = User.withUsername("testuser")
                .password("password")
                .authorities("ROLE_USER")
                .build();

        String refreshToken = jwtTokenUtil.generateRefreshToken(userDetails);

        assertNotNull(refreshToken);
        assertFalse(refreshToken.isEmpty());
        assertTrue(refreshToken.contains(".")); // JWT should contain dots
    }

    @Test
    void testExtractUsername_ValidToken() {
        UserDetails userDetails = User.withUsername("testuser")
                .password("password")
                .authorities("ROLE_USER")
                .build();

        String token = jwtTokenUtil.generateToken(userDetails);
        String extractedUsername = jwtTokenUtil.extractUsername(token);

        assertEquals("testuser", extractedUsername);
    }

    @Test
    void testExtractUsername_InvalidToken() {
        String invalidToken = "invalid.token.here";

        assertThrows(Exception.class, () -> {
            jwtTokenUtil.extractUsername(invalidToken);
        });
    }

    @Test
    void testExtractExpiration_ValidToken() {
        UserDetails userDetails = User.withUsername("testuser")
                .password("password")
                .authorities("ROLE_USER")
                .build();

        String token = jwtTokenUtil.generateToken(userDetails);
        Date expiration = jwtTokenUtil.extractExpiration(token);

        assertNotNull(expiration);
        assertTrue(expiration.after(new Date())); // Should be in the future
    }

    @Test
    void testExtractClaim_ValidToken() {
        UserDetails userDetails = User.withUsername("testuser")
                .password("password")
                .authorities("ROLE_USER")
                .build();

        String token = jwtTokenUtil.generateToken(userDetails);
        
        // Extract subject claim
        String subject = jwtTokenUtil.extractClaim(token, claims -> claims.getSubject());
        assertEquals("testuser", subject);

        // Extract issued at claim
        Date issuedAt = jwtTokenUtil.extractClaim(token, claims -> claims.getIssuedAt());
        assertNotNull(issuedAt);
        assertTrue(issuedAt.before(new Date()) || issuedAt.equals(new Date()));
    }

    @Test
    void testValidateToken_ValidTokenAndUserDetails() {
        UserDetails userDetails = User.withUsername("testuser")
                .password("password")
                .authorities("ROLE_USER")
                .build();

        String token = jwtTokenUtil.generateToken(userDetails);
        Boolean isValid = jwtTokenUtil.validateToken(token, userDetails);

        assertTrue(isValid);
    }

    @Test
    void testValidateToken_InvalidUsername() {
        UserDetails userDetails1 = User.withUsername("testuser1")
                .password("password")
                .authorities("ROLE_USER")
                .build();

        UserDetails userDetails2 = User.withUsername("testuser2")
                .password("password")
                .authorities("ROLE_USER")
                .build();

        String token = jwtTokenUtil.generateToken(userDetails1);
        Boolean isValid = jwtTokenUtil.validateToken(token, userDetails2);

        assertFalse(isValid);
    }

    @Test
    void testValidateToken_InvalidToken() {
        UserDetails userDetails = User.withUsername("testuser")
                .password("password")
                .authorities("ROLE_USER")
                .build();

        String invalidToken = "invalid.token.here";
        
        // Since validateToken will throw an exception for invalid tokens, we should catch it
        assertThrows(Exception.class, () -> {
            jwtTokenUtil.validateToken(invalidToken, userDetails);
        });
    }

    @Test
    void testIsValidToken_ValidToken() {
        UserDetails userDetails = User.withUsername("testuser")
                .password("password")
                .authorities("ROLE_USER")
                .build();

        String token = jwtTokenUtil.generateToken(userDetails);
        Boolean isValid = jwtTokenUtil.isValidToken(token);

        assertTrue(isValid);
    }

    @Test
    void testIsValidToken_InvalidToken() {
        String invalidToken = "invalid.token.here";
        Boolean isValid = jwtTokenUtil.isValidToken(invalidToken);

        assertFalse(isValid);
    }

    @Test
    void testIsValidToken_NullToken() {
        Boolean isValid = jwtTokenUtil.isValidToken(null);

        assertFalse(isValid);
    }

    @Test
    void testIsValidToken_EmptyToken() {
        Boolean isValid = jwtTokenUtil.isValidToken("");

        assertFalse(isValid);
    }

    @Test
    void testTokenExpiration() {
        // Set very short expiration time
        ReflectionTestUtils.setField(jwtTokenUtil, "jwtExpiration", 1); // 1 second

        UserDetails userDetails = User.withUsername("testuser")
                .password("password")
                .authorities("ROLE_USER")
                .build();

        String token = jwtTokenUtil.generateToken(userDetails);

        // Wait for token to expire
        try {
            Thread.sleep(1500); // Wait 1.5 seconds to ensure expiration
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Test that token is expired by checking isValidToken instead of validateToken
        Boolean isValid = jwtTokenUtil.isValidToken(token);
        assertFalse(isValid);
    }

    @Test
    void testRefreshTokenContainsTypeClaim() {
        UserDetails userDetails = User.withUsername("testuser")
                .password("password")
                .authorities("ROLE_USER")
                .build();

        String refreshToken = jwtTokenUtil.generateRefreshToken(userDetails);
        
        // Extract the type claim
        String type = jwtTokenUtil.extractClaim(refreshToken, claims -> claims.get("type", String.class));
        assertEquals("refresh", type);
    }

    @Test
    void testTokenStructure() {
        UserDetails userDetails = User.withUsername("testuser")
                .password("password")
                .authorities("ROLE_USER")
                .build();

        String token = jwtTokenUtil.generateToken(userDetails);

        // JWT should have 3 parts separated by dots
        String[] parts = token.split("\\.");
        assertEquals(3, parts.length);

        // Each part should not be empty
        for (String part : parts) {
            assertFalse(part.isEmpty());
        }
    }
}
