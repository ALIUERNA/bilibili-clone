package com.bili.demo.controller;

import com.bili.demo.data.DataStore;
import com.bili.demo.data.UserStore;
import com.bili.demo.model.Comment;
import com.bili.demo.model.Danmaku;
import com.bili.demo.model.Video;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 视频详情 / 弹幕 / 评论 / 一键三连 相关接口。
 */
@RestController
@RequestMapping("/api/videos")
public class VideoController {

    private final DataStore store;
    private final UserStore userStore;
    private final AtomicLong selfDanmakuId = new AtomicLong(1);
    private final AtomicLong selfCommentId = new AtomicLong(1);

    public VideoController(DataStore store, UserStore userStore) {
        this.store = store;
        this.userStore = userStore;
    }

    /**
     * 互动会加经验（和 B 站一样，点赞、投币、收藏、发弹幕都会涨经验）。
     * 这里把涨经验后的状态一起返回给前端，前端就能弹出「+5 经验」的小动画。
     */
    private Map<String, Object> expPayload(int gain) {
        Map<String, Object> data = new LinkedHashMap<>();
        if (gain > 0) {
            boolean levelUp = userStore.addExp(gain);
            data.put("expGain", gain);
            data.put("levelUp", levelUp);
            var u = userStore.get();
            data.put("level", u.level);
            data.put("exp", u.exp);
            data.put("expMax", u.expMax);
            data.put("coins", u.coins);
        }
        return data;
    }

    /** 视频详情 */
    @GetMapping("/{id}")
    public ResponseEntity<?> detail(@PathVariable long id) {
        Video v = store.findVideo(id);
        if (v == null) {
            return ResponseEntity.status(404).body(Map.of("message", "视频不存在或已被删除"));
        }
        // 详情页只返回靠前的弹幕，模拟「弹幕池」按时间加载
        Video detail = new Video();
        copyForDetail(v, detail);
        detail.danmakuList = v.danmakuList.size() > 400
                ? new ArrayList<>(v.danmakuList.subList(0, 400))
                : v.danmakuList;
        return ResponseEntity.ok(detail);
    }

    /** 相关推荐 */
    @GetMapping("/{id}/related")
    public List<Video> related(@PathVariable long id, @RequestParam(defaultValue = "12") int limit) {
        Video v = store.findVideo(id);
        if (v == null) {
            return List.of();
        }
        List<Video> result = new ArrayList<>();
        for (Video r : store.related(v, limit)) {
            Video c = new Video();
            copyForDetail(r, c);
            c.danmakuList = List.of();
            result.add(c);
        }
        return result;
    }

    /** 获取弹幕 */
    @GetMapping("/{id}/danmaku")
    public List<Danmaku> danmaku(@PathVariable long id) {
        Video v = store.findVideo(id);
        return v == null ? List.of() : v.danmakuList;
    }

    /** 发送弹幕 */
    @PostMapping("/{id}/danmaku")
    public ResponseEntity<?> sendDanmaku(@PathVariable long id, @RequestBody Map<String, Object> body) {
        Video v = store.findVideo(id);
        if (v == null) {
            return ResponseEntity.status(404).body(Map.of("message", "视频不存在"));
        }
        String text = String.valueOf(body.getOrDefault("text", "")).trim();
        if (text.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "弹幕内容不能为空"));
        }
        if (text.length() > 50) {
            text = text.substring(0, 50);
        }
        Danmaku d = new Danmaku(1_000_000L + selfDanmakuId.getAndIncrement(), text, 0, 1, "#FFFFFF");
        d.time = body.get("time") == null ? 0 : Double.parseDouble(String.valueOf(body.get("time")));
        d.mode = body.get("mode") == null ? 1 : Integer.parseInt(String.valueOf(body.get("mode")));
        d.color = body.get("color") == null ? "#FFFFFF" : String.valueOf(body.get("color"));
        d.self = true;
        v.danmakuList.add(d);
        v.danmakus++;

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("danmaku", d);
        data.putAll(expPayload(3));   // 发弹幕 +3 经验
        return ResponseEntity.ok(data);
    }

    /** 获取评论 */
    @GetMapping("/{id}/comments")
    public Map<String, Object> comments(@PathVariable long id,
                                        @RequestParam(defaultValue = "1") int page,
                                        @RequestParam(defaultValue = "20") int size) {
        List<Comment> all = store.commentMap.getOrDefault(id, new ArrayList<>());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("items", all.size() > size ? new ArrayList<>(all.subList(0, size)) : all);
        data.put("total", all.size() + 1284);   // 加上一点「历史评论」的装饰数字
        data.put("page", page);
        data.put("hasMore", all.size() > size);
        return data;
    }

    /** 发表评论 */
    @PostMapping("/{id}/comments")
    public ResponseEntity<?> addComment(@PathVariable long id, @RequestBody Map<String, Object> body) {
        Video v = store.findVideo(id);
        if (v == null) {
            return ResponseEntity.status(404).body(Map.of("message", "视频不存在"));
        }
        String content = String.valueOf(body.getOrDefault("content", "")).trim();
        if (content.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "评论内容不能为空"));
        }
        if (content.length() > 300) {
            content = content.substring(0, 300);
        }
        Comment c = new Comment(2_000_000L + selfCommentId.getAndIncrement(),
                String.valueOf(body.getOrDefault("user", "我")),
                String.valueOf(body.getOrDefault("face", "😀")),
                content, "刚刚", 0);
        c.location = "本机";
        c.floor = 1;
        c.faceUrl = userStore.get().faceUrl;
        List<Comment> list = store.commentMap.computeIfAbsent(id, k -> new ArrayList<>());
        list.add(0, c);
        v.replies++;

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("comment", c);
        data.putAll(expPayload(5));   // 发评论 +5 经验
        return ResponseEntity.ok(data);
    }

    /**
     * 点赞 / 投币 / 收藏 / 分享 / 关注 UP 主
     * 例：POST /api/videos/100001/action?type=like
     */
    @PostMapping("/{id}/action")
    public ResponseEntity<?> action(@PathVariable long id, @RequestParam String type) {
        Video v = store.findVideo(id);
        if (v == null) {
            return ResponseEntity.status(404).body(Map.of("message", "视频不存在"));
        }
        int expGain = 0;
        switch (type) {
            case "like" -> {
                v.liked = !v.liked;
                v.likes += v.liked ? 1 : -1;
                expGain = v.liked ? 5 : 0;      // 点赞 +5 经验
            }
            case "coin" -> {
                v.coined = !v.coined;
                v.coins += v.coined ? 1 : -1;
                expGain = v.coined ? 10 : 0;    // 投币 +10 经验
            }
            case "fav" -> {
                v.favored = !v.favored;
                v.favorites += v.favored ? 1 : -1;
                expGain = v.favored ? 5 : 0;    // 收藏 +5 经验
            }
            case "follow" -> {
                v.followed = !v.followed;
                var up = store.findUp(v.upId);
                if (up != null) {
                    up.followed = v.followed;
                    up.fans += v.followed ? 1 : -1;
                }
                expGain = v.followed ? 5 : 0;   // 关注 +5 经验
            }
            case "share" -> {
                v.shares++;
                expGain = 2;                    // 分享 +2 经验
            }
            default -> {
                return ResponseEntity.badRequest().body(Map.of("message", "不支持的操作类型: " + type));
            }
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("liked", v.liked);
        data.put("coined", v.coined);
        data.put("favored", v.favored);
        data.put("followed", v.followed);
        data.put("likes", v.likes);
        data.put("coins", v.coins);
        data.put("favorites", v.favorites);
        data.put("shares", v.shares);
        data.putAll(expPayload(expGain));
        return ResponseEntity.ok(data);
    }

    /** 播放量 +1（前端打开播放页时调用） */
    @PostMapping("/{id}/view")
    public Map<String, Object> view(@PathVariable long id) {
        Video v = store.findVideo(id);
        Map<String, Object> data = new LinkedHashMap<>();
        if (v != null) {
            v.views++;
            data.put("views", v.views);
        }
        return data;
    }

    private void copyForDetail(Video v, Video c) {
        c.id = v.id;
        c.bvid = v.bvid;
        c.title = v.title;
        c.category = v.category;
        c.coverColor1 = v.coverColor1;
        c.coverColor2 = v.coverColor2;
        c.coverEmoji = v.coverEmoji;
        c.coverText = v.coverText;
        c.duration = v.duration;
        c.views = v.views;
        c.danmakus = v.danmakus;
        c.likes = v.likes;
        c.coins = v.coins;
        c.favorites = v.favorites;
        c.shares = v.shares;
        c.replies = v.replies;
        c.upId = v.upId;
        c.upName = v.upName;
        c.upFace = v.upFace;
        c.pubTime = v.pubTime;
        c.pubAgo = v.pubAgo;
        c.desc = v.desc;
        c.tags = v.tags;
        c.liked = v.liked;
        c.coined = v.coined;
        c.favored = v.favored;
        c.followed = v.followed;
        c.danmakuList = v.danmakuList;
    }
}
