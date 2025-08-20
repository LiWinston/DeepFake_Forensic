import React, { useState } from 'react';
import { Form, Input, Button, Card, message, Tabs } from 'antd';
import { UserOutlined, LockOutlined, MailOutlined } from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../contexts/AuthContext';
import authService from '../services/auth';
import type { LoginRequest, RegisterRequest } from '../services/auth';
import './AuthPage.css';

const { TabPane } = Tabs;

const AuthPage: React.FC = () => {
  const navigate = useNavigate();
  const { login } = useAuth();
  const [loading, setLoading] = useState(false);

  const handleLogin = async (values: LoginRequest) => {
    setLoading(true);
    try {
      await login(values.username, values.password);
      message.success('Login successful!');
      navigate('/');
    } catch (error: any) {
      message.error(error.response?.data?.message || 'Login failed');
    } finally {
      setLoading(false);
    }
  };  const handleRegister = async (values: RegisterRequest) => {
    setLoading(true);
    try {
      await authService.register(values);
      message.success('Registration successful!');
      // Auto login after successful registration
      await login(values.username, values.password);
      navigate('/');
    } catch (error: any) {
      message.error(error.response?.data?.message || 'Registration failed');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="auth-page">
      <Card className="auth-card" title="DeepFake Forensic System">
        <Tabs defaultActiveKey="login" centered>
          <TabPane tab="Login" key="login">
            <Form
              name="login"
              onFinish={handleLogin}
              autoComplete="off"
              layout="vertical"
            >
              <Form.Item
                label="Username"
                name="username"
                rules={[{ required: true, message: 'Please enter username!' }]}
              >
                <Input
                  prefix={<UserOutlined />}
                  placeholder="Username"
                  size="large"
                />
              </Form.Item>

              <Form.Item
                label="Password"
                name="password"
                rules={[{ required: true, message: 'Please enter password!' }]}
              >
                <Input.Password
                  prefix={<LockOutlined />}
                  placeholder="Password"
                  size="large"
                />
              </Form.Item>

              <Form.Item>
                <Button
                  type="primary"
                  htmlType="submit"
                  loading={loading}
                  size="large"
                  block
                >
                  Login
                </Button>
              </Form.Item>
            </Form>
          </TabPane>

          <TabPane tab="Register" key="register">
            <Form
              name="register"
              onFinish={handleRegister}
              autoComplete="off"
              layout="vertical"
            >
              <Form.Item
                label="Username"
                name="username"
                rules={[
                  { required: true, message: 'Please enter username!' },
                  { min: 3, message: 'Username must be at least 3 characters!' },
                  { max: 50, message: 'Username cannot exceed 50 characters!' },
                ]}
              >
                <Input
                  prefix={<UserOutlined />}
                  placeholder="Username"
                  size="large"
                />
              </Form.Item>

              <Form.Item
                label="Email"
                name="email"
                rules={[
                  { required: true, message: 'Please enter email!' },
                  { type: 'email', message: 'Please enter a valid email address!' },
                ]}
              >
                <Input
                  prefix={<MailOutlined />}
                  placeholder="Email"
                  size="large"
                />
              </Form.Item>

              <Form.Item
                label="Password"
                name="password"
                rules={[
                  { required: true, message: 'Please enter password!' },
                  { min: 6, message: 'Password must be at least 6 characters!' },
                ]}
              >
                <Input.Password
                  prefix={<LockOutlined />}
                  placeholder="Password"
                  size="large"
                />
              </Form.Item>

              <Form.Item
                label="Confirm Password"
                name="confirmPassword"
                dependencies={['password']}
                rules={[
                  { required: true, message: 'Please confirm password!' },
                  ({ getFieldValue }) => ({
                    validator(_, value) {
                      if (!value || getFieldValue('password') === value) {
                        return Promise.resolve();
                      }
                      return Promise.reject(new Error('Passwords do not match!'));
                    },
                  }),
                ]}
              >
                <Input.Password
                  prefix={<LockOutlined />}
                  placeholder="Confirm Password"
                  size="large"
                />
              </Form.Item>

              <Form.Item
                label="Name"
                name="firstName"
              >
                <Input placeholder="Name (Optional)" size="large" />
              </Form.Item>

              <Form.Item>
                <Button
                  type="primary"
                  htmlType="submit"
                  loading={loading}
                  size="large"
                  block
                >
                  Register
                </Button>
              </Form.Item>
            </Form>
          </TabPane>
        </Tabs>
      </Card>
    </div>
  );
};

export default AuthPage;
