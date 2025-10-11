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
  Tooltip,
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
  RedoOutlined,
  DashboardOutlined,
  PictureOutlined,
  CodeOutlined,
} from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import { useMetadataAnalysis } from '../hooks';
import { traditionalAnalysisAPI } from '../services/traditional';
import { analysisTaskApi } from '../services/project';
import { videoTraditionalAPI, type VideoTraditionalSubResult } from '../services/videoTraditional';
import { formatDateTime } from '../utils';
import type { 
  MetadataAnalysis, 
  UploadFile, 
  TraditionalAnalysisResult,
  AnalysisTask
} from '../types';

const { Text, Title } = Typography;
const { TabPane } = Tabs;
const { Panel } = Collapse;

export interface AnalysisOverviewProps {
  file?: UploadFile;
  showFileInfo?: boolean;
  // When provided, clicking "Details" will call this instead of opening internal modal
  onSelectAnalysis?: (record: AnalysisRecord) => void;
}

export interface AnalysisRecord {
  id: string;
  type: 'METADATA' | 'TRADITIONAL' | 'VIDEO_TRADITIONAL' | 'AI';
  status: 'PENDING' | 'PROCESSING' | 'IN_PROGRESS' | 'COMPLETED' | 'FAILED' | 'NOT_STARTED' | 'NOT_AVAILABLE';
  riskScore?: number;
  createdTime: string;
  completedTime?: string;
  errorMessage?: string;
  data?: MetadataAnalysis | TraditionalAnalysisResult | any;
}

// ---------- Helper functions (module scope) ----------
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
    case 'NOT_STARTED':
      return <InfoCircleOutlined style={{ color: '#d9d9d9' }} />;
    case 'NOT_AVAILABLE':
      return <InfoCircleOutlined style={{ color: '#bfbfbf' }} />;
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
    NOT_STARTED: { color: 'default', text: 'Not Started' },
    NOT_AVAILABLE: { color: 'default', text: 'Not Available' },
  } as const;
  const config = (statusConfig as any)[status] || { color: 'default', text: status };
  return <Tag color={config.color}>{config.text}</Tag>;
};

const getAnalysisTypeInfo = (type: string) => {
  const typeConfig = {
    METADATA: { color: 'blue', text: 'Metadata Analysis', icon: <ScanOutlined /> },
    TRADITIONAL: { color: 'purple', text: 'Photo Traditional', icon: <ExperimentOutlined /> },
    VIDEO_TRADITIONAL: { color: 'geekblue', text: 'Video Traditional', icon: <ExperimentOutlined /> },
    AI: { color: 'green', text: 'AI Detection', icon: <RobotOutlined /> },
  } as const;
  return (typeConfig as any)[type] || { color: 'default', text: type, icon: <InfoCircleOutlined /> };
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
  if (riskScore === undefined || riskScore === null) return <Tag>N/A</Tag>;
  if (riskScore <= 30) return <Tag color="green">Low Risk</Tag>;
  if (riskScore <= 70) return <Tag color="orange">Medium Risk</Tag>;
  return <Tag color="red">High Risk</Tag>;
};

// Reusable renderers for details
const renderTraditionalAnalysisDetails = (analysis: TraditionalAnalysisResult) => {
  return (
  <Tabs defaultActiveKey="summary" destroyInactiveTabPane>
      <TabPane tab="Analysis Summary" key="summary">
        <Row gutter={[16, 16]}>
          <Col span={6}>
            <Statistic title="Overall Confidence Score" value={analysis.overallConfidenceScore} suffix="%" valueStyle={{ color: analysis.overallConfidenceScore > 50 ? '#cf1322' : '#3f8600' }} />
          </Col>
          <Col span={6}>
            <Statistic title="Authenticity Assessment" value={analysis.authenticityAssessment.replace('_', ' ')} valueStyle={{ color: ['MANIPULATED', 'LIKELY_MANIPULATED'].includes(analysis.authenticityAssessment) ? '#cf1322' : '#3f8600' }} />
          </Col>
          <Col span={6}>
            <Statistic title="Processing Time" value={analysis.processingTimeMs} suffix="ms" />
          </Col>
          <Col span={6}>
            <Statistic title="Image Dimensions" value={`${analysis.imageWidth}×${analysis.imageHeight}`} />
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
          <Row gutter={[16, 16]} wrap>
            <Col xs={24} lg={12}>
              <Card title="ELA Results" size="small">
                <Statistic title="Confidence Score" value={analysis.elaAnalysis.confidenceScore} suffix="%" style={{ marginBottom: 16 }} />
                <Statistic title="Suspicious Regions" value={analysis.elaAnalysis.suspiciousRegions} style={{ marginBottom: 16 }} />
                <Text>{analysis.elaAnalysis.analysis}</Text>
              </Card>
            </Col>
            {analysis.elaAnalysis.resultImageUrl && (
              <Col xs={24} lg={12}>
                <Card title="ELA Visualization" size="small" bodyStyle={{ overflow: 'hidden' }}>
                  <Image src={analysis.elaAnalysis.resultImageUrl} alt="ELA Result" style={{ width: '100%' }} />
                </Card>
              </Col>
            )}
          </Row>
        </TabPane>
      )}
      {analysis.cfaAnalysis && (
        <TabPane tab="CFA Analysis" key="cfa">
          <Row gutter={[16, 16]} wrap>
            <Col xs={24} lg={12}>
              <Card title="CFA Results" size="small">
                <Statistic title="Confidence Score" value={analysis.cfaAnalysis.confidenceScore} suffix="%" style={{ marginBottom: 16 }} />
                <Statistic title="Interpolation Anomalies" value={analysis.cfaAnalysis.interpolationAnomalies} style={{ marginBottom: 16 }} />
                <Text>{analysis.cfaAnalysis.analysis}</Text>
              </Card>
            </Col>
            {analysis.cfaAnalysis.heatmapImageUrl && (
              <Col xs={24} lg={12}>
                <Card title="CFA Heatmap" size="small" bodyStyle={{ overflow: 'hidden' }}>
                  <Image src={analysis.cfaAnalysis.heatmapImageUrl} alt="CFA Heatmap" style={{ width: '100%' }} />
                </Card>
              </Col>
            )}
          </Row>
        </TabPane>
      )}
      {analysis.copyMoveAnalysis && (
        <TabPane tab="Copy-Move Detection" key="copymove">
          <Row gutter={[16, 16]} wrap>
            <Col xs={24} lg={12}>
              <Card title="Copy-Move Results" size="small">
                <Statistic title="Confidence Score" value={analysis.copyMoveAnalysis.confidenceScore} suffix="%" style={{ marginBottom: 16 }} />
                <Statistic title="Suspicious Blocks" value={analysis.copyMoveAnalysis.suspiciousBlocks} style={{ marginBottom: 16 }} />
                <Text>{analysis.copyMoveAnalysis.analysis}</Text>
              </Card>
            </Col>
            {analysis.copyMoveAnalysis.resultImageUrl && (
              <Col xs={24} lg={12}>
                <Card title="Copy-Move Visualization" size="small" bodyStyle={{ overflow: 'hidden' }}>
                  <Image src={analysis.copyMoveAnalysis.resultImageUrl} alt="Copy-Move Result" style={{ width: '100%' }} />
                </Card>
              </Col>
            )}
          </Row>
        </TabPane>
      )}
      {analysis.lightingAnalysis && (
        <TabPane tab="Lighting Analysis" key="lighting">
          <Row gutter={[16, 16]} wrap>
            <Col xs={24} lg={12}>
              <Card title="Lighting Results" size="small">
                <Statistic title="Confidence Score" value={analysis.lightingAnalysis.confidenceScore} suffix="%" style={{ marginBottom: 16 }} />
                <Statistic title="Inconsistencies" value={analysis.lightingAnalysis.inconsistencies} style={{ marginBottom: 16 }} />
                <Text>{analysis.lightingAnalysis.analysis}</Text>
              </Card>
            </Col>
            {analysis.lightingAnalysis.analysisImageUrl && (
              <Col xs={24} lg={12}>
                <Card title="Lighting Analysis" size="small" bodyStyle={{ overflow: 'hidden' }}>
                  <Image src={analysis.lightingAnalysis.analysisImageUrl} alt="Lighting Analysis" style={{ width: '100%' }} />
                </Card>
              </Col>
            )}
          </Row>
        </TabPane>
      )}
      {analysis.noiseAnalysis && (
        <TabPane tab="Noise Residual" key="noise">
          <Row gutter={[16, 16]} wrap>
            <Col xs={24} lg={12}>
              <Card title="Noise Residual Results" size="small">
                <Statistic title="Confidence Score" value={analysis.noiseAnalysis.confidenceScore} suffix="%" style={{ marginBottom: 16 }} />
                <Statistic title="Suspicious Regions" value={analysis.noiseAnalysis.suspiciousRegions} style={{ marginBottom: 16 }} />
                <Text>{analysis.noiseAnalysis.analysis}</Text>
              </Card>
            </Col>
            {analysis.noiseAnalysis.resultImageUrl && (
              <Col xs={24} lg={12}>
                <Card title="Noise Residual Visualization" size="small" bodyStyle={{ overflow: 'hidden' }}>
                  <Image src={analysis.noiseAnalysis.resultImageUrl} alt="Noise Residual" style={{ width: '100%' }} />
                </Card>
              </Col>
            )}
          </Row>
        </TabPane>
      )}
    </Tabs>
  );
};

const prettyMethod = (m?: string) => {
  if (!m) return '';
  const map: any = { NOISE: 'Noise Pattern', FLOW: 'Optical Flow', FREQ: 'Frequency Domain', FREQUENCY: 'Frequency Domain', TEMPORAL: 'Temporal Inconsistency', COPYMOVE: 'Copy-Move' };
  return map[m] || m;
};

// Render video traditional analysis details
const renderVideoTraditionalAnalysisDetails = (data: any) => {
  const subtasks = data?.subtasks || [];
  if (subtasks.length === 0) {
    return <Alert message="No video traditional analysis results available" type="info" />;
  }

  // Helper: Render key metrics with smart formatting
  const renderMetricValue = (key: string, value: any): React.ReactNode => {
    // Boolean flags with semantic tags
    if (typeof value === 'boolean' || key.includes('detected') || key.includes('manipulated')) {
      const boolVal = typeof value === 'boolean' ? value : (value === 'true' || value === true);
      return <Tag color={boolVal ? 'error' : 'success'} icon={boolVal ? <ExclamationCircleOutlined /> : <CheckCircleOutlined />}>
        {boolVal ? 'Detected' : 'Clean'}
      </Tag>;
    }
    
    // Numeric scores/ratios with precision
    if (typeof value === 'number') {
      if (key.includes('score') || key.includes('ratio') || key.includes('consistency')) {
        return <Text strong style={{ color: value > 0.5 ? '#ff4d4f' : '#52c41a' }}>{value.toFixed(6)}</Text>;
      }
      return <Text code>{value.toFixed(6)}</Text>;
    }
    
    // File paths - show basename
    if (typeof value === 'string' && (key.includes('path') || key.includes('file'))) {
      const basename = value.split(/[/\\]/).pop() || value;
      return <Tooltip title={value}><Text code style={{ fontSize: 11 }}>{basename}</Text></Tooltip>;
    }
    
    return <Text>{String(value)}</Text>;
  };

  // Helper: Render complex nested object (like frame_statistics)
  const renderNestedObject = (obj: any, title: string) => {
    if (!obj || typeof obj !== 'object') return null;
    
    return (
      <Collapse 
        ghost 
        size="small"
        items={[{
          key: '1',
          label: <Text strong>{title}</Text>,
          children: (
            <Descriptions bordered size="small" column={2}>
              {Object.entries(obj).map(([k, v]: [string, any]) => (
                <Descriptions.Item label={k} key={k}>
                  {typeof v === 'object' ? (
                    <Text code style={{ fontSize: 11, wordBreak: 'break-all' }}>
                      {JSON.stringify(v).substring(0, 80)}...
                    </Text>
                  ) : (
                    renderMetricValue(k, v)
                  )}
                </Descriptions.Item>
              ))}
            </Descriptions>
          )
        }]}
      />
    );
  };

  return (
    <Tabs defaultActiveKey="0" type="card">
      {subtasks.map((st: any, idx: number) => {
        const simpleMetrics: [string, any][] = [];
        const complexMetrics: [string, any][] = [];
        
        // Categorize metrics
        if (st.result && typeof st.result === 'object') {
          Object.entries(st.result).forEach(([key, value]) => {
            if (typeof value === 'object' && value !== null && !Array.isArray(value)) {
              complexMetrics.push([key, value]);
            } else {
              simpleMetrics.push([key, value]);
            }
          });
        }

        return (
          <TabPane 
            tab={
              <Space>
                <Tag color="geekblue">{prettyMethod(st.method)}</Tag>
                {st.status === 'FAILED' && <ExclamationCircleOutlined style={{ color: '#ff4d4f' }} />}
              </Space>
            } 
            key={String(idx)}
          >
            {st.status === 'FAILED' && st.errorMessage && (
              <Alert 
                message="Analysis Failed" 
                description={st.errorMessage} 
                type="error" 
                showIcon 
                style={{ marginBottom: 16 }} 
              />
            )}

            {/* Key Metrics */}
            {simpleMetrics.length > 0 && (
              <div style={{ marginBottom: 16 }}>
                <Title level={5}><DashboardOutlined /> Key Metrics</Title>
                <Descriptions bordered size="small" column={2}>
                  {simpleMetrics.map(([key, value]) => {
                    // Skip array display in simple metrics
                    if (Array.isArray(value)) {
                      return (
                        <Descriptions.Item label={key} span={2} key={key}>
                          <Text type="secondary" style={{ fontSize: 11 }}>
                            Array ({value.length} items) - See raw data below
                          </Text>
                        </Descriptions.Item>
                      );
                    }
                    
                    return (
                      <Descriptions.Item label={key} key={key}>
                        {renderMetricValue(key, value)}
                      </Descriptions.Item>
                    );
                  })}
                </Descriptions>
              </div>
            )}

            {/* Complex Nested Metrics (Collapsible) */}
            {complexMetrics.length > 0 && (
              <div style={{ marginBottom: 16 }}>
                <Title level={5}><BarChartOutlined /> Detailed Statistics</Title>
                {complexMetrics.map(([key, value]) => (
                  <div key={key} style={{ marginBottom: 8 }}>
                    {renderNestedObject(value, key.replace(/_/g, ' ').toUpperCase())}
                  </div>
                ))}
              </div>
            )}

            {/* Visual Artifacts */}
            {st.artifacts && Object.keys(st.artifacts).length > 0 && (
              <div style={{ marginBottom: 16 }}>
                <Title level={5}><PictureOutlined /> Visual Artifacts</Title>
                <Row gutter={[16, 16]}>
                  {Object.entries(st.artifacts).map(([name, url]: [string, any]) => (
                    <Col xs={24} sm={12} md={8} key={name}>
                      <Card
                        size="small"
                        title={<Text strong style={{ fontSize: 12 }}>{name.replace(/_/g, ' ').toUpperCase()}</Text>}
                        hoverable
                        cover={
                          <Image
                            src={String(url)}
                            alt={name}
                            style={{ 
                              width: '100%', 
                              height: 250, 
                              objectFit: 'contain',
                              backgroundColor: '#f5f5f5'
                            }}
                            fallback="data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg=="
                          />
                        }
                      >
                        <Card.Meta 
                          description={
                            <a href={String(url)} target="_blank" rel="noopener noreferrer" style={{ fontSize: 11 }}>
                              View Full Size
                            </a>
                          } 
                        />
                      </Card>
                    </Col>
                  ))}
                </Row>
              </div>
            )}

            {/* Raw Data (Collapsible) */}
            {st.result && (
              <Collapse 
                ghost 
                size="small"
                items={[{
                  key: 'raw',
                  label: <Text type="secondary"><CodeOutlined /> Raw Analysis Data (JSON)</Text>,
                  children: (
                    <pre style={{ 
                      backgroundColor: '#f5f5f5', 
                      padding: 12, 
                      borderRadius: 4,
                      maxHeight: 400,
                      overflow: 'auto',
                      fontSize: 11,
                      lineHeight: 1.4
                    }}>
                      {JSON.stringify(st.result, null, 2)}
                    </pre>
                  )
                }]}
              />
            )}

            {/* Fallback */}
            {!st.result && (!st.artifacts || Object.keys(st.artifacts).length === 0) && (
              <Alert 
                message="No results available" 
                description="This analysis did not produce any outputs." 
                type="info" 
              />
            )}
          </TabPane>
        );
      })}
    </Tabs>
  );
};

const renderMetadataTree = (data: Record<string, any>, prefix = ''): any[] => {
  return Object.entries(data).map(([key, value]) => {
    const nodeKey = prefix ? `${prefix}.${key}` : key;
    if (typeof value === 'object' && value !== null && !Array.isArray(value)) {
      return { title: <Text strong>{key}</Text>, key: nodeKey, children: renderMetadataTree(value, nodeKey) };
    } else {
      return { title: (<Space><Text>{key}:</Text><Text copyable type="secondary">{Array.isArray(value) ? value.join(', ') : String(value)}</Text></Space>), key: nodeKey, isLeaf: true };
    }
  });
};

const renderMetadataAnalysisDetails = (analysis: MetadataAnalysis) => {
  if (!analysis.result) return <div>No analysis result available</div>;
  const result = analysis.result;
  const panels: React.ReactNode[] = [];
  
  // EXIF Data Panel
  if (result.exifData) {
    panels.push(
      <Panel header="EXIF Data" key="exif">
        <Tree defaultExpandAll treeData={renderMetadataTree(result.exifData)} />
      </Panel>
    );
  }

  // File Headers Panel
  if (result.fileHeaders) {
    panels.push(
      <Panel header="File Headers" key="headers">
        <Tree defaultExpandAll treeData={renderMetadataTree(result.fileHeaders)} />
      </Panel>
    );
  }

  // Hash Data Panel
  if (result.hashData) {
    panels.push(
      <Panel header="Hash Data" key="hash">
        <Descriptions bordered size="small">
          {result.hashData.md5 && (
            <Descriptions.Item label="MD5" span={3}><Text copyable code>{result.hashData.md5}</Text></Descriptions.Item>
          )}
          {result.hashData.sha256 && (
            <Descriptions.Item label="SHA256" span={3}><Text copyable code>{result.hashData.sha256}</Text></Descriptions.Item>
          )}
        </Descriptions>
      </Panel>
    );
  }

  // Technical Data Panel
  if (result.technicalData) {
    panels.push(
      <Panel header="Technical Data" key="technical">
        <Descriptions bordered size="small">
          {Object.entries(result.technicalData).map(([key, value]) => (
            <Descriptions.Item label={key} key={key}>{String(value)}</Descriptions.Item>
          ))}
        </Descriptions>
      </Panel>
    );
  }

  // NEW: File Header Analysis Panel (Week 7 requirement)
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
            <Text>File Header Signature Analysis</Text>
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
          <Descriptions.Item label="Detected Format" span={2}>
            <Text code>{headerData.detectedFormat || 'Unknown'}</Text>
          </Descriptions.Item>
          <Descriptions.Item label="Expected Format">
            <Text code>{headerData.expectedFormat || 'Unknown'}</Text>
          </Descriptions.Item>
          
          <Descriptions.Item label="Format Match" span={2}>
            {headerData.formatMatch ? (
              <Tag color="green">Match</Tag>
            ) : (
              <Tag color="red">Mismatch</Tag>
            )}
          </Descriptions.Item>
          <Descriptions.Item label="Integrity Status">
            <Tag color={
              headerData.integrityStatus === 'INTACT' ? 'green' :
              headerData.integrityStatus === 'FORMAT_MISMATCH' ? 'red' :
              headerData.integrityStatus === 'UNKNOWN_FORMAT' ? 'orange' : 'default'
            }>
              {headerData.integrityStatus}
            </Tag>
          </Descriptions.Item>
          
          {headerData.signatureHex && (
            <Descriptions.Item label="File Signature" span={3}>
              <Text copyable code style={{ fontSize: '12px' }}>
                {headerData.signatureHex}
              </Text>
            </Descriptions.Item>
          )}
        </Descriptions>
      </Panel>
    );
  }

  // NEW: Container Analysis Panel
  if (result.containerAnalysis && Object.keys(result.containerAnalysis).length > 0) {
    panels.push(
      <Panel 
        header={
          <Space>
            <InfoCircleOutlined />
            <Text>Container Integrity Analysis</Text>
            {result.containerAnalysis.status === 'PENDING_IMPLEMENTATION' && (
              <Tag color="orange">In Development</Tag>
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
          <Descriptions.Item label="Integrity Verification" span={2}>
            {result.containerAnalysis.integrityVerified !== undefined ? (
              <Tag color={result.containerAnalysis.integrityVerified ? 'green' : 'red'}>
                {result.containerAnalysis.integrityVerified ? 'Verified' : 'Not Verified'}
              </Tag>
            ) : (
              <Tag color="default">Not Checked</Tag>
            )}
          </Descriptions.Item>
          <Descriptions.Item label="Analysis Status">
            <Tag color={result.containerAnalysis.status === 'PENDING_IMPLEMENTATION' ? 'orange' : 'blue'}>
              {result.containerAnalysis.status || 'UNKNOWN'}
            </Tag>
          </Descriptions.Item>
          
          {result.containerAnalysis.analysisResults && (
            <Descriptions.Item label="Analysis Results" span={3}>
              <Text type="secondary" style={{ fontSize: '12px', whiteSpace: 'pre-wrap' }}>
                {result.containerAnalysis.analysisResults}
              </Text>
            </Descriptions.Item>
          )}
        </Descriptions>
      </Panel>
    );
  }

  // NEW: Analysis Notes Panel (dedicated display)
  if (result.suspicious?.analysisNotes) {
    panels.push(
      <Panel header="Detailed Analysis Notes" key="analysisNotes">
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

  // NEW: Raw Metadata Panel (complete technical data)
  if (result.rawMetadata) {
    panels.push(
      <Panel 
        header={
          <Space>
            <BarChartOutlined />
            <Text>Raw Metadata</Text>
            <Tag color="blue">Complete Technical Data</Tag>
          </Space>
        } 
        key="rawMetadata"
      >
        <div style={{ marginBottom: 16 }}>
          <Text type="secondary">
            The following is the complete raw metadata extracted from the file, containing all technical details. This data is very important for forensic analysis.
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
            Download Raw Data
          </Button>
        </div>
      </Panel>
    );
  }

  // Parsed Metadata Tree Panel
  if ((result as any).parsedMetadata && Object.keys((result as any).parsedMetadata).length > 0) {
    panels.push(
      <Panel header="Structured Analysis Data" key="parsed">
        <div style={{ marginBottom: 16 }}>
          <Text type="secondary">
            Parsed structured metadata for easy viewing and analysis of technical parameters.
          </Text>
        </div>
        <Tree defaultExpandAll treeData={renderMetadataTree((result as any).parsedMetadata)} />
      </Panel>
    );
  }

  // Suspicious Analysis Panel (enhanced)
  if (result.suspicious) {
    panels.push(
      <Panel key="suspicious" header={(<Space><WarningOutlined /><Text>Suspicious Analysis</Text>{getRiskLevelTag(result.suspicious.riskScore)}</Space>)}>
        <Row gutter={16}>
          <Col span={8}><Statistic title="Risk Score" value={result.suspicious.riskScore} suffix="%" valueStyle={{ color: result.suspicious.riskScore <= 30 ? '#3f8600' : '#cf1322' }} /></Col>
          <Col span={8}><Statistic title="Has Anomalies" value={result.suspicious.hasAnomalies ? 'Yes' : 'No'} valueStyle={{ color: result.suspicious.hasAnomalies ? '#cf1322' : '#3f8600' }} /></Col>
          <Col span={8}><Statistic title="Anomalies Found" value={result.suspicious.anomalies.length} /></Col>
        </Row>
        
        {result.suspicious.assessmentConclusion && (
          <div style={{ marginTop: 16 }}>
            <Title level={5}>Forensic Assessment Conclusion:</Title>
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
            <Title level={5}>Detected Anomalies:</Title>
            <Timeline>
              {result.suspicious.anomalies.map((anomaly, index) => (
                <Timeline.Item key={index} dot={<WarningOutlined style={{ color: '#ff4d4f' }} />}>
                  <Text>{anomaly}</Text>
                </Timeline.Item>
              ))}
            </Timeline>
          </div>
        )}
      </Panel>
    );
  }
  
  return <Collapse defaultActiveKey={['suspicious', 'fileHeader', 'analysisNotes']} ghost size="small">{panels}</Collapse>;
};

export const AnalysisDetails: React.FC<{ file?: UploadFile; record: AnalysisRecord }> = ({ record }) => {
  return (
    <div>
      <Descriptions bordered size="small" style={{ marginBottom: 16 }}>
        <Descriptions.Item label="Analysis Type" span={2}>{getAnalysisTypeTag(record.type)}</Descriptions.Item>
        <Descriptions.Item label="Status">{getStatusTag(record.status)}</Descriptions.Item>
        <Descriptions.Item label="Risk Score" span={3}><Space><Text>{record.riskScore !== undefined ? `${Math.round(record.riskScore)}%` : 'N/A'}</Text>{getRiskLevelTag(record.riskScore)}</Space></Descriptions.Item>
        <Descriptions.Item label="Created Time" span={3}>{formatDateTime(record.createdTime)}</Descriptions.Item>
        {record.completedTime && (<Descriptions.Item label="Completed Time" span={3}>{formatDateTime(record.completedTime)}</Descriptions.Item>)}
        {record.errorMessage && (<Descriptions.Item label="Error Message" span={3}><Text type="danger">{record.errorMessage}</Text></Descriptions.Item>)}
      </Descriptions>
      {record.type === 'TRADITIONAL' && record.data && renderTraditionalAnalysisDetails(record.data as TraditionalAnalysisResult)}
      {record.type === 'METADATA' && record.data && renderMetadataAnalysisDetails(record.data as MetadataAnalysis)}
      {record.type === 'VIDEO_TRADITIONAL' && record.data && renderVideoTraditionalAnalysisDetails(record.data)}
      {record.type === 'AI' && (
        <Alert message="AI Analysis" description="AI-based deepfake detection is not yet implemented." type="info" />
      )}
    </div>
  );
};

const AnalysisOverview: React.FC<AnalysisOverviewProps> = ({
  file,
  showFileInfo = true,
  onSelectAnalysis,
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

  // State for traditional analysis
  const [traditionalAnalysis, setTraditionalAnalysis] = useState<TraditionalAnalysisResult | null>(null);
  const [traditionalLoading, setTraditionalLoading] = useState(false);
  const [triggeringAnalysis, setTriggeringAnalysis] = useState(false);
  // Video traditional subtasks (from AnalysisTask with JSON results)
  const [videoTradSubs, setVideoTradSubs] = useState<VideoTraditionalSubResult[]>([]);
  const [videoTradLoading, setVideoTradLoading] = useState(false);

  // Load traditional analysis
  const loadTraditionalAnalysis = useCallback(async (fileMd5: string) => {
    setTraditionalLoading(true);
    try {
      const traditionalResult = await traditionalAnalysisAPI.getAnalysisResult(fileMd5);
      setTraditionalAnalysis(traditionalResult);
      return traditionalResult;
    } catch (error) {
      console.error('Error loading traditional analysis:', error);
      setTraditionalAnalysis(null);
      return null;
    } finally {
      setTraditionalLoading(false);
    }
  }, []);

  // Load video traditional analysis results via new endpoint
  const loadVideoTraditional = useCallback(async (file: UploadFile) => {
    setVideoTradLoading(true);
    try {
      if (!file?.md5Hash) { setVideoTradSubs([]); return []; }
      const subs = await videoTraditionalAPI.getResultsByFile(file.md5Hash);
      setVideoTradSubs(subs);
      return subs;
    } catch (e) {
      console.error('Failed to load video traditional results', e);
      setVideoTradSubs([]);
      return [];
    } finally {
      setVideoTradLoading(false);
    }
  }, []);

  // Load all analysis types
  const loadAllAnalyses = useCallback(async (fileMd5?: string) => {
    if (!fileMd5) {
      setAllAnalyses([]);
      setTraditionalAnalysis(null);
      setVideoTradSubs([]);
      return;
    }

    setLoading(true);
    try {
      // Load metadata analyses
      await loadMetadataAnalyses(fileMd5);
      
      // For images only: load photo traditional
      if (file?.fileType?.startsWith('image')) {
        await loadTraditionalAnalysis(fileMd5);
      } else {
        setTraditionalAnalysis(null);
      }

      // Load video-traditional results (if file context available)
      if (file) {
        await loadVideoTraditional(file);
      }

      // TODO: Add AI analysis loading here when implemented
      
    } catch (error) {
      console.error('Error loading analyses:', error);
      message.error('Failed to load analysis results');
    } finally {
      setLoading(false);
    }
  }, [file, loadMetadataAnalyses, loadTraditionalAnalysis, loadVideoTraditional]);

  // Convert all analyses to AnalysisRecord format
  useEffect(() => {
    if (!file?.md5Hash) {
      setAllAnalyses([]);
      return;
    }

    const analyses: AnalysisRecord[] = [];

    // Always show metadata analysis (even if not available)
    if (metadataAnalyses.length > 0) {
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
      analyses.push(...metadataRecords);
    } else {
      // Show metadata analysis as not started
      analyses.push({
        id: 'metadata-placeholder',
        type: 'METADATA' as const,
        status: 'NOT_STARTED',
        riskScore: undefined,
        createdTime: '',
        completedTime: undefined,
        errorMessage: undefined,
        data: undefined
      });
    }

    // Always show traditional analysis (even if not available) for images
    if (traditionalAnalysis) {
      analyses.push({
        id: `traditional-${traditionalAnalysis.id}`,
        type: 'TRADITIONAL',
        status: traditionalAnalysis.analysisStatus,
        riskScore: traditionalAnalysis.overallConfidenceScore,
        createdTime: traditionalAnalysis.createdAt,
        completedTime: traditionalAnalysis.updatedAt,
        errorMessage: traditionalAnalysis.errorMessage,
        data: traditionalAnalysis
      });
    } else {
      // Only show placeholder for images; hide for videos
      if (file?.fileType?.startsWith('image')) {
        analyses.push({
          id: 'traditional-placeholder',
          type: 'TRADITIONAL',
          status: 'NOT_STARTED',
          riskScore: undefined,
          createdTime: '',
          completedTime: undefined,
          errorMessage: undefined,
          data: undefined
        });
      }
    }

    // Video Traditional (from API)
    if (videoTradSubs.length > 0) {
       // Compute aggregate status/time
      const anyFailed = videoTradSubs.some(v => v.success === false);
      const status = anyFailed ? 'FAILED' : 'COMPLETED';
      const createdTimes = videoTradSubs.map(v => v.createdAt).filter(Boolean);
      const completedTimes = videoTradSubs.map(v => v.updatedAt).filter(Boolean);
      analyses.push({
        id: `video-traditional-${file?.md5Hash || 'x'}`,
        type: 'VIDEO_TRADITIONAL',
        status: status as any,
        riskScore: undefined,
        createdTime: createdTimes.sort()[0] || '',
        completedTime: completedTimes.sort().slice(-1)[0] || undefined,
        errorMessage: anyFailed ? 'Some subtasks failed' : undefined,
        data: {
          subtasks: videoTradSubs.map(v => ({
            id: v.id,
            type: `VIDEO_TRADITIONAL_${v.method}`,
            method: v.method,
            artifacts: v.artifacts || {},
            result: v.result || {},
            status: v.success ? 'COMPLETED' : 'FAILED'
          }))
        }
      });
    } else {
      if (file?.fileType?.startsWith('video')) {
        analyses.push({
          id: 'video-traditional-placeholder',
          type: 'VIDEO_TRADITIONAL',
          status: 'NOT_STARTED',
          riskScore: undefined,
          createdTime: '',
          completedTime: undefined,
          errorMessage: undefined,
          data: undefined
        });
      }
    }

    // TODO: Add AI analysis here when implemented
    analyses.push({
      id: 'ai-placeholder',
      type: 'AI',
      status: 'NOT_AVAILABLE',
      riskScore: undefined,
      createdTime: '',
      completedTime: undefined,
      errorMessage: undefined,
      data: undefined
    });

    setAllAnalyses(analyses);
  }, [file, metadataAnalyses, traditionalAnalysis, videoTradSubs]);

  // Load analyses when file changes
  useEffect(() => {
    if (file?.md5Hash) {
      loadAllAnalyses(file.md5Hash);
    } else {
      setAllAnalyses([]);
    }
  }, [file, loadAllAnalyses]);


  const handleViewDetails = (record: AnalysisRecord) => {
    if (onSelectAnalysis) {
      onSelectAnalysis(record);
      return;
    }
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

  // Handle starting analysis (for NOT_STARTED status)
  const handleStartAnalysisAction = async (record: AnalysisRecord) => {
    if (!file?.md5Hash) {
      message.error('No file selected');
      return;
    }

    if (record.type === 'METADATA') {
      startMetadataAnalysis(file.md5Hash);
    } else if (record.type === 'TRADITIONAL') {
      setTriggeringAnalysis(true);
      try {
        const result = await traditionalAnalysisAPI.triggerAnalysis(file.md5Hash, false);
        if (result.success) {
          message.success({
            content: 'Traditional analysis started! You will receive an email when it\'s complete.',
            duration: 5
          });
        } else {
          message.error(result.message);
        }
      } catch (error) {
        message.error('Failed to start traditional analysis');
      } finally {
        setTriggeringAnalysis(false);
      }
    }
  };

  // Handle re-analysis (for existing records)
  const handleReAnalysis = async (record: AnalysisRecord) => {
    if (!file?.md5Hash) {
      message.error('No file selected');
      return;
    }

    if (record.type === 'METADATA') {
      startMetadataAnalysis(file.md5Hash);
    } else if (record.type === 'TRADITIONAL') {
      Modal.confirm({
        title: 'Confirm Re-analysis',
        icon: <ExclamationCircleOutlined />,
        content: (
          <div>
            <p>Are you sure you want to re-run the traditional analysis?</p>
            <p><strong>Note:</strong> Traditional analysis has high computational cost and will take approximately <strong>2-5 minutes</strong> to complete.</p>
            <p>You will receive an email notification when the analysis is complete.</p>
          </div>
        ),
        okText: 'Yes, Re-analyze',
        cancelText: 'Cancel',
        onOk: async () => {
          if (!file?.md5Hash) return;
          setTriggeringAnalysis(true);
          try {
            const result = await traditionalAnalysisAPI.triggerAnalysis(file.md5Hash, true);
            if (result.success) {
              message.success({
                content: 'Traditional analysis started! You will receive an email when it\'s complete.',
                duration: 5
              });
            } else {
              message.error(result.message);
            }
          } catch (error) {
            message.error('Failed to re-run traditional analysis');
          } finally {
            setTriggeringAnalysis(false);
          }
        },
      });
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
        { text: 'Photo Traditional', value: 'TRADITIONAL' },
        { text: 'Video Traditional', value: 'VIDEO_TRADITIONAL' },
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
      render: (_, record: AnalysisRecord) => {
        const isNotStarted = record.status === 'NOT_STARTED';
        const isNotAvailable = record.status === 'NOT_AVAILABLE';
        const isCompleted = record.status === 'COMPLETED';
        const isFailed = record.status === 'FAILED';
        const isProcessing = record.status === 'IN_PROGRESS' || record.status === 'PROCESSING' || record.status === 'PENDING';

        return (
          <Space>
            {/* Start Analysis Button - only for NOT_STARTED */}
            {isNotStarted && (record.type === 'METADATA' || record.type === 'TRADITIONAL') && (
              <Button
                type="primary"
                size="small"
                icon={record.type === 'METADATA' ? <ScanOutlined /> : <ExperimentOutlined />}
                onClick={() => handleStartAnalysisAction(record)}
                loading={record.type === 'METADATA' ? metadataLoading : triggeringAnalysis}
                style={record.type === 'TRADITIONAL' ? 
                  { background: '#722ed1', borderColor: '#722ed1' } : {}
                }
              >
                Start
              </Button>
            )}

            {/* Re-analyze Button - for completed/failed analyses */}
            {(isCompleted || isFailed) && (
              <Button
                type="text"
                size="small"
                icon={<RedoOutlined />}
                onClick={() => handleReAnalysis(record)}
                loading={record.type === 'METADATA' ? metadataLoading : triggeringAnalysis}
                title="Re-analyze"
              >
                Re-run
              </Button>
            )}

            {/* Details Button - only for completed analyses */}
            <Button
              type="text"
              size="small"
              icon={<EyeOutlined />}
              onClick={() => handleViewDetails(record)}
              disabled={!isCompleted}
            >
              Details
            </Button>

            {/* Delete Button - only for metadata analyses */}
            {record.type === 'METADATA' && isCompleted && (
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

            {/* Not Available Label - for AI analysis */}
            {isNotAvailable && (
              <Text type="secondary" style={{ fontStyle: 'italic' }}>
                Coming Soon
              </Text>
            )}

            {/* Processing Indicator */}
            {isProcessing && (
              <Text type="secondary" style={{ fontStyle: 'italic' }}>
                Processing...
              </Text>
            )}
          </Space>
        );
      },
    },
  ];

  // Render traditional analysis details

  // Render metadata analysis details (complete implementation from original)

  return (
    <div>
      <Card
        title={
          <Space>
            <BarChartOutlined />
          </Space>
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
          loading={loading || metadataLoading || traditionalLoading || videoTradLoading}
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
            {selectedAnalysis.type === 'VIDEO_TRADITIONAL' && selectedAnalysis.data && (
              renderVideoTraditionalAnalysisDetails(selectedAnalysis.data)
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
