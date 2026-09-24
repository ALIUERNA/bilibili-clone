package com.bili.demo.data;

import com.bili.demo.auth.UserContext;
import com.bili.demo.config.UploadPaths;
import com.bili.demo.db.DataSyncService;
import com.bili.demo.db.UserRepository;
import com.bili.demo.model.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 当前登录用户的状态中心。
 *
 * 两种运行模式：
 *  - MySQL 模式（默认）：用户资料 / 等级经验 / 硬币 / 签到 全部读写 users 表，
 *    按请求里的 token 区分不同用户（多个账号同时在线也不会串号）；
 *  - 内存模式（数据库连不上时兜底）：退回原来的 uploads/profile.json，保证项目依然能跑。
 *
 * 老代码的调用方式完全没变：userStore.get() / addExp() / checkin() / stats() / uploadPath() …
 */
@Component
public class UserStore {

    private static final Logger log = LoggerFactory.getLogger(UserStore.class);
    private static final ObjectMapper JSON = new ObjectMapper();
    /** 演示账号：没有登录时，老功能（点赞、发弹幕等）依然用这个账号，保持向后兼容 */
    public static final long DEMO_USER_ID = 90001L;

    private final UserRepository userRepo;
    private final DataSyncService sync;
    private final UploadPaths uploadPaths;

    /** userId → User，避免每次请求都查库 */
    private final Map<Long, User> cache = new ConcurrentHashMap<>();

    private Path profileFile;
    private User jsonFallback;

    public UserStore(UserRepository userRepo, DataSyncService sync, UploadPaths uploadPaths) {
        this.userRepo = userRepo;
        this.sync = sync;
        this.uploadPaths = uploadPaths;
    }

    @PostConstruct
    public void init() {
        Path dir = uploadPaths.root();
        this.profileFile = dir.resolve("profile.json");
        this.jsonFallback = loadJson();
        if (jsonFallback == null) {
            jsonFallback = createDefaultUser();
            saveJson();
        }
        refreshCheckinState(jsonFallback);
        log.info(">>> 用户资料目录：{}（模式：{}）", dir, sync.mysqlMode() ? "MySQL" : "内存兜底");
    }

    public boolean isMysqlMode() {
        return sync.mysqlMode();
    }

    /** 当前请求对应的用户；未登录时返回演示账号 */
    public User get() {
        Long id = UserContext.userId();
        if (id == null) {
            id = DEMO_USER_ID;
        }
        User user = resolve(id);
        refreshCheckinState(user);
        return user;
    }

    /** 按 id 取用户（个人中心、空间页用） */
    public User resolve(long id) {
        if (sync.mysqlMode()) {
            return cache.computeIfAbsent(id, key -> userRepo.findById(key).orElseGet(() -> {
                userRepo.ensureDemoUser();
                return userRepo.findById(key).orElseGet(() -> userRepo.findById(DEMO_USER_ID).orElse(jsonFallback));
            }));
        }
        return jsonFallback;
    }

    public void evict(long userId) {
        cache.remove(userId);
    }

    private User createDefaultUser() {
        User u = new User(DEMO_USER_ID, "a哩a哩萌新", "😎", "这个人很懒，什么都没写~");
        u.level = 5;
        u.exp = 3860;
        u.expMax = 4800;
        u.medal = "萌新勋章";
        u.joinDays = 168;
        u.coins = 328;
        u.bCoins = 1200;
        u.following = 186;
        u.followers = 42;
        u.likes = 1288;
        u.vip = true;
        u.vipLabel = "年度大会员";
        u.lastCheckin = "";
        return u;
    }

    private User loadJson() {
        try {
            if (Files.exists(profileFile)) {
                return JSON.readValue(profileFile.toFile(), User.class);
            }
        } catch (Exception e) {
            log.warn("读取用户资料失败，使用默认资料：{}", e.getMessage());
        }
        return null;
    }

    /** 保存当前用户（MySQL 模式写数据库，内存模式写 JSON） */
    public synchronized void save() {
        save(get());
    }

    public synchronized void save(User user) {
        if (user == null) {
            return;
        }
        if (sync.mysqlMode()) {
            userRepo.updateStats(user);
        } else {
            jsonFallback = user;
            saveJson();
        }
    }

    private void saveJson() {
        try {
            Files.createDirectories(profileFile.getParent());
            JSON.writerWithDefaultPrettyPrinter().writeValue(profileFile.toFile(), jsonFallback);
        } catch (Exception e) {
            log.warn("保存用户资料失败：{}", e.getMessage());
        }
    }

    private void refreshCheckinState(User user) {
        if (user != null) {
            user.checkedToday = LocalDate.now().toString().equals(user.lastCheckin);
        }
    }

    /** 上传头像后把图片地址记下来 */
    public synchronized void setAvatarUrl(String url) {
        User user = get();
        user.faceUrl = url;
        if (sync.mysqlMode()) {
            userRepo.updateAvatar(user.id, url);
        } else {
            saveJson();
        }
    }

    /** 恢复成 emoji 头像 */
    public synchronized void clearAvatar() {
        User user = get();
        user.faceUrl = null;
        if (sync.mysqlMode()) {
            userRepo.updateAvatar(user.id, null);
        } else {
            saveJson();
        }
    }

    /** 修改昵称和签名 */
    public synchronized Map<String, Object> updateProfile(String name, String sign) {
        User user = get();
        if (name != null && !name.isBlank()) {
            String n = name.trim();
            user.name = n.substring(0, Math.min(20, n.length()));
        }
        if (sign != null) {
            String s = sign.trim();
            user.sign = s.length() > 60 ? s.substring(0, 60) : s;
        }
        if (sync.mysqlMode()) {
            userRepo.updateBasic(user.id, user.name, user.sign, user.faceUrl, user.face);
        } else {
            saveJson();
        }
        Map<String, Object> data = new java.util.LinkedHashMap<>();
        data.put("name", user.name);
        data.put("sign", user.sign);
        return data;
    }

    /** 加经验；满了就升级 */
    public synchronized boolean addExp(int amount) {
        if (amount <= 0) {
            return false;
        }
        User user = get();
        user.exp += amount;
        boolean levelUp = false;
        while (user.exp >= user.expMax) {
            user.exp -= user.expMax;
            user.level = Math.min(6, user.level + 1);
            user.expMax = nextExpMax(user.level);
            levelUp = true;
            if (user.level >= 6) {
                user.exp = Math.min(user.exp, user.expMax);
                break;
            }
        }
        save(user);
        return levelUp;
    }

    private int nextExpMax(int level) {
        return switch (level) {
            case 1 -> 200;
            case 2 -> 1500;
            case 3 -> 4500;
            case 4 -> 10800;
            case 5 -> 28800;
            default -> 50000;
        };
    }

    /** 每日签到：经验 +10，硬币 +5 */
    public synchronized Map<String, Object> checkin() {
        User user = get();
        Map<String, Object> data = new java.util.LinkedHashMap<>();
        if (user.checkedToday) {
            data.put("success", false);
            data.put("message", "今天已经签到过了，明天再来吧~");
            data.put("user", user);
            return data;
        }
        user.lastCheckin = LocalDate.now().toString();
        user.checkedToday = true;
        user.coins += 5;
        boolean levelUp = addExp(10);
        data.put("success", true);
        data.put("message", "签到成功！经验 +10，硬币 +5");
        data.put("expGain", 10);
        data.put("levelUp", levelUp);
        data.put("user", user);
        return data;
    }

    /** 展示字段（含经验进度）。未登录时 user 为 null，前端据此进入游客态 */
    public Map<String, Object> stats() {
        return stats(UserContext.loggedIn() ? get() : null);
    }

    public Map<String, Object> stats(User user) {
        Map<String, Object> data = new java.util.LinkedHashMap<>();
        data.put("login", UserContext.loggedIn());
        data.put("user", user);
        if (user != null) {
            data.put("expPercent", user.expMax == 0 ? 0 : Math.round(user.exp * 1000.0 / user.expMax) / 10.0);
            data.put("nextLevelExp", Math.max(0, user.expMax - user.exp));
        }
        return data;
    }

    /** 上传目录（绝对路径） */
    public Path uploadPath() {
        return uploadPaths.root();
    }

    public File profileDir() {
        return uploadPath().toFile();
    }
}
