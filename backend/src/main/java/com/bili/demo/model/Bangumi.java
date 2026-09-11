package com.bili.demo.model;

/**
 * 番剧。
 */
public class Bangumi {

    public long id;
    public String title;
    public String color1;
    public String color2;
    public String emoji;
    public String status;        // 连载中 / 已完结
    public String area;          // 日本 / 国产
    public int episode;          // 更新到第几集
    public int totalEpisode;     // 总集数
    public long followers;       // 追番人数
    public double score;         // 评分
    public String tag;           // 标签，如 奇幻 / 热血
    public String desc;          // 简介
    public boolean followed;     // 我是否已追番
    public String pubTime;       // 更新时间

    public Bangumi() {
    }
}
