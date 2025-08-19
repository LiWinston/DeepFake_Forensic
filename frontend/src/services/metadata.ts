import httpClient from './http';
import { API_ENDPOINTS } from '../constants';
import type { MetadataAnalysis, MetadataResult, ApiResponse } from '../types';

// Backend DTO shape
export interface MetadataAnalysisResponseDTO {
  success: boolean;
  message?: string;
  fileMd5: string;
  extractionStatus?: string;
  basicMetadata?: Record<string, any>;
  exifData?: Record<string, any>;
  videoMetadata?: Record<string, any>;
  hashValues?: { md5?: string; sha256?: string } & Record<string, any>;
  suspiciousIndicators?: { hasAnomalies?: boolean; anomalies?: string[]; riskScore?: number } & Record<string, any>;
  analysisTime?: string;
}

const mapToMetadataAnalysis = (dto: MetadataAnalysisResponseDTO): MetadataAnalysis => {
  const result: MetadataResult = {
    exifData: dto.exifData || undefined,
    fileHeaders: dto.basicMetadata || undefined,
    hashData: dto.hashValues ? { md5: (dto.hashValues as any).md5, sha256: (dto.hashValues as any).sha256 } : undefined,
    technicalData: dto.videoMetadata || undefined,
    suspicious: dto.suspiciousIndicators ? {
      hasAnomalies: !!dto.suspiciousIndicators.hasAnomalies,
      anomalies: dto.suspiciousIndicators.anomalies || [],
      riskScore: dto.suspiciousIndicators.riskScore ?? 0,
    } : undefined,
  };
  return {
    id: `${dto.fileMd5}-${dto.analysisTime || 'now'}`,
    fileId: dto.fileMd5,
    analysisType: 'FULL',
    status: dto.success ? 'COMPLETED' : 'FAILED',
    result,
    createdTime: dto.analysisTime || new Date().toISOString(),
    completedTime: dto.analysisTime || new Date().toISOString(),
    errorMessage: dto.success ? undefined : (dto.message || 'Analysis not available'),
  };
};

class MetadataService {
  // GET /metadata/analysis/{fileMd5}
  async getAnalysis(fileMd5: string): Promise<MetadataAnalysis> {
    const res = await httpClient.get<ApiResponse<MetadataAnalysisResponseDTO>>(
      `${API_ENDPOINTS.METADATA_ANALYSIS}/${fileMd5}`
    );
    const dto = (res.data?.data as any) || (res.data as any);
    return mapToMetadataAnalysis(dto as MetadataAnalysisResponseDTO);
  }

  // Additional granular fetchers (optional usage)
  async getBasic(fileMd5: string) {
    const res = await httpClient.get<ApiResponse<any>>(`${API_ENDPOINTS.METADATA_BASIC}/${fileMd5}`);
    return (res.data?.data as any) || (res.data as any);
  }
  async getExif(fileMd5: string) {
    const res = await httpClient.get<ApiResponse<any>>(`${API_ENDPOINTS.METADATA_EXIF}/${fileMd5}`);
    return (res.data?.data as any) || (res.data as any);
  }
  async getVideo(fileMd5: string) {
    const res = await httpClient.get<ApiResponse<any>>(`${API_ENDPOINTS.METADATA_VIDEO}/${fileMd5}`);
    return (res.data?.data as any) || (res.data as any);
  }
  async getSuspicious(fileMd5: string) {
    const res = await httpClient.get<ApiResponse<any>>(`${API_ENDPOINTS.METADATA_SUSPICIOUS}/${fileMd5}`);
    return (res.data?.data as any) || (res.data as any);
  }
  async getHashes(fileMd5: string) {
    const res = await httpClient.get<ApiResponse<any>>(`${API_ENDPOINTS.METADATA_HASHES}/${fileMd5}`);
    return (res.data?.data as any) || (res.data as any);
  }

  // POST /metadata/analysis/{fileMd5}/start - Start analysis
  async startAnalysis(fileMd5: string): Promise<{
    success: boolean;
    message: string;
    status?: string;
  }> {
    const res = await httpClient.post<ApiResponse<any>>(
      `${API_ENDPOINTS.METADATA_ANALYSIS_START}/${fileMd5}/start`
    );
    const data = (res.data?.data as any) || (res.data as any);
    return {
      success: data.success || false,
      message: data.message || 'Failed to start analysis',
      status: data.status
    };
  }

  // GET /metadata/analysis/{fileMd5}/status - Get analysis status
  async getAnalysisStatus(fileMd5: string): Promise<{
    hasAnalysis: boolean;
    status: string;
    message: string;
    analysisTime?: string;
  }> {
    const res = await httpClient.get<ApiResponse<any>>(
      `${API_ENDPOINTS.METADATA_ANALYSIS_STATUS}/${fileMd5}/status`
    );
    const data = (res.data?.data as any) || (res.data as any);
    return {
      hasAnalysis: data.hasAnalysis || false,
      status: data.status || 'UNKNOWN',
      message: data.message || 'Unknown status',
      analysisTime: data.analysisTime
    };
  }
}

export default new MetadataService();
