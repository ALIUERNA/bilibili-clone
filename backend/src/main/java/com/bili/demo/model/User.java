package com.bili.demo.model;

/**
 * 登录用户。
 */
public class User {

    public long id;
    public String username;    // 登录账号（唯一）
    public String email;       // 邮箱（唯一）
    public boolean hasPassword; // 是否设置过密码
    public boolean hasEmail;    // 是否绑定邮箱
    public String createdAt;    // 注册时间
    public String lastLoginAt;  // 最后登录时间
    public String name;        // 昵称（可以改）
    public String face;        // 头像 emoji（没上传图片时用这个）
    public String faceUrl;     // 上传的头像图片地址，例如 /api/files/avatar/xxx.png
    public String sign;        // 个性签名
    public int level;          // 等级
    public int exp;            // 当前等级已获得的经验
    public int expMax;         // 升到下一级需要的经验
    public String medal;       // 粉丝勋章名
    public int joinDays;       // 加入天数
    public long coins;         // 硬币
    public long bCoins;        // B 币
    public long following;     // 关注数
    public long followers;     // 粉丝数
    public long likes;         // 获赞数
    public boolean vip;        // 是否大会员
    public String vipLabel;    // 大会员文案
    public String lastCheckin; // 上次签到日期（yyyy-MM-dd）
    public boolean checkedToday; // 今天是否已签到

    public User() {
    }

    public User(long id, String name, String face, String sign) {
        this.id = id;
        this.name = name;
        this.face = face;
        this.sign = sign;
    }
}
