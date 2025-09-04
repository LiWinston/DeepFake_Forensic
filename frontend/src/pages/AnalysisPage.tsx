import React from 'react';
import {
  Layout,
  Typography,
  Card,
  Row,
  Col,
  Alert,
} from 'antd';
import AnalysisOverview from '../components/AnalysisOverview';

const { Content } = Layout;
const { Title, Paragraph } = Typography;

const AnalysisPage: React.FC = () => {
  return (
    <Content style={{ padding: '24px' }}>
      {/* Page Header */}
      <div style={{ marginBottom: 24 }}>
        <Title level={2}>Metadata Analysis</Title>
        <Paragraph>
          View and manage metadata analysis results for all uploaded files. 
          Analyze EXIF data, file headers, hash values, and detect suspicious anomalies.
        </Paragraph>
      </div>

      {/* Information Alert */}
      <Alert
        message="Analysis Information"
        description={
          <div>
            <p>• <strong>EXIF Analysis:</strong> Extract camera settings, GPS data, and device information</p>
            <p>• <strong>Header Analysis:</strong> Examine file structure and container metadata</p>
            <p>• <strong>Hash Analysis:</strong> Verify file integrity using MD5 and SHA-256</p>
            <p>• <strong>Anomaly Detection:</strong> Identify suspicious patterns and alterations</p>
          </div>
        }
        type="info"
        showIcon
        style={{ marginBottom: 24 }}
      />

      {/* Analysis Component */}
      <Row>
        <Col span={24}>
          <AnalysisOverview showFileInfo={false} />
        </Col>
      </Row>

      {/* Analysis Guidelines */}
      <Card 
        title="Analysis Guidelines" 
        style={{ marginTop: 24 }}
        size="small"
      >
        <Row gutter={[16, 16]}>
          <Col xs={24} md={12}>
            <Title level={5}>Risk Assessment</Title>
            <div>
              <p><strong>Low Risk (0-30%):</strong> File appears authentic with no significant anomalies</p>
              <p><strong>Medium Risk (31-70%):</strong> Some suspicious patterns detected, requires review</p>
              <p><strong>High Risk (71-100%):</strong> Multiple anomalies detected, likely manipulated</p>
            </div>
          </Col>
          <Col xs={24} md={12}>
            <Title level={5}>Analysis Types</Title>
            <div>
              <p><strong>EXIF:</strong> Camera metadata and settings analysis</p>
              <p><strong>HEADER:</strong> File structure and format verification</p>
              <p><strong>HASH:</strong> Integrity verification using cryptographic hashes</p>
              <p><strong>FULL:</strong> Comprehensive analysis including all types</p>
            </div>
          </Col>
        </Row>
      </Card>
    </Content>
  );
};

export default AnalysisPage;
