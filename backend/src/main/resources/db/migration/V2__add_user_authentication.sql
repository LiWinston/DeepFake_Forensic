-- 创建用户表
CREATE TABLE IF NOT EXISTS users (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(50) NOT NULL UNIQUE,
    email VARCHAR(100) NOT NULL UNIQUE,
    password VARCHAR(100) NOT NULL,
    first_name VARCHAR(50),
    last_name VARCHAR(50),
    role ENUM('USER', 'ADMIN') NOT NULL DEFAULT 'USER',
    status ENUM('ACTIVE', 'INACTIVE', 'LOCKED', 'EXPIRED') NOT NULL DEFAULT 'ACTIVE',
    last_login_at DATETIME,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- 为现有的 media_files 表添加 user_id 列（如果不存在）
ALTER TABLE media_files 
ADD COLUMN IF NOT EXISTS user_id BIGINT,
ADD CONSTRAINT fk_media_files_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;

-- 为现有的 media_metadata 表添加 user_id 列（如果不存在）
ALTER TABLE media_metadata 
ADD COLUMN IF NOT EXISTS user_id BIGINT,
ADD CONSTRAINT fk_media_metadata_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;

-- 创建索引以提高查询性能
CREATE INDEX IF NOT EXISTS idx_media_files_user_id ON media_files(user_id);
CREATE INDEX IF NOT EXISTS idx_media_metadata_user_id ON media_metadata(user_id);
CREATE INDEX IF NOT EXISTS idx_media_files_user_md5 ON media_files(user_id, file_md5);
CREATE INDEX IF NOT EXISTS idx_media_metadata_user_md5 ON media_metadata(user_id, file_md5);

-- 创建默认管理员用户（密码为 admin123，需要BCrypt加密）
-- $2a$10$7JKRgBZrOE7QhWv0IzF8YOkxK.SY8j8MxYwCxXKH.eQ.ZBGvXBdJm 是 "admin123" 的BCrypt哈希
INSERT IGNORE INTO users (username, email, password, first_name, last_name, role, status) 
VALUES ('admin', 'admin@forensic.com', '$2a$10$7JKRgBZrOE7QhWv0IzF8YOkxK.SY8j8MxYwCxXKH.eQ.ZBGvXBdJm', 'Admin', 'User', 'ADMIN', 'ACTIVE');

-- 创建默认普通用户（密码为 user123）
-- $2a$10$OqT7PdFqP8uD7sGjvqFY1OLf5DHDJhU5gBgFfZTq7rXKH8GxF3wNi 是 "user123" 的BCrypt哈希
INSERT IGNORE INTO users (username, email, password, first_name, last_name, role, status) 
VALUES ('user', 'user@forensic.com', '$2a$10$OqT7PdFqP8uD7sGjvqFY1OLf5DHDJhU5gBgFfZTq7rXKH8GxF3wNi', 'Test', 'User', 'USER', 'ACTIVE');
