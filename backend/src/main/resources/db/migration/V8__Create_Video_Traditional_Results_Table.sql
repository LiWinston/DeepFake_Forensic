-- Create table for video traditional analysis results
CREATE TABLE IF NOT EXISTS video_traditional_analysis_results (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    file_md5 VARCHAR(32) NOT NULL,
    method VARCHAR(32) NOT NULL,
    artifacts_json TEXT,
    result_json TEXT,
    success BOOLEAN,
    error_message TEXT,
    analysis_task_id BIGINT,
    user_id BIGINT,
    project_id BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_vtrad_task FOREIGN KEY (analysis_task_id) REFERENCES analysis_tasks(id),
    CONSTRAINT fk_vtrad_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_vtrad_project FOREIGN KEY (project_id) REFERENCES projects(id)
);

CREATE INDEX IF NOT EXISTS idx_vtrad_md5 ON video_traditional_analysis_results(file_md5);
CREATE INDEX IF NOT EXISTS idx_vtrad_method ON video_traditional_analysis_results(method);
