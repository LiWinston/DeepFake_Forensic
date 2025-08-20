import React from 'react';
import { Typography, Card, Row, Col, Progress, Timeline, Space, Button } from 'antd';
import { 
  ScanOutlined, 
  EyeOutlined, 
  CloudUploadOutlined,
  BarChartOutlined,
  FileTextOutlined,
  SafetyCertificateOutlined,
  ExperimentOutlined,
  ThunderboltOutlined,
  RobotOutlined,
  SearchOutlined,
  CheckCircleOutlined
} from '@ant-design/icons';

const { Title, Paragraph } = Typography;

const HomePage: React.FC = () => {
  const homeStyles = {
    container: {
      background: 'linear-gradient(135deg, #0a0e27 0%, #1a1a2e 50%, #16213e 100%)',
      minHeight: '100vh',
      color: '#ffffff',
      overflow: 'hidden',
    },
    heroSection: {
      padding: '80px 40px',
      position: 'relative' as const,
    },
    heroContent: {
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'space-between',
      maxWidth: '1400px',
      margin: '0 auto',
      gap: '60px',
      flexWrap: 'wrap' as const,
    },
    heroText: {
      flex: 1,
      maxWidth: '600px',
    },
    heroTitle: {
      fontSize: '3.5rem',
      fontWeight: 700,
      marginBottom: '20px',
      lineHeight: '1.2',
      background: 'linear-gradient(45deg, #00d4ff, #ff0084, #ffff00)',
      WebkitBackgroundClip: 'text',
      WebkitTextFillColor: 'transparent',
      backgroundClip: 'text',
    },
    heroSubtitle: {
      color: '#8892b0',
      fontWeight: 400,
      marginBottom: '24px',
    },
    heroDescription: {
      fontSize: '1.1rem',
      color: '#a8b2d1',
      lineHeight: '1.8',
      marginBottom: '40px',
    },
    sectionTitle: {
      textAlign: 'center' as const,
      color: '#ffffff',
      fontSize: '2.5rem',
      fontWeight: 600,
      marginBottom: '60px',
      position: 'relative' as const,
    },
    pipelineSection: {
      padding: '80px 40px',
      background: 'rgba(255, 255, 255, 0.02)',
      backdropFilter: 'blur(10px)',
    },
    pipelineContainer: {
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'center',
      maxWidth: '1200px',
      margin: '0 auto',
      flexWrap: 'wrap' as const,
      gap: '20px',
    },
    pipelineStep: {
      display: 'flex',
      flexDirection: 'column' as const,
      alignItems: 'center',
      textAlign: 'center' as const,
      padding: '30px 20px',
      background: 'rgba(255, 255, 255, 0.05)',
      borderRadius: '16px',
      border: '1px solid rgba(255, 255, 255, 0.1)',
      backdropFilter: 'blur(10px)',
      transition: 'all 0.3s ease',
      flex: 1,
      minWidth: '200px',
      maxWidth: '250px',
      cursor: 'pointer',
    },
    stepIcon: {
      width: '80px',
      height: '80px',
      borderRadius: '50%',
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'center',
      fontSize: '2rem',
      marginBottom: '20px',
      color: 'white',
      boxShadow: '0 10px 30px rgba(0, 212, 255, 0.3)',
    },
    techSection: {
      padding: '80px 40px',
    },
    techCard: {
      background: 'rgba(255, 255, 255, 0.05)',
      border: '1px solid rgba(255, 255, 255, 0.1)',
      borderRadius: '16px',
      backdropFilter: 'blur(10px)',
      transition: 'all 0.3s ease',
      height: '100%',
    },
    techIcon: {
      fontSize: '3rem',
      textAlign: 'center' as const,
      marginBottom: '20px',
      color: '#00d4ff',
    },
    capabilitiesSection: {
      padding: '80px 40px',
      background: 'rgba(255, 255, 255, 0.02)',
    },
    ctaSection: {
      padding: '80px 40px',
    },
    ctaCard: {
      background: 'linear-gradient(135deg, rgba(0, 212, 255, 0.1), rgba(255, 0, 132, 0.1))',
      border: '1px solid rgba(255, 255, 255, 0.2)',
      borderRadius: '20px',
      backdropFilter: 'blur(20px)',
      textAlign: 'center' as const,
    },
  };

  return (
    <div style={homeStyles.container}>
      {/* Hero Section */}
      <section style={homeStyles.heroSection}>
        <div style={homeStyles.heroContent}>
          <div style={homeStyles.heroText}>
            <Title level={1} style={homeStyles.heroTitle}>
              DeepFake Forensic Platform
            </Title>
            <Title level={3} style={homeStyles.heroSubtitle}>
              Advanced AI-Powered Digital Media Authentication
            </Title>
            <Paragraph style={homeStyles.heroDescription}>
              Cutting-edge forensic tool leveraging deep learning algorithms, metadata analysis, 
              and traditional forensic techniques to detect deepfakes, synthetic media, and digital manipulations.
            </Paragraph>            <Space size="large" style={{ marginTop: '40px' }}>
              <div style={{ textAlign: 'center' }}>
                <div style={{ 
                  color: '#ffffff', 
                  fontSize: '1rem', 
                  fontWeight: 600, 
                  marginBottom: '8px',
                  textTransform: 'uppercase',
                  letterSpacing: '1px'
                }}>
                  Detection Accuracy
                </div>
                <div style={{ 
                  color: '#00d4ff', 
                  fontSize: '2.5rem', 
                  fontWeight: 700,
                  textShadow: '0 0 20px rgba(0, 212, 255, 0.5)'
                }}>
                  98.7%
                </div>
              </div>
              <div style={{ textAlign: 'center' }}>
                <div style={{ 
                  color: '#ffffff', 
                  fontSize: '1rem', 
                  fontWeight: 600, 
                  marginBottom: '8px',
                  textTransform: 'uppercase',
                  letterSpacing: '1px'
                }}>
                  Supported Formats
                </div>
                <div style={{ 
                  color: '#00d4ff', 
                  fontSize: '2.5rem', 
                  fontWeight: 700,
                  textShadow: '0 0 20px rgba(0, 212, 255, 0.5)'
                }}>
                  25+
                </div>
              </div>
              <div style={{ textAlign: 'center' }}>
                <div style={{ 
                  color: '#ffffff', 
                  fontSize: '1rem', 
                  fontWeight: 600, 
                  marginBottom: '8px',
                  textTransform: 'uppercase',
                  letterSpacing: '1px'
                }}>
                  Analysis Speed
                </div>
                <div style={{ 
                  color: '#00d4ff', 
                  fontSize: '2.5rem', 
                  fontWeight: 700,
                  textShadow: '0 0 20px rgba(0, 212, 255, 0.5)'
                }}>
                  3.2s/file
                </div>
              </div>
            </Space>
          </div>
        </div>
      </section>

      {/* Detection Pipeline */}
      <section style={homeStyles.pipelineSection}>
        <Title level={2} style={homeStyles.sectionTitle}>Detection Pipeline</Title>
        <div style={homeStyles.pipelineContainer}>
          <div 
            style={homeStyles.pipelineStep}
            onMouseEnter={(e) => {
              e.currentTarget.style.transform = 'translateY(-5px)';
              e.currentTarget.style.background = 'rgba(255, 255, 255, 0.08)';
              e.currentTarget.style.borderColor = 'rgba(0, 212, 255, 0.3)';
            }}
            onMouseLeave={(e) => {
              e.currentTarget.style.transform = 'translateY(0)';
              e.currentTarget.style.background = 'rgba(255, 255, 255, 0.05)';
              e.currentTarget.style.borderColor = 'rgba(255, 255, 255, 0.1)';
            }}
          >
            <div style={{...homeStyles.stepIcon, background: 'linear-gradient(135deg, #00d4ff, #0099cc)'}}>
              <CloudUploadOutlined />
            </div>
            <div>
              <h3 style={{ color: '#ffffff', fontSize: '1.2rem', fontWeight: 600, marginBottom: '10px' }}>
                Upload & Validation
              </h3>
              <p style={{ color: '#a8b2d1', fontSize: '0.9rem', lineHeight: '1.5', margin: 0 }}>
                Secure file upload with format validation and integrity checks
              </p>
            </div>
          </div>
          
          <div style={{ fontSize: '2rem', color: '#00d4ff', fontWeight: 'bold' }}>→</div>
          
          <div 
            style={homeStyles.pipelineStep}
            onMouseEnter={(e) => {
              e.currentTarget.style.transform = 'translateY(-5px)';
              e.currentTarget.style.background = 'rgba(255, 255, 255, 0.08)';
              e.currentTarget.style.borderColor = 'rgba(0, 212, 255, 0.3)';
            }}
            onMouseLeave={(e) => {
              e.currentTarget.style.transform = 'translateY(0)';
              e.currentTarget.style.background = 'rgba(255, 255, 255, 0.05)';
              e.currentTarget.style.borderColor = 'rgba(255, 255, 255, 0.1)';
            }}
          >
            <div style={{...homeStyles.stepIcon, background: 'linear-gradient(135deg, #ff0084, #cc0066)', boxShadow: '0 10px 30px rgba(255, 0, 132, 0.3)'}}>
              <RobotOutlined />
            </div>
            <div>
              <h3 style={{ color: '#ffffff', fontSize: '1.2rem', fontWeight: 600, marginBottom: '10px' }}>
                AI Analysis
              </h3>
              <p style={{ color: '#a8b2d1', fontSize: '0.9rem', lineHeight: '1.5', margin: 0 }}>
                Multi-model deep learning detection using CNNs, GANs, and transformers
              </p>
            </div>
          </div>
          
          <div style={{ fontSize: '2rem', color: '#00d4ff', fontWeight: 'bold' }}>→</div>
          
          <div 
            style={homeStyles.pipelineStep}
            onMouseEnter={(e) => {
              e.currentTarget.style.transform = 'translateY(-5px)';
              e.currentTarget.style.background = 'rgba(255, 255, 255, 0.08)';
              e.currentTarget.style.borderColor = 'rgba(0, 212, 255, 0.3)';
            }}
            onMouseLeave={(e) => {
              e.currentTarget.style.transform = 'translateY(0)';
              e.currentTarget.style.background = 'rgba(255, 255, 255, 0.05)';
              e.currentTarget.style.borderColor = 'rgba(255, 255, 255, 0.1)';
            }}
          >
            <div style={{...homeStyles.stepIcon, background: 'linear-gradient(135deg, #ffff00, #cccc00)', boxShadow: '0 10px 30px rgba(255, 255, 0, 0.3)', color: '#000'}}>
              <SearchOutlined />
            </div>
            <div>
              <h3 style={{ color: '#ffffff', fontSize: '1.2rem', fontWeight: 600, marginBottom: '10px' }}>
                Forensic Examination
              </h3>
              <p style={{ color: '#a8b2d1', fontSize: '0.9rem', lineHeight: '1.5', margin: 0 }}>
                Traditional forensic techniques: ELA, noise analysis, metadata inspection
              </p>
            </div>
          </div>
          
          <div style={{ fontSize: '2rem', color: '#00d4ff', fontWeight: 'bold' }}>→</div>
          
          <div 
            style={homeStyles.pipelineStep}
            onMouseEnter={(e) => {
              e.currentTarget.style.transform = 'translateY(-5px)';
              e.currentTarget.style.background = 'rgba(255, 255, 255, 0.08)';
              e.currentTarget.style.borderColor = 'rgba(0, 212, 255, 0.3)';
            }}
            onMouseLeave={(e) => {
              e.currentTarget.style.transform = 'translateY(0)';
              e.currentTarget.style.background = 'rgba(255, 255, 255, 0.05)';
              e.currentTarget.style.borderColor = 'rgba(255, 255, 255, 0.1)';
            }}
          >
            <div style={{...homeStyles.stepIcon, background: 'linear-gradient(135deg, #00ff88, #00cc66)', boxShadow: '0 10px 30px rgba(0, 255, 136, 0.3)'}}>
              <FileTextOutlined />
            </div>
            <div>
              <h3 style={{ color: '#ffffff', fontSize: '1.2rem', fontWeight: 600, marginBottom: '10px' }}>
                Comprehensive Report
              </h3>
              <p style={{ color: '#a8b2d1', fontSize: '0.9rem', lineHeight: '1.5', margin: 0 }}>
                Detailed forensic analysis with confidence scores and visual evidence
              </p>
            </div>
          </div>
        </div>
      </section>

      {/* Core Technologies */}
      <section style={homeStyles.techSection}>
        <Title level={2} style={homeStyles.sectionTitle}>Core Technologies</Title>
        <Row gutter={[24, 24]}>
          <Col xs={24} md={8}>
            <Card style={homeStyles.techCard} hoverable>
              <div style={homeStyles.techIcon}>
                <ExperimentOutlined />
              </div>
              <Title level={4} style={{ color: '#ffffff', textAlign: 'center', marginBottom: '20px' }}>
                Deep Learning Algorithms
              </Title>
              <ul style={{ color: '#a8b2d1', marginBottom: '30px' }}>
                <li style={{ marginBottom: '8px', fontSize: '0.9rem' }}>Convolutional Neural Networks (CNNs)</li>
                <li style={{ marginBottom: '8px', fontSize: '0.9rem' }}>Generative Adversarial Networks Detection</li>
                <li style={{ marginBottom: '8px', fontSize: '0.9rem' }}>Autoencoders for Anomaly Detection</li>
                <li style={{ marginBottom: '8px', fontSize: '0.9rem' }}>3D CNNs for Temporal Analysis</li>
                <li style={{ marginBottom: '8px', fontSize: '0.9rem' }}>Vision Transformers</li>
              </ul>
              <Progress 
                percent={95} 
                strokeColor={{ '0%': '#108ee9', '100%': '#87d068' }}
                style={{ marginTop: '20px' }}
              />
            </Card>
          </Col>
          
          <Col xs={24} md={8}>
            <Card style={homeStyles.techCard} hoverable>
              <div style={{...homeStyles.techIcon, color: '#ff0084'}}>
                <ScanOutlined />
              </div>
              <Title level={4} style={{ color: '#ffffff', textAlign: 'center', marginBottom: '20px' }}>
                Forensic Techniques
              </Title>
              <ul style={{ color: '#a8b2d1', marginBottom: '30px' }}>
                <li style={{ marginBottom: '8px', fontSize: '0.9rem' }}>Error Level Analysis (ELA)</li>
                <li style={{ marginBottom: '8px', fontSize: '0.9rem' }}>Noise Pattern Analysis</li>
                <li style={{ marginBottom: '8px', fontSize: '0.9rem' }}>Local Binary Patterns (LBP)</li>
                <li style={{ marginBottom: '8px', fontSize: '0.9rem' }}>Copy-Move Forgery Detection</li>
                <li style={{ marginBottom: '8px', fontSize: '0.9rem' }}>Temporal Inconsistency Detection</li>
              </ul>
              <Progress 
                percent={90} 
                strokeColor={{ '0%': '#722ed1', '100%': '#eb2f96' }}
                style={{ marginTop: '20px' }}
              />
            </Card>
          </Col>
          
          <Col xs={24} md={8}>
            <Card style={homeStyles.techCard} hoverable>
              <div style={{...homeStyles.techIcon, color: '#ffff00'}}>
                <SafetyCertificateOutlined />
              </div>
              <Title level={4} style={{ color: '#ffffff', textAlign: 'center', marginBottom: '20px' }}>
                Metadata Analysis
              </Title>
              <ul style={{ color: '#a8b2d1', marginBottom: '30px' }}>
                <li style={{ marginBottom: '8px', fontSize: '0.9rem' }}>EXIF Data Examination</li>
                <li style={{ marginBottom: '8px', fontSize: '0.9rem' }}>Container Metadata Verification</li>
                <li style={{ marginBottom: '8px', fontSize: '0.9rem' }}>File Header Analysis</li>
                <li style={{ marginBottom: '8px', fontSize: '0.9rem' }}>Cryptographic Hashing (SHA/MD5)</li>
                <li style={{ marginBottom: '8px', fontSize: '0.9rem' }}>Chain of Custody Tracking</li>
              </ul>
              <Progress 
                percent={88} 
                strokeColor={{ '0%': '#fa8c16', '100%': '#faad14' }}
                style={{ marginTop: '20px' }}
              />
            </Card>
          </Col>
        </Row>
      </section>

      {/* Detection Capabilities */}
      <section style={homeStyles.capabilitiesSection}>
        <Title level={2} style={homeStyles.sectionTitle}>Detection Capabilities</Title>
        <Row gutter={[32, 32]}>
          <Col xs={24} lg={12}>
            <Card style={homeStyles.techCard}>
              <Title level={4} style={{ color: '#ffffff', display: 'flex', alignItems: 'center', gap: '10px' }}>
                <ThunderboltOutlined style={{ color: '#00d4ff', fontSize: '1.5rem' }} />
                Real-time Detection
              </Title>              <Timeline
                items={[
                  {
                    dot: <CheckCircleOutlined style={{ 
                      fontSize: '16px', 
                      color: '#52c41a',
                      background: 'rgba(255, 255, 255, 0.1)',
                      borderRadius: '50%',
                      padding: '2px'
                    }} />,
                    children: <span style={{ color: '#a8b2d1' }}>Face swap detection - 99.2% accuracy</span>
                  },
                  {
                    dot: <CheckCircleOutlined style={{ 
                      fontSize: '16px', 
                      color: '#52c41a',
                      background: 'rgba(255, 255, 255, 0.1)',
                      borderRadius: '50%',
                      padding: '2px'
                    }} />,
                    children: <span style={{ color: '#a8b2d1' }}>Lip-sync manipulation detection - 97.8% accuracy</span>
                  },
                  {
                    dot: <CheckCircleOutlined style={{ 
                      fontSize: '16px', 
                      color: '#52c41a',
                      background: 'rgba(255, 255, 255, 0.1)',
                      borderRadius: '50%',
                      padding: '2px'
                    }} />,
                    children: <span style={{ color: '#a8b2d1' }}>AI-generated content identification - 98.5% accuracy</span>
                  },
                  {
                    dot: <CheckCircleOutlined style={{ 
                      fontSize: '16px', 
                      color: '#52c41a',
                      background: 'rgba(255, 255, 255, 0.1)',
                      borderRadius: '50%',
                      padding: '2px'
                    }} />,
                    children: <span style={{ color: '#a8b2d1' }}>Traditional photo manipulation - 96.9% accuracy</span>
                  }
                ]}
              />
            </Card>
          </Col>
          
          <Col xs={24} lg={12}>
            <Card style={homeStyles.techCard}>
              <Title level={4} style={{ color: '#ffffff', display: 'flex', alignItems: 'center', gap: '10px' }}>
                <EyeOutlined style={{ color: '#00d4ff', fontSize: '1.5rem' }} />
                Visual Analysis
              </Title>
              <div style={{ display: 'flex', justifyContent: 'space-around', alignItems: 'center', marginTop: '30px' }}>
                <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: '15px' }}>
                  <Progress type="circle" percent={97} size={80} strokeColor="#1890ff" />
                  <span style={{ color: '#a8b2d1', fontSize: '0.9rem', textAlign: 'center' }}>Facial Artifacts</span>
                </div>
                <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: '15px' }}>
                  <Progress type="circle" percent={95} size={80} strokeColor="#52c41a" />
                  <span style={{ color: '#a8b2d1', fontSize: '0.9rem', textAlign: 'center' }}>Temporal Consistency</span>
                </div>
                <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: '15px' }}>
                  <Progress type="circle" percent={93} size={80} strokeColor="#faad14" />
                  <span style={{ color: '#a8b2d1', fontSize: '0.9rem', textAlign: 'center' }}>Edge Anomalies</span>
                </div>
              </div>
            </Card>
          </Col>
        </Row>
      </section>

      {/* Call to Action */}
      <section style={homeStyles.ctaSection}>
        <Card style={homeStyles.ctaCard}>
          <div style={{ padding: '60px 40px' }}>
            <Title level={3} style={{ color: '#ffffff', fontSize: '2rem', marginBottom: '20px' }}>
              Ready to Secure Digital Truth?
            </Title>
            <Paragraph style={{ color: '#a8b2d1', fontSize: '1.1rem', marginBottom: '40px' }}>
              Join forensic investigators worldwide in the fight against digital deception.
            </Paragraph>
            <Space size="large">
              <Button 
                type="primary" 
                size="large" 
                icon={<CloudUploadOutlined />}
                style={{
                  height: '50px',
                  padding: '0 30px',
                  fontSize: '1rem',
                  borderRadius: '25px',
                  background: 'linear-gradient(45deg, #00d4ff, #0099cc)',
                  border: 'none',
                  boxShadow: '0 10px 30px rgba(0, 212, 255, 0.3)',
                }}
              >
                Start Analysis
              </Button>
              <Button 
                size="large" 
                icon={<BarChartOutlined />}
                style={{
                  height: '50px',
                  padding: '0 30px',
                  fontSize: '1rem',
                  borderRadius: '25px',
                  borderColor: '#00d4ff',
                  color: '#00d4ff',
                }}
              >
                View Reports
              </Button>
            </Space>
          </div>
        </Card>
      </section>
    </div>
  );
};

export default HomePage;
