# =============================================================================
# 完整建库建表脚本（只有「本机完全没有 bili_clone 库」时才需要）
#
# 本机已经有库的同学请忽略本文件，直接用 docs/db/03_incremental_idempotent.sql 做增量。
# 后端启动时也会自动执行 db/migration/V1 + V2（等价于本文件 + 增量），
# 所以正常情况下**什么都不用手工执行**。
#
# 执行：
#   mysql -uroot -p123456 < docs/db/04_create_database.sql
# =============================================================================

CREATE DATABASE IF NOT EXISTS `bili_clone`
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_0900_ai_ci;

USE `bili_clone`;

-- 建表语句与 backend/src/main/resources/db/migration/V1__baseline.sql 完全一致，
-- 由后端启动时自动执行；此处仅作为「手工建库」的入口。
--
-- 执行顺序建议：
--   1) 本文件（建库）
--   2) docs/db/01_V1_baseline.sql（建表 + 视图，幂等）
--   3) docs/db/03_incremental_idempotent.sql（补认证 / 媒体 / 节点 / 用户中心相关字段和表，幂等）
--
-- 演示数据（16 位 UP 主 + 80 条稿件 + 1.5 万条弹幕 + 评论 + 番剧 + 动态 + 内容节点）
-- 由后端首次启动时自动写入（DataSyncService），不需要 SQL 脚本。
SELECT '数据库已就绪，请继续执行 01_V1_baseline.sql 与 03_incremental_idempotent.sql' AS next_step;
