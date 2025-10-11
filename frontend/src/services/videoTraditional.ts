import http from './http';

export interface VideoTraditionalSubResult {
  id: number;
  fileMd5: string;
  method: 'NOISE' | 'FLOW' | 'FREQ' | 'TEMPORAL' | 'COPYMOVE' | string;
  artifacts: Record<string, string>;
  result: any;
  success: boolean;
  errorMessage?: string;
  createdAt: string;
  updatedAt: string;
}

export const videoTraditionalAPI = {
  async getResultsByFile(fileMd5: string): Promise<VideoTraditionalSubResult[]> {
    const res = await http.get(`/video-traditional-analysis/result/${fileMd5}`);
    return res.data?.success ? (res.data.data as VideoTraditionalSubResult[]) : [];
  }
};
