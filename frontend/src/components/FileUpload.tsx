import React, { useCallback, useState } from 'react';
import {
  Upload,
  Button,
  Progress,
  Card,
  List,
  Tag,
  Space,
  Typography,
  Alert,
  Row,
  Col,
} from 'antd';
import {
  InboxOutlined,
  UploadOutlined,
  DeleteOutlined,
  CheckCircleOutlined,
  ExclamationCircleOutlined,
} from '@ant-design/icons';
import type { UploadProps } from 'antd';
import { useFileUpload } from '../hooks';
import { formatFileSize, isSupportedFileType, getFileCategory } from '../utils';
import { SUPPORTED_FILE_EXTENSIONS, MAX_FILE_SIZE } from '../constants';
import type { UploadFile as ApiUploadFile } from '../types';

const { Dragger } = Upload;
const { Text, Title } = Typography;

interface FileUploadProps {
  onUploadSuccess?: (file: ApiUploadFile) => void;
  onUploadError?: (error: string) => void;
  accept?: string;
  maxSize?: number;
  showProgress?: boolean;
}

const FileUpload: React.FC<FileUploadProps> = ({
  onUploadSuccess,
  onUploadError,
  accept,
  maxSize = MAX_FILE_SIZE,
  showProgress = true,
}) => {
  const { uploadProgress, isUploading, uploadFile, clearProgress, clearAllProgress } = useFileUpload();
  const [isDragOver, setIsDragOver] = useState(false);

  const validateFile = useCallback((file: File): boolean => {
    // Check file size
    if (file.size > maxSize) {
      const errorMsg = `File size exceeds limit. Maximum size: ${formatFileSize(maxSize)}`;
      onUploadError?.(errorMsg);
      return false;
    }

    // Check file type
    if (!isSupportedFileType(file.name)) {
      const errorMsg = `Unsupported file type. Supported formats: ${SUPPORTED_FILE_EXTENSIONS.join(', ')}`;
      onUploadError?.(errorMsg);
      return false;
    }

    return true;
  }, [maxSize, onUploadError]);

  const handleFileUpload = useCallback(async (file: File) => {
    if (!validateFile(file)) {
      return;
    }

    try {
      const result = await uploadFile(file);
      if (result) {
        onUploadSuccess?.(result);
      }
    } catch (error) {
      const errorMsg = error instanceof Error ? error.message : 'Upload failed';
      onUploadError?.(errorMsg);
    }
  }, [uploadFile, validateFile, onUploadSuccess, onUploadError]);

  const uploadProps: UploadProps = {
    name: 'file',
    multiple: true,
    accept: accept || SUPPORTED_FILE_EXTENSIONS.map(ext => `.${ext}`).join(','),
    beforeUpload: (file) => {
      handleFileUpload(file);
      return false; // Prevent automatic upload
    },
    onDrop: (e) => {
      setIsDragOver(false);
      console.log('Dropped files', e.dataTransfer.files);
    },
    onDragEnter: () => setIsDragOver(true),
    onDragLeave: () => setIsDragOver(false),
    showUploadList: false,
  };

  const getStatusIcon = (status: string) => {
    switch (status) {
      case 'success':
        return <CheckCircleOutlined style={{ color: '#52c41a' }} />;
      case 'error':
        return <ExclamationCircleOutlined style={{ color: '#ff4d4f' }} />;
      default:
        return null;
    }
  };

  const getStatusColor = (status: string) => {
    switch (status) {
      case 'success':
        return 'success';
      case 'error':
        return 'error';
      case 'uploading':
        return 'processing';
      default:
        return 'default';
    }
  };

  return (
    <div>
      <Card
        title={
          <Space>
            <UploadOutlined />
            <Title level={4} style={{ margin: 0 }}>
              File Upload
            </Title>
          </Space>
        }
        extra={
          uploadProgress.length > 0 && (
            <Button 
              size="small" 
              onClick={clearAllProgress}
              disabled={isUploading}
            >
              Clear All
            </Button>
          )
        }
      >
        <Dragger 
          {...uploadProps}
          className={isDragOver ? 'drag-over' : ''}
          style={{
            padding: '20px',
            border: isDragOver ? '2px dashed #1890ff' : undefined,
            backgroundColor: isDragOver ? '#f0f8ff' : undefined,
          }}
        >
          <p className="ant-upload-drag-icon">
            <InboxOutlined style={{ fontSize: 48, color: '#1890ff' }} />
          </p>
          <p className="ant-upload-text">
            Click or drag files to this area to upload
          </p>
          <p className="ant-upload-hint">
            Support for image and video files. Maximum file size: {formatFileSize(maxSize)}
          </p>
          <p className="ant-upload-hint">
            <Text type="secondary">
              Supported formats: {SUPPORTED_FILE_EXTENSIONS.join(', ')}
            </Text>
          </p>
        </Dragger>

        {showProgress && uploadProgress.length > 0 && (
          <div style={{ marginTop: 16 }}>
            <Title level={5}>Upload Progress</Title>
            <List
              dataSource={uploadProgress}
              renderItem={(item) => (
                <List.Item
                  actions={[
                    <Button
                      key="delete"
                      type="text"
                      size="small"
                      icon={<DeleteOutlined />}
                      onClick={() => clearProgress(item.fileId)}
                      disabled={item.status === 'uploading'}
                    />
                  ]}
                >
                  <List.Item.Meta
                    avatar={getStatusIcon(item.status)}
                    title={
                      <Space>
                        <Text>{item.fileName}</Text>
                        <Tag color={getStatusColor(item.status)}>
                          {getFileCategory(item.fileName)}
                        </Tag>
                      </Space>
                    }
                    description={
                      <div>
                        {item.status === 'uploading' && (
                          <Progress
                            percent={item.progress}
                            size="small"
                            status="active"
                          />
                        )}
                        {item.status === 'success' && (
                          <Text type="success">Upload completed</Text>
                        )}
                        {item.status === 'error' && (
                          <Text type="danger">{item.errorMessage}</Text>
                        )}
                      </div>
                    }
                  />
                </List.Item>
              )}
            />
          </div>
        )}

        <Row gutter={16} style={{ marginTop: 16 }}>
          <Col span={12}>
            <Alert
              message="Tip"
              description="Files are uploaded in chunks for better reliability and resume support."
              type="info"
              showIcon
              banner
            />
          </Col>
          <Col span={12}>
            <Alert
              message="Security"
              description="All uploaded files are validated and scanned for integrity."
              type="success"
              showIcon
              banner
            />
          </Col>
        </Row>
      </Card>

      <style jsx>{`
        .drag-over {
          border-color: #1890ff !important;
          background-color: #f0f8ff !important;
        }
      `}</style>
    </div>
  );
};

export default FileUpload;
