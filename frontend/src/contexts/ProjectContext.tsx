import React, { createContext, useContext, useState, useEffect, useCallback } from 'react';
import type { ReactNode } from 'react';
import { message } from 'antd';
import { projectApi } from '../services/project';
import type { Project } from '../types';

// Project context interface
interface ProjectContextType {
  // State
  projects: Project[];
  loading: boolean;
  error: string | null;
  lastUpdated: number | null;
  
  // Actions
  loadProjects: () => Promise<void>;
  refreshProjects: () => Promise<void>;
  addProject: (project: Project) => void;
  updateProject: (project: Project) => void;
  removeProject: (projectId: number) => void;
  clearCache: () => void;
}

// Create context
const ProjectContext = createContext<ProjectContextType | undefined>(undefined);

// Cache configuration
const CACHE_DURATION = 5 * 60 * 1000; // 5 minutes
const FORCE_REFRESH_THRESHOLD = 30 * 1000; // 30 seconds minimum between force refreshes

interface ProjectProviderProps {
  children: ReactNode;
}

// Provider component
export const ProjectProvider: React.FC<ProjectProviderProps> = ({ children }) => {
  const [projects, setProjects] = useState<Project[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [lastUpdated, setLastUpdated] = useState<number | null>(null);
  const [lastForceRefresh, setLastForceRefresh] = useState<number>(0);

  // Check if cache is still valid
  const isCacheValid = useCallback(() => {
    if (!lastUpdated) return false;
    return Date.now() - lastUpdated < CACHE_DURATION;
  }, [lastUpdated]);

  // Load projects from API
  const loadProjects = useCallback(async () => {
    // If cache is valid and we have data, don't make a request
    if (isCacheValid() && projects.length > 0) {
      console.log('ProjectContext: Using cached projects data');
      return;
    }

    // If already loading, don't make another request
    if (loading) {
      console.log('ProjectContext: Already loading, skipping request');
      return;
    }

    try {
      setLoading(true);
      setError(null);
      console.log('ProjectContext: Fetching projects from API');
      
      const response = await projectApi.getActiveProjects();
      setProjects(response.data);
      setLastUpdated(Date.now());
      
      console.log('ProjectContext: Successfully loaded projects:', response.data.length);
    } catch (err) {
      const errorMessage = err instanceof Error ? err.message : 'Failed to load projects';
      setError(errorMessage);
      console.error('ProjectContext: Failed to load projects:', err);
      message.error(errorMessage);
    } finally {
      setLoading(false);
    }
  }, [isCacheValid, projects.length, loading]);

  // Force refresh projects (ignores cache)
  const refreshProjects = useCallback(async () => {
    const now = Date.now();
    
    // Prevent too frequent force refreshes
    if (now - lastForceRefresh < FORCE_REFRESH_THRESHOLD) {
      console.log('ProjectContext: Force refresh throttled');
      return;
    }

    try {
      setLoading(true);
      setError(null);
      setLastForceRefresh(now);
      console.log('ProjectContext: Force refreshing projects');
      
      const response = await projectApi.getActiveProjects();
      setProjects(response.data);
      setLastUpdated(Date.now());
      
      console.log('ProjectContext: Successfully refreshed projects:', response.data.length);
    } catch (err) {
      const errorMessage = err instanceof Error ? err.message : 'Failed to refresh projects';
      setError(errorMessage);
      console.error('ProjectContext: Failed to refresh projects:', err);
      message.error(errorMessage);
    } finally {
      setLoading(false);
    }
  }, [lastForceRefresh]);

  // Add new project to local state
  const addProject = useCallback((project: Project) => {
    setProjects(prev => [...prev, project]);
    setLastUpdated(Date.now());
    console.log('ProjectContext: Added project:', project.name);
  }, []);

  // Update existing project in local state
  const updateProject = useCallback((updatedProject: Project) => {
    setProjects(prev => 
      prev.map(project => 
        project.id === updatedProject.id ? updatedProject : project
      )
    );
    setLastUpdated(Date.now());
    console.log('ProjectContext: Updated project:', updatedProject.name);
  }, []);

  // Remove project from local state
  const removeProject = useCallback((projectId: number) => {
    setProjects(prev => prev.filter(project => project.id !== projectId));
    setLastUpdated(Date.now());
    console.log('ProjectContext: Removed project:', projectId);
  }, []);

  // Clear cache and force reload
  const clearCache = useCallback(() => {
    setProjects([]);
    setLastUpdated(null);
    setError(null);
    console.log('ProjectContext: Cache cleared');
  }, []);

  // Auto-load projects on mount
  useEffect(() => {
    loadProjects();
  }, [loadProjects]);

  const contextValue: ProjectContextType = {
    projects,
    loading,
    error,
    lastUpdated,
    loadProjects,
    refreshProjects,
    addProject,
    updateProject,
    removeProject,
    clearCache,
  };

  return (
    <ProjectContext.Provider value={contextValue}>
      {children}
    </ProjectContext.Provider>
  );
};

// Custom hook to use project context
export const useProjectContext = () => {
  const context = useContext(ProjectContext);
  if (context === undefined) {
    throw new Error('useProjectContext must be used within a ProjectProvider');
  }
  return context;
};

// Convenience hook for read-only access to projects
export const useProjects = () => {
  const { projects, loading, error, loadProjects } = useProjectContext();
  return { projects, loading, error, loadProjects };
};
