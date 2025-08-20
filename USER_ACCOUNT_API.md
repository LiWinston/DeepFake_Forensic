# User Account Management API Documentation

## Overview
This document describes the API endpoints for user account management including authentication, password reset, email verification, and profile management.

## Base URL
```
http://localhost:8082/api/v1
```

## Authentication Endpoints

### 1. User Registration
- **POST** `/auth/register`
- **Description**: Register a new user account
- **Request Body**:
```json
{
  "username": "string (required, 3-50 chars)",
  "email": "string (required, valid email)",
  "password": "string (required, 6-100 chars)",
  "firstName": "string (optional)",
  "lastName": "string (optional)"
}
```
- **Response**:
```json
{
  "success": true,
  "message": "User registered successfully",
  "data": {
    "token": "jwt_token",
    "refreshToken": "refresh_token",
    "user": {
      "id": 1,
      "username": "testuser",
      "email": "test@example.com",
      "firstName": "John",
      "lastName": "Doe",
      "role": "USER",
      "emailVerified": false,
      "lastLoginAt": null
    }
  }
}
```

### 2. User Login
- **POST** `/auth/login`
- **Description**: Authenticate user login
- **Request Body**:
```json
{
  "username": "string (required)",
  "password": "string (required)"
}
```
- **Response**: Same as registration response

### 3. Refresh Token
- **POST** `/auth/refresh`
- **Description**: Refresh access token
- **Request Body**:
```json
{
  "refreshToken": "string (required)"
}
```
- **Response**: Same as login response

### 4. Logout
- **POST** `/auth/logout`
- **Description**: Logout user (client-side token removal)
- **Response**:
```json
{
  "success": true,
  "message": "Logout successful",
  "data": "Logout successful"
}
```

## Account Management Endpoints

### 5. Request Password Reset
- **POST** `/account/password/reset-request`
- **Description**: Send password reset email
- **Request Body**:
```json
{
  "email": "string (required, valid email)"
}
```
- **Response**:
```json
{
  "success": true,
  "message": "Password reset email sent successfully"
}
```

### 6. Confirm Password Reset
- **POST** `/account/password/reset-confirm`
- **Description**: Reset password using token
- **Request Body**:
```json
{
  "token": "string (required)",
  "password": "string (required, 6-100 chars)",
  "confirmPassword": "string (required, must match password)"
}
```
- **Response**:
```json
{
  "success": true,
  "message": "Password reset successfully"
}
```

### 7. Change Password
- **POST** `/account/password/change`
- **Description**: Change password for authenticated user
- **Headers**: `Authorization: Bearer <token>`
- **Request Body**:
```json
{
  "currentPassword": "string (required)",
  "newPassword": "string (required, 6-100 chars)",
  "confirmPassword": "string (required, must match newPassword)"
}
```
- **Response**:
```json
{
  "success": true,
  "message": "Password changed successfully"
}
```

### 8. Request Email Verification
- **POST** `/account/email/verify-request`
- **Description**: Send email verification
- **Request Body**:
```json
{
  "email": "string (required, valid email)"
}
```
- **Response**:
```json
{
  "success": true,
  "message": "Verification email sent successfully"
}
```

### 9. Verify Email
- **POST** `/account/email/verify?token={token}`
- **Description**: Verify email using token
- **Query Parameters**: `token` (required)
- **Response**:
```json
{
  "success": true,
  "message": "Email verified successfully"
}
```

### 10. Update Profile
- **PUT** `/account/profile`
- **Description**: Update user profile
- **Headers**: `Authorization: Bearer <token>`
- **Request Body**:
```json
{
  "firstName": "string (optional, max 50 chars)",
  "lastName": "string (optional, max 50 chars)"
}
```
- **Response**:
```json
{
  "success": true,
  "message": "Profile updated successfully",
  "data": {
    "id": 1,
    "username": "testuser",
    "email": "test@example.com",
    "firstName": "John",
    "lastName": "Doe",
    "role": "USER",
    "emailVerified": true,
    "lastLoginAt": "2024-08-21T10:30:00"
  }
}
```

### 11. Get Profile
- **GET** `/account/profile`
- **Description**: Get current user profile
- **Headers**: `Authorization: Bearer <token>`
- **Response**: Same as update profile response

## User Endpoints

### 12. Get Current User
- **GET** `/users/me`
- **Description**: Get current authenticated user information
- **Headers**: `Authorization: Bearer <token>`
- **Response**: Same as profile response

## Error Responses

All endpoints may return error responses in the following format:
```json
{
  "success": false,
  "message": "Error description",
  "errorCode": "ERROR_CODE (optional)",
  "timestamp": 1692612345678
}
```

### Common Error Codes
- `VALIDATION_ERROR`: Request validation failed
- `AUTHENTICATION_ERROR`: Authentication required or failed
- `AUTHORIZATION_ERROR`: Insufficient permissions
- `NOT_FOUND`: Resource not found
- `CONFLICT`: Resource conflict (e.g., email already exists)
- `RATE_LIMIT`: Too many requests
- `INTERNAL_ERROR`: Internal server error

## Email Configuration

### Environment Variables
```bash
MAIL_USERNAME=your_email@gmail.com
MAIL_PASSWORD=your_app_password
MAIL_FROM=noreply@deepfakeforensic.com
MAIL_SUPPORT=support@deepfakeforensic.com
```

### Application Properties
```properties
# Mail Configuration
spring.mail.host=smtp.gmail.com
spring.mail.port=587
spring.mail.username=${MAIL_USERNAME:}
spring.mail.password=${MAIL_PASSWORD:}
spring.mail.properties.mail.smtp.auth=true
spring.mail.properties.mail.smtp.starttls.enable=true
spring.mail.properties.mail.smtp.starttls.required=true

# Application Email Settings
app.mail.from=${MAIL_FROM:noreply@deepfakeforensic.com}
app.mail.support=${MAIL_SUPPORT:support@deepfakeforensic.com}
app.mail.reset-password-expiration=3600000
app.mail.verification-expiration=86400000
```

## Security Notes

1. **Password Reset Tokens**: Valid for 1 hour, single-use only
2. **Email Verification Tokens**: Valid for 24 hours, single-use only
3. **JWT Tokens**: Valid for 24 hours
4. **Refresh Tokens**: Valid for 7 days
5. **Rate Limiting**: Implement rate limiting for sensitive endpoints
6. **HTTPS**: Always use HTTPS in production
7. **Token Storage**: Store tokens securely in HttpOnly cookies or secure storage

## Testing

### Example cURL Commands

Register a new user:
```bash
curl -X POST http://localhost:8082/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "username": "testuser",
    "email": "test@example.com",
    "password": "password123",
    "firstName": "John",
    "lastName": "Doe"
  }'
```

Request password reset:
```bash
curl -X POST http://localhost:8082/api/v1/account/password/reset-request \
  -H "Content-Type: application/json" \
  -d '{
    "email": "test@example.com"
  }'
```

Change password:
```bash
curl -X POST http://localhost:8082/api/v1/account/password/change \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -d '{
    "currentPassword": "oldpassword",
    "newPassword": "newpassword123",
    "confirmPassword": "newpassword123"
  }'
```
