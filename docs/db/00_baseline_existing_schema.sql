
/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
 SET NAMES utf8mb4 ;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;
SET @MYSQLDUMP_TEMP_LOG_BIN = @@SESSION.SQL_LOG_BIN;
SET @@SESSION.SQL_LOG_BIN= 0;
SET @@GLOBAL.GTID_PURGED=/*!80000 '+'*/ '937c1085-c2a3-11f0-ab9e-345a60228f78:1-3114';
DROP TABLE IF EXISTS `bangumi`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
 SET character_set_client = utf8mb4 ;
CREATE TABLE `bangumi` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `title` varchar(120) NOT NULL,
  `cover_path` varchar(255) DEFAULT NULL,
  `color1` varchar(16) DEFAULT '#A18CD1',
  `color2` varchar(16) DEFAULT '#FBC2EB',
  `emoji` varchar(16) DEFAULT 0xF09F93BA,
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
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `bangumi_follows`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
 SET character_set_client = utf8mb4 ;
CREATE TABLE `bangumi_follows` (
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
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `captcha_codes`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
 SET character_set_client = utf8mb4 ;
CREATE TABLE `captcha_codes` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `captcha_key` varchar(64) NOT NULL COMMENT '楠岃瘉鐮佹爣璇嗭紙鍓嶇?鍥炰紶锛',
  `code` varchar(10) NOT NULL COMMENT '楠岃瘉鐮佺瓟妗',
  `purpose` varchar(30) NOT NULL DEFAULT 'LOGIN' COMMENT '鐢ㄩ?锛歀OGIN/REGISTER/RESET',
  `client_ip` varchar(64) DEFAULT NULL COMMENT '璇锋眰鏂?IP锛堢敤浜庨?鐜囬檺鍒讹級',
  `used` tinyint(1) NOT NULL DEFAULT '0' COMMENT '鏄?惁宸蹭娇鐢?紙鐢ㄨ繃鍗冲け鏁堬級',
  `expires_at` datetime NOT NULL COMMENT '杩囨湡鏃堕棿锛堥粯璁?5 鍒嗛挓锛',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_captcha_key` (`captcha_key`),
  KEY `idx_captcha_expires` (`expires_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='鍥惧舰楠岃瘉鐮';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `categories`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
 SET character_set_client = utf8mb4 ;
CREATE TABLE `categories` (
  `id` int unsigned NOT NULL AUTO_INCREMENT,
  `code` varchar(40) NOT NULL COMMENT '鑻辨枃鏍囪瘑锛堣矾鐢?/ 绛涢?鐢?級',
  `name` varchar(40) NOT NULL COMMENT '涓?枃鍚',
  `emoji` varchar(16) DEFAULT NULL COMMENT '鍥炬爣',
  `node_type` varchar(20) NOT NULL DEFAULT 'VIDEO' COMMENT 'VIDEO/GAME/LIVE/BANGUMI/ARTICLE/ACTIVITY/COMMUNITY',
  `parent_id` int unsigned DEFAULT NULL COMMENT '涓婄骇鍒嗗尯锛堜竴绾т负 NULL锛',
  `route_path` varchar(120) DEFAULT NULL COMMENT '鍓嶇?璺?敱璺?緞',
  `sort_order` int NOT NULL DEFAULT '0' COMMENT '鎺掑簭',
  `is_placeholder` tinyint(1) NOT NULL DEFAULT '0' COMMENT '1=棰勭暀鑺傜偣锛堝崰浣嶉〉锛',
  `enabled` tinyint(1) NOT NULL DEFAULT '1' COMMENT '鏄?惁鍚?敤',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_categories_code` (`code`),
  KEY `idx_categories_parent` (`parent_id`),
  CONSTRAINT `fk_categories_parent` FOREIGN KEY (`parent_id`) REFERENCES `categories` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB AUTO_INCREMENT=46 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='鍐呭?鍒嗗尯/棰勭暀鑺傜偣';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `comments`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
 SET character_set_client = utf8mb4 ;
CREATE TABLE `comments` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `video_id` bigint unsigned NOT NULL,
  `user_id` bigint unsigned DEFAULT NULL,
  `parent_id` bigint unsigned DEFAULT NULL COMMENT '鍥炲?鐨勮瘎璁篒D锛堟ゼ涓?ゼ锛',
  `content` varchar(500) NOT NULL,
  `like_count` int NOT NULL DEFAULT '0',
  `location` varchar(30) DEFAULT NULL COMMENT 'IP灞炲湴',
  `up_liked` tinyint(1) NOT NULL DEFAULT '0' COMMENT 'UP涓绘槸鍚︾偣璧',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_comments_video` (`video_id`,`created_at`),
  KEY `idx_comments_parent` (`parent_id`),
  CONSTRAINT `fk_comments_parent` FOREIGN KEY (`parent_id`) REFERENCES `comments` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_comments_video` FOREIGN KEY (`video_id`) REFERENCES `videos` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='璇勮?';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `danmaku`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
 SET character_set_client = utf8mb4 ;
CREATE TABLE `danmaku` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `video_id` bigint unsigned NOT NULL COMMENT '瑙嗛?ID',
  `user_id` bigint unsigned DEFAULT NULL COMMENT '鍙戦?鑰',
  `content` varchar(100) NOT NULL COMMENT '寮瑰箷鍐呭?',
  `time_sec` decimal(8,1) NOT NULL DEFAULT '0.0' COMMENT '鍑虹幇鍦ㄧ?鍑犵?',
  `mode` tinyint NOT NULL DEFAULT '1' COMMENT '1婊氬姩 4搴曢儴 5椤堕儴',
  `color` varchar(16) NOT NULL DEFAULT '#FFFFFF',
  `font_size` tinyint NOT NULL DEFAULT '25',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_danmaku_video_time` (`video_id`,`time_sec`),
  CONSTRAINT `fk_danmaku_video` FOREIGN KEY (`video_id`) REFERENCES `videos` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='寮瑰箷';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `dynamics`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
 SET character_set_client = utf8mb4 ;
CREATE TABLE `dynamics` (
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
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `email_verification_codes`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
 SET character_set_client = utf8mb4 ;
CREATE TABLE `email_verification_codes` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `email` varchar(120) NOT NULL COMMENT '鎺ユ敹閭??',
  `code` varchar(10) NOT NULL COMMENT '6 浣嶉獙璇佺爜',
  `purpose` varchar(30) NOT NULL DEFAULT 'LOGIN' COMMENT 'LOGIN/REGISTER/RESET',
  `client_ip` varchar(64) DEFAULT NULL COMMENT '璇锋眰鏂?IP',
  `used` tinyint(1) NOT NULL DEFAULT '0' COMMENT '鏄?惁宸蹭娇鐢',
  `expires_at` datetime NOT NULL COMMENT '杩囨湡鏃堕棿锛堥粯璁?10 鍒嗛挓锛',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_evc_email_purpose` (`email`,`purpose`),
  KEY `idx_evc_expires` (`expires_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='閭??楠岃瘉鐮';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `favorite_folders`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
 SET character_set_client = utf8mb4 ;
CREATE TABLE `favorite_folders` (
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
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `favorite_items`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
 SET character_set_client = utf8mb4 ;
CREATE TABLE `favorite_items` (
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
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `follows`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
 SET character_set_client = utf8mb4 ;
CREATE TABLE `follows` (
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
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `messages`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
 SET character_set_client = utf8mb4 ;
CREATE TABLE `messages` (
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
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `qr_login_sessions`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
 SET character_set_client = utf8mb4 ;
CREATE TABLE `qr_login_sessions` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `qr_id` varchar(64) NOT NULL COMMENT '浜岀淮鐮両D锛堝墠绔?疆璇㈢敤锛',
  `status` varchar(20) NOT NULL DEFAULT 'WAITING' COMMENT 'WAITING/SCANNED/CONFIRMED/EXPIRED/CANCELED',
  `user_id` bigint unsigned DEFAULT NULL COMMENT '纭??鐧诲綍鐨勭敤鎴',
  `token` varchar(80) DEFAULT NULL COMMENT '纭??鍚庝笅鍙戠殑浠ょ墝',
  `client_ip` varchar(64) DEFAULT NULL COMMENT '鐢熸垚浜岀淮鐮佺殑 IP',
  `scanned_at` datetime DEFAULT NULL COMMENT '琚?壂鎻忔椂闂',
  `confirmed_at` datetime DEFAULT NULL COMMENT '纭??鏃堕棿',
  `expires_at` datetime NOT NULL COMMENT '杩囨湡鏃堕棿锛堥粯璁?2 鍒嗛挓锛',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_qr_id` (`qr_id`),
  KEY `idx_qr_status` (`status`),
  KEY `fk_qr_user` (`user_id`),
  CONSTRAINT `fk_qr_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='浜岀淮鐮佺櫥褰曚細璇';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `user_actions`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
 SET character_set_client = utf8mb4 ;
CREATE TABLE `user_actions` (
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='鐢ㄦ埛浜掑姩璁板綍';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `user_tokens`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
 SET character_set_client = utf8mb4 ;
CREATE TABLE `user_tokens` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `user_id` bigint unsigned NOT NULL COMMENT '鐢ㄦ埛ID',
  `token` varchar(80) NOT NULL COMMENT '鐧诲綍浠ょ墝锛堥殢鏈哄瓧绗︿覆锛',
  `login_type` varchar(20) NOT NULL DEFAULT 'PASSWORD' COMMENT 'PASSWORD/EMAIL/QR',
  `expires_at` datetime NOT NULL COMMENT '杩囨湡鏃堕棿',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tokens_token` (`token`),
  KEY `idx_tokens_user` (`user_id`),
  CONSTRAINT `fk_tokens_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='鐧诲綍浠ょ墝';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `users`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
 SET character_set_client = utf8mb4 ;
CREATE TABLE `users` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '鐢ㄦ埛ID',
  `username` varchar(50) NOT NULL COMMENT '鐧诲綍璐﹀彿锛堝敮涓?級',
  `email` varchar(120) DEFAULT NULL COMMENT '閭??锛堝敮涓?紝鐢ㄤ簬閭??楠岃瘉鐮佺櫥褰曪級',
  `password_hash` varchar(100) DEFAULT NULL COMMENT '瀵嗙爜鍝堝笇锛圔Crypt锛岀粷涓嶅瓨鏄庢枃锛',
  `nickname` varchar(50) NOT NULL COMMENT '鏄电О',
  `avatar_url` varchar(255) DEFAULT NULL COMMENT '澶村儚鍥剧墖鍦板潃锛堜笂浼犵殑锛',
  `avatar_emoji` varchar(16) DEFAULT '馃槑' COMMENT '娌′笂浼犲ご鍍忔椂鐢ㄧ殑 emoji',
  `sign` varchar(120) DEFAULT NULL COMMENT '涓??绛惧悕',
  `level` tinyint NOT NULL DEFAULT '1' COMMENT '绛夌骇 1-6',
  `exp` int NOT NULL DEFAULT '0' COMMENT '褰撳墠绛夌骇宸茶幏寰楃粡楠',
  `exp_max` int NOT NULL DEFAULT '200' COMMENT '鍗囧埌涓嬩竴绾ч渶瑕佺殑缁忛獙',
  `coins` int NOT NULL DEFAULT '0' COMMENT '纭?竵',
  `b_coins` int NOT NULL DEFAULT '0' COMMENT 'B甯',
  `following_count` int NOT NULL DEFAULT '0' COMMENT '鍏虫敞鏁',
  `follower_count` int NOT NULL DEFAULT '0' COMMENT '绮変笣鏁',
  `like_count` int NOT NULL DEFAULT '0' COMMENT '鑾疯禐鏁',
  `vip` tinyint(1) NOT NULL DEFAULT '0' COMMENT '鏄?惁澶т細鍛',
  `vip_label` varchar(30) DEFAULT NULL COMMENT '澶т細鍛樻枃妗',
  `last_checkin` date DEFAULT NULL COMMENT '涓婃?绛惧埌鏃ユ湡锛堝垽鏂?粖澶╂槸鍚︾?鍒帮級',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '1姝ｅ父 0绂佺敤',
  `last_login_at` datetime DEFAULT NULL COMMENT '鏈?悗鐧诲綍鏃堕棿',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_users_username` (`username`),
  UNIQUE KEY `uk_users_email` (`email`),
  KEY `idx_users_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='鐢ㄦ埛琛';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `v_video_full`;
/*!50001 DROP VIEW IF EXISTS `v_video_full`*/;
SET @saved_cs_client     = @@character_set_client;
SET character_set_client = utf8mb4;
/*!50001 CREATE VIEW `v_video_full` AS SELECT 
 1 AS `id`,
 1 AS `bvid`,
 1 AS `title`,
 1 AS `category_id`,
 1 AS `cover_path`,
 1 AS `cover_color1`,
 1 AS `cover_color2`,
 1 AS `cover_emoji`,
 1 AS `cover_text`,
 1 AS `video_path`,
 1 AS `poster_path`,
 1 AS `duration`,
 1 AS `width`,
 1 AS `height`,
 1 AS `file_size`,
 1 AS `views`,
 1 AS `danmaku_count`,
 1 AS `like_count`,
 1 AS `coin_count`,
 1 AS `fav_count`,
 1 AS `share_count`,
 1 AS `reply_count`,
 1 AS `up_id`,
 1 AS `up_name`,
 1 AS `up_avatar`,
 1 AS `tags`,
 1 AS `description`,
 1 AS `status`,
 1 AS `pub_time`,
 1 AS `created_at`,
 1 AS `updated_at`,
 1 AS `category_name`,
 1 AS `category_code`*/;
SET character_set_client = @saved_cs_client;
DROP TABLE IF EXISTS `video_uploads`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
 SET character_set_client = utf8mb4 ;
CREATE TABLE `video_uploads` (
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
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `videos`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
 SET character_set_client = utf8mb4 ;
CREATE TABLE `videos` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `bvid` varchar(20) DEFAULT NULL COMMENT 'BV鍙凤紙灞曠ず鐢?級',
  `title` varchar(200) NOT NULL COMMENT '鏍囬?',
  `category_id` int unsigned DEFAULT NULL COMMENT '鎵?睘鍒嗗尯',
  `cover_path` varchar(255) DEFAULT NULL COMMENT '鐪熷疄灏侀潰甯у湴鍧',
  `cover_color1` varchar(16) DEFAULT '#FF9A9E' COMMENT '鍗犱綅灏侀潰娓愬彉鑹?',
  `cover_color2` varchar(16) DEFAULT '#FAD0C4' COMMENT '鍗犱綅灏侀潰娓愬彉鑹?',
  `cover_emoji` varchar(16) DEFAULT '馃幀' COMMENT '鍗犱綅灏侀潰鍥炬爣',
  `cover_text` varchar(60) DEFAULT NULL COMMENT '鍗犱綅灏侀潰鐭?枃瀛',
  `video_path` varchar(255) DEFAULT NULL COMMENT '瑙嗛?鏂囦欢鍦板潃锛堝彲绌猴級',
  `poster_path` varchar(255) DEFAULT NULL COMMENT '鎾?斁鍣ㄥ皝闈㈠浘',
  `duration` int NOT NULL DEFAULT '0' COMMENT '鏃堕暱锛堢?锛',
  `width` int DEFAULT NULL COMMENT '瑙嗛?瀹',
  `height` int DEFAULT NULL COMMENT '瑙嗛?楂',
  `file_size` bigint DEFAULT NULL COMMENT '鏂囦欢澶у皬锛堝瓧鑺傦級',
  `views` bigint NOT NULL DEFAULT '0' COMMENT '鎾?斁閲',
  `danmaku_count` int NOT NULL DEFAULT '0' COMMENT '寮瑰箷鏁',
  `like_count` int NOT NULL DEFAULT '0',
  `coin_count` int NOT NULL DEFAULT '0',
  `fav_count` int NOT NULL DEFAULT '0',
  `share_count` int NOT NULL DEFAULT '0',
  `reply_count` int NOT NULL DEFAULT '0',
  `up_id` bigint unsigned DEFAULT NULL COMMENT 'UP涓伙紙users.id锛',
  `up_name` varchar(50) DEFAULT NULL COMMENT 'UP涓绘樀绉板揩鐓',
  `up_avatar` varchar(16) DEFAULT NULL COMMENT 'UP涓诲ご鍍?emoji',
  `tags` varchar(255) DEFAULT NULL COMMENT '鏍囩?锛堣嫳鏂囬?鍙峰垎闅旓級',
  `description` text COMMENT '绠?粙',
  `status` varchar(20) NOT NULL DEFAULT 'PUBLISHED' COMMENT 'PUBLISHED/REVIEWING/DRAFT',
  `pub_time` datetime DEFAULT NULL COMMENT '鍙戝竷鏃堕棿',
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='瑙嗛?';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `watch_history`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
 SET character_set_client = utf8mb4 ;
CREATE TABLE `watch_history` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `user_id` bigint unsigned NOT NULL,
  `video_id` bigint unsigned NOT NULL,
  `progress_sec` int NOT NULL DEFAULT '0' COMMENT '鐪嬪埌绗?嚑绉',
  `watched_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_history_user_video` (`user_id`,`video_id`),
  KEY `idx_history_time` (`watched_at`),
  KEY `fk_history_video` (`video_id`),
  CONSTRAINT `fk_history_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_history_video` FOREIGN KEY (`video_id`) REFERENCES `videos` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='瑙傜湅鍘嗗彶';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!50001 DROP VIEW IF EXISTS `v_video_full`*/;
/*!50001 SET @saved_cs_client          = @@character_set_client */;
/*!50001 SET @saved_cs_results         = @@character_set_results */;
/*!50001 SET @saved_col_connection     = @@collation_connection */;
/*!50001 SET character_set_client      = utf8mb4 */;
/*!50001 SET character_set_results     = utf8mb4 */;
/*!50001 SET collation_connection      = utf8mb4_0900_ai_ci */;
/*!50001 CREATE ALGORITHM=UNDEFINED */
/*!50013 DEFINER=`root`@`localhost` SQL SECURITY DEFINER */
/*!50001 VIEW `v_video_full` AS select `v`.`id` AS `id`,`v`.`bvid` AS `bvid`,`v`.`title` AS `title`,`v`.`category_id` AS `category_id`,`v`.`cover_path` AS `cover_path`,`v`.`cover_color1` AS `cover_color1`,`v`.`cover_color2` AS `cover_color2`,`v`.`cover_emoji` AS `cover_emoji`,`v`.`cover_text` AS `cover_text`,`v`.`video_path` AS `video_path`,`v`.`poster_path` AS `poster_path`,`v`.`duration` AS `duration`,`v`.`width` AS `width`,`v`.`height` AS `height`,`v`.`file_size` AS `file_size`,`v`.`views` AS `views`,`v`.`danmaku_count` AS `danmaku_count`,`v`.`like_count` AS `like_count`,`v`.`coin_count` AS `coin_count`,`v`.`fav_count` AS `fav_count`,`v`.`share_count` AS `share_count`,`v`.`reply_count` AS `reply_count`,`v`.`up_id` AS `up_id`,`v`.`up_name` AS `up_name`,`v`.`up_avatar` AS `up_avatar`,`v`.`tags` AS `tags`,`v`.`description` AS `description`,`v`.`status` AS `status`,`v`.`pub_time` AS `pub_time`,`v`.`created_at` AS `created_at`,`v`.`updated_at` AS `updated_at`,`c`.`name` AS `category_name`,`c`.`code` AS `category_code` from (`videos` `v` left join `categories` `c` on((`c`.`id` = `v`.`category_id`))) */;
/*!50001 SET character_set_client      = @saved_cs_client */;
/*!50001 SET character_set_results     = @saved_cs_results */;
/*!50001 SET collation_connection      = @saved_col_connection */;
SET @@SESSION.SQL_LOG_BIN = @MYSQLDUMP_TEMP_LOG_BIN;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

