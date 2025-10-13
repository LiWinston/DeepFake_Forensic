// Report generation utilities for analysis results

import jsPDF from 'jspdf';
import type {
  UploadFile,
  MetadataAnalysis,
  TraditionalAnalysisResult,
  AnalysisTask,
} from '../types';

interface ReportData {
  file: UploadFile;
  metadata?: MetadataAnalysis;
  traditional?: TraditionalAnalysisResult;
  videoTraditional?: any;
  aiDetection?: AnalysisTask;
}

/**
 * Generate a comprehensive forensic analysis report
 */
export const generateAnalysisReport = (data: ReportData): string => {
  const { file, metadata, traditional, videoTraditional, aiDetection } = data;
  
  let report = '';
  
  // Header
  report += '='.repeat(80) + '\n';
  report += 'DEEPFAKE FORENSIC ANALYSIS REPORT\n';
  report += '='.repeat(80) + '\n\n';
  
  // File Information Section
  report += '1. FILE INFORMATION\n';
  report += '-'.repeat(80) + '\n';
  report += `File Name: ${file.originalName}\n`;
  report += `File Size: ${(file.fileSize / 1024 / 1024).toFixed(2)} MB\n`;
  report += `File Type: ${file.fileType || 'Unknown'}\n`;
  report += `Upload Time: ${new Date(file.uploadTime).toLocaleString()}\n`;
  if (file.md5Hash) {
    report += `MD5 Hash: ${file.md5Hash}\n`;
  }
  report += `File Path: ${file.filePath}\n`;
  report += `Status: ${file.status}\n`;
  report += '\n';
  
  // Metadata Analysis Section
  if (metadata?.result) {
    report += '2. METADATA ANALYSIS\n';
    report += '-'.repeat(80) + '\n';
    report += `Analysis Status: ${metadata.status}\n`;
    report += `Analysis Time: ${new Date(metadata.createdTime).toLocaleString()}\n`;
    
    if (metadata.result.suspicious) {
      report += `\nSUSPICIOUS ANALYSIS:\n`;
      report += `  Risk Score: ${metadata.result.suspicious.riskScore}%\n`;
      report += `  Has Anomalies: ${metadata.result.suspicious.hasAnomalies ? 'Yes' : 'No'}\n`;
      
      if (metadata.result.suspicious.assessmentConclusion) {
        report += `  Assessment: ${metadata.result.suspicious.assessmentConclusion}\n`;
      }
      
      if (metadata.result.suspicious.anomalies.length > 0) {
        report += `\n  Detected Anomalies:\n`;
        metadata.result.suspicious.anomalies.forEach((anomaly, idx) => {
          report += `    ${idx + 1}. ${anomaly}\n`;
        });
      }
      
      if (metadata.result.suspicious.analysisNotes) {
        report += `\n  Analysis Notes:\n`;
        report += `${metadata.result.suspicious.analysisNotes}\n`;
      }
    }
    
    // File Header Analysis
    if (metadata.result.fileHeaderAnalysis) {
      const header = metadata.result.fileHeaderAnalysis;
      report += `\nFILE HEADER ANALYSIS:\n`;
      report += `  Detected Format: ${header.detectedFormat || 'Unknown'}\n`;
      report += `  Expected Format: ${header.expectedFormat || 'Unknown'}\n`;
      report += `  Format Match: ${header.formatMatch ? 'Yes' : 'No'}\n`;
      report += `  Integrity Status: ${header.integrityStatus || 'Unknown'}\n`;
      report += `  Risk Level: ${header.riskLevel || 'Unknown'}\n`;
      if (header.signatureHex) {
        report += `  File Signature: ${header.signatureHex}\n`;
      }
      if (header.summary) {
        report += `  Summary: ${header.summary}\n`;
      }
    }
    
    // Hash Data
    if (metadata.result.hashData) {
      report += `\nHASH DATA:\n`;
      if (metadata.result.hashData.md5) {
        report += `  MD5: ${metadata.result.hashData.md5}\n`;
      }
      if (metadata.result.hashData.sha256) {
        report += `  SHA256: ${metadata.result.hashData.sha256}\n`;
      }
    }
    
    // Technical Data
    if (metadata.result.technicalData) {
      report += `\nTECHNICAL DATA:\n`;
      Object.entries(metadata.result.technicalData).forEach(([key, value]) => {
        report += `  ${key}: ${value}\n`;
      });
    }
    
    // EXIF Data
    if (metadata.result.exifData) {
      report += `\nEXIF DATA:\n`;
      report += formatNestedObject(metadata.result.exifData, '  ');
    }
    
    report += '\n';
  }
  
  // Traditional Analysis Section (for images)
  if (traditional) {
    report += '3. TRADITIONAL FORENSIC ANALYSIS\n';
    report += '-'.repeat(80) + '\n';
    report += `Analysis Status: ${traditional.analysisStatus}\n`;
    report += `Overall Confidence Score: ${traditional.overallConfidenceScore}%\n`;
    report += `Authenticity Assessment: ${traditional.authenticityAssessment.replace('_', ' ')}\n`;
    report += `Processing Time: ${traditional.processingTimeMs} ms\n`;
    report += `Image Dimensions: ${traditional.imageWidth} × ${traditional.imageHeight}\n`;
    report += `File Size: ${(traditional.fileSizeBytes / 1024 / 1024).toFixed(2)} MB\n`;
    
    report += `\nANALYSIS SUMMARY:\n`;
    report += `${traditional.analysisSummary}\n`;
    
    report += `\nDETAILED FINDINGS:\n`;
    report += `${traditional.detailedFindings}\n`;
    
    // Individual analysis results
    if (traditional.elaAnalysis) {
      report += `\nERROR LEVEL ANALYSIS (ELA):\n`;
      report += `  Confidence Score: ${traditional.elaAnalysis.confidenceScore}%\n`;
      report += `  Suspicious Regions: ${traditional.elaAnalysis.suspiciousRegions}\n`;
      report += `  Analysis: ${traditional.elaAnalysis.analysis}\n`;
    }
    
    if (traditional.cfaAnalysis) {
      report += `\nCOLOR FILTER ARRAY (CFA) ANALYSIS:\n`;
      report += `  Confidence Score: ${traditional.cfaAnalysis.confidenceScore}%\n`;
      report += `  Interpolation Anomalies: ${traditional.cfaAnalysis.interpolationAnomalies}\n`;
      report += `  Analysis: ${traditional.cfaAnalysis.analysis}\n`;
    }
    
    if (traditional.copyMoveAnalysis) {
      report += `\nCOPY-MOVE DETECTION:\n`;
      report += `  Confidence Score: ${traditional.copyMoveAnalysis.confidenceScore}%\n`;
      report += `  Suspicious Blocks: ${traditional.copyMoveAnalysis.suspiciousBlocks}\n`;
      report += `  Analysis: ${traditional.copyMoveAnalysis.analysis}\n`;
    }
    
    if (traditional.lightingAnalysis) {
      report += `\nLIGHTING ANALYSIS:\n`;
      report += `  Confidence Score: ${traditional.lightingAnalysis.confidenceScore}%\n`;
      report += `  Inconsistencies: ${traditional.lightingAnalysis.inconsistencies}\n`;
      report += `  Analysis: ${traditional.lightingAnalysis.analysis}\n`;
    }
    
    if (traditional.noiseAnalysis) {
      report += `\nNOISE RESIDUAL ANALYSIS:\n`;
      report += `  Confidence Score: ${traditional.noiseAnalysis.confidenceScore}%\n`;
      report += `  Suspicious Regions: ${traditional.noiseAnalysis.suspiciousRegions}\n`;
      report += `  Analysis: ${traditional.noiseAnalysis.analysis}\n`;
    }
    
    report += '\n';
  }
  
  // Video Traditional Analysis Section
  if (videoTraditional?.subtasks && videoTraditional.subtasks.length > 0) {
    report += '4. VIDEO TRADITIONAL ANALYSIS\n';
    report += '-'.repeat(80) + '\n';
    
    videoTraditional.subtasks.forEach((subtask: any, idx: number) => {
      report += `\n${idx + 1}. ${prettyMethod(subtask.method)}:\n`;
      report += `  Status: ${subtask.status}\n`;
      
      if (subtask.result) {
        Object.entries(subtask.result).forEach(([key, value]: [string, any]) => {
          if (typeof value === 'object' && value !== null && !Array.isArray(value)) {
            report += `  ${key}:\n`;
            report += formatNestedObject(value, '    ');
          } else if (!Array.isArray(value)) {
            report += `  ${key}: ${value}\n`;
          }
        });
      }
      
      if (subtask.errorMessage) {
        report += `  Error: ${subtask.errorMessage}\n`;
      }
    });
    
    report += '\n';
  }
  
  // AI Detection Section
  if (aiDetection && aiDetection.results) {
    try {
      const aiResults = JSON.parse(aiDetection.results);
      
      report += '5. AI DETECTION ANALYSIS\n';
      report += '-'.repeat(80) + '\n';
      report += `Analysis Status: ${aiDetection.status}\n`;
      report += `Model Used: ${aiResults.model || 'Unknown'}\n`;
      
      if (aiResults.result) {
        const prediction = aiResults.result.prediction || 'Unknown';
        const confidence = aiResults.result.confidence || aiResults.result.confidenceScore || 0;
        
        report += `\nDETECTION RESULT:\n`;
        report += `  Prediction: ${prediction}\n`;
        report += `  Confidence Score: ${(confidence * 100).toFixed(2)}%\n`;
        
        const probabilities = aiResults.result.probabilities || aiResults.result.class_probabilities || {};
        if (Object.keys(probabilities).length > 0) {
          report += `\nCLASS PROBABILITIES:\n`;
          Object.entries(probabilities).forEach(([className, prob]: [string, any]) => {
            report += `  ${className}: ${typeof prob === 'number' ? (prob * 100).toFixed(2) : prob}%\n`;
          });
        }
      }
      
      report += '\n';
    } catch (e) {
      console.error('Failed to parse AI detection results:', e);
    }
  }
  
  // Footer
  report += '='.repeat(80) + '\n';
  report += `Report Generated: ${new Date().toLocaleString()}\n`;
  report += '='.repeat(80) + '\n';
  
  return report;
};

/**
 * Format nested object for report
 */
const formatNestedObject = (obj: Record<string, any>, indent: string = ''): string => {
  let result = '';
  Object.entries(obj).forEach(([key, value]) => {
    if (typeof value === 'object' && value !== null && !Array.isArray(value)) {
      result += `${indent}${key}:\n`;
      result += formatNestedObject(value, indent + '  ');
    } else if (Array.isArray(value)) {
      result += `${indent}${key}: [${value.join(', ')}]\n`;
    } else {
      result += `${indent}${key}: ${value}\n`;
    }
  });
  return result;
};

/**
 * Pretty method name for video traditional analysis
 */
const prettyMethod = (m?: string): string => {
  if (!m) return '';
  const map: Record<string, string> = {
    NOISE: 'Noise Pattern',
    FLOW: 'Optical Flow',
    FREQ: 'Frequency Domain',
    FREQUENCY: 'Frequency Domain',
    TEMPORAL: 'Temporal Inconsistency',
    COPYMOVE: 'Copy-Move'
  };
  return map[m] || m;
};

/**
 * Download report as text file
 */
export const downloadReport = (content: string, filename: string): void => {
  const blob = new Blob([content], { type: 'text/plain;charset=utf-8' });
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  URL.revokeObjectURL(url);
};

/**
 * Generate and download PDF report
 */
export const generatePDFReport = (data: ReportData, filename: string): void => {
  const { file, metadata, traditional, videoTraditional, aiDetection } = data;
  
  // Create PDF document
  const doc = new jsPDF({
    orientation: 'portrait',
    unit: 'mm',
    format: 'a4',
  });
  
  const pageWidth = doc.internal.pageSize.getWidth();
  const pageHeight = doc.internal.pageSize.getHeight();
  const margin = 15;
  const lineHeight = 7;
  let yPos = margin;
  
  // Helper function to add text with wrapping
  const addText = (text: string, fontSize: number = 10, isBold: boolean = false) => {
    if (yPos > pageHeight - margin - 10) {
      doc.addPage();
      yPos = margin;
    }
    
    doc.setFontSize(fontSize);
    doc.setFont('helvetica', isBold ? 'bold' : 'normal');
    
    const lines = doc.splitTextToSize(text, pageWidth - 2 * margin);
    lines.forEach((line: string) => {
      if (yPos > pageHeight - margin - 10) {
        doc.addPage();
        yPos = margin;
      }
      doc.text(line, margin, yPos);
      yPos += lineHeight;
    });
  };
  
  const addSection = (title: string) => {
    yPos += 5;
    doc.setFillColor(240, 240, 240);
    doc.rect(margin, yPos - 5, pageWidth - 2 * margin, 8, 'F');
    doc.setFontSize(12);
    doc.setFont('helvetica', 'bold');
    doc.text(title, margin + 2, yPos);
    yPos += lineHeight + 3;
  };
  
  const addLine = () => {
    doc.setDrawColor(200, 200, 200);
    doc.line(margin, yPos, pageWidth - margin, yPos);
    yPos += 3;
  };
  
  // Header
  doc.setFillColor(41, 128, 185);
  doc.rect(0, 0, pageWidth, 40, 'F');
  doc.setTextColor(255, 255, 255);
  doc.setFontSize(20);
  doc.setFont('helvetica', 'bold');
  doc.text('DEEPFAKE FORENSIC ANALYSIS REPORT', pageWidth / 2, 20, { align: 'center' });
  doc.setFontSize(10);
  doc.setFont('helvetica', 'normal');
  doc.text(`Generated: ${new Date().toLocaleString()}`, pageWidth / 2, 30, { align: 'center' });
  
  doc.setTextColor(0, 0, 0);
  yPos = 50;
  
  // 1. File Information
  addSection('1. FILE INFORMATION');
  addText(`File Name: ${file.originalName}`);
  addText(`File Size: ${(file.fileSize / 1024 / 1024).toFixed(2)} MB`);
  addText(`File Type: ${file.fileType || 'Unknown'}`);
  addText(`Upload Time: ${new Date(file.uploadTime).toLocaleString()}`);
  if (file.md5Hash) {
    addText(`MD5 Hash: ${file.md5Hash}`);
  }
  addText(`Status: ${file.status}`);
  addLine();
  
  // 2. Metadata Analysis
  if (metadata?.result) {
    addSection('2. METADATA ANALYSIS');
    addText(`Analysis Status: ${metadata.status}`);
    addText(`Analysis Time: ${new Date(metadata.createdTime).toLocaleString()}`);
    
    if (metadata.result.suspicious) {
      yPos += 3;
      addText('SUSPICIOUS ANALYSIS:', 11, true);
      addText(`  Risk Score: ${metadata.result.suspicious.riskScore}%`);
      addText(`  Has Anomalies: ${metadata.result.suspicious.hasAnomalies ? 'Yes' : 'No'}`);
      
      if (metadata.result.suspicious.assessmentConclusion) {
        addText(`  Assessment: ${metadata.result.suspicious.assessmentConclusion}`);
      }
      
      if (metadata.result.suspicious.anomalies.length > 0) {
        yPos += 2;
        addText('  Detected Anomalies:', 10, true);
        metadata.result.suspicious.anomalies.forEach((anomaly, idx) => {
          addText(`    ${idx + 1}. ${anomaly}`, 9);
        });
      }
    }
    
    // File Header Analysis
    if (metadata.result.fileHeaderAnalysis) {
      const header = metadata.result.fileHeaderAnalysis;
      yPos += 3;
      addText('FILE HEADER ANALYSIS:', 11, true);
      addText(`  Detected Format: ${header.detectedFormat || 'Unknown'}`);
      addText(`  Expected Format: ${header.expectedFormat || 'Unknown'}`);
      addText(`  Format Match: ${header.formatMatch ? 'Yes' : 'No'}`);
      addText(`  Integrity Status: ${header.integrityStatus || 'Unknown'}`);
      addText(`  Risk Level: ${header.riskLevel || 'Unknown'}`);
      if (header.summary) {
        addText(`  Summary: ${header.summary}`);
      }
    }
    
    // Hash Data
    if (metadata.result.hashData) {
      yPos += 3;
      addText('HASH DATA:', 11, true);
      if (metadata.result.hashData.md5) {
        addText(`  MD5: ${metadata.result.hashData.md5}`, 8);
      }
      if (metadata.result.hashData.sha256) {
        addText(`  SHA256: ${metadata.result.hashData.sha256}`, 8);
      }
    }
    
    addLine();
  }
  
  // 3. Traditional Analysis
  if (traditional) {
    addSection('3. TRADITIONAL FORENSIC ANALYSIS');
    addText(`Analysis Status: ${traditional.analysisStatus}`);
    addText(`Overall Confidence Score: ${traditional.overallConfidenceScore}%`);
    addText(`Authenticity Assessment: ${traditional.authenticityAssessment.replace('_', ' ')}`);
    addText(`Processing Time: ${traditional.processingTimeMs} ms`);
    addText(`Image Dimensions: ${traditional.imageWidth} × ${traditional.imageHeight}`);
    
    yPos += 3;
    addText('ANALYSIS SUMMARY:', 11, true);
    addText(traditional.analysisSummary, 9);
    
    yPos += 3;
    addText('DETAILED FINDINGS:', 11, true);
    addText(traditional.detailedFindings, 9);
    
    // Individual analysis results
    if (traditional.elaAnalysis) {
      yPos += 3;
      addText('ERROR LEVEL ANALYSIS (ELA):', 11, true);
      addText(`  Confidence Score: ${traditional.elaAnalysis.confidenceScore}%`);
      addText(`  Suspicious Regions: ${traditional.elaAnalysis.suspiciousRegions}`);
      addText(`  Analysis: ${traditional.elaAnalysis.analysis}`, 9);
    }
    
    if (traditional.cfaAnalysis) {
      yPos += 3;
      addText('COLOR FILTER ARRAY (CFA) ANALYSIS:', 11, true);
      addText(`  Confidence Score: ${traditional.cfaAnalysis.confidenceScore}%`);
      addText(`  Interpolation Anomalies: ${traditional.cfaAnalysis.interpolationAnomalies}`);
      addText(`  Analysis: ${traditional.cfaAnalysis.analysis}`, 9);
    }
    
    if (traditional.copyMoveAnalysis) {
      yPos += 3;
      addText('COPY-MOVE DETECTION:', 11, true);
      addText(`  Confidence Score: ${traditional.copyMoveAnalysis.confidenceScore}%`);
      addText(`  Suspicious Blocks: ${traditional.copyMoveAnalysis.suspiciousBlocks}`);
      addText(`  Analysis: ${traditional.copyMoveAnalysis.analysis}`, 9);
    }
    
    if (traditional.lightingAnalysis) {
      yPos += 3;
      addText('LIGHTING ANALYSIS:', 11, true);
      addText(`  Confidence Score: ${traditional.lightingAnalysis.confidenceScore}%`);
      addText(`  Inconsistencies: ${traditional.lightingAnalysis.inconsistencies}`);
      addText(`  Analysis: ${traditional.lightingAnalysis.analysis}`, 9);
    }
    
    if (traditional.noiseAnalysis) {
      yPos += 3;
      addText('NOISE RESIDUAL ANALYSIS:', 11, true);
      addText(`  Confidence Score: ${traditional.noiseAnalysis.confidenceScore}%`);
      addText(`  Suspicious Regions: ${traditional.noiseAnalysis.suspiciousRegions}`);
      addText(`  Analysis: ${traditional.noiseAnalysis.analysis}`, 9);
    }
    
    addLine();
  }
  
  // 4. Video Traditional Analysis
  if (videoTraditional?.subtasks && videoTraditional.subtasks.length > 0) {
    addSection('4. VIDEO TRADITIONAL ANALYSIS');
    
    videoTraditional.subtasks.forEach((subtask: any, idx: number) => {
      yPos += 2;
      addText(`${idx + 1}. ${prettyMethod(subtask.method)}:`, 11, true);
      addText(`  Status: ${subtask.status}`);
      
      if (subtask.result) {
        Object.entries(subtask.result).forEach(([key, value]: [string, any]) => {
          if (typeof value !== 'object' && !Array.isArray(value)) {
            addText(`  ${key}: ${value}`, 9);
          }
        });
      }
      
      if (subtask.errorMessage) {
        addText(`  Error: ${subtask.errorMessage}`, 9);
      }
    });
    
    addLine();
  }
  
  // 5. AI Detection
  if (aiDetection && aiDetection.results) {
    try {
      const aiResults = JSON.parse(aiDetection.results);
      
      addSection('5. AI DETECTION ANALYSIS');
      addText(`Analysis Status: ${aiDetection.status}`);
      addText(`Model Used: ${aiResults.model || 'Unknown'}`);
      
      if (aiResults.result) {
        const prediction = aiResults.result.prediction || 'Unknown';
        const confidence = aiResults.result.confidence || aiResults.result.confidenceScore || 0;
        
        yPos += 3;
        addText('DETECTION RESULT:', 11, true);
        addText(`  Prediction: ${prediction}`);
        addText(`  Confidence Score: ${(confidence * 100).toFixed(2)}%`);
        
        const probabilities = aiResults.result.probabilities || aiResults.result.class_probabilities || {};
        if (Object.keys(probabilities).length > 0) {
          yPos += 2;
          addText('CLASS PROBABILITIES:', 11, true);
          Object.entries(probabilities).forEach(([className, prob]: [string, any]) => {
            addText(`  ${className}: ${typeof prob === 'number' ? (prob * 100).toFixed(2) : prob}%`);
          });
        }
      }
      
      addLine();
    } catch (e) {
      console.error('Failed to parse AI detection results:', e);
    }
  }
  
  // Footer on last page
  const totalPages = doc.internal.pages.length - 1;
  for (let i = 1; i <= totalPages; i++) {
    doc.setPage(i);
    doc.setFontSize(8);
    doc.setTextColor(128, 128, 128);
    doc.text(`Page ${i} of ${totalPages}`, pageWidth / 2, pageHeight - 10, { align: 'center' });
    doc.text('DeepFake Forensic Analysis Platform', pageWidth / 2, pageHeight - 5, { align: 'center' });
  }
  
  // Save PDF
  doc.save(filename);
};

