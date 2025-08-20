import React, { useState, useEffect } from 'react';
import { Card, message, Result, Spin, Button } from 'antd';
import { CheckCircleOutlined, CloseCircleOutlined } from '@ant-design/icons';
import { Link, useSearchParams, useNavigate } from 'react-router-dom';
import authService from '../services/auth';
import './AuthPage.css';

const EmailVerificationPage: React.FC = () => {
  const [loading, setLoading] = useState(true);
  const [verified, setVerified] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  
  const token = searchParams.get('token');

  useEffect(() => {
    if (!token) {
      setError('Invalid verification link');
      setLoading(false);
      return;
    }

    const verifyEmail = async () => {
      try {
        await authService.verifyEmail(token);
        setVerified(true);
        message.success('Email verified successfully!');
      } catch (error: any) {
        setError(error.response?.data?.message || 'Email verification failed');
        message.error('Email verification failed');
      } finally {
        setLoading(false);
      }
    };

    verifyEmail();
  }, [token]);

  if (loading) {
    return (
      <div className="auth-page">
        <Card className="auth-card">
          <div style={{ textAlign: 'center', padding: '40px 0' }}>
            <Spin size="large" />
            <p style={{ marginTop: 16 }}>Verifying your email...</p>
          </div>
        </Card>
      </div>
    );
  }

  if (verified) {
    return (
      <div className="auth-page">
        <Card className="auth-card">
          <Result
            status="success"
            title="Email Verified Successfully!"
            subTitle="Your email has been verified. You can now access all features."
            icon={<CheckCircleOutlined style={{ color: '#52c41a' }} />}            extra={[
              <Link to="/auth" key="login">
                <Button type="primary">
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
      <Card className="auth-card">
        <Result
          status="error"
          title="Email Verification Failed"
          subTitle={error || 'The verification link is invalid or has expired.'}
          icon={<CloseCircleOutlined style={{ color: '#ff4d4f' }} />}          extra={[
            <Link to="/auth" key="login">
              <Button>
                Back to Login
              </Button>
            </Link>
          ]}
        />
      </Card>
    </div>
  );
};

export default EmailVerificationPage;
