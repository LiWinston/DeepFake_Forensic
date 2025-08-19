import http from './http';
import type { 
  Project, 
  CreateProjectRequest, 
  AnalysisTask, 
  CreateAnalysisTaskRequest
} from '../types';

// Project API
export const projectApi = {
  // 获取用户所有项目
  getProjects: () => 
    http.get<Project[]>('/projects'),

  // 根据ID获取项目
  getProject: (projectId: number) => 
    http.get<Project>(`/projects/${projectId}`),

  // 创建新项目
  createProject: (data: CreateProjectRequest) => 
    http.post<Project>('/projects', data),

  // 更新项目
  updateProject: (projectId: number, data: Partial<CreateProjectRequest>) => 
    http.put<Project>(`/projects/${projectId}`, data),

  // 删除项目（归档）
  deleteProject: (projectId: number) => 
    http.delete(`/projects/${projectId}`),

  // 归档项目
  archiveProject: (projectId: number) => 
    http.put<Project>(`/projects/${projectId}/archive`),

  // 根据状态获取项目
  getProjectsByStatus: (status: string) => 
    http.get<Project[]>(`/projects/status/${status}`),

  // 根据类型获取项目
  getProjectsByType: (type: string) => 
    http.get<Project[]>(`/projects/type/${type}`),

  // 搜索项目
  searchProjects: (keyword: string) => 
    http.get<Project[]>(`/projects/search?keyword=${encodeURIComponent(keyword)}`),

  // 根据案件编号获取项目
  getProjectByCaseNumber: (caseNumber: string) => 
    http.get<Project>(`/projects/case/${encodeURIComponent(caseNumber)}`),

  // 获取活跃项目
  getActiveProjects: () => 
    http.get<Project[]>('/projects/active'),

  // 获取即将到期的项目
  getProjectsWithDeadlines: (daysAhead: number = 7) => 
    http.get<Project[]>(`/projects/deadlines?daysAhead=${daysAhead}`),

  // 获取项目统计
  getProjectStatistics: () => 
    http.get<{
      totalProjects: number;
      activeProjects: number;
      completedProjects: number;
      archivedProjects: number;
    }>('/projects/statistics')
};

// Analysis Task API
export const analysisTaskApi = {
  // 创建分析任务
  createAnalysisTask: (data: CreateAnalysisTaskRequest) => 
    http.post<AnalysisTask>('/analysis-tasks', data),

  // 获取分析任务
  getAnalysisTask: (taskId: number) => 
    http.get<AnalysisTask>(`/analysis-tasks/${taskId}`),

  // 更新分析任务
  updateAnalysisTask: (taskId: number, data: Partial<AnalysisTask>) => 
    http.put<AnalysisTask>(`/analysis-tasks/${taskId}`, data),

  // 删除分析任务
  deleteAnalysisTask: (taskId: number) => 
    http.delete(`/analysis-tasks/${taskId}`),

  // 开始分析任务
  startAnalysisTask: (taskId: number) => 
    http.put<AnalysisTask>(`/analysis-tasks/${taskId}/start`),

  // 完成分析任务
  completeAnalysisTask: (taskId: number, resultData: string, confidenceScore?: number) => 
    http.put<AnalysisTask>(`/analysis-tasks/${taskId}/complete`, {
      resultData,
      confidenceScore
    }),

  // 失败分析任务
  failAnalysisTask: (taskId: number, errorMessage: string) => 
    http.put<AnalysisTask>(`/analysis-tasks/${taskId}/fail`, {
      errorMessage
    }),

  // 取消分析任务
  cancelAnalysisTask: (taskId: number) => 
    http.put<AnalysisTask>(`/analysis-tasks/${taskId}/cancel`),

  // 获取项目的分析任务
  getProjectAnalysisTasks: (projectId: number) => 
    http.get<AnalysisTask[]>(`/analysis-tasks/project/${projectId}`),

  // 获取用户所有分析任务
  getUserAnalysisTasks: () => 
    http.get<AnalysisTask[]>('/analysis-tasks'),

  // 根据状态获取分析任务
  getAnalysisTasksByStatus: (status: string) => 
    http.get<AnalysisTask[]>(`/analysis-tasks/status/${status}`),

  // 根据类型获取分析任务
  getAnalysisTasksByType: (projectId: number, type: string) => 
    http.get<AnalysisTask[]>(`/analysis-tasks/project/${projectId}/type/${type}`),

  // 获取运行中的任务
  getRunningTasks: () => 
    http.get<AnalysisTask[]>('/analysis-tasks/running'),

  // 获取待处理的任务
  getPendingTasks: () => 
    http.get<AnalysisTask[]>('/analysis-tasks/pending'),

  // 搜索分析任务
  searchAnalysisTasks: (keyword: string) => 
    http.get<AnalysisTask[]>(`/analysis-tasks/search?keyword=${encodeURIComponent(keyword)}`),

  // 获取分析任务统计
  getAnalysisTaskStatistics: (projectId: number) => 
    http.get<{
      totalTasks: number;
      pendingTasks: number;
      runningTasks: number;
      completedTasks: number;
      failedTasks: number;
    }>(`/analysis-tasks/project/${projectId}/statistics`)
};
