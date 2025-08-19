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
    INDEX idx_projects_created_at (created_at),
    
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- Create analysis_tasks table for different types of analysis
CREATE TABLE analysis_tasks (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_name VARCHAR(255) NOT NULL,
    analysis_type ENUM(
        'METADATA_ANALYSIS', 
        'DEEPFAKE_DETECTION', 
        'EDIT_DETECTION', 
        'COMPRESSION_ANALYSIS', 
        'HASH_VERIFICATION',
        'EXIF_ANALYSIS',
        'STEGANOGRAPHY_DETECTION',
        'SIMILARITY_ANALYSIS',
        'TEMPORAL_ANALYSIS',
        'QUALITY_ASSESSMENT'
    ) NOT NULL,
    status ENUM('PENDING', 'RUNNING', 'COMPLETED', 'FAILED', 'CANCELLED', 'PAUSED') NOT NULL DEFAULT 'PENDING',
    description TEXT,
    result_data LONGTEXT,
    confidence_score DOUBLE,
    notes TEXT,
    started_at DATETIME,
    completed_at DATETIME,
    project_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    INDEX idx_analysis_tasks_project_id (project_id),
    INDEX idx_analysis_tasks_user_id (user_id),
    INDEX idx_analysis_tasks_status (status),
    INDEX idx_analysis_tasks_type (analysis_type),
    INDEX idx_analysis_tasks_created_at (created_at),
    
    FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- Add project_id to media_files table
ALTER TABLE media_files 
ADD COLUMN project_id BIGINT NOT NULL AFTER user_id,
ADD INDEX idx_media_files_project_id (project_id),
ADD FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE;

-- Add project_id and analysis_task_id to media_metadata table
ALTER TABLE media_metadata 
ADD COLUMN project_id BIGINT NOT NULL AFTER user_id,
ADD COLUMN analysis_task_id BIGINT AFTER project_id,
ADD INDEX idx_media_metadata_project_id (project_id),
ADD INDEX idx_media_metadata_analysis_task_id (analysis_task_id),
ADD FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE,
ADD FOREIGN KEY (analysis_task_id) REFERENCES analysis_tasks(id) ON DELETE SET NULL;
