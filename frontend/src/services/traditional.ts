import httpClient from './http';
import type { TraditionalAnalysisResult } from '../types';

// Traditional analysis API
export const traditionalAnalysisAPI = {
  // Get traditional analysis result by file MD5
  getAnalysisResult: async (fileMd5: string): Promise<TraditionalAnalysisResult | null> => {
    try {
      const response = await httpClient.get(`/traditional-analysis/result/${fileMd5}`);
      return response.data?.success ? response.data.data : null;
    } catch (error) {
      console.error('Failed to get traditional analysis result:', error);
      return null;
    }
  },

  // Get analysis status for a file
  getAnalysisStatus: async (fileMd5: string): Promise<string> => {
    try {
      const response = await httpClient.get(`/traditional-analysis/status/${fileMd5}`);
      return response.data?.success ? response.data.data : 'NOT_FOUND';
    } catch (error) {
      console.error('Failed to get traditional analysis status:', error);
      return 'ERROR';
    }
  },

  // Get analysis summary for a file (lightweight version)
  getAnalysisSummary: async (fileMd5: string): Promise<any> => {
    try {
      const response = await httpClient.get(`/traditional-analysis/summary/${fileMd5}`);
      return response.data?.success ? response.data.data : null;
    } catch (error) {
      console.error('Failed to get traditional analysis summary:', error);
      return null;
    }
  },

  // Get analysis results for a project with pagination
  getProjectAnalysisResults: async (
    projectId: number,
    page: number = 0,
    size: number = 10
  ): Promise<any> => {
    try {
      const response = await httpClient.get(`/traditional-analysis/project/${projectId}`, {
        params: { page, size }
      });
      return response.data?.success ? response.data.data : null;
    } catch (error) {
      console.error('Failed to get project traditional analysis results:', error);
      return null;
    }
  },

  // Trigger traditional analysis for a file
  triggerAnalysis: async (fileMd5: string, force: boolean = false): Promise<{ success: boolean; message: string }> => {
    try {
      const response = await httpClient.post(`/traditional-analysis/trigger/${fileMd5}`, null, {
        params: { force }
      });
      return {
        success: response.data?.success || false,
        message: response.data?.message || response.data?.data || 'Unknown error'
      };
    } catch (error: any) {
      console.error('Failed to trigger traditional analysis:', error);
      return {
        success: false,
        message: error.response?.data?.message || 'Failed to trigger analysis'
      };
    }
  }
};
