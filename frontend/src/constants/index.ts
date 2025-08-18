// API endpoints and configuration constants

export const API_BASE_URL = (import.meta as any).env?.VITE_API_BASE_URL || 'http://localhost:8082/api/v1';

export const API_ENDPOINTS = {
  // File Upload (match backend)
  UPLOAD_CHUNK: '/upload/chunk',
  UPLOAD_PROGRESS: '/upload/progress',
  UPLOAD_CHECK: '/upload/check',
  UPLOAD_SUPPORTED_TYPES: '/upload/supported-types',
  UPLOAD_VALIDATE: '/upload/validate',
  UPLOAD_FILES: '/upload/files',
  UPLOAD_DELETE: '/upload/files',
  UPLOAD_PREVIEW: '/upload/files', // GET /{fileId}/preview
  
  // Metadata Analysis (match backend)
  METADATA_ANALYSIS: '/metadata/analysis',
  METADATA_ANALYSIS_START: '/metadata/analysis',  // POST /{fileMd5}/start
  METADATA_ANALYSIS_STATUS: '/metadata/analysis', // GET /{fileMd5}/status
  METADATA_BASIC: '/metadata/basic',
  METADATA_EXIF: '/metadata/exif',
  METADATA_VIDEO: '/metadata/video',
  METADATA_SUSPICIOUS: '/metadata/suspicious',
  METADATA_HASHES: '/metadata/hashes',
} as const;

export const FILE_TYPES = {
  IMAGE: ['jpg', 'jpeg', 'png', 'gif', 'bmp', 'webp', 'tiff'],
  VIDEO: ['mp4', 'avi', 'mov', 'wmv', 'flv', 'webm', 'mkv'],
} as const;

export const SUPPORTED_FILE_EXTENSIONS = [
  ...FILE_TYPES.IMAGE,
  ...FILE_TYPES.VIDEO,
];

export const MAX_FILE_SIZE = 1024 * 1024 * 1024; // 1GB
export const CHUNK_SIZE = 5 * 1024 * 1024; // 5MB

export const UPLOAD_STATUS = {
  UPLOADING: 'UPLOADING',
  COMPLETED: 'COMPLETED',
  FAILED: 'FAILED',
} as const;

export const ANALYSIS_STATUS = {
  PENDING: 'PENDING',
  PROCESSING: 'PROCESSING',
  COMPLETED: 'COMPLETED',
  FAILED: 'FAILED',
} as const;

export const PAGE_SIZE = 20;

export const ROUTES = {
  HOME: '/',
  UPLOAD: '/upload',
  FILES: '/files',
  ANALYSIS: '/analysis',
  SETTINGS: '/settings',
} as const;
