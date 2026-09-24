package com.bili.demo.controller;

import com.bili.demo.auth.UserContext;
import com.bili.demo.data.DataStore;
import com.bili.demo.data.UserStore;
import com.bili.demo.db.ContentRepository;
import com.bili.demo.db.DataSyncService;
import com.bili.demo.db.UserRepository;
import com.bili.demo.media.MediaService;
import com.bili.demo.model.User;
import com.bili.demo.model.Video;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;

/**
 * 个人中心：历史记录 / 收藏 / 关注 / 消息 / 设置 / 投稿。
 * 全部需要登录（未登录返回 401，前端会跳转登录页）。
 */
@RestController
@RequestMapping("/api/uc")
public class UserCenterController {

    private final UserRepository userRepo;
    private final ContentRepository contentRepo;
    private final DataSyncService sync;
    private final DataStore store;
    private final UserStore userStore;
    private final MediaService media;

    public UserCenterController(UserRepository userRepo, ContentRepository contentRepo, DataSyncService sync,
                                DataStore store, UserStore userStore, MediaService media) {
        this.userRepo = userRepo;
        this.contentRepo = contentRepo;
        this.sync = sync;
        this.store = store;
        this.userStore = userStore;
        this.media = media;
    }

    private Long uid() {
        return UserContext.userId();
    }

    private ResponseEntity<?> needLogin() {
        return ResponseEntity.status(401).body(Map.of("success", false, "message", "请先登录"));
    }

    /** 个人中心总览：用户信息 + 各模块数量 */
    @GetMapping("/overview")
    public ResponseEntity<?> overview() {
        Long id = uid();
        if (id == null) {
            return needLogin();
        }
        User user = userStore.resolve(id);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("user", user);
        data.put("expPercent", user.expMax == 0 ? 0 : Math.round(user.exp * 1000.0 / user.expMax) / 10.0);
        Map<String, Object> counts = new LinkedHashMap<>();
        counts.put("history", userRepo.history(id, 200).size());
        counts.put("favorites", userRepo.favorites(id, 200, 0).size());
        counts.put("following", userRepo.followingIds(id).size());
        counts.put("uploads", userRepo.uploads(id).size());
        counts.put("unread", userRepo.unreadCount(id));
        counts.put("bangumi", userRepo.followedBangumiIds(id).size());
        counts.put("likes", contentRepo.actionsOf(id, "LIKE", 200).size());
        counts.put("coins", contentRepo.actionsOf(id, "COIN", 200).size());
        data.put("counts", counts);
        data.put("videos", lightCopies(store.videos, 8));
        return ResponseEntity.ok(data);
    }

    // ---------------- 历史记录 ----------------

    @GetMapping("/history")
    public ResponseEntity<?> history(@RequestParam(defaultValue = "100") int limit) {
        Long id = uid();
        if (id == null) {
            return needLogin();
        }
        List<Map<String, Object>> rows = userRepo.history(id, limit);
        for (Map<String, Object> row : rows) {
            row.put("coverUrl", coverUrl(row.get("cover_path")));
        }
        return ResponseEntity.ok(Map.of("items", rows, "total", rows.size()));
    }

    @DeleteMapping("/history/{videoId}")
    public ResponseEntity<?> deleteHistory(@PathVariable long videoId) {
        Long id = uid();
        if (id == null) {
            return needLogin();
        }
        userRepo.deleteHistory(id, videoId);
        return ResponseEntity.ok(Map.of("success", true, "message", "已删除该记录"));
    }

    @DeleteMapping("/history")
    public ResponseEntity<?> clearHistory() {
        Long id = uid();
        if (id == null) {
            return needLogin();
        }
        userRepo.deleteHistory(id, 0);
        return ResponseEntity.ok(Map.of("success", true, "message", "已清空观看历史"));
    }

    // ---------------- 收藏 ----------------

    @GetMapping("/favorites")
    public ResponseEntity<?> favorites(@RequestParam(defaultValue = "100") int limit,
                                       @RequestParam(defaultValue = "0") long folderId) {
        Long id = uid();
        if (id == null) {
            return needLogin();
        }
        List<Map<String, Object>> rows = userRepo.favorites(id, limit, folderId);
        for (Map<String, Object> row : rows) {
            row.put("coverUrl", coverUrl(row.get("cover_path")));
        }
        return ResponseEntity.ok(Map.of("items", rows, "folders", userRepo.favoriteFolders(id)));
    }

    @DeleteMapping("/favorites/{videoId}")
    public ResponseEntity<?> removeFavorite(@PathVariable long videoId) {
        Long id = uid();
        if (id == null) {
            return needLogin();
        }
        long folderId = userRepo.defaultFolderId(id);
        try {
            contentRepo.saveAction(id, videoId, "FAV", false);
        } catch (Exception ignored) {
        }
        userRepo.removeFavorite(folderId, videoId);
        Video v = store.findVideo(videoId);
        if (v != null) {
            v.favored = false;
            v.favorites = Math.max(0, v.favorites - 1);
            sync.saveVideoStats(v);
        }
        return ResponseEntity.ok(Map.of("success", true, "message", "已取消收藏"));
    }

    @GetMapping("/folders")
    public ResponseEntity<?> folders() {
        Long id = uid();
        if (id == null) {
            return needLogin();
        }
        return ResponseEntity.ok(userRepo.favoriteFolders(id));
    }

    @PostMapping("/folders")
    public ResponseEntity<?> createFolder(@RequestBody Map<String, Object> body) {
        Long id = uid();
        if (id == null) {
            return needLogin();
        }
        String name = String.valueOf(body.getOrDefault("name", "")).trim();
        if (name.isEmpty()) {
            return ResponseEntity.ok(Map.of("success", false, "message", "收藏夹名称不能为空"));
        }
        long folderId = userRepo.createFolder(id, name, !"0".equals(String.valueOf(body.get("isPublic"))));
        return ResponseEntity.ok(Map.of("success", folderId > 0, "folderId", folderId,
                "message", folderId > 0 ? "收藏夹已创建" : "创建失败"));
    }

    @DeleteMapping("/folders/{folderId}")
    public ResponseEntity<?> deleteFolder(@PathVariable long folderId) {
        Long id = uid();
        if (id == null) {
            return needLogin();
        }
        userRepo.deleteFolder(id, folderId);
        return ResponseEntity.ok(Map.of("success", true, "message", "收藏夹已删除"));
    }

    // ---------------- 点赞 / 投币 ----------------

    @GetMapping("/actions")
    public ResponseEntity<?> actions(@RequestParam(defaultValue = "LIKE") String type,
                                     @RequestParam(defaultValue = "100") int limit) {
        Long id = uid();
        if (id == null) {
            return needLogin();
        }
        List<Map<String, Object>> rows = contentRepo.actionsOf(id, type.toUpperCase(), limit);
        for (Map<String, Object> row : rows) {
            row.put("coverUrl", coverUrl(row.get("cover_path")));
        }
        return ResponseEntity.ok(Map.of("items", rows, "type", type.toUpperCase()));
    }

    // ---------------- 关注 ----------------

    @GetMapping("/following")
    public ResponseEntity<?> following() {
        Long id = uid();
        if (id == null) {
            return needLogin();
        }
        return ResponseEntity.ok(Map.of("items", userRepo.followingUsers(id)));
    }

    @PostMapping("/follow/{upId}")
    public ResponseEntity<?> follow(@PathVariable long upId) {
        Long id = uid();
        if (id == null) {
            return needLogin();
        }
        boolean followed = userRepo.toggleFollow(id, upId);
        return ResponseEntity.ok(Map.of("success", true, "followed", followed,
                "message", followed ? "关注成功" : "已取消关注"));
    }

    // ---------------- 消息 ----------------

    @GetMapping("/messages")
    public ResponseEntity<?> messages(@RequestParam(defaultValue = "ALL") String type,
                                      @RequestParam(defaultValue = "50") int limit) {
        Long id = uid();
        if (id == null) {
            return needLogin();
        }
        return ResponseEntity.ok(Map.of(
                "items", userRepo.messages(id, type, limit),
                "unread", userRepo.unreadCount(id)));
    }

    @PostMapping("/messages/read")
    public ResponseEntity<?> readMessages(@RequestBody(required = false) Map<String, Object> body) {
        Long id = uid();
        if (id == null) {
            return needLogin();
        }
        long msgId = body == null || body.get("id") == null ? 0 : Long.parseLong(String.valueOf(body.get("id")));
        userRepo.markMessageRead(id, msgId);
        return ResponseEntity.ok(Map.of("success", true, "unread", userRepo.unreadCount(id)));
    }

    // ---------------- 设置 ----------------

    @GetMapping("/settings")
    public ResponseEntity<?> settings() {
        Long id = uid();
        if (id == null) {
            return needLogin();
        }
        Map<String, Object> data = new LinkedHashMap<>(userRepo.settings(id));
        data.put("user", userStore.resolve(id));
        return ResponseEntity.ok(data);
    }

    @PostMapping("/settings")
    public ResponseEntity<?> saveSettings(@RequestBody Map<String, Object> body) {
        Long id = uid();
        if (id == null) {
            return needLogin();
        }
        userRepo.saveSettings(id, body);
        return ResponseEntity.ok(Map.of("success", true, "message", "设置已保存",
                "settings", userRepo.settings(id)));
    }

    // ---------------- 投稿 ----------------

    @GetMapping("/uploads")
    public ResponseEntity<?> uploads() {
        Long id = uid();
        if (id == null) {
            return needLogin();
        }
        return ResponseEntity.ok(Map.of("items", userRepo.uploads(id)));
    }

    /** 投稿：上传视频文件 → FFmpeg 自动截帧封面 → 直接发布 */
    @PostMapping("/uploads")
    public ResponseEntity<?> upload(@RequestParam("file") MultipartFile file,
                                    @RequestParam String title,
                                    @RequestParam(required = false) String node,
                                    @RequestParam(required = false) String description) {
        Long id = uid();
        if (id == null) {
            return needLogin();
        }
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "请选择视频文件"));
        }
        String lower = String.valueOf(file.getOriginalFilename()).toLowerCase();
        boolean isVideo = lower.endsWith(".mp4") || lower.endsWith(".webm") || lower.endsWith(".mov")
                || lower.endsWith(".mkv") || (file.getContentType() != null && file.getContentType().startsWith("video/"));
        if (!isVideo) {
            return ResponseEntity.badRequest().body(Map.of("success", false,
                    "message", "目前支持 mp4 / webm / mov / mkv 格式的视频"));
        }
        try {
            User user = userStore.resolve(id);
            String base = "upload-" + id + "-" + System.currentTimeMillis();
            Map<String, Object> saved = media.saveUpload(file, base);

            Video v = new Video();
            v.title = title == null || title.isBlank() ? "我的投稿" : title.trim();
            v.upId = id;
            v.upName = user.name;
            v.upFace = user.face;
            v.coverText = v.title.length() > 9 ? v.title.substring(0, 9) : v.title;
            v.coverEmoji = "🎬";
            v.coverColor1 = "#FB7299";
            v.coverColor2 = "#A6C1EE";
            v.desc = description == null ? "用户投稿作品" : description;
            v.categoryCode = node;
            v.tags.add(node == null ? "原创" : node);
            long newId = 300000L + Math.abs(new Random().nextInt(600000));
            v.id = newId;
            v.bvid = Video.toBvid(newId);

            String nodeCode = node == null || node.isBlank() ? "life" : node;
            Map<String, Integer> catIds = contentRepo.categoryIdByCode();
            Integer catId = catIds.get(nodeCode);
            int duration = ((Number) saved.getOrDefault("duration", 0)).intValue();
            v.duration = duration > 0 ? duration : 60;

            if (sync.mysqlMode()) {
                long videoId = contentRepo.insertVideo(v, catId, nodeCode,
                        String.valueOf(saved.get("videoPath")), (String) saved.get("coverPath"),
                        saved.get("coverPath") == null ? "PLACEHOLDER" : "FFMPEG_FRAME", v.duration);
                if (videoId > 0) {
                    v.id = videoId;
                }
                long uploadId = userRepo.insertUpload(id, v.title, catId, (String) saved.get("fileName"),
                        (String) saved.get("videoPath"), (String) saved.get("coverPath"), "PUBLISHED");
                userRepo.publishUpload(uploadId, v.id);
            }
            v.coverUrl = (String) saved.get("coverUrl");
            v.posterUrl = v.coverUrl;
            v.videoUrl = (String) saved.get("videoUrl");
            v.playable = v.videoUrl != null;
            v.coverSource = v.coverUrl == null ? "PLACEHOLDER" : "FFMPEG_FRAME";
            v.pubTime = java.time.LocalDateTime.now()
                    .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
            v.pubAgo = "刚刚";
            store.videos.add(0, v);
            store.commentMap.put(v.id, new ArrayList<>());

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("success", true);
            data.put("message", "投稿成功！封面已用 FFmpeg 自动截帧");
            data.put("videoId", v.id);
            data.put("coverUrl", v.coverUrl);
            data.put("videoUrl", v.videoUrl);
            data.put("duration", v.duration);
            return ResponseEntity.ok(data);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("success", false,
                    "message", "投稿失败：" + e.getMessage()));
        }
    }

    @DeleteMapping("/uploads/{uploadId}")
    public ResponseEntity<?> deleteUpload(@PathVariable long uploadId) {
        Long id = uid();
        if (id == null) {
            return needLogin();
        }
        userRepo.deleteUpload(id, uploadId);
        return ResponseEntity.ok(Map.of("success", true, "message", "已删除该投稿记录"));
    }

    // ---------------- 搜索历史 ----------------

    @GetMapping("/search-history")
    public ResponseEntity<?> searchHistory() {
        Long id = uid();
        if (id == null) {
            return needLogin();
        }
        return ResponseEntity.ok(Map.of("items", userRepo.searchHistory(id, 20)));
    }

    @DeleteMapping("/search-history")
    public ResponseEntity<?> clearSearchHistory() {
        Long id = uid();
        if (id == null) {
            return needLogin();
        }
        userRepo.clearSearchHistory(id);
        return ResponseEntity.ok(Map.of("success", true, "message", "已清空搜索历史"));
    }

    // ---------------- 工具 ----------------

    private String coverUrl(Object path) {
        if (path == null) {
            return null;
        }
        String p = String.valueOf(path);
        if (p.isBlank()) {
            return null;
        }
        return p.startsWith("/api/") ? p : "/api/files/cover/" + p.replace('\\', '/').replace("covers/", "");
    }

    private List<Video> lightCopies(List<Video> source, int limit) {
        List<Video> list = new ArrayList<>();
        for (int i = 0; i < source.size() && i < limit; i++) {
            list.add(source.get(i));
        }
        return list;
    }
}
