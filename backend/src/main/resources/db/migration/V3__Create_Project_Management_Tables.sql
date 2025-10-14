-- Create projects table for forensic case management
CREATE TABLE projects (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    description TEXT,
    case_number VARCHAR(100) UNIQUE NOT NULL,
    client_name VARCHAR(200),
    client_contact VARCHAR(255),
    project_type ENUM('GENERAL', 'CRIMINAL', 'CIVIL', 'CORPORATE', 'ACADEMIC_RESEARCH') NOT NULL DEFAULT 'GENERAL',
    status ENUM('ACTIVE', 'COMPLETED', 'SUSPENDED', 'ARCHIVED') NOT NULL DEFAULT 'ACTIVE',
    tags VARCHAR(500),
    deadline DATETIME,
    case_date DATETIME,
    evidence_description TEXT,
    notes TEXT,
    user_id BIGINT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    INDEX idx_projects_user_id (user_id),
    INDEX idx_projects_case_number (case_number),
    INDEX idx_projects_status (status),
    INDEX idx_projects_type (project_type),
    INDEX idx_projects_created_at (created_at)
);

-- Create analysis_tasks table for different types of analysis
CREATE TABLE analysis_tasks (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_name VARCHAR(255) NOT NULL,
    analysis_type ENUM(
        'METADATA_ANALYSIS', 
        'DEEPFAKE_DETECTION', 
        'EDIT_DETECTION', 
        'AUTHENTICITY_VERIFICATION',
        'COMPRESSION_ANALYSIS', 
        'NOISE_ANALYSIS',
        'GEOMETRIC_ANALYSIS',
        'LIGHTING_ANALYSIS',
        'SHADOW_ANALYSIS',
        'PIXEL_LEVEL_ANALYSIS',
        'FREQUENCY_DOMAIN_ANALYSIS',
        'BLOCKCHAIN_VERIFICATION',
        'COMPREHENSIVE_REPORT'
    ) NOT NULL,
    status ENUM('PENDING', 'QUEUED', 'RUNNING', 'COMPLETED', 'FAILED', 'CANCELLED', 'PAUSED') NOT NULL DEFAULT 'PENDING',
    description TEXT,
    parameters LONGTEXT,
    results LONGTEXT,
    notes TEXT,
    confidence_score DOUBLE,
    error_message LONGTEXT,
    priority INT DEFAULT 5,
    progress DOUBLE DEFAULT 0.0,
    estimated_duration BIGINT,
    actual_duration BIGINT,
    started_at DATETIME,
    completed_at DATETIME,
    project_id BIGINT NOT NULL,
    media_file_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    INDEX idx_analysis_tasks_project_id (project_id),
    INDEX idx_analysis_tasks_media_file_id (media_file_id),
    INDEX idx_analysis_tasks_user_id (user_id),
    INDEX idx_analysis_tasks_status (status),
    INDEX idx_analysis_tasks_type (analysis_type),
    INDEX idx_analysis_tasks_created_at (created_at)
);

-- Add project_id to media_files table
ALTER TABLE media_files 
ADD COLUMN project_id BIGINT NOT NULL AFTER user_id,
ADD INDEX idx_media_files_project_id (project_id);

-- Add project_id and analysis_task_id to media_metadata table
ALTER TABLE media_metadata 
ADD COLUMN project_id BIGINT NOT NULL AFTER user_id,
ADD COLUMN analysis_task_id BIGINT AFTER project_id,
ADD INDEX idx_media_metadata_project_id (project_id),
ADD INDEX idx_media_metadata_analysis_task_id (analysis_task_id);
