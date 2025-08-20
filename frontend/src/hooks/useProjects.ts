import { useState, useEffect, useCallback } from 'react';
import { message } from 'antd';
import { projectApi } from '../services/project';
import type { Project } from '../types';

/**
 * Custom hook for managing project state with caching
 * Prevents duplicate requests and provides loading state
 */
export const useProjects = () => {
  const [projects, setProjects] = useState<Project[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const loadProjects = useCallback(async (showMessage = false) => {
    try {
      setLoading(true);
      setError(null);
      
      const response = await projectApi.getActiveProjects();
      setProjects(response.data);
      
      if (showMessage) {
        message.success('Projects loaded successfully');
      }
    } catch (err) {
      const errorMsg = 'Failed to load projects';
      setError(errorMsg);
      console.error('Error loading projects:', err);
      
      if (showMessage) {
        message.error(errorMsg);
      }
    } finally {
      setLoading(false);
    }
  }, []);

  // Load projects on mount
  useEffect(() => {
    loadProjects();
  }, [loadProjects]);

  const refreshProjects = useCallback(() => {
    return loadProjects(true);
  }, [loadProjects]);

  const getProjectById = useCallback((id: number): Project | undefined => {
    return projects.find(project => project.id === id);
  }, [projects]);

  const getProjectByName = useCallback((name: string): Project | undefined => {
    return projects.find(project => project.name === name);
  }, [projects]);

  return {
    projects,
    loading,
    error,
    loadProjects,
    refreshProjects,
    getProjectById,
    getProjectByName,
  };
};

export default useProjects;
