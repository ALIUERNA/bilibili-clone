package com.bili.demo.controller;

import com.bili.demo.data.DataStore;
import com.bili.demo.model.Banner;
import com.bili.demo.model.Category;
import com.bili.demo.model.PageResult;
import com.bili.demo.model.Video;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 首页 / 列表 / 排行榜相关接口。
 */
@RestController
@RequestMapping("/api")
public class HomeController {

    private final DataStore store;

    public HomeController(DataStore store) {
        this.store = store;
    }

    /** 首页聚合接口：轮播图 + 分区导航 + 热搜词 */
    @GetMapping("/home")
    public Map<String, Object> home() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("banners", store.banners);
        data.put("categories", store.categories);
        data.put("hotSearch", store.hotSearch);
        data.put("onlineCount", 12_845_392);   // 「当前在线人数」装饰用
        return data;
    }

    /** 分区列表 */
    @GetMapping("/categories")
    public List<Category> categories() {
        return store.categories;
    }

    /**
     * 视频列表（首页推荐流 / 分区页）
     * 例：/api/videos?category=游戏&page=1&size=24
     */
    @GetMapping("/videos")
    public PageResult<Video> videos(@RequestParam(required = false) String category,
                                    @RequestParam(defaultValue = "1") int page,
                                    @RequestParam(defaultValue = "24") int size) {
        List<Video> list = store.recommend(category);
        List<Video> copy = new ArrayList<>();
        for (Video v : list) {
            copy.add(lightCopy(v));
        }
        return PageResult.of(copy, page, size);
    }

    /**
     * 排行榜
     * 例：/api/rankings?type=all&limit=20
     */
    @GetMapping("/rankings")
    public List<Video> rankings(@RequestParam(defaultValue = "all") String type,
                                @RequestParam(defaultValue = "20") int limit) {
        List<Video> list = store.ranking(type, limit);
        List<Video> copy = new ArrayList<>();
        for (Video v : list) {
            copy.add(lightCopy(v));
        }
        return copy;
    }

    /**
     * 列表里不需要返回弹幕详情，这里复制一份「轻量版」，减少传输体积。
     */
    private Video lightCopy(Video v) {
        Video c = new Video();
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
        return c;
    }
}
