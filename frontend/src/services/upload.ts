import httpClient from './http';
import { API_ENDPOINTS, API_BASE_URL, CHUNK_SIZE } from '../constants';
import type { ApiResponse } from '../types';
import SparkMD5 from 'spark-md5';

export interface UploadProgressDTO {
  success: boolean;
  message?: string;
  fileMd5: string;
  fileName?: string;
  uploadedChunks?: number;
  totalChunks?: number;
  uploadProgress?: number; // 0-100
  missingChunks?: number[];
  uploadStatus?: string; // UPLOADING|COMPLETED|FAILED
  fileId?: number;
}

class UploadService {
  // Calculate MD5 (chunked to avoid large memory)
  async calculateMD5(file: File): Promise<string> {
    return new Promise((resolve, reject) => {
      const chunkSize = 2 * 1024 * 1024; // 2MB
      const chunks = Math.ceil(file.size / chunkSize);
      let currentChunk = 0;
      const spark = new SparkMD5.ArrayBuffer();
      const fileReader = new FileReader();

      fileReader.onload = (e) => {
        spark.append(e.target?.result as ArrayBuffer);
        currentChunk++;
        if (currentChunk < chunks) {
          loadNext();
        } else {
          resolve(spark.end());
        }
      };
      fileReader.onerror = () => reject(new Error('Failed to read file for MD5'));

      function loadNext() {
        const start = currentChunk * chunkSize;
        const end = Math.min(start + chunkSize, file.size);
        fileReader.readAsArrayBuffer(file.slice(start, end));
      }
      loadNext();
    });
  }

  splitFileIntoChunks(file: File, chunkSize: number = CHUNK_SIZE): Blob[] {
    const chunks: Blob[] = [];
    let start = 0;
    while (start < file.size) {
      const end = Math.min(start + chunkSize, file.size);
      chunks.push(file.slice(start, end));
      start = end;
    }
    return chunks;
  }

  // POST /upload/chunk
  async uploadChunk(params: {
    fileMd5: string;
    fileName: string;
    chunkIndex: number;
    totalChunks: number;
    totalSize: number;
    file: Blob;
    projectId: number;
    chunkMd5?: string;
    uploadedBy?: string;
  }, onProgress?: (progress: number) => void): Promise<UploadProgressDTO> {
    const form = new FormData();
    form.append('fileMd5', params.fileMd5);
    form.append('fileName', params.fileName);
    form.append('chunkIndex', String(params.chunkIndex));
    form.append('totalChunks', String(params.totalChunks));
    form.append('totalSize', String(params.totalSize));
    form.append('projectId', String(params.projectId));
    if (params.chunkMd5) form.append('chunkMd5', params.chunkMd5);
    if (params.uploadedBy) form.append('uploadedBy', params.uploadedBy);
    form.append('file', params.file);

    const res = await httpClient.post<ApiResponse<UploadProgressDTO>>(
      API_ENDPOINTS.UPLOAD_CHUNK,
      form,
      {
        headers: { 'Content-Type': 'multipart/form-data' },
        onUploadProgress: (evt) => {
          if (onProgress && evt.total) {
            onProgress(Math.round((evt.loaded * 100) / evt.total));
          }
        },
      }
    );
    // backend may return plain DTO or wrapped Result<DTO>
    return (res.data?.data as any) || (res.data as any);
  }

  // GET /upload/progress/{fileMd5}
  async getProgress(fileMd5: string): Promise<UploadProgressDTO> {
    const res = await httpClient.get<ApiResponse<UploadProgressDTO>>(
      `${API_ENDPOINTS.UPLOAD_PROGRESS}/${fileMd5}`
    );
    return (res.data?.data as any) || (res.data as any);
  }

  // GET /upload/check/{fileMd5}
  async check(fileMd5: string): Promise<any> {
    const res = await httpClient.get<ApiResponse<any>>(
      `${API_ENDPOINTS.UPLOAD_CHECK}/${fileMd5}`
    );
    return (res.data?.data as any) || (res.data as any);
  }

  // GET /upload/supported-types
  async getSupportedTypes(): Promise<any> {
    const res = await httpClient.get<ApiResponse<any>>(
      API_ENDPOINTS.UPLOAD_SUPPORTED_TYPES
    );
    return (res.data?.data as any) || (res.data as any);
  }

  // POST /upload/validate
  async validateFile(fileName: string, file?: File): Promise<any> {
    const form = new FormData();
    form.append('fileName', fileName);
    if (file) form.append('file', file);
    const res = await httpClient.post<ApiResponse<any>>(
      API_ENDPOINTS.UPLOAD_VALIDATE,
      form,
      { headers: { 'Content-Type': 'multipart/form-data' } }
    );
    return (res.data?.data as any) || (res.data as any);
  }

  // GET /upload/files
  async getFiles(page: number = 1, pageSize: number = 20, status?: string, type?: string): Promise<{
    files: any[],
    total: number,
    current: number,
    pageSize: number
  }> {
    const params: Record<string, string | number> = {
      page: page - 1, // Backend uses 0-based pagination
      size: pageSize
    };
    
    if (status) params.status = status;
    if (type) params.type = type;

    const res = await httpClient.get<ApiResponse<any>>(API_ENDPOINTS.UPLOAD_FILES, { params });
    const data = (res.data?.data as any) || (res.data as any);
    
    return {
      files: data.files || [],
      total: data.total || 0,
      current: data.current || page,
      pageSize: data.pageSize || pageSize
    };
  }

  // DELETE /upload/files/{fileId}
  async deleteFile(fileId: string): Promise<boolean> {
    try {
      const res = await httpClient.delete<ApiResponse<any>>(`${API_ENDPOINTS.UPLOAD_DELETE}/${fileId}`);
      const data = (res.data?.data as any) || (res.data as any);
      return data.success === true;
    } catch (error) {
      console.error('Failed to delete file:', error);
      return false;
    }
  }

  // Get file preview URL
  getPreviewUrl(fileId: string): string {
    return `${API_BASE_URL}${API_ENDPOINTS.UPLOAD_PREVIEW}/${fileId}/preview`;
  }

  // Get file thumbnail URL
  getThumbnailUrl(fileId: string): string {
    return `${API_BASE_URL}${API_ENDPOINTS.UPLOAD_THUMBNAIL}/${fileId}/thumbnail`;
  }

  // High-level upload that matches backend contract
  async uploadFileWithChunks(
    file: File,
    projectId: number,
    onProgress?: (progress: number) => void,
    onChunkProgress?: (chunkIndex: number, progress: number) => void
  ): Promise<UploadProgressDTO> {
    const fileMd5 = await this.calculateMD5(file);
    const chunks = this.splitFileIntoChunks(file);

    // optional: check existing
    try { await this.check(fileMd5); } catch (_) {}

    for (let i = 0; i < chunks.length; i++) {
      const chunk = chunks[i];
      // compute chunk md5 (small chunk, ok)
      const chunkMd5 = await new Promise<string>((resolve, reject) => {
        const reader = new FileReader();
        reader.onload = (e) => {
          const md5 = SparkMD5.ArrayBuffer.hash(e.target?.result as ArrayBuffer);
          resolve(md5);
        };
        reader.onerror = () => reject(new Error('Failed to read chunk'));
        reader.readAsArrayBuffer(chunk);
      });

      const resp = await this.uploadChunk({
        fileMd5,
        fileName: file.name,
        chunkIndex: i,
        totalChunks: chunks.length,
        totalSize: file.size,
        file: chunk,
        projectId,
        chunkMd5,
      }, (p) => onChunkProgress && onChunkProgress(i, p));

      if (resp.uploadProgress != null && onProgress) {
        onProgress(Math.round(resp.uploadProgress));
      }

      if (resp.uploadStatus === 'COMPLETED') {
        return resp;
      }
    }

    // final progress query
    return await this.getProgress(fileMd5);
  }
}

export default new UploadService();
