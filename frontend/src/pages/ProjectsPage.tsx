import React, { useState, useEffect } from 'react';
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
  DatePicker,
  message,
  Popconfirm,
  Tooltip,
  Row,
  Col,
  Statistic
} from 'antd';
import {
  PlusOutlined,
  EditOutlined,
  DeleteOutlined,
  InboxOutlined,
  SearchOutlined,
  CalendarOutlined,
  UserOutlined
} from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import dayjs from 'dayjs';
import { projectApi } from '../services/project';
import type { Project, CreateProjectRequest, ProjectType, ProjectStatus } from '../types';

const { Option } = Select;
const { TextArea } = Input;

const ProjectsPage: React.FC = () => {
  const [projects, setProjects] = useState<Project[]>([]);
  const [loading, setLoading] = useState(false);
  const [modalVisible, setModalVisible] = useState(false);
  const [editingProject, setEditingProject] = useState<Project | null>(null);
  const [form] = Form.useForm();
  const [searchKeyword, setSearchKeyword] = useState('');
  const [filterStatus, setFilterStatus] = useState<ProjectStatus | 'ALL'>('ALL');
  const [statistics, setStatistics] = useState({
    totalProjects: 0,
    activeProjects: 0,
    completedProjects: 0,
    archivedProjects: 0
  });

  // 加载项目列表
  const loadProjects = async () => {
    try {
      setLoading(true);
      let response;
      
      if (searchKeyword) {
        response = await projectApi.searchProjects(searchKeyword);
      } else if (filterStatus !== 'ALL') {
        response = await projectApi.getProjectsByStatus(filterStatus);
      } else {
        response = await projectApi.getProjects();
      }
      
      setProjects(response.data);
    } catch (error) {
      message.error('加载项目列表失败');
    } finally {
      setLoading(false);
    }
  };

  // 加载统计数据
  const loadStatistics = async () => {
    try {
      const response = await projectApi.getProjectStatistics();
      setStatistics(response.data);
    } catch (error) {
      console.error('加载统计数据失败:', error);
    }
  };

  useEffect(() => {
    loadProjects();
    loadStatistics();
  }, [searchKeyword, filterStatus]);

  // 创建或更新项目
  const handleSubmit = async (values: CreateProjectRequest) => {
    try {
      if (editingProject) {
        await projectApi.updateProject(editingProject.id, values);
        message.success('项目更新成功');
      } else {
        await projectApi.createProject(values);
        message.success('项目创建成功');
      }
      setModalVisible(false);
      setEditingProject(null);
      form.resetFields();
      loadProjects();
      loadStatistics();
    } catch (error) {
      message.error(editingProject ? '项目更新失败' : '项目创建失败');
    }
  };

  // 删除项目
  const handleDelete = async (projectId: number) => {
    try {
      await projectApi.deleteProject(projectId);
      message.success('项目删除成功');
      loadProjects();
      loadStatistics();
    } catch (error) {
      message.error('项目删除失败');
    }
  };

  // 归档项目
  const handleArchive = async (projectId: number) => {
    try {
      await projectApi.archiveProject(projectId);
      message.success('项目归档成功');
      loadProjects();
      loadStatistics();
    } catch (error) {
      message.error('项目归档失败');
    }
  };

  // 打开编辑模态框
  const handleEdit = (project: Project) => {
    setEditingProject(project);
    form.setFieldsValue({
      ...project,
      deadline: project.deadline ? dayjs(project.deadline) : null,
      caseDate: project.caseDate ? dayjs(project.caseDate) : null,
    });
    setModalVisible(true);
  };

  // 项目类型标签颜色
  const getProjectTypeColor = (type: ProjectType): string => {
    const colorMap: Record<ProjectType, string> = {
      GENERAL: 'default',
      CRIMINAL: 'red',
      CIVIL: 'blue',
      CORPORATE: 'green',
      ACADEMIC_RESEARCH: 'purple'
    };
    return colorMap[type] || 'default';
  };

  // 项目状态标签颜色
  const getProjectStatusColor = (status: ProjectStatus): string => {
    const colorMap: Record<ProjectStatus, string> = {
      ACTIVE: 'processing',
      COMPLETED: 'success',
      SUSPENDED: 'warning',
      ARCHIVED: 'default'
    };
    return colorMap[status] || 'default';
  };

  // 项目类型中文名
  const getProjectTypeLabel = (type: ProjectType): string => {
    const labelMap: Record<ProjectType, string> = {
      GENERAL: '一般调查',
      CRIMINAL: '刑事案件',
      CIVIL: '民事案件',
      CORPORATE: '企业调查',
      ACADEMIC_RESEARCH: '学术研究'
    };
    return labelMap[type] || type;
  };

  // 项目状态中文名
  const getProjectStatusLabel = (status: ProjectStatus): string => {
    const labelMap: Record<ProjectStatus, string> = {
      ACTIVE: '进行中',
      COMPLETED: '已完成',
      SUSPENDED: '暂停',
      ARCHIVED: '已归档'
    };
    return labelMap[status] || status;
  };

  const columns: ColumnsType<Project> = [
    {
      title: '项目名称',
      dataIndex: 'name',
      key: 'name',
      width: 200,
      ellipsis: {
        showTitle: false,
      },
      render: (name) => (
        <Tooltip placement="topLeft" title={name}>
          {name}
        </Tooltip>
      ),
    },
    {
      title: '案件编号',
      dataIndex: 'caseNumber',
      key: 'caseNumber',
      width: 120,
    },
    {
      title: '项目类型',
      dataIndex: 'projectType',
      key: 'projectType',
      width: 100,
      render: (type: ProjectType) => (
        <Tag color={getProjectTypeColor(type)}>
          {getProjectTypeLabel(type)}
        </Tag>
      ),
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 100,
      render: (status: ProjectStatus) => (
        <Tag color={getProjectStatusColor(status)}>
          {getProjectStatusLabel(status)}
        </Tag>
      ),
    },
    {
      title: '委托方',
      dataIndex: 'clientName',
      key: 'clientName',
      width: 120,
      ellipsis: true,
    },
    {
      title: '截止日期',
      dataIndex: 'deadline',
      key: 'deadline',
      width: 120,
      render: (deadline) => 
        deadline ? dayjs(deadline).format('YYYY-MM-DD') : '-',
    },
    {
      title: '创建时间',
      dataIndex: 'createdAt',
      key: 'createdAt',
      width: 120,
      render: (createdAt) => dayjs(createdAt).format('YYYY-MM-DD'),
    },
    {
      title: '操作',
      key: 'action',
      width: 180,
      render: (_, record) => (
        <Space size="small">
          <Tooltip title="编辑">
            <Button
              type="link"
              icon={<EditOutlined />}
              onClick={() => handleEdit(record)}
            />
          </Tooltip>
          
          {record.status !== 'ARCHIVED' && (
            <Tooltip title="归档">
              <Popconfirm
                title="确认归档此项目？"
                onConfirm={() => handleArchive(record.id)}
                okText="确认"
                cancelText="取消"
              >
                <Button
                  type="link"
                  icon={<InboxOutlined />}
                />
              </Popconfirm>
            </Tooltip>
          )}
          
          <Tooltip title="删除">
            <Popconfirm
              title="确认删除此项目？此操作不可逆转。"
              onConfirm={() => handleDelete(record.id)}
              okText="确认"
              cancelText="取消"
            >
              <Button
                type="link"
                danger
                icon={<DeleteOutlined />}
              />
            </Popconfirm>
          </Tooltip>
        </Space>
      ),
    },
  ];

  return (
    <div style={{ padding: '24px' }}>
      {/* 统计卡片 */}
      <Row gutter={16} style={{ marginBottom: 24 }}>
        <Col span={6}>
          <Card>
            <Statistic
              title="总项目数"
              value={statistics.totalProjects}
              prefix={<UserOutlined />}
            />
          </Card>
        </Col>
        <Col span={6}>
          <Card>
            <Statistic
              title="进行中"
              value={statistics.activeProjects}
              prefix={<CalendarOutlined />}
              valueStyle={{ color: '#1890ff' }}
            />
          </Card>
        </Col>
        <Col span={6}>
          <Card>
            <Statistic
              title="已完成"
              value={statistics.completedProjects}
              valueStyle={{ color: '#52c41a' }}
            />
          </Card>
        </Col>
        <Col span={6}>
          <Card>
            <Statistic
              title="已归档"
              value={statistics.archivedProjects}
              valueStyle={{ color: '#8c8c8c' }}
            />
          </Card>
        </Col>
      </Row>

      <Card
        title="项目管理"
        extra={
          <Space>
            {/* 搜索框 */}
            <Input
              placeholder="搜索项目..."
              prefix={<SearchOutlined />}
              value={searchKeyword}
              onChange={(e) => setSearchKeyword(e.target.value)}
              style={{ width: 200 }}
              allowClear
            />
            
            {/* 状态筛选 */}
            <Select
              value={filterStatus}
              onChange={setFilterStatus}
              style={{ width: 120 }}
            >
              <Option value="ALL">全部状态</Option>
              <Option value="ACTIVE">进行中</Option>
              <Option value="COMPLETED">已完成</Option>
              <Option value="SUSPENDED">暂停</Option>
              <Option value="ARCHIVED">已归档</Option>
            </Select>
            
            {/* 新建按钮 */}
            <Button
              type="primary"
              icon={<PlusOutlined />}
              onClick={() => {
                setEditingProject(null);
                form.resetFields();
                setModalVisible(true);
              }}
            >
              新建项目
            </Button>
          </Space>
        }
      >
        <Table
          columns={columns}
          dataSource={projects}
          rowKey="id"
          loading={loading}
          pagination={{
            pageSize: 10,
            showSizeChanger: true,
            showQuickJumper: true,
            showTotal: (total) => `共 ${total} 个项目`,
          }}
        />
      </Card>

      {/* 创建/编辑项目模态框 */}
      <Modal
        title={editingProject ? '编辑项目' : '新建项目'}
        open={modalVisible}
        onCancel={() => {
          setModalVisible(false);
          setEditingProject(null);
          form.resetFields();
        }}
        footer={null}
        width={800}
      >
        <Form
          form={form}
          layout="vertical"
          onFinish={handleSubmit}
        >
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item
                name="name"
                label="项目名称"
                rules={[{ required: true, message: '请输入项目名称' }]}
              >
                <Input placeholder="请输入项目名称" />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item
                name="caseNumber"
                label="案件编号"
              >
                <Input placeholder="请输入案件编号（可选）" />
              </Form.Item>
            </Col>
          </Row>

          <Row gutter={16}>
            <Col span={12}>
              <Form.Item
                name="projectType"
                label="项目类型"
                initialValue="GENERAL"
              >
                <Select>
                  <Option value="GENERAL">一般调查</Option>
                  <Option value="CRIMINAL">刑事案件</Option>
                  <Option value="CIVIL">民事案件</Option>
                  <Option value="CORPORATE">企业调查</Option>
                  <Option value="ACADEMIC_RESEARCH">学术研究</Option>
                </Select>
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item
                name="clientName"
                label="委托方"
              >
                <Input placeholder="请输入委托方名称" />
              </Form.Item>
            </Col>
          </Row>

          <Row gutter={16}>
            <Col span={12}>
              <Form.Item
                name="clientContact"
                label="委托方联系方式"
              >
                <Input placeholder="请输入联系方式" />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item
                name="deadline"
                label="截止日期"
              >
                <DatePicker style={{ width: '100%' }} />
              </Form.Item>
            </Col>
          </Row>

          <Form.Item
            name="description"
            label="项目描述"
          >
            <TextArea rows={3} placeholder="请输入项目描述" />
          </Form.Item>

          <Form.Item
            name="evidenceDescription"
            label="证据描述"
          >
            <TextArea rows={2} placeholder="请描述相关证据" />
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
                setModalVisible(false);
                setEditingProject(null);
                form.resetFields();
              }}>
                取消
              </Button>
              <Button type="primary" htmlType="submit">
                {editingProject ? '更新' : '创建'}
              </Button>
            </Space>
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};

export default ProjectsPage;
