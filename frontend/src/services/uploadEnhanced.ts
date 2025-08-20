import httpClient from './http';
import { API_BASE_URL } from '../constants';
import uploadService from './upload';
import type { UploadProgressDTO } from './upload';
import SparkMD5 from 'spark-md5';

export interface EnhancedUploadProgressDTO extends UploadProgressDTO {
  uploadStatus?: 'UPLOADING' | 'PAUSED' | 'COMPLETED' | 'FAILED' | 'CANCELLED';
  retryCount?: number;
  failedChunks?: number[];
}

class UploadServiceEnhanced {
  
  /**
   * Upload chunk with automatic retry support
   */
  async uploadChunkWithRetry(params: {
    fileMd5: string;
    fileName: string;
    chunkIndex: number;
    totalChunks: number;
    totalSize: number;
    file: Blob;
    projectId: number;
    chunkMd5?: string;
    uploadedBy?: string;
  }, onProgress?: (progress: number) => void): Promise<EnhancedUploadProgressDTO> {
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

    const res = await httpClient.post<any>(
      `${API_BASE_URL}/upload-enhanced/chunk`,
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
    
    return (res.data?.data as any) || (res.data as any);
  }

  /**
   * Get enhanced upload progress
   */
  async getEnhancedProgress(fileMd5: string): Promise<EnhancedUploadProgressDTO> {
    const res = await httpClient.get<any>(`${API_BASE_URL}/upload-enhanced/progress/${fileMd5}`);
    return (res.data?.data as any) || (res.data as any);
  }

  /**
   * Pause upload
   */
  async pauseUpload(fileMd5: string): Promise<{ success: boolean; message: string }> {
    const res = await httpClient.post<any>(`${API_BASE_URL}/upload-enhanced/pause/${fileMd5}`);
    return (res.data?.data as any) || (res.data as any);
  }

  /**
   * Resume upload
   */
  async resumeUpload(fileMd5: string): Promise<{ success: boolean; message: string }> {
    const res = await httpClient.post<any>(`${API_BASE_URL}/upload-enhanced/resume/${fileMd5}`);
    return (res.data?.data as any) || (res.data as any);
  }

  /**
   * Cancel upload
   */
  async cancelUpload(fileMd5: string): Promise<{ success: boolean; message: string }> {
    const res = await httpClient.post<any>(`${API_BASE_URL}/upload-enhanced/cancel/${fileMd5}`);
    return (res.data?.data as any) || (res.data as any);
  }

  /**
   * Retry failed chunks
   */
  async retryFailedChunks(fileMd5: string): Promise<{ success: boolean; message: string }> {
    const res = await httpClient.post<any>(`${API_BASE_URL}/upload-enhanced/retry/${fileMd5}`);
    return (res.data?.data as any) || (res.data as any);
  }

  /**
   * Enhanced file upload with full retry and pause/resume support
   */
  async uploadFileWithEnhancements(
    file: File,
    projectId: number,
    options: {
      enableRetry?: boolean;
      enablePauseResume?: boolean;
      maxRetries?: number;
      onProgress?: (progress: number) => void;
      onChunkProgress?: (chunkIndex: number, progress: number) => void;
      onStatusChange?: (status: string) => void;
    } = {}
  ): Promise<EnhancedUploadProgressDTO> {
    const {
      enableRetry = true,
      enablePauseResume = true,
      maxRetries = 3,
      onProgress,
      onChunkProgress,
      onStatusChange
    } = options;

    const fileMd5 = await uploadService.calculateMD5(file);
    const chunks = uploadService.splitFileIntoChunks(file);

    // Check existing progress
    try {
      const existingProgress = await this.getEnhancedProgress(fileMd5);
      if (existingProgress.uploadStatus === 'COMPLETED') {
        return existingProgress;
      }
      if (existingProgress.uploadStatus === 'PAUSED' && enablePauseResume) {
        onStatusChange?.('PAUSED');
        // Return current progress, let user decide to resume
        return existingProgress;
      }
    } catch (e) {
      // No existing progress, continue with new upload
    }

    // Upload chunks
    for (let i = 0; i < chunks.length; i++) {
      const chunk = chunks[i];
      
      // Check if we should use enhanced upload or regular upload
      const uploadMethod = enableRetry ? 
        this.uploadChunkWithRetry.bind(this) : 
        uploadService.uploadChunk.bind(uploadService);

      // Calculate chunk MD5
      const chunkMd5 = await new Promise<string>((resolve, reject) => {
        const reader = new FileReader();
        reader.onload = (e) => {
          const md5 = SparkMD5.ArrayBuffer.hash(e.target?.result as ArrayBuffer);
          resolve(md5);
        };
        reader.onerror = () => reject(new Error('Failed to read chunk'));
        reader.readAsArrayBuffer(chunk);
      });

      let retryCount = 0;
      let success = false;

      while (!success && retryCount <= maxRetries) {
        try {
          const resp = await uploadMethod({
            fileMd5,
            fileName: file.name,
            chunkIndex: i,
            totalChunks: chunks.length,
            totalSize: file.size,
            file: chunk,
            projectId,
            chunkMd5,
          }, (p) => onChunkProgress?.(i, p));

          if (resp.success || resp.uploadProgress != null) {
            success = true;
            
            // Update overall progress
            if (resp.uploadProgress != null && onProgress) {
              onProgress(Math.round(resp.uploadProgress));
            }

            // Update status
            if (resp.uploadStatus && onStatusChange) {
              onStatusChange(resp.uploadStatus);
            }

            // Check if completed
            if (resp.uploadStatus === 'COMPLETED') {
              return resp as EnhancedUploadProgressDTO;
            }
          } else {
            throw new Error(resp.message || 'Upload failed');
          }

        } catch (error) {
          retryCount++;
          if (retryCount > maxRetries) {
            throw new Error(`Chunk ${i} failed after ${maxRetries} retries: ${error}`);
          }
          
          // Wait before retry
          await new Promise(resolve => setTimeout(resolve, 1000 * retryCount));
        }
      }
    }

    // Final progress check
    return await this.getEnhancedProgress(fileMd5);
  }

  /**
   * Resume upload from where it left off
   */
  async resumeFileUpload(
    fileMd5: string,
    options: {
      onProgress?: (progress: number) => void;
      onChunkProgress?: (chunkIndex: number, progress: number) => void;
      onStatusChange?: (status: string) => void;
    } = {}
  ): Promise<EnhancedUploadProgressDTO> {
    const { onStatusChange } = options;

    // Resume upload
    await this.resumeUpload(fileMd5);
    onStatusChange?.('UPLOADING');

    // Get current progress to find missing chunks
    const progress = await this.getEnhancedProgress(fileMd5);
    
    if (progress.missingChunks && progress.missingChunks.length > 0) {
      // TODO: Implement resumption of specific missing chunks
      // This would require getting the original file and re-uploading missing chunks
      onStatusChange?.('RESUMING');
    }

    return progress;
  }
}

const uploadServiceEnhanced = new UploadServiceEnhanced();
export default uploadServiceEnhanced;
