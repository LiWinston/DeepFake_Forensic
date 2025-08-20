// Common types for the DeepFake Forensic application

export interface ApiResponse<T = any> {
  success: boolean;
  data: T;
  message?: string;
  code?: number;
}

export interface PaginationResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  currentPage: number;
  pageSize: number;
}

// Project Management Types
export interface Project {
  id: number;
  name: string;
  description?: string;
  caseNumber: string;
  clientName?: string;
  clientContact?: string;
  projectType: ProjectType;
  status: ProjectStatus;
  tags?: string;
  deadline?: string;
  caseDate?: string;
  evidenceDescription?: string;
  notes?: string;
  createdAt: string;
  updatedAt: string;
}

export type ProjectType = 
  | 'GENERAL'
  | 'CRIMINAL'
  | 'CIVIL'
  | 'CORPORATE'
  | 'ACADEMIC_RESEARCH';

export type ProjectStatus = 
  | 'ACTIVE'
  | 'COMPLETED'
  | 'SUSPENDED'
  | 'ARCHIVED';

export interface CreateProjectRequest {
  name: string;
  description?: string;
  caseNumber?: string;
  clientName?: string;
  clientContact?: string;
  projectType?: ProjectType;
  deadline?: string;
  caseDate?: string;
  evidenceDescription?: string;
  notes?: string;
  tags?: string;
}

// Analysis Task Types
export interface AnalysisTask {
  id: number;
  taskName: string;
  analysisType: AnalysisType;
  status: TaskStatus;
  description?: string;
  resultData?: string;
  confidenceScore?: number;
  notes?: string;
  startedAt?: string;
  completedAt?: string;
  createdAt: string;
  updatedAt: string;
  projectId: number;
}

export type AnalysisType = 
  | 'METADATA_ANALYSIS'
  | 'DEEPFAKE_DETECTION'
  | 'EDIT_DETECTION'
  | 'COMPRESSION_ANALYSIS'
  | 'HASH_VERIFICATION'
  | 'EXIF_ANALYSIS'
  | 'STEGANOGRAPHY_DETECTION'
  | 'SIMILARITY_ANALYSIS'
  | 'TEMPORAL_ANALYSIS'
  | 'QUALITY_ASSESSMENT';

export type TaskStatus = 
  | 'PENDING'
  | 'RUNNING'
  | 'COMPLETED'
  | 'FAILED'
  | 'CANCELLED'
  | 'PAUSED';

export interface CreateAnalysisTaskRequest {
  taskName?: string;
  analysisType: AnalysisType;
  description?: string;
  notes?: string;
  projectId: number;
}

// File Upload Types
export interface UploadFile {
  id: string;
  filename: string;
  originalName: string;
  fileType: string;
  fileSize: number;
  filePath: string;
  uploadTime: string;
  status: 'UPLOADING' | 'COMPLETED' | 'FAILED';
  chunkTotal?: number;
  chunkUploaded?: number;
  md5Hash?: string;
  projectId?: number; // Associated project ID
}

export interface ChunkUploadRequest {
  fileId: string;
  chunkIndex: number;
  chunkSize: number;
  totalChunks: number;
  md5Hash: string;
}

export interface ChunkUploadResponse {
  uploaded: boolean;
  needUpload: boolean;
  uploadUrl?: string;
}

// Metadata Analysis Types
export interface MetadataAnalysis {
  id: string;
  fileId: string;
  analysisType: string;
  status: 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED';
  result?: MetadataResult;
  createdTime: string;
  completedTime?: string;
  errorMessage?: string;
}

export interface MetadataResult {
  exifData?: Record<string, any>;
  fileHeaders?: Record<string, any>;
  hashData?: {
    md5?: string;
    sha256?: string;
  };
  technicalData?: {
    compression?: string;
    colorSpace?: string;
    resolution?: string;
    bitDepth?: string;
  };
  suspicious?: {
    hasAnomalies: boolean;
    anomalies: string[];
    riskScore: number;
  };
}

// UI Component Types
export interface TableColumn<T = any> {
  key: string;
  title: string;
  dataIndex?: keyof T;
  render?: (value: any, record: T, index: number) => React.ReactNode;
  sorter?: boolean;
  width?: number;
}

export interface UploadProgress {
  fileId: string;
  fileName: string;
  progress: number;
  status: 'uploading' | 'success' | 'error';
  errorMessage?: string;
}
