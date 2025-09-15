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
  message,
} from 'antd';
import {
  CheckCircleOutlined,
  ExclamationCircleOutlined,
  LoadingOutlined,
  WarningOutlined,
  InfoCircleOutlined,
  EyeOutlined,
  DeleteOutlined,
  BarChartOutlined,
} from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import { useMetadataAnalysis } from '../hooks';
import { formatDateTime } from '../utils';
import type { MetadataAnalysis, MetadataResult, UploadFile } from '../types';

const { Text, Title } = Typography;
const { Panel } = Collapse;

interface MetadataAnalysisProps {
  file?: UploadFile;
  showFileInfo?: boolean;
}

const MetadataAnalysisComponent: React.FC<MetadataAnalysisProps> = ({
  file,
  showFileInfo = true,
}) => {
  const { 
    analyses, 
    loading, 
    startAnalysis, 
    getAnalysis,
    getAnalysisStatus,
    loadAnalyses, 
    deleteAnalysis 
  } = useMetadataAnalysis();
  
  const [selectedAnalysis, setSelectedAnalysis] = useState<MetadataAnalysis | null>(null);
  const [detailModalVisible, setDetailModalVisible] = useState(false);
  const [currentFileStatus, setCurrentFileStatus] = useState<string>('');
  const [pollingTimer, setPollingTimer] = useState<number | null>(null);

  // Load analysis status and results
  useEffect(() => {
    if (file && file.md5Hash) {
      loadAnalyses(file.md5Hash);
      // Get current analysis status
      getAnalysisStatus(file.md5Hash).then(status => {
        setCurrentFileStatus(status.status);
        // If processing, start polling
        if (status.status === 'PROCESSING' && file.md5Hash) {
          startPolling(file.md5Hash);
        }
      });
    } else {
      loadAnalyses();
      setCurrentFileStatus('');
    }
  }, [file, loadAnalyses, getAnalysisStatus]);

  // Clean up timer
  useEffect(() => {
    return () => {
      if (pollingTimer) {
        clearInterval(pollingTimer);
      }
    };
  }, [pollingTimer]);

  // Start polling to check analysis status
  const startPolling = useCallback((fileMd5: string) => {
    const timer = window.setInterval(async () => {
      const status = await getAnalysisStatus(fileMd5);
      setCurrentFileStatus(status.status);
      
      if (status.status !== 'PROCESSING') {
        clearInterval(timer);
        setPollingTimer(null);
        // Reload analysis results
        if (status.hasAnalysis) {
          loadAnalyses(fileMd5);
        }
      }
    }, 3000); // Check every 3 seconds
    
    setPollingTimer(timer);
  }, [getAnalysisStatus, loadAnalyses]);

  const handleStartAnalysis = useCallback(async () => {
    if (!file || !file.md5Hash) {
      message.error('File MD5 hash is missing, cannot perform metadata analysis');
      return;
    }
    
    try {
      const success = await startAnalysis(file.md5Hash);
      if (success) {
        setCurrentFileStatus('PROCESSING');
        // Start polling to check status
        startPolling(file.md5Hash);
      }
    } catch (error) {
      console.error('Failed to start analysis:', error);
      message.error('Failed to start analysis');
    }
  }, [file, startAnalysis, startPolling]);

  const handleViewResults = useCallback(async () => {
    if (!file || !file.md5Hash) return;
    
    try {
      await getAnalysis(file.md5Hash);
    } catch (error) {
      console.error('Failed to get analysis results:', error);
      message.error('Failed to get analysis results');
    }
  }, [file, getAnalysis]);

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
                format={(p) => `${p}%`}
                strokeColor={
                  record.result.suspicious.riskScore <= 30 ? '#52c41a' :
                  record.result.suspicious.riskScore <= 70 ? '#faad14' : '#ff4d4f'
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

  const renderMetadataTree = (data: Record<string, any>, prefix = ''): any[] => {
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

    // New: File Header Analysis (Week 7 requirement)
    if (result.fileHeaderAnalysis) {
      const headerData = result.fileHeaderAnalysis;
      const getRiskIcon = (riskLevel?: string) => {
        switch (riskLevel) {
          case 'HIGH': return <WarningOutlined style={{ color: '#ff4d4f' }} />;
          case 'MEDIUM': return <ExclamationCircleOutlined style={{ color: '#faad14' }} />;
          case 'LOW': return <CheckCircleOutlined style={{ color: '#52c41a' }} />;
          default: return <InfoCircleOutlined style={{ color: '#1890ff' }} />;
        }
      };

      panels.push(
        <Panel 
          header={
            <Space>
              {getRiskIcon(headerData.riskLevel)}
              <Text>文件头签名分析</Text>
              <Tag color={
                headerData.riskLevel === 'HIGH' ? 'red' :
                headerData.riskLevel === 'MEDIUM' ? 'orange' :
                headerData.riskLevel === 'LOW' ? 'green' : 'blue'
              }>
                {headerData.riskLevel || 'UNKNOWN'}
              </Tag>
            </Space>
          } 
          key="fileHeader"
        >
          {headerData.summary && (
            <Alert
              message={headerData.summary}
              type={
                headerData.riskLevel === 'HIGH' ? 'error' :
                headerData.riskLevel === 'MEDIUM' ? 'warning' :
                headerData.riskLevel === 'LOW' ? 'success' : 'info'
              }
              showIcon
              style={{ marginBottom: 16 }}
            />
          )}
          
          <Descriptions bordered size="small">
            <Descriptions.Item label="检测格式" span={2}>
              <Text code>{headerData.detectedFormat || 'Unknown'}</Text>
            </Descriptions.Item>
            <Descriptions.Item label="期望格式">
              <Text code>{headerData.expectedFormat || 'Unknown'}</Text>
            </Descriptions.Item>
            
            <Descriptions.Item label="格式匹配" span={2}>
              {headerData.formatMatch ? (
                <Tag color="green">匹配</Tag>
              ) : (
                <Tag color="red">不匹配</Tag>
              )}
            </Descriptions.Item>
            <Descriptions.Item label="完整性状态">
              <Tag color={
                headerData.integrityStatus === 'INTACT' ? 'green' :
                headerData.integrityStatus === 'FORMAT_MISMATCH' ? 'red' :
                headerData.integrityStatus === 'UNKNOWN_FORMAT' ? 'orange' : 'default'
              }>
                {headerData.integrityStatus}
              </Tag>
            </Descriptions.Item>
            
            {headerData.signatureHex && (
              <Descriptions.Item label="文件签名" span={3}>
                <Text copyable code style={{ fontSize: '12px' }}>
                  {headerData.signatureHex}
                </Text>
              </Descriptions.Item>
            )}
          </Descriptions>
        </Panel>
      );
    }

    // New: Container Analysis (Week 7 requirement)
    if (result.containerAnalysis && Object.keys(result.containerAnalysis).length > 0) {
      panels.push(
        <Panel 
          header={
            <Space>
              <InfoCircleOutlined />
              <Text>容器完整性分析</Text>
              {result.containerAnalysis.status === 'PENDING_IMPLEMENTATION' && (
                <Tag color="orange">开发中</Tag>
              )}
            </Space>
          } 
          key="container"
        >
          {result.containerAnalysis.message && (
            <Alert
              message={result.containerAnalysis.message}
              type="info"
              showIcon
              style={{ marginBottom: 16 }}
            />
          )}
          
          <Descriptions bordered size="small">
            <Descriptions.Item label="完整性验证" span={2}>
              {result.containerAnalysis.integrityVerified !== undefined ? (
                <Tag color={result.containerAnalysis.integrityVerified ? 'green' : 'red'}>
                  {result.containerAnalysis.integrityVerified ? '已验证' : '未验证'}
                </Tag>
              ) : (
                <Tag color="default">未检查</Tag>
              )}
            </Descriptions.Item>
            <Descriptions.Item label="分析状态">
              <Tag color={result.containerAnalysis.status === 'PENDING_IMPLEMENTATION' ? 'orange' : 'blue'}>
                {result.containerAnalysis.status || 'UNKNOWN'}
              </Tag>
            </Descriptions.Item>
            
            {result.containerAnalysis.analysisResults && (
              <Descriptions.Item label="分析结果" span={3}>
                <Text type="secondary" style={{ fontSize: '12px', whiteSpace: 'pre-wrap' }}>
                  {result.containerAnalysis.analysisResults}
                </Text>
              </Descriptions.Item>
            )}
          </Descriptions>
        </Panel>
      );
    }

    // Analysis Notes Panel (dedicated display)
    if (result.suspicious?.analysisNotes) {
      panels.push(
        <Panel header="详细分析备注" key="analysisNotes">
          <div style={{ 
            padding: '12px', 
            backgroundColor: '#f5f5f5', 
            borderRadius: '6px',
            fontSize: '13px',
            lineHeight: '1.6',
            whiteSpace: 'pre-wrap',
            fontFamily: 'monospace'
          }}>
            {result.suspicious.analysisNotes}
          </div>
        </Panel>
      );
    }

    // Raw Metadata Panel (complete technical data)
    if (result.rawMetadata) {
      panels.push(
        <Panel 
          header={
            <Space>
              <BarChartOutlined />
              <Text>原始元数据</Text>
              <Tag color="blue">完整技术数据</Tag>
            </Space>
          } 
          key="rawMetadata"
        >
          <div style={{ marginBottom: 16 }}>
            <Text type="secondary">
              以下是从文件中提取的完整原始元数据，包含所有技术细节。这些数据对法证分析非常重要。
            </Text>
          </div>
          
          <div style={{ 
            padding: '16px', 
            backgroundColor: '#fafafa', 
            border: '1px solid #d9d9d9',
            borderRadius: '6px',
            fontSize: '12px',
            lineHeight: '1.5',
            whiteSpace: 'pre-wrap',
            fontFamily: 'Consolas, Monaco, "Courier New", monospace',
            maxHeight: '400px',
            overflowY: 'auto'
          }}>
            {result.rawMetadata}
          </div>
          
          <div style={{ marginTop: 12, textAlign: 'right' }}>
            <Button 
              size="small" 
              onClick={() => {
                const blob = new Blob([result.rawMetadata || ''], { type: 'text/plain' });
                const url = URL.createObjectURL(blob);
                const a = document.createElement('a');
                a.href = url;
                a.download = `metadata_${new Date().getTime()}.txt`;
                a.click();
                URL.revokeObjectURL(url);
              }}
            >
              下载原始数据
            </Button>
          </div>
        </Panel>
      );
    }

    // New: parsed raw metadata tree
    if ((result as any).parsedMetadata && Object.keys((result as any).parsedMetadata).length > 0) {
      panels.push(
        <Panel header="结构化分析数据" key="parsed">
          <div style={{ marginBottom: 16 }}>
            <Text type="secondary">
              解析后的结构化元数据，便于查看和分析各项技术参数。
            </Text>
          </div>
          <Tree defaultExpandAll treeData={renderMetadataTree((result as any).parsedMetadata)} />
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
          
          {result.suspicious.assessmentConclusion && (
            <div style={{ marginTop: 16 }}>
              <Title level={5}>法证评估结论:</Title>
              <Alert
                message={result.suspicious.assessmentConclusion}
                type={
                  result.suspicious.riskScore >= 70 ? 'error' :
                  result.suspicious.riskScore >= 40 ? 'warning' :
                  result.suspicious.riskScore >= 20 ? 'info' : 'success'
                }
                showIcon
                style={{ marginBottom: 16 }}
              />
            </div>
          )}
          
          {result.suspicious.anomalies.length > 0 && (
            <div style={{ marginTop: 16 }}>
              <Title level={5}>检测到的异常指标:</Title>
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
          
          {result.suspicious.analysisNotes && (
            <div style={{ marginTop: 16 }}>
              <Title level={5}>详细分析备注:</Title>
              <Text type="secondary" style={{ fontSize: '12px' }}>
                {result.suspicious.analysisNotes}
              </Text>
            </div>
          )}
        </Panel>
      );
    }

    return (
      <Collapse 
        defaultActiveKey={['suspicious', 'fileHeader', 'analysisNotes']} 
        ghost
        size="small"
      >
        {panels}
      </Collapse>
    );
  };

  return (
    <div>
      <Card
        title={
          <Space>
            <BarChartOutlined />
            <Title level={4} style={{ margin: 0 }}>
              Metadata Analysis
            </Title>
          </Space>
        }
        extra={
          file && (
            <Space>
              {/* Analysis status indicator */}
              {currentFileStatus && (
                <span>
                  Status: <Tag color={
                    currentFileStatus === 'SUCCESS' ? 'green' : 
                    currentFileStatus === 'PROCESSING' ? 'blue' :
                    currentFileStatus === 'FAILED' ? 'red' : 'default'
                  }>
                    {currentFileStatus === 'SUCCESS' ? 'Completed' :
                     currentFileStatus === 'PROCESSING' ? 'Analyzing' :
                     currentFileStatus === 'FAILED' ? 'Failed' :
                     currentFileStatus === 'NOT_FOUND' ? 'Not Analyzed' : currentFileStatus}
                  </Tag>
                </span>
              )}
              
              {/* Show different buttons based on status */}
              {currentFileStatus === 'SUCCESS' ? (
                <Button
                  type="primary"
                  icon={<EyeOutlined />}
                  onClick={handleViewResults}
                  loading={loading}
                >
                  View Results
                </Button>
              ) : currentFileStatus === 'FAILED' ? (
                <Button
                  type="primary"
                  icon={<BarChartOutlined />}
                  onClick={handleStartAnalysis}
                  loading={loading}
                  disabled={!file || file.status !== 'COMPLETED'}
                >
                  Re-analyze
                </Button>
              ) : currentFileStatus === 'PROCESSING' ? (
                <Button
                  type="primary"
                  icon={<LoadingOutlined />}
                  loading={true}
                  disabled={true}
                >
                  Analyzing...
                </Button>
              ) : (
                <Button
                  type="primary"
                  icon={<BarChartOutlined />}
                  onClick={handleStartAnalysis}
                  loading={loading}
                  disabled={!file || file.status !== 'COMPLETED' || !file.md5Hash}
                >
                  Start Analysis
                </Button>
              )}
            </Space>
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
            <BarChartOutlined />
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
