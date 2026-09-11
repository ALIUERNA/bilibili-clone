package com.bili.demo.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 评论（美食家 / 楼层）。
 */
public class Comment {

    public long id;
    public long userId;
    public String user;        // 昵称
    public String face;        // 头像（emoji）
    public String faceUrl;     // 上传的头像图片（有值就优先显示图片）
    public String content;     // 内容
    public String time;        // 相对时间
    public String location;    // IP 属地
    public int likes;          // 点赞数
    public int floor;          // 楼层
    public boolean liked;      // 我是否点过赞
    public boolean upLiked;    // 是否被 UP 主点赞
    public List<Comment> replies = new ArrayList<>();

    public Comment() {
    }

    public Comment(long id, String user, String face, String content, String time, int likes) {
        this.id = id;
        this.user = user;
        this.face = face;
        this.content = content;
        this.time = time;
        this.likes = likes;
    }
}
