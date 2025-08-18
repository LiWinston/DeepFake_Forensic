import React, { useCallback, useState } from 'react';
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
} from 'antd';
import {
  DeleteOutlined,
  EyeOutlined,
  ReloadOutlined,
  SearchOutlined,
  BarChartOutlined,
} from '@ant-design/icons';
import type { ColumnsType, TablePaginationConfig } from 'antd/es/table';
import { useFilesList, useMetadataAnalysis } from '../hooks';
import { formatFileSize, formatDateTime, getFileCategory } from '../utils';
import type { UploadFile } from '../types';

const { Text, Title } = Typography;
const { Option } = Select;
const { Search } = Input;

interface FilesListProps {
  onFileSelect?: (file: UploadFile) => void;
  selectable?: boolean;
  showActions?: boolean;
}

const FilesList: React.FC<FilesListProps> = ({
  onFileSelect,
  selectable = false,
  showActions = true,
}) => {
  const { files, loading, pagination, loadFiles, deleteFile, refreshFiles } = useFilesList();
  const { analyzeFile } = useMetadataAnalysis();
  
  const [selectedRowKeys, setSelectedRowKeys] = useState<string[]>([]);
  const [previewFile, setPreviewFile] = useState<UploadFile | null>(null);
  const [filterType, setFilterType] = useState<string>('');
  const [searchText, setSearchText] = useState<string>('');

  const handleTableChange = useCallback((pagination: TablePaginationConfig) => {
    loadFiles(pagination.current, pagination.pageSize);
  }, [loadFiles]);

  const handleDelete = useCallback(async (fileId: string) => {
    await deleteFile(fileId);
  }, [deleteFile]);

  const handleAnalyze = useCallback(async (fileId: string) => {
    try {
      await analyzeFile(fileId);
    } catch (error) {
      console.error('Analysis failed:', error);
    }
  }, [analyzeFile]);

  const handlePreview = useCallback((file: UploadFile) => {
    setPreviewFile(file);
  }, []);

  const handleFilterChange = useCallback((value: string) => {
    setFilterType(value);
    loadFiles(1, pagination.pageSize);
  }, [loadFiles, pagination.pageSize]);

  const handleSearch = useCallback((value: string) => {
    setSearchText(value);
    // Implement search logic here
    // For now, we'll just filter by filename
    loadFiles(1, pagination.pageSize);
  }, [loadFiles, pagination.pageSize]);

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
                src={`/api/files/preview/${record.id}`}
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
      width: 150,
      render: (_, record: UploadFile) => (
        <Space size="small">
          <Tooltip title="Preview">
            <Button
              type="text"
              size="small"
              icon={<EyeOutlined />}
              onClick={() => handlePreview(record)}
            />
          </Tooltip>
          <Tooltip title="Analyze">
            <Button
              type="text"
              size="small"
              icon={<BarChartOutlined />}
              onClick={() => handleAnalyze(record.id)}
              disabled={record.status !== 'COMPLETED'}
            />
          </Tooltip>
          <Tooltip title="Delete">
            <Popconfirm
              title="Are you sure you want to delete this file?"
              onConfirm={() => handleDelete(record.id)}
              okText="Yes"
              cancelText="No"
            >
              <Button
                type="text"
                size="small"
                icon={<DeleteOutlined />}
                danger
              />
            </Popconfirm>
          </Tooltip>
        </Space>
      ),
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

  return (
    <Card
      title={
        <Space>
          <Title level={4} style={{ margin: 0 }}>
            Files ({pagination.total})
          </Title>
        </Space>
      }
      extra={
        <Space>
          <Button
            icon={<ReloadOutlined />}
            onClick={refreshFiles}
            loading={loading}
          >
            Refresh
          </Button>
        </Space>
      }
    >
      <Row gutter={16} style={{ marginBottom: 16 }}>
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
                src={`/api/files/preview/${previewFile.id}`}
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
