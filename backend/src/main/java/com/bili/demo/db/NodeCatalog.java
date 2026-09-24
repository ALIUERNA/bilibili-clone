package com.bili.demo.db;

import com.bili.demo.model.ContentNode;

import java.util.ArrayList;
import java.util.List;

/**
 * 内容节点目录（前端导航 / 侧边栏 / 路由 / 首页分区都从这里读）。
 *
 * 这些节点是「内容节点」而不是简单的视频分区：
 *  - VIDEO：有视频内容的节点（动画 / 音乐 / 游戏 / 科技 …）
 *  - BANGUMI / LIVE / ARTICLE / ACTIVITY / COMMUNITY：其它形态的内容节点，
 *    目前是预留节点（is_placeholder = 1），前端会渲染成占位页，后续可以平滑扩展。
 */
public final class NodeCatalog {

    private NodeCatalog() {
    }

    /** code, name, emoji, nodeType, routePath, placeholder, sort */
    public static final String[][] NODES = {
            {"recommend", "推荐", "🌟", "VIDEO", "/", "0", "1"},
            {"hot", "热门", "🔥", "VIDEO", "/node/hot", "0", "2"},
            {"anime", "动画", "🎬", "VIDEO", "/node/anime", "0", "10"},
            {"bangumi", "番剧", "📺", "BANGUMI", "/bangumi", "0", "11"},
            {"guochuang", "国创", "🐉", "VIDEO", "/node/guochuang", "0", "12"},
            {"music", "音乐", "🎵", "VIDEO", "/node/music", "0", "13"},
            {"dance", "舞蹈", "💃", "VIDEO", "/node/dance", "0", "14"},
            {"game", "游戏", "🎮", "GAME", "/node/game", "0", "15"},
            {"knowledge", "知识", "📚", "VIDEO", "/node/knowledge", "0", "16"},
            {"tech", "科技", "💻", "VIDEO", "/node/tech", "0", "17"},
            {"sports", "体育", "🏀", "VIDEO", "/node/sports", "0", "18"},
            {"car", "汽车", "🚗", "VIDEO", "/node/car", "0", "19"},
            {"life", "生活", "🏠", "VIDEO", "/node/life", "0", "20"},
            {"food", "美食", "🍜", "VIDEO", "/node/food", "0", "21"},
            {"animal", "动物", "🐾", "VIDEO", "/node/animal", "0", "22"},
            {"kichiku", "鬼畜", "🎹", "VIDEO", "/node/kichiku", "0", "23"},
            {"fashion", "时尚", "👗", "VIDEO", "/node/fashion", "0", "24"},
            {"film", "影视", "🎥", "VIDEO", "/node/film", "0", "25"},
            {"documentary", "纪录片", "🎞️", "VIDEO", "/node/documentary", "1", "26"},
            {"information", "资讯", "📰", "VIDEO", "/node/information", "1", "27"},
            {"entertainment", "娱乐", "🎤", "VIDEO", "/node/entertainment", "1", "28"},
            {"movie", "电影", "🎦", "VIDEO", "/node/movie", "1", "29"},
            {"variety", "综艺", "🎭", "VIDEO", "/node/variety", "1", "30"},
            {"live", "直播", "📡", "LIVE", "/live", "1", "31"},
            {"column", "专栏", "📝", "ARTICLE", "/column", "1", "32"},
            {"activity", "活动", "🎉", "ACTIVITY", "/activity", "1", "33"},
            {"community", "社区中心", "🏛️", "COMMUNITY", "/community", "1", "34"},
            {"newstar", "新星计划", "✨", "ACTIVITY", "/node/newstar", "1", "35"},
    };

    /** parentCode, code, name, emoji, nodeType, routePath, placeholder, sort */
    public static final String[][] SUB_NODES = {
            {"anime", "anime_series", "连载动画", "📼", "VIDEO", "/node/anime_series", "1", "100"},
            {"anime", "anime_finish", "完结动画", "✅", "VIDEO", "/node/anime_finish", "1", "101"},
            {"anime", "anime_mad", "MAD·AMV", "🎞️", "VIDEO", "/node/anime_mad", "1", "102"},
            {"game", "game_standalone", "单机游戏", "🕹️", "GAME", "/node/game_standalone", "1", "110"},
            {"game", "game_esports", "电子竞技", "🏆", "GAME", "/node/game_esports", "1", "111"},
            {"game", "game_mobile", "手机游戏", "📱", "GAME", "/node/game_mobile", "1", "112"},
            {"tech", "tech_digital", "数码", "📷", "VIDEO", "/node/tech_digital", "1", "120"},
            {"tech", "tech_pc", "计算机技术", "🖥️", "VIDEO", "/node/tech_pc", "1", "121"},
            {"film", "film_review", "影视杂谈", "🎬", "VIDEO", "/node/film_review", "1", "130"},
            {"film", "film_cut", "影视剪辑", "✂️", "VIDEO", "/node/film_cut", "1", "131"},
            {"music", "music_cover", "翻唱", "🎤", "VIDEO", "/node/music_cover", "1", "140"},
            {"music", "music_vocaloid", "VOCALOID·UTAU", "🎤", "VIDEO", "/node/music_vocaloid", "1", "141"},
            {"knowledge", "knowledge_science", "科学科普", "🔬", "VIDEO", "/node/knowledge_science", "1", "150"},
            {"knowledge", "knowledge_humanity", "人文历史", "📜", "VIDEO", "/node/knowledge_humanity", "1", "151"},
            {"community", "community_help", "帮助中心", "❓", "COMMUNITY", "/node/community_help", "1", "160"},
    };

    public static List<ContentNode> all() {
        List<ContentNode> list = new ArrayList<>();
        for (String[] n : NODES) {
            ContentNode node = new ContentNode(n[0], n[1], n[2], n[3], n[4], Integer.parseInt(n[6]));
            node.placeholder = "1".equals(n[5]);
            list.add(node);
        }
        return list;
    }

    public static List<ContentNode> children() {
        List<ContentNode> list = new ArrayList<>();
        for (String[] n : SUB_NODES) {
            ContentNode node = new ContentNode(n[1], n[2], n[3], n[4], n[5], Integer.parseInt(n[7]));
            node.parentCode = n[0];
            node.placeholder = "1".equals(n[6]);
            list.add(node);
        }
        return list;
    }

    /** 分区名（中文）→ 节点 code，老数据迁移 / 兼容用 */
    public static String codeOfName(String name) {
        if (name == null) {
            return null;
        }
        for (String[] n : NODES) {
            if (n[1].equals(name)) {
                return n[0];
            }
        }
        return switch (name) {
            case "运动" -> "sports";
            default -> null;
        };
    }
}
