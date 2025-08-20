import React, { useState, useEffect } from 'react';
import { Form, Input, Button, Card, message, Result, Spin } from 'antd';
import { LockOutlined, CheckCircleOutlined } from '@ant-design/icons';
import { Link, useSearchParams, useNavigate } from 'react-router-dom';
import authService from '../services/auth';
import './AuthPage.css';

const ResetPasswordPage: React.FC = () => {
  const [form] = Form.useForm();
  const [loading, setLoading] = useState(false);
  const [validating, setValidating] = useState(true);
  const [tokenValid, setTokenValid] = useState(false);
  const [resetSuccess, setResetSuccess] = useState(false);
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  
  const token = searchParams.get('token');

  useEffect(() => {
    if (!token) {
      message.error('Invalid reset link');
      navigate('/auth');
      return;
    }

    // Validate token on component mount
    const validateToken = async () => {
      try {
        await authService.validateResetToken(token);
        setTokenValid(true);
      } catch (error: any) {
        message.error('Invalid or expired reset link');
        setTokenValid(false);
      } finally {
        setValidating(false);
      }
    };

    validateToken();
  }, [token, navigate]);

  const handleSubmit = async (values: { password: string; confirmPassword: string }) => {
    if (!token) return;
    
    setLoading(true);
    try {
      await authService.resetPassword(token, values.password);
      setResetSuccess(true);
      message.success('Password reset successfully!');
    } catch (error: any) {
      message.error(error.response?.data?.message || 'Failed to reset password');
    } finally {
      setLoading(false);
    }
  };

  if (validating) {
    return (
      <div className="auth-page">
        <Card className="auth-card">
          <div style={{ textAlign: 'center', padding: '40px 0' }}>
            <Spin size="large" />
            <p style={{ marginTop: 16 }}>Validating reset link...</p>
          </div>
        </Card>
      </div>
    );
  }

  if (!tokenValid) {
    return (
      <div className="auth-page">
        <Card className="auth-card">
          <Result
            status="error"
            title="Invalid Reset Link"
            subTitle="This password reset link is invalid or has expired. Please request a new one."
            extra={[
              <Link to="/forgot-password" key="reset">
                <Button type="primary">Request New Reset Link</Button>
              </Link>,
              <Link to="/auth" key="login">
                <Button>Back to Login</Button>
              </Link>
            ]}
          />
        </Card>
      </div>
    );
  }

  if (resetSuccess) {
    return (
      <div className="auth-page">
        <Card className="auth-card">
          <Result
            status="success"
            title="Password Reset Successfully!"
            subTitle="Your password has been reset successfully. You can now login with your new password."
            extra={[
              <Link to="/auth" key="login">
                <Button type="primary" icon={<CheckCircleOutlined />}>
                  Go to Login
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
      <Card className="auth-card" title="Reset Password">
        <Form
          form={form}
          name="resetPassword"
          onFinish={handleSubmit}
          autoComplete="off"
          layout="vertical"
        >
          <Form.Item
            label="New Password"
            name="password"
            rules={[
              { required: true, message: 'Please enter your new password!' },
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
            label="Confirm New Password"
            name="confirmPassword"
            dependencies={['password']}
            rules={[
              { required: true, message: 'Please confirm your new password!' },
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
              block
            >
              Reset Password
            </Button>
          </Form.Item>

          <Form.Item>
            <Link to="/auth">
              <Button type="link" block>
                Back to Login
              </Button>
            </Link>
          </Form.Item>
        </Form>
      </Card>
    </div>
  );
};

export default ResetPasswordPage;
