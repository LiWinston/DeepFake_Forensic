import React, { useState } from 'react';
import { Form, Input, Button, Card, message, Result } from 'antd';
import { MailOutlined, ArrowLeftOutlined } from '@ant-design/icons';
import { Link } from 'react-router-dom';
import authService from '../services/auth';
import './AuthPage.css';

const ForgotPasswordPage: React.FC = () => {
  const [loading, setLoading] = useState(false);
  const [emailSent, setEmailSent] = useState(false);
  const [email, setEmail] = useState('');

  const handleSubmit = async (values: { email: string }) => {
    setLoading(true);
    try {
      await authService.requestPasswordReset(values.email);
      setEmail(values.email);
      setEmailSent(true);
      message.success('Password reset email sent successfully!');
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
            title="Reset Email Sent!"
            subTitle={`We've sent a password reset link to ${email}. Please check your email and follow the instructions to reset your password.`}
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
      <Card className="auth-card" title="Forgot Password">
        <Form
          name="forgotPassword"
          onFinish={handleSubmit}
          autoComplete="off"
          layout="vertical"
        >
          <Form.Item
            label="Email Address"
            name="email"
            rules={[
              { required: true, message: 'Please enter your email!' },
              { type: 'email', message: 'Please enter a valid email address!' },
            ]}
          >
            <Input
              prefix={<MailOutlined />}
              placeholder="Enter your email address"
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
