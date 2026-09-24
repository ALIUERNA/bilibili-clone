-- =============================================================================
-- V2 增量脚本：在原结构上补齐「登录认证 / 视频媒体 / 内容节点 / 用户中心」能力
-- 原则：只新增字段和表，不改名、不删列、不删表、不动已有数据。
-- 迁移执行器（SchemaMigrator）会记录 schema_migrations，保证只会执行一次。
-- 手动执行请用 docs/db/03_incremental_idempotent.sql（带存在性判断，可重复执行）。
-- =============================================================================

-- ---------- 1. users：邮箱验证状态、最近登录 IP、账号来源 ----------
ALTER TABLE `users`
  ADD COLUMN `email_verified` tinyint(1) NOT NULL DEFAULT 0 COMMENT '邮箱是否已验证' AFTER `email`,
  ADD COLUMN `last_login_ip` varchar(64) DEFAULT NULL COMMENT '最后登录IP' AFTER `last_login_at`,
  ADD COLUMN `register_source` varchar(20) NOT NULL DEFAULT 'PASSWORD' COMMENT '注册来源：PASSWORD/EMAIL/QR' AFTER `last_login_ip`,
  ADD COLUMN `qr_openid` varchar(64) DEFAULT NULL COMMENT '二维码登录关联的客户端标识' AFTER `register_source`,
  ADD COLUMN `medal` varchar(30) DEFAULT NULL COMMENT '粉丝勋章名' AFTER `vip_label`,
  ADD COLUMN `join_days` int NOT NULL DEFAULT 0 COMMENT '加入天数（展示用，可空）' AFTER `medal`;

CREATE INDEX `idx_users_last_login` ON `users` (`last_login_at`);

-- ---------- 2. videos：内容节点、封面来源、媒体扫描信息 ----------
ALTER TABLE `videos`
  ADD COLUMN `node_code` varchar(40) DEFAULT NULL COMMENT '内容节点编码（对应 categories.code）' AFTER `category_id`,
  ADD COLUMN `cover_source` varchar(20) NOT NULL DEFAULT 'PLACEHOLDER'
      COMMENT '封面来源：FFMPEG_FRAME / UPLOAD / PLACEHOLDER' AFTER `cover_path`,
  ADD COLUMN `media_scanned_at` datetime DEFAULT NULL COMMENT '媒体文件扫描时间' AFTER `file_size`,
  ADD COLUMN `playable` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否有真实视频文件可播放' AFTER `media_scanned_at`;

CREATE INDEX `idx_videos_node` ON `videos` (`node_code`);
CREATE INDEX `idx_videos_playable` ON `videos` (`playable`, `status`);

-- ---------- 3. 新增：视频多清晰度源（可选，一个视频可以挂多个文件） ----------
CREATE TABLE `video_sources` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `video_id` bigint unsigned NOT NULL,
  `quality` varchar(20) NOT NULL DEFAULT '720P' COMMENT '清晰度标签',
  `file_path` varchar(255) NOT NULL COMMENT '视频文件相对路径',
  `mime_type` varchar(60) NOT NULL DEFAULT 'video/mp4',
  `width` int DEFAULT NULL,
  `height` int DEFAULT NULL,
  `file_size` bigint DEFAULT NULL,
  `is_default` tinyint(1) NOT NULL DEFAULT 0,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_video_quality` (`video_id`,`quality`),
  CONSTRAINT `fk_vsource_video` FOREIGN KEY (`video_id`) REFERENCES `videos` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='视频清晰度源';

-- ---------- 4. 新增：用户设置（设置页） ----------
CREATE TABLE `user_settings` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `user_id` bigint unsigned NOT NULL,
  `theme` varchar(20) NOT NULL DEFAULT 'light' COMMENT 'light/dark/system',
  `autoplay` tinyint(1) NOT NULL DEFAULT 1 COMMENT '自动播放',
  `default_quality` varchar(20) NOT NULL DEFAULT '1080P',
  `danmaku_on` tinyint(1) NOT NULL DEFAULT 1 COMMENT '默认开启弹幕',
  `danmaku_opacity` decimal(3,2) NOT NULL DEFAULT 0.95,
  `danmaku_fontsize` tinyint NOT NULL DEFAULT 25,
  `danmaku_speed` tinyint NOT NULL DEFAULT 9,
  `danmaku_area` varchar(20) NOT NULL DEFAULT 'all' COMMENT 'all/top/bottom',
  `show_history` tinyint(1) NOT NULL DEFAULT 1 COMMENT '公开观看历史',
  `email_notify` tinyint(1) NOT NULL DEFAULT 1,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_settings_user` (`user_id`),
  CONSTRAINT `fk_settings_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户设置';

-- ---------- 5. 新增：搜索历史 ----------
CREATE TABLE `search_history` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `user_id` bigint unsigned NOT NULL,
  `keyword` varchar(80) NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_search_user_kw` (`user_id`,`keyword`),
  KEY `idx_search_time` (`created_at`),
  CONSTRAINT `fk_search_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='搜索历史';

-- ---------- 6. 新增：登录日志（安全审计，可选） ----------
CREATE TABLE `login_logs` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `user_id` bigint unsigned DEFAULT NULL,
  `username` varchar(50) DEFAULT NULL,
  `login_type` varchar(20) NOT NULL DEFAULT 'PASSWORD',
  `success` tinyint(1) NOT NULL DEFAULT 1,
  `fail_reason` varchar(120) DEFAULT NULL,
  `client_ip` varchar(64) DEFAULT NULL,
  `user_agent` varchar(255) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_login_user_time` (`user_id`,`created_at`),
  CONSTRAINT `fk_loginlog_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='登录日志';

-- ---------- 7. 补齐 categories 的展示字段（内容节点用） ----------
ALTER TABLE `categories`
  ADD COLUMN `description` varchar(200) DEFAULT NULL COMMENT '节点描述' AFTER `name`,
  ADD COLUMN `icon_url` varchar(255) DEFAULT NULL COMMENT '节点图标图片' AFTER `emoji`,
  ADD COLUMN `is_new` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否新节点（角标）' AFTER `is_placeholder`;

-- ---------- 8. 补齐 messages 的跳转与扩展字段 ----------
ALTER TABLE `messages`
  ADD COLUMN `from_user_id` bigint unsigned DEFAULT NULL COMMENT '消息来源用户' AFTER `user_id`,
  ADD COLUMN `extra` varchar(255) DEFAULT NULL COMMENT '扩展 JSON' AFTER `link`;
