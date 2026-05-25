-- 全局音乐黑名单（歌手 / 歌曲 / 关键词）
CREATE TABLE IF NOT EXISTS `global_blacklist` (
  `id`         BIGINT       NOT NULL AUTO_INCREMENT,
  `type`       VARCHAR(20)  NOT NULL COMMENT 'artist | song | keyword',
  `value`      VARCHAR(255) NOT NULL COMMENT '歌手名 / 歌曲名 / 关键词',
  `artist`     VARCHAR(255) DEFAULT NULL COMMENT '歌曲对应艺人（type=song 时用）',
  `scope`      VARCHAR(50)  DEFAULT NULL COMMENT '关键词过滤范围（type=keyword 时用）',
  `reason`     VARCHAR(500) DEFAULT NULL,
  `added_by`   VARCHAR(50)  NOT NULL DEFAULT 'admin',
  `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_type` (`type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 管理员发送的通知记录
CREATE TABLE IF NOT EXISTS `admin_notification` (
  `id`           BIGINT       NOT NULL AUTO_INCREMENT,
  `title`        VARCHAR(255) NOT NULL,
  `body`         TEXT         NOT NULL,
  `type`         VARCHAR(30)  NOT NULL DEFAULT 'system' COMMENT 'system | weekly | feature | push',
  `target_group` VARCHAR(50)  NOT NULL DEFAULT 'all'    COMMENT 'all | expiring | new | inactive',
  `status`       VARCHAR(20)  NOT NULL DEFAULT 'draft'  COMMENT 'draft | scheduled | sent',
  `scheduled_at` DATETIME     DEFAULT NULL,
  `sent_at`      DATETIME     DEFAULT NULL,
  `sent_count`   INT          NOT NULL DEFAULT 0,
  `opened_count` INT          NOT NULL DEFAULT 0,
  `created_by`   VARCHAR(50)  NOT NULL DEFAULT 'admin',
  `created_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 管理员操作审计日志
CREATE TABLE IF NOT EXISTS `admin_audit_log` (
  `id`         BIGINT       NOT NULL AUTO_INCREMENT,
  `operator`   VARCHAR(50)  NOT NULL DEFAULT 'admin',
  `operation`  VARCHAR(255) NOT NULL,
  `module`     VARCHAR(50)  NOT NULL,
  `detail`     TEXT         DEFAULT NULL,
  `level`      VARCHAR(10)  NOT NULL DEFAULT 'info' COMMENT 'ok | info | warn | danger',
  `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 在 platform_bindings 表添加 cookie_expires_at 字段（V1 未含此列）
-- 注意：MySQL 8.0 不支持 ADD COLUMN IF NOT EXISTS（MariaDB 扩展），Flyway 已保证每个 version 只跑一次
ALTER TABLE `platform_bindings`
  ADD COLUMN `cookie_expires_at` DATETIME DEFAULT NULL COMMENT 'Cookie 过期时间';
