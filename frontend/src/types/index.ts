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
