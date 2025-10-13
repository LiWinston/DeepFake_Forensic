import React, { useState, useEffect, useCallback } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import {
  Layout,
  Row,
  Col,
  Typography,
  Card,
  Space,
  Button,
  Drawer,
  Modal,
  Alert,
  Divider,
  Breadcrumb,
  Tag,
  message,
} from 'antd';
import {
  FileTextOutlined,
  BarChartOutlined,
  EyeOutlined,
  HomeOutlined,
  ProjectOutlined,
  ArrowLeftOutlined,
  DownloadOutlined,
} from '@ant-design/icons';
import FilesList from '../components/FilesList';
import FileUpload from '../components/FileUpload';
import AnalysisOverview, { AnalysisDetails } from '../components/AnalysisOverview';
import type { AnalysisRecord } from '../components/AnalysisOverview';
import type { UploadFile, Project } from '../types';
import uploadService from '../services/upload';
import { projectApi } from '../services/project';
import { traditionalAnalysisAPI } from '../services/traditional';
import { videoTraditionalAPI } from '../services/videoTraditional';
import { analysisService } from '../services/analysis';
import { useMetadataAnalysis } from '../hooks';
import { generatePDFReport } from '../utils/reportGenerator';
import '../styles/pages/FilesPage.css';

const { Content } = Layout;
const { Title, Paragraph } = Typography;

const FilesPage: React.FC = () => {
  const { projectId } = useParams<{ projectId?: string }>();
  const navigate = useNavigate();
  const [selectedFile, setSelectedFile] = useState<UploadFile | null>(null);
  const [resultsOpen, setResultsOpen] = useState(false);
  const [detailOpen, setDetailOpen] = useState(false);
  const [selectedRecord, setSelectedRecord] = useState<AnalysisRecord | null>(null);
  const [previewModalVisible, setPreviewModalVisible] = useState(false);
  const [refreshTrigger, setRefreshTrigger] = useState(0);
  const [currentProject, setCurrentProject] = useState<Project | null>(null);
  const [downloadingReport, setDownloadingReport] = useState(false);
  
  // Use metadata analysis hook
  const { analyses: metadataAnalyses, loadAnalyses: loadMetadataAnalyses } = useMetadataAnalysis();

  // Load project info if projectId is provided
  useEffect(() => {
    const loadProjectInfo = async () => {
      if (projectId) {
        try {
          const response = await projectApi.getProject(Number(projectId));
          setCurrentProject(response.data);
        } catch (error) {
          console.error('Failed to load project info:', error);
        }
      }
    };

    loadProjectInfo();
  }, [projectId]);

  const handleFileSelect = (file: UploadFile) => {
    setSelectedFile(file);
  };

  const handleAnalyzeFile = () => {
    if (selectedFile) {
      setResultsOpen(true);
    }
  };

  const handlePreviewFile = () => {
    if (selectedFile) {
      setPreviewModalVisible(true);
    }
  };

  const handleCloseResults = () => setResultsOpen(false);
  const handleCloseDetail = () => setDetailOpen(false);

  const handleClosePreviewModal = () => {
    setPreviewModalVisible(false);
  };

  const handleUploadSuccess = (file: UploadFile) => {
    console.log('Upload successful:', file);
    // Trigger refresh of files list
    setRefreshTrigger(prev => prev + 1);
  };

  const handleUploadError = (error: string) => {
    console.error('Upload error:', error);
  };
  
  // Handle download report as PDF
  const handleDownloadReport = useCallback(async () => {
    if (!selectedFile || !selectedFile.md5Hash) {
      message.error('No file selected or file analysis not completed');
      return;
    }

    setDownloadingReport(true);
    try {
      // Determine if file is image or video
      const isImage = selectedFile.originalName?.toLowerCase().match(/\.(jpg|jpeg|png|gif|bmp|webp|tif|tiff|heic|heif)$/);
      
      // Load all analysis data
      const [metadataData, traditionalData, videoTraditionalData, aiDetectionData] = await Promise.allSettled([
        // Load metadata analysis
        loadMetadataAnalyses(selectedFile.md5Hash).then(() => metadataAnalyses[0]),
        
        // Load traditional analysis (for images only)
        isImage ? traditionalAnalysisAPI.getAnalysisResult(selectedFile.md5Hash).catch(() => null) : Promise.resolve(null),
        
        // Load video traditional analysis
        videoTraditionalAPI.getResultsByFile(selectedFile.md5Hash).catch(() => []),
        
        // Load AI detection (for images only)
        isImage ? analysisService.getAiImageResultByMd5(selectedFile.md5Hash)
          .then(resp => (resp.data as any)?.data)
          .catch(() => null) : Promise.resolve(null),
      ]);

      // Extract data from settled promises
      const metadata = metadataData.status === 'fulfilled' ? metadataData.value : undefined;
      const traditional = traditionalData.status === 'fulfilled' ? (traditionalData.value || undefined) : undefined;
      const videoTradSubs = videoTraditionalData.status === 'fulfilled' ? videoTraditionalData.value : undefined;
      const aiDetection = aiDetectionData.status === 'fulfilled' ? (aiDetectionData.value || undefined) : undefined;

      // Generate and download PDF report
      const filename = `forensic_report_${selectedFile.originalName.replace(/\.[^/.]+$/, '')}_${new Date().getTime()}.pdf`;
      generatePDFReport({
        file: selectedFile,
        metadata,
        traditional,
        videoTraditional: videoTradSubs && videoTradSubs.length > 0 ? {
          subtasks: videoTradSubs.map((v: any) => ({
            id: v.id,
            type: `VIDEO_TRADITIONAL_${v.method}`,
            method: v.method,
            artifacts: v.artifacts || {},
            result: v.result || {},
            status: v.success ? 'COMPLETED' : 'FAILED',
            errorMessage: v.success ? undefined : 'Analysis failed'
          }))
        } : undefined,
        aiDetection,
      }, filename);
      
      message.success('PDF report downloaded successfully');
    } catch (error) {
      console.error('Error generating PDF report:', error);
      message.error('Failed to generate PDF report');
    } finally {
      setDownloadingReport(false);
    }
  }, [selectedFile, metadataAnalyses, loadMetadataAnalyses]);
  
  const shiftLevel = detailOpen ? 2 : resultsOpen ? 1 : 0;

  const handleBackdropClick = () => {
    if (detailOpen) setDetailOpen(false);
    else if (resultsOpen) setResultsOpen(false);
  };

  return (
    <Content style={{ padding: '24px', position: 'relative', overflow: 'hidden' }}>
      {/* Backdrop overlay for push effect */}
      {(resultsOpen || detailOpen) && (
        <div
          className={`shift-overlay ${detailOpen ? 'level-2' : 'level-1'}`}
          onClick={handleBackdropClick}
        />
      )}

      <div className={`push-container ${shiftLevel === 1 ? 'shift-1' : ''} ${shiftLevel === 2 ? 'shift-2' : ''}`}>
      {/* Breadcrumb Navigation */}
      <Breadcrumb style={{ marginBottom: 16 }}>
        <Breadcrumb.Item>
          <HomeOutlined />
          <span onClick={() => navigate('/')} style={{ cursor: 'pointer', marginLeft: 8 }}>
            Home
          </span>
        </Breadcrumb.Item>
        {currentProject && (
          <Breadcrumb.Item>
            <ProjectOutlined />
            <span onClick={() => navigate('/projects')} style={{ cursor: 'pointer', marginLeft: 8 }}>
              Projects
            </span>
          </Breadcrumb.Item>
        )}
        <Breadcrumb.Item>
          <FileTextOutlined />
          <span style={{ marginLeft: 8 }}>
            {currentProject ? `Files - ${currentProject.name}` : 'File Management'}
          </span>
        </Breadcrumb.Item>
      </Breadcrumb>

      {/* Project Information Banner */}
      {currentProject && (
        <Card style={{ marginBottom: 24, background: 'linear-gradient(135deg, #f0f9ff, #e0f2fe)' }}>
          <Row align="middle" justify="space-between">
            <Col>
              <Space direction="vertical" size="small">
                <Title level={4} style={{ margin: 0, color: '#1890ff' }}>
                  <ProjectOutlined /> {currentProject.name}
                </Title>                <Space wrap>
                  <Tag color="blue">{currentProject.projectType}</Tag>
                  <Tag color={
                    currentProject.status === 'ACTIVE' ? 'green' :
                    currentProject.status === 'COMPLETED' ? 'blue' :
                    currentProject.status === 'SUSPENDED' ? 'orange' : 'default'
                  }>
                    {currentProject.status}
                  </Tag>
                  {currentProject.caseNumber && (
                    <Tag color="purple">Case: {currentProject.caseNumber}</Tag>
                  )}
                </Space>
                {currentProject.description && (
                  <Paragraph style={{ margin: 0, color: '#666' }}>
                    {currentProject.description}
                  </Paragraph>
                )}
              </Space>
            </Col>
            <Col>
              <Button 
                icon={<ArrowLeftOutlined />} 
                onClick={() => navigate('/projects')}
              >
                Back to Projects
              </Button>
            </Col>
          </Row>
        </Card>
      )}

      {/* Page Header */}
      <div style={{ marginBottom: 24 }}>
        <Title level={2}>
          {currentProject ? `File Management - ${currentProject.name}` : 'File Management'}
        </Title>
        <Paragraph>
          {currentProject 
            ? `Upload and manage files for the ${currentProject.name} project. Select a file to view its properties and analysis results.`
            : 'Upload files and manage your media collection for forensic analysis. Select a file to view its properties and analysis results.'
          }
        </Paragraph>
      </div>      {/* Upload Section */}
      <FileUpload
        onUploadSuccess={handleUploadSuccess}
        onUploadError={handleUploadError}
        showProgress={true}
        defaultProjectId={projectId ? Number(projectId) : undefined}
      />

      <Divider />

  <Row gutter={[24, 24]}>        {/* Files List Section */}
        <Col xs={24} lg={16}>
          <FilesList
            onFileSelect={handleFileSelect}
            selectable={true}
            showActions={true}
            defaultProjectId={projectId ? Number(projectId) : undefined}
            selectedFile={selectedFile}
            key={`${refreshTrigger}-${projectId}`} // Force refresh when upload succeeds or project changes
          />
        </Col>

        {/* File Details Section */}
        <Col xs={24} lg={8}>
          <Card
            title={
              <Space>
                <FileTextOutlined />
                <span>File Details</span>
              </Space>
            }
            size="small"
            style={{ height: 'fit-content' }}
          >
            {selectedFile ? (
              <div>
                <Space direction="vertical" style={{ width: '100%' }} size="small">
                  <Row gutter={8}>
                    <Col span={8}><strong>Name:</strong></Col>
                    <Col span={16} style={{ wordBreak: 'break-all' }}>
                      {selectedFile.originalName}
                    </Col>
                  </Row>
                  <Row gutter={8}>
                    <Col span={8}><strong>Size:</strong></Col>
                    <Col span={16}>
                      {(selectedFile.fileSize / 1024 / 1024).toFixed(2)} MB
                    </Col>
                  </Row>
                  <Row gutter={8}>
                    <Col span={8}><strong>Type:</strong></Col>
                    <Col span={16}>
                      {selectedFile.fileType || 'Unknown'}
                    </Col>
                  </Row>
                  <Row gutter={8}>
                    <Col span={8}><strong>Status:</strong></Col>
                    <Col span={16}>
                      <Tag color={
                        selectedFile.status === 'COMPLETED' ? 'green' :
                        selectedFile.status === 'UPLOADING' ? 'processing' :
                        selectedFile.status === 'FAILED' ? 'error' : 'default'
                      }>
                        {selectedFile.status}
                      </Tag>
                    </Col>
                  </Row>
                  <Row gutter={8}>
                    <Col span={8}><strong>Upload:</strong></Col>
                    <Col span={16}>
                      {new Date(selectedFile.uploadTime).toLocaleString()}
                    </Col>
                  </Row>
                  {selectedFile.md5Hash && (
                    <Row gutter={8}>
                      <Col span={8}><strong>MD5:</strong></Col>
                      <Col span={16} style={{ wordBreak: 'break-all', fontSize: '11px', fontFamily: 'monospace' }}>
                        {selectedFile.md5Hash}
                      </Col>
                    </Row>
                  )}
                  <Row gutter={8}>
                    <Col span={8}><strong>Path:</strong></Col>
                    <Col span={16} style={{ wordBreak: 'break-all', fontSize: '11px', fontFamily: 'monospace' }}>
                      {selectedFile.filePath}
                    </Col>
                  </Row>
                </Space>

                <div style={{ marginTop: 16 }}>
                  <Space direction="vertical" style={{ width: '100%' }} size="small">
                    <Button
                      icon={<EyeOutlined />}
                      onClick={handlePreviewFile}
                      disabled={selectedFile.status !== 'COMPLETED'}
                      block
                    >
                      Preview File
                    </Button>
                    <Button
                      type="primary"
                      icon={<DownloadOutlined />}
                      onClick={handleDownloadReport}
                      disabled={selectedFile.status !== 'COMPLETED' || !selectedFile.md5Hash}
                      loading={downloadingReport}
                      block
                    >
                      Download Report
                    </Button>
                    <Button
                      icon={<BarChartOutlined />}
                      onClick={handleAnalyzeFile}
                      disabled={selectedFile.status !== 'COMPLETED' || !selectedFile.md5Hash}
                      block
                    >
                      View Analysis
                    </Button>
                  </Space>
                </div>

                {selectedFile.status !== 'COMPLETED' && (
                  <Alert
                    message="File not ready"
                    description="Analysis and preview are only available for completed uploads."
                    type="warning"
                    showIcon
                    style={{ marginTop: 16 }}
                  />
                )}
              </div>
            ) : (
              <div style={{ textAlign: 'center', padding: '20px 0' }}>
                <Paragraph type="secondary">
                  Select a file from the list to view its details
                </Paragraph>
              </div>
            )}
          </Card>
        </Col>
      </Row>
  </div>

      {/* Results Drawer (Level 1) */}
      <Drawer
        title={
          <Space>
            <BarChartOutlined />
            <span>Analysis Results - {selectedFile?.originalName}</span>
          </Space>
        }
        placement="right"
        open={resultsOpen}
        onClose={handleCloseResults}
        push={{ distance: 240 }}
        width={Math.min(window.innerWidth * 0.74, 1280)}
  styles={{ body: { paddingBottom: 24, overflow: 'visible' } }}
        zIndex={1060}
        mask={false}
        rootClassName={`drawer-level-1 ${detailOpen ? 'drawer-pushed' : ''}`}
      >
        {selectedFile && (
          <AnalysisOverview
            file={selectedFile}
            showFileInfo={false}
            mediaKind={
              selectedFile.originalName?.toLowerCase().match(/\.(jpg|jpeg|png|gif|bmp|webp|tif|tiff|heic|heif)$/)
                ? 'image'
                : 'video'
            }
            onSelectAnalysis={(record) => {
              setSelectedRecord(record);
              setDetailOpen(true);
            }}
          />
        )}

        {/* Detail Drawer (Level 2 - nested) */}
        <Drawer
          title={
            <Space>
              <EyeOutlined />
              <span>Analysis Detail - {selectedFile?.originalName}</span>
            </Space>
          }
          placement="right"
          open={detailOpen}
          onClose={handleCloseDetail}
          push={{ distance: 180 }}
          width={Math.min(window.innerWidth * 0.56, 980)}
          mask
          maskClosable
          maskStyle={{ backgroundColor: 'rgba(0,0,0,0)' }}
          destroyOnClose
          styles={{ body: { overflow: 'auto' } }}
          rootClassName="drawer-level-2"
        >
          {selectedRecord && (
            <AnalysisDetails file={selectedFile || undefined} record={selectedRecord} />
          )}
        </Drawer>
      </Drawer>

      {/* Preview Modal */}
      <Modal
        title={`File Preview - ${selectedFile?.originalName}`}
        open={previewModalVisible}
        onCancel={handleClosePreviewModal}
        footer={null}
        width={800}
        centered
      >
        {selectedFile && (
          <div style={{ textAlign: 'center' }}>
            {selectedFile.originalName?.toLowerCase().match(/\.(jpg|jpeg|png|gif|bmp|webp)$/) ? (
              <img
                src={uploadService.getPreviewUrl(selectedFile.id)}
                alt={selectedFile.originalName}
                style={{ maxWidth: '100%', maxHeight: '500px' }}
                onError={(e) => {
                  (e.target as HTMLImageElement).src = 'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAMIAAADDCAYAAADQvc6UAAABRWlDQ1BJQ0MgUHJvZmlsZQAAKJFjYGASSSwoyGFhYGDIzSspCnJ3UoiIjFJgf8LAwSDCIMogwMCcmFxc4BgQ4ANUwgCjUcG3awyMIPqyLsis7PPOq3QdDFcvjV3jOD1boQVTPQrgSkktTgbSf4A4LbmgqISBgTEFyFYuLykAsTuAbJEioKOA7DkgdjqEvQHEToKwj4DVhAQ5A9k3gGyB5IxEoBmML4BsnSQk8XQkNtReEOBxcfXxUQg1Mjc0dyHgXNJBSWpFCYh2zi+oLMpMzyhRcASGUqqCZ16yno6CkYGRAQMDKMwhqj/fAIcloxgHQqxAjIHBEugw5sUIsSQpBobtQPdLciLEVJYzMPBHMDBsayhILEqEO4DxG0txmrERhM29nYGBddr//5/DGRjYNRkY/l7////39v///y4Dmn+LgeHANwDrkl1AuO+pmgAAADhlWElmTU0AKgAAAAgAAYdpAAQAAAABAAAAGgAAAAAAAqACAAQAAAABAAAAwqADAAQAAAABAAAAwwAAAAD9b/HnAAAHlklEQVR4Ae3dP3Ik1xkE8Hm38MZGg/wnhgIBfgKHBIg9gQMfwKGBo4MjGTgwfAKnPoE9ggNDNxAlBwYODAyMDAhLYEAC7l+sLULn1Rn3/fVvHzTQRu3a';
                }}
              />
            ) : selectedFile.originalName?.toLowerCase().match(/\.(mp4|avi|mov|wmv|flv|webm|mkv)$/) ? (
              <video
                controls
                style={{ maxWidth: '100%', maxHeight: '500px' }}
                src={uploadService.getPreviewUrl(selectedFile.id)}
              >
                Your browser does not support the video tag.
              </video>
            ) : (
              <div style={{ padding: '40px' }}>
                <Paragraph type="secondary">
                  Preview not available for this file type
                </Paragraph>
                <Paragraph>
                  <strong>File:</strong> {selectedFile.originalName}
                  <br />
                  <strong>Size:</strong> {(selectedFile.fileSize / 1024 / 1024).toFixed(2)} MB
                </Paragraph>
              </div>
            )}
          </div>
        )}
      </Modal>
    </Content>
  );
};

export default FilesPage;
