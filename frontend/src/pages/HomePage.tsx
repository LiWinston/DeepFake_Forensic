import React from 'react';
import { Typography, Card, Row, Col, Button } from 'antd';
import { UploadOutlined, BarChartOutlined, FileTextOutlined } from '@ant-design/icons';

const { Title, Paragraph } = Typography;

const HomePage: React.FC = () => {
  return (
    <div style={{ padding: '24px' }}>
      <Title level={2}>Welcome to DeepFake Forensic Platform</Title>
      <Paragraph>
        A comprehensive forensic tool for detecting and analyzing deepfake, digitally altered, and synthetic media.
      </Paragraph>
      
      <Row gutter={[24, 24]} style={{ marginTop: '32px' }}>
        <Col xs={24} sm={12} md={8}>
          <Card
            title="Upload Files"
            actions={[
              <Button type="primary" icon={<UploadOutlined />}>
                Start Upload
              </Button>
            ]}
          >
            <p>Upload images and videos for forensic analysis. Supports multiple file formats including JPEG, PNG, MP4, AVI.</p>
          </Card>
        </Col>
        
        <Col xs={24} sm={12} md={8}>
          <Card
            title="File Management"
            actions={[
              <Button type="default" icon={<FileTextOutlined />}>
                View Files
              </Button>
            ]}
          >
            <p>Manage uploaded files, view metadata information, and organize your forensic evidence.</p>
          </Card>
        </Col>
        
        <Col xs={24} sm={12} md={8}>
          <Card
            title="Analysis Results"
            actions={[
              <Button type="default" icon={<BarChartOutlined />}>
                View Results
              </Button>
            ]}
          >
            <p>Review forensic analysis results, view detection reports, and export findings.</p>
          </Card>
        </Col>
      </Row>

      <div style={{ marginTop: '48px', textAlign: 'center' }}>
        <Title level={3}>Platform Features</Title>
        <Row gutter={[16, 16]} style={{ marginTop: '24px' }}>
          <Col xs={24} md={8}>
            <Card>
              <Title level={4}>Deepfake Detection</Title>
              <p>Advanced AI algorithms to detect synthetic and manipulated media content.</p>
            </Card>
          </Col>
          <Col xs={24} md={8}>
            <Card>
              <Title level={4}>Metadata Analysis</Title>
              <p>Comprehensive metadata extraction and analysis for digital forensic evidence.</p>
            </Card>
          </Col>
          <Col xs={24} md={8}>
            <Card>
              <Title level={4}>Forensic Reports</Title>
              <p>Generate detailed forensic reports suitable for legal and investigative purposes.</p>
            </Card>
          </Col>
        </Row>
      </div>
    </div>
  );
};

export default HomePage;
