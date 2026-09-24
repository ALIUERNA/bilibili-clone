package com.bili.demo.db;

import com.bili.demo.data.DataStore;
import com.bili.demo.model.*;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 内存数据仓库 &lt;-&gt; MySQL 的同步桥。
 *
 * 启动顺序：
 *   1. DataStore 先在内存里生成一套完整的演示数据（保证没有数据库也能跑）；
 *   2. 本类随后检查 MySQL：
 *      - 表是空的  → 把内存里的演示数据**写入 MySQL**（只插一次）；
 *      - 表里有数据 → 反过来用 MySQL 的数据**覆盖内存**，从此 MySQL 是唯一数据源；
 *      - 连不上    → 保持内存模式，只打印警告，不影响启动。
 *   3. 之后所有「播放量 +1 / 发弹幕 / 发评论 / 点赞」都会写回 MySQL（见 persistXxx 方法）。
 */
@Component
public class DataSyncService {

    private static final Logger log = LoggerFactory.getLogger(DataSyncService.class);

    private final Database db;
    private final UserRepository userRepo;
    private final ContentRepository contentRepo;
    private final DataStore store;

    private volatile boolean mysqlMode = false;

    public DataSyncService(Database db, UserRepository userRepo, ContentRepository contentRepo, DataStore store) {
        this.db = db;
        this.userRepo = userRepo;
        this.contentRepo = contentRepo;
        this.store = store;
    }

    @PostConstruct
    public void init() {
        db.probeNow();
        if (tryUpgradeToMysql()) {
            return;
        }
        log.warn(">>> 未检测到可用的 MySQL（或结构初始化失败），已进入内存模式；"
                + "后台每 15 秒自动重试，MySQL 恢复后无需重启即可切回数据库模式。");
    }

    /**
     * 是否处于 MySQL 模式。
     * 内存模式下会顺带重试一次——{@link Database#available()} 只在后台探测，不会阻塞请求线程，
     * 所以 MySQL 比应用晚启动、或中途重启，都能自动恢复而不用让用户等。
     */
    public boolean mysqlMode() {
        if (mysqlMode) {
            return true;
        }
        return tryUpgradeToMysql();
    }

    /** 尝试连接 MySQL、建表/迁移、导入内存数据；成功返回 true */
    private synchronized boolean tryUpgradeToMysql() {
        if (mysqlMode) {
            return true;
        }
        if (!db.available()) {
            return false;
        }
        if (!db.ensureSchema()) {
            log.warn(">>> 数据库结构初始化失败，继续内存模式。原因：{}", db.lastError());
            return false;
        }
        try {
            seedOrLoad();
            mysqlMode = true;
            log.info(">>> 已切换到 MySQL 模式：{} 个节点 / {} 条视频 / {} 条弹幕 / {} 条评论",
                    store.categories.size(), store.videos.size(), db.count("danmaku"), db.count("comments"));
            return true;
        } catch (Exception e) {
            log.error(">>> 数据库同步失败，继续内存模式：{}", e.getMessage(), e);
            return false;
        }
    }

    // ==================================================================
    // 首次初始化 / 读取
    // ==================================================================

    private void seedOrLoad() {
        userRepo.ensureDemoUser();
        // 演示账号密码：bili_demo / bili123456（用于体验「账号密码 + 图形验证码」和二维码确认）
        userRepo.ensureDemoPassword("bili123456");
        seedUpUsers();
        seedNodes();

        long videoCount = contentRepo.countVideos();
        if (videoCount <= 0) {
            seedContent();
        } else {
            loadContent();
        }
    }

    private void seedUpUsers() {
        for (Up up : store.ups) {
            userRepo.insertUpUser(up.id, up.name, up.face, up.sign, up.fans, up.medal,
                    parseLevel(up.level), up.likes);
        }
    }

    private int parseLevel(String level) {
        try {
            return Integer.parseInt(String.valueOf(level).replaceAll("[^0-9]", ""));
        } catch (Exception e) {
            return 6;
        }
    }

    /** 写入内容节点（存在则更新名字/图标，不会删除任何节点） */
    private void seedNodes() {
        for (ContentNode node : NodeCatalog.all()) {
            contentRepo.insertNode(node);
        }
        Map<String, Integer> ids = contentRepo.categoryIdByCode();
        for (ContentNode child : NodeCatalog.children()) {
            child.parentId = ids.get(child.parentCode);
            contentRepo.insertNode(child);
        }
    }

    private void seedContent() {
        // 演示数据里的分区是中文名（如「动画」），这里用 code + 中文名双索引映射到 categories.id
        Map<String, Integer> ids = contentRepo.categoryIdByCodeOrName();
        for (Video v : store.videos) {
            if (v.categoryCode == null) {
                v.categoryCode = NodeCatalog.codeOfName(v.category);
            }
        }
        contentRepo.insertVideos(store.videos, ids);
        log.info(">>> 首次运行：已把 {} 条演示稿件写入 MySQL", store.videos.size());

        int danmakuTotal = 0;
        for (Video v : store.videos) {
            if (v.danmakuList != null && !v.danmakuList.isEmpty()) {
                contentRepo.batchInsertDanmaku(v.id, v.danmakuList);
                danmakuTotal += v.danmakuList.size();
            }
            List<Comment> comments = store.commentMap.get(v.id);
            if (comments != null && !comments.isEmpty()) {
                contentRepo.batchInsertComments(v.id, comments);
            }
        }
        log.info(">>> 首次运行：已写入 {} 条弹幕", danmakuTotal);

        contentRepo.batchInsertBangumi(store.bangumis);
        contentRepo.batchInsertDynamics(store.dynamics);
        // 首次写入后，用数据库里的内容节点重建首页导航（视频/游戏/直播/番剧/专栏/活动/社区中心…）
        rebuildCategoryNav();
        // 再读一遍，确保内存里的分类名 / 封面地址和数据库一致
        List<Video> fresh = contentRepo.loadVideos();
        if (!fresh.isEmpty()) {
            store.videos.clear();
            store.videos.addAll(fresh);
            for (Video v : store.videos) {
                v.danmakuList = contentRepo.loadDanmaku(v.id);
                store.commentMap.put(v.id, contentRepo.loadComments(v.id));
            }
        }
    }

    private void loadContent() {
        // 兼容修复：老数据里 category_id 可能为空（只有 node_code），这里自动补上
        int repaired = contentRepo.repairCategoryLinks();
        if (repaired > 0) {
            log.info(">>> 已修复 {} 条稿件的分区关联", repaired);
        }
        List<Video> videos = contentRepo.loadVideos();
        if (videos.isEmpty()) {
            return;
        }
        store.videos.clear();
        store.videos.addAll(videos);
        store.commentMap.clear();
        int danmaku = 0;
        for (Video v : store.videos) {
            v.danmakuList = contentRepo.loadDanmaku(v.id);
            danmaku += v.danmakuList.size();
            store.commentMap.put(v.id, contentRepo.loadComments(v.id));
        }
        log.info(">>> 已从 MySQL 读取 {} 条稿件 / {} 条弹幕", store.videos.size(), danmaku);

        List<Bangumi> bangumis = contentRepo.loadBangumi();
        if (!bangumis.isEmpty()) {
            store.bangumis.clear();
            store.bangumis.addAll(bangumis);
        }
        List<Dynamic> dynamics = contentRepo.loadDynamics();
        if (!dynamics.isEmpty()) {
            store.dynamics.clear();
            store.dynamics.addAll(dynamics);
        }
        List<Up> ups = userRepo.loadUps();
        if (!ups.isEmpty()) {
            store.ups.clear();
            store.ups.addAll(ups);
        }
        rebuildCategoryNav();
        // 让首页轮播优先选用有真实视频 / 真实封面帧的稿件
        store.reseedBannersForMedia();
    }

    /** 用数据库里的内容节点重建首页分区导航 */
    public void rebuildCategoryNav() {
        try {
            List<ContentNode> nodes = contentRepo.nodes();
            if (nodes.isEmpty()) {
                return;
            }
            Map<String, Integer> counts = contentRepo.videoCountByNode();
            store.categories.clear();
            for (ContentNode n : nodes) {
                if (n.parentId != null) {
                    continue;   // 导航只展示一级节点
                }
                Category c = new Category(n.code, n.name, n.emoji == null ? "🎬" : n.emoji,
                        counts.getOrDefault(n.code, 0));
                store.categories.add(c);
            }
        } catch (Exception e) {
            log.warn("重建分区导航失败: {}", e.getMessage());
        }
    }

    // ==================================================================
    // 写回（供 Controller 调用）
    // ==================================================================

    public void saveVideoStats(Video v) {
        if (mysqlMode && v != null) {
            contentRepo.updateVideoStats(v);
        }
    }

    public void saveDanmaku(long videoId, Long userId, Danmaku d) {
        if (mysqlMode) {
            contentRepo.insertDanmaku(videoId, userId, d);
        }
    }

    public long saveComment(long videoId, Long userId, Comment c) {
        if (mysqlMode) {
            return contentRepo.insertComment(videoId, userId, c);
        }
        return 0;
    }

    public void saveAction(long userId, long videoId, String type, boolean add) {
        if (mysqlMode && userId > 0) {
            contentRepo.saveAction(userId, videoId, type, add);
        }
    }

    public Map<Long, Set<String>> loadActions(long userId) {
        if (mysqlMode && userId > 0) {
            return contentRepo.loadActions(userId);
        }
        return new LinkedHashMap<>();
    }

    public void saveHistory(long userId, long videoId, int progressSec) {
        if (mysqlMode && userId > 0) {
            userRepo.upsertHistory(userId, videoId, progressSec);
        }
    }

    public void persistUser(User u) {
        if (mysqlMode && u != null) {
            userRepo.updateStats(u);
        }
    }

    public Database database() {
        return db;
    }
}
