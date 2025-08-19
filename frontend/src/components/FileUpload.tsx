import React, { useCallback, useState, useEffect } from 'react';
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
  Select,
  Form,
  Modal,
  Input,
  message,
} from 'antd';
import {
  InboxOutlined,
  UploadOutlined,
  DeleteOutlined,
  CheckCircleOutlined,
  ExclamationCircleOutlined,
  PlusOutlined,
} from '@ant-design/icons';
import type { UploadProps } from 'antd';
import { useFileUpload } from '../hooks';
import { formatFileSize, isSupportedFileType, getFileCategory } from '../utils';
import { SUPPORTED_FILE_EXTENSIONS, MAX_FILE_SIZE } from '../constants';
import type { UploadFile as ApiUploadFile, Project } from '../types';
import { projectApi } from '../services/project';

const { Dragger } = Upload;
const { Text, Title } = Typography;

interface FileUploadProps {
  onUploadSuccess?: (file: ApiUploadFile) => void;
  onUploadError?: (error: string) => void;
  accept?: string;
  maxSize?: number;
  showProgress?: boolean;
  showProjectSelector?: boolean;
  defaultProjectId?: number;
}

const FileUpload: React.FC<FileUploadProps> = ({
  onUploadSuccess,
  onUploadError,
  accept,
  maxSize = MAX_FILE_SIZE,
  showProgress = true,
  showProjectSelector = true,
  defaultProjectId,
}) => {
  const { uploadProgress, isUploading, uploadFile, clearProgress, clearAllProgress } = useFileUpload();
  const [isDragOver, setIsDragOver] = useState(false);
  const [selectedProjectId, setSelectedProjectId] = useState<number | undefined>(defaultProjectId);
  const [projects, setProjects] = useState<Project[]>([]);
  const [showNewProjectModal, setShowNewProjectModal] = useState(false);
  const [newProjectForm] = Form.useForm();

  // Load projects on component mount
  useEffect(() => {
    if (showProjectSelector) {
      loadProjects();
    }
  }, [showProjectSelector]);

  const loadProjects = async () => {
    try {
      const response = await projectApi.getProjects();
      setProjects(response.data);
    } catch (error) {
      console.error('Failed to load projects:', error);
      message.error('加载项目列表失败');
    }
  };

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

    if (showProjectSelector && !selectedProjectId) {
      onUploadError?.('请先选择一个项目');
      return;
    }

    const projectId = selectedProjectId || defaultProjectId;
    if (!projectId) {
      onUploadError?.('项目ID无效');
      return;
    }

    try {
      const result = await uploadFile(file, projectId);
      if (result) {
        onUploadSuccess?.(result);
      }
    } catch (error) {
      const errorMsg = error instanceof Error ? error.message : 'Upload failed';
      onUploadError?.(errorMsg);
    }
  }, [uploadFile, validateFile, onUploadSuccess, onUploadError, selectedProjectId, defaultProjectId, showProjectSelector]);

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
    showUploadList: false,
  };

  // Handlers on wrapper to reflect drag state
  const onWrapperDragOver = (e: React.DragEvent<HTMLDivElement>) => {
    e.preventDefault();
    setIsDragOver(true);
  };
  const onWrapperDragLeave = () => setIsDragOver(false);
  const onWrapperDrop = () => setIsDragOver(false);

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
        {/* Project Selector */}
        {showProjectSelector && (
          <div style={{ marginBottom: 16 }}>
            <Form layout="vertical">
              <Form.Item label="选择项目" required>
                <Select
                  placeholder="请选择或创建项目"
                  value={selectedProjectId}
                  onChange={setSelectedProjectId}
                  style={{ width: '100%' }}
                  dropdownRender={menu => (
                    <div>
                      {menu}
                      <div style={{ padding: '8px', borderTop: '1px solid #f0f0f0' }}>
                        <Button
                          type="text"
                          icon={<PlusOutlined />}
                          onClick={() => setShowNewProjectModal(true)}
                          style={{ width: '100%' }}
                        >
                          创建新项目
                        </Button>
                      </div>
                    </div>
                  )}
                >
                  {projects.map(project => (
                    <Select.Option key={project.id} value={project.id}>
                      {project.name} ({project.caseNumber})
                    </Select.Option>
                  ))}
                </Select>
              </Form.Item>
            </Form>
          </div>
        )}

        <div onDragOver={onWrapperDragOver} onDragLeave={onWrapperDragLeave} onDrop={onWrapperDrop}>
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
        </div>

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

      {/* New Project Modal */}
      <Modal
        title="创建新项目"
        open={showNewProjectModal}
        onCancel={() => {
          setShowNewProjectModal(false);
          newProjectForm.resetFields();
        }}
        onOk={async () => {
          try {
            const values = await newProjectForm.validateFields();
            const response = await projectApi.createProject(values);
            const newProject = response.data;
            setProjects(prev => [...prev, newProject]);
            setSelectedProjectId(newProject.id);
            setShowNewProjectModal(false);
            newProjectForm.resetFields();
            message.success('项目创建成功');
          } catch (error) {
            console.error('Failed to create project:', error);
            message.error('创建项目失败');
          }
        }}
      >
        <Form form={newProjectForm} layout="vertical">
          <Form.Item
            name="name"
            label="项目名称"
            rules={[{ required: true, message: '请输入项目名称' }]}
          >
            <Input placeholder="请输入项目名称" />
          </Form.Item>
          <Form.Item
            name="caseNumber"
            label="案件编号"
            rules={[{ required: true, message: '请输入案件编号' }]}
          >
            <Input placeholder="请输入案件编号" />
          </Form.Item>
          <Form.Item name="description" label="项目描述">
            <Input.TextArea placeholder="请输入项目描述" rows={3} />
          </Form.Item>
          <Form.Item
            name="projectType"
            label="项目类型"
            rules={[{ required: true, message: '请选择项目类型' }]}
          >
            <Select placeholder="请选择项目类型">
              <Select.Option value="GENERAL">一般案件</Select.Option>
              <Select.Option value="CRIMINAL">刑事案件</Select.Option>
              <Select.Option value="CIVIL">民事案件</Select.Option>
              <Select.Option value="CORPORATE">企业案件</Select.Option>
              <Select.Option value="ACADEMIC_RESEARCH">学术研究</Select.Option>
            </Select>
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};

export default FileUpload;
