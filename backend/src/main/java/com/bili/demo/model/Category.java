package com.bili.demo.model;

/**
 * 首页顶部的分区（导航）项。
 */
public class Category {

    public String key;    // 英文标识，用于接口筛选
    public String name;   // 中文名，如「游戏」
    public String emoji;  // 图标
    public int count;     // 该分区的稿件数量

    public Category() {
    }

    public Category(String key, String name, String emoji, int count) {
        this.key = key;
        this.name = name;
        this.emoji = emoji;
        this.count = count;
    }
}
