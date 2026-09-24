-- =============================================================================
-- V1 基线脚本（幂等）
-- 说明：本机已经存在 bili_clone 库和下面这些表，本文件用 CREATE TABLE IF NOT EXISTS
--       描述「已有结构」，已存在的表不会被改动、更不会删表，因此可以安全地在老库上执行。
--       如果你是全新的机器（没有 bili_clone 库），执行本文件 + V2 即可得到完整结构。
-- 字符集：utf8mb4 / utf8mb4_0900_ai_ci（与现有库保持一致）
-- =============================================================================

CREATE TABLE IF NOT EXISTS `users` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '用户ID',
  `username` varchar(50) NOT NULL COMMENT '登录账号（唯一）',
  `email` varchar(120) DEFAULT NULL COMMENT '邮箱（唯一，用于邮箱验证码登录）',
  `password_hash` varchar(100) DEFAULT NULL COMMENT '密码哈希（BCrypt，绝不存明文）',
  `nickname` varchar(50) NOT NULL COMMENT '昵称',
  `avatar_url` varchar(255) DEFAULT NULL COMMENT '头像图片地址（上传的）',
  `avatar_emoji` varchar(16) DEFAULT '😀' COMMENT '没上传头像时用的 emoji',
  `sign` varchar(120) DEFAULT NULL COMMENT '个性签名',
  `level` tinyint NOT NULL DEFAULT '1' COMMENT '等级 1-6',
  `exp` int NOT NULL DEFAULT '0' COMMENT '当前等级已获得经验',
  `exp_max` int NOT NULL DEFAULT '200' COMMENT '升到下一级需要的经验',
  `coins` int NOT NULL DEFAULT '0' COMMENT '硬币',
  `b_coins` int NOT NULL DEFAULT '0' COMMENT 'B币',
  `following_count` int NOT NULL DEFAULT '0' COMMENT '关注数',
  `follower_count` int NOT NULL DEFAULT '0' COMMENT '粉丝数',
  `like_count` int NOT NULL DEFAULT '0' COMMENT '获赞数',
  `vip` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否大会员',
  `vip_label` varchar(30) DEFAULT NULL COMMENT '大会员文案',
  `last_checkin` date DEFAULT NULL COMMENT '上次签到日期（判断今天是否已签到）',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '1正常 0禁用',
  `last_login_at` datetime DEFAULT NULL COMMENT '最后登录时间',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_users_username` (`username`),
  UNIQUE KEY `uk_users_email` (`email`),
  KEY `idx_users_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户表';

CREATE TABLE IF NOT EXISTS `categories` (
  `id` int unsigned NOT NULL AUTO_INCREMENT,
  `code` varchar(40) NOT NULL COMMENT '英文标识（路由 / 筛选用）',
  `name` varchar(40) NOT NULL COMMENT '中文名',
  `emoji` varchar(16) DEFAULT NULL COMMENT '图标',
  `node_type` varchar(20) NOT NULL DEFAULT 'VIDEO' COMMENT 'VIDEO/GAME/LIVE/BANGUMI/ARTICLE/ACTIVITY/COMMUNITY',
  `parent_id` int unsigned DEFAULT NULL COMMENT '上级分区（一级为 NULL）',
  `route_path` varchar(120) DEFAULT NULL COMMENT '前端路由路径',
  `sort_order` int NOT NULL DEFAULT '0' COMMENT '排序',
  `is_placeholder` tinyint(1) NOT NULL DEFAULT '0' COMMENT '1=预留节点（占位页）',
  `enabled` tinyint(1) NOT NULL DEFAULT '1' COMMENT '是否启用',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_categories_code` (`code`),
  KEY `idx_categories_parent` (`parent_id`),
  CONSTRAINT `fk_categories_parent` FOREIGN KEY (`parent_id`) REFERENCES `categories` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='内容分区/预留节点';

CREATE TABLE IF NOT EXISTS `videos` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `bvid` varchar(20) DEFAULT NULL COMMENT 'BV号（展示用）',
  `title` varchar(200) NOT NULL COMMENT '标题',
  `category_id` int unsigned DEFAULT NULL COMMENT '所属分区',
  `cover_path` varchar(255) DEFAULT NULL COMMENT '真实封面帧地址',
  `cover_color1` varchar(16) DEFAULT '#FF9A9E' COMMENT '占位封面渐变色1',
  `cover_color2` varchar(16) DEFAULT '#FAD0C4' COMMENT '占位封面渐变色2',
  `cover_emoji` varchar(16) DEFAULT '🎬' COMMENT '占位封面图标',
  `cover_text` varchar(60) DEFAULT NULL COMMENT '占位封面短文字',
  `video_path` varchar(255) DEFAULT NULL COMMENT '视频文件地址（可空）',
  `poster_path` varchar(255) DEFAULT NULL COMMENT '播放器封面图',
  `duration` int NOT NULL DEFAULT '0' COMMENT '时长（秒）',
  `width` int DEFAULT NULL COMMENT '视频宽',
  `height` int DEFAULT NULL COMMENT '视频高',
  `file_size` bigint DEFAULT NULL COMMENT '文件大小（字节）',
  `views` bigint NOT NULL DEFAULT '0' COMMENT '播放量',
  `danmaku_count` int NOT NULL DEFAULT '0' COMMENT '弹幕数',
  `like_count` int NOT NULL DEFAULT '0',
  `coin_count` int NOT NULL DEFAULT '0',
  `fav_count` int NOT NULL DEFAULT '0',
  `share_count` int NOT NULL DEFAULT '0',
  `reply_count` int NOT NULL DEFAULT '0',
  `up_id` bigint unsigned DEFAULT NULL COMMENT 'UP主（users.id）',
  `up_name` varchar(50) DEFAULT NULL COMMENT 'UP主昵称快照',
  `up_avatar` varchar(16) DEFAULT NULL COMMENT 'UP主头像 emoji',
  `tags` varchar(255) DEFAULT NULL COMMENT '标签（英文逗号分隔）',
  `description` text COMMENT '简介',
  `status` varchar(20) NOT NULL DEFAULT 'PUBLISHED' COMMENT 'PUBLISHED/REVIEWING/DRAFT',
  `pub_time` datetime DEFAULT NULL COMMENT '发布时间',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_videos_bvid` (`bvid`),
  KEY `idx_videos_category` (`category_id`),
  KEY `idx_videos_up` (`up_id`),
  KEY `idx_videos_status_pub` (`status`,`pub_time`),
  KEY `idx_videos_views` (`views`),
  CONSTRAINT `fk_videos_category` FOREIGN KEY (`category_id`) REFERENCES `categories` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_videos_up` FOREIGN KEY (`up_id`) REFERENCES `users` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='视频';

CREATE TABLE IF NOT EXISTS `bangumi` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `title` varchar(120) NOT NULL,
  `cover_path` varchar(255) DEFAULT NULL,
  `color1` varchar(16) DEFAULT '#A18CD1',
  `color2` varchar(16) DEFAULT '#FBC2EB',
  `emoji` varchar(16) DEFAULT '📺',
  `status` varchar(20) NOT NULL DEFAULT '连载中',
  `area` varchar(20) DEFAULT '日本',
  `episode` int NOT NULL DEFAULT '0',
  `total_episode` int NOT NULL DEFAULT '0',
  `followers` int NOT NULL DEFAULT '0',
  `score` decimal(3,1) NOT NULL DEFAULT '0.0',
  `tag` varchar(30) DEFAULT NULL,
  `description` varchar(500) DEFAULT NULL,
  `pub_time` varchar(40) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_bangumi_area_status` (`area`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='番剧';

CREATE TABLE IF NOT EXISTS `captcha_codes` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `captcha_key` varchar(64) NOT NULL COMMENT '验证码标识（前端回传）',
  `code` varchar(10) NOT NULL COMMENT '验证码答案',
  `purpose` varchar(30) NOT NULL DEFAULT 'LOGIN' COMMENT '用途：LOGIN/REGISTER/RESET',
  `client_ip` varchar(64) DEFAULT NULL COMMENT '请求方 IP（用于频率限制）',
  `used` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否已使用（用过即失效）',
  `expires_at` datetime NOT NULL COMMENT '过期时间（默认 5 分钟）',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_captcha_key` (`captcha_key`),
  KEY `idx_captcha_expires` (`expires_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='图形验证码';

CREATE TABLE IF NOT EXISTS `email_verification_codes` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `email` varchar(120) NOT NULL COMMENT '接收邮箱',
  `code` varchar(10) NOT NULL COMMENT '6 位验证码',
  `purpose` varchar(30) NOT NULL DEFAULT 'LOGIN' COMMENT 'LOGIN/REGISTER/RESET',
  `client_ip` varchar(64) DEFAULT NULL COMMENT '请求方 IP',
  `used` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否已使用',
  `expires_at` datetime NOT NULL COMMENT '过期时间（默认 10 分钟）',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_evc_email_purpose` (`email`,`purpose`),
  KEY `idx_evc_expires` (`expires_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='邮箱验证码';

CREATE TABLE IF NOT EXISTS `qr_login_sessions` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `qr_id` varchar(64) NOT NULL COMMENT '二维码ID（前端轮询用）',
  `status` varchar(20) NOT NULL DEFAULT 'WAITING' COMMENT 'WAITING/SCANNED/CONFIRMED/EXPIRED/CANCELED',
  `user_id` bigint unsigned DEFAULT NULL COMMENT '确认登录的用户',
  `token` varchar(80) DEFAULT NULL COMMENT '确认后下发的令牌',
  `client_ip` varchar(64) DEFAULT NULL COMMENT '生成二维码的 IP',
  `scanned_at` datetime DEFAULT NULL COMMENT '被扫描时间',
  `confirmed_at` datetime DEFAULT NULL COMMENT '确认时间',
  `expires_at` datetime NOT NULL COMMENT '过期时间（默认 2 分钟）',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_qr_id` (`qr_id`),
  KEY `idx_qr_status` (`status`),
  KEY `fk_qr_user` (`user_id`),
  CONSTRAINT `fk_qr_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='二维码登录会话';

CREATE TABLE IF NOT EXISTS `user_tokens` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `user_id` bigint unsigned NOT NULL COMMENT '用户ID',
  `token` varchar(80) NOT NULL COMMENT '登录令牌（随机字符串）',
  `login_type` varchar(20) NOT NULL DEFAULT 'PASSWORD' COMMENT 'PASSWORD/EMAIL/QR',
  `expires_at` datetime NOT NULL COMMENT '过期时间',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tokens_token` (`token`),
  KEY `idx_tokens_user` (`user_id`),
  CONSTRAINT `fk_tokens_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='登录令牌';

CREATE TABLE IF NOT EXISTS `danmaku` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `video_id` bigint unsigned NOT NULL COMMENT '视频ID',
  `user_id` bigint unsigned DEFAULT NULL COMMENT '发送者',
  `content` varchar(100) NOT NULL COMMENT '弹幕内容',
  `time_sec` decimal(8,1) NOT NULL DEFAULT '0.0' COMMENT '出现在第几秒',
  `mode` tinyint NOT NULL DEFAULT '1' COMMENT '1滚动 4底部 5顶部',
  `color` varchar(16) NOT NULL DEFAULT '#FFFFFF',
  `font_size` tinyint NOT NULL DEFAULT '25',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_danmaku_video_time` (`video_id`,`time_sec`),
  CONSTRAINT `fk_danmaku_video` FOREIGN KEY (`video_id`) REFERENCES `videos` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='弹幕';

CREATE TABLE IF NOT EXISTS `comments` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `video_id` bigint unsigned NOT NULL,
  `user_id` bigint unsigned DEFAULT NULL,
  `parent_id` bigint unsigned DEFAULT NULL COMMENT '回复的评论ID（楼中楼）',
  `content` varchar(500) NOT NULL,
  `like_count` int NOT NULL DEFAULT '0',
  `location` varchar(30) DEFAULT NULL COMMENT 'IP属地',
  `up_liked` tinyint(1) NOT NULL DEFAULT '0' COMMENT 'UP主是否点赞',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_comments_video` (`video_id`,`created_at`),
  KEY `idx_comments_parent` (`parent_id`),
  CONSTRAINT `fk_comments_parent` FOREIGN KEY (`parent_id`) REFERENCES `comments` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_comments_video` FOREIGN KEY (`video_id`) REFERENCES `videos` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='评论';

CREATE TABLE IF NOT EXISTS `dynamics` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `up_id` bigint unsigned DEFAULT NULL,
  `up_name` varchar(50) DEFAULT NULL,
  `up_avatar` varchar(16) DEFAULT NULL,
  `content` varchar(500) NOT NULL,
  `emoji` varchar(16) DEFAULT NULL,
  `color1` varchar(16) DEFAULT '#FBC2EB',
  `color2` varchar(16) DEFAULT '#A6C1EE',
  `video_id` bigint unsigned DEFAULT NULL,
  `like_count` int NOT NULL DEFAULT '0',
  `reply_count` int NOT NULL DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_dynamics_up` (`up_id`),
  KEY `idx_dynamics_time` (`created_at`),
  KEY `fk_dynamics_video` (`video_id`),
  CONSTRAINT `fk_dynamics_up` FOREIGN KEY (`up_id`) REFERENCES `users` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_dynamics_video` FOREIGN KEY (`video_id`) REFERENCES `videos` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='动态';

CREATE TABLE IF NOT EXISTS `follows` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `user_id` bigint unsigned NOT NULL COMMENT '关注发起人',
  `up_id` bigint unsigned NOT NULL COMMENT '被关注的 UP 主',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_follow` (`user_id`,`up_id`),
  KEY `fk_follow_up` (`up_id`),
  CONSTRAINT `fk_follow_up` FOREIGN KEY (`up_id`) REFERENCES `users` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_follow_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='关注关系';

CREATE TABLE IF NOT EXISTS `favorite_folders` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `user_id` bigint unsigned NOT NULL,
  `name` varchar(60) NOT NULL DEFAULT '默认收藏夹',
  `is_default` tinyint(1) NOT NULL DEFAULT '0',
  `is_public` tinyint(1) NOT NULL DEFAULT '1',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_folder_user` (`user_id`),
  CONSTRAINT `fk_folder_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='收藏夹';

CREATE TABLE IF NOT EXISTS `favorite_items` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `folder_id` bigint unsigned NOT NULL,
  `video_id` bigint unsigned NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_fav_item` (`folder_id`,`video_id`),
  KEY `fk_favitem_video` (`video_id`),
  CONSTRAINT `fk_favitem_folder` FOREIGN KEY (`folder_id`) REFERENCES `favorite_folders` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_favitem_video` FOREIGN KEY (`video_id`) REFERENCES `videos` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='收藏内容';

CREATE TABLE IF NOT EXISTS `messages` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `user_id` bigint unsigned NOT NULL,
  `type` varchar(20) NOT NULL DEFAULT 'SYSTEM' COMMENT 'SYSTEM/REPLY/LIKE/AT',
  `title` varchar(120) NOT NULL,
  `content` varchar(500) DEFAULT NULL,
  `link` varchar(200) DEFAULT NULL COMMENT '点击跳转的路由',
  `is_read` tinyint(1) NOT NULL DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_msg_user_read` (`user_id`,`is_read`),
  CONSTRAINT `fk_msg_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='消息中心';

CREATE TABLE IF NOT EXISTS `user_actions` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `user_id` bigint unsigned NOT NULL,
  `video_id` bigint unsigned NOT NULL,
  `action_type` varchar(20) NOT NULL COMMENT 'LIKE/COIN/FAV',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_action` (`user_id`,`video_id`,`action_type`),
  KEY `idx_action_video` (`video_id`),
  CONSTRAINT `fk_action_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_action_video` FOREIGN KEY (`video_id`) REFERENCES `videos` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户互动记录';

CREATE TABLE IF NOT EXISTS `video_uploads` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `user_id` bigint unsigned NOT NULL,
  `video_id` bigint unsigned DEFAULT NULL COMMENT '审核通过后关联的视频',
  `title` varchar(200) NOT NULL,
  `category_id` int unsigned DEFAULT NULL,
  `file_name` varchar(255) DEFAULT NULL,
  `file_path` varchar(255) DEFAULT NULL,
  `cover_path` varchar(255) DEFAULT NULL,
  `status` varchar(20) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/UPLOADING/REVIEWING/PUBLISHED/REJECTED',
  `progress` int NOT NULL DEFAULT '0' COMMENT '上传进度 0-100',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_uploads_user` (`user_id`),
  CONSTRAINT `fk_uploads_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户投稿记录';

CREATE TABLE IF NOT EXISTS `watch_history` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `user_id` bigint unsigned NOT NULL,
  `video_id` bigint unsigned NOT NULL,
  `progress_sec` int NOT NULL DEFAULT '0' COMMENT '看到第几秒',
  `watched_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_history_user_video` (`user_id`,`video_id`),
  KEY `idx_history_time` (`watched_at`),
  KEY `fk_history_video` (`video_id`),
  CONSTRAINT `fk_history_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_history_video` FOREIGN KEY (`video_id`) REFERENCES `videos` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='观看历史';

CREATE TABLE IF NOT EXISTS `bangumi_follows` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `user_id` bigint unsigned NOT NULL,
  `bangumi_id` bigint unsigned NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_bg_follow` (`user_id`,`bangumi_id`),
  KEY `fk_bgfollow_bangumi` (`bangumi_id`),
  CONSTRAINT `fk_bgfollow_bangumi` FOREIGN KEY (`bangumi_id`) REFERENCES `bangumi` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_bgfollow_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='追番';

-- 视频全量视图（老库里已有，用 OR REPLACE 保证一致，不改动任何数据）
CREATE OR REPLACE VIEW `v_video_full` AS
SELECT v.`id`, v.`bvid`, v.`title`, v.`category_id`, v.`cover_path`, v.`cover_color1`,
       v.`cover_color2`, v.`cover_emoji`, v.`cover_text`, v.`video_path`, v.`poster_path`,
       v.`duration`, v.`width`, v.`height`, v.`file_size`, v.`views`, v.`danmaku_count`,
       v.`like_count`, v.`coin_count`, v.`fav_count`, v.`share_count`, v.`reply_count`,
       v.`up_id`, v.`up_name`, v.`up_avatar`, v.`tags`, v.`description`, v.`status`,
       v.`pub_time`, v.`created_at`, v.`updated_at`,
       c.`name` AS `category_name`, c.`code` AS `category_code`
FROM `videos` v LEFT JOIN `categories` c ON c.`id` = v.`category_id`;
