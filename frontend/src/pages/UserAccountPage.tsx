import React, { useState, useEffect } from 'react';
import {
  Card,
  Form,
  Input,
  Button,
  message,
  Tabs,
  Divider,
  Typography,
  Space,
  Modal,
  Row,
  Col,
} from 'antd';
import {
  UserOutlined,
  LockOutlined,
  MailOutlined,
  SaveOutlined,
  EditOutlined,
  CheckCircleOutlined,
  ExclamationCircleOutlined,
} from '@ant-design/icons';
import { useAuth } from '../contexts/AuthContext';
import authService from '../services/auth';
import type { User, UserProfileUpdate, PasswordChange } from '../services/auth';
import './AuthPage.css'; // 复用认证页面的样式

const { Title, Text } = Typography;
const { TabPane } = Tabs;

const UserAccountPage: React.FC = () => {
  const { user, refreshUser } = useAuth();
  const [loading, setLoading] = useState(false);
  const [profileForm] = Form.useForm();
  const [passwordForm] = Form.useForm();

  // Initialize profile form with current user data
  useEffect(() => {
    if (user) {
      profileForm.setFieldsValue({
        username: user.username,
        email: user.email,
        firstName: user.firstName || '',
        lastName: user.lastName || '',
      });
    }
  }, [user, profileForm]);

  const handleUpdateProfile = async (values: UserProfileUpdate) => {
    setLoading(true);
    try {
      await authService.updateProfile({
        firstName: values.firstName,
        lastName: values.lastName,
      });
      message.success('Profile updated successfully!');
      await refreshUser(); // Refresh user data in context
    } catch (error: any) {
      message.error(error.response?.data?.message || 'Failed to update profile');
    } finally {
      setLoading(false);
    }
  };

  const handleChangePassword = async (values: PasswordChange) => {
    setLoading(true);
    try {
      await authService.changePassword(values);
      message.success('Password changed successfully!');
      passwordForm.resetFields();
    } catch (error: any) {
      message.error(error.response?.data?.message || 'Failed to change password');
    } finally {
      setLoading(false);
    }
  };

  const handleRequestEmailVerification = async () => {
    if (!user?.email) return;
    
    try {
      await authService.requestEmailVerification({ email: user.email });
      message.success('Verification email sent successfully!');
    } catch (error: any) {
      message.error(error.response?.data?.message || 'Failed to send verification email');
    }
  };

  const showDeleteAccountConfirm = () => {
    Modal.confirm({
      title: 'Delete Account',
      icon: <ExclamationCircleOutlined />,
      content: 'Are you sure you want to delete your account? This action cannot be undone.',
      okText: 'Yes, Delete',
      okType: 'danger',
      cancelText: 'Cancel',
      onOk() {
        message.info('Account deletion feature will be implemented soon.');
      },
    });
  };

  return (
    <div style={{ 
      minHeight: '100vh', 
      background: 'linear-gradient(135deg, #0a0e27 0%, #1a1a2e 50%, #16213e 100%)',
      padding: '24px' 
    }}>
      <div style={{ maxWidth: 800, margin: '0 auto' }}>
        <Title level={2} style={{ color: '#ffffff', textAlign: 'center', marginBottom: 32 }}>
          Account Management
        </Title>

        <Tabs defaultActiveKey="profile" centered>
          <TabPane 
            tab={
              <span>
                <UserOutlined />
                Profile Information
              </span>
            } 
            key="profile"
          >
            <Card title="Profile Information" style={{ background: 'rgba(255, 255, 255, 0.95)' }}>
              <Row gutter={[16, 16]}>
                <Col xs={24} sm={12}>
                  <Text strong>Username:</Text>
                  <div style={{ marginBottom: 16 }}>
                    <Text code style={{ fontSize: '16px' }}>{user?.username}</Text>
                  </div>
                </Col>
                <Col xs={24} sm={12}>
                  <Text strong>Email:</Text>
                  <div style={{ marginBottom: 16, display: 'flex', alignItems: 'center', gap: 8 }}>
                    <Text code style={{ fontSize: '16px' }}>{user?.email}</Text>
                    {user?.emailVerified ? (
                      <CheckCircleOutlined style={{ color: '#52c41a' }} />
                    ) : (
                      <Button 
                        type="link" 
                        size="small" 
                        onClick={handleRequestEmailVerification}
                      >
                        Verify Email
                      </Button>
                    )}
                  </div>
                </Col>
                <Col xs={24} sm={12}>
                  <Text strong>Role:</Text>
                  <div style={{ marginBottom: 16 }}>
                    <Text code style={{ fontSize: '16px' }}>{user?.role}</Text>
                  </div>
                </Col>
                <Col xs={24} sm={12}>
                  <Text strong>Last Login:</Text>
                  <div style={{ marginBottom: 16 }}>
                    <Text code style={{ fontSize: '16px' }}>
                      {user?.lastLoginAt ? new Date(user.lastLoginAt).toLocaleString() : 'N/A'}
                    </Text>
                  </div>
                </Col>
              </Row>

              <Divider />

              <Form
                form={profileForm}
                layout="vertical"
                onFinish={handleUpdateProfile}
                disabled={loading}
              >
                <Row gutter={16}>
                  <Col xs={24} sm={12}>
                    <Form.Item
                      name="firstName"
                      label="First Name"
                    >
                      <Input
                        prefix={<UserOutlined />}
                        placeholder="Enter first name"
                        size="large"
                      />
                    </Form.Item>
                  </Col>
                  <Col xs={24} sm={12}>
                    <Form.Item
                      name="lastName"
                      label="Last Name"
                    >
                      <Input
                        prefix={<UserOutlined />}
                        placeholder="Enter last name"
                        size="large"
                      />
                    </Form.Item>
                  </Col>
                </Row>

                <Form.Item>
                  <Button
                    type="primary"
                    htmlType="submit"
                    loading={loading}
                    size="large"
                    icon={<SaveOutlined />}
                  >
                    Update Profile
                  </Button>
                </Form.Item>
              </Form>
            </Card>
          </TabPane>

          <TabPane 
            tab={
              <span>
                <LockOutlined />
                Change Password
              </span>
            } 
            key="password"
          >
            <Card title="Change Password" style={{ background: 'rgba(255, 255, 255, 0.95)' }}>
              <Form
                form={passwordForm}
                layout="vertical"
                onFinish={handleChangePassword}
                disabled={loading}
              >
                <Form.Item
                  name="currentPassword"
                  label="Current Password"
                  rules={[
                    { required: true, message: 'Please enter your current password!' },
                  ]}
                >
                  <Input.Password
                    prefix={<LockOutlined />}
                    placeholder="Enter current password"
                    size="large"
                  />
                </Form.Item>

                <Form.Item
                  name="newPassword"
                  label="New Password"
                  rules={[
                    { required: true, message: 'Please enter new password!' },
                    { min: 6, message: 'Password must be at least 6 characters!' },
                  ]}
                >
                  <Input.Password
                    prefix={<LockOutlined />}
                    placeholder="Enter new password"
                    size="large"
                  />
                </Form.Item>

                <Form.Item
                  name="confirmPassword"
                  label="Confirm New Password"
                  dependencies={['newPassword']}
                  rules={[
                    { required: true, message: 'Please confirm your new password!' },
                    ({ getFieldValue }) => ({
                      validator(_, value) {
                        if (!value || getFieldValue('newPassword') === value) {
                          return Promise.resolve();
                        }
                        return Promise.reject(new Error('Passwords do not match!'));
                      },
                    }),
                  ]}
                >
                  <Input.Password
                    prefix={<LockOutlined />}
                    placeholder="Confirm new password"
                    size="large"
                  />
                </Form.Item>

                <Form.Item>
                  <Button
                    type="primary"
                    htmlType="submit"
                    loading={loading}
                    size="large"
                    icon={<LockOutlined />}
                  >
                    Change Password
                  </Button>
                </Form.Item>
              </Form>
            </Card>
          </TabPane>

          <TabPane 
            tab={
              <span>
                <MailOutlined />
                Email Settings
              </span>
            } 
            key="email"
          >
            <Card title="Email Settings" style={{ background: 'rgba(255, 255, 255, 0.95)' }}>
              <Space direction="vertical" size="large" style={{ width: '100%' }}>
                <div>
                  <Text strong>Email Verification Status:</Text>
                  <div style={{ marginTop: 8 }}>
                    {user?.emailVerified ? (
                      <Space>
                        <CheckCircleOutlined style={{ color: '#52c41a' }} />
                        <Text style={{ color: '#52c41a' }}>Email verified</Text>
                        <Text type="secondary">
                          ({user.emailVerifiedAt ? new Date(user.emailVerifiedAt).toLocaleString() : 'N/A'})
                        </Text>
                      </Space>
                    ) : (
                      <Space>
                        <ExclamationCircleOutlined style={{ color: '#faad14' }} />
                        <Text style={{ color: '#faad14' }}>Email not verified</Text>
                        <Button 
                          type="primary" 
                          size="small"
                          onClick={handleRequestEmailVerification}
                        >
                          Send Verification Email
                        </Button>
                      </Space>
                    )}
                  </div>
                </div>

                <Divider />

                <div>
                  <Text strong>Current Email:</Text>
                  <div style={{ marginTop: 8 }}>
                    <Text code style={{ fontSize: '16px' }}>{user?.email}</Text>
                  </div>
                  <div style={{ marginTop: 8 }}>
                    <Text type="secondary">
                      To change your email address, please contact support.
                    </Text>
                  </div>
                </div>
              </Space>
            </Card>
          </TabPane>

          <TabPane 
            tab={
              <span style={{ color: '#ff4d4f' }}>
                <ExclamationCircleOutlined />
                Danger Zone
              </span>
            } 
            key="danger"
          >
            <Card 
              title="Danger Zone" 
              style={{ background: 'rgba(255, 255, 255, 0.95)', border: '1px solid #ff4d4f' }}
            >
              <Space direction="vertical" size="large" style={{ width: '100%' }}>
                <div>
                  <Title level={5} style={{ color: '#ff4d4f' }}>Delete Account</Title>
                  <Text type="secondary">
                    Once you delete your account, there is no going back. Please be certain.
                  </Text>
                  <div style={{ marginTop: 16 }}>
                    <Button 
                      danger 
                      size="large"
                      onClick={showDeleteAccountConfirm}
                    >
                      Delete Account
                    </Button>
                  </div>
                </div>
              </Space>
            </Card>
          </TabPane>
        </Tabs>
      </div>
    </div>
  );
};

export default UserAccountPage;
