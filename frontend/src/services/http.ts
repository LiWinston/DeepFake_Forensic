import axios, { AxiosInstance, AxiosRequestConfig, AxiosResponse } from 'axios';
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
  (config: AxiosRequestConfig) => {
    // Add auth token if available
    const token = localStorage.getItem('token');
    if (token && config.headers) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    
    // Log request in development
    if (import.meta.env.DEV) {
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
    if (import.meta.env.DEV) {
      console.log('API Response:', {
        status: response.status,
        url: response.config.url,
        data: response.data,
      });
    }
    
    // Handle API response format
    if (response.data && typeof response.data === 'object') {
      if (response.data.success === false) {
        message.error(response.data.message || 'Request failed');
        return Promise.reject(new Error(response.data.message || 'Request failed'));
      }
    }
    
    return response;
  },
  (error) => {
    console.error('Response Error:', error);
    
    // Handle different error status codes
    if (error.response) {
      const { status, data } = error.response;
      
      switch (status) {
        case 401:
          message.error('Authentication failed');
          localStorage.removeItem('token');
          window.location.href = '/login';
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
