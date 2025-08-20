import { useState, useCallback, useRef, useEffect } from 'react';
import { message } from 'antd';
import uploadService from '../services/upload';
import SparkMD5 from 'spark-md5';

export interface EnhancedUploadProgress {
  fileId: string;
  fileName: string;
  progress: number;
  status: 'uploading' | 'paused' | 'completed' | 'failed' | 'cancelled';
  chunkProgress: Map<number, number>;
  uploadedChunks: number;
  totalChunks: number;
  missingChunks: number[];
  retryCount: number;
  errorMessage?: string;
  canPause: boolean;
  canResume: boolean;
  canCancel: boolean;
}

export interface UploadConfig {
  maxRetries: number;
  retryDelay: number;
  enablePauseResume: boolean;
  chunkSize: number;
  concurrentChunks: number;
}

const defaultConfig: UploadConfig = {
  maxRetries: 3,
  retryDelay: 2000,
  enablePauseResume: true,
  chunkSize: 2 * 1024 * 1024, // 2MB
  concurrentChunks: 3,
};

/**
 * Enhanced upload hook with retry mechanism and pause/resume support
 */
export const useEnhancedFileUpload = (config: Partial<UploadConfig> = {}) => {
  const finalConfig = { ...defaultConfig, ...config };
  
  const [uploads, setUploads] = useState<Map<string, EnhancedUploadProgress>>(new Map());
  const [isUploading, setIsUploading] = useState(false);
  
  // Track upload controllers for each file
  const uploadControllers = useRef<Map<string, {
    abortController: AbortController;
    isPaused: boolean;
    retryTimeouts: Set<number>;
  }>>(new Map());

  const updateUploadProgress = useCallback((fileId: string, updates: Partial<EnhancedUploadProgress>) => {
    setUploads(prev => {
      const newMap = new Map(prev);
      const current = newMap.get(fileId);
      if (current) {
        newMap.set(fileId, { ...current, ...updates });
      }
      return newMap;
    });
  }, []);

  const uploadChunkWithRetry = useCallback(async (
    fileId: string,
    chunk: Blob,
    chunkIndex: number,
    chunkMd5: string,
    uploadParams: any,
    retryCount = 0
  ): Promise<boolean> => {
    const controller = uploadControllers.current.get(fileId);
    if (!controller || controller.abortController.signal.aborted) {
      return false;
    }

    // Check if paused
    if (controller.isPaused) {
      updateUploadProgress(fileId, { status: 'paused' });
      return false;
    }

    try {
      const response = await uploadService.uploadChunk({
        ...uploadParams,
        chunkIndex,
        chunkMd5,
        file: chunk,
      }, (progress) => {
        // Update chunk progress
        updateUploadProgress(fileId, {
          chunkProgress: new Map([[chunkIndex, progress]])
        });
      });

      if (response.success) {
        // Update overall progress
        updateUploadProgress(fileId, {
          uploadedChunks: response.uploadedChunks || 0,
          progress: response.uploadProgress || 0,
          missingChunks: response.missingChunks || [],
        });

        return true;
      } else {
        throw new Error(response.message || 'Upload failed');
      }

    } catch (error) {
      const errorMessage = error instanceof Error ? error.message : 'Unknown error';
      
      if (retryCount < finalConfig.maxRetries) {
        // Schedule retry
        const timeout = setTimeout(() => {
          uploadChunkWithRetry(fileId, chunk, chunkIndex, chunkMd5, uploadParams, retryCount + 1);
        }, finalConfig.retryDelay * (retryCount + 1)); // Exponential backoff

        // Track timeout for cleanup
        controller.retryTimeouts.add(timeout);
        
        updateUploadProgress(fileId, {
          retryCount: retryCount + 1,
          errorMessage: `Retrying chunk ${chunkIndex} (${retryCount + 1}/${finalConfig.maxRetries}): ${errorMessage}`
        });

        return false;
      } else {
        // Max retries reached
        updateUploadProgress(fileId, {
          status: 'failed',
          errorMessage: `Chunk ${chunkIndex} failed after ${finalConfig.maxRetries} attempts: ${errorMessage}`
        });
        return false;
      }
    }
  }, [finalConfig.maxRetries, finalConfig.retryDelay, updateUploadProgress]);

  const uploadFile = useCallback(async (file: File, projectId: number): Promise<string | null> => {
    const fileId = `${file.name}_${Date.now()}`;
    const fileMd5 = await uploadService.calculateMD5(file);
    const chunks = uploadService.splitFileIntoChunks(file, finalConfig.chunkSize);

    // Initialize upload progress
    const initialProgress: EnhancedUploadProgress = {
      fileId,
      fileName: file.name,
      progress: 0,
      status: 'uploading',
      chunkProgress: new Map(),
      uploadedChunks: 0,
      totalChunks: chunks.length,
      missingChunks: Array.from({ length: chunks.length }, (_, i) => i),
      retryCount: 0,
      canPause: finalConfig.enablePauseResume,
      canResume: false,
      canCancel: true,
    };

    setUploads(prev => new Map(prev).set(fileId, initialProgress));

    // Initialize upload controller
    const abortController = new AbortController();
    uploadControllers.current.set(fileId, {
      abortController,
      isPaused: false,
      retryTimeouts: new Set(),
    });

    setIsUploading(true);

    try {
      // Check if file already exists
      try {
        await uploadService.check(fileMd5);
      } catch (e) {
        // File doesn't exist, continue with upload
      }

      const uploadParams = {
        fileMd5,
        fileName: file.name,
        totalChunks: chunks.length,
        totalSize: file.size,
        projectId,
      };

      // Upload chunks with concurrency control
      const semaphore = Array(finalConfig.concurrentChunks).fill(null).map(() => Promise.resolve(true));
      let semaphoreIndex = 0;

      const uploadPromises = chunks.map(async (chunk, index) => {
        // Wait for available slot
        await semaphore[semaphoreIndex];

        const chunkMd5 = await new Promise<string>((resolve, reject) => {
          const reader = new FileReader();
          reader.onload = (e) => {
            const md5 = SparkMD5.ArrayBuffer.hash(e.target?.result as ArrayBuffer);
            resolve(md5);
          };
          reader.onerror = () => reject(new Error('Failed to calculate chunk MD5'));
          reader.readAsArrayBuffer(chunk);
        });

        // Create promise for this slot
        const slotPromise = uploadChunkWithRetry(fileId, chunk, index, chunkMd5, uploadParams);
        semaphore[semaphoreIndex] = slotPromise;
        semaphoreIndex = (semaphoreIndex + 1) % finalConfig.concurrentChunks;

        return slotPromise;
      });

      // Wait for all chunks to complete
      const results = await Promise.all(uploadPromises);
      const allSuccessful = results.every(result => result);

      if (allSuccessful) {
        updateUploadProgress(fileId, {
          status: 'completed',
          progress: 100,
        });
        message.success(`File "${file.name}" uploaded successfully`);
        return fileId;
      } else {
        updateUploadProgress(fileId, {
          status: 'failed',
          errorMessage: 'Some chunks failed to upload',
        });
        message.error(`Upload failed for file "${file.name}"`);
        return null;
      }

    } catch (error) {
      const errorMessage = error instanceof Error ? error.message : 'Upload failed';
      updateUploadProgress(fileId, {
        status: 'failed',
        errorMessage,
      });
      message.error(`Upload failed: ${errorMessage}`);
      return null;
    } finally {
      setIsUploading(false);
      
      // Cleanup
      const controller = uploadControllers.current.get(fileId);
      if (controller) {
        controller.retryTimeouts.forEach(timeout => clearTimeout(timeout));
        uploadControllers.current.delete(fileId);
      }
    }
  }, [finalConfig, uploadChunkWithRetry, updateUploadProgress]);

  const pauseUpload = useCallback((fileId: string) => {
    const controller = uploadControllers.current.get(fileId);
    if (controller) {
      controller.isPaused = true;
      updateUploadProgress(fileId, {
        status: 'paused',
        canPause: false,
        canResume: true,
      });
      message.info('Upload paused');
    }
  }, [updateUploadProgress]);

  const resumeUpload = useCallback((fileId: string) => {
    const controller = uploadControllers.current.get(fileId);
    if (controller) {
      controller.isPaused = false;
      updateUploadProgress(fileId, {
        status: 'uploading',
        canPause: true,
        canResume: false,
      });
      message.info('Upload resumed');
      
      // TODO: Resume failed chunks
      // This would require re-implementing the upload logic for missing chunks
    }
  }, [updateUploadProgress]);

  const cancelUpload = useCallback((fileId: string) => {
    const controller = uploadControllers.current.get(fileId);
    if (controller) {
      controller.abortController.abort();
      controller.retryTimeouts.forEach(timeout => clearTimeout(timeout));
      uploadControllers.current.delete(fileId);
      
      updateUploadProgress(fileId, {
        status: 'cancelled',
        canPause: false,
        canResume: false,
        canCancel: false,
      });
      
      setUploads(prev => {
        const newMap = new Map(prev);
        newMap.delete(fileId);
        return newMap;
      });
      
      message.info('Upload cancelled');
    }
  }, [updateUploadProgress]);

  const clearUpload = useCallback((fileId: string) => {
    setUploads(prev => {
      const newMap = new Map(prev);
      newMap.delete(fileId);
      return newMap;
    });
  }, []);

  const clearAllUploads = useCallback(() => {
    setUploads(new Map());
  }, []);

  // Cleanup on unmount
  useEffect(() => {
    return () => {
      uploadControllers.current.forEach(controller => {
        controller.abortController.abort();
        controller.retryTimeouts.forEach(timeout => clearTimeout(timeout));
      });
      uploadControllers.current.clear();
    };
  }, []);

  return {
    uploads: Array.from(uploads.values()),
    isUploading,
    uploadFile,
    pauseUpload,
    resumeUpload,
    cancelUpload,
    clearUpload,
    clearAllUploads,
  };
};
