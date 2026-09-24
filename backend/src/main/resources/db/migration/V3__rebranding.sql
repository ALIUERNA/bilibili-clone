-- ============================================================================
-- V3：品牌更名为「a哩a哩」+ 清理演示数据里的 AI 内容（幂等，可重复执行）
--
-- 说明：V1/V2 建表后，演示数据是由 DataStore 写入的；本脚本负责把
--       已经落库的旧品牌文案 / AI 文案同步更新，避免只改源码、库里仍是旧数据。
-- ============================================================================

-- ---------------- 品牌：用户与消息 ----------------
UPDATE users SET nickname = 'a哩a哩萌新'
 WHERE nickname = '哔哩哔哩萌新';

UPDATE messages SET title = '欢迎来到a哩a哩'
 WHERE title LIKE '%哔哩哔哩%';

UPDATE messages SET content = REPLACE(content, '哔哩哔哩', 'a哩a哩')
 WHERE content LIKE '%哔哩哔哩%';

-- ---------------- AI：标题 / 标签 / 图标 ----------------
UPDATE videos SET title = '实测：把旧笔记本改造成家庭服务器，结果如何'
 WHERE title = '实测：让AI帮我写了一天代码，结果如何';

UPDATE videos SET tags = REPLACE(tags, 'AI写代码', '独立游戏开发')
 WHERE tags LIKE '%AI写代码%';

UPDATE bangumi SET emoji = '⚙️'
 WHERE title = '机械之心' AND emoji = '🤖';

UPDATE categories SET emoji = '🎤'
 WHERE code = 'music_vocaloid' AND emoji = '🤖';
