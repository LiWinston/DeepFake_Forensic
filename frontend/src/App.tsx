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
} from 'antd';
import {
  HomeOutlined,
  UploadOutlined,
  FileTextOutlined,
  BarChartOutlined,
  MenuFoldOutlined,
  MenuUnfoldOutlined,
} from '@ant-design/icons';

// Import pages
import HomePage from './pages/HomePage';

const { Header, Sider, Content } = Layout;
const { Title } = Typography;

// Navigation component
const AppNavigation: React.FC = () => {
  const [collapsed, setCollapsed] = useState(false);
  const navigate = useNavigate();
  const location = useLocation();

  const menuItems = [
    {
      key: '/',
      icon: <HomeOutlined />,
      label: 'Home',
    },
    {
      key: '/upload',
      icon: <UploadOutlined />,
      label: 'Upload Files',
    },
    {
      key: '/files',
      icon: <FileTextOutlined />,
      label: 'File Management',
    },
    {
      key: '/analysis',
      icon: <BarChartOutlined />,
      label: 'Analysis',
    },
  ];

  const handleMenuClick = ({ key }: { key: string }) => {
    navigate(key);
  };

  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Sider trigger={null} collapsible collapsed={collapsed}>
        <div style={{
          height: '32px',
          margin: '16px',
          background: 'rgba(255, 255, 255, 0.2)',
          borderRadius: '4px',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          color: 'white',
          fontWeight: 'bold',
        }}>
          {collapsed ? 'DF' : 'DeepFake Forensic'}
        </div>
        <Menu
          theme="dark"
          mode="inline"
          selectedKeys={[location.pathname]}
          items={menuItems}
          onClick={handleMenuClick}
        />
      </Sider>
      <Layout>
        <Header style={{
          padding: '0 16px',
          background: '#fff',
          display: 'flex',
          alignItems: 'center',
          boxShadow: '0 1px 4px rgba(0,21,41,.08)'
        }}>
          <Button
            type="text"
            icon={collapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />}
            onClick={() => setCollapsed(!collapsed)}
            style={{
              fontSize: '16px',
              width: 64,
              height: 64,
            }}
          />
          <Title level={4} style={{ margin: 0, marginLeft: '16px' }}>
            Deepfake Detection & Forensic Analysis Platform
          </Title>
        </Header>
        <Content style={{ margin: '24px 16px 0', overflow: 'initial' }}>
          <div style={{ padding: 24, background: '#fff', minHeight: 360 }}>
            <Routes>
              <Route path="/" element={<HomePage />} />
              <Route path="/upload" element={<div>Upload Page - Coming Soon</div>} />
              <Route path="/files" element={<div>File Management - Coming Soon</div>} />
              <Route path="/analysis" element={<div>Analysis Page - Coming Soon</div>} />
              <Route path="*" element={<Navigate to="/" replace />} />
            </Routes>
          </div>
        </Content>
      </Layout>
    </Layout>
  );
};

// Main App component
const App: React.FC = () => {
  return (
    <Router>
      <AppNavigation />
    </Router>
  );
};

export default App;
