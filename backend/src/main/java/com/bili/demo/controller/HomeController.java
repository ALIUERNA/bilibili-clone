package com.bili.demo.controller;

import com.bili.demo.data.DataStore;
import com.bili.demo.db.ContentRepository;
import com.bili.demo.db.DataSyncService;
import com.bili.demo.auth.UserContext;
import com.bili.demo.model.Banner;
import com.bili.demo.model.Category;
import com.bili.demo.model.ContentNode;
import com.bili.demo.model.PageResult;
import com.bili.demo.model.Video;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 首页 / 列表 / 排行榜 / 内容节点 相关接口。
 */
@RestController
@RequestMapping("/api")
public class HomeController {

    private final DataStore store;
    private final ContentRepository contentRepo;
    private final DataSyncService sync;

    public HomeController(DataStore store, ContentRepository contentRepo, DataSyncService sync) {
        this.store = store;
        this.contentRepo = contentRepo;
        this.sync = sync;
    }

    /** 首页聚合接口：轮播图 + 分区导航 + 内容节点 + 热搜词 */
    @GetMapping("/home")
    public Map<String, Object> home() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("banners", bannersWithCover());
        data.put("categories", store.categories);
        data.put("nodes", nodes());
        data.put("hotSearch", store.hotSearch);
        data.put("onlineCount", 12_845_392);   // 「当前在线人数」装饰用
        data.put("login", UserContext.loggedIn());
        return data;
    }

    /** 轮播图：优先用真实视频封面帧，没有真实封面时保留渐变色兜底（前端不会出现空白） */
    private List<Map<String, Object>> bannersWithCover() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (Banner b : store.banners) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", b.id);
            item.put("title", b.title);
            item.put("subtitle", b.subtitle);
            item.put("emoji", b.emoji);
            item.put("color1", b.color1);
            item.put("color2", b.color2);
            item.put("tag", b.tag);
            item.put("videoId", b.videoId);
            Video v = b.videoId > 0 ? store.findVideo(b.videoId) : null;
            if (v != null && v.coverUrl != null) {
                item.put("coverUrl", v.coverUrl);
                item.put("posterUrl", v.posterUrl);
            }
            item.put("coverText", v == null ? null : v.coverText);
            item.put("upName", v == null ? null : v.upName);
            list.add(item);
        }
        return list;
    }

    /** 内容节点（视频 / 游戏 / 直播 / 番剧 / 专栏 / 活动 / 社区中心 …） */
    @GetMapping("/nodes")
    public List<ContentNode> nodes() {
        List<ContentNode> all = contentRepo.nodes();
        if (all.isEmpty()) {
            // 内存兜底：数据库不可用时用内置目录生成
            all = com.bili.demo.db.NodeCatalog.all();
            Map<String, Integer> seedCounts = new LinkedHashMap<>();
            for (Category c : store.categories) {
                seedCounts.put(c.key, c.count);
            }
            for (ContentNode n : all) {
                n.videoCount = seedCounts.getOrDefault(n.code, 0);
            }
        }

        Map<String, Integer> counts = contentRepo.videoCountByNode();
        // 「推荐 / 热门」不是真实分区：它们在 categories 表里有记录、但没有稿件挂在上面，
        // 直接查计数会得到 0，页面就会出现「热门 · 0 个视频」这种看着像坏掉的文案。
        // 这两个是聚合入口，用全站已发布稿件总数更符合直觉。
        int totalVideos = counts.values().stream().mapToInt(Integer::intValue).sum();

        List<ContentNode> roots = new ArrayList<>();
        Map<String, ContentNode> byCode = new LinkedHashMap<>();
        for (ContentNode n : all) {
            if ("recommend".equals(n.code) || "hot".equals(n.code)) {
                n.videoCount = totalVideos;
            } else {
                n.videoCount = counts.getOrDefault(n.code, n.videoCount);
            }
            if (n.parentId == null) {
                roots.add(n);
                byCode.put(n.code, n);
            }
        }
        for (ContentNode n : all) {
            if (n.parentId != null) {
                for (ContentNode root : roots) {
                    if (root.id == n.parentId) {
                        root.children.add(n);
                        break;
                    }
                }
            }
        }
        return roots;
    }

    /** 单个内容节点（分区页 / 占位页用） */
    @GetMapping("/nodes/{code}")
    public Map<String, Object> node(@PathVariable String code) {
        for (ContentNode n : nodes()) {
            if (n.code.equalsIgnoreCase(code)) {
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("node", n);
                data.put("videos", lightCopies(store.recommendByNode(code, 48)));
                return data;
            }
        }
        return Map.of("node", Map.of("code", code, "name", code, "nodeType", "VIDEO", "placeholder", true),
                "videos", List.of());
    }

    /** 分区列表（兼容老前端） */
    @GetMapping("/categories")
    public List<Category> categories() {
        return store.categories;
    }

    /**
     * 视频列表（首页推荐流 / 分区页 / 内容节点页）
     * 例：/api/videos?category=游戏&page=1&size=24
     *     /api/videos?node=game&page=1&size=24
     */
    @GetMapping("/videos")
    public PageResult<Video> videos(@RequestParam(required = false) String category,
                                    @RequestParam(required = false) String node,
                                    @RequestParam(defaultValue = "1") int page,
                                    @RequestParam(defaultValue = "24") int size) {
        List<Video> list = (node != null && !node.isBlank())
                ? store.recommendByNode(node, 240)
                : store.recommend(category);
        return PageResult.of(lightCopies(list), page, size);
    }

    /**
     * 排行榜
     * 例：/api/rankings?type=all&limit=20
     */
    @GetMapping("/rankings")
    public List<Video> rankings(@RequestParam(defaultValue = "all") String type,
                                @RequestParam(defaultValue = "20") int limit) {
        return lightCopies(store.ranking(type, limit));
    }

    private List<Video> lightCopies(List<Video> source) {
        Map<Long, Set<String>> actions = sync.loadActions(currentUid());
        List<Video> copy = new ArrayList<>(source.size());
        for (Video v : source) {
            Video c = lightCopy(v);
            Set<String> set = actions.get(v.id);
            if (set != null) {
                c.liked = set.contains("LIKE");
                c.coined = set.contains("COIN");
                c.favored = set.contains("FAV");
            }
            copy.add(c);
        }
        return copy;
    }

    private long currentUid() {
        Long id = UserContext.userId();
        return id == null ? com.bili.demo.data.UserStore.DEMO_USER_ID : id;
    }

    /**
     * 列表里不需要返回弹幕详情，这里复制一份「轻量版」，减少传输体积。
     * 注意：真实封面 / 视频地址一定要带上，前端才能显示真实封面帧并播放真实视频。
     */
    private Video lightCopy(Video v) {
        Video c = new Video();
        c.id = v.id;
        c.bvid = v.bvid;
        c.title = v.title;
        c.category = v.category;
        c.categoryCode = v.categoryCode;
        c.coverColor1 = v.coverColor1;
        c.coverColor2 = v.coverColor2;
        c.coverEmoji = v.coverEmoji;
        c.coverText = v.coverText;
        c.coverUrl = v.coverUrl;
        c.posterUrl = v.posterUrl;
        c.videoUrl = v.videoUrl;
        c.coverSource = v.coverSource;
        c.playable = v.playable;
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
        return c;
    }
}
