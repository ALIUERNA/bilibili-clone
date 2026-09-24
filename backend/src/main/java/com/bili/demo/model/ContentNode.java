package com.bili.demo.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 内容节点（分区 / 预留节点）。
 * 视频、游戏、直播、番剧、动画、音乐…… 都是节点，nodeType 决定前端用哪种页面渲染。
 */
public class ContentNode {

    public int id;
    public String code;          // 英文标识，如 game
    public String name;          // 中文名，如 游戏
    public String emoji;         // 图标
    public String description;   // 节点描述
    public String nodeType;      // VIDEO/GAME/LIVE/BANGUMI/ARTICLE/ACTIVITY/COMMUNITY
    public Integer parentId;     // 上级节点
    public String routePath;     // 前端路由，如 /node/game
    public int sortOrder;
    public boolean placeholder;  // true = 预留节点（占位页）
    public boolean isNew;
    public boolean enabled;
    public String parentCode;    // 仅在内存装配子节点时使用
    public int videoCount;       // 该节点下稿件数量
    public List<ContentNode> children = new ArrayList<>();

    public ContentNode() {
    }

    public ContentNode(String code, String name, String emoji, String nodeType, String routePath, int sortOrder) {
        this.code = code;
        this.name = name;
        this.emoji = emoji;
        this.nodeType = nodeType;
        this.routePath = routePath;
        this.sortOrder = sortOrder;
    }
}
