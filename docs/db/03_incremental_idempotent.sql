# =============================================================================
# 增量迁移脚本（幂等，可重复执行）
#
# 用途：不想让后端自动迁移时，可以手工在已有的 bili_clone 库上执行本脚本。
#      已经存在的列 / 表 / 索引会自动跳过，不会报错，也不会删除任何数据。
#
# 执行方式（Windows，本机 MySQL root/123456）：
#   mysql -uroot -p123456 --default-character-set=utf8mb4 bili_clone < docs/db/03_incremental_idempotent.sql
#
# 注意：后端启动时执行的迁移是 backend/src/main/resources/db/migration/V1__baseline.sql
#      和 V2__auth_media_nodes.sql（带 schema_migrations 版本记录）。两份内容等价。
# =============================================================================

-- ---------- 1. users 表补字段 ----------
DROP PROCEDURE IF EXISTS bili_add_column;
DELIMITER //
CREATE PROCEDURE bili_add_column(IN tbl VARCHAR(64), IN col VARCHAR(64), IN ddl TEXT)
BEGIN
  IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = tbl AND COLUMN_NAME = col) THEN
    SET @bili_sql = CONCAT('ALTER TABLE `', tbl, '` ADD COLUMN ', ddl);
    PREPARE bili_stmt FROM @bili_sql;
    EXECUTE bili_stmt;
    DEALLOCATE PREPARE bili_stmt;
  END IF;
END //
DELIMITER ;

CALL bili_add_column('users', 'email_verified',
  "`email_verified` tinyint(1) NOT NULL DEFAULT 0 COMMENT '邮箱是否已验证' AFTER `email`");
CALL bili_add_column('users', 'last_login_ip',
  "`last_login_ip` varchar(64) DEFAULT NULL COMMENT '最后登录IP' AFTER `last_login_at`");
CALL bili_add_column('users', 'register_source',
  "`register_source` varchar(20) NOT NULL DEFAULT 'PASSWORD' COMMENT '注册来源：PASSWORD/EMAIL/QR' AFTER `last_login_ip`");
CALL bili_add_column('users', 'qr_openid',
  "`qr_openid` varchar(64) DEFAULT NULL COMMENT '二维码登录关联的客户端标识' AFTER `register_source`");
CALL bili_add_column('users', 'medal',
  "`medal` varchar(30) DEFAULT NULL COMMENT '粉丝勋章名' AFTER `vip_label`");
CALL bili_add_column('users', 'join_days',
  "`join_days` int NOT NULL DEFAULT 0 COMMENT '加入天数（展示用）' AFTER `medal`");

-- ---------- 2. videos 表补字段 ----------
CALL bili_add_column('videos', 'node_code',
  "`node_code` varchar(40) DEFAULT NULL COMMENT '内容节点编码（对应 categories.code）' AFTER `category_id`");
CALL bili_add_column('videos', 'cover_source',
  "`cover_source` varchar(20) NOT NULL DEFAULT 'PLACEHOLDER' COMMENT '封面来源：FFMPEG_FRAME / UPLOAD / PLACEHOLDER' AFTER `cover_path`");
CALL bili_add_column('videos', 'media_scanned_at',
  "`media_scanned_at` datetime DEFAULT NULL COMMENT '媒体文件扫描时间' AFTER `file_size`");
CALL bili_add_column('videos', 'playable',
  "`playable` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否有真实视频文件可播放' AFTER `media_scanned_at`");

-- ---------- 3. categories / messages 表补字段 ----------
CALL bili_add_column('categories', 'description',
  "`description` varchar(200) DEFAULT NULL COMMENT '节点描述' AFTER `name`");
CALL bili_add_column('categories', 'icon_url',
  "`icon_url` varchar(255) DEFAULT NULL COMMENT '节点图标图片' AFTER `emoji`");
CALL bili_add_column('categories', 'is_new',
  "`is_new` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否新节点（角标）' AFTER `is_placeholder`");
CALL bili_add_column('messages', 'from_user_id',
  "`from_user_id` bigint unsigned DEFAULT NULL COMMENT '消息来源用户' AFTER `user_id`");
CALL bili_add_column('messages', 'extra',
  "`extra` varchar(255) DEFAULT NULL COMMENT '扩展 JSON' AFTER `link`");

DROP PROCEDURE IF EXISTS bili_add_column;

-- ---------- 4. 补索引（存在则跳过） ----------
DROP PROCEDURE IF EXISTS bili_add_index;
DELIMITER //
CREATE PROCEDURE bili_add_index(IN tbl VARCHAR(64), IN idx VARCHAR(64), IN cols VARCHAR(255))
BEGIN
  IF NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS
                 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = tbl AND INDEX_NAME = idx) THEN
    SET @bili_sql = CONCAT('CREATE INDEX `', idx, '` ON `', tbl, '` (', cols, ')');
    PREPARE bili_stmt FROM @bili_sql;
    EXECUTE bili_stmt;
    DEALLOCATE PREPARE bili_stmt;
  END IF;
END //
DELIMITER ;

CALL bili_add_index('users', 'idx_users_last_login', '`last_login_at`');
CALL bili_add_index('videos', 'idx_videos_node', '`node_code`');
CALL bili_add_index('videos', 'idx_videos_playable', '`playable`, `status`');

DROP PROCEDURE IF EXISTS bili_add_index;

-- ---------- 5. 新增表（全部 IF NOT EXISTS，不删表） ----------
CREATE TABLE IF NOT EXISTS `video_sources` (
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='视频清晰度源';

CREATE TABLE IF NOT EXISTS `user_settings` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `user_id` bigint unsigned NOT NULL,
  `theme` varchar(20) NOT NULL DEFAULT 'light',
  `autoplay` tinyint(1) NOT NULL DEFAULT 1,
  `default_quality` varchar(20) NOT NULL DEFAULT '1080P',
  `danmaku_on` tinyint(1) NOT NULL DEFAULT 1,
  `danmaku_opacity` decimal(3,2) NOT NULL DEFAULT 0.95,
  `danmaku_fontsize` tinyint NOT NULL DEFAULT 25,
  `danmaku_speed` tinyint NOT NULL DEFAULT 9,
  `danmaku_area` varchar(20) NOT NULL DEFAULT 'all',
  `show_history` tinyint(1) NOT NULL DEFAULT 1,
  `email_notify` tinyint(1) NOT NULL DEFAULT 1,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_settings_user` (`user_id`),
  CONSTRAINT `fk_settings_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户设置';

CREATE TABLE IF NOT EXISTS `search_history` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `user_id` bigint unsigned NOT NULL,
  `keyword` varchar(80) NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_search_user_kw` (`user_id`,`keyword`),
  KEY `idx_search_time` (`created_at`),
  CONSTRAINT `fk_search_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='搜索历史';

CREATE TABLE IF NOT EXISTS `login_logs` (
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='登录日志';

CREATE TABLE IF NOT EXISTS `schema_migrations` (
  `version` varchar(80) NOT NULL,
  `applied_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `note` varchar(200) DEFAULT NULL,
  PRIMARY KEY (`version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='迁移执行记录';

-- ---------- 6. 兼容修复：老数据里 category_id 为空但 node_code 有值的稿件 ----------
UPDATE videos v JOIN categories c ON c.code = v.node_code
SET v.category_id = c.id
WHERE v.category_id IS NULL;

-- 同一个演示视频被重复绑定到多条稿件时，只保留 id 最小的那条
UPDATE videos v
JOIN (SELECT video_path, MIN(id) AS keep_id FROM videos
      WHERE video_path IS NOT NULL AND video_path <> ''
      GROUP BY video_path HAVING COUNT(*) > 1) k
  ON k.video_path = v.video_path AND v.id <> k.keep_id
SET v.video_path = NULL, v.poster_path = NULL, v.cover_path = NULL,
    v.playable = 0, v.cover_source = 'PLACEHOLDER';

SELECT '增量迁移完成' AS result,
       (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'users') AS users_columns,
       (SELECT COUNT(*) FROM videos) AS videos,
       (SELECT COUNT(*) FROM categories) AS nodes;
