import React, { useState } from 'react';
import {
  Layout,
  Row,
  Col,
  Typography,
  Card,
  Space,
  Button,
  Modal,
  Tabs,
  Alert,
  Divider,
} from 'antd';
import {
  FileTextOutlined,
  BarChartOutlined,
  EyeOutlined,
} from '@ant-design/icons';
import FilesList from '../components/FilesList';
import FileUpload from '../components/FileUpload';
import MetadataAnalysis from '../components/MetadataAnalysis';
import type { UploadFile } from '../types';
import uploadService from '../services/upload';

const { Content } = Layout;
const { Title, Paragraph } = Typography;
const { TabPane } = Tabs;

const FilesPage: React.FC = () => {
  const [selectedFile, setSelectedFile] = useState<UploadFile | null>(null);
  const [analysisModalVisible, setAnalysisModalVisible] = useState(false);
  const [previewModalVisible, setPreviewModalVisible] = useState(false);
  const [refreshTrigger, setRefreshTrigger] = useState(0);

  const handleFileSelect = (file: UploadFile) => {
    setSelectedFile(file);
  };

  const handleAnalyzeFile = () => {
    if (selectedFile) {
      setAnalysisModalVisible(true);
    }
  };

  const handlePreviewFile = () => {
    if (selectedFile) {
      setPreviewModalVisible(true);
    }
  };

  const handleCloseAnalysisModal = () => {
    setAnalysisModalVisible(false);
  };

  const handleClosePreviewModal = () => {
    setPreviewModalVisible(false);
  };

  const handleUploadSuccess = (file: UploadFile) => {
    console.log('Upload successful:', file);
    // Trigger refresh of files list
    setRefreshTrigger(prev => prev + 1);
  };

  const handleUploadError = (error: string) => {
    console.error('Upload error:', error);
  };

  return (
    <Content style={{ padding: '24px' }}>      {/* Page Header */}
      <div style={{ marginBottom: 24 }}>
        <Title level={2}>File Management</Title>
        <Paragraph>
          Upload files and manage your media collection for forensic analysis. 
          Select a file to view its properties and analysis results.
        </Paragraph>      </div>

      {/* Upload Section */}
      <FileUpload
        onUploadSuccess={handleUploadSuccess}
        onUploadError={handleUploadError}
        showProgress={true}
      />

      <Divider />

      <Row gutter={[24, 24]}>        {/* Files List Section */}
        <Col xs={24} lg={16}>
          <FilesList
            onFileSelect={handleFileSelect}
            selectable={true}
            showActions={true}
            key={refreshTrigger} // Force refresh when upload succeeds
          />
        </Col>

        {/* File Details Section */}
        <Col xs={24} lg={8}>
          <Card
            title={
              <Space>
                <FileTextOutlined />
                <span>File Details</span>
              </Space>
            }
            size="small"
          >
            {selectedFile ? (
              <div>
                <Tabs defaultActiveKey="details" size="small">
                  <TabPane tab="Details" key="details">
                    <Space direction="vertical" style={{ width: '100%' }}>
                      <div>
                        <strong>File Name:</strong>
                        <br />
                        <span>{selectedFile.originalName}</span>
                      </div>
                      <div>
                        <strong>File Size:</strong>
                        <br />
                        <span>{(selectedFile.fileSize / 1024 / 1024).toFixed(2)} MB</span>
                      </div>
                      <div>
                        <strong>File Type:</strong>
                        <br />
                        <span>{selectedFile.fileType || 'Unknown'}</span>
                      </div>
                      <div>
                        <strong>Status:</strong>
                        <br />
                        <span>{selectedFile.status}</span>
                      </div>
                      <div>
                        <strong>Upload Time:</strong>
                        <br />
                        <span>{new Date(selectedFile.uploadTime).toLocaleString()}</span>
                      </div>
                      {selectedFile.md5Hash && (
                        <div>
                          <strong>MD5 Hash:</strong>
                          <br />
                          <span style={{ fontFamily: 'monospace', fontSize: '12px' }}>
                            {selectedFile.md5Hash}
                          </span>
                        </div>
                      )}
                    </Space>
                  </TabPane>
                  <TabPane tab="Path" key="path">
                    <div>
                      <strong>File Path:</strong>
                      <br />
                      <span style={{ fontFamily: 'monospace', fontSize: '12px' }}>
                        {selectedFile.filePath}
                      </span>
                    </div>
                  </TabPane>
                </Tabs>

                <div style={{ marginTop: 16 }}>
                  <Space direction="vertical" style={{ width: '100%' }}>
                    <Button
                      type="primary"
                      icon={<BarChartOutlined />}
                      onClick={handleAnalyzeFile}
                      disabled={selectedFile.status !== 'COMPLETED' || !selectedFile.md5Hash}
                      block
                    >
                      Analyze Metadata
                    </Button>
                    <Button
                      icon={<EyeOutlined />}
                      onClick={handlePreviewFile}
                      disabled={selectedFile.status !== 'COMPLETED'}
                      block
                    >
                      Preview File
                    </Button>
                  </Space>
                </div>

                {selectedFile.status !== 'COMPLETED' && (
                  <Alert
                    message="File not ready"
                    description="File analysis and preview are only available for completed uploads."
                    type="warning"
                    showIcon
                    style={{ marginTop: 16 }}
                  />
                )}
              </div>
            ) : (
              <div style={{ textAlign: 'center', padding: '20px 0' }}>
                <Paragraph type="secondary">
                  Select a file from the list to view its details
                </Paragraph>
              </div>
            )}
          </Card>
        </Col>
      </Row>

      {/* Analysis Modal */}
      <Modal
        title={`Metadata Analysis - ${selectedFile?.originalName}`}
        open={analysisModalVisible}
        onCancel={handleCloseAnalysisModal}
        footer={null}
        width={1200}
        centered
      >
        {selectedFile && (
          <MetadataAnalysis
            file={selectedFile}
            showFileInfo={false}
          />
        )}
      </Modal>

      {/* Preview Modal */}
      <Modal
        title={`File Preview - ${selectedFile?.originalName}`}
        open={previewModalVisible}
        onCancel={handleClosePreviewModal}
        footer={null}
        width={800}
        centered
      >
        {selectedFile && (
          <div style={{ textAlign: 'center' }}>
            {selectedFile.originalName?.toLowerCase().match(/\.(jpg|jpeg|png|gif|bmp|webp)$/) ? (
              <img
                src={uploadService.getPreviewUrl(selectedFile.id)}
                alt={selectedFile.originalName}
                style={{ maxWidth: '100%', maxHeight: '500px' }}
                onError={(e) => {
                  (e.target as HTMLImageElement).src = 'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAMIAAADDCAYAAADQvc6UAAABRWlDQ1BJQ0MgUHJvZmlsZQAAKJFjYGASSSwoyGFhYGDIzSspCnJ3UoiIjFJgf8LAwSDCIMogwMCcmFxc4BgQ4ANUwgCjUcG3awyMIPqyLsis7PPOq3QdDFcvjV3jOD1boQVTPQrgSkktTgbSf4A4LbmgqISBgTEFyFYuLykAsTuAbJEioKOA7DkgdjqEvQHEToKwj4DVhAQ5A9k3gGyB5IxEoBmML4BsnSQk8XQkNtReEOBxcfXxUQg1Mjc0dyHgXNJBSWpFCYh2zi+oLMpMzyhRcASGUqqCZ16yno6CkYGRAQMDKMwhqj/fAIcloxgHQqxAjIHBEugw5sUIsSQpBobtQPdLciLEVJYzMPBHMDBsayhILEqEO4DxG0txmrERhM29nYGBddr//5/DGRjYNRkY/l7////39v///y4Dmn+LgeHANwDrkl1AuO+pmgAAADhlWElmTU0AKgAAAAgAAYdpAAQAAAABAAAAGgAAAAAAAqACAAQAAAABAAAAwqADAAQAAAABAAAAwwAAAAD9b/HnAAAHlklEQVR4Ae3dP3Ik1xkE8Hm38MZGg/wnhgIBfgKHBIg9gQMfwKGBo4MjGTgwfAKnPoE9ggNDNxAlBwYODAyMDAhLYEAC7l+sLULn1Rn3/fVvHzTQRu3a';
                }}
              />
            ) : selectedFile.originalName?.toLowerCase().match(/\.(mp4|avi|mov|wmv|flv|webm|mkv)$/) ? (
              <video
                controls
                style={{ maxWidth: '100%', maxHeight: '500px' }}
                src={uploadService.getPreviewUrl(selectedFile.id)}
              >
                Your browser does not support the video tag.
              </video>
            ) : (
              <div style={{ padding: '40px' }}>
                <Paragraph type="secondary">
                  Preview not available for this file type
                </Paragraph>
                <Paragraph>
                  <strong>File:</strong> {selectedFile.originalName}
                  <br />
                  <strong>Size:</strong> {(selectedFile.fileSize / 1024 / 1024).toFixed(2)} MB
                </Paragraph>
              </div>
            )}
          </div>
        )}
      </Modal>
    </Content>
  );
};

export default FilesPage;
