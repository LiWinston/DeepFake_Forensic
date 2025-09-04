import React, { useCallback, useState, useEffect } from 'react';
import {
  Table,
  Button,
  Space,
  Tag,
  Typography,
  Image,
  Modal,
  Tooltip,
  Card,
  Select,
  Input,
  Row,
  Col,
  Popconfirm,
  message,
} from 'antd';
import {
  DeleteOutlined,
  EyeOutlined,
  ReloadOutlined,
  SearchOutlined,
  BarChartOutlined,
  ProjectOutlined,
} from '@ant-design/icons';
import type { ColumnsType, TablePaginationConfig } from 'antd/es/table';
import { useFilesList, useMetadataAnalysis } from '../hooks';
import { useProjects } from '../contexts/ProjectContext';
import { formatFileSize, formatDateTime, getFileCategory } from '../utils';
import type { UploadFile } from '../types';
import uploadService from '../services/upload';

const { Text, Title } = Typography;
const { Option } = Select;
const { Search } = Input;

interface FilesListProps {
  onFileSelect?: (file: UploadFile) => void;
  selectable?: boolean;
  showActions?: boolean;
  defaultProjectId?: number;
}

const FilesList: React.FC<FilesListProps> = ({
  onFileSelect,
  selectable = false,
  showActions = true,
  defaultProjectId,
}) => {
  const { files, loading, pagination, loadFiles, deleteFile, refreshFiles } = useFilesList();
  const { analyzeFile } = useMetadataAnalysis();
  const { projects, loading: projectsLoading } = useProjects();

  const [selectedRowKeys, setSelectedRowKeys] = useState<string[]>([]);
  const [previewFile, setPreviewFile] = useState<UploadFile | null>(null);
  const [filterType, setFilterType] = useState<string>('');
  const [searchText, setSearchText] = useState<string>('');
  const [selectedProjectId, setSelectedProjectId] = useState<number | undefined>(defaultProjectId);
  const [showAllFiles, setShowAllFiles] = useState(false);  // Auto-select first project if no default is set
  useEffect(() => {
    if (!defaultProjectId && projects.length > 0 && !showAllFiles && !selectedProjectId) {
      setSelectedProjectId(projects[0].id);
    }
  }, [defaultProjectId, projects, showAllFiles, selectedProjectId]);

  // Load files when project selection changes with debouncing to prevent duplicate requests
  useEffect(() => {
    if (projectsLoading || projects.length === 0) {
      return; // Don't load files until projects are loaded
    }

    const timeoutId = setTimeout(() => {
      if (showAllFiles) {
        loadFiles(1, pagination.pageSize, undefined, filterType || undefined, undefined, searchText || undefined);
      } else if (selectedProjectId) {
        loadFiles(1, pagination.pageSize, undefined, filterType || undefined, selectedProjectId, searchText || undefined);
      } else if (projects.length > 0 && !selectedProjectId) {
        // If no project is selected but projects exist, select the first one
        const firstProject = projects[0];
        setSelectedProjectId(firstProject.id);
        // Don't call loadFiles here as it will be triggered by the selectedProjectId change
      }
    }, 100); // 100ms debounce

    return () => clearTimeout(timeoutId);
  }, [selectedProjectId, showAllFiles, projects, projectsLoading, loadFiles, pagination.pageSize, filterType, searchText]);
  const handleTableChange = useCallback((pagination: TablePaginationConfig) => {
    if (showAllFiles) {
      loadFiles(pagination.current, pagination.pageSize, undefined, filterType || undefined, undefined, searchText || undefined);
    } else {
      loadFiles(pagination.current, pagination.pageSize, undefined, filterType || undefined, selectedProjectId, searchText || undefined);
    }
  }, [loadFiles, selectedProjectId, showAllFiles, filterType, searchText]);
  const handleDelete = useCallback(async (fileId: string) => {
    // Find the project for this file to check permissions
    const file = files.find(f => f.id === fileId);
    if (file && file.projectId) {
      const project = projects.find(p => p.id === file.projectId);
      if (project && project.status !== 'ACTIVE' && project.status !== 'COMPLETED') {
        const statusMessages = {
          'SUSPENDED': 'Cannot delete files from a suspended project.',
          'ARCHIVED': 'Cannot delete files from an archived project.'
        };
        const errorMsg = statusMessages[project.status as keyof typeof statusMessages] || 
                        'Cannot delete files from this project due to its current status.';
        message.error(errorMsg);
        return;
      }
    }
    await deleteFile(fileId);
  }, [deleteFile, files, projects]);

  const handleAnalyze = useCallback(async (file: UploadFile) => {
    try {
      if (!file.md5Hash) {
        message.error('File MD5 hash is missing, cannot perform analysis');
        return;
      }
      
      // Check project permissions for analysis
      if (file.projectId) {
        const project = projects.find(p => p.id === file.projectId);
        if (project && project.status !== 'ACTIVE') {
          const statusMessages = {
            'SUSPENDED': 'Cannot analyze files from a suspended project. Please resume the project first.',
            'COMPLETED': 'Cannot analyze files from a completed project.',
            'ARCHIVED': 'Cannot analyze files from an archived project. Please reactivate the project first.'
          };
          const errorMsg = statusMessages[project.status as keyof typeof statusMessages] || 
                          'Cannot analyze files from this project due to its current status.';
          message.error(errorMsg);
          return;
        }
      }
      
      await analyzeFile(file.md5Hash);
      message.success('Metadata analysis started');
    } catch (error) {
      console.error('Analysis failed:', error);
      message.error('Failed to start analysis');
    }
  }, [analyzeFile, projects]);

  const handlePreview = useCallback((file: UploadFile) => {
    setPreviewFile(file);
  }, []);
  const handleFilterChange = useCallback((value: string) => {
    setFilterType(value);
    if (showAllFiles) {
      loadFiles(1, pagination.pageSize, undefined, value || undefined, undefined, searchText || undefined);
    } else {
      loadFiles(1, pagination.pageSize, undefined, value || undefined, selectedProjectId, searchText || undefined);
    }
  }, [loadFiles, pagination.pageSize, selectedProjectId, showAllFiles, searchText]);
  const handleSearch = useCallback((value: string) => {
    setSearchText(value);
    // Implement search logic here - pass search keyword to loadFiles
    if (showAllFiles) {
      loadFiles(1, pagination.pageSize, undefined, filterType || undefined, undefined, value);
    } else {
      loadFiles(1, pagination.pageSize, undefined, filterType || undefined, selectedProjectId, value);
    }
  }, [loadFiles, pagination.pageSize, selectedProjectId, showAllFiles, filterType]);

  const handleProjectChange = useCallback((value: number | 'all') => {
    if (value === 'all') {
      setShowAllFiles(true);
      setSelectedProjectId(undefined);
    } else {
      setShowAllFiles(false);
      setSelectedProjectId(value);
    }
  }, []);  const handleRefresh = useCallback(() => {
    if (showAllFiles) {
      refreshFiles(undefined, filterType || undefined, searchText || undefined);
    } else {
      refreshFiles(selectedProjectId, filterType || undefined, searchText || undefined);
    }
  }, [refreshFiles, selectedProjectId, showAllFiles, filterType, searchText]);

  const getFileStatusTag = (status: string) => {
    const statusConfig = {
      UPLOADING: { color: 'processing', text: 'Uploading' },
      COMPLETED: { color: 'success', text: 'Completed' },
      FAILED: { color: 'error', text: 'Failed' },
    };
    
    const config = statusConfig[status as keyof typeof statusConfig] || 
                  { color: 'default', text: status };
    
    return <Tag color={config.color}>{config.text}</Tag>;
  };

  const getFileTypeTag = (filename: string) => {
    const category = getFileCategory(filename);
    const typeConfig = {
      image: { color: 'blue', text: 'Image' },
      video: { color: 'purple', text: 'Video' },
      unknown: { color: 'default', text: 'Unknown' },
    };
    
    const config = typeConfig[category];
    return <Tag color={config.color}>{config.text}</Tag>;
  };

  const columns: ColumnsType<UploadFile> = [
    {
      title: 'File Name',
      dataIndex: 'originalName',
      key: 'originalName',
      ellipsis: {
        showTitle: false,
      },
      render: (text: string, record: UploadFile) => (
        <Tooltip title={text}>
          <Space>
            {getFileCategory(text) === 'image' && (
              <Image
                width={32}
                height={32}
                src={uploadService.getThumbnailUrl(record.id)}
                fallback="data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAMIAAADDCAYAAADQvc6UAAABRWlDQ1BJQ0MgUHJvZmlsZQAAKJFjYGASSSwoyGFhYGDIzSspCnJ3UoiIjFJgf8LAwSDCIMogwMCcmFxc4BgQ4ANUwgCjUcG3awyMIPqyLsis7PPOq3QdDFcvjV3jOD1boQVTPQrgSkktTgbSf4A4LbmgqISBgTEFyFYuLykAsTuAbJEioKOA7DkgdjqEvQHEToKwj4DVhAQ5A9k3gGyB5IxEoBmML4BsnSQk8XQkNtReEOBxcfXxUQg1Mjc0dyHgXNJBSWpFCYh2zi+oLMpMzyhRcASGUqqCZ16yno6CkYGRAQMDKMwhqj/fAIcloxgHQqxAjIHBEugw5sUIsSQpBobtQPdLciLEVJYzMPBHMDBsayhILEqEO4DxG0txmrERhM29nYGBddr//5/DGRjYNRkY/l7////39v///y4Dmn+LgeHANwDrkl1AuO+pmgAAADhlWElmTU0AKgAAAAgAAYdpAAQAAAABAAAAGgAAAAAAAqACAAQAAAABAAAAwqADAAQAAAABAAAAwwAAAAD9b/HnAAAHlklEQVR4Ae3dP3Ik1xkE8Hm38MZGg/wnhgIBfgKHBIg9gQMfwKGBo4MjGTgwfAKnPoE9ggNDNxAlBwYODAyMDAhLYEAC7l+sLULn1Rn3/fVvHzTQRu3a"
                preview={false}
                style={{ borderRadius: 4 }}
              />
            )}
            <Text>{text}</Text>
          </Space>
        </Tooltip>
      ),
    },
    {
      title: 'Type',
      dataIndex: 'originalName',
      key: 'type',
      width: 80,
      render: (filename: string) => getFileTypeTag(filename),
    },
    {
      title: 'Size',
      dataIndex: 'fileSize',
      key: 'fileSize',
      width: 100,
      render: (size: number) => formatFileSize(size),
      sorter: (a, b) => a.fileSize - b.fileSize,
    },
    {
      title: 'Status',
      dataIndex: 'status',
      key: 'status',
      width: 100,
      render: (status: string) => getFileStatusTag(status),
    },
    {
      title: 'Upload Time',
      dataIndex: 'uploadTime',
      key: 'uploadTime',
      width: 180,
      render: (time: string) => formatDateTime(time),
      sorter: (a, b) => new Date(a.uploadTime).getTime() - new Date(b.uploadTime).getTime(),
    },
  ];

  if (showActions) {
    columns.push({
      title: 'Actions',
      key: 'actions',
      width: 150,      render: (_, record: UploadFile) => {
        // Find project for this file to check permissions
        const project = record.projectId ? projects.find(p => p.id === record.projectId) : null;
        const canAnalyze = project ? project.status === 'ACTIVE' : true;
        const canDelete = project ? (project.status === 'ACTIVE' || project.status === 'COMPLETED') : true;
        
        return (
          <Space size="small">
            <Tooltip title="Preview">
              <Button
                type="text"
                size="small"
                icon={<EyeOutlined />}
                onClick={() => handlePreview(record)}
              />
            </Tooltip>
            <Tooltip title={canAnalyze ? "Analyze" : "Analysis not allowed for this project status"}>
              <Button
                type="text"
                size="small"
                icon={<BarChartOutlined />}
                onClick={() => handleAnalyze(record)}
                disabled={record.status !== 'COMPLETED' || !record.md5Hash || !canAnalyze}
              />
            </Tooltip>
            <Tooltip title={canDelete ? "Delete" : "Delete not allowed for this project status"}>
              <Popconfirm
                title="Are you sure you want to delete this file?"
                onConfirm={() => handleDelete(record.id)}
                okText="Yes"
                cancelText="No"
                disabled={!canDelete}
              >
                <Button
                  type="text"
                  size="small"
                  icon={<DeleteOutlined />}
                  danger
                  disabled={!canDelete}
                />
              </Popconfirm>
            </Tooltip>
          </Space>
        );
      },
    });
  }

  const rowSelection = selectable ? {
    selectedRowKeys,
    onChange: (keys: React.Key[]) => {
      setSelectedRowKeys(keys as string[]);
      if (onFileSelect && keys.length > 0) {
        const selectedFile = files.find(file => file.id === keys[0]);
        if (selectedFile) {
          onFileSelect(selectedFile);
        }
      }
    },
    type: 'radio' as const,
  } : undefined;

  const getCurrentProjectName = () => {
    if (showAllFiles) return 'All Projects';
    if (!selectedProjectId) return 'No Project Selected';
    const project = projects.find(p => p.id === selectedProjectId);
    return project ? project.name : 'Unknown Project';
  };

  return (
    <Card
      title={
        <Space>
          <ProjectOutlined />
          <Title level={4} style={{ margin: 0 }}>
            Files - {getCurrentProjectName()} ({pagination.total})
          </Title>
        </Space>
      }
      extra={
        <Space>
          <Button
            icon={<ReloadOutlined />}
            onClick={handleRefresh}
            loading={loading}
          >
            Refresh
          </Button>
        </Space>
      }
    >
      <Row gutter={16} style={{ marginBottom: 16 }}>
        <Col span={6}>
          <Select
            placeholder="Select project..."
            allowClear={false}
            style={{ width: '100%' }}
            loading={projectsLoading}
            value={showAllFiles ? 'all' : selectedProjectId}
            onChange={handleProjectChange}
          >
            <Option value="all">🗂️ All Projects</Option>
            {projects.map(project => (
              <Option key={project.id} value={project.id}>
                📁 {project.name}
              </Option>
            ))}
          </Select>
        </Col>
        <Col span={8}>
          <Search
            placeholder="Search files..."
            allowClear
            enterButton={<SearchOutlined />}
            onSearch={handleSearch}
            onChange={(e) => setSearchText(e.target.value)}
            value={searchText}
          />
        </Col>
        <Col span={6}>
          <Select
            placeholder="Filter by type"
            allowClear
            style={{ width: '100%' }}
            onChange={handleFilterChange}
            value={filterType || undefined}
          >
            <Option value="">All Types</Option>
            <Option value="image">Images</Option>
            <Option value="video">Videos</Option>
          </Select>
        </Col>
      </Row>

      <Table
        columns={columns}
        dataSource={files}
        rowKey="id"
        loading={loading}
        pagination={{
          current: pagination.current,
          pageSize: pagination.pageSize,
          total: pagination.total,
          showSizeChanger: true,
          showQuickJumper: true,
          showTotal: (total, range) =>
            `${range[0]}-${range[1]} of ${total} files`,
          pageSizeOptions: ['10', '20', '50', '100'],
        }}
        onChange={handleTableChange}
        rowSelection={rowSelection}
        scroll={{ x: 800 }}
      />

      {/* File Preview Modal */}
      <Modal
        title={`Preview: ${previewFile?.originalName}`}
        open={!!previewFile}
        onCancel={() => setPreviewFile(null)}
        footer={null}
        width={800}
        centered
      >
        {previewFile && (
          <div style={{ textAlign: 'center' }}>
            {getFileCategory(previewFile.originalName) === 'image' ? (
              <Image
                src={uploadService.getPreviewUrl(previewFile.id)}
                alt={previewFile.originalName}
                style={{ maxWidth: '100%', maxHeight: 500 }}
                fallback="data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAMIAAADDCAYAAADQvc6UAAABRWlDQ1BJQ0MgUHJvZmlsZQAAKJFjYGASSSwoyGFhYGDIzSspCnJ3UoiIjFJgf8LAwSDCIMogwMCcmFxc4BgQ4ANUwgCjUcG3awyMIPqyLsis7PPOq3QdDFcvjV3jOD1boQVTPQrgSkktTgbSf4A4LbmgqISBgTEFyFYuLykAsTuAbJEioKOA7DkgdjqEvQHEToKwj4DVhAQ5A9k3gGyB5IxEoBmML4BsnSQk8XQkNtReEOBxcfXxUQg1Mjc0dyHgXNJBSWpFCYh2zi+oLMpMzyhRcASGUqqCZ16yno6CkYGRAQMDKMwhqj/fAIcloxgHQqxAjIHBEugw5sUIsSQpBobtQPdLciLEVJYzMPBHMDBsayhILEqEO4DxG0txmrERhM29nYGBddr//5/DGRjYNRkY/l7////39v///y4Dmn+LgeHANwDrkl1AuO+pmgAAADhlWElmTU0AKgAAAAgAAYdpAAQAAAABAAAAGgAAAAAAAqACAAQAAAABAAAAwqADAAQAAAABAAAAwwAAAAD9b/HnAAAHlklEQVR4Ae3dP3Ik1xkE8Hm38MZGg/wnhgIBfgKHBIg9gQMfwKGBo4MjGTgwfAKnPoE9ggNDNxAlBwYODAyMDAhLYEAC7l+sLULn1Rn3/fVvHzTQRu3a"
              />
            ) : (
              <div style={{ padding: 40 }}>
                <Text type="secondary">Video preview not available</Text>
                <br />
                <Text type="secondary">File: {previewFile.originalName}</Text>
                <br />
                <Text type="secondary">Size: {formatFileSize(previewFile.fileSize)}</Text>
              </div>
            )}
          </div>
        )}
      </Modal>
    </Card>
  );
};

export default FilesList;
