package com.bili.demo.db;

import com.bili.demo.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Component;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 内容相关的数据库访问层：内容节点（分区）、视频、弹幕、评论、番剧、动态、互动记录、清晰度源。
 */
@Component
public class ContentRepository {

    private static final Logger log = LoggerFactory.getLogger(ContentRepository.class);
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final Database db;

    public ContentRepository(Database db) {
        this.db = db;
    }

    private JdbcTemplate j() {
        return db.jdbc();
    }

    // ==================================================================
    // categories（内容节点）
    // ==================================================================

    private static final RowMapper<ContentNode> NODE_MAPPER = (rs, i) -> {
        ContentNode n = new ContentNode();
        n.id = rs.getInt("id");
        n.code = rs.getString("code");
        n.name = rs.getString("name");
        n.emoji = rs.getString("emoji");
        n.description = rs.getString("description");
        n.nodeType = rs.getString("node_type");
        int parent = rs.getInt("parent_id");
        n.parentId = rs.wasNull() ? null : parent;
        n.routePath = rs.getString("route_path");
        n.sortOrder = rs.getInt("sort_order");
        n.placeholder = rs.getBoolean("is_placeholder");
        n.isNew = rs.getBoolean("is_new");
        n.enabled = rs.getBoolean("enabled");
        return n;
    };

    private static final String NODE_COLS =
            "id, code, name, emoji, description, node_type, parent_id, route_path, sort_order, is_placeholder, is_new, enabled";

    public List<ContentNode> nodes() {
        try {
            return j().query("SELECT " + NODE_COLS + " FROM categories WHERE enabled = 1 ORDER BY sort_order, id",
                    NODE_MAPPER);
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    public Map<String, Integer> categoryIdByCode() {
        Map<String, Integer> map = new LinkedHashMap<>();
        try {
            for (Map<String, Object> row : j().queryForList("SELECT id, code, name FROM categories")) {
                map.put(String.valueOf(row.get("code")), ((Number) row.get("id")).intValue());
            }
        } catch (Exception ignored) {
        }
        return map;
    }

    /**
     * code 和中文名都能查到的分类 id 表。
     * 演示数据里视频只存了中文分区名（如「动画」），写入 videos.category_id 时要用它来映射。
     */
    public Map<String, Integer> categoryIdByCodeOrName() {
        Map<String, Integer> map = new LinkedHashMap<>();
        try {
            for (Map<String, Object> row : j().queryForList("SELECT id, code, name FROM categories")) {
                int id = ((Number) row.get("id")).intValue();
                map.put(String.valueOf(row.get("code")), id);
                Object name = row.get("name");
                if (name != null) {
                    map.putIfAbsent(String.valueOf(name), id);
                }
            }
        } catch (Exception ignored) {
        }
        return map;
    }

    /** 修复历史数据：category_id 为空但 node_code 有值的稿件，按 node_code 补上分类 */
    public int repairCategoryLinks() {
        try {
            return j().update("""
                    UPDATE videos v JOIN categories c ON c.code = v.node_code
                    SET v.category_id = c.id
                    WHERE v.category_id IS NULL
                    """);
        } catch (Exception e) {
            return 0;
        }
    }

    public Map<Integer, ContentNode> categoryById() {
        Map<Integer, ContentNode> map = new LinkedHashMap<>();
        for (ContentNode n : nodes()) {
            map.put(n.id, n);
        }
        return map;
    }

    public void insertNode(ContentNode n) {
        j().update("""
                INSERT INTO categories(code, name, emoji, description, node_type, parent_id, route_path,
                                       sort_order, is_placeholder, is_new, enabled)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1)
                ON DUPLICATE KEY UPDATE name = VALUES(name), emoji = VALUES(emoji),
                                        description = VALUES(description), node_type = VALUES(node_type),
                                        route_path = VALUES(route_path), sort_order = VALUES(sort_order),
                                        is_placeholder = VALUES(is_placeholder), is_new = VALUES(is_new)
                """, n.code, n.name, n.emoji, n.description, n.nodeType, n.parentId, n.routePath,
                n.sortOrder, n.placeholder ? 1 : 0, n.isNew ? 1 : 0);
    }

    /** 各节点的稿件数量（内容节点页展示用） */
    public Map<String, Integer> videoCountByNode() {
        Map<String, Integer> map = new LinkedHashMap<>();
        try {
            for (Map<String, Object> row : j().queryForList("""
                    SELECT c.code, COUNT(v.id) AS n FROM categories c
                    LEFT JOIN videos v ON v.category_id = c.id AND v.status = 'PUBLISHED'
                    GROUP BY c.code
                    """)) {
                map.put(String.valueOf(row.get("code")), ((Number) row.get("n")).intValue());
            }
        } catch (Exception ignored) {
        }
        return map;
    }

    // ==================================================================
    // videos
    // ==================================================================

    private static final RowMapper<Video> VIDEO_MAPPER = (rs, i) -> {
        Video v = new Video();
        v.id = rs.getLong("id");
        v.bvid = rs.getString("bvid");
        v.title = rs.getString("title");
        v.category = rs.getString("category_name");
        v.categoryCode = rs.getString("category_code");
        v.coverColor1 = rs.getString("cover_color1");
        v.coverColor2 = rs.getString("cover_color2");
        v.coverEmoji = rs.getString("cover_emoji");
        v.coverText = rs.getString("cover_text");
        v.coverUrl = toUrl(rs.getString("cover_path"));
        v.posterUrl = toUrl(rs.getString("poster_path") != null ? rs.getString("poster_path") : rs.getString("cover_path"));
        v.videoUrl = toUrl(rs.getString("video_path"));
        v.coverSource = rs.getString("cover_source");
        v.playable = rs.getBoolean("playable") && v.videoUrl != null;
        v.duration = rs.getInt("duration");
        v.views = rs.getLong("views");
        v.danmakus = rs.getInt("danmaku_count");
        v.likes = rs.getInt("like_count");
        v.coins = rs.getInt("coin_count");
        v.favorites = rs.getInt("fav_count");
        v.shares = rs.getInt("share_count");
        v.replies = rs.getInt("reply_count");
        long upId = rs.getLong("up_id");
        v.upId = rs.wasNull() ? 0 : upId;
        v.upName = rs.getString("up_name");
        v.upFace = rs.getString("up_avatar");
        Timestamp pub = rs.getTimestamp("pub_time");
        LocalDateTime pubTime = pub == null ? LocalDateTime.now() : pub.toLocalDateTime();
        v.pubTime = pubTime.format(TIME_FMT);
        v.pubAgo = ago(pubTime);
        v.desc = rs.getString("description");
        String tags = rs.getString("tags");
        v.tags = (tags == null || tags.isBlank()) ? new ArrayList<>() : new ArrayList<>(Arrays.asList(tags.split(",")));
        return v;
    };

    private static String toUrl(String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        if (path.startsWith("http") || path.startsWith("/api/")) {
            return path;
        }
        String p = path.replace('\\', '/');
        if (p.startsWith("videos/")) {
            return "/api/files/video/" + p.substring("videos/".length());
        }
        if (p.startsWith("covers/")) {
            return "/api/files/cover/" + p.substring("covers/".length());
        }
        if (p.startsWith("avatars/")) {
            return "/api/files/avatar/" + p.substring("avatars/".length());
        }
        return "/api/files/cover/" + p;
    }

    public static String ago(LocalDateTime time) {
        long minutes = java.time.Duration.between(time, LocalDateTime.now()).toMinutes();
        if (minutes < 1) {
            return "刚刚";
        }
        if (minutes < 60) {
            return minutes + "分钟前";
        }
        long hours = minutes / 60;
        if (hours < 24) {
            return hours + "小时前";
        }
        long days = hours / 24;
        if (days < 30) {
            return days + "天前";
        }
        if (days < 365) {
            return (days / 30) + "个月前";
        }
        return (days / 365) + "年前";
    }

    private static final String VIDEO_SELECT = """
            SELECT v.id, v.bvid, v.title, v.category_id, v.cover_path, v.cover_color1, v.cover_color2,
                   v.cover_emoji, v.cover_text, v.video_path, v.poster_path, v.cover_source, v.playable,
                   v.duration, v.views, v.danmaku_count, v.like_count, v.coin_count, v.fav_count,
                   v.share_count, v.reply_count, v.up_id, v.up_name, v.up_avatar, v.tags, v.description,
                   v.status, v.pub_time, c.name AS category_name, c.code AS category_code
            FROM videos v LEFT JOIN categories c ON c.id = v.category_id
            """;

    /** 已经被使用的视频文件相对路径（避免同一个演示文件被反复绑定到多条稿件） */
    public Set<String> usedVideoPaths() {
        try {
            return new LinkedHashSet<>(j().queryForList(
                    "SELECT video_path FROM videos WHERE video_path IS NOT NULL AND video_path <> ''", String.class));
        } catch (Exception e) {
            return new LinkedHashSet<>();
        }
    }

    /**
     * 清理重复绑定：同一个 video_path 只保留 id 最小的那条稿件，其余恢复成「无视频」状态。
     * （历史版本每次启动都会重复绑定演示视频，这里做一次性修复）
     */
    public int resetDuplicateBindings() {
        try {
            return j().update("""
                    UPDATE videos v
                    JOIN (SELECT video_path, MIN(id) AS keep_id FROM videos
                          WHERE video_path IS NOT NULL AND video_path <> ''
                          GROUP BY video_path HAVING COUNT(*) > 1) k
                      ON k.video_path = v.video_path AND v.id <> k.keep_id
                    SET v.video_path = NULL, v.poster_path = NULL, v.cover_path = NULL,
                        v.playable = 0, v.cover_source = 'PLACEHOLDER'
                    """);
        } catch (Exception e) {
            return 0;
        }
    }

    /** 所有带媒体文件的稿件（用于清理「文件已丢失」的失效绑定） */
    public List<Map<String, Object>> mediaBindings() {
        try {
            return j().queryForList("""
                    SELECT id, video_path, cover_path FROM videos
                    WHERE (video_path IS NOT NULL AND video_path <> '')
                       OR (cover_path IS NOT NULL AND cover_path <> '')
                    """);
        } catch (Exception e) {
            return List.of();
        }
    }

    /** 清掉一条稿件的视频绑定（文件不存在时调用） */
    public int clearVideoBinding(long videoId) {
        try {
            return j().update("UPDATE videos SET video_path = NULL, playable = 0 WHERE id = ?", videoId);
        } catch (Exception e) {
            return 0;
        }
    }

    /** 清掉一条稿件的封面绑定（文件不存在时调用） */
    public int clearCoverBinding(long videoId) {
        try {
            return j().update("""
                    UPDATE videos SET cover_path = NULL, poster_path = NULL, cover_source = 'PLACEHOLDER'
                    WHERE id = ?
                    """, videoId);
        } catch (Exception e) {
            return 0;
        }
    }

    public long countVideos() {
        try {
            Long n = j().queryForObject("SELECT COUNT(*) FROM videos", Long.class);
            return n == null ? 0 : n;
        } catch (Exception e) {
            return -1;
        }
    }

    public List<Video> loadVideos() {
        try {
            return j().query(VIDEO_SELECT + " WHERE v.status <> 'DRAFT' ORDER BY v.id", VIDEO_MAPPER);
        } catch (Exception e) {
            log.warn("读取视频失败: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    public List<Video> loadVideosByNode(String nodeCode, int limit) {
        try {
            return j().query(VIDEO_SELECT + " WHERE c.code = ? AND v.status = 'PUBLISHED' ORDER BY v.views DESC LIMIT ?",
                    VIDEO_MAPPER, nodeCode, limit);
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    public Optional<Video> findVideo(long id) {
        try {
            List<Video> list = j().query(VIDEO_SELECT + " WHERE v.id = ?", VIDEO_MAPPER, id);
            return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /** 批量写入视频（首次初始化用） */
    public void insertVideos(List<Video> videos, Map<String, Integer> categoryIds) {
        String sql = """
                INSERT INTO videos(id, bvid, title, category_id, node_code, cover_color1, cover_color2,
                                   cover_emoji, cover_text, duration, views, danmaku_count, like_count,
                                   coin_count, fav_count, share_count, reply_count, up_id, up_name, up_avatar,
                                   tags, description, status, pub_time)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'PUBLISHED', ?)
                ON DUPLICATE KEY UPDATE title = VALUES(title)
                """;
        j().batchUpdate(sql, videos, 500, (ps, v) -> {
            ps.setLong(1, v.id);
            ps.setString(2, v.bvid);
            ps.setString(3, v.title);
            setIntOrNull(ps, 4, categoryIds.get(v.category));
            ps.setString(5, v.categoryCode == null ? null : v.categoryCode);
            ps.setString(6, v.coverColor1);
            ps.setString(7, v.coverColor2);
            ps.setString(8, v.coverEmoji);
            ps.setString(9, v.coverText);
            ps.setInt(10, v.duration);
            ps.setLong(11, v.views);
            ps.setInt(12, v.danmakus);
            ps.setInt(13, v.likes);
            ps.setInt(14, v.coins);
            ps.setInt(15, v.favorites);
            ps.setInt(16, v.shares);
            ps.setInt(17, v.replies);
            setLongOrNull(ps, 18, v.upId);
            ps.setString(19, v.upName);
            ps.setString(20, v.upFace);
            ps.setString(21, String.join(",", v.tags));
            ps.setString(22, v.desc);
            ps.setTimestamp(23, parseTime(v.pubTime));
        });
    }

    private void setIntOrNull(PreparedStatement ps, int idx, Integer value) throws java.sql.SQLException {
        if (value == null) {
            ps.setNull(idx, java.sql.Types.INTEGER);
        } else {
            ps.setInt(idx, value);
        }
    }

    private void setLongOrNull(PreparedStatement ps, int idx, Long value) throws java.sql.SQLException {
        if (value == null || value == 0) {
            ps.setNull(idx, java.sql.Types.BIGINT);
        } else {
            ps.setLong(idx, value);
        }
    }

    private Timestamp parseTime(String text) {
        try {
            return Timestamp.valueOf(LocalDateTime.parse(text.trim(), TIME_FMT));
        } catch (Exception e) {
            return Timestamp.valueOf(LocalDateTime.now());
        }
    }

    /** 播放量 / 点赞数等统计字段回写 */
    public void updateVideoStats(Video v) {
        try {
            j().update("""
                    UPDATE videos SET views = ?, danmaku_count = ?, like_count = ?, coin_count = ?,
                                      fav_count = ?, share_count = ?, reply_count = ?, duration = ?
                    WHERE id = ?
                    """, v.views, v.danmakus, v.likes, v.coins, v.favorites, v.shares, v.replies, v.duration, v.id);
        } catch (Exception e) {
            log.warn("更新视频统计失败: {}", e.getMessage());
        }
    }

    public void updateVideoMedia(long videoId, String coverPath, String posterPath, String videoPath,
                                 int duration, int width, int height, long fileSize, String coverSource) {
        try {
            j().update("""
                    UPDATE videos SET cover_path = ?, poster_path = ?, video_path = ?, duration = ?,
                                      width = ?, height = ?, file_size = ?, cover_source = ?, playable = ?,
                                      media_scanned_at = NOW()
                    WHERE id = ?
                    """, coverPath, posterPath, videoPath, duration, width, height, fileSize, coverSource,
                    videoPath != null ? 1 : 0, videoId);
        } catch (Exception e) {
            log.warn("更新视频媒体信息失败: {}", e.getMessage());
        }
    }

    public void updateVideoDuration(long videoId, int duration, int width, int height, long fileSize) {
        try {
            j().update("""
                    UPDATE videos SET duration = ?, width = ?, height = ?, file_size = ?,
                                      media_scanned_at = NOW(), playable = IF(video_path IS NULL, 0, 1)
                    WHERE id = ?
                    """, duration, width, height, fileSize, videoId);
        } catch (Exception ignored) {
        }
    }

    /** 新增一条投稿视频（投稿页用） */
    public long insertVideo(Video v, Integer categoryId, String nodeCode, String videoPath, String coverPath,
                            String coverSource, int duration) {
        KeyHolder kh = new GeneratedKeyHolder();
        j().update(conn -> {
            PreparedStatement ps = conn.prepareStatement("""
                    INSERT INTO videos(bvid, title, category_id, node_code, cover_path, cover_source,
                                       cover_color1, cover_color2, cover_emoji, cover_text, video_path,
                                       poster_path, duration, file_size, up_id, up_name, up_avatar, tags,
                                       description, status, pub_time, playable)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'PUBLISHED', NOW(), ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, v.bvid);
            ps.setString(2, v.title);
            setIntOrNull(ps, 3, categoryId);
            ps.setString(4, nodeCode);
            ps.setString(5, coverPath);
            ps.setString(6, coverSource);
            ps.setString(7, v.coverColor1);
            ps.setString(8, v.coverColor2);
            ps.setString(9, v.coverEmoji);
            ps.setString(10, v.coverText);
            ps.setString(11, videoPath);
            ps.setString(12, coverPath);
            ps.setInt(13, duration);
            ps.setLong(14, 0L);
            setLongOrNull(ps, 15, v.upId);
            ps.setString(16, v.upName);
            ps.setString(17, v.upFace);
            ps.setString(18, String.join(",", v.tags));
            ps.setString(19, v.desc);
            ps.setInt(20, videoPath == null ? 0 : 1);
            return ps;
        }, kh);
        Number key = kh.getKey();
        return key == null ? 0 : key.longValue();
    }

    /** 把还没绑定真实视频的稿件补充上去（媒体扫描后调用） */
    public List<Map<String, Object>> videosWithoutMedia() {
        try {
            return j().queryForList("""
                    SELECT id, title FROM videos WHERE status = 'PUBLISHED' AND (video_path IS NULL OR video_path = '')
                    ORDER BY id LIMIT 200
                    """);
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    // ==================================================================
    // danmaku
    // ==================================================================

    public List<Danmaku> loadDanmaku(long videoId) {
        try {
            return j().query("""
                    SELECT id, content, time_sec, mode, color, font_size FROM danmaku
                    WHERE video_id = ? ORDER BY time_sec LIMIT 2000
                    """, (rs, i) -> {
                Danmaku d = new Danmaku();
                d.id = rs.getLong("id");
                d.text = rs.getString("content");
                d.time = rs.getDouble("time_sec");
                d.mode = rs.getInt("mode");
                d.color = rs.getString("color");
                d.fontSize = rs.getInt("font_size");
                return d;
            }, videoId);
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    public void insertDanmaku(long videoId, Long userId, Danmaku d) {
        j().update("""
                INSERT INTO danmaku(video_id, user_id, content, time_sec, mode, color, font_size)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, videoId, userId, d.text, d.time, d.mode, d.color, d.fontSize <= 0 ? 25 : d.fontSize);
        j().update("UPDATE videos SET danmaku_count = danmaku_count + 1 WHERE id = ?", videoId);
    }

    public void batchInsertDanmaku(long videoId, List<Danmaku> list) {
        String sql = "INSERT INTO danmaku(video_id, user_id, content, time_sec, mode, color, font_size) VALUES (?, NULL, ?, ?, ?, ?, ?)";
        j().batchUpdate(sql, list, 500, (ps, d) -> {
            ps.setLong(1, videoId);
            ps.setString(2, d.text);
            ps.setDouble(3, d.time);
            ps.setInt(4, d.mode);
            ps.setString(5, d.color);
            ps.setInt(6, d.fontSize <= 0 ? 25 : d.fontSize);
        });
    }

    // ==================================================================
    // comments
    // ==================================================================

    public List<Comment> loadComments(long videoId) {
        try {
            return j().query("""
                    SELECT c.id, c.user_id, c.content, c.like_count, c.location, c.up_liked, c.created_at,
                           u.nickname, u.avatar_emoji, u.avatar_url
                    FROM comments c LEFT JOIN users u ON u.id = c.user_id
                    WHERE c.video_id = ? AND c.parent_id IS NULL
                    ORDER BY c.id DESC LIMIT 200
                    """, (rs, i) -> {
                Comment c = new Comment();
                c.id = rs.getLong("id");
                c.userId = rs.getLong("user_id");
                c.user = rs.getString("nickname") == null ? "游客" : rs.getString("nickname");
                c.face = rs.getString("avatar_emoji") == null ? "😀" : rs.getString("avatar_emoji");
                c.faceUrl = rs.getString("avatar_url");
                c.content = rs.getString("content");
                c.likes = rs.getInt("like_count");
                c.location = rs.getString("location");
                c.upLiked = rs.getBoolean("up_liked");
                Timestamp ts = rs.getTimestamp("created_at");
                c.time = ts == null ? "" : ago(ts.toLocalDateTime());
                return c;
            }, videoId);
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    public long insertComment(long videoId, Long userId, Comment c) {
        KeyHolder kh = new GeneratedKeyHolder();
        j().update(conn -> {
            PreparedStatement ps = conn.prepareStatement("""
                    INSERT INTO comments(video_id, user_id, content, like_count, location, up_liked)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, videoId);
            if (userId == null) {
                ps.setNull(2, java.sql.Types.BIGINT);
            } else {
                ps.setLong(2, userId);
            }
            ps.setString(3, c.content);
            ps.setInt(4, c.likes);
            ps.setString(5, c.location);
            ps.setInt(6, c.upLiked ? 1 : 0);
            return ps;
        }, kh);
        j().update("UPDATE videos SET reply_count = reply_count + 1 WHERE id = ?", videoId);
        Number key = kh.getKey();
        return key == null ? 0 : key.longValue();
    }

    public void batchInsertComments(long videoId, List<Comment> list) {
        String sql = "INSERT INTO comments(video_id, user_id, content, like_count, location, up_liked) VALUES (?, NULL, ?, ?, ?, ?)";
        j().batchUpdate(sql, list, 500, (ps, c) -> {
            ps.setLong(1, videoId);
            ps.setString(2, c.content);
            ps.setInt(3, c.likes);
            ps.setString(4, c.location);
            ps.setInt(5, c.upLiked ? 1 : 0);
        });
    }

    // ==================================================================
    // user_actions
    // ==================================================================

    /** 用户对视频的互动状态：videoId -> {LIKE, COIN, FAV} */
    public Map<Long, Set<String>> loadActions(long userId) {
        Map<Long, Set<String>> map = new LinkedHashMap<>();
        try {
            for (Map<String, Object> row : j().queryForList(
                    "SELECT video_id, action_type FROM user_actions WHERE user_id = ?", userId)) {
                long vid = ((Number) row.get("video_id")).longValue();
                map.computeIfAbsent(vid, k -> new HashSet<>()).add(String.valueOf(row.get("action_type")));
            }
        } catch (Exception ignored) {
        }
        return map;
    }

    public void saveAction(long userId, long videoId, String type, boolean add) {
        try {
            if (add) {
                j().update("""
                        INSERT INTO user_actions(user_id, video_id, action_type) VALUES (?, ?, ?)
                        ON DUPLICATE KEY UPDATE created_at = NOW()
                        """, userId, videoId, type);
            } else {
                j().update("DELETE FROM user_actions WHERE user_id = ? AND video_id = ? AND action_type = ?",
                        userId, videoId, type);
            }
        } catch (Exception e) {
            log.warn("保存互动记录失败: {}", e.getMessage());
        }
    }

    /** 我的点赞 / 投币 / 收藏列表 */
    public List<Map<String, Object>> actionsOf(long userId, String type, int limit) {
        try {
            return j().queryForList("""
                    SELECT a.created_at, v.id, v.bvid, v.title, v.duration, v.cover_path, v.cover_color1,
                           v.cover_color2, v.cover_emoji, v.cover_text, v.up_name, v.views, v.danmaku_count
                    FROM user_actions a JOIN videos v ON v.id = a.video_id
                    WHERE a.user_id = ? AND a.action_type = ? ORDER BY a.id DESC LIMIT ?
                    """, userId, type, limit);
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    // ==================================================================
    // video_sources
    // ==================================================================

    public void upsertSource(long videoId, String quality, String filePath, int width, int height,
                             long fileSize, boolean isDefault) {
        try {
            j().update("""
                    INSERT INTO video_sources(video_id, quality, file_path, width, height, file_size, is_default)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    ON DUPLICATE KEY UPDATE file_path = VALUES(file_path), width = VALUES(width),
                                            height = VALUES(height), file_size = VALUES(file_size),
                                            is_default = VALUES(is_default)
                    """, videoId, quality, filePath, width, height, fileSize, isDefault ? 1 : 0);
        } catch (Exception ignored) {
        }
    }

    public List<Map<String, Object>> sources(long videoId) {
        try {
            return j().queryForList("""
                    SELECT quality, file_path, mime_type, width, height, file_size, is_default
                    FROM video_sources WHERE video_id = ? ORDER BY height DESC
                    """, videoId);
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    // ==================================================================
    // bangumi
    // ==================================================================

    public List<Bangumi> loadBangumi() {
        try {
            return j().query("""
                    SELECT id, title, cover_path, color1, color2, emoji, status, area, episode, total_episode,
                           followers, score, tag, description, pub_time FROM bangumi ORDER BY id
                    """, (rs, i) -> {
                Bangumi b = new Bangumi();
                b.id = rs.getLong("id");
                b.title = rs.getString("title");
                b.color1 = rs.getString("color1");
                b.color2 = rs.getString("color2");
                b.emoji = rs.getString("emoji");
                b.status = rs.getString("status");
                b.area = rs.getString("area");
                b.episode = rs.getInt("episode");
                b.totalEpisode = rs.getInt("total_episode");
                b.followers = rs.getLong("followers");
                b.score = rs.getDouble("score");
                b.tag = rs.getString("tag");
                b.desc = rs.getString("description");
                b.pubTime = rs.getString("pub_time");
                return b;
            });
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    public void batchInsertBangumi(List<Bangumi> list) {
        String sql = """
                INSERT INTO bangumi(id, title, color1, color2, emoji, status, area, episode, total_episode,
                                    followers, score, tag, description, pub_time)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE title = VALUES(title)
                """;
        j().batchUpdate(sql, list, 200, (ps, b) -> {
            ps.setLong(1, b.id);
            ps.setString(2, b.title);
            ps.setString(3, b.color1);
            ps.setString(4, b.color2);
            ps.setString(5, b.emoji);
            ps.setString(6, b.status);
            ps.setString(7, b.area);
            ps.setInt(8, b.episode);
            ps.setInt(9, b.totalEpisode);
            ps.setLong(10, b.followers);
            ps.setDouble(11, b.score);
            ps.setString(12, b.tag);
            ps.setString(13, b.desc);
            ps.setString(14, b.pubTime);
        });
    }

    // ==================================================================
    // dynamics
    // ==================================================================

    public List<Dynamic> loadDynamics() {
        try {
            return j().query("""
                    SELECT d.id, d.up_id, d.up_name, d.up_avatar, d.content, d.emoji, d.color1, d.color2,
                           d.video_id, d.like_count, d.reply_count, d.created_at, v.title AS video_title
                    FROM dynamics d LEFT JOIN videos v ON v.id = d.video_id
                    ORDER BY d.id DESC LIMIT 200
                    """, (rs, i) -> {
                Dynamic dy = new Dynamic();
                dy.id = rs.getLong("id");
                dy.upId = rs.getLong("up_id");
                dy.upName = rs.getString("up_name");
                dy.upFace = rs.getString("up_avatar");
                dy.content = rs.getString("content");
                dy.emoji = rs.getString("emoji");
                dy.color1 = rs.getString("color1");
                dy.color2 = rs.getString("color2");
                dy.videoId = rs.getLong("video_id");
                dy.videoTitle = rs.getString("video_title");
                dy.likes = rs.getInt("like_count");
                dy.comments = rs.getInt("reply_count");
                Timestamp ts = rs.getTimestamp("created_at");
                dy.time = ts == null ? "" : ago(ts.toLocalDateTime());
                return dy;
            });
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    public void batchInsertDynamics(List<Dynamic> list) {
        String sql = """
                INSERT INTO dynamics(id, up_id, up_name, up_avatar, content, emoji, color1, color2,
                                     video_id, like_count, reply_count)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE content = VALUES(content)
                """;
        j().batchUpdate(sql, list, 200, (ps, d) -> {
            ps.setLong(1, d.id);
            setLongOrNull(ps, 2, d.upId);
            ps.setString(3, d.upName);
            ps.setString(4, d.upFace);
            ps.setString(5, d.content);
            ps.setString(6, d.emoji);
            ps.setString(7, d.color1);
            ps.setString(8, d.color2);
            setLongOrNull(ps, 9, d.videoId);
            ps.setInt(10, d.likes);
            ps.setInt(11, d.comments);
        });
    }

    // ==================================================================
    // 用户投稿产生的 UP 主（投稿人自己也是一位 UP 主）
    // ==================================================================

    /** 把注册用户同步成 UP 主账号，方便视频表的外键和空间页 */
    public void ensureUpUser(long userId, String nickname, String emoji) {
        try {
            Integer n = j().queryForObject("SELECT COUNT(*) FROM users WHERE id = ?", Integer.class, userId);
            if (n != null && n > 0) {
                return;
            }
        } catch (Exception ignored) {
        }
    }
}
