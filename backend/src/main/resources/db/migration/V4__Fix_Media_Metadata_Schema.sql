-- Fix media_metadata table schema to ensure all technical metadata fields exist
-- This migration ensures compatibility between JPA entity and actual database schema

-- Check if media_metadata table exists, create if it doesn't
CREATE TABLE IF NOT EXISTS media_metadata (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    file_md5 VARCHAR(32) NOT NULL,
    sha256_hash VARCHAR(64),
    sha1_hash VARCHAR(40),
    
    -- EXIF Data fields
    camera_model VARCHAR(100),
    camera_make VARCHAR(100),
    date_taken DATETIME,
    gps_location VARCHAR(200),
    gps_latitude DOUBLE,
    gps_longitude DOUBLE,
    image_width INT,
    image_height INT,
    orientation INT,
    color_space VARCHAR(50),
    
    -- Video Metadata fields
    video_duration INT,
    frame_rate DOUBLE,
    video_codec VARCHAR(50),
    audio_codec VARCHAR(50),
    bit_rate INT,
    
    -- Technical Metadata fields
    mime_type VARCHAR(100),
    file_format VARCHAR(50),
    compression_level INT,
    raw_metadata LONGTEXT,
    
    -- Analysis Results
    extraction_status ENUM('PENDING', 'SUCCESS', 'PARTIAL', 'FAILED') NOT NULL DEFAULT 'PENDING',
    analysis_notes LONGTEXT,
    suspicious_indicators LONGTEXT,
    
    -- Timestamps
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    -- Foreign Keys
    user_id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    analysis_task_id BIGINT,
    
    -- Indexes
    INDEX idx_media_metadata_file_md5 (file_md5),
    INDEX idx_media_metadata_user_id (user_id),
    INDEX idx_media_metadata_project_id (project_id),
    INDEX idx_media_metadata_analysis_task_id (analysis_task_id),
    INDEX idx_media_metadata_extraction_status (extraction_status),
    INDEX idx_media_metadata_created_at (created_at),
    
    -- Unique constraint for user-specific metadata
    UNIQUE KEY uk_metadata_file_user (file_md5, user_id)
);

-- Add missing columns if they don't exist (for existing installations)
-- Technical metadata fields that might be missing
ALTER TABLE media_metadata 
ADD COLUMN IF NOT EXISTS mime_type VARCHAR(100),
ADD COLUMN IF NOT EXISTS file_format VARCHAR(50),
ADD COLUMN IF NOT EXISTS compression_level INT;

-- Ensure image dimensions are properly defined
ALTER TABLE media_metadata 
MODIFY COLUMN IF EXISTS image_width INT,
MODIFY COLUMN IF EXISTS image_height INT;

-- Add indexes for forensic analysis queries
CREATE INDEX IF NOT EXISTS idx_metadata_camera_make ON media_metadata(camera_make);
CREATE INDEX IF NOT EXISTS idx_metadata_camera_model ON media_metadata(camera_model);
CREATE INDEX IF NOT EXISTS idx_metadata_date_taken ON media_metadata(date_taken);
CREATE INDEX IF NOT EXISTS idx_metadata_dimensions ON media_metadata(image_width, image_height);
CREATE INDEX IF NOT EXISTS idx_metadata_gps ON media_metadata(gps_latitude, gps_longitude);
CREATE INDEX IF NOT EXISTS idx_metadata_suspicious ON media_metadata(suspicious_indicators(255));
