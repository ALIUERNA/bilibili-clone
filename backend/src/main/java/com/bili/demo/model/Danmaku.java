package com.bili.demo.model;

/**
 * 弹幕。
 */
public class Danmaku {

    public long id;
    public String text;      // 弹幕内容
    public double time;      // 出现在视频的第几秒
    public int mode;         // 1=滚动 4=底部固定 5=顶部固定
    public String color;     // 颜色
    public int fontSize;     // 字号
    public boolean self;     // 是否是自己发的

    public Danmaku() {
    }

    public Danmaku(long id, String text, double time, int mode, String color) {
        this.id = id;
        this.text = text;
        this.time = time;
        this.mode = mode;
        this.color = color;
        this.fontSize = 25;
    }
}
