import httpClient from './http';
import { API_ENDPOINTS } from '../constants';
import type { 
  MetadataAnalysis, 
  ApiResponse,
  PaginationResponse 
} from '../types';

export interface AnalyzeRequest {
  fileId: string;
  analysisType: 'EXIF' | 'HEADER' | 'HASH' | 'FULL';
}

class MetadataService {
  /**
   * Start metadata analysis for a file
   */
  async analyzeFile(request: AnalyzeRequest): Promise<MetadataAnalysis> {
    const response = await httpClient.post<ApiResponse<MetadataAnalysis>>(
      API_ENDPOINTS.METADATA_ANALYZE,
      request
    );
    return response.data.data;
  }

  /**
   * Get metadata analysis list
   */
  async getAnalysisList(
    page: number = 0,
    size: number = 20,
    fileId?: string,
    status?: string
  ): Promise<PaginationResponse<MetadataAnalysis>> {
    const params = new URLSearchParams({
      page: page.toString(),
      size: size.toString(),
    });

    if (fileId) {
      params.append('fileId', fileId);
    }

    if (status) {
      params.append('status', status);
    }

    const response = await httpClient.get<ApiResponse<PaginationResponse<MetadataAnalysis>>>(
      `${API_ENDPOINTS.METADATA_LIST}?${params}`
    );
    return response.data.data;
  }

  /**
   * Get metadata analysis detail
   */
  async getAnalysisDetail(analysisId: string): Promise<MetadataAnalysis> {
    const response = await httpClient.get<ApiResponse<MetadataAnalysis>>(
      `${API_ENDPOINTS.METADATA_DETAIL}/${analysisId}`
    );
    return response.data.data;
  }

  /**
   * Delete metadata analysis
   */
  async deleteAnalysis(analysisId: string): Promise<boolean> {
    const response = await httpClient.delete<ApiResponse<boolean>>(
      `${API_ENDPOINTS.METADATA_DELETE}/${analysisId}`
    );
    return response.data.data;
  }

  /**
   * Get analyses for a specific file
   */
  async getFileAnalyses(fileId: string): Promise<MetadataAnalysis[]> {
    const response = await this.getAnalysisList(0, 100, fileId);
    return response.content;
  }

  /**
   * Check if analysis is in progress for a file
   */
  async isAnalysisInProgress(fileId: string): Promise<boolean> {
    const analyses = await this.getFileAnalyses(fileId);
    return analyses.some(analysis => 
      analysis.status === 'PENDING' || analysis.status === 'PROCESSING'
    );
  }

  /**
   * Get latest completed analysis for a file
   */
  async getLatestAnalysis(fileId: string): Promise<MetadataAnalysis | null> {
    const analyses = await this.getFileAnalyses(fileId);
    const completedAnalyses = analyses
      .filter(analysis => analysis.status === 'COMPLETED')
      .sort((a, b) => new Date(b.createdTime).getTime() - new Date(a.createdTime).getTime());
    
    return completedAnalyses.length > 0 ? completedAnalyses[0] : null;
  }
}

export default new MetadataService();
