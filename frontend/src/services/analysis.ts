import httpClient from './http';

export interface StartAnalysisRequest {
  taskId?: string;
  fileMd5: string;
  projectId?: number;
  runMetadata?: boolean;
  runTraditionalImage?: boolean;
  runImageAI?: boolean;
  runVideoTraditional?: boolean;
  runVideoAI?: boolean;
  selectedImageModel?: string;
  selectedTraditionalMethods?: string[];
}

export const analysisService = {
  start: (payload: StartAnalysisRequest) =>
    httpClient.post('/analysis/start', payload),

  progress: (taskId: string) =>
    httpClient.get(`/analysis/progress/${encodeURIComponent(taskId)}`)
};
