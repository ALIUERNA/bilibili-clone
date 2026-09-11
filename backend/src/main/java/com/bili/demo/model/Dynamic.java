package com.bili.demo.model;

/**
 * 个人空间里的「动态」。
 */
public class Dynamic {

    public long id;
    public long upId;
    public String upName;
    public String upFace;
    public String content;     // 动态文字
    public String time;        // 相对时间
    public String emoji;       // 配图
    public String color1;
    public String color2;
    public int likes;
    public int comments;
    public boolean liked;
    public long videoId;       // 关联的投稿（0 表示没有）
    public String videoTitle;

    public Dynamic() {
    }
}
