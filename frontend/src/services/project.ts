import http from './http';
import type { 
  Project, 
  CreateProjectRequest, 
  AnalysisTask, 
  CreateAnalysisTaskRequest
} from '../types';

// Project API
export const projectApi = {
  // Get all user projects
  getProjects: () => 
    http.get<Project[]>('/projects'),

  // Get project by ID
  getProject: (projectId: number) => 
    http.get<Project>(`/projects/${projectId}`),

  // Create new project
  createProject: (data: CreateProjectRequest) => 
    http.post<Project>('/projects', data),

  // Update project
  updateProject: (projectId: number, data: Partial<CreateProjectRequest>) => 
    http.put<Project>(`/projects/${projectId}`, data),

  // Delete project (archive)
  deleteProject: (projectId: number) => 
    http.delete(`/projects/${projectId}`),

  // Archive project
  archiveProject: (projectId: number) => 
    http.put<Project>(`/projects/${projectId}/archive`),

  // Get projects by status
  getProjectsByStatus: (status: string) => 
    http.get<Project[]>(`/projects/status/${status}`),

  // Get projects by type
  getProjectsByType: (type: string) => 
    http.get<Project[]>(`/projects/type/${type}`),

  // Search projects
  searchProjects: (keyword: string) => 
    http.get<Project[]>(`/projects/search?keyword=${encodeURIComponent(keyword)}`),

  // Get project by case number
  getProjectByCaseNumber: (caseNumber: string) => 
    http.get<Project>(`/projects/case/${encodeURIComponent(caseNumber)}`),

  // Get active projects
  getActiveProjects: () => 
    http.get<Project[]>('/projects/active'),

  // Get projects with deadlines
  getProjectsWithDeadlines: (daysAhead: number = 7) => 
    http.get<Project[]>(`/projects/deadlines?daysAhead=${daysAhead}`),

  // Get project statistics
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
  // Create analysis task
  createAnalysisTask: (data: CreateAnalysisTaskRequest) => 
    http.post<AnalysisTask>('/analysis-tasks', data),

  // Get analysis task
  getAnalysisTask: (taskId: number) => 
    http.get<AnalysisTask>(`/analysis-tasks/${taskId}`),

  // Update analysis task
  updateAnalysisTask: (taskId: number, data: Partial<AnalysisTask>) => 
    http.put<AnalysisTask>(`/analysis-tasks/${taskId}`, data),

  // Delete analysis task
  deleteAnalysisTask: (taskId: number) => 
    http.delete(`/analysis-tasks/${taskId}`),

  // Start analysis task
  startAnalysisTask: (taskId: number) => 
    http.put<AnalysisTask>(`/analysis-tasks/${taskId}/start`),

  // Complete analysis task
  completeAnalysisTask: (taskId: number, resultData: string, confidenceScore?: number) => 
    http.put<AnalysisTask>(`/analysis-tasks/${taskId}/complete`, {
      resultData,
      confidenceScore
    }),

  // Fail analysis task
  failAnalysisTask: (taskId: number, errorMessage: string) => 
    http.put<AnalysisTask>(`/analysis-tasks/${taskId}/fail`, {
      errorMessage
    }),

  // Cancel analysis task
  cancelAnalysisTask: (taskId: number) => 
    http.put<AnalysisTask>(`/analysis-tasks/${taskId}/cancel`),

  // Get project analysis tasks
  getProjectAnalysisTasks: (projectId: number) => 
    http.get<AnalysisTask[]>(`/analysis-tasks/project/${projectId}`),

  // Get all user analysis tasks
  getUserAnalysisTasks: () => 
    http.get<AnalysisTask[]>('/analysis-tasks'),

  // Get analysis tasks by status
  getAnalysisTasksByStatus: (status: string) => 
    http.get<AnalysisTask[]>(`/analysis-tasks/status/${status}`),

  // Get analysis tasks by type
  getAnalysisTasksByType: (projectId: number, type: string) => 
    http.get<AnalysisTask[]>(`/analysis-tasks/project/${projectId}/type/${type}`),

  // Get running tasks
  getRunningTasks: () => 
    http.get<AnalysisTask[]>('/analysis-tasks/running'),

  // Get pending tasks
  getPendingTasks: () => 
    http.get<AnalysisTask[]>('/analysis-tasks/pending'),

  // Search analysis tasks
  searchAnalysisTasks: (keyword: string) => 
    http.get<AnalysisTask[]>(`/analysis-tasks/search?keyword=${encodeURIComponent(keyword)}`),

  // Get analysis task statistics
  getAnalysisTaskStatistics: (projectId: number) => 
    http.get<{
      totalTasks: number;
      pendingTasks: number;
      runningTasks: number;
      completedTasks: number;
      failedTasks: number;
    }>(`/analysis-tasks/project/${projectId}/statistics`)
};
