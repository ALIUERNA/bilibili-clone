package com.bili.demo.model;

/**
 * UP 主（用户）。
 */
public class Up {

    public long id;
    public String name;      // 昵称
    public String face;      // 头像（emoji）
    public String sign;      // 个性签名
    public long fans;        // 粉丝数
    public int videoCount;   // 投稿数
    public long likes;       // 获赞数
    public String level;     // 等级，如 Lv6
    public String medal;     // 粉丝勋章名
    public boolean followed; // 我是否已关注

    public Up() {
    }

    public Up(long id, String name, String face, String sign) {
        this.id = id;
        this.name = name;
        this.face = face;
        this.sign = sign;
    }
}
