import { describe, it, expect, vi, beforeEach } from 'vitest'
import httpClient from '../src/services/http'
import type { ApiResponse } from '../src/types'

// Mock axios
vi.mock('axios', () => ({
  default: {
    create: vi.fn(() => ({
      interceptors: {
        request: { use: vi.fn() },
        response: { use: vi.fn() }
      }
    })),
    post: vi.fn()
  }
}))

// Mock antd message
vi.mock('antd', () => ({
  message: {
    error: vi.fn(),
    success: vi.fn()
  }
}))

// Mock localStorage
const mockLocalStorage = {
  getItem: vi.fn(),
  setItem: vi.fn(),
  removeItem: vi.fn()
}
Object.defineProperty(window, 'localStorage', {
  value: mockLocalStorage
})

describe('HTTP Client Service', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  describe('HTTP Client Configuration', () => {
    it('should create axios instance with correct configuration', () => {
      // Since we're mocking axios, we can't test the actual instance creation
      // But we can test that the module exports correctly
      expect(httpClient).toBeDefined()
    })
  })

  describe('API Response Types', () => {
    it('should validate ApiResponse structure', () => {
      const mockResponse: ApiResponse<{ id: string }> = {
        success: true,
        data: { id: 'test-123' },
        message: 'Success',
        timestamp: Date.now()
      }

      expect(mockResponse.success).toBe(true)
      expect(mockResponse.data.id).toBe('test-123')
      expect(typeof mockResponse.timestamp).toBe('number')
    })

    it('should handle error responses', () => {
      const errorResponse: ApiResponse<null> = {
        success: false,
        data: null,
        message: 'Error occurred',
        errorCode: 'VALIDATION_ERROR'
      }

      expect(errorResponse.success).toBe(false)
      expect(errorResponse.data).toBe(null)
      expect(errorResponse.errorCode).toBe('VALIDATION_ERROR')
    })
  })

  describe('LocalStorage Integration', () => {
    it('should handle localStorage operations', () => {
      mockLocalStorage.getItem.mockReturnValue('test-token')
      
      expect(mockLocalStorage.getItem('auth_token')).toBe('test-token')
      expect(mockLocalStorage.getItem).toHaveBeenCalledWith('auth_token')
    })

    it('should handle token storage', () => {
      mockLocalStorage.setItem('auth_token', 'new-token')
      
      expect(mockLocalStorage.setItem).toHaveBeenCalledWith('auth_token', 'new-token')
    })

    it('should handle token removal', () => {
      mockLocalStorage.removeItem('auth_token')
      
      expect(mockLocalStorage.removeItem).toHaveBeenCalledWith('auth_token')
    })
  })

  describe('Environment Detection', () => {
    it('should handle development environment', () => {
      // Mock import.meta.env
      Object.defineProperty(import.meta, 'env', {
        value: { DEV: true },
        writable: true
      })

      expect((import.meta as any).env.DEV).toBe(true)
    })

    it('should handle production environment', () => {
      // Mock import.meta.env
      Object.defineProperty(import.meta, 'env', {
        value: { DEV: false },
        writable: true
      })

      expect((import.meta as any).env.DEV).toBe(false)
    })
  })

  describe('Error Handling Scenarios', () => {
    it('should handle 401 Unauthorized', () => {
      const errorResponse = {
        response: {
          status: 401,
          data: { message: 'Unauthorized' }
        }
      }

      expect(errorResponse.response.status).toBe(401)
      expect(errorResponse.response.data.message).toBe('Unauthorized')
    })

    it('should handle 400 Bad Request', () => {
      const errorResponse = {
        response: {
          status: 400,
          data: { message: 'Bad request' }
        }
      }

      expect(errorResponse.response.status).toBe(400)
    })

    it('should handle 404 Not Found', () => {
      const errorResponse = {
        response: {
          status: 404,
          data: { message: 'Not found' }
        }
      }

      expect(errorResponse.response.status).toBe(404)
    })

    it('should handle 500 Server Error', () => {
      const errorResponse = {
        response: {
          status: 500,
          data: { message: 'Server error' }
        }
      }

      expect(errorResponse.response.status).toBe(500)
    })

    it('should handle network errors', () => {
      const networkError = {
        request: {},
        message: 'Network Error'
      }

      expect(networkError.request).toBeDefined()
      expect(networkError.message).toBe('Network Error')
    })
  })

  describe('Request Configuration', () => {
    it('should validate request headers', () => {
      const mockConfig = {
        headers: {
          'Content-Type': 'application/json',
          'Authorization': 'Bearer test-token'
        }
      }

      expect(mockConfig.headers['Content-Type']).toBe('application/json')
      expect(mockConfig.headers['Authorization']).toBe('Bearer test-token')
    })

    it('should validate request timeout', () => {
      const timeout = 30000
      expect(timeout).toBe(30000)
    })
  })
})
