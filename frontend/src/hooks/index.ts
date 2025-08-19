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

  const uploadFile = useCallback(async (file: File) => {
    const tempId = generateId();
    const progressInfo: UploadProgress = {
      fileId: tempId,
      fileName: file.name,
      progress: 0,
      status: 'uploading',
    };

    setUploadProgress(prev => new Map(prev).set(tempId, progressInfo));
    setIsUploading(true);

    try {
      const result = await uploadService.uploadFileWithChunks(
        file,
        (progress) => {
          setUploadProgress(prev => {
            const newMap = new Map(prev);
            const current = newMap.get(tempId);
            if (current) newMap.set(tempId, { ...current, progress });
            return newMap;
          });
        }
      );

      setUploadProgress(prev => {
        const newMap = new Map(prev);
        newMap.set(tempId, { ...progressInfo, progress: 100, status: 'success', fileId: String(result.fileId || result.fileMd5) });
        return newMap;
      });

      message.success(`File "${file.name}" uploaded successfully`);
      // Return a minimal UploadFile-like object for UI compatibility
      const mapped: UploadFile = {
        id: String(result.fileId || result.fileMd5),
        filename: result.fileName || file.name,
        originalName: result.fileName || file.name,
        fileType: '',
        fileSize: file.size,
        filePath: '',
        uploadTime: new Date().toISOString(),
        status: result.uploadStatus === 'COMPLETED' ? 'COMPLETED' : 'UPLOADING',
        chunkTotal: result.totalChunks || undefined,
        chunkUploaded: result.uploadedChunks || undefined,
        md5Hash: result.fileMd5,
      };
      return mapped;
    } catch (error) {
      const errMsg = error instanceof Error ? error.message : 'Upload failed';
      setUploadProgress(prev => {
        const newMap = new Map(prev);
        newMap.set(tempId, { ...progressInfo, status: 'error', errorMessage: errMsg });
        return newMap;
      });
      message.error(`Upload failed: ${errMsg}`);
      return null;
    } finally {
      setIsUploading(false);
    }
  }, []);

  const clearProgress = useCallback((id: string) => {
    setUploadProgress(prev => { const m = new Map(prev); m.delete(id); return m; });
  }, []);

  const clearAllProgress = useCallback(() => setUploadProgress(new Map()), []);

  return { uploadProgress: Array.from(uploadProgress.values()), isUploading, uploadFile, clearProgress, clearAllProgress };
};

/**
 * Hook for managing uploaded files list
 */
export const useFilesList = (pageSize: number = 20) => {
  const [files, setFiles] = useState<UploadFile[]>([]);
  const [loading, setLoading] = useState(false);
  const [pagination, setPagination] = useState({ current: 1, pageSize, total: 0 });

  const loadFiles = useCallback(async (page: number = 1, size: number = pageSize) => {
    setLoading(true);
    try {
      // Call API to get files list
      const result = await uploadService.getFiles(page, size);
      setFiles(result.files || []);
      setPagination({
        current: result.current,
        pageSize: result.pageSize,
        total: result.total
      });
      
      if (result.files.length === 0) {
        message.info('No files found. Please upload files first.');
      }
    } catch (error) {
      message.error('Failed to load files list');
      console.error('Failed to load files:', error);
      setFiles([]);
      setPagination({ current: page, pageSize: size, total: 0 });
    } finally {
      setLoading(false);
    }
  }, [pageSize]);

  const deleteFile = useCallback(async (fileId: string) => {
    try {
      setLoading(true);
      const success = await uploadService.deleteFile(fileId);
      if (success) {
        message.success('File deleted successfully');
        // Refresh the current page
        await loadFiles(pagination.current, pagination.pageSize);
      } else {
        message.warning('Delete functionality not yet implemented');
      }
    } catch (error) {
      message.error('Failed to delete file');
      console.error('Failed to delete file:', error);
    } finally {
      setLoading(false);
    }
  }, [loadFiles, pagination.current, pagination.pageSize]);

  const refreshFiles = useCallback(() => {
    console.log('Refreshing files list...');
    loadFiles(pagination.current, pagination.pageSize);
  }, [loadFiles, pagination.current, pagination.pageSize]);

  useEffect(() => { 
    loadFiles(1, pageSize); 
  }, [loadFiles, pageSize]);

  return { files, loading, pagination, loadFiles, deleteFile, refreshFiles };
};

/**
 * Hook for managing metadata analysis
 */
export const useMetadataAnalysis = () => {
  const [analyses, setAnalyses] = useState<MetadataAnalysis[]>([]);
  const [loading, setLoading] = useState(false);
  const [analysisStatus, setAnalysisStatus] = useState<Record<string, string>>({});

  // Start analysis
  const startAnalysis = useCallback(async (fileMd5: string) => {
    setLoading(true);
    try {
      const result = await metadataService.startAnalysis(fileMd5);
      if (result.success) {
        message.success(result.message || 'Analysis started successfully');
        setAnalysisStatus(prev => ({ ...prev, [fileMd5]: 'PROCESSING' }));
        return true;
      } else {
        message.error(result.message || 'Failed to start analysis');
        return false;
      }
    } catch (error) {
      console.error('Failed to start analysis:', error);
      message.error('Failed to start analysis');
      return false;
    } finally {
      setLoading(false);
    }
  }, []);

  // Get analysis results (existing logic for viewing results)
  const getAnalysis = useCallback(async (fileMd5: string) => {
    setLoading(true);
    try {
      const result = await metadataService.getAnalysis(fileMd5);
      setAnalyses(prev => [result, ...prev.filter(a => a.fileId !== fileMd5)]);
      return result;
    } catch (error) {
      console.error('Failed to get analysis:', error);
      return null;
    } finally {
      setLoading(false);
    }
  }, []);

  // Get analysis status
  const getAnalysisStatus = useCallback(async (fileMd5: string) => {
    try {
      const status = await metadataService.getAnalysisStatus(fileMd5);
      setAnalysisStatus(prev => ({ ...prev, [fileMd5]: status.status }));
      return status;
    } catch (error) {
      console.error('Failed to get analysis status:', error);
      return { hasAnalysis: false, status: 'ERROR', message: 'Failed to get status' };
    }
  }, []);

  // Load analysis results
  const loadAnalyses = useCallback(async (fileMd5?: string) => {
    setLoading(true);
    try {
      if (fileMd5) {
        // First get status
        const status = await getAnalysisStatus(fileMd5);
        
        // If there are completed analysis results, get detailed content
        if (status.hasAnalysis && status.status === 'SUCCESS') {
          const result = await metadataService.getAnalysis(fileMd5);
          setAnalyses([result]);
        } else {
          setAnalyses([]);
        }
      } else {
        setAnalyses([]);
      }
    } finally {
      setLoading(false);
    }
  }, [getAnalysisStatus]);

  const deleteAnalysis = useCallback(async (_analysisId: string) => {
    message.info('Delete analysis API not implemented yet');
  }, []);

  // Compatibility: maintain original analyzeFile method (actually calls start analysis)
  const analyzeFile = useCallback(async (fileMd5: string) => {
    return await startAnalysis(fileMd5);
  }, [startAnalysis]);

  return { 
    analyses, 
    loading, 
    analysisStatus,
    analyzeFile,           // Start analysis (compatibility)
    startAnalysis,         // Explicit start analysis
    getAnalysis,          // Get analysis results
    getAnalysisStatus,    // Get analysis status
    loadAnalyses, 
    deleteAnalysis 
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