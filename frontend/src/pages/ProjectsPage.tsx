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
  UserOutlined,
  PlayCircleOutlined,
  PauseCircleOutlined,
  CheckCircleOutlined,
  ReloadOutlined
} from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import dayjs from 'dayjs';
import { projectApi } from '../services/project';
import { useProjectContext } from '../contexts/ProjectContext';
import type { Project, CreateProjectRequest, ProjectType, ProjectStatus } from '../types';

const { Option } = Select;
const { TextArea } = Input;
const { RangePicker } = DatePicker;

const ProjectsPage: React.FC = () => {
  // Use shared project context for active projects, but maintain local state for all projects
  const { addProject: addToActiveProjects, updateProject: updateActiveProject, removeProject: removeFromActiveProjects } = useProjectContext();
  
  const [projects, setProjects] = useState<Project[]>([]);
  const [loading, setLoading] = useState(false);
  const [modalVisible, setModalVisible] = useState(false);
  const [editingProject, setEditingProject] = useState<Project | null>(null);
  const [form] = Form.useForm();  const [searchKeyword, setSearchKeyword] = useState('');
  const [filterStatus, setFilterStatus] = useState<ProjectStatus | 'ALL'>('ALL');
  const [createdDateFilter, setCreatedDateFilter] = useState<[dayjs.Dayjs | null, dayjs.Dayjs | null] | null>(null);
  const [deadlineDateFilter, setDeadlineDateFilter] = useState<[dayjs.Dayjs | null, dayjs.Dayjs | null] | null>(null);
  const [statistics, setStatistics] = useState({
    totalProjects: 0,
    activeProjects: 0,
    completedProjects: 0,
    archivedProjects: 0
  });  // Load project list
  const loadProjects = async () => {
    try {
      setLoading(true);
      let response;
      
      if (searchKeyword) {
        response = await projectApi.searchProjects(searchKeyword);
      } else if (filterStatus !== 'ALL') {
        response = await projectApi.getProjectsByStatus(filterStatus);
      } else if (createdDateFilter && createdDateFilter[0] && createdDateFilter[1]) {
        const [start, end] = createdDateFilter;
        response = await projectApi.getProjectsByCreatedDate(
          end.format('YYYY-MM-DD'), 
          start.format('YYYY-MM-DD')
        );
      } else if (deadlineDateFilter && deadlineDateFilter[0] && deadlineDateFilter[1]) {
        const [start, end] = deadlineDateFilter;
        response = await projectApi.getProjectsByDeadline(
          end.format('YYYY-MM-DD'), 
          start.format('YYYY-MM-DD')
        );
      } else {
        response = await projectApi.getProjects();
      }
      
      setProjects(response.data);
    } catch (error) {
      message.error('Failed to load project list');
    } finally {
      setLoading(false);
    }
  };

  // Load statistics
  const loadStatistics = async () => {
    try {
      const response = await projectApi.getProjectStatistics();
      setStatistics(response.data);
    } catch (error) {
      console.error('Failed to load statistics:', error);
    }
  };
  useEffect(() => {
    loadProjects();
    loadStatistics();
  }, [searchKeyword, filterStatus, createdDateFilter, deadlineDateFilter]);
  // Create or update project
  const handleSubmit = async (values: CreateProjectRequest) => {
    try {
      if (editingProject) {
        const response = await projectApi.updateProject(editingProject.id, values);
        message.success('Project updated successfully');
        // Update both local and shared state
        updateActiveProject(response.data);
      } else {
        const response = await projectApi.createProject(values);
        message.success('Project created successfully');
        // Add to shared state if project is active
        if (response.data.status === 'ACTIVE') {
          addToActiveProjects(response.data);
        }
      }
      setModalVisible(false);
      setEditingProject(null);
      form.resetFields();
      loadProjects();
      loadStatistics();
    } catch (error) {
      message.error(editingProject ? 'Failed to update project' : 'Failed to create project');
    }
  };
  // Delete project
  const handleDelete = async (projectId: number) => {
    try {
      await projectApi.deleteProject(projectId);
      message.success('Project deleted successfully');
      // Remove from shared state
      removeFromActiveProjects(projectId);
      loadProjects();
      loadStatistics();
    } catch (error) {
      message.error('Failed to delete project');
    }
  };  // Archive project
  const handleArchive = async (projectId: number) => {
    try {
      const response = await projectApi.archiveProject(projectId);
      message.success('Project archived successfully');
      // Update shared state
      if (response.data.status !== 'ACTIVE') {
        removeFromActiveProjects(projectId);
      } else {
        updateActiveProject(response.data);
      }
      loadProjects();
      loadStatistics();
    } catch (error) {
      message.error('Failed to archive project');
    }
  };

  // Reactivate project
  const handleReactivate = async (projectId: number) => {
    try {
      const response = await projectApi.reactivateProject(projectId);
      message.success('Project reactivated successfully');
      // Add to shared state since it's now active
      addToActiveProjects(response.data);
      loadProjects();
      loadStatistics();
    } catch (error) {
      message.error('Failed to reactivate project');
    }
  };

  // Suspend project
  const handleSuspend = async (projectId: number) => {
    try {
      const response = await projectApi.suspendProject(projectId);
      message.success('Project suspended successfully');
      updateActiveProject(response.data);
      loadProjects();
      loadStatistics();
    } catch (error) {
      message.error('Failed to suspend project');
    }
  };

  // Resume project
  const handleResume = async (projectId: number) => {
    try {
      const response = await projectApi.resumeProject(projectId);
      message.success('Project resumed successfully');
      updateActiveProject(response.data);
      loadProjects();
      loadStatistics();
    } catch (error) {
      message.error('Failed to resume project');
    }
  };

  // Complete project
  const handleComplete = async (projectId: number) => {
    try {
      const response = await projectApi.completeProject(projectId);
      message.success('Project completed successfully');
      updateActiveProject(response.data);
      loadProjects();
      loadStatistics();
    } catch (error) {
      message.error('Failed to complete project');
    }
  };

  // Open edit modal
  const handleEdit = (project: Project) => {
    setEditingProject(project);
    form.setFieldsValue({
      ...project,
      deadline: project.deadline ? dayjs(project.deadline) : null,
      caseDate: project.caseDate ? dayjs(project.caseDate) : null,
    });
    setModalVisible(true);
  };

  // Project type tag colors
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

  // Project status label colors
  const getProjectStatusColor = (status: ProjectStatus): string => {
    const colorMap: Record<ProjectStatus, string> = {
      ACTIVE: 'processing',
      COMPLETED: 'success',
      SUSPENDED: 'warning',
      ARCHIVED: 'default'
    };
    return colorMap[status] || 'default';
  };

  // Project type display names
  const getProjectTypeLabel = (type: ProjectType): string => {
    const labelMap: Record<ProjectType, string> = {
      GENERAL: 'General Investigation',
      CRIMINAL: 'Criminal Case',
      CIVIL: 'Civil Case',
      CORPORATE: 'Corporate Investigation',
      ACADEMIC_RESEARCH: 'Academic Research'
    };
    return labelMap[type] || type;
  };

  // Project status display names
  const getProjectStatusLabel = (status: ProjectStatus): string => {
    const labelMap: Record<ProjectStatus, string> = {
      ACTIVE: 'Active',
      COMPLETED: 'Completed',
      SUSPENDED: 'Suspended',
      ARCHIVED: 'Archived'
    };
    return labelMap[status] || status;
  };

  const columns: ColumnsType<Project> = [
    {
      title: 'Project Name',
      dataIndex: 'name',
      key: 'name',
      width: 150,
      fixed: 'left',
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
      title: 'Case Number',
      dataIndex: 'caseNumber',
      key: 'caseNumber',
      width: 120,
      ellipsis: true,
      responsive: ['md'],
    },
    {
      title: 'Project Type',
      dataIndex: 'projectType',
      key: 'projectType',
      width: 120,
      render: (type: ProjectType) => (
        <Tag color={getProjectTypeColor(type)}>
          {getProjectTypeLabel(type)}
        </Tag>
      ),
    },
    {
      title: 'Status',
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
      title: 'Client',
      dataIndex: 'clientName',
      key: 'clientName',
      width: 120,
      ellipsis: true,
      responsive: ['lg'],
    },
    {
      title: 'Deadline',
      dataIndex: 'deadline',
      key: 'deadline',
      width: 110,
      responsive: ['sm'],
      render: (deadline) => 
        deadline ? dayjs(deadline).format('YYYY-MM-DD') : '-',
    },
    {
      title: 'Created Time',
      dataIndex: 'createdAt',
      key: 'createdAt',
      width: 110,
      responsive: ['md'],
      render: (createdAt) => dayjs(createdAt).format('YYYY-MM-DD'),
    },
    {
      title: 'Actions',
      key: 'action',
      width: 120,
      fixed: 'right',      render: (_, record) => (
        <Space size="small" className="table-actions">
          <Tooltip title="Edit">
            <Button
              type="link"
              size="small"
              icon={<EditOutlined />}
              onClick={() => handleEdit(record)}
            />
          </Tooltip>
          
          {/* Status-based action buttons */}
          {record.status === 'ACTIVE' && (
            <>
              <Tooltip title="Suspend">
                <Popconfirm
                  title="Confirm suspending this project?"
                  onConfirm={() => handleSuspend(record.id)}
                  okText="Confirm"
                  cancelText="Cancel"
                >
                  <Button
                    type="link"
                    size="small"
                    icon={<PauseCircleOutlined />}
                  />
                </Popconfirm>
              </Tooltip>
              
              <Tooltip title="Complete">
                <Popconfirm
                  title="Confirm completing this project?"
                  onConfirm={() => handleComplete(record.id)}
                  okText="Confirm"
                  cancelText="Cancel"
                >
                  <Button
                    type="link"
                    size="small"
                    icon={<CheckCircleOutlined />}
                  />
                </Popconfirm>
              </Tooltip>
            </>
          )}
          
          {record.status === 'SUSPENDED' && (
            <Tooltip title="Resume">
              <Popconfirm
                title="Confirm resuming this project?"
                onConfirm={() => handleResume(record.id)}
                okText="Confirm"
                cancelText="Cancel"
              >
                <Button
                  type="link"
                  size="small"
                  icon={<PlayCircleOutlined />}
                />
              </Popconfirm>
            </Tooltip>
          )}
          
          {record.status === 'ARCHIVED' && (
            <Tooltip title="Reactivate">
              <Popconfirm
                title="Confirm reactivating this project?"
                onConfirm={() => handleReactivate(record.id)}
                okText="Confirm"
                cancelText="Cancel"
              >
                <Button
                  type="link"
                  size="small"
                  icon={<ReloadOutlined />}
                />
              </Popconfirm>
            </Tooltip>
          )}
          
          {record.status !== 'ARCHIVED' && (
            <Tooltip title="Archive">
              <Popconfirm
                title="Confirm archiving this project?"
                onConfirm={() => handleArchive(record.id)}
                okText="Confirm"
                cancelText="Cancel"
              >
                <Button
                  type="link"
                  size="small"
                  icon={<InboxOutlined />}
                />
              </Popconfirm>
            </Tooltip>
          )}
          
          <Tooltip title="Delete">
            <Popconfirm
              title="Confirm deleting this project? This action cannot be undone."
              onConfirm={() => handleDelete(record.id)}
              okText="Confirm"
              cancelText="Cancel"
            >
              <Button
                type="link"
                size="small"
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
    <div className="projects-page">
      {/* Statistics cards */}
      <Row gutter={[16, 16]} className="projects-stats" style={{ marginBottom: 24 }}>
        <Col xs={12} sm={12} md={6} lg={6} xl={6}>
          <Card>
            <Statistic
              title="Total Projects"
              value={statistics.totalProjects}
              prefix={<UserOutlined />}
            />
          </Card>
        </Col>
        <Col xs={12} sm={12} md={6} lg={6} xl={6}>
          <Card>
            <Statistic
              title="In Progress"
              value={statistics.activeProjects}
              prefix={<CalendarOutlined />}
              valueStyle={{ color: '#1890ff' }}
            />
          </Card>
        </Col>
        <Col xs={12} sm={12} md={6} lg={6} xl={6}>
          <Card>
            <Statistic
              title="Completed"
              value={statistics.completedProjects}
              valueStyle={{ color: '#52c41a' }}
            />
          </Card>
        </Col>
        <Col xs={12} sm={12} md={6} lg={6} xl={6}>
          <Card>
            <Statistic
              title="Archived"
              value={statistics.archivedProjects}
              valueStyle={{ color: '#8c8c8c' }}
            />
          </Card>
        </Col>
      </Row>

      <Card
        title="Project Management"
        extra={
          <Space wrap className="projects-controls">            {/* Search box */}
            <Input
              placeholder="Search projects..."
              prefix={<SearchOutlined />}
              value={searchKeyword}
              onChange={(e) => setSearchKeyword(e.target.value)}
              style={{ width: '200px', minWidth: '150px' }}
              allowClear
            />
            
            {/* Status filter */}
            <Select
              value={filterStatus}
              onChange={setFilterStatus}
              style={{ width: '120px', minWidth: '100px' }}
            >
              <Option value="ALL">All Status</Option>
              <Option value="ACTIVE">In Progress</Option>
              <Option value="COMPLETED">Completed</Option>
              <Option value="SUSPENDED">Suspended</Option>
              <Option value="ARCHIVED">Archived</Option>
            </Select>
              {/* Created date filter */}
            <RangePicker
              placeholder={['Created After', 'Created Before']}
              value={createdDateFilter}
              onChange={(dates) => setCreatedDateFilter(dates)}
              style={{ width: '240px' }}
              allowClear
            />
            
            {/* Deadline filter */}
            <RangePicker
              placeholder={['Deadline After', 'Deadline Before']}
              value={deadlineDateFilter}
              onChange={(dates) => setDeadlineDateFilter(dates)}
              style={{ width: '240px' }}
              allowClear
            />
            
            {/* Create button */}
            <Button
              type="primary"
              icon={<PlusOutlined />}
              onClick={() => {
                setEditingProject(null);
                form.resetFields();
                setModalVisible(true);
              }}
            >
              Create Project
            </Button>
          </Space>
        }
      >
        <Table
          className="projects-table"
          columns={columns}
          dataSource={projects}
          rowKey="id"
          loading={loading}
          scroll={{ x: 800 }}
          pagination={{
            pageSize: 10,
            showSizeChanger: true,
            showQuickJumper: true,
            showTotal: (total) => `Total ${total} projects`,
            responsive: true,
          }}
        />
      </Card>

      {/* Create/Edit project modal */}
      <Modal
        title={editingProject ? 'Edit Project' : 'Create Project'}
        open={modalVisible}
        onCancel={() => {
          setModalVisible(false);
          setEditingProject(null);
          form.resetFields();
        }}
        footer={null}
        width="90%"
        style={{ maxWidth: 800 }}
      >
        <Form
          form={form}
          layout="vertical"
          onFinish={handleSubmit}
          className="project-form"
        >
          <Row gutter={16}>
            <Col xs={24} sm={12}>
              <Form.Item
                name="name"
                label="Project Name"
                rules={[{ required: true, message: 'Please enter project name' }]}
              >
                <Input placeholder="Enter project name" />
              </Form.Item>
            </Col>
            <Col xs={24} sm={12}>
              <Form.Item
                name="caseNumber"
                label="Case Number"
              >
                <Input placeholder="Enter case number (optional)" />
              </Form.Item>
            </Col>
          </Row>

          <Row gutter={16}>
            <Col xs={24} sm={12}>
              <Form.Item
                name="projectType"
                label="Project Type"
                initialValue="GENERAL"
              >
                <Select>
                  <Option value="GENERAL">General Investigation</Option>
                  <Option value="CRIMINAL">Criminal Case</Option>
                  <Option value="CIVIL">Civil Case</Option>
                  <Option value="CORPORATE">Corporate Investigation</Option>
                  <Option value="ACADEMIC_RESEARCH">Academic Research</Option>
                </Select>
              </Form.Item>
            </Col>
            <Col xs={24} sm={12}>
              <Form.Item
                name="clientName"
                label="Client"
              >
                <Input placeholder="Enter client name" />
              </Form.Item>
            </Col>
          </Row>

          <Row gutter={16}>
            <Col xs={24} sm={12}>
              <Form.Item
                name="clientContact"
                label="Client Contact"
              >
                <Input placeholder="Enter contact information" />
              </Form.Item>
            </Col>
            <Col xs={24} sm={12}>
              <Form.Item
                name="deadline"
                label="Deadline"
              >
                <DatePicker style={{ width: '100%' }} />
              </Form.Item>
            </Col>
          </Row>

          <Form.Item
            name="description"
            label="Project Description"
          >
            <TextArea rows={3} placeholder="Enter project description" />
          </Form.Item>

          <Form.Item
            name="evidenceDescription"
            label="Evidence Description"
          >
            <TextArea rows={2} placeholder="Describe related evidence" />
          </Form.Item>

                    <Form.Item
            name="notes"
            label="Notes"
          >
            <TextArea rows={2} placeholder="Enter notes" />
          </Form.Item>

          <Form.Item style={{ textAlign: 'right', marginBottom: 0 }}>
            <Space>
              <Button onClick={() => {
                setModalVisible(false);
                setEditingProject(null);
                form.resetFields();
              }}>
                Cancel
              </Button>
              <Button type="primary" htmlType="submit">
                {editingProject ? 'Update' : 'Create'}
              </Button>
            </Space>
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};

export default ProjectsPage;
