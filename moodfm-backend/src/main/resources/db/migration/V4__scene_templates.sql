CREATE TABLE IF NOT EXISTS `scene_template` (
  `id`         BIGINT       NOT NULL AUTO_INCREMENT,
  `key`        VARCHAR(50)  NOT NULL,
  `name`       VARCHAR(50)  NOT NULL,
  `cn`         VARCHAR(100) DEFAULT NULL,
  `active`     TINYINT(1)   NOT NULL DEFAULT 1,
  `songs`      INT          NOT NULL DEFAULT 0,
  `accuracy`   VARCHAR(10)  DEFAULT '—',
  `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_key` (`key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT IGNORE INTO `scene_template` (`key`, `name`, `cn`, `active`, `songs`, `accuracy`) VALUES
('commute',  '通勤模式', '上下班路途',   1, 240, '88%'),
('study',    '专注学习', '高专注力场景', 1, 185, '91%'),
('workout',  '运动跑步', '高能量运动',   1, 312, '86%'),
('writing',  '创作写作', '创意工作流',   1, 167, '84%'),
('sleep',    '睡前放松', '深夜慢节奏',   1, 198, '93%'),
('party',    '派对社交', '高能量社交',   1, 276, '79%'),
('midnight', '深夜电台', '午夜氛围',     0, 144, '85%');
