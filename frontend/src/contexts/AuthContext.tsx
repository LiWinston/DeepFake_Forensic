import React, { createContext, useContext, useState, useEffect } from 'react';
import type { ReactNode } from 'react';
import authService from '../services/auth';
import type { User } from '../services/auth';

interface AuthContextType {
  user: User | null;
  isLoggedIn: boolean;
  login: (username: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
  refreshUser: () => Promise<void>;
  loading: boolean;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export const useAuth = () => {
  const context = useContext(AuthContext);
  if (context === undefined) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
};

interface AuthProviderProps {
  children: ReactNode;
}

export const AuthProvider: React.FC<AuthProviderProps> = ({ children }) => {
  const [user, setUser] = useState<User | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const initAuth = async () => {
      try {
        if (authService.isLoggedIn() && !authService.isTokenExpired()) {
          const userInfo = await authService.getCurrentUser();
          setUser(userInfo);
        } else {
          // Token is expired or doesn't exist, clear auth data
          await authService.logout();
        }
      } catch (error) {
        console.error('Failed to initialize auth:', error);
        await authService.logout();
      } finally {
        setLoading(false);
      }
    };

    initAuth();
  }, []);  const login = async (username: string, password: string) => {
    const response = await authService.login({ username, password });
    setUser(response.user);
  };
  const logout = async () => {
    await authService.logout();
    setUser(null);
  };

  const refreshUser = async () => {
    try {
      if (authService.isLoggedIn() && !authService.isTokenExpired()) {
        const userInfo = await authService.getCurrentUser();
        setUser(userInfo);
      }
    } catch (error) {
      console.error('Failed to refresh user:', error);
      await authService.logout();
      setUser(null);
    }
  };

  const value = {
    user,
    isLoggedIn: !!user,
    login,
    logout,
    refreshUser,
    loading,
  };

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
};
