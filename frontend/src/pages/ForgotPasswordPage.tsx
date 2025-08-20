import React, { useState } from 'react';
import { Form, Input, Button, Card, message, Result } from 'antd';
import { UserOutlined, ArrowLeftOutlined } from '@ant-design/icons';
import { Link } from 'react-router-dom';
import authService from '../services/auth';
import './AuthPage.css';

const ForgotPasswordPage: React.FC = () => {
  const [loading, setLoading] = useState(false);
  const [emailSent, setEmailSent] = useState(false);
  const [username, setUsername] = useState('');

  const handleSubmit = async (values: { username: string }) => {
    setLoading(true);
    try {
      await authService.requestPasswordReset(values.username);
      setUsername(values.username);
      setEmailSent(true);
      message.success('If the username exists, a password reset email has been sent!');
    } catch (error: any) {
      message.error(error.response?.data?.message || 'Failed to send reset email');
    } finally {
      setLoading(false);
    }
  };

  if (emailSent) {
    return (
      <div className="auth-page">
        <Card className="auth-card">
          <Result
            status="success"
            title="Reset Request Submitted!"
            subTitle={`If the username "${username}" exists in our system, we've sent a password reset link to the associated email address. Please check your email and follow the instructions to reset your password.`}
            extra={[
              <Link to="/auth" key="back">
                <Button type="primary" icon={<ArrowLeftOutlined />}>
                  Back to Login
                </Button>
              </Link>
            ]}
          />
        </Card>
      </div>
    );
  }

  return (
    <div className="auth-page">
      <Card className="auth-card" title="Forgot Password">        <Form
          name="forgotPassword"
          onFinish={handleSubmit}
          autoComplete="off"
          layout="vertical"
        >
          <Form.Item
            label="Username"
            name="username"
            rules={[
              { required: true, message: 'Please enter your username!' },
              { min: 3, message: 'Username must be at least 3 characters!' },
            ]}
          >
            <Input
              prefix={<UserOutlined />}
              placeholder="Enter your username"
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
              Send Reset Link
            </Button>
          </Form.Item>

          <Form.Item>
            <Link to="/auth">
              <Button type="link" icon={<ArrowLeftOutlined />} block>
                Back to Login
              </Button>
            </Link>
          </Form.Item>
        </Form>
      </Card>
    </div>
  );
};

export default ForgotPasswordPage;
