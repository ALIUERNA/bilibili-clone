package com.bili.demo.data;

import com.bili.demo.model.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 当前登录用户的状态。
 *
 * 和视频数据不同，用户资料（昵称 / 头像 / 等级经验 / 硬币 / 签到记录）
 * 会被写进 uploads/profile.json，所以重启服务后还在。
 */
@Component
public class UserStore {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Value("${bili.upload-dir:./uploads}")
    private String uploadDir;

    private User user;
    private Path profileFile;

    @PostConstruct
    public void init() {
        Path dir = Paths.get(uploadDir).toAbsolutePath().normalize();
        this.profileFile = dir.resolve("profile.json");
        this.user = load();
        if (user == null) {
            user = createDefaultUser();
            save();
        }
        refreshCheckinState();
        System.out.println(">>> 用户资料文件：" + profileFile);
    }

    private User createDefaultUser() {
        User u = new User(90001L, "哔哩哔哩萌新", "😎", "这个人很懒，什么都没写~");
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

    private User load() {
        try {
            if (Files.exists(profileFile)) {
                return JSON.readValue(profileFile.toFile(), User.class);
            }
        } catch (Exception e) {
            System.out.println(">>> 读取用户资料失败，使用默认资料：" + e.getMessage());
        }
        return null;
    }

    public synchronized void save() {
        try {
            Files.createDirectories(profileFile.getParent());
            JSON.writerWithDefaultPrettyPrinter().writeValue(profileFile.toFile(), user);
        } catch (Exception e) {
            System.out.println(">>> 保存用户资料失败：" + e.getMessage());
        }
    }

    public User get() {
        refreshCheckinState();
        return user;
    }

    private void refreshCheckinState() {
        user.checkedToday = LocalDate.now().toString().equals(user.lastCheckin);
    }

    /** 上传头像后把图片地址记下来 */
    public synchronized void setAvatarUrl(String url) {
        user.faceUrl = url;
        save();
    }

    /** 恢复成 emoji 头像 */
    public synchronized void clearAvatar() {
        user.faceUrl = null;
        save();
    }

    /** 修改昵称和签名 */
    public synchronized Map<String, Object> updateProfile(String name, String sign) {
        if (name != null && !name.isBlank()) {
            user.name = name.trim().substring(0, Math.min(20, name.trim().length()));
        }
        if (sign != null) {
            String s = sign.trim();
            user.sign = s.length() > 60 ? s.substring(0, 60) : s;
        }
        save();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("name", user.name);
        data.put("sign", user.sign);
        return data;
    }

    /**
     * 加经验。经验满了就升级（和 B 站一样，升级会补满下一级的经验条）。
     *
     * @return 本次是否升级
     */
    public synchronized boolean addExp(int amount) {
        if (amount <= 0) {
            return false;
        }
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
        save();
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
        refreshCheckinState();
        Map<String, Object> data = new LinkedHashMap<>();
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

    /** 变成「已登录用户」需要的展示字段（顺便算一下加入天数） */
    public Map<String, Object> stats() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("user", user);
        data.put("expPercent", user.expMax == 0 ? 0 : Math.round(user.exp * 1000.0 / user.expMax) / 10.0);
        data.put("nextLevelExp", Math.max(0, user.expMax - user.exp));
        return data;
    }

    /** 上传目录（绝对路径） */
    public Path uploadPath() {
        return Paths.get(uploadDir).toAbsolutePath().normalize();
    }

    public File profileDir() {
        return uploadPath().toFile();
    }

    public long daysSinceJoin() {
        return ChronoUnit.DAYS.between(LocalDate.now().minusDays(user.joinDays), LocalDate.now());
    }
}
