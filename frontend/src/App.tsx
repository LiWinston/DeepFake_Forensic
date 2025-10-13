import React, { useState } from 'react';
import {
  BrowserRouter as Router,
  Routes,
  Route,
  Navigate,
  useNavigate,
  useLocation,
} from 'react-router-dom';
import {
  Layout,
  Menu,
  Typography,
  Button,
  Avatar,
  Dropdown,
} from 'antd';
import {
  HomeOutlined,
  FileTextOutlined,
  MenuFoldOutlined,
  MenuUnfoldOutlined,
  UserOutlined,
  LogoutOutlined,
  ProjectOutlined,
} from '@ant-design/icons';

// Import pages
import HomePage from './pages/HomePage';
import FilesPage from './pages/FilesPage';
import ProjectsPage from './pages/ProjectsPage';
import ProjectDetailPage from './pages/ProjectDetailPage';
import AuthPage from './pages/AuthPage';
import ForgotPasswordPage from './pages/ForgotPasswordPage';
import ResetPasswordPage from './pages/ResetPasswordPage';
import EmailVerificationPage from './pages/EmailVerificationPage';
import UserAccountPage from './pages/UserAccountPage';

// Import components and contexts
import ProtectedRoute from './components/ProtectedRoute';
import { AuthProvider, useAuth } from './contexts/AuthContext';
import { ProjectProvider } from './contexts/ProjectContext';

const { Header, Sider, Content } = Layout;
const { Title } = Typography;

// Navigation component
const AppNavigation: React.FC = () => {
  const [collapsed, setCollapsed] = useState(false);
  const navigate = useNavigate();
  const location = useLocation();
  const { user, logout } = useAuth();

  const appStyles = {    layout: {
      background: 'linear-gradient(135deg, #0a0e27 0%, #1a1a2e 50%, #16213e 100%)',
      minHeight: '100vh',
    },sider: {
      background: 'linear-gradient(180deg, #0f1419 0%, #1a1a2e 100%)',
      borderRight: '1px solid rgba(0, 212, 255, 0.1)',
      boxShadow: '2px 0 10px rgba(0, 0, 0, 0.3)',
      position: 'fixed' as const,
      height: '100vh',
      left: 0,
      top: 0,
      zIndex: 1000,
    },
    logo: {
      height: '64px',
      margin: '16px',
      background: 'linear-gradient(135deg, rgba(0, 212, 255, 0.1), rgba(255, 0, 132, 0.1))',
      border: '1px solid rgba(0, 212, 255, 0.2)',
      borderRadius: '8px',
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'center',
      color: 'white',
      fontWeight: 'bold',
      position: 'relative' as const,
      overflow: 'hidden',
      transition: 'all 0.3s ease',
    },
    logoText: {
      position: 'relative' as const,
      zIndex: 1,
      background: 'linear-gradient(45deg, #00d4ff, #ffffff)',
      WebkitBackgroundClip: 'text',
      WebkitTextFillColor: 'transparent',
      backgroundClip: 'text',
      fontSize: '14px',
      letterSpacing: '0.5px',
    },    header: {
      background: 'rgba(15, 20, 25, 0.95)',
      backdropFilter: 'blur(10px)',
      borderBottom: '1px solid rgba(0, 212, 255, 0.1)',
      boxShadow: '0 2px 20px rgba(0, 0, 0, 0.3)',
      padding: '0 24px',
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'space-between',
      position: 'relative' as const,
    },
    headerLeft: {
      display: 'flex',
      alignItems: 'center',
    },    toggleBtn: {
      border: 'none',
      background: 'transparent',
      color: '#00d4ff',
      transition: 'all 0.3s ease',
      borderRadius: '8px',
    },    title: {
      margin: '0 0 0 20px',
      color: '#ffffff',
      fontWeight: 600,
      fontSize: '1.3rem',
      textShadow: '0 0 10px rgba(0, 212, 255, 0.3)',
    },
    userMenu: {
      display: 'flex',
      alignItems: 'center',
      cursor: 'pointer',
      padding: '8px 16px',
      borderRadius: '8px',
      transition: 'all 0.3s ease',
      border: '1px solid transparent',
    },
    userAvatar: {
      background: 'linear-gradient(45deg, #00d4ff, #1890ff)',
      marginRight: '8px',
      boxShadow: '0 2px 8px rgba(0, 212, 255, 0.3)',
    },    username: {
      fontWeight: 500,
      color: '#ffffff',
    },
    content: {
      margin: '0',
      background: 'transparent',
      minHeight: 'calc(100vh - 64px)',
      position: 'relative' as const,
    },
    contentInner: {
      background: 'transparent',
      minHeight: 'calc(100vh - 64px)',
      padding: '0',
      position: 'relative' as const,
      overflow: 'hidden',
    },
  };

  const handleLogout = async () => {
    await logout();
    navigate('/auth');
  };
  const userMenuItems = [
    {
      key: 'user-info',
      label: `${user?.username}`,
      disabled: true,
    },
    {
      type: 'divider' as const,
    },
    {
      key: 'account',
      label: 'Account Settings',
      icon: <UserOutlined />,
      onClick: () => navigate('/account'),
    },
    {
      key: 'logout',
      label: 'Logout',
      icon: <LogoutOutlined />,
      onClick: handleLogout,
    },
  ];

  const menuItems = [
    {
      key: '/',
      icon: <HomeOutlined />,
      label: 'Home',
    },
    {
      key: '/projects',
      icon: <ProjectOutlined />,
      label: 'Projects',
    },
    {
      key: '/files',
      icon: <FileTextOutlined />,
      label: 'File Management',
    },
  ];

  const handleMenuClick = ({ key }: { key: string }) => {
    navigate(key);
  };  return (
    <Layout style={appStyles.layout}>
      <Sider 
        trigger={null} 
        collapsible 
        collapsed={collapsed}
        style={appStyles.sider}
        width={280}
        collapsedWidth={80}
      >
        <div style={appStyles.logo}>
          <span style={{
            ...appStyles.logoText,
            fontSize: collapsed ? '16px' : '14px',
            fontWeight: collapsed ? 900 : 'bold',
          }}>
            {collapsed ? 'DF' : 'DeepFake Forensic'}
          </span>
        </div>        <Menu
          theme="dark"
          mode="inline"
          selectedKeys={[location.pathname]}
          items={menuItems}
          onClick={handleMenuClick}
          style={{ 
            background: 'transparent',
            border: 'none',
          }}
        />      </Sider>
      <Layout style={{ marginLeft: collapsed ? 80 : 280, transition: 'margin-left 0.2s' }}>
        <Header style={{...appStyles.header, position: 'fixed', right: 0, zIndex: 999, width: `calc(100% - ${collapsed ? 80 : 280}px)`, transition: 'width 0.2s'}}>
          <div style={appStyles.headerLeft}>
            <Button
              style={{
                ...appStyles.toggleBtn,
                fontSize: '16px',
                width: 64,
                height: 64,
              }}
              type="text"
              icon={collapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />}
              onClick={() => setCollapsed(!collapsed)}
              onMouseEnter={(e) => {
                e.currentTarget.style.background = 'rgba(0, 212, 255, 0.1)';
                e.currentTarget.style.color = '#00d4ff';
                e.currentTarget.style.transform = 'scale(1.1)';
              }}              onMouseLeave={(e) => {
                e.currentTarget.style.background = 'transparent';
                e.currentTarget.style.color = '#00d4ff';
                e.currentTarget.style.transform = 'scale(1)';
              }}
            />
            <Title level={4} style={appStyles.title}>
              Deepfake Detection & Forensic Analysis Platform
            </Title>
          </div>
          <Dropdown menu={{ items: userMenuItems }} placement="bottomRight">
            <div 
              style={appStyles.userMenu}
              onMouseEnter={(e) => {
                e.currentTarget.style.background = 'rgba(0, 212, 255, 0.05)';
                e.currentTarget.style.borderColor = 'rgba(0, 212, 255, 0.2)';
                e.currentTarget.style.transform = 'translateY(-1px)';
              }}
              onMouseLeave={(e) => {
                e.currentTarget.style.background = 'transparent';
                e.currentTarget.style.borderColor = 'transparent';
                e.currentTarget.style.transform = 'translateY(0)';
              }}
            >
              <Avatar 
                icon={<UserOutlined />} 
                size="small" 
                style={appStyles.userAvatar}
              />
              <span style={appStyles.username}>{user?.username}</span>
            </div>
          </Dropdown>        </Header>
        <Content style={{...appStyles.content, marginTop: 64}}>
          <div style={appStyles.contentInner}>            <Routes>
              <Route path="/" element={<HomePage />} />
              <Route path="/projects" element={<ProjectsPage />} />
              <Route path="/projects/:projectId" element={<ProjectDetailPage />} />
              <Route path="/files" element={<FilesPage />} />
              <Route path="/files/:projectId" element={<FilesPage />} />
              <Route path="/account" element={<UserAccountPage />} />
              <Route path="*" element={<Navigate to="/" replace />} />
            </Routes>
          </div>
        </Content>
      </Layout>
    </Layout>
  );
};

// Main App component with conditional ProjectProvider
const AppWithProjectProvider: React.FC = () => {
  const { isLoggedIn, loading } = useAuth();
  
  if (loading) {
    return <div>Loading...</div>;
  }
  
  return (    <Router>
      <Routes>
        <Route path="/auth" element={<AuthPage />} />
        <Route path="/forgot-password" element={<ForgotPasswordPage />} />
        <Route path="/reset-password" element={<ResetPasswordPage />} />
        <Route path="/verify-email" element={<EmailVerificationPage />} />
        <Route path="/*" element={
          <ProtectedRoute>
            {isLoggedIn ? (
              <ProjectProvider>
                <AppNavigation />
              </ProjectProvider>
            ) : (
              <AppNavigation />
            )}
          </ProtectedRoute>
        } />
      </Routes>
    </Router>
  );
};

// Main App component
const App: React.FC = () => {
  return (
    <AuthProvider>
      <AppWithProjectProvider />
    </AuthProvider>
  );
};

export default App;
