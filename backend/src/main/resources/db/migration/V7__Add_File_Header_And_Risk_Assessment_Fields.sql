-- Week 7 requirements: Add file header analysis and risk assessment fields to media_metadata table

-- File Header/Signature Analysis Fields
ALTER TABLE media_metadata ADD COLUMN detected_file_format VARCHAR(50);
ALTER TABLE media_metadata ADD COLUMN expected_file_format VARCHAR(50);
ALTER TABLE media_metadata ADD COLUMN file_format_match BOOLEAN;
ALTER TABLE media_metadata ADD COLUMN file_signature_hex VARCHAR(32);
ALTER TABLE media_metadata ADD COLUMN file_integrity_status VARCHAR(50);

-- Risk Assessment Fields
ALTER TABLE media_metadata ADD COLUMN risk_score INTEGER;
ALTER TABLE media_metadata ADD COLUMN assessment_conclusion VARCHAR(500);

-- Container/Frame Analysis Fields (for future Week 7 requirements)
ALTER TABLE media_metadata ADD COLUMN container_integrity_verified BOOLEAN;
ALTER TABLE media_metadata ADD COLUMN container_analysis_results TEXT;

-- Add comments for documentation
COMMENT ON COLUMN media_metadata.detected_file_format IS 'File format detected from signature analysis (e.g., JPEG, PNG, MP4)';
COMMENT ON COLUMN media_metadata.expected_file_format IS 'Expected file format based on file extension';
COMMENT ON COLUMN media_metadata.file_format_match IS 'Whether detected format matches expected format';
COMMENT ON COLUMN media_metadata.file_signature_hex IS 'Hexadecimal representation of file signature';
COMMENT ON COLUMN media_metadata.file_integrity_status IS 'File integrity status: INTACT, FORMAT_MISMATCH, UNKNOWN_FORMAT, ANALYSIS_FAILED';
COMMENT ON COLUMN media_metadata.risk_score IS 'Risk assessment score from 0-100 (100 = highest risk)';
COMMENT ON COLUMN media_metadata.assessment_conclusion IS 'Human-readable assessment conclusion';
COMMENT ON COLUMN media_metadata.container_integrity_verified IS 'Whether container structure integrity has been verified';
COMMENT ON COLUMN media_metadata.container_analysis_results IS 'Detailed container analysis results';

-- Create index on commonly queried fields for performance
CREATE INDEX idx_media_metadata_risk_score ON media_metadata(risk_score);
CREATE INDEX idx_media_metadata_file_integrity ON media_metadata(file_integrity_status);
CREATE INDEX idx_media_metadata_detected_format ON media_metadata(detected_file_format);
