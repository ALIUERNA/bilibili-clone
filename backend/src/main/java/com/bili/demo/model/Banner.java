package com.bili.demo.model;

/**
 * 首页轮播图。
 */
public class Banner {

    public long id;
    public String title;      // 主标题
    public String subtitle;   // 副标题
    public String emoji;      // 配图 emoji
    public String color1;
    public String color2;
    public String tag;        // 角标，如「热门」「新番」
    public long videoId;      // 点击后跳转的视频

    public Banner() {
    }

    public Banner(long id, String title, String subtitle, String emoji,
                  String color1, String color2, String tag, long videoId) {
        this.id = id;
        this.title = title;
        this.subtitle = subtitle;
        this.emoji = emoji;
        this.color1 = color1;
        this.color2 = color2;
        this.tag = tag;
        this.videoId = videoId;
    }
}
