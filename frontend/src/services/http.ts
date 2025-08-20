import axios from 'axios';
import type { AxiosInstance, AxiosResponse, InternalAxiosRequestConfig } from 'axios';
import { message } from 'antd';
import { API_BASE_URL } from '../constants';
import type { ApiResponse } from '../types';

// Create axios instance
const httpClient: AxiosInstance = axios.create({
  baseURL: API_BASE_URL,
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json',
  },
});

// Request interceptor
httpClient.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    // Add auth token if available
    const token = localStorage.getItem('auth_token');
    if (token && config.headers) {
      (config.headers as any).Authorization = `Bearer ${token}`;
    }
    
    // Log request in development
    if ((import.meta as any).env?.DEV) {
      console.log('API Request:', {
        method: config.method?.toUpperCase(),
        url: config.url,
        data: config.data,
      });
    }
    
    return config;
  },
  (error) => {
    console.error('Request Error:', error);
    return Promise.reject(error);
  }
);

// Response interceptor
httpClient.interceptors.response.use(
  (response: AxiosResponse<ApiResponse>) => {
    // Log response in development
    if ((import.meta as any).env?.DEV) {
      console.log('API Response:', {
        status: response.status,
        url: response.config.url,
        data: response.data,
      });
    }
    
    // Handle API response format
    if (response.data && typeof response.data === 'object') {
      if ((response.data as any).success === false) {
        message.error((response.data as any).message || 'Request failed');
        return Promise.reject(new Error((response.data as any).message || 'Request failed'));
      }
    }
    
    return response;
  },
  async (error) => {
    const originalRequest = error.config;
    
    // Handle 401 Unauthorized
    if (error.response?.status === 401 && !originalRequest._retry) {
      originalRequest._retry = true;
      
      try {
        // Try to refresh token
        const refreshToken = localStorage.getItem('refresh_token');
        if (refreshToken) {
          const response = await axios.post(`${API_BASE_URL}/auth/refresh`, {
            refreshToken,
          });
          
          const { token, refreshToken: newRefreshToken } = response.data;
          localStorage.setItem('auth_token', token);
          localStorage.setItem('refresh_token', newRefreshToken);
          
          // Retry original request with new token
          originalRequest.headers['Authorization'] = `Bearer ${token}`;
          return httpClient(originalRequest);
        }
      } catch (refreshError) {
        // Refresh failed, redirect to login
        localStorage.removeItem('auth_token');
        localStorage.removeItem('refresh_token');
        localStorage.removeItem('user_info');
        window.location.href = '/login';
        return Promise.reject(error);
      }
    }
    
    // Handle other errors
    console.error('Response Error:', error);
    
    if (error.response) {
      const { status, data } = error.response;
      
      switch (status) {
        case 400:
          message.error(data?.message || 'Bad request');
          break;
        case 403:
          message.error('Access denied');
          break;
        case 404:
          message.error('Resource not found');
          break;
        case 500:
          message.error('Server error');
          break;
        default:
          message.error(data?.message || 'Request failed');
      }
    } else if (error.request) {
      message.error('Network error, please check your connection');
    } else {
      message.error('Request failed');
    }
    
    return Promise.reject(error);
  }
);

export default httpClient;
