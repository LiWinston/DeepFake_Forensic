-- Create Traditional Analysis Results Table
CREATE TABLE IF NOT EXISTS traditional_analysis_results (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    file_md5 VARCHAR(32) NOT NULL,
    original_file_path VARCHAR(255) NOT NULL,
    
    -- Analysis Status
    analysis_status ENUM('PENDING', 'IN_PROGRESS', 'COMPLETED', 'FAILED', 'PARTIAL_SUCCESS') NOT NULL DEFAULT 'PENDING',
    
    -- ELA Analysis Results
    ela_confidence_score DOUBLE DEFAULT NULL,
    ela_result_path VARCHAR(255) DEFAULT NULL,
    ela_suspicious_regions INT DEFAULT NULL,
    
    -- CFA Analysis Results
    cfa_confidence_score DOUBLE DEFAULT NULL,
    cfa_heatmap_path VARCHAR(255) DEFAULT NULL,
    cfa_interpolation_anomalies INT DEFAULT NULL,
    
    -- Copy-Move Detection Results
    copymove_confidence_score DOUBLE DEFAULT NULL,
    copymove_result_path VARCHAR(255) DEFAULT NULL,
    copymove_suspicious_blocks INT DEFAULT NULL,
    
    -- Lighting Analysis Results
    lighting_confidence_score DOUBLE DEFAULT NULL,
    lighting_analysis_path VARCHAR(255) DEFAULT NULL,
    lighting_inconsistencies INT DEFAULT NULL,
    
    -- Overall Analysis Results
    overall_confidence_score DOUBLE DEFAULT NULL,
    authenticity_assessment ENUM('AUTHENTIC', 'LIKELY_AUTHENTIC', 'SUSPICIOUS', 'LIKELY_MANIPULATED', 'MANIPULATED', 'INCONCLUSIVE') DEFAULT NULL,
    analysis_summary TEXT DEFAULT NULL,
    detailed_findings TEXT DEFAULT NULL,
    error_message TEXT DEFAULT NULL,
    
    -- Processing Metadata
    processing_time_ms BIGINT DEFAULT NULL,
    image_width INT DEFAULT NULL,
    image_height INT DEFAULT NULL,
    file_size_bytes BIGINT DEFAULT NULL,
    
    -- Timestamps and relationships
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    -- Foreign keys
    user_id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    analysis_task_id BIGINT DEFAULT NULL,
    
    -- Indexes
    INDEX idx_file_md5 (file_md5),
    INDEX idx_user_id (user_id),
    INDEX idx_project_id (project_id),
    INDEX idx_analysis_status (analysis_status),
    INDEX idx_authenticity_assessment (authenticity_assessment),
    INDEX idx_created_at (created_at),
    
    -- Foreign key constraints
    CONSTRAINT fk_traditional_analysis_user 
        FOREIGN KEY (user_id) REFERENCES users(id) 
        ON DELETE CASCADE,
    
    CONSTRAINT fk_traditional_analysis_project 
        FOREIGN KEY (project_id) REFERENCES projects(id) 
        ON DELETE CASCADE,
    
    CONSTRAINT fk_traditional_analysis_task 
        FOREIGN KEY (analysis_task_id) REFERENCES analysis_tasks(id) 
        ON DELETE SET NULL,
    
    -- Unique constraint
    UNIQUE KEY uk_traditional_analysis_file_md5 (file_md5)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
