package com.bili.demo.data;

import com.bili.demo.model.*;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 全站模拟数据仓库。
 *
 * 说明：为了让项目「下载下来就能跑」，这里没有使用任何数据库，
 * 所有稿件 / UP主 / 弹幕 / 评论 / 番剧 都在应用启动时用代码生成，保存在内存里。
 * 重启一下服务，数据就恢复初始状态（比如你发的弹幕会消失），这对演示来说足够了。
 */
@Component
public class DataStore {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /** 分区定义：名称、图标、UP主昵称、头像、签名、粉丝数、等级、勋章 */
    private static final String[][] CATEGORY_DEFS = {
            {"动画", "🎬", "二次元观察员", "🌸", "每月新番速览，动画区十年老观众", "1862000", "Lv6", "观测者"},
            {"番剧", "📺", "追番小助手", "📺", "带你追完每一部值得看的番", "2431000", "Lv6", "追番人"},
            {"国创", "🐉", "国漫情报站", "🐉", "国漫崛起，我们一起见证", "987000", "Lv6", "国创粉"},
            {"音乐", "🎵", "电音小熊猫", "🎧", "翻唱 / 编曲 / 电音，每周五更新", "1320000", "Lv6", "音符"},
            {"舞蹈", "💃", "琉璃不吃糖", "💃", "宅舞 / 街舞，练舞日常记录", "764000", "Lv6", "舞者"},
            {"游戏", "🎮", "番茄不酸", "🍅", "主玩动作游戏，偶尔直播", "3210000", "Lv6", "番茄"},
            {"知识", "📚", "硬核科普局", "🔬", "把复杂的事讲成大白话", "2870000", "Lv6", "知识官"},
            {"科技", "💻", "影像研究所", "🎬", "数码测评 / 装机 / 影像技术", "4120000", "Lv6", "研究员"},
            {"运动", "🏀", "撸铁少女阿May", "🏋️", "健身 / 跑步，一起变强", "562000", "Lv6", "铁块"},
            {"汽车", "🚗", "老王说车", "🚗", "试驾 / 用车 / 养车，只说真话", "1130000", "Lv6", "老司机"},
            {"生活", "🏠", "阿柴的日常", "🐕", "记录普通人的一天", "890000", "Lv6", "柴友"},
            {"美食", "🍜", "熊猫厨房", "🐼", "家常菜复刻，深夜放毒", "1980000", "Lv6", "吃货"},
            {"动物", "🐾", "橘猫观察日记", "🐈", "一只很胖的橘猫和它的家人", "1540000", "Lv6", "猫奴"},
            {"鬼畜", "🎹", "鬼畜电音魔术师", "🎹", "纯手工调教，笑点密集预警", "645000", "Lv6", "素材"},
            {"时尚", "👗", "穿搭研究所", "👗", "平价穿搭 / 小个子显高公式", "432000", "Lv5", "潮人"},
            {"影视", "🎥", "影视拆解室", "🎥", "镜头语言 / 幕后解析 / 影评", "2210000", "Lv6", "影迷"},
    };

    /** 每个分区的稿件标题 */
    private static final String[][] VIDEO_TITLES = {
            {"【动画杂谈】这部十月新番为什么能让全网真香？",
             "我花300小时，把这部冷门神作剪成了一支OP",
             "2024年度动画大赏：这10部作品值得你补番",
             "当国配遇上日漫，效果居然这么好笑",
             "画了三年动画，我终于让角色动起来了【自制动画】"},
            {"本季度最强黑马，第一集就封神了",
             "追番指南：这5部新番千万不要错过",
             "这部番的OP我听了一百遍还是好听",
             "曾经的霸权番，现在看依然不过时",
             "新番速报：本周更新剧集吐槽合集"},
            {"国漫崛起！这三部国产动画的质量真的顶",
             "国产3D动画特效进化史，看完热血沸腾",
             "被低估的国创神作，值得一个爆火",
             "【国创】水墨风动画能有多美？",
             "国漫新番首播，画面直接拉满"},
            {"【翻唱】这首歌我循环了一整个夏天",
             "用一首钢琴曲，治愈你的深夜emo",
             "国产电音到底有多顶？听完你就懂了",
             "把老歌改成国风编曲是什么体验",
             "【纯音乐】写作业必听的30分钟白噪音"},
            {"【宅舞】第一次挑战这么难的编舞",
             "街舞battle现场，观众全都站起来了",
             "在家练了30天，身材变化有多大？",
             "中国风舞蹈到底有多美",
             "【翻跳】这个舞步我练了两百遍"},
            {"全BOSS无伤打法教学，看完你也能做到",
             "肝了500小时，我把这个游戏玩成了艺术",
             "新版本最强出装，看完直接上分",
             "【实况】这游戏把我吓到摔鼠标",
             "花100块能抽到想要的皮肤吗？"},
            {"为什么飞机窗户是圆的？一个细节救了无数人",
             "三分钟讲清楚：通货膨胀到底怎么影响你",
             "人类为什么会做梦？最新研究给出了答案",
             "把数学史讲成人话：从0到微积分",
             "为什么熬夜会让你越来越笨"},
            {"花2万块组装了一台4K剪辑主机，值吗？",
             "把家里的电费账单做成了可视化大屏",
             "实测：让AI帮我写了一天代码，结果如何",
             "2000元的国产手机，能拍出什么水平的视频",
             "自制机械键盘，手感居然比原厂还好"},
            {"每天100个深蹲，30天后发生了什么",
             "从零基础到跑完半马，我用了12周",
             "羽毛球反手发力教学，学会直接提升一个段位",
             "在家练核心的5个动作，新手也能做",
             "健身一年，我把体检报告逆转了"},
            {"30万预算，这台车到底值不值得买？",
             "开了三年的国产新能源，电池还剩多少电",
             "自驾2000公里，充电焦虑真实体验",
             "新手买车最容易踩的5个坑",
             "老车翻新记：花小钱办大事"},
            {"我的10平米小屋改造日记",
             "在县城生活一个月，一共花了多少钱",
             "00后独居生活vlog：今天也在好好吃饭",
             "断舍离之后，我的生活轻松了很多",
             "第一次一个人去旅行，遇见了这些事"},
            {"复刻餐厅招牌菜，成本只要10块钱",
             "深夜食堂：一个人也要好好吃饭",
             "挑战一口吃掉整只烤鸭，结果……",
             "在家做麻辣香锅，比外卖还好吃",
             "实测：网上爆火的懒人食谱真的靠谱吗"},
            {"养了三年的橘猫，体重终于突破10斤",
             "流浪猫第一次进屋，反应太可爱了",
             "带狗狗去海边，它彻底玩疯了",
             "仓鼠的深夜健身房，笑死我了",
             "猫在家到底在干什么？装了摄像头才知道"},
            {"【鬼畜】这可能是今年最洗脑的BGM",
             "用一百种方式演绎同一句台词",
             "全明星鬼畜：笑点密集预警",
             "把新闻播报做成电音是什么效果",
             "【调教】当经典配音遇上鬼畜素材"},
            {"150小个子穿搭公式，显高10cm",
             "学生党平价穿搭，全身不超过200块",
             "秋冬季叠穿教程，一件外套三种风格",
             "衣柜里最容易踩雷的5件单品",
             "重新整理衣柜，我找到了最适合自己的风格"},
            {"这部电影的镜头语言，值得反复研究",
             "10分钟看完年度最佳悬疑片",
             "沉浸式影评：为什么它能拿这么多奖",
             "被低估的国产电影，值得二刷",
             "幕后揭秘：一场戏拍了27遍"},
    };

    /** 每个分区的标签池 */
    private static final String[] COMMON_TAGS = {"原创", "自制", "4K", "高清", "干货", "教程", "vlog", "记录"};
    private static final String[] HOT_TAGS = {"热门", "推荐", "新人up", "播放量起飞", "神仙操作", "真实体验"};

    /** 封面渐变配色（B 站常见的糖果色） */
    private static final String[][] PALETTE = {
            {"#FF9A9E", "#FAD0C4"}, {"#A18CD1", "#FBC2EB"}, {"#84FAB0", "#8FD3F4"},
            {"#FFD3A5", "#FD6585"}, {"#43E97B", "#38F9D7"}, {"#FBC2EB", "#A6C1EE"},
            {"#F6D365", "#FDA085"}, {"#5EE7DF", "#B490CA"}, {"#F093FB", "#F5576C"},
            {"#4FACFE", "#00F2FE"}, {"#43CBFF", "#9708CC"}, {"#FFDEE9", "#B5FFFC"},
            {"#FFC3A0", "#FFAFBD"}, {"#C2E9FB", "#A1C4FD"}, {"#FDCBF1", "#E6DEE9"},
            {"#E0C3FC", "#8EC5FC"}, {"#F8B195", "#F67280"}, {"#C6FFDD", "#FBD786"},
    };

    /** 弹幕文案池 */
    private static final String[] DANMAKU_POOL = {
            "2333333", "前方高能", "爷青回", "泪目", "有内味了", "666666", "考古现场", "打卡",
            "第一次看到这种操作", "UP主加油！", "这个BGM绝了", "一键三连", "太强了吧", "笑死我了",
            "心疼UP主", "好看不火系列", "已三连", "循环了一百遍", "学到了学到了", "质量太高了",
            "建议加大力度", "终于等到更新", "声音好好听", "这剪辑绝了", "细节拉满", "看完直接关注",
            "压力给到UP这边", "我承认我笑了", "前排占座", "这个操作我学不会", "太治愈了",
            "每天一遍，烦恼再见", "收藏夹见", "万人血书求下期", "这波操作很有想法"
    };

    private static final String[] DANMAKU_COLORS = {"#FFFFFF", "#FFFFFF", "#FFFFFF", "#FF6699", "#FFF24B", "#00FFFF", "#FF9900"};

    /** 评论区用户 */
    private static final String[][] COMMENT_USERS = {
            {"奶茶续命选手", "🧋"}, {"今天也要早睡", "🌙"}, {"像素猎人", "🕹️"}, {"橙子汽水", "🍊"},
            {"键盘侠克星", "⌨️"}, {"猫猫头头", "🐱"}, {"不吃香菜", "🥬"}, {"深夜档观众", "🌃"},
            {"阿柴二号机", "🐕"}, {"小破站忠实用户", "📺"}, {"路过的观众", "🚶"}, {"摸鱼先锋", "🐟"},
            {"自习室常客", "📖"}, {"大熊猫饲养员", "🐼"}, {"柠檬酸不酸", "🍋"}, {"海边捡贝壳", "🐚"},
    };

    private static final String[] COMMENT_TEXTS = {
            "前排！这个质量真的顶，UP主辛苦了",
            "看完了，默默点了个三连，期待下期",
            "这个点还有人在看吗（2024还在看+1）",
            "讲得比我老师清楚多了，已收藏",
            "画质和剪辑都在线，支持一下",
            "笑死我了，最后那个转折真没想到",
            "建议做成系列，我想看更多",
            "已经看到第三遍了，还是很好看",
            "UP主的用心肉眼可见，加油！",
            "这就是天赋吗，我一个普通人只有羡慕的份",
            "评论区的朋友都好有才",
            "收藏夹吃灰系列（但我真的会看）",
            "这个BGM叫什么名字？求告知",
            "看完之后马上分享给了室友",
            "催更催更，什么时候出下期",
            "终于理解为什么这么多人推荐了",
    };

    private static final String[] REPLY_TEXTS = {
            "同求BGM", "已经三连了+1", "楼主说得对", "我也是看到第五遍才发现细节",
            "哈哈哈哈哈同款操作", "说得太真实了", "+1，我也这么觉得",
    };

    private static final String[] LOCATIONS = {"广东", "北京", "上海", "浙江", "江苏", "四川", "湖北", "山东", "湖南", "海外"};

    private static final String[] AGO_TEXTS = {"刚刚", "3分钟前", "18分钟前", "1小时前", "5小时前", "昨天", "2天前", "3天前", "5天前", "1周前", "2周前"};

    private static final String[] DYNAMIC_TEXTS = {
            "新视频已经上传啦，这次的素材拍了整整两个星期，大家记得来看～",
            "今天收到了粉丝寄来的信，看完真的有点感动，谢谢你们一直都在。",
            "设备升级完成！接下来画质会有明显提升，预告一下下周的新企划。",
            "刚下播，今天和大家聊得很开心，晚安啦。",
            "在剪辑室里待了 12 小时，终于把成片压出来了，先发个封面给大家看看。",
            "后台收到很多问拍摄参数的朋友，我整理了一份文档，评论区自取。",
            "今年的第一个小目标达成了，感谢每一个陪着我的你。",
            "拍外景遇到了超好的天气，随手拍了几张，你们更喜欢哪一张？",
    };

    // ================= 数据容器 =================

    public final List<Video> videos = new ArrayList<>();
    public final List<Up> ups = new ArrayList<>();
    public final List<Bangumi> bangumis = new ArrayList<>();
    public final List<Banner> banners = new ArrayList<>();
    public final List<Dynamic> dynamics = new ArrayList<>();
    public final List<Category> categories = new ArrayList<>();
    public final List<String> hotSearch = new ArrayList<>();
    public final Map<Long, List<Comment>> commentMap = new ConcurrentHashMap<>();

    private final Random rnd = new Random(20241108L);
    private final Random recRnd = new Random(9527L);
    private long aid = 100000L;
    private long danmakuId = 900000L;
    private long commentId = 700000L;
    private long dynamicId = 3000L;

    @PostConstruct
    public void init() {
        seedCategoriesAndUps();
        seedVideos();
        seedBanners();
        seedBangumi();
        seedDynamics();
        seedHotSearch();
        System.out.printf(">>> 模拟数据加载完成：%d 个分区 / %d 位UP主 / %d 条稿件 / %d 部番剧%n",
                categories.size(), ups.size(), videos.size(), bangumis.size());
    }

    // ================= 初始化数据 =================

    private void seedCategoriesAndUps() {
        for (int i = 0; i < CATEGORY_DEFS.length; i++) {
            String[] def = CATEGORY_DEFS[i];
            Up up = new Up(1000L + i, def[2], def[3], def[4]);
            up.fans = Long.parseLong(def[5]);
            up.videoCount = rnd.nextInt(400) + 80;
            up.likes = up.fans / 3 + rnd.nextInt(50000);
            up.level = def[6];
            up.medal = def[7];
            ups.add(up);
        }
        // 「推荐」「热门」是聚合页，没有独立稿件
        categories.add(new Category("recommend", "推荐", "🌟", 0));
        categories.add(new Category("hot", "热门", "🔥", 0));
        for (int i = 0; i < CATEGORY_DEFS.length; i++) {
            String[] def = CATEGORY_DEFS[i];
            categories.add(new Category(def[0], def[0], def[1], VIDEO_TITLES[i].length));
        }
    }

    private void seedVideos() {
        for (int c = 0; c < CATEGORY_DEFS.length; c++) {
            String categoryName = CATEGORY_DEFS[c][0];
            String emoji = CATEGORY_DEFS[c][1];
            Up up = ups.get(c);
            String[] titles = VIDEO_TITLES[c];
            for (int t = 0; t < titles.length; t++) {
                Video v = new Video(aid++, titles[t], categoryName);
                v.upId = up.id;
                v.upName = up.name;
                v.upFace = up.face;
                v.coverEmoji = t % 3 == 0 ? emoji : (t % 3 == 1 ? CATEGORY_DEFS[c][3] : emoji);
                v.coverText = shortCoverText(titles[t]);
                String[] colors = PALETTE[(c * 5 + t) % PALETTE.length];
                v.coverColor1 = colors[0];
                v.coverColor2 = colors[1];

                v.duration = 45 + rnd.nextInt(1500);
                v.views = 30000L + rnd.nextInt(3_200_000);
                v.danmakus = (int) (v.views / (120 + rnd.nextInt(260)));
                v.likes = (int) (v.views / (11 + rnd.nextInt(9)));
                v.coins = (int) (v.views / (30 + rnd.nextInt(30)));
                v.favorites = (int) (v.views / (18 + rnd.nextInt(20)));
                v.shares = (int) (v.views / (180 + rnd.nextInt(220)));
                v.replies = (int) (v.views / (300 + rnd.nextInt(500)));

                LocalDateTime pub = LocalDateTime.now()
                        .minusDays(rnd.nextInt(60))
                        .minusHours(rnd.nextInt(24))
                        .minusMinutes(rnd.nextInt(60));
                v.pubTime = pub.format(TIME_FMT);
                v.pubAgo = pubAgo(pub);

                v.tags.add(categoryName);
                v.tags.add(COMMON_TAGS[rnd.nextInt(COMMON_TAGS.length)]);
                if (rnd.nextInt(3) == 0) {
                    v.tags.add(HOT_TAGS[rnd.nextInt(HOT_TAGS.length)]);
                }
                v.desc = "这是「" + v.title + "」，本期视频把重点都讲清楚了，"
                        + "喜欢的话别忘了点赞投币收藏一键三连支持一下～\n"
                        + "视频内容仅供学习交流使用，素材版权归原作者所有，如有侵权请联系删除。\n"
                        + "商务合作请私信，感谢大家的支持！";

                v.danmakuList = buildDanmaku(v);
                videos.add(v);
                commentMap.put(v.id, buildComments(v));
            }
        }
    }

    /** 从标题里提炼几个字放到封面上（模仿 B 站封面上的大字） */
    private String shortCoverText(String title) {
        String t = title.replaceAll("[【】《》（）()：:？！!，,。.、……\\-—』「」]", "");
        return t.length() > 9 ? t.substring(0, 9) : t;
    }

    private List<Danmaku> buildDanmaku(Video v) {
        // 数量多一点，屏幕上才有 B 站那种「弹幕扑面而来」的感觉
        int count = 140 + rnd.nextInt(120);
        List<Danmaku> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String text = DANMAKU_POOL[rnd.nextInt(DANMAKU_POOL.length)];
            // 用 1.6 次方让弹幕在视频前半段更密集，更像真实视频
            double ratio = Math.pow(rnd.nextDouble(), 1.6);
            double time = Math.round(ratio * Math.max(10, v.duration - 2) * 10) / 10.0;
            int mode = rnd.nextInt(16) == 0 ? (rnd.nextBoolean() ? 4 : 5) : 1;
            String color = DANMAKU_COLORS[rnd.nextInt(DANMAKU_COLORS.length)];
            list.add(new Danmaku(danmakuId++, text, time, mode, color));
        }
        list.sort(Comparator.comparingDouble(d -> d.time));
        return list;
    }

    private List<Comment> buildComments(Video v) {
        int count = 6 + rnd.nextInt(9);
        List<Comment> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String[] u = COMMENT_USERS[rnd.nextInt(COMMENT_USERS.length)];
            Comment c = new Comment(commentId++, u[0], u[1],
                    COMMENT_TEXTS[rnd.nextInt(COMMENT_TEXTS.length)],
                    AGO_TEXTS[rnd.nextInt(AGO_TEXTS.length)],
                    rnd.nextInt(8000));
            c.floor = i + 1;
            c.location = LOCATIONS[rnd.nextInt(LOCATIONS.length)];
            c.likes = rnd.nextInt(5000);
            c.upLiked = rnd.nextInt(9) == 0;
            // 大约三分之一的评论带回复
            if (rnd.nextInt(3) == 0) {
                int replyCount = 1 + rnd.nextInt(3);
                for (int r = 0; r < replyCount; r++) {
                    String[] ru = COMMENT_USERS[rnd.nextInt(COMMENT_USERS.length)];
                    Comment reply = new Comment(commentId++, ru[0], ru[1],
                            REPLY_TEXTS[rnd.nextInt(REPLY_TEXTS.length)],
                            AGO_TEXTS[rnd.nextInt(AGO_TEXTS.length)],
                            rnd.nextInt(800));
                    reply.location = LOCATIONS[rnd.nextInt(LOCATIONS.length)];
                    c.replies.add(reply);
                }
            }
            list.add(c);
        }
        return list;
    }

    private void seedBanners() {
        // 挑播放量最高的稿件来做轮播
        List<Video> top = new ArrayList<>(videos);
        top.sort((a, b) -> Long.compare(b.views, a.views));
        String[][] meta = {
                {"新番黑马口碑爆棚", "本周更新第 8 集，追番人数突破 200 万", "热门"},
                {"年度最佳剪辑合集", "300 小时素材浓缩成 5 分钟，看完直接上头", "精选"},
                {"装机避坑指南", "两万块预算，这样配才不亏", "科技区"},
                {"跟着我一起吃", "复刻餐厅招牌菜，成本只要 10 块钱", "美食区"},
                {"健身 30 天挑战", "每天 100 个深蹲，身体变化有多大", "运动区"},
                {"国创年度片单", "这几部国产动画，质量真的顶", "国创"},
        };
        for (int i = 0; i < meta.length; i++) {
            Video v = top.get(i);
            banners.add(new Banner(i + 1L, meta[i][0], meta[i][1], v.coverEmoji,
                    v.coverColor1, v.coverColor2, meta[i][2], v.id));
        }
    }

    private void seedBangumi() {
        String[][] defs = {
                {"星海归途", "#A18CD1", "#FBC2EB", "🌌", "连载中", "日本", "12", "16", "2684000", "9.6", "科幻"},
                {"雾都食肆", "#FDA085", "#F6D365", "🍲", "连载中", "日本", "8", "12", "1423000", "9.2", "治愈"},
                {"剑起长歌", "#F093FB", "#F5576C", "⚔️", "已完结", "国产", "26", "26", "3157000", "9.4", "热血"},
                {"你的晴天预报", "#84FAB0", "#8FD3F4", "☀️", "连载中", "日本", "10", "13", "987000", "8.9", "日常"},
                {"山海拾遗录", "#5EE7DF", "#B490CA", "🐉", "连载中", "国产", "15", "24", "1876000", "9.1", "奇幻"},
                {"机械之心", "#4FACFE", "#00F2FE", "🤖", "已完结", "日本", "24", "24", "2231000", "9.3", "机甲"},
                {"小镇侦探社", "#FFD3A5", "#FD6585", "🔍", "连载中", "日本", "6", "12", "756000", "8.7", "悬疑"},
                {"花开彼岸", "#FBC2EB", "#A6C1EE", "🌸", "已完结", "日本", "13", "13", "1654000", "9.0", "恋爱"},
                {"长安十二时", "#FF9A9E", "#FAD0C4", "🏮", "已完结", "国产", "36", "36", "2987000", "9.5", "历史"},
                {"深海回声", "#43CBFF", "#9708CC", "🌊", "连载中", "日本", "9", "12", "654000", "8.8", "冒险"},
                {"厨房里的魔法师", "#C6FFDD", "#FBD786", "🧑‍🍳", "连载中", "日本", "4", "12", "432000", "8.6", "美食"},
                {"纸上江湖", "#FFC3A0", "#FFAFBD", "📜", "连载中", "国产", "11", "20", "1234000", "9.2", "武侠"},
        };
        for (int i = 0; i < defs.length; i++) {
            String[] d = defs[i];
            Bangumi b = new Bangumi();
            b.id = 5000L + i;
            b.title = d[0];
            b.color1 = d[1];
            b.color2 = d[2];
            b.emoji = d[3];
            b.status = d[4];
            b.area = d[5];
            b.episode = Integer.parseInt(d[6]);
            b.totalEpisode = Integer.parseInt(d[7]);
            b.followers = Long.parseLong(d[8]);
            b.score = Double.parseDouble(d[9]);
            b.tag = d[10];
            b.pubTime = "每周" + "一二三四五六日".charAt(rnd.nextInt(7)) + " 更新";
            b.desc = "《" + b.title + "》是一部" + b.area + "的" + b.tag + "题材作品，"
                    + "讲述了主角在命运转折点做出的选择，以及由此展开的一段旅程。画面与配乐均有不俗表现，值得一追。";
            bangumis.add(b);
        }
    }

    private void seedDynamics() {
        for (int i = 0; i < DYNAMIC_TEXTS.length; i++) {
            Up up = ups.get(i % ups.size());
            Dynamic d = new Dynamic();
            d.id = dynamicId++;
            d.upId = up.id;
            d.upName = up.name;
            d.upFace = up.face;
            d.content = DYNAMIC_TEXTS[i];
            d.time = AGO_TEXTS[rnd.nextInt(AGO_TEXTS.length)];
            d.emoji = up.face;
            String[] colors = PALETTE[rnd.nextInt(PALETTE.length)];
            d.color1 = colors[0];
            d.color2 = colors[1];
            d.likes = rnd.nextInt(20000);
            d.comments = rnd.nextInt(2000);
            if (rnd.nextBoolean()) {
                Video v = videos.get(rnd.nextInt(videos.size()));
                d.videoId = v.id;
                d.videoTitle = v.title;
            }
            dynamics.add(d);
        }
    }

    private void seedHotSearch() {
        hotSearch.addAll(List.of("新番速览", "全BOSS无伤", "装机避坑", "深夜食堂",
                "国漫崛起", "健身30天", "镜头语言", "AI写代码", "宅舞翻跳", "周末去哪玩"));
    }

    private String pubAgo(LocalDateTime pub) {
        long minutes = java.time.Duration.between(pub, LocalDateTime.now()).toMinutes();
        if (minutes < 60) {
            return Math.max(1, minutes) + "分钟前";
        }
        long hours = minutes / 60;
        if (hours < 24) {
            return hours + "小时前";
        }
        long days = hours / 24;
        if (days < 30) {
            return days + "天前";
        }
        return (days / 30) + "个月前";
    }

    // ================= 查询方法（供 Controller 调用） =================

    public Video findVideo(long id) {
        for (Video v : videos) {
            if (v.id == id) {
                return v;
            }
        }
        return null;
    }

    public Up findUp(long id) {
        for (Up u : ups) {
            if (u.id == id) {
                return u;
            }
        }
        return null;
    }

    /** 首页推荐：把稿件打散一点，看起来更像个性化推荐 */
    public List<Video> recommend(String category) {
        List<Video> list = new ArrayList<>();
        if (category == null || category.isBlank() || "recommend".equals(category)) {
            list.addAll(videos);
            Collections.shuffle(list, recRnd);
        } else if ("hot".equals(category)) {
            list.addAll(videos);
            list.sort((a, b) -> Long.compare(score(b), score(a)));
        } else {
            for (Video v : videos) {
                if (v.category.equals(category)) {
                    list.add(v);
                }
            }
            list.sort((a, b) -> Long.compare(b.views, a.views));
        }
        return list;
    }

    public static long score(Video v) {
        return v.views + (long) v.likes * 12 + (long) v.danmakus * 6 + (long) v.favorites * 8;
    }

    /** 相关推荐：优先同分区，不足时用热门补齐 */
    public List<Video> related(Video current, int limit) {
        List<Video> same = new ArrayList<>();
        for (Video v : videos) {
            if (v.category.equals(current.category) && v.id != current.id) {
                same.add(v);
            }
        }
        same.sort((a, b) -> Long.compare(b.views, a.views));
        if (same.size() < limit) {
            List<Video> others = new ArrayList<>(videos);
            others.removeIf(v -> v.id == current.id || same.contains(v));
            others.sort((a, b) -> Long.compare(score(b), score(a)));
            for (Video v : others) {
                if (same.size() >= limit) {
                    break;
                }
                same.add(v);
            }
        }
        return same.size() > limit ? new ArrayList<>(same.subList(0, limit)) : same;
    }

    /** 搜索：标题 / UP主 / 分区 / 标签 任意命中 */
    public List<Video> search(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return new ArrayList<>();
        }
        String kw = keyword.trim().toLowerCase();
        List<Video> hit = new ArrayList<>();
        for (Video v : videos) {
            boolean match = v.title.toLowerCase().contains(kw)
                    || v.upName.toLowerCase().contains(kw)
                    || v.category.toLowerCase().contains(kw);
            if (!match) {
                for (String tag : v.tags) {
                    if (tag.toLowerCase().contains(kw)) {
                        match = true;
                        break;
                    }
                }
            }
            if (match) {
                hit.add(v);
            }
        }
        hit.sort((a, b) -> Long.compare(score(b), score(a)));
        return hit;
    }

    /** 排行榜 */
    public List<Video> ranking(String type, int limit) {
        List<Video> list = new ArrayList<>(videos);
        if ("like".equals(type)) {
            list.sort((a, b) -> Integer.compare(b.likes, a.likes));
        } else if ("danmaku".equals(type)) {
            list.sort((a, b) -> Integer.compare(b.danmakus, a.danmakus));
        } else if ("coin".equals(type)) {
            list.sort((a, b) -> Integer.compare(b.coins, a.coins));
        } else if ("fav".equals(type)) {
            list.sort((a, b) -> Integer.compare(b.favorites, a.favorites));
        } else {
            list.sort((a, b) -> Long.compare(score(b), score(a)));
        }
        return list.size() > limit ? new ArrayList<>(list.subList(0, limit)) : list;
    }
}
