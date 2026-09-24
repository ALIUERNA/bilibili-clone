package com.bili.demo.controller;

import com.bili.demo.db.ContentRepository;
import com.bili.demo.db.DataSyncService;
import com.bili.demo.db.Database;
import com.bili.demo.media.MediaService;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 媒体与运行时状态接口。
 *   GET  /api/media/status → FFmpeg 是否可用、视频/封面目录、数据库模式
 *   POST /api/media/scan   → 手动重新扫描 uploads/videos：绑定视频 + FFmpeg 截帧封面
 *   POST /api/media/samples→ 生成演示视频（不依赖任何外部素材）
 *   GET  /api/media/sources/{videoId} → 某个视频的清晰度列表
 */
@RestController
@RequestMapping("/api/media")
public class MediaController {

    private final MediaService media;
    private final DataSyncService sync;
    private final Database db;
    private final ContentRepository contentRepo;

    public MediaController(MediaService media, DataSyncService sync, Database db, ContentRepository contentRepo) {
        this.media = media;
        this.sync = sync;
        this.db = db;
        this.contentRepo = contentRepo;
    }

    @GetMapping("/status")
    public Map<String, Object> status() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("mysqlMode", sync.mysqlMode());
        data.put("database", db.available() ? "connected" : "unavailable");
        data.put("ffmpeg", media.ffmpeg());
        data.put("ffmpegAvailable", media.ffmpegAvailable());
        data.put("videosDir", media.videosDir().toString());
        data.put("coversDir", media.coversDir().toString());
        data.put("videoCount", db.count("videos"));
        data.put("danmakuCount", db.count("danmaku"));
        data.put("commentCount", db.count("comments"));
        data.put("userCount", db.count("users"));
        data.put("categoryCount", db.count("categories"));
        data.put("captchaCount", db.count("captcha_codes"));
        data.put("emailCodeCount", db.count("email_verification_codes"));
        data.put("qrSessionCount", db.count("qr_login_sessions"));
        return data;
    }

    /** 重新扫描：把 uploads/videos 里的视频绑到稿件上，并 FFmpeg 截帧生成封面 */
    @PostMapping("/scan")
    public Map<String, Object> scan() {
        Map<String, Object> result = media.scan(false);
        return result;
    }

    /** 生成演示视频（首次启动会自动调用，这里可以手动补） */
    @PostMapping("/samples")
    public Map<String, Object> samples() {
        Map<String, Object> result = new LinkedHashMap<>(media.generateSampleVideos());
        return result;
    }

    @GetMapping("/sources/{videoId}")
    public Object sources(@PathVariable long videoId) {
        return contentRepo.sources(videoId);
    }
}
