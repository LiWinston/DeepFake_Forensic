import React, { useEffect, useState, useCallback } from 'react';
import {
  Card,
  Table,
  Tag,
  Space,
  Button,
  Descriptions,
  Alert,
  Progress,
  Modal,
  Typography,
  Row,
  Col,
  Statistic,
  Timeline,
  Collapse,
  Tree,
} from 'antd';
import {
  AnalysisOutlined,
  CheckCircleOutlined,
  ExclamationCircleOutlined,
  LoadingOutlined,
  WarningOutlined,
  InfoCircleOutlined,
  EyeOutlined,
  DeleteOutlined,
} from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import { useMetadataAnalysis } from '../hooks';
import { formatDateTime } from '../utils';
import type { MetadataAnalysis, MetadataResult, UploadFile } from '../types';

const { Text, Title, Paragraph } = Typography;
const { Panel } = Collapse;

interface MetadataAnalysisProps {
  file?: UploadFile;
  showFileInfo?: boolean;
}

const MetadataAnalysisComponent: React.FC<MetadataAnalysisProps> = ({
  file,
  showFileInfo = true,
}) => {
  const { analyses, loading, analyzeFile, loadAnalyses, deleteAnalysis } = useMetadataAnalysis();
  const [selectedAnalysis, setSelectedAnalysis] = useState<MetadataAnalysis | null>(null);
  const [detailModalVisible, setDetailModalVisible] = useState(false);

  useEffect(() => {
    if (file) {
      loadAnalyses(file.id);
    } else {
      loadAnalyses();
    }
  }, [file, loadAnalyses]);

  const handleStartAnalysis = useCallback(async () => {
    if (!file) return;
    
    try {
      await analyzeFile(file.id, 'FULL');
      // Reload analyses to show the new one
      setTimeout(() => loadAnalyses(file.id), 1000);
    } catch (error) {
      console.error('Failed to start analysis:', error);
    }
  }, [file, analyzeFile, loadAnalyses]);

  const handleViewDetails = useCallback((analysis: MetadataAnalysis) => {
    setSelectedAnalysis(analysis);
    setDetailModalVisible(true);
  }, []);

  const handleDeleteAnalysis = useCallback(async (analysisId: string) => {
    await deleteAnalysis(analysisId);
  }, [deleteAnalysis]);

  const getStatusIcon = (status: string) => {
    switch (status) {
      case 'COMPLETED':
        return <CheckCircleOutlined style={{ color: '#52c41a' }} />;
      case 'FAILED':
        return <ExclamationCircleOutlined style={{ color: '#ff4d4f' }} />;
      case 'PROCESSING':
        return <LoadingOutlined style={{ color: '#1890ff' }} />;
      case 'PENDING':
        return <InfoCircleOutlined style={{ color: '#faad14' }} />;
      default:
        return null;
    }
  };

  const getStatusTag = (status: string) => {
    const statusConfig = {
      PENDING: { color: 'orange', text: 'Pending' },
      PROCESSING: { color: 'blue', text: 'Processing' },
      COMPLETED: { color: 'green', text: 'Completed' },
      FAILED: { color: 'red', text: 'Failed' },
    };
    
    const config = statusConfig[status as keyof typeof statusConfig] || 
                  { color: 'default', text: status };
    
    return <Tag color={config.color}>{config.text}</Tag>;
  };

  const getAnalysisTypeTag = (type: string) => {
    const typeConfig = {
      EXIF: { color: 'purple', text: 'EXIF Data' },
      HEADER: { color: 'cyan', text: 'File Headers' },
      HASH: { color: 'blue', text: 'Hash Analysis' },
      FULL: { color: 'gold', text: 'Full Analysis' },
    };
    
    const config = typeConfig[type as keyof typeof typeConfig] || 
                  { color: 'default', text: type };
    
    return <Tag color={config.color}>{config.text}</Tag>;
  };

  const getRiskLevelTag = (riskScore: number) => {
    if (riskScore <= 30) {
      return <Tag color="green">Low Risk</Tag>;
    } else if (riskScore <= 70) {
      return <Tag color="orange">Medium Risk</Tag>;
    } else {
      return <Tag color="red">High Risk</Tag>;
    }
  };

  const columns: ColumnsType<MetadataAnalysis> = [
    {
      title: 'Analysis Type',
      dataIndex: 'analysisType',
      key: 'analysisType',
      render: (type: string) => getAnalysisTypeTag(type),
    },
    {
      title: 'Status',
      dataIndex: 'status',
      key: 'status',
      render: (status: string) => (
        <Space>
          {getStatusIcon(status)}
          {getStatusTag(status)}
        </Space>
      ),
    },
    {
      title: 'Risk Score',
      key: 'riskScore',
      render: (_, record: MetadataAnalysis) => {
        if (record.result?.suspicious) {
          return (
            <Space>
              <Progress
                type="circle"
                size="small"
                percent={record.result.suspicious.riskScore}
                format={(percent) => `${percent}%`}
                strokeColor={
                  (percent || 0) <= 30 ? '#52c41a' :
                  (percent || 0) <= 70 ? '#faad14' : '#ff4d4f'
                }
              />
              {getRiskLevelTag(record.result.suspicious.riskScore)}
            </Space>
          );
        }
        return <Text type="secondary">-</Text>;
      },
    },
    {
      title: 'Created Time',
      dataIndex: 'createdTime',
      key: 'createdTime',
      render: (time: string) => formatDateTime(time),
    },
    {
      title: 'Actions',
      key: 'actions',
      render: (_, record: MetadataAnalysis) => (
        <Space>
          <Button
            type="text"
            size="small"
            icon={<EyeOutlined />}
            onClick={() => handleViewDetails(record)}
            disabled={record.status !== 'COMPLETED'}
          >
            Details
          </Button>
          <Button
            type="text"
            size="small"
            icon={<DeleteOutlined />}
            danger
            onClick={() => handleDeleteAnalysis(record.id)}
          >
            Delete
          </Button>
        </Space>
      ),
    },
  ];

  const renderMetadataTree = (data: Record<string, any>, prefix = '') => {
    return Object.entries(data).map(([key, value]) => {
      const nodeKey = prefix ? `${prefix}.${key}` : key;
      
      if (typeof value === 'object' && value !== null && !Array.isArray(value)) {
        return {
          title: <Text strong>{key}</Text>,
          key: nodeKey,
          children: renderMetadataTree(value, nodeKey),
        };
      } else {
        return {
          title: (
            <Space>
              <Text>{key}:</Text>
              <Text copyable type="secondary">
                {Array.isArray(value) ? value.join(', ') : String(value)}
              </Text>
            </Space>
          ),
          key: nodeKey,
          isLeaf: true,
        };
      }
    });
  };

  const renderAnalysisDetails = (result: MetadataResult) => {
    const panels = [];

    if (result.exifData) {
      panels.push(
        <Panel header="EXIF Data" key="exif">
          <Tree
            defaultExpandAll
            treeData={renderMetadataTree(result.exifData)}
          />
        </Panel>
      );
    }

    if (result.fileHeaders) {
      panels.push(
        <Panel header="File Headers" key="headers">
          <Tree
            defaultExpandAll
            treeData={renderMetadataTree(result.fileHeaders)}
          />
        </Panel>
      );
    }

    if (result.hashData) {
      panels.push(
        <Panel header="Hash Data" key="hash">
          <Descriptions bordered size="small">
            {result.hashData.md5 && (
              <Descriptions.Item label="MD5" span={3}>
                <Text copyable code>{result.hashData.md5}</Text>
              </Descriptions.Item>
            )}
            {result.hashData.sha256 && (
              <Descriptions.Item label="SHA256" span={3}>
                <Text copyable code>{result.hashData.sha256}</Text>
              </Descriptions.Item>
            )}
          </Descriptions>
        </Panel>
      );
    }

    if (result.technicalData) {
      panels.push(
        <Panel header="Technical Data" key="technical">
          <Descriptions bordered size="small">
            {Object.entries(result.technicalData).map(([key, value]) => (
              <Descriptions.Item label={key} key={key}>
                {String(value)}
              </Descriptions.Item>
            ))}
          </Descriptions>
        </Panel>
      );
    }

    if (result.suspicious) {
      panels.push(
        <Panel 
          header={
            <Space>
              <WarningOutlined />
              <Text>Suspicious Analysis</Text>
              {getRiskLevelTag(result.suspicious.riskScore)}
            </Space>
          } 
          key="suspicious"
        >
          <Row gutter={16}>
            <Col span={8}>
              <Statistic
                title="Risk Score"
                value={result.suspicious.riskScore}
                suffix="%"
                valueStyle={{
                  color: result.suspicious.riskScore <= 30 ? '#3f8600' :
                         result.suspicious.riskScore <= 70 ? '#cf1322' : '#cf1322'
                }}
              />
            </Col>
            <Col span={8}>
              <Statistic
                title="Has Anomalies"
                value={result.suspicious.hasAnomalies ? 'Yes' : 'No'}
                valueStyle={{
                  color: result.suspicious.hasAnomalies ? '#cf1322' : '#3f8600'
                }}
              />
            </Col>
            <Col span={8}>
              <Statistic
                title="Anomalies Found"
                value={result.suspicious.anomalies.length}
              />
            </Col>
          </Row>
          
          {result.suspicious.anomalies.length > 0 && (
            <div style={{ marginTop: 16 }}>
              <Title level={5}>Detected Anomalies:</Title>
              <Timeline>
                {result.suspicious.anomalies.map((anomaly, index) => (
                  <Timeline.Item
                    key={index}
                    dot={<WarningOutlined style={{ color: '#ff4d4f' }} />}
                  >
                    <Text>{anomaly}</Text>
                  </Timeline.Item>
                ))}
              </Timeline>
            </div>
          )}
        </Panel>
      );
    }

    return (
      <Collapse defaultActiveKey={['suspicious']} ghost>
        {panels}
      </Collapse>
    );
  };

  return (
    <div>
      <Card
        title={
          <Space>
            <AnalysisOutlined />
            <Title level={4} style={{ margin: 0 }}>
              Metadata Analysis
            </Title>
          </Space>
        }
        extra={
          file && (
            <Button
              type="primary"
              icon={<AnalysisOutlined />}
              onClick={handleStartAnalysis}
              loading={loading}
              disabled={!file || file.status !== 'COMPLETED'}
            >
              Start Analysis
            </Button>
          )
        }
      >
        {showFileInfo && file && (
          <Alert
            message={`Analyzing file: ${file.originalName}`}
            description={`File size: ${file.fileSize} bytes | Upload time: ${formatDateTime(file.uploadTime)}`}
            type="info"
            showIcon
            style={{ marginBottom: 16 }}
          />
        )}

        <Table
          columns={columns}
          dataSource={analyses}
          rowKey="id"
          loading={loading}
          pagination={{
            pageSize: 10,
            showSizeChanger: true,
            showQuickJumper: true,
            showTotal: (total, range) =>
              `${range[0]}-${range[1]} of ${total} analyses`,
          }}
        />
      </Card>

      {/* Analysis Details Modal */}
      <Modal
        title={
          <Space>
            <AnalysisOutlined />
            <Text>Analysis Details</Text>
            {selectedAnalysis && getAnalysisTypeTag(selectedAnalysis.analysisType)}
          </Space>
        }
        open={detailModalVisible}
        onCancel={() => setDetailModalVisible(false)}
        footer={null}
        width={1000}
        centered
      >
        {selectedAnalysis && (
          <div>
            <Descriptions bordered size="small" style={{ marginBottom: 16 }}>
              <Descriptions.Item label="Analysis ID" span={2}>
                <Text copyable code>{selectedAnalysis.id}</Text>
              </Descriptions.Item>
              <Descriptions.Item label="Status">
                {getStatusTag(selectedAnalysis.status)}
              </Descriptions.Item>
              <Descriptions.Item label="Created Time" span={3}>
                {formatDateTime(selectedAnalysis.createdTime)}
              </Descriptions.Item>
              {selectedAnalysis.completedTime && (
                <Descriptions.Item label="Completed Time" span={3}>
                  {formatDateTime(selectedAnalysis.completedTime)}
                </Descriptions.Item>
              )}
              {selectedAnalysis.errorMessage && (
                <Descriptions.Item label="Error Message" span={3}>
                  <Text type="danger">{selectedAnalysis.errorMessage}</Text>
                </Descriptions.Item>
              )}
            </Descriptions>

            {selectedAnalysis.result && renderAnalysisDetails(selectedAnalysis.result)}
          </div>
        )}
      </Modal>
    </div>
  );
};

export default MetadataAnalysisComponent;
