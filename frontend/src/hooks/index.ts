import { useState, useEffect, useCallback, useRef } from 'react';
import { message } from 'antd';
import uploadService from '../services/upload';
import metadataService from '../services/metadata';
import type { UploadFile, MetadataAnalysis, UploadProgress } from '../types';
import { generateId } from '../utils';

/**
 * Hook for managing file uploads with chunk support
 */
export const useFileUpload = () => {
  const [uploadProgress, setUploadProgress] = useState<Map<string, UploadProgress>>(new Map());
  const [isUploading, setIsUploading] = useState(false);

  const uploadFile = useCallback(async (file: File): Promise<UploadFile | null> => {
    const uploadId = generateId();
    const progressInfo: UploadProgress = {
      fileId: uploadId,
      fileName: file.name,
      progress: 0,
      status: 'uploading',
    };

    setUploadProgress(prev => new Map(prev).set(uploadId, progressInfo));
    setIsUploading(true);

    try {
      const result = await uploadService.uploadFileWithChunks(
        file,
        (progress) => {
          setUploadProgress(prev => {
            const newMap = new Map(prev);
            const current = newMap.get(uploadId);
            if (current) {
              newMap.set(uploadId, { ...current, progress });
            }
            return newMap;
          });
        }
      );

      setUploadProgress(prev => {
        const newMap = new Map(prev);
        newMap.set(uploadId, {
          ...progressInfo,
          progress: 100,
          status: 'success',
          fileId: result.id,
        });
        return newMap;
      });

      message.success(`File "${file.name}" uploaded successfully`);
      return result;
    } catch (error) {
      const errorMessage = error instanceof Error ? error.message : 'Upload failed';
      
      setUploadProgress(prev => {
        const newMap = new Map(prev);
        newMap.set(uploadId, {
          ...progressInfo,
          status: 'error',
          errorMessage,
        });
        return newMap;
      });

      message.error(`Upload failed: ${errorMessage}`);
      return null;
    } finally {
      setIsUploading(false);
    }
  }, []);

  const clearProgress = useCallback((uploadId: string) => {
    setUploadProgress(prev => {
      const newMap = new Map(prev);
      newMap.delete(uploadId);
      return newMap;
    });
  }, []);

  const clearAllProgress = useCallback(() => {
    setUploadProgress(new Map());
  }, []);

  return {
    uploadProgress: Array.from(uploadProgress.values()),
    isUploading,
    uploadFile,
    clearProgress,
    clearAllProgress,
  };
};

/**
 * Hook for managing uploaded files list
 */
export const useFilesList = (pageSize: number = 20) => {
  const [files, setFiles] = useState<UploadFile[]>([]);
  const [loading, setLoading] = useState(false);
  const [pagination, setPagination] = useState({
    current: 1,
    pageSize,
    total: 0,
  });

  const loadFiles = useCallback(async (page: number = 1, size: number = pageSize, fileType?: string) => {
    setLoading(true);
    try {
      const response = await uploadService.getFilesList(page - 1, size, fileType);
      setFiles(response.content);
      setPagination({
        current: page,
        pageSize: size,
        total: response.totalElements,
      });
    } catch (error) {
      message.error('Failed to load files');
      console.error('Load files error:', error);
    } finally {
      setLoading(false);
    }
  }, [pageSize]);

  const deleteFile = useCallback(async (fileId: string) => {
    try {
      await uploadService.deleteFile(fileId);
      message.success('File deleted successfully');
      // Reload current page
      loadFiles(pagination.current, pagination.pageSize);
    } catch (error) {
      message.error('Failed to delete file');
      console.error('Delete file error:', error);
    }
  }, [loadFiles, pagination.current, pagination.pageSize]);

  const refreshFiles = useCallback(() => {
    loadFiles(pagination.current, pagination.pageSize);
  }, [loadFiles, pagination.current, pagination.pageSize]);

  useEffect(() => {
    loadFiles();
  }, [loadFiles]);

  return {
    files,
    loading,
    pagination,
    loadFiles,
    deleteFile,
    refreshFiles,
  };
};

/**
 * Hook for managing metadata analysis
 */
export const useMetadataAnalysis = () => {
  const [analyses, setAnalyses] = useState<MetadataAnalysis[]>([]);
  const [loading, setLoading] = useState(false);

  const analyzeFile = useCallback(async (fileId: string, analysisType: 'EXIF' | 'HEADER' | 'HASH' | 'FULL' = 'FULL') => {
    try {
      setLoading(true);
      const result = await metadataService.analyzeFile({ fileId, analysisType });
      message.success('Analysis started successfully');
      return result;
    } catch (error) {
      message.error('Failed to start analysis');
      console.error('Analysis error:', error);
      throw error;
    } finally {
      setLoading(false);
    }
  }, []);

  const loadAnalyses = useCallback(async (fileId?: string) => {
    try {
      setLoading(true);
      const response = await metadataService.getAnalysisList(0, 100, fileId);
      setAnalyses(response.content);
    } catch (error) {
      message.error('Failed to load analyses');
      console.error('Load analyses error:', error);
    } finally {
      setLoading(false);
    }
  }, []);

  const deleteAnalysis = useCallback(async (analysisId: string) => {
    try {
      await metadataService.deleteAnalysis(analysisId);
      message.success('Analysis deleted successfully');
      // Remove from local state
      setAnalyses(prev => prev.filter(analysis => analysis.id !== analysisId));
    } catch (error) {
      message.error('Failed to delete analysis');
      console.error('Delete analysis error:', error);
    }
  }, []);

  return {
    analyses,
    loading,
    analyzeFile,
    loadAnalyses,
    deleteAnalysis,
  };
};

/**
 * Hook for handling API loading states
 */
export const useLoading = (initialState: boolean = false) => {
  const [loading, setLoading] = useState(initialState);
  const [error, setError] = useState<string | null>(null);

  const withLoading = useCallback(async <T>(
    asyncFunction: () => Promise<T>
  ): Promise<T | null> => {
    setLoading(true);
    setError(null);
    
    try {
      const result = await asyncFunction();
      return result;
    } catch (err) {
      const errorMessage = err instanceof Error ? err.message : 'An error occurred';
      setError(errorMessage);
      console.error('API Error:', err);
      return null;
    } finally {
      setLoading(false);
    }
  }, []);

  return { loading, error, withLoading, setLoading, setError };
};

/**
 * Hook for debounced values
 */
export const useDebounce = <T>(value: T, delay: number): T => {
  const [debouncedValue, setDebouncedValue] = useState<T>(value);

  useEffect(() => {
    const handler = setTimeout(() => {
      setDebouncedValue(value);
    }, delay);

    return () => {
      clearTimeout(handler);
    };
  }, [value, delay]);

  return debouncedValue;
};

/**
 * Hook for handling previous values
 */
export const usePrevious = <T>(value: T): T | undefined => {
  const ref = useRef<T>();
  
  useEffect(() => {
    ref.current = value;
  });
  
  return ref.current;
};

/**
 * Hook for local storage state management
 */
export const useLocalStorage = <T>(
  key: string,
  initialValue: T
): [T, (value: T | ((val: T) => T)) => void] => {
  const [storedValue, setStoredValue] = useState<T>(() => {
    try {
      const item = window.localStorage.getItem(key);
      return item ? JSON.parse(item) : initialValue;
    } catch (error) {
      console.error(`Error reading localStorage key "${key}":`, error);
      return initialValue;
    }
  });

  const setValue = useCallback((value: T | ((val: T) => T)) => {
    try {
      const valueToStore = value instanceof Function ? value(storedValue) : value;
      setStoredValue(valueToStore);
      window.localStorage.setItem(key, JSON.stringify(valueToStore));
    } catch (error) {
      console.error(`Error setting localStorage key "${key}":`, error);
    }
  }, [key, storedValue]);

  return [storedValue, setValue];
};
