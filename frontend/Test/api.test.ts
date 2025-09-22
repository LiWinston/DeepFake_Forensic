import { describe, it, expect, vi, beforeEach } from 'vitest'

// Mock API service functions
const API_BASE_URL = 'http://localhost:8082/api'

interface ApiResponse<T> {
  data: T
  status: number
  message: string
}

const mockFetch = vi.fn()
global.fetch = mockFetch

// Mock API functions
const uploadFile = async (file: File): Promise<ApiResponse<{ fileId: string }>> => {
  const formData = new FormData()
  formData.append('file', file)
  
  const response = await fetch(`${API_BASE_URL}/upload`, {
    method: 'POST',
    body: formData
  })
  
  return response.json()
}

const getFileMetadata = async (fileId: string): Promise<ApiResponse<any>> => {
  const response = await fetch(`${API_BASE_URL}/metadata/${fileId}`)
  return response.json()
}

const analyzeFile = async (fileId: string): Promise<ApiResponse<{ riskScore: number }>> => {
  const response = await fetch(`${API_BASE_URL}/analyze/${fileId}`, {
    method: 'POST'
  })
  return response.json()
}

describe('API Service Functions', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  describe('uploadFile', () => {
    it('should upload file successfully', async () => {
      const mockFile = new File(['test content'], 'test.jpg', { type: 'image/jpeg' })
      const mockResponse = {
        data: { fileId: 'file-123' },
        status: 200,
        message: 'File uploaded successfully'
      }

      mockFetch.mockResolvedValueOnce({
        json: () => Promise.resolve(mockResponse)
      })

      const result = await uploadFile(mockFile)

      expect(mockFetch).toHaveBeenCalledWith(
        `${API_BASE_URL}/upload`,
        expect.objectContaining({
          method: 'POST',
          body: expect.any(FormData)
        })
      )
      expect(result).toEqual(mockResponse)
    })

    it('should handle upload errors', async () => {
      const mockFile = new File(['test content'], 'test.jpg', { type: 'image/jpeg' })
      const mockError = {
        data: null,
        status: 400,
        message: 'File too large'
      }

      mockFetch.mockResolvedValueOnce({
        json: () => Promise.resolve(mockError)
      })

      const result = await uploadFile(mockFile)

      expect(result.status).toBe(400)
      expect(result.message).toBe('File too large')
    })
  })

  describe('getFileMetadata', () => {
    it('should fetch file metadata', async () => {
      const fileId = 'file-123'
      const mockMetadata = {
        data: {
          id: fileId,
          name: 'test.jpg',
          size: 1024000,
          type: 'image/jpeg'
        },
        status: 200,
        message: 'Metadata retrieved'
      }

      mockFetch.mockResolvedValueOnce({
        json: () => Promise.resolve(mockMetadata)
      })

      const result = await getFileMetadata(fileId)

      expect(mockFetch).toHaveBeenCalledWith(`${API_BASE_URL}/metadata/${fileId}`)
      expect(result.data.id).toBe(fileId)
    })
  })

  describe('analyzeFile', () => {
    it('should analyze file for deepfake detection', async () => {
      const fileId = 'file-123'
      const mockAnalysis = {
        data: { riskScore: 0.75 },
        status: 200,
        message: 'Analysis completed'
      }

      mockFetch.mockResolvedValueOnce({
        json: () => Promise.resolve(mockAnalysis)
      })

      const result = await analyzeFile(fileId)

      expect(mockFetch).toHaveBeenCalledWith(
        `${API_BASE_URL}/analyze/${fileId}`,
        expect.objectContaining({
          method: 'POST'
        })
      )
      expect(result.data.riskScore).toBe(0.75)
    })

    it('should handle analysis errors', async () => {
      const fileId = 'invalid-file'
      const mockError = {
        data: null,
        status: 404,
        message: 'File not found'
      }

      mockFetch.mockResolvedValueOnce({
        json: () => Promise.resolve(mockError)
      })

      const result = await analyzeFile(fileId)

      expect(result.status).toBe(404)
      expect(result.message).toBe('File not found')
    })
  })

  describe('API Error Handling', () => {
    it('should handle network errors', async () => {
      mockFetch.mockRejectedValueOnce(new Error('Network error'))

      const mockFile = new File(['test'], 'test.jpg')
      
      await expect(uploadFile(mockFile)).rejects.toThrow('Network error')
    })

    it('should handle invalid JSON responses', async () => {
      mockFetch.mockResolvedValueOnce({
        json: () => Promise.reject(new Error('Invalid JSON'))
      })

      const mockFile = new File(['test'], 'test.jpg')
      
      await expect(uploadFile(mockFile)).rejects.toThrow('Invalid JSON')
    })
  })
})
