import httpClient from './http';
import { API_ENDPOINTS, CHUNK_SIZE } from '../constants';
import type { 
  UploadFile, 
  ChunkUploadRequest, 
  ChunkUploadResponse,
  ApiResponse,
  PaginationResponse 
} from '../types';

export interface InitUploadRequest {
  fileName: string;
  fileSize: number;
  fileType: string;
  md5Hash: string;
  chunkSize: number;
  totalChunks: number;
}

export interface InitUploadResponse {
  fileId: string;
  needUpload: boolean;
  uploadedChunks: number[];
}

export interface CompleteUploadRequest {
  fileId: string;
  fileName: string;
  fileSize: number;
  md5Hash: string;
}

class UploadService {
  /**
   * Initialize file upload
   */
  async initUpload(request: InitUploadRequest): Promise<InitUploadResponse> {
    const response = await httpClient.post<ApiResponse<InitUploadResponse>>(
      API_ENDPOINTS.UPLOAD_INIT,
      request
    );
    return response.data.data;
  }

  /**
   * Upload file chunk
   */
  async uploadChunk(
    fileId: string,
    chunkIndex: number,
    chunk: Blob,
    onProgress?: (progress: number) => void
  ): Promise<ChunkUploadResponse> {
    const formData = new FormData();
    formData.append('fileId', fileId);
    formData.append('chunkIndex', chunkIndex.toString());
    formData.append('chunk', chunk);

    const response = await httpClient.post<ApiResponse<ChunkUploadResponse>>(
      API_ENDPOINTS.UPLOAD_CHUNK,
      formData,
      {
        headers: {
          'Content-Type': 'multipart/form-data',
        },
        onUploadProgress: (progressEvent) => {
          if (onProgress && progressEvent.total) {
            const progress = Math.round((progressEvent.loaded * 100) / progressEvent.total);
            onProgress(progress);
          }
        },
      }
    );
    return response.data.data;
  }

  /**
   * Complete file upload
   */
  async completeUpload(request: CompleteUploadRequest): Promise<UploadFile> {
    const response = await httpClient.post<ApiResponse<UploadFile>>(
      API_ENDPOINTS.UPLOAD_COMPLETE,
      request
    );
    return response.data.data;
  }

  /**
   * Get uploaded files list
   */
  async getFilesList(
    page: number = 0,
    size: number = 20,
    fileType?: string
  ): Promise<PaginationResponse<UploadFile>> {
    const params = new URLSearchParams({
      page: page.toString(),
      size: size.toString(),
    });

    if (fileType) {
      params.append('fileType', fileType);
    }

    const response = await httpClient.get<ApiResponse<PaginationResponse<UploadFile>>>(
      `${API_ENDPOINTS.UPLOAD_LIST}?${params}`
    );
    return response.data.data;
  }

  /**
   * Delete uploaded file
   */
  async deleteFile(fileId: string): Promise<boolean> {
    const response = await httpClient.delete<ApiResponse<boolean>>(
      `${API_ENDPOINTS.UPLOAD_DELETE}/${fileId}`
    );
    return response.data.data;
  }

  /**
   * Calculate file MD5 hash
   */
  async calculateMD5(file: File): Promise<string> {
    return new Promise((resolve, reject) => {
      const crypto = window.crypto;
      if (!crypto || !crypto.subtle) {
        reject(new Error('Web Crypto API not supported'));
        return;
      }

      const reader = new FileReader();
      reader.onload = async (e) => {
        try {
          const arrayBuffer = e.target?.result as ArrayBuffer;
          const hashBuffer = await crypto.subtle.digest('SHA-256', arrayBuffer);
          const hashArray = Array.from(new Uint8Array(hashBuffer));
          const hashHex = hashArray.map(b => b.toString(16).padStart(2, '0')).join('');
          resolve(hashHex);
        } catch (error) {
          reject(error);
        }
      };
      reader.onerror = () => reject(new Error('Failed to read file'));
      reader.readAsArrayBuffer(file);
    });
  }

  /**
   * Split file into chunks
   */
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

  /**
   * Upload file with chunked upload support
   */
  async uploadFileWithChunks(
    file: File,
    onProgress?: (progress: number) => void,
    onChunkProgress?: (chunkIndex: number, progress: number) => void
  ): Promise<UploadFile> {
    try {
      // Calculate MD5 hash
      const md5Hash = await this.calculateMD5(file);
      
      // Split file into chunks
      const chunks = this.splitFileIntoChunks(file);
      const totalChunks = chunks.length;

      // Initialize upload
      const initResponse = await this.initUpload({
        fileName: file.name,
        fileSize: file.size,
        fileType: file.type,
        md5Hash,
        chunkSize: CHUNK_SIZE,
        totalChunks,
      });

      if (!initResponse.needUpload) {
        // File already exists, complete upload directly
        return await this.completeUpload({
          fileId: initResponse.fileId,
          fileName: file.name,
          fileSize: file.size,
          md5Hash,
        });
      }

      // Upload chunks
      const uploadedChunks = initResponse.uploadedChunks || [];
      let uploadedCount = uploadedChunks.length;

      for (let i = 0; i < chunks.length; i++) {
        if (uploadedChunks.includes(i)) {
          // Chunk already uploaded
          continue;
        }

        await this.uploadChunk(
          initResponse.fileId,
          i,
          chunks[i],
          (chunkProgress) => {
            if (onChunkProgress) {
              onChunkProgress(i, chunkProgress);
            }
          }
        );

        uploadedCount++;
        
        if (onProgress) {
          const totalProgress = Math.round((uploadedCount * 100) / totalChunks);
          onProgress(totalProgress);
        }
      }

      // Complete upload
      return await this.completeUpload({
        fileId: initResponse.fileId,
        fileName: file.name,
        fileSize: file.size,
        md5Hash,
      });
    } catch (error) {
      console.error('Upload failed:', error);
      throw error;
    }
  }
}

export default new UploadService();
