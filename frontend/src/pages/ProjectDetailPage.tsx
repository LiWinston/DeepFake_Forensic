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

  // 加载项目详情
  const loadProject = async () => {
    if (!projectId) return;
    
    try {
      const response = await projectApi.getProject(Number(projectId));
      setProject(response.data);
    } catch (error) {
      message.error('加载项目详情失败');
      navigate('/projects');
    }
  };

  // 加载分析任务
  const loadAnalysisTasks = async () => {
    if (!projectId) return;
    
    try {
      setLoading(true);
      const response = await analysisTaskApi.getProjectAnalysisTasks(Number(projectId));
      setAnalysisTasks(response.data);
    } catch (error) {
      message.error('加载分析任务失败');
    } finally {
      setLoading(false);
    }
  };

  // 加载任务统计
  const loadTaskStatistics = async () => {
    if (!projectId) return;
    
    try {
      const response = await analysisTaskApi.getAnalysisTaskStatistics(Number(projectId));
      setTaskStatistics(response.data);
    } catch (error) {
      console.error('加载任务统计失败:', error);
    }
  };

  useEffect(() => {
    loadProject();
    loadAnalysisTasks();
    loadTaskStatistics();
  }, [projectId]);

  // 创建分析任务
  const handleCreateTask = async (values: CreateAnalysisTaskRequest) => {
    try {
      await analysisTaskApi.createAnalysisTask({
        ...values,
        projectId: Number(projectId)
      });
      message.success('分析任务创建成功');
      setTaskModalVisible(false);
      form.resetFields();
      loadAnalysisTasks();
      loadTaskStatistics();
    } catch (error) {
      message.error('分析任务创建失败');
    }
  };

  // 开始任务
  const handleStartTask = async (taskId: number) => {
    try {
      await analysisTaskApi.startAnalysisTask(taskId);
      message.success('任务已开始');
      loadAnalysisTasks();
      loadTaskStatistics();
    } catch (error) {
      message.error('启动任务失败');
    }
  };

  // 取消任务
  const handleCancelTask = async (taskId: number) => {
    try {
      await analysisTaskApi.cancelAnalysisTask(taskId);
      message.success('任务已取消');
      loadAnalysisTasks();
      loadTaskStatistics();
    } catch (error) {
      message.error('取消任务失败');
    }
  };

  // 删除任务
  const handleDeleteTask = async (taskId: number) => {
    try {
      await analysisTaskApi.deleteAnalysisTask(taskId);
      message.success('任务删除成功');
      loadAnalysisTasks();
      loadTaskStatistics();
    } catch (error) {
      message.error('删除任务失败');
    }
  };

  // 获取任务状态颜色
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

  // 获取任务状态中文名
  const getTaskStatusLabel = (status: TaskStatus): string => {
    const labelMap: Record<TaskStatus, string> = {
      PENDING: '等待中',
      RUNNING: '运行中',
      COMPLETED: '已完成',
      FAILED: '失败',
      CANCELLED: '已取消',
      PAUSED: '已暂停'
    };
    return labelMap[status] || status;
  };

  // 获取分析类型中文名
  const getAnalysisTypeLabel = (type: AnalysisType): string => {
    const labelMap: Record<AnalysisType, string> = {
      METADATA_ANALYSIS: '元数据分析',
      DEEPFAKE_DETECTION: '深度伪造检测',
      EDIT_DETECTION: '编辑痕迹检测',
      COMPRESSION_ANALYSIS: '压缩分析',
      HASH_VERIFICATION: '哈希验证',
      EXIF_ANALYSIS: 'EXIF数据分析',
      STEGANOGRAPHY_DETECTION: '隐写术检测',
      SIMILARITY_ANALYSIS: '相似性分析',
      TEMPORAL_ANALYSIS: '时间序列分析',
      QUALITY_ASSESSMENT: '质量评估'
    };
    return labelMap[type] || type;
  };

  const taskColumns: ColumnsType<AnalysisTask> = [
    {
      title: '任务名称',
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
      title: '分析类型',
      dataIndex: 'analysisType',
      key: 'analysisType',
      width: 150,
      render: (type: AnalysisType) => (
        <Tag>{getAnalysisTypeLabel(type)}</Tag>
      ),
    },
    {
      title: '状态',
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
      title: '置信度',
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
      title: '创建时间',
      dataIndex: 'createdAt',
      key: 'createdAt',
      width: 120,
      render: (createdAt) => dayjs(createdAt).format('MM-DD HH:mm'),
    },
    {
      title: '完成时间',
      dataIndex: 'completedAt',
      key: 'completedAt',
      width: 120,
      render: (completedAt) => 
        completedAt ? dayjs(completedAt).format('MM-DD HH:mm') : '-',
    },
    {
      title: '操作',
      key: 'action',
      width: 200,
      render: (_, record) => (
        <Space size="small">
          {record.status === 'PENDING' && (
            <Tooltip title="开始任务">
              <Button
                type="link"
                icon={<PlayCircleOutlined />}
                onClick={() => handleStartTask(record.id)}
              />
            </Tooltip>
          )}
          
          {(record.status === 'RUNNING' || record.status === 'PENDING') && (
            <Tooltip title="取消任务">
              <Popconfirm
                title="确认取消此任务？"
                onConfirm={() => handleCancelTask(record.id)}
                okText="确认"
                cancelText="取消"
              >
                <Button
                  type="link"
                  icon={<StopOutlined />}
                />
              </Popconfirm>
            </Tooltip>
          )}
          
          <Tooltip title="删除任务">
            <Popconfirm
              title="确认删除此任务？此操作不可逆转。"
              onConfirm={() => handleDeleteTask(record.id)}
              okText="确认"
              cancelText="取消"
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
    return <div>加载中...</div>;
  }

  return (
    <div style={{ padding: '24px' }}>
      {/* 项目信息头部 */}
      <Card style={{ marginBottom: 24 }}>
        <Row>
          <Col span={24}>
            <Space>
              <Button 
                icon={<ArrowLeftOutlined />} 
                onClick={() => navigate('/projects')}
              >
                返回项目列表
              </Button>
              <Title level={3} style={{ margin: 0 }}>
                {project.name}
              </Title>
              <Tag color="blue">{project.caseNumber}</Tag>
            </Space>
          </Col>
        </Row>
        
        <Descriptions style={{ marginTop: 16 }} column={3}>
          <Descriptions.Item label="项目类型">
            <Tag>{project.projectType}</Tag>
          </Descriptions.Item>
          <Descriptions.Item label="状态">
            <Tag color="processing">{project.status}</Tag>
          </Descriptions.Item>
          <Descriptions.Item label="委托方">
            {project.clientName || '-'}
          </Descriptions.Item>
          <Descriptions.Item label="截止日期">
            {project.deadline ? dayjs(project.deadline).format('YYYY-MM-DD') : '-'}
          </Descriptions.Item>
          <Descriptions.Item label="案件日期">
            {project.caseDate ? dayjs(project.caseDate).format('YYYY-MM-DD') : '-'}
          </Descriptions.Item>
          <Descriptions.Item label="创建时间">
            {dayjs(project.createdAt).format('YYYY-MM-DD HH:mm')}
          </Descriptions.Item>
        </Descriptions>
        
        {project.description && (
          <div style={{ marginTop: 16 }}>
            <strong>项目描述：</strong>
            <div style={{ marginTop: 8 }}>{project.description}</div>
          </div>
        )}
      </Card>

      <Tabs defaultActiveKey="tasks">
        <TabPane tab="分析任务" key="tasks">
          {/* 任务统计 */}
          <Row gutter={16} style={{ marginBottom: 24 }}>
            <Col span={5}>
              <Card>
                <Statistic
                  title="总任务数"
                  value={taskStatistics.totalTasks}
                />
              </Card>
            </Col>
            <Col span={5}>
              <Card>
                <Statistic
                  title="等待中"
                  value={taskStatistics.pendingTasks}
                  valueStyle={{ color: '#8c8c8c' }}
                />
              </Card>
            </Col>
            <Col span={5}>
              <Card>
                <Statistic
                  title="运行中"
                  value={taskStatistics.runningTasks}
                  valueStyle={{ color: '#1890ff' }}
                />
              </Card>
            </Col>
            <Col span={5}>
              <Card>
                <Statistic
                  title="已完成"
                  value={taskStatistics.completedTasks}
                  valueStyle={{ color: '#52c41a' }}
                />
              </Card>
            </Col>
            <Col span={4}>
              <Card>
                <Statistic
                  title="失败"
                  value={taskStatistics.failedTasks}
                  valueStyle={{ color: '#ff4d4f' }}
                />
              </Card>
            </Col>
          </Row>

          {/* 分析任务表格 */}
          <Card
            title="分析任务"
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
                新建分析任务
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
                showTotal: (total) => `共 ${total} 个任务`,
              }}
            />
          </Card>
        </TabPane>

        <TabPane tab="项目文件" key="files">
          {/* TODO: 这里可以添加项目相关文件的管理 */}
          <Card title="项目文件">
            <div style={{ textAlign: 'center', padding: '50px', color: '#999' }}>
              文件管理功能开发中...
            </div>
          </Card>
        </TabPane>
      </Tabs>

      {/* 创建分析任务模态框 */}
      <Modal
        title="新建分析任务"
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
            label="任务名称"
          >
            <Input placeholder="可选，系统将自动生成" />
          </Form.Item>

          <Form.Item
            name="analysisType"
            label="分析类型"
            rules={[{ required: true, message: '请选择分析类型' }]}
          >
            <Select placeholder="请选择分析类型">
              <Option value="METADATA_ANALYSIS">元数据分析</Option>
              <Option value="DEEPFAKE_DETECTION">深度伪造检测</Option>
              <Option value="EDIT_DETECTION">编辑痕迹检测</Option>
              <Option value="COMPRESSION_ANALYSIS">压缩分析</Option>
              <Option value="HASH_VERIFICATION">哈希验证</Option>
              <Option value="EXIF_ANALYSIS">EXIF数据分析</Option>
              <Option value="STEGANOGRAPHY_DETECTION">隐写术检测</Option>
              <Option value="SIMILARITY_ANALYSIS">相似性分析</Option>
              <Option value="TEMPORAL_ANALYSIS">时间序列分析</Option>
              <Option value="QUALITY_ASSESSMENT">质量评估</Option>
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
