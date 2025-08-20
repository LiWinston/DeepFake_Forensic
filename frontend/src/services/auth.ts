// Authentication service
import http from './http';

export interface LoginRequest {
  username: string;
  password: string;
}

export interface RegisterRequest {
  username: string;
  email: string;
  password: string;
  firstName?: string;
  lastName?: string;
}

export interface PasswordResetRequest {
  email: string;
}

export interface PasswordResetConfirm {
  token: string;
  password: string;
  confirmPassword: string;
}

export interface PasswordChange {
  currentPassword: string;
  newPassword: string;
  confirmPassword: string;
}

export interface EmailVerificationRequest {
  email: string;
}

export interface UserProfileUpdate {
  firstName?: string;
  lastName?: string;
}

export interface AuthResponse {
  token: string;
  refreshToken: string;
  user: {
    id: number;
    username: string;
    email: string;
    firstName?: string;
    lastName?: string;
    role: 'USER' | 'ADMIN';
    lastLoginAt?: string;
  };
}

export interface User {
  id: number;
  username: string;
  email: string;
  firstName?: string;
  lastName?: string;
  role: 'USER' | 'ADMIN';
  emailVerified: boolean;
  emailVerifiedAt?: string;
  lastLoginAt?: string;
}

class AuthService {
  private readonly TOKEN_KEY = 'auth_token';
  private readonly REFRESH_TOKEN_KEY = 'refresh_token';
  private readonly USER_KEY = 'user_info';

  // User login
  async login(data: LoginRequest): Promise<AuthResponse> {
    const response = await http.post<AuthResponse>('/auth/login', data);
    this.setAuthData(response.data);
    return response.data;
  }

  // User registration
  async register(data: RegisterRequest): Promise<AuthResponse> {
    const response = await http.post<AuthResponse>('/auth/register', data);
    this.setAuthData(response.data);
    return response.data;
  }

  // User logout
  async logout(): Promise<void> {
    try {
      await http.post('/auth/logout');
    } catch (error) {
      console.warn('Logout request failed:', error);
    } finally {
      this.clearAuthData();
    }
  }

  // Refresh token
  async refreshToken(): Promise<AuthResponse> {
    const refreshToken = this.getRefreshToken();
    if (!refreshToken) {
      throw new Error('No refresh token available');
    }

    const response = await http.post<AuthResponse>('/auth/refresh', {
      refreshToken,
    });
    this.setAuthData(response.data);
    return response.data;
  }  // Request password reset
  async requestPasswordReset(username: string): Promise<void> {
    await http.post('/api/v1/account/password/reset-request', { username });
  }

  // Validate reset token
  async validateResetToken(token: string): Promise<void> {
    await http.get(`/api/v1/account/password/validate-token?token=${token}`);
  }
  // Reset password with token
  async resetPassword(token: string, newPassword: string): Promise<void> {
    await http.post('/api/v1/account/password/reset-confirm', {
      token,
      password: newPassword,
      confirmPassword: newPassword
    });
  }

  // Confirm password reset
  async confirmPasswordReset(data: PasswordResetConfirm): Promise<void> {
    await http.post('/api/v1/account/password/reset-confirm', data);
  }

  // Change password
  async changePassword(data: PasswordChange): Promise<void> {
    await http.post('/api/v1/account/password/change', data);
  }

  // Request email verification
  async requestEmailVerification(data: EmailVerificationRequest): Promise<void> {
    await http.post('/api/v1/account/email/verify-request', data);
  }

  // Verify email
  async verifyEmail(token: string): Promise<void> {
    await http.post(`/api/v1/account/email/verify?token=${token}`);
  }

  // Update profile
  async updateProfile(data: UserProfileUpdate): Promise<User> {
    const response = await http.put<User>('/api/v1/account/profile', data);
    return response.data;
  }

  // Get current user
  async getCurrentUser(): Promise<User> {
    const response = await http.get<User>('/api/v1/account/profile');
    return response.data;
  }

  // Get profile (alias for getCurrentUser)
  async getProfile(): Promise<User> {
    return this.getCurrentUser();
  }

  // Set authentication data
  private setAuthData(authResponse: AuthResponse): void {
    localStorage.setItem(this.TOKEN_KEY, authResponse.token);
    localStorage.setItem(this.REFRESH_TOKEN_KEY, authResponse.refreshToken);
    localStorage.setItem(this.USER_KEY, JSON.stringify(authResponse.user));
  }

  // Clear authentication data
  private clearAuthData(): void {
    localStorage.removeItem(this.TOKEN_KEY);
    localStorage.removeItem(this.REFRESH_TOKEN_KEY);
    localStorage.removeItem(this.USER_KEY);
  }

  // Get access token
  getToken(): string | null {
    return localStorage.getItem(this.TOKEN_KEY);
  }

  // Get refresh token
  getRefreshToken(): string | null {
    return localStorage.getItem(this.REFRESH_TOKEN_KEY);
  }

  // Get user information
  getUserInfo(): User | null {
    const userStr = localStorage.getItem(this.USER_KEY);
    if (!userStr) return null;
    
    try {
      return JSON.parse(userStr);
    } catch {
      return null;
    }
  }

  // Check if user is logged in
  isLoggedIn(): boolean {
    return !!this.getToken();
  }

  // Check if token is expired
  isTokenExpired(): boolean {
    const token = this.getToken();
    if (!token) return true;

    try {
      const payload = JSON.parse(atob(token.split('.')[1]));
      const currentTime = Date.now() / 1000;
      return payload.exp < currentTime;
    } catch {
      return true;
    }
  }
}

export const authService = new AuthService();
export default authService;
