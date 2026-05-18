-- MoodFM 数据库初始化脚本
-- 对应 PRD v2.0 数据模型

SET NAMES utf8mb4;
SET time_zone = '+08:00';

-- 用户表
CREATE TABLE IF NOT EXISTS users (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  username VARCHAR(50) NOT NULL,
  email VARCHAR(100) UNIQUE,
  phone VARCHAR(20) UNIQUE,
  password_hash VARCHAR(255) NOT NULL,
  avatar_url VARCHAR(500),
  status TINYINT DEFAULT 1 COMMENT '1正常 0软删除',
  role VARCHAR(20) DEFAULT 'USER' COMMENT 'USER / ADMIN',
  email_verified TINYINT(1) DEFAULT 0,
  login_fail_count INT DEFAULT 0,
  lock_until DATETIME,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_email (email),
  INDEX idx_phone (phone)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 平台账号绑定表
CREATE TABLE IF NOT EXISTS platform_bindings (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  platform VARCHAR(20) NOT NULL COMMENT 'netease / qqmusic / spotify',
  platform_user_id VARCHAR(100),
  platform_username VARCHAR(100),
  cookie_encrypted TEXT COMMENT 'AES-256-GCM 加密',
  is_default TINYINT DEFAULT 0,
  is_valid TINYINT DEFAULT 1,
  last_validated_at DATETIME,
  expires_at DATETIME,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_user_platform (user_id, platform),
  INDEX idx_user (user_id),
  CONSTRAINT fk_binding_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 心情会话表
CREATE TABLE IF NOT EXISTS mood_sessions (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  raw_input TEXT COMMENT '用户原始输入',
  mood_params JSON COMMENT 'AI 解析后的结构化参数',
  scene VARCHAR(50),
  started_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  ended_at DATETIME,
  duration_minutes INT,
  INDEX idx_user_started (user_id, started_at),
  CONSTRAINT fk_session_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 歌曲缓存表
CREATE TABLE IF NOT EXISTS songs (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  title VARCHAR(255) NOT NULL,
  artist VARCHAR(255) NOT NULL,
  album VARCHAR(255),
  duration_seconds INT,
  cover_url VARCHAR(500),
  features JSON COMMENT '流派/BPM/能量/语言等特征',
  qdrant_point_id VARCHAR(64) COMMENT 'M2 阶段',
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_title_artist (title(100), artist(100)),
  FULLTEXT INDEX ft_title_artist (title, artist) WITH PARSER ngram
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 平台歌曲映射表
CREATE TABLE IF NOT EXISTS platform_song_mapping (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  song_id BIGINT NOT NULL,
  platform VARCHAR(20) NOT NULL,
  platform_song_id VARCHAR(100) NOT NULL,
  available TINYINT DEFAULT 1,
  UNIQUE KEY uk_platform_song (platform, platform_song_id),
  INDEX idx_song (song_id),
  CONSTRAINT fk_mapping_song FOREIGN KEY (song_id) REFERENCES songs(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 播放记录表
CREATE TABLE IF NOT EXISTS play_records (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  session_id BIGINT,
  song_id BIGINT NOT NULL,
  platform VARCHAR(20) NOT NULL,
  played_seconds INT,
  total_seconds INT,
  action VARCHAR(20) COMMENT 'completed / skipped / liked / disliked',
  played_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_user_played (user_id, played_at),
  INDEX idx_session (session_id),
  CONSTRAINT fk_record_user FOREIGN KEY (user_id) REFERENCES users(id),
  CONSTRAINT fk_record_song FOREIGN KEY (song_id) REFERENCES songs(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 用户画像表
CREATE TABLE IF NOT EXISTS user_profiles (
  user_id BIGINT PRIMARY KEY,
  genre_weights JSON COMMENT '流派偏好权重',
  artist_weights JSON COMMENT '艺人偏好权重',
  language_preferences JSON,
  blacklist_artists JSON,
  blacklist_songs JSON,
  blacklist_keywords JSON,
  qdrant_user_vector_id VARCHAR(64),
  notification_prefs JSON COMMENT '通知偏好 {"weeklyReport":true,"cookieExpiry":true,"newFeatures":true}',
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_profile_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 周报表
CREATE TABLE IF NOT EXISTS weekly_reports (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  week_start DATE NOT NULL,
  week_end DATE NOT NULL,
  data JSON COMMENT '完整报告数据',
  ai_summary TEXT,
  generated_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_user_week (user_id, week_start),
  CONSTRAINT fk_report_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 全局黑名单表（管理员维护，歌手/歌曲/关键词）
CREATE TABLE IF NOT EXISTS global_blacklist (
  id         BIGINT PRIMARY KEY AUTO_INCREMENT,
  type       VARCHAR(20)  NOT NULL COMMENT 'artist | song | keyword',
  value      VARCHAR(255) NOT NULL,
  artist     VARCHAR(255),
  scope      VARCHAR(50),
  reason     VARCHAR(500),
  added_by   VARCHAR(100),
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_type (type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 管理员操作审计日志
CREATE TABLE IF NOT EXISTS admin_audit_log (
  id         BIGINT PRIMARY KEY AUTO_INCREMENT,
  operator   VARCHAR(100),
  operation  VARCHAR(500),
  module     VARCHAR(100),
  detail     TEXT,
  level      VARCHAR(20),
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 管理员通知表
CREATE TABLE IF NOT EXISTS admin_notification (
  id            BIGINT PRIMARY KEY AUTO_INCREMENT,
  title         VARCHAR(255) NOT NULL,
  body          TEXT,
  type          VARCHAR(50),
  target_group  VARCHAR(100),
  status        VARCHAR(20) DEFAULT 'draft' COMMENT 'draft | sent | scheduled',
  scheduled_at  DATETIME,
  sent_at       DATETIME,
  sent_count    INT DEFAULT 0,
  opened_count  INT DEFAULT 0,
  created_by    VARCHAR(100),
  created_at    DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at    DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- AI 场景模板表
CREATE TABLE IF NOT EXISTS scene_template (
  id         BIGINT PRIMARY KEY AUTO_INCREMENT,
  `key`      VARCHAR(50)  NOT NULL UNIQUE,
  name       VARCHAR(100) NOT NULL,
  cn         VARCHAR(100),
  active     TINYINT(1) DEFAULT 1,
  songs      INT DEFAULT 0,
  accuracy   VARCHAR(20),
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 初始管理员账号（username: admin / password: admin）
INSERT IGNORE INTO users (username, email, password_hash, status, role, email_verified)
VALUES ('admin', 'admin@moodfm.local', '$2a$10$ZKY8PVloCYgIuWy7necs9.35K7rtDqFo5llFau6pKCjSYezWYHDV.', 1, 'ADMIN', 1);

-- 反馈事件表
CREATE TABLE IF NOT EXISTS feedback_events (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  session_id BIGINT,
  song_id BIGINT,
  event_type VARCHAR(30) COMMENT 'play / skip / like / dislike / volume_up',
  event_data JSON,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_user_created (user_id, created_at),
  INDEX idx_session (session_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
