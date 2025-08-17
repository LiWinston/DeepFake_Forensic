import React from 'react';
import ReactDOM from 'react-dom/client';
import { ConfigProvider } from 'antd';
import enUS from 'antd/locale/en_US';
import App from './App';
import './index.css';

// Configure Ant Design theme
const theme = {
  token: {
    colorPrimary: '#1890ff',
    borderRadius: 6,
  },
};

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <ConfigProvider 
      locale={enUS}
      theme={theme}
    >
      <App />
    </ConfigProvider>
  </React.StrictMode>,
);
