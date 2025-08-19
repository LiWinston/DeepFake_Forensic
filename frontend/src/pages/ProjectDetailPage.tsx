import React, { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import {
  Card,
  Table,
  Button,
  Space,
  Tag,
  Modal,
  Form,
  Input,
  Select,
  message,
  Popconfirm,
  Tooltip,
  Row,
  Col,
  Statistic,
  Progress,
  Typography,
  Descriptions,
  Tabs
} from 'antd';
import {
  PlusOutlined,
  EditOutlined,
  DeleteOutlined,
  PlayCircleOutlined,
  StopOutlined,
  CheckCircleOutlined,
  CloseCircleOutlined,
  PauseCircleOutlined,
  ArrowLeftOutlined
} from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import dayjs from 'dayjs';
import { projectApi, analysisTaskApi } from '../services/project';
import type { 
  Project, 
  AnalysisTask, 
  CreateAnalysisTaskRequest, 
  AnalysisType, 
  TaskStatus 
} from '../types';

const { Option } = Select;
const { TextArea } = Input;
const { Title } = Typography;
const { TabPane } = Tabs;

const ProjectDetailPage: React.FC = () => {
  const { projectId } = useParams<{ projectId: string }>();
  const navigate = useNavigate();
  const [project, setProject] = useState<Project | null>(null);
  const [analysisTasks, setAnalysisTasks] = useState<AnalysisTask[]>([]);
  const [loading, setLoading] = useState(false);
  const [taskModalVisible, setTaskModalVisible] = useState(false);
  const [editingTask, setEditingTask] = useState<AnalysisTask | null>(null);
  const [form] = Form.useForm();
  const [taskStatistics, setTaskStatistics] = useState({
    totalTasks: 0,
    pendingTasks: 0,
    runningTasks: 0,
    completedTasks: 0,
    failedTasks: 0
  });

  // Load project details
  const loadProject = async () => {
    if (!projectId) return;
    
    try {
      const response = await projectApi.getProject(Number(projectId));
      setProject(response.data);
    } catch (error) {
      message.error('Failed to load project details');
      navigate('/projects');
    }
  };

  // Load analysis tasks
  const loadAnalysisTasks = async () => {
    if (!projectId) return;
    
    try {
      setLoading(true);
      const response = await analysisTaskApi.getProjectAnalysisTasks(Number(projectId));
      setAnalysisTasks(response.data);
    } catch (error) {
      message.error('Failed to load analysis tasks');
    } finally {
      setLoading(false);
    }
  };

  // Load task statistics
  const loadTaskStatistics = async () => {
    if (!projectId) return;
    
    try {
      const response = await analysisTaskApi.getAnalysisTaskStatistics(Number(projectId));
      setTaskStatistics(response.data);
    } catch (error) {
      console.error('Failed to load task statistics:', error);
    }
  };

  useEffect(() => {
    loadProject();
    loadAnalysisTasks();
    loadTaskStatistics();
  }, [projectId]);

  // Create analysis task
  const handleCreateTask = async (values: CreateAnalysisTaskRequest) => {
    try {
      await analysisTaskApi.createAnalysisTask({
        ...values,
        projectId: Number(projectId)
      });
      message.success('Analysis task created successfully');
      setTaskModalVisible(false);
      form.resetFields();
      loadAnalysisTasks();
      loadTaskStatistics();
    } catch (error) {
      message.error('Failed to create analysis task');
    }
  };

  // Start task
  const handleStartTask = async (taskId: number) => {
    try {
      await analysisTaskApi.startAnalysisTask(taskId);
      message.success('Task started');
      loadAnalysisTasks();
      loadTaskStatistics();
    } catch (error) {
      message.error('Failed to start task');
    }
  };

  // Cancel task
  const handleCancelTask = async (taskId: number) => {
    try {
      await analysisTaskApi.cancelAnalysisTask(taskId);
      message.success('Task cancelled');
      loadAnalysisTasks();
      loadTaskStatistics();
    } catch (error) {
      message.error('Failed to cancel task');
    }
  };

  // Delete task
  const handleDeleteTask = async (taskId: number) => {
    try {
      await analysisTaskApi.deleteAnalysisTask(taskId);
      message.success('Task deleted successfully');
      loadAnalysisTasks();
      loadTaskStatistics();
    } catch (error) {
      message.error('Failed to delete task');
    }
  };

  // Get task status color
  const getTaskStatusColor = (status: TaskStatus): string => {
    const colorMap: Record<TaskStatus, string> = {
      PENDING: 'default',
      RUNNING: 'processing',
      COMPLETED: 'success',
      FAILED: 'error',
      CANCELLED: 'warning',
      PAUSED: 'warning'
    };
    return colorMap[status] || 'default';
  };

  // Get task status display names
  const getTaskStatusLabel = (status: TaskStatus): string => {
    const labelMap: Record<TaskStatus, string> = {
      PENDING: 'Pending',
      RUNNING: 'Running',
      COMPLETED: 'Completed',
      FAILED: 'Failed',
      CANCELLED: 'Cancelled',
      PAUSED: 'Paused'
    };
    return labelMap[status] || status;
  };

  // Get analysis type display names
  const getAnalysisTypeLabel = (type: AnalysisType): string => {
    const labelMap: Record<AnalysisType, string> = {
      METADATA_ANALYSIS: 'Metadata Analysis',
      DEEPFAKE_DETECTION: 'Deepfake Detection',
      EDIT_DETECTION: 'Edit Detection',
      COMPRESSION_ANALYSIS: 'Compression Analysis',
      HASH_VERIFICATION: 'Hash Verification',
      EXIF_ANALYSIS: 'EXIF Data Analysis',
      STEGANOGRAPHY_DETECTION: 'Steganography Detection',
      SIMILARITY_ANALYSIS: 'Similarity Analysis',
      TEMPORAL_ANALYSIS: 'Temporal Analysis',
      QUALITY_ASSESSMENT: 'Quality Assessment'
    };
    return labelMap[type] || type;
  };

  const taskColumns: ColumnsType<AnalysisTask> = [
    {
      title: 'Task Name',
      dataIndex: 'taskName',
      key: 'taskName',
      width: 200,
      ellipsis: {
        showTitle: false,
      },
      render: (taskName) => (
        <Tooltip placement="topLeft" title={taskName}>
          {taskName}
        </Tooltip>
      ),
    },
    {
      title: 'Analysis Type',
      dataIndex: 'analysisType',
      key: 'analysisType',
      width: 150,
      render: (type: AnalysisType) => (
        <Tag>{getAnalysisTypeLabel(type)}</Tag>
      ),
    },
    {
      title: 'Status',
      dataIndex: 'status',
      key: 'status',
      width: 100,
      render: (status: TaskStatus) => (
        <Tag color={getTaskStatusColor(status)}>
          {getTaskStatusLabel(status)}
        </Tag>
      ),
    },
    {
      title: 'Confidence',
      dataIndex: 'confidenceScore',
      key: 'confidenceScore',
      width: 120,
      render: (score) => {
        if (score !== null && score !== undefined) {
          return <Progress percent={Math.round(score * 100)} size="small" />;
        }
        return '-';
      },
    },
    {
      title: 'Created Time',
      dataIndex: 'createdAt',
      key: 'createdAt',
      width: 120,
      render: (createdAt) => dayjs(createdAt).format('MM-DD HH:mm'),
    },
    {
      title: 'Completed Time',
      dataIndex: 'completedAt',
      key: 'completedAt',
      width: 120,
      render: (completedAt) => 
        completedAt ? dayjs(completedAt).format('MM-DD HH:mm') : '-',
    },
    {
      title: 'Actions',
      key: 'action',
      width: 200,
      render: (_, record) => (
        <Space size="small">
          {record.status === 'PENDING' && (
            <Tooltip title="Start Task">
              <Button
                type="link"
                icon={<PlayCircleOutlined />}
                onClick={() => handleStartTask(record.id)}
              />
            </Tooltip>
          )}
          
          {(record.status === 'RUNNING' || record.status === 'PENDING') && (
            <Tooltip title="Cancel Task">
              <Popconfirm
                title="Confirm canceling this task?"
                onConfirm={() => handleCancelTask(record.id)}
                okText="Confirm"
                cancelText="Cancel"
              >
                <Button
                  type="link"
                  icon={<StopOutlined />}
                />
              </Popconfirm>
            </Tooltip>
          )}
          
          <Tooltip title="Delete Task">
            <Popconfirm
              title="Confirm deleting this task? This action cannot be undone."
              onConfirm={() => handleDeleteTask(record.id)}
              okText="Confirm"
              cancelText="Cancel"
            >
              <Button
                type="link"
                danger
                icon={<DeleteOutlined />}
                disabled={record.status === 'RUNNING'}
              />
            </Popconfirm>
          </Tooltip>
        </Space>
      ),
    },
  ];

  if (!project) {
    return <div>Loading...</div>;
  }

  return (
    <div style={{ padding: '24px' }}>
      {/* Project information header */}
      <Card style={{ marginBottom: 24 }}>
        <Row>
          <Col span={24}>
            <Space>
              <Button 
                icon={<ArrowLeftOutlined />} 
                onClick={() => navigate('/projects')}
              >
                Back to Project List
              </Button>
              <Title level={3} style={{ margin: 0 }}>
                {project.name}
              </Title>
              <Tag color="blue">{project.caseNumber}</Tag>
            </Space>
          </Col>
        </Row>
        
        <Descriptions style={{ marginTop: 16 }} column={3}>
          <Descriptions.Item label="Project Type">
            <Tag>{project.projectType}</Tag>
          </Descriptions.Item>
          <Descriptions.Item label="Status">
            <Tag color="processing">{project.status}</Tag>
          </Descriptions.Item>
          <Descriptions.Item label="Client">
            {project.clientName || '-'}
          </Descriptions.Item>
          <Descriptions.Item label="Deadline">
            {project.deadline ? dayjs(project.deadline).format('YYYY-MM-DD') : '-'}
          </Descriptions.Item>
          <Descriptions.Item label="Case Date">
            {project.caseDate ? dayjs(project.caseDate).format('YYYY-MM-DD') : '-'}
          </Descriptions.Item>
          <Descriptions.Item label="Created Time">
            {dayjs(project.createdAt).format('YYYY-MM-DD HH:mm')}
          </Descriptions.Item>
        </Descriptions>
        
        {project.description && (
          <div style={{ marginTop: 16 }}>
            <strong>Project Description:</strong>
            <div style={{ marginTop: 8 }}>{project.description}</div>
          </div>
        )}
      </Card>

      <Tabs defaultActiveKey="tasks">
        <TabPane tab="Analysis Tasks" key="tasks">
          {/* Task statistics */}
          <Row gutter={16} style={{ marginBottom: 24 }}>
            <Col span={5}>
              <Card>
                <Statistic
                  title="Total Tasks"
                  value={taskStatistics.totalTasks}
                />
              </Card>
            </Col>
            <Col span={5}>
              <Card>
                <Statistic
                  title="Pending"
                  value={taskStatistics.pendingTasks}
                  valueStyle={{ color: '#8c8c8c' }}
                />
              </Card>
            </Col>
            <Col span={5}>
              <Card>
                <Statistic
                  title="Running"
                  value={taskStatistics.runningTasks}
                  valueStyle={{ color: '#1890ff' }}
                />
              </Card>
            </Col>
            <Col span={5}>
              <Card>
                <Statistic
                  title="Completed"
                  value={taskStatistics.completedTasks}
                  valueStyle={{ color: '#52c41a' }}
                />
              </Card>
            </Col>
            <Col span={4}>
              <Card>
                <Statistic
                  title="Failed"
                  value={taskStatistics.failedTasks}
                  valueStyle={{ color: '#ff4d4f' }}
                />
              </Card>
            </Col>
          </Row>

          {/* Analysis task table */}
          <Card
            title="Analysis Tasks"
            extra={
              <Button
                type="primary"
                icon={<PlusOutlined />}
                onClick={() => {
                  setEditingTask(null);
                  form.resetFields();
                  setTaskModalVisible(true);
                }}
              >
                Create Analysis Task
              </Button>
            }
          >
            <Table
              columns={taskColumns}
              dataSource={analysisTasks}
              rowKey="id"
              loading={loading}
              pagination={{
                pageSize: 10,
                showSizeChanger: true,
                showTotal: (total) => `Total ${total} tasks`,
              }}
            />
          </Card>
        </TabPane>

        <TabPane tab="Project Files" key="files">
          {/* TODO: Add project file management here */}
          <Card title="Project Files">
            <div style={{ textAlign: 'center', padding: '50px', color: '#999' }}>
              File management feature under development...
            </div>
          </Card>
        </TabPane>
      </Tabs>

      {/* Create analysis task modal */}
      <Modal
        title="Create Analysis Task"
        open={taskModalVisible}
        onCancel={() => {
          setTaskModalVisible(false);
          setEditingTask(null);
          form.resetFields();
        }}
        footer={null}
        width={600}
      >
        <Form
          form={form}
          layout="vertical"
          onFinish={handleCreateTask}
        >
          <Form.Item
            name="taskName"
            label="Task Name"
          >
            <Input placeholder="Optional, system will auto-generate" />
          </Form.Item>

          <Form.Item
            name="analysisType"
            label="Analysis Type"
            rules={[{ required: true, message: 'Please select analysis type' }]}
          >
            <Select placeholder="Please select analysis type">
              <Option value="METADATA_ANALYSIS">Metadata Analysis</Option>
              <Option value="DEEPFAKE_DETECTION">Deepfake Detection</Option>
              <Option value="EDIT_DETECTION">Edit Detection</Option>
              <Option value="COMPRESSION_ANALYSIS">Compression Analysis</Option>
              <Option value="HASH_VERIFICATION">Hash Verification</Option>
              <Option value="EXIF_ANALYSIS">EXIF Data Analysis</Option>
              <Option value="STEGANOGRAPHY_DETECTION">Steganography Detection</Option>
              <Option value="SIMILARITY_ANALYSIS">Similarity Analysis</Option>
              <Option value="TEMPORAL_ANALYSIS">Temporal Analysis</Option>
              <Option value="QUALITY_ASSESSMENT">Quality Assessment</Option>
            </Select>
          </Form.Item>

          <Form.Item
            name="description"
            label="任务描述"
          >
            <TextArea rows={3} placeholder="请输入任务描述" />
          </Form.Item>

          <Form.Item
            name="notes"
            label="备注"
          >
            <TextArea rows={2} placeholder="请输入备注信息" />
          </Form.Item>

          <Form.Item style={{ textAlign: 'right', marginBottom: 0 }}>
            <Space>
              <Button onClick={() => {
                setTaskModalVisible(false);
                setEditingTask(null);
                form.resetFields();
              }}>
                取消
              </Button>
              <Button type="primary" htmlType="submit">
                创建任务
              </Button>
            </Space>
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};

export default ProjectDetailPage;
