-- Fix TEXT columns that may need to store large analysis results
-- TEXT: 64KB max (65,535 bytes)
-- MEDIUMTEXT: 16MB max (16,777,215 bytes)
-- LONGTEXT: 4GB max (4,294,967,295 bytes)

-- For video analysis, TEMPORAL/FLOW results can be very large with per-frame data
-- Using MEDIUMTEXT (16MB) should be sufficient for most cases while avoiding unnecessary overhead

-- Ensure analysis_tasks results column is LONGTEXT (should already be, but verify)
ALTER TABLE analysis_tasks 
    MODIFY COLUMN results LONGTEXT,
    MODIFY COLUMN parameters LONGTEXT,
    MODIFY COLUMN error_message LONGTEXT;

-- Fix video_traditional_analysis_results columns from TEXT to MEDIUMTEXT
ALTER TABLE video_traditional_analysis_results
    MODIFY COLUMN artifacts_json MEDIUMTEXT,
    MODIFY COLUMN result_json MEDIUMTEXT,
    MODIFY COLUMN error_message MEDIUMTEXT;

-- Fix traditional_analysis_results columns from TEXT to MEDIUMTEXT
ALTER TABLE traditional_analysis_results
    MODIFY COLUMN analysis_summary MEDIUMTEXT,
    MODIFY COLUMN detailed_findings MEDIUMTEXT,
    MODIFY COLUMN error_message MEDIUMTEXT;

-- Add noise analysis columns that were missed in V6
ALTER TABLE traditional_analysis_results
    ADD COLUMN IF NOT EXISTS noise_confidence_score DOUBLE DEFAULT NULL AFTER lighting_inconsistencies,
    ADD COLUMN IF NOT EXISTS noise_result_path VARCHAR(255) DEFAULT NULL AFTER noise_confidence_score,
    ADD COLUMN IF NOT EXISTS noise_suspicious_regions INT DEFAULT NULL AFTER noise_result_path;

