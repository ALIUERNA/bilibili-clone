package com.bili.demo.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 视频（稿件）实体。
 * 字段全部用 public，是为了让初学者一眼看懂 JSON 结构，实际项目里建议用 getter/setter。
 */
public class Video {

    public long id;                  // 稿件 id（aid）
    public String bvid;              // BV 号，如 BV1xx411c7mD
    public String title;             // 标题
    public String category;          // 所属分区，如「游戏」
    public String categoryCode;      // 分区 / 内容节点编码，如 game

    /** 真实媒体文件（没有视频文件时为 null，前端会自动用渐变占位图兜底） */
    public String coverUrl;          // 真实封面帧，例如 /api/files/cover/1.jpg
    public String posterUrl;         // 播放器封面图
    public String videoUrl;          // 真实视频文件，例如 /api/files/video/1.mp4
    public String coverSource;       // FFMPEG_FRAME / UPLOAD / PLACEHOLDER
    public boolean playable;         // 是否有真实视频文件

    public String coverColor1;       // 封面渐变色 1
    public String coverColor2;       // 封面渐变色 2
    public String coverEmoji;        // 封面上的大图标
    public String coverText;         // 封面上的短文字

    public int duration;             // 时长（秒）
    public long views;               // 播放量
    public int danmakus;             // 弹幕数
    public int likes;                // 点赞数
    public int coins;                // 投币数
    public int favorites;            // 收藏数
    public int shares;               // 分享数
    public int replies;              // 评论数

    public long upId;                // UP 主 id
    public String upName;            // UP 主昵称
    public String upFace;            // UP 主头像（emoji）

    public String pubTime;           // 发布时间，如 2024-11-08 20:15
    public String pubAgo;            // 相对时间，如 3天前

    public String desc;              // 简介
    public List<String> tags = new ArrayList<>();

    /** 当前登录用户对这条视频的操作状态（演示用，内存里改） */
    public boolean liked;
    public boolean coined;
    public boolean favored;
    public boolean followed;

    /** 弹幕列表（详情接口才返回） */
    public List<Danmaku> danmakuList = new ArrayList<>();

    public Video() {
    }

    public Video(long id, String title, String category) {
        this.id = id;
        this.title = title;
        this.category = category;
        this.bvid = toBvid(id);
    }

    /** 根据 aid 生成一个像模像样的 BV 号 */
    public static String toBvid(long aid) {
        String s = Long.toString(aid * 1000003L + 411234567890L, 36).toUpperCase();
        while (s.length() < 10) {
            s = "0" + s;
        }
        return "BV1" + s.substring(s.length() - 9);
    }
}
