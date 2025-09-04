import React, { useEffect, useState, useCallback } from 'react';
import {
  Card,
  Table,
  Tag,
  Space,
  Button,
  Descriptions,
  Alert,
  Modal,
  Typography,
  Row,
  Col,
  Statistic,
  Tabs,
  Image,
  Divider,
  message,
  Timeline,
  Collapse,
  Tree,
} from 'antd';
import {
  CheckCircleOutlined,
  ExclamationCircleOutlined,
  LoadingOutlined,
  InfoCircleOutlined,
  EyeOutlined,
  DeleteOutlined,
  BarChartOutlined,
  ExperimentOutlined,
  RobotOutlined,
  ScanOutlined,
  WarningOutlined,
} from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import { useMetadataAnalysis } from '../hooks';
import { traditionalAnalysisAPI } from '../services/traditional';
import { formatDateTime } from '../utils';
import type { 
  MetadataAnalysis, 
  UploadFile, 
  TraditionalAnalysisResult
} from '../types';

const { Text, Title } = Typography;
const { TabPane } = Tabs;
const { Panel } = Collapse;

interface AnalysisOverviewProps {
  file?: UploadFile;
  showFileInfo?: boolean;
}

interface AnalysisRecord {
  id: string;
  type: 'METADATA' | 'TRADITIONAL' | 'AI';
  status: string;
  riskScore?: number;
  createdTime: string;
  completedTime?: string;
  errorMessage?: string;
  data?: MetadataAnalysis | TraditionalAnalysisResult | any;
}

const AnalysisOverview: React.FC<AnalysisOverviewProps> = ({
  file,
  showFileInfo = true,
}) => {
  const { 
    analyses: metadataAnalyses, 
    loading: metadataLoading, 
    startAnalysis: startMetadataAnalysis, 
    loadAnalyses: loadMetadataAnalyses, 
    deleteAnalysis: deleteMetadataAnalysis 
  } = useMetadataAnalysis();
  
  const [allAnalyses, setAllAnalyses] = useState<AnalysisRecord[]>([]);
  const [loading, setLoading] = useState(false);
  const [selectedAnalysis, setSelectedAnalysis] = useState<AnalysisRecord | null>(null);
  const [detailModalVisible, setDetailModalVisible] = useState(false);

  // Load all analysis types
  const loadAllAnalyses = useCallback(async (fileMd5?: string) => {
    if (!fileMd5) {
      setAllAnalyses([]);
      return;
    }

    setLoading(true);
    const analyses: AnalysisRecord[] = [];

    try {
      // Load metadata analyses
      await loadMetadataAnalyses(fileMd5);
      
      // Load traditional analysis
      const traditionalResult = await traditionalAnalysisAPI.getAnalysisResult(fileMd5);
      if (traditionalResult) {
        analyses.push({
          id: `traditional-${traditionalResult.id}`,
          type: 'TRADITIONAL',
          status: traditionalResult.analysisStatus,
          riskScore: traditionalResult.overallConfidenceScore,
          createdTime: traditionalResult.createdAt,
          completedTime: traditionalResult.updatedAt,
          errorMessage: traditionalResult.errorMessage,
          data: traditionalResult
        });
      } else {
        // Check if traditional analysis is in progress
        const traditionalStatus = await traditionalAnalysisAPI.getAnalysisStatus(fileMd5);
        if (traditionalStatus !== 'NOT_FOUND') {
          analyses.push({
            id: `traditional-pending`,
            type: 'TRADITIONAL',
            status: traditionalStatus,
            createdTime: new Date().toISOString(),
          });
        }
      }

      // TODO: Add AI analysis loading here when implemented
      
    } catch (error) {
      console.error('Error loading analyses:', error);
      message.error('Failed to load analysis results');
    } finally {
      setLoading(false);
    }
  }, [loadMetadataAnalyses]);

  // Convert metadata analyses to AnalysisRecord format
  useEffect(() => {
    const metadataRecords: AnalysisRecord[] = metadataAnalyses.map(analysis => ({
      id: `metadata-${analysis.id}`,
      type: 'METADATA' as const,
      status: analysis.status,
      riskScore: analysis.result?.suspicious?.riskScore,
      createdTime: analysis.createdTime,
      completedTime: analysis.completedTime,
      errorMessage: analysis.errorMessage,
      data: analysis
    }));

    // Merge with existing traditional/AI analyses
    setAllAnalyses(prev => {
      const nonMetadata = prev.filter(a => a.type !== 'METADATA');
      return [...metadataRecords, ...nonMetadata];
    });
  }, [metadataAnalyses]);

  // Load analyses when file changes
  useEffect(() => {
    if (file?.md5Hash) {
      loadAllAnalyses(file.md5Hash);
    } else {
      setAllAnalyses([]);
    }
  }, [file, loadAllAnalyses]);

  const getStatusIcon = (status: string) => {
    switch (status) {
      case 'COMPLETED':
        return <CheckCircleOutlined style={{ color: '#52c41a' }} />;
      case 'FAILED':
        return <ExclamationCircleOutlined style={{ color: '#ff4d4f' }} />;
      case 'PROCESSING':
      case 'IN_PROGRESS':
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
      IN_PROGRESS: { color: 'blue', text: 'Processing' },
      COMPLETED: { color: 'green', text: 'Completed' },
      FAILED: { color: 'red', text: 'Failed' },
    };
    
    const config = statusConfig[status as keyof typeof statusConfig] || 
                  { color: 'default', text: status };
    
    return <Tag color={config.color}>{config.text}</Tag>;
  };

  const getAnalysisTypeInfo = (type: string) => {
    const typeConfig = {
      METADATA: { 
        color: 'blue', 
        text: 'Metadata Analysis',
        icon: <ScanOutlined />
      },
      TRADITIONAL: { 
        color: 'purple', 
        text: 'Traditional Forensics',
        icon: <ExperimentOutlined />
      },
      AI: { 
        color: 'green', 
        text: 'AI Detection',
        icon: <RobotOutlined />
      },
    };
    
    return typeConfig[type as keyof typeof typeConfig] || 
           { color: 'default', text: type, icon: <InfoCircleOutlined /> };
  };

  const getAnalysisTypeTag = (type: string) => {
    const info = getAnalysisTypeInfo(type);
    return (
      <Tag color={info.color} icon={info.icon}>
        {info.text}
      </Tag>
    );
  };

  const getRiskLevelTag = (riskScore?: number) => {
    if (riskScore === undefined || riskScore === null) {
      return <Tag>N/A</Tag>;
    }
    
    if (riskScore <= 30) {
      return <Tag color="green">Low Risk</Tag>;
    } else if (riskScore <= 70) {
      return <Tag color="orange">Medium Risk</Tag>;
    } else {
      return <Tag color="red">High Risk</Tag>;
    }
  };

  const handleViewDetails = (record: AnalysisRecord) => {
    setSelectedAnalysis(record);
    setDetailModalVisible(true);
  };

  const handleDeleteAnalysis = async (analysisId: string) => {
    const [type, id] = analysisId.split('-');
    
    if (type === 'metadata') {
      await deleteMetadataAnalysis(id);
      message.success('Analysis deleted successfully');
    } else {
      message.info('Delete functionality not implemented for this analysis type');
    }
  };

  const handleStartAnalysis = (type: string) => {
    if (!file?.md5Hash) {
      message.error('No file selected');
      return;
    }

    if (type === 'METADATA') {
      startMetadataAnalysis(file.md5Hash);
    } else {
      message.info(`${type} analysis will be triggered automatically when available`);
    }
  };

  // Table columns configuration
  const columns: ColumnsType<AnalysisRecord> = [
    {
      title: 'Analysis Type',
      dataIndex: 'type',
      key: 'type',
      render: (type: string) => getAnalysisTypeTag(type),
      filters: [
        { text: 'Metadata Analysis', value: 'METADATA' },
        { text: 'Traditional Forensics', value: 'TRADITIONAL' },
        { text: 'AI Detection', value: 'AI' },
      ],
      onFilter: (value, record) => record.type === value,
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
      dataIndex: 'riskScore',
      key: 'riskScore',
      render: (score: number) => (
        <Space>
          <Text>{score !== undefined ? `${Math.round(score)}%` : 'N/A'}</Text>
          {getRiskLevelTag(score)}
        </Space>
      ),
      sorter: (a, b) => (a.riskScore || 0) - (b.riskScore || 0),
    },
    {
      title: 'Created Time',
      dataIndex: 'createdTime',
      key: 'createdTime',
      render: (time: string) => formatDateTime(time),
      sorter: (a, b) => new Date(a.createdTime).getTime() - new Date(b.createdTime).getTime(),
    },
    {
      title: 'Actions',
      key: 'actions',
      render: (_, record: AnalysisRecord) => (
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
          {record.type === 'METADATA' && (
            <Button
              type="text"
              size="small"
              icon={<DeleteOutlined />}
              danger
              onClick={() => handleDeleteAnalysis(record.id)}
            >
              Delete
            </Button>
          )}
        </Space>
      ),
    },
  ];

  // Render traditional analysis details
  const renderTraditionalAnalysisDetails = (analysis: TraditionalAnalysisResult) => {
    return (
      <Tabs defaultActiveKey="summary">
        <TabPane tab="Analysis Summary" key="summary">
          <Row gutter={[16, 16]}>
            <Col span={6}>
              <Statistic
                title="Overall Confidence Score"
                value={analysis.overallConfidenceScore}
                suffix="%"
                valueStyle={{ 
                  color: analysis.overallConfidenceScore > 50 ? '#cf1322' : '#3f8600'
                }}
              />
            </Col>
            <Col span={6}>
              <Statistic
                title="Authenticity Assessment"
                value={analysis.authenticityAssessment.replace('_', ' ')}
                valueStyle={{
                  color: ['MANIPULATED', 'LIKELY_MANIPULATED'].includes(analysis.authenticityAssessment) ? '#cf1322' : '#3f8600'
                }}
              />
            </Col>
            <Col span={6}>
              <Statistic
                title="Processing Time"
                value={analysis.processingTimeMs}
                suffix="ms"
              />
            </Col>
            <Col span={6}>
              <Statistic
                title="Image Dimensions"
                value={`${analysis.imageWidth}×${analysis.imageHeight}`}
              />
            </Col>
          </Row>
          
          <Divider />
          
          <div style={{ marginBottom: 16 }}>
            <Title level={5}>Analysis Summary</Title>
            <Text style={{ whiteSpace: 'pre-line' }}>{analysis.analysisSummary}</Text>
          </div>
          
          <div>
            <Title level={5}>Detailed Findings</Title>
            <Text style={{ whiteSpace: 'pre-line' }}>{analysis.detailedFindings}</Text>
          </div>
        </TabPane>

        {analysis.elaAnalysis && (
          <TabPane tab="Error Level Analysis" key="ela">
            <Row gutter={[16, 16]}>
              <Col span={12}>
                <Card title="ELA Results" size="small">
                  <Statistic
                    title="Confidence Score"
                    value={analysis.elaAnalysis.confidenceScore}
                    suffix="%"
                    style={{ marginBottom: 16 }}
                  />
                  <Statistic
                    title="Suspicious Regions"
                    value={analysis.elaAnalysis.suspiciousRegions}
                    style={{ marginBottom: 16 }}
                  />
                  <Text>{analysis.elaAnalysis.analysisNotes}</Text>
                </Card>
              </Col>
              {analysis.elaAnalysis.resultImageUrl && (
                <Col span={12}>
                  <Card title="ELA Visualization" size="small">
                    <Image
                      src={analysis.elaAnalysis.resultImageUrl}
                      alt="ELA Result"
                      style={{ maxWidth: '100%' }}
                    />
                  </Card>
                </Col>
              )}
            </Row>
          </TabPane>
        )}

        {analysis.cfaAnalysis && (
          <TabPane tab="CFA Analysis" key="cfa">
            <Row gutter={[16, 16]}>
              <Col span={12}>
                <Card title="CFA Results" size="small">
                  <Statistic
                    title="Confidence Score"
                    value={analysis.cfaAnalysis.confidenceScore}
                    suffix="%"
                    style={{ marginBottom: 16 }}
                  />
                  <Statistic
                    title="Interpolation Anomalies"
                    value={analysis.cfaAnalysis.interpolationAnomalies}
                    style={{ marginBottom: 16 }}
                  />
                  <Text>{analysis.cfaAnalysis.analysisNotes}</Text>
                </Card>
              </Col>
              {analysis.cfaAnalysis.heatmapImageUrl && (
                <Col span={12}>
                  <Card title="CFA Heatmap" size="small">
                    <Image
                      src={analysis.cfaAnalysis.heatmapImageUrl}
                      alt="CFA Heatmap"
                      style={{ maxWidth: '100%' }}
                    />
                  </Card>
                </Col>
              )}
            </Row>
          </TabPane>
        )}

        {analysis.copyMoveAnalysis && (
          <TabPane tab="Copy-Move Detection" key="copymove">
            <Row gutter={[16, 16]}>
              <Col span={12}>
                <Card title="Copy-Move Results" size="small">
                  <Statistic
                    title="Confidence Score"
                    value={analysis.copyMoveAnalysis.confidenceScore}
                    suffix="%"
                    style={{ marginBottom: 16 }}
                  />
                  <Statistic
                    title="Suspicious Blocks"
                    value={analysis.copyMoveAnalysis.suspiciousBlocks}
                    style={{ marginBottom: 16 }}
                  />
                  <Text>{analysis.copyMoveAnalysis.analysisNotes}</Text>
                </Card>
              </Col>
              {analysis.copyMoveAnalysis.resultImageUrl && (
                <Col span={12}>
                  <Card title="Copy-Move Visualization" size="small">
                    <Image
                      src={analysis.copyMoveAnalysis.resultImageUrl}
                      alt="Copy-Move Result"
                      style={{ maxWidth: '100%' }}
                    />
                  </Card>
                </Col>
              )}
            </Row>
          </TabPane>
        )}

        {analysis.lightingAnalysis && (
          <TabPane tab="Lighting Analysis" key="lighting">
            <Row gutter={[16, 16]}>
              <Col span={12}>
                <Card title="Lighting Results" size="small">
                  <Statistic
                    title="Confidence Score"
                    value={analysis.lightingAnalysis.confidenceScore}
                    suffix="%"
                    style={{ marginBottom: 16 }}
                  />
                  <Statistic
                    title="Lighting Inconsistencies"
                    value={analysis.lightingAnalysis.lightingInconsistencies}
                    style={{ marginBottom: 16 }}
                  />
                  <Text>{analysis.lightingAnalysis.analysisNotes}</Text>
                </Card>
              </Col>
              {analysis.lightingAnalysis.analysisImageUrl && (
                <Col span={12}>
                  <Card title="Lighting Analysis" size="small">
                    <Image
                      src={analysis.lightingAnalysis.analysisImageUrl}
                      alt="Lighting Analysis"
                      style={{ maxWidth: '100%' }}
                    />
                  </Card>
                </Col>
              )}
            </Row>
          </TabPane>
        )}
      </Tabs>
    );
  };

  // Render metadata analysis details (complete implementation from original)
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

  const renderMetadataAnalysisDetails = (analysis: MetadataAnalysis) => {
    if (!analysis.result) {
      return <div>No analysis result available</div>;
    }

    const result = analysis.result;
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

    // Parsed raw metadata tree
    if ((result as any).parsedMetadata && Object.keys((result as any).parsedMetadata).length > 0) {
      panels.push(
        <Panel header="Full Analysis" key="parsed">
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
            <BarChartOutlined />
            {/*<Title level={4} style={{ margin: 0 }}>*/}
            {/*  Forensic Analysis Results*/}
            {/*</Title>*/}
          </Space>
        }
        extra={
          file && (
            <Space>
              <Button 
                type="primary"
                onClick={() => handleStartAnalysis('METADATA')}
                loading={metadataLoading}
              >
                Start Analyze
              </Button>
            </Space>
          )
        }
      >
        {showFileInfo && file && (
          <Alert
            message={`Analysis for: ${file.filename}`}
            description={`File Size: ${(file.fileSize / 1024 / 1024).toFixed(2)} MB | MD5: ${file.md5Hash}`}
            type="info"
            style={{ marginBottom: 16 }}
          />
        )}

        <Table
          columns={columns}
          dataSource={allAnalyses}
          rowKey="id"
          loading={loading || metadataLoading}
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
          selectedAnalysis ? (
            <Space>
              {getAnalysisTypeInfo(selectedAnalysis.type).icon}
              <Text>Results - {file?.filename}</Text>
            </Space>
          ) : (
            <Text>Analysis Results</Text>
          )
        }
        open={detailModalVisible}
        onCancel={() => setDetailModalVisible(false)}
        footer={null}
        width={1200}
        centered
      >
        {selectedAnalysis && (
          <div>
            <Descriptions bordered size="small" style={{ marginBottom: 16 }}>
              <Descriptions.Item label="Analysis Type" span={2}>
                {getAnalysisTypeTag(selectedAnalysis.type)}
              </Descriptions.Item>
              <Descriptions.Item label="Status">
                {getStatusTag(selectedAnalysis.status)}
              </Descriptions.Item>
              <Descriptions.Item label="Risk Score" span={3}>
                <Space>
                  <Text>{selectedAnalysis.riskScore !== undefined ? `${Math.round(selectedAnalysis.riskScore)}%` : 'N/A'}</Text>
                  {getRiskLevelTag(selectedAnalysis.riskScore)}
                </Space>
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

            {selectedAnalysis.type === 'TRADITIONAL' && selectedAnalysis.data && (
              renderTraditionalAnalysisDetails(selectedAnalysis.data as TraditionalAnalysisResult)
            )}
            
            {selectedAnalysis.type === 'METADATA' && selectedAnalysis.data && (
              renderMetadataAnalysisDetails(selectedAnalysis.data as MetadataAnalysis)
            )}
            
            {selectedAnalysis.type === 'AI' && (
              <Alert
                message="AI Analysis"
                description="AI-based deepfake detection is not yet implemented."
                type="info"
              />
            )}
          </div>
        )}
      </Modal>
    </div>
  );
};

export default AnalysisOverview;
