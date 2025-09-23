import { describe, it, expect } from 'vitest'
import type { 
  ApiResponse, 
  PaginationResponse, 
  Project, 
  ProjectType, 
  ProjectStatus,
  AnalysisTask,
  AnalysisType,
  TaskStatus,
  UploadFile,
  MetadataAnalysis,
  MetadataResult,
  TraditionalAnalysisResult,
  AuthenticityAssessment
} from '../src/types/index'

describe('Type Definitions and Data Validation', () => {
  describe('ApiResponse interface', () => {
    it('should create valid ApiResponse objects', () => {
      const response: ApiResponse<{ id: string }> = {
        success: true,
        data: { id: 'test-123' },
        message: 'Success',
        timestamp: Date.now()
      }

      expect(response.success).toBe(true)
      expect(response.data.id).toBe('test-123')
      expect(response.message).toBe('Success')
      expect(typeof response.timestamp).toBe('number')
    })
  })

  describe('Project interface', () => {
    it('should create valid Project objects', () => {
      const project: Project = {
        id: 1,
        name: 'Test Project',
        description: 'Test Description',
        caseNumber: 'CASE-001',
        clientName: 'Test Client',
        projectType: 'CRIMINAL',
        status: 'ACTIVE',
        createdAt: '2024-01-01T00:00:00Z',
        updatedAt: '2024-01-01T00:00:00Z'
      }

      expect(project.id).toBe(1)
      expect(project.name).toBe('Test Project')
      expect(project.projectType).toBe('CRIMINAL')
      expect(project.status).toBe('ACTIVE')
    })

    it('should validate ProjectType enum values', () => {
      const validTypes: ProjectType[] = ['GENERAL', 'CRIMINAL', 'CIVIL', 'CORPORATE', 'ACADEMIC_RESEARCH']
      const testType: ProjectType = 'CRIMINAL'

      expect(validTypes).toContain(testType)
    })

    it('should validate ProjectStatus enum values', () => {
      const validStatuses: ProjectStatus[] = ['ACTIVE', 'COMPLETED', 'SUSPENDED', 'ARCHIVED']
      const testStatus: ProjectStatus = 'ACTIVE'

      expect(validStatuses).toContain(testStatus)
    })
  })

  describe('AnalysisTask interface', () => {
    it('should create valid AnalysisTask objects', () => {
      const task: AnalysisTask = {
        id: 1,
        taskName: 'Test Analysis',
        analysisType: 'METADATA_ANALYSIS',
        status: 'PENDING',
        projectId: 1,
        createdAt: '2024-01-01T00:00:00Z',
        updatedAt: '2024-01-01T00:00:00Z'
      }

      expect(task.id).toBe(1)
      expect(task.taskName).toBe('Test Analysis')
      expect(task.analysisType).toBe('METADATA_ANALYSIS')
      expect(task.status).toBe('PENDING')
    })

    it('should validate AnalysisType enum values', () => {
      const validTypes: AnalysisType[] = [
        'METADATA_ANALYSIS', 'DEEPFAKE_DETECTION', 'EDIT_DETECTION',
        'COMPRESSION_ANALYSIS', 'HASH_VERIFICATION', 'EXIF_ANALYSIS'
      ]
      const testType: AnalysisType = 'METADATA_ANALYSIS'

      expect(validTypes).toContain(testType)
    })

    it('should validate TaskStatus enum values', () => {
      const validStatuses: TaskStatus[] = ['PENDING', 'RUNNING', 'COMPLETED', 'FAILED', 'CANCELLED', 'PAUSED']
      const testStatus: TaskStatus = 'PENDING'

      expect(validStatuses).toContain(testStatus)
    })
  })

  describe('UploadFile interface', () => {
    it('should create valid UploadFile objects', () => {
      const file: UploadFile = {
        id: 'file-123',
        filename: 'test.jpg',
        originalName: 'original.jpg',
        fileType: 'image/jpeg',
        fileSize: 1024000,
        filePath: '/uploads/test.jpg',
        uploadTime: '2024-01-01T00:00:00Z',
        status: 'COMPLETED'
      }

      expect(file.id).toBe('file-123')
      expect(file.filename).toBe('test.jpg')
      expect(file.fileSize).toBe(1024000)
      expect(file.status).toBe('COMPLETED')
    })
  })

  describe('MetadataResult interface', () => {
    it('should create valid MetadataResult objects', () => {
      const result: MetadataResult = {
        exifData: { camera: 'Test Camera' },
        fileHeaders: { contentType: 'image/jpeg' },
        hashData: { md5: 'abc123', sha256: 'def456' },
        suspicious: {
          hasAnomalies: true,
          anomalies: ['suspicious_metadata'],
          riskScore: 0.75
        }
      }

      expect(result.exifData?.camera).toBe('Test Camera')
      expect(result.hashData?.md5).toBe('abc123')
      expect(result.suspicious?.hasAnomalies).toBe(true)
      expect(result.suspicious?.riskScore).toBe(0.75)
    })

    it('should validate risk score range', () => {
      const validResults = [
        { riskScore: 0, expected: true },
        { riskScore: 0.5, expected: true },
        { riskScore: 1, expected: true },
        { riskScore: -0.1, expected: false },
        { riskScore: 1.1, expected: false }
      ]

      validResults.forEach(({ riskScore, expected }) => {
        const isValid = riskScore >= 0 && riskScore <= 1
        expect(isValid).toBe(expected)
      })
    })
  })

  describe('AuthenticityAssessment enum', () => {
    it('should validate AuthenticityAssessment values', () => {
      const validAssessments: AuthenticityAssessment[] = [
        'AUTHENTIC', 'LIKELY_AUTHENTIC', 'SUSPICIOUS', 
        'LIKELY_MANIPULATED', 'MANIPULATED'
      ]
      const testAssessment: AuthenticityAssessment = 'AUTHENTIC'

      expect(validAssessments).toContain(testAssessment)
    })
  })
})
