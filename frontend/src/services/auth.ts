// 认证服务
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
  lastLoginAt?: string;
}

class AuthService {
  private readonly TOKEN_KEY = 'auth_token';
  private readonly REFRESH_TOKEN_KEY = 'refresh_token';
  private readonly USER_KEY = 'user_info';

  // 登录
  async login(data: LoginRequest): Promise<AuthResponse> {
    const response = await http.post<AuthResponse>('/auth/login', data);
    this.setAuthData(response.data);
    return response.data;
  }

  // 注册
  async register(data: RegisterRequest): Promise<AuthResponse> {
    const response = await http.post<AuthResponse>('/auth/register', data);
    this.setAuthData(response.data);
    return response.data;
  }

  // 登出
  async logout(): Promise<void> {
    try {
      await http.post('/auth/logout');
    } catch (error) {
      console.warn('Logout request failed:', error);
    } finally {
      this.clearAuthData();
    }
  }

  // 刷新令牌
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
  }

  // 获取当前用户信息
  async getCurrentUser(): Promise<User> {
    const response = await http.get<User>('/users/me');
    return response.data;
  }

  // 设置认证数据
  private setAuthData(authResponse: AuthResponse): void {
    localStorage.setItem(this.TOKEN_KEY, authResponse.token);
    localStorage.setItem(this.REFRESH_TOKEN_KEY, authResponse.refreshToken);
    localStorage.setItem(this.USER_KEY, JSON.stringify(authResponse.user));
  }

  // 清除认证数据
  private clearAuthData(): void {
    localStorage.removeItem(this.TOKEN_KEY);
    localStorage.removeItem(this.REFRESH_TOKEN_KEY);
    localStorage.removeItem(this.USER_KEY);
  }

  // 获取访问令牌
  getToken(): string | null {
    return localStorage.getItem(this.TOKEN_KEY);
  }

  // 获取刷新令牌
  getRefreshToken(): string | null {
    return localStorage.getItem(this.REFRESH_TOKEN_KEY);
  }

  // 获取用户信息
  getUserInfo(): User | null {
    const userStr = localStorage.getItem(this.USER_KEY);
    if (!userStr) return null;
    
    try {
      return JSON.parse(userStr);
    } catch {
      return null;
    }
  }

  // 检查是否已登录
  isLoggedIn(): boolean {
    return !!this.getToken();
  }

  // 检查令牌是否过期
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
