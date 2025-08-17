import React, { useState } from 'react';
import {
  Layout,
  Row,
  Col,
  Card,
  Typography,
  Space,
  Alert,
  Divider,
} from 'antd';
import FileUpload from '../components/FileUpload';
import FilesList from '../components/FilesList';
import type { UploadFile } from '../types';

const { Content } = Layout;
const { Title, Paragraph } = Typography;

const UploadPage: React.FC = () => {
  const [refreshTrigger, setRefreshTrigger] = useState(0);

  const handleUploadSuccess = (file: UploadFile) => {
    console.log('Upload successful:', file);
    // Trigger refresh of files list
    setRefreshTrigger(prev => prev + 1);
  };

  const handleUploadError = (error: string) => {
    console.error('Upload error:', error);
  };

  return (
    <Content style={{ padding: '24px' }}>
      {/* Page Header */}
      <div style={{ marginBottom: 24 }}>
        <Title level={2}>File Upload</Title>
        <Paragraph>
          Upload images and videos for forensic analysis. Supported formats include 
          JPEG, PNG, GIF, MP4, AVI, MOV, and more. Files are uploaded securely 
          using chunked upload for reliability.
        </Paragraph>
      </div>

      {/* Security Notice */}
      <Alert
        message="Security Information"
        description={
          <div>
            <p>• All uploaded files are validated for type and integrity</p>
            <p>• Files are stored securely with encrypted metadata</p>
            <p>• Large files are uploaded in chunks for better reliability</p>
            <p>• Duplicate files are automatically detected and deduplicated</p>
          </div>
        }
        type="info"
        showIcon
        style={{ marginBottom: 24 }}
      />

      <Row gutter={[24, 24]}>
        {/* Upload Section */}
        <Col span={24}>
          <FileUpload
            onUploadSuccess={handleUploadSuccess}
            onUploadError={handleUploadError}
            showProgress={true}
          />
        </Col>

        <Col span={24}>
          <Divider />
        </Col>

        {/* Files List Section */}
        <Col span={24}>
          <FilesList 
            showActions={true}
            key={refreshTrigger} // Force refresh when upload succeeds
          />
        </Col>
      </Row>

      {/* Usage Guidelines */}
      <Card 
        title="Upload Guidelines" 
        style={{ marginTop: 24 }}
        size="small"
      >
        <Row gutter={[16, 16]}>
          <Col xs={24} md={12}>
            <Title level={5}>Supported File Types</Title>
            <div>
              <Space direction="vertical" size="small">
                <div>
                  <strong>Images:</strong> JPEG, PNG, GIF, BMP, WEBP, TIFF
                </div>
                <div>
                  <strong>Videos:</strong> MP4, AVI, MOV, WMV, FLV, WEBM, MKV
                </div>
              </Space>
            </div>
          </Col>
          <Col xs={24} md={12}>
            <Title level={5}>File Size Limits</Title>
            <div>
              <Space direction="vertical" size="small">
                <div>
                  <strong>Maximum file size:</strong> 1GB per file
                </div>
                <div>
                  <strong>Chunk size:</strong> 5MB (for reliable upload)
                </div>
                <div>
                  <strong>Concurrent uploads:</strong> Up to 3 files
                </div>
              </Space>
            </div>
          </Col>
        </Row>
      </Card>
    </Content>
  );
};

export default UploadPage;
