package com.bili.demo.db;

import com.bili.demo.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Component;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 用户、登录认证、用户中心（历史 / 收藏 / 消息 / 设置 / 投稿）的数据库访问层。
 * 全部使用 JdbcTemplate 手写 SQL，不引入 ORM，跟项目原有的「轻量」风格保持一致。
 */
@Component
public class UserRepository {

    private static final Logger log = LoggerFactory.getLogger(UserRepository.class);
    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final Database db;

    public UserRepository(Database db) {
        this.db = db;
    }

    private JdbcTemplate j() {
        return db.jdbc();
    }

    // ==================================================================
    // 验证码的内存兜底
    //
    // 图形验证码和邮箱验证码原本是「无条件写 MySQL」的，于是数据库一挂，
    // 登录页就会直接 500——这和项目「没有数据库也能跑」的定位是矛盾的。
    // 这里给两张验证码表各配一份内存实现：数据库可用时走 MySQL，
    // 不可用时自动落到内存（进程重启即失效，对演示场景完全够用）。
    // ==================================================================

    private final Map<String, Code> captchaMemory = new ConcurrentHashMap<>();
    private final Map<String, Code> emailCodeMemory = new ConcurrentHashMap<>();
    /** token -> [userId, 过期时间毫秒] */
    private final Map<String, long[]> tokenMemory = new ConcurrentHashMap<>();
    /** qrId -> 二维码会话 */
    private final Map<String, QrSession> qrMemory = new ConcurrentHashMap<>();

    /** 顺手清掉过期的登录令牌，避免内存无限增长 */
    private void pruneTokens() {
        if (tokenMemory.size() < 128) {
            return;
        }
        long now = System.currentTimeMillis();
        tokenMemory.entrySet().removeIf(e -> e.getValue()[1] < now);
    }

    /** 内存里的一条验证码 */
    private static final class Code {
        final String value;
        final String purpose;
        final long expiresAt;
        volatile boolean used;

        Code(String value, String purpose, long ttlSeconds) {
            this.value = value;
            this.purpose = purpose;
            this.expiresAt = System.currentTimeMillis() + ttlSeconds * 1000L;
        }

        boolean matches(String input, String wantPurpose) {
            return !used
                    && expiresAt > System.currentTimeMillis()
                    && purpose.equals(wantPurpose)
                    && value.equalsIgnoreCase(input.trim());
        }
    }

    /** 顺手清掉过期的，避免内存无限增长 */
    private void prune(Map<String, Code> store) {
        if (store.size() < 256) {
            return;
        }
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<String, Code>> it = store.entrySet().iterator();
        while (it.hasNext()) {
            Code c = it.next().getValue();
            if (c.expiresAt < now || c.used) {
                it.remove();
            }
        }
    }

    // ==================================================================
    // users
    // ==================================================================

    private static final RowMapper<User> USER_MAPPER = (rs, i) -> {
        User u = new User();
        u.id = rs.getLong("id");
        u.username = rs.getString("username");
        u.email = rs.getString("email");
        u.hasEmail = u.email != null && !u.email.isBlank();
        u.hasPassword = rs.getString("password_hash") != null;
        u.name = rs.getString("nickname");
        u.face = rs.getString("avatar_emoji") == null ? "😀" : rs.getString("avatar_emoji");
        u.faceUrl = rs.getString("avatar_url");
        u.sign = rs.getString("sign");
        u.level = rs.getInt("level");
        u.exp = rs.getInt("exp");
        u.expMax = rs.getInt("exp_max");
        u.coins = rs.getLong("coins");
        u.bCoins = rs.getLong("b_coins");
        u.following = rs.getLong("following_count");
        u.followers = rs.getLong("follower_count");
        u.likes = rs.getLong("like_count");
        u.vip = rs.getBoolean("vip");
        u.vipLabel = rs.getString("vip_label");
        u.medal = rs.getString("medal");
        u.joinDays = rs.getInt("join_days");
        Timestamp last = rs.getTimestamp("last_checkin");
        u.lastCheckin = last == null ? "" : last.toLocalDateTime().toLocalDate().toString();
        u.checkedToday = LocalDate.now().toString().equals(u.lastCheckin);
        Timestamp created = rs.getTimestamp("created_at");
        u.createdAt = created == null ? null : created.toLocalDateTime().format(DT);
        Timestamp login = rs.getTimestamp("last_login_at");
        u.lastLoginAt = login == null ? null : login.toLocalDateTime().format(DT);
        if (u.joinDays <= 0 && created != null) {
            u.joinDays = (int) Math.max(1, java.time.temporal.ChronoUnit.DAYS
                    .between(created.toLocalDateTime().toLocalDate(), LocalDate.now()));
        }
        if (u.medal == null || u.medal.isBlank()) {
            u.medal = u.level >= 5 ? "铁粉勋章" : "萌新勋章";
        }
        return u;
    };

    private static final String USER_COLS = "id, username, email, password_hash, nickname, avatar_url, " +
            "avatar_emoji, sign, level, exp, exp_max, coins, b_coins, following_count, follower_count, " +
            "like_count, vip, vip_label, medal, join_days, last_checkin, created_at, last_login_at";

    public Optional<User> findById(long id) {
        try {
            List<User> list = j().query("SELECT " + USER_COLS + " FROM users WHERE id = ?", USER_MAPPER, id);
            return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public Optional<User> findByUsername(String username) {
        try {
            List<User> list = j().query("SELECT " + USER_COLS + " FROM users WHERE username = ?", USER_MAPPER, username);
            return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public Optional<User> findByEmail(String email) {
        try {
            List<User> list = j().query("SELECT " + USER_COLS + " FROM users WHERE email = ?", USER_MAPPER, email);
            return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /** 只取密码哈希（不放进 User 对象，避免误传给前端） */
    public String passwordHash(long userId) {
        try {
            return j().queryForObject("SELECT password_hash FROM users WHERE id = ?", String.class, userId);
        } catch (Exception e) {
            return null;
        }
    }

    public boolean existsUsername(String username) {
        return countBy("username", username) > 0;
    }

    public boolean existsEmail(String email) {
        return countBy("email", email) > 0;
    }

    private long countBy(String column, String value) {
        try {
            Long n = j().queryForObject("SELECT COUNT(*) FROM users WHERE " + column + " = ?", Long.class, value);
            return n == null ? 0 : n;
        } catch (Exception e) {
            return 0;
        }
    }

    public long insertUser(String username, String email, String passwordHash, String nickname,
                           String emoji, String source) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        j().update(conn -> {
            PreparedStatement ps = conn.prepareStatement("""
                    INSERT INTO users(username, email, password_hash, nickname, avatar_emoji, sign,
                                      level, exp, exp_max, coins, b_coins, vip, medal, join_days,
                                      status, register_source, email_verified)
                    VALUES (?, ?, ?, ?, ?, ?, 1, 0, 200, 20, 0, 0, '萌新勋章', 1, 1, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, username);
            ps.setString(2, email);
            ps.setString(3, passwordHash);
            ps.setString(4, nickname);
            ps.setString(5, emoji);
            ps.setString(6, "这个人很懒，什么都没写~");
            ps.setString(7, source);
            ps.setInt(8, "EMAIL".equals(source) ? 1 : 0);
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        long id = key == null ? 0 : key.longValue();
        // 新用户自动准备一个默认收藏夹 + 一条欢迎消息
        try {
            j().update("INSERT INTO favorite_folders(user_id, name, is_default, is_public) VALUES (?, '默认收藏夹', 1, 1)", id);
            addMessage(id, "SYSTEM", "欢迎来到a哩a哩",
                    "登录成功！在这里可以发弹幕、评论、收藏视频，快去逛逛吧～", "/");
        } catch (Exception e) {
            log.warn("初始化新用户数据失败: {}", e.getMessage());
        }
        return id;
    }

    /** 演示账号（id=90001）：保证老功能「不登录也能玩」依然可用 */
    public void ensureDemoUser() {
        try {
            if (findById(90001L).isPresent()) {
                return;
            }
            KeyHolder kh = new GeneratedKeyHolder();
            j().update(conn -> {
                PreparedStatement ps = conn.prepareStatement("""
                        INSERT INTO users(id, username, email, nickname, avatar_emoji, sign, level, exp, exp_max,
                                          coins, b_coins, following_count, follower_count, like_count, vip, vip_label,
                                          medal, join_days, status, register_source, email_verified)
                        VALUES (90001, 'bili_demo', 'demo@bilibili.local', 'a哩a哩萌新', '😎', '这个人很懒，什么都没写~',
                                5, 3860, 4800, 328, 1200, 186, 42, 1288, 1, '年度大会员', '萌新勋章', 168, 1, 'PASSWORD', 1)
                        """, Statement.RETURN_GENERATED_KEYS);
                return ps;
            }, kh);
            j().update("INSERT INTO favorite_folders(user_id, name, is_default, is_public) VALUES (90001, '默认收藏夹', 1, 1)");
            log.info(">>> 已创建演示账号 bili_demo（id=90001）");
        } catch (Exception e) {
            log.warn("创建演示账号失败: {}", e.getMessage());
        }
    }

    /**
     * 给演示账号补一个密码（BIli123456 这种），方便直接体验「账号密码 + 图形验证码」登录，
     * 也方便二维码登录时在「模拟手机」里确认。已经设置过密码就不会覆盖。
     */
    public void ensureDemoPassword(String rawPassword) {
        try {
            String existing = passwordHash(90001L);
            if (existing != null && !existing.isBlank()) {
                return;
            }
            String hash = new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode(rawPassword);
            updatePassword(90001L, hash);
            log.info(">>> 演示账号 bili_demo 的密码已初始化");
        } catch (Exception e) {
            log.warn("初始化演示账号密码失败: {}", e.getMessage());
        }
    }

    public void touchLogin(long userId, String ip) {        try {
            j().update("UPDATE users SET last_login_at = NOW(), last_login_ip = ? WHERE id = ?", ip, userId);
        } catch (Exception ignored) {
        }
    }

    public void updatePassword(long userId, String hash) {
        j().update("UPDATE users SET password_hash = ? WHERE id = ?", hash, userId);
    }

    /**
     * 把演示数据里的 UP 主写进 users 表（id 1000+），
     * 这样 videos.up_id / follows.up_id 的外键才有对应记录，个人空间也能正常跳转。
     */
    public void insertUpUser(long id, String nickname, String emoji, String sign, long followers,
                             String medal, int level, long likes) {
        try {
            j().update("""
                    INSERT INTO users(id, username, nickname, avatar_emoji, sign, level, exp, exp_max,
                                      coins, b_coins, follower_count, like_count, medal, join_days,
                                      status, register_source, email_verified)
                    VALUES (?, ?, ?, ?, ?, ?, 0, 200, 0, 0, ?, ?, ?, 365, 1, 'PASSWORD', 1)
                    ON DUPLICATE KEY UPDATE nickname = VALUES(nickname), avatar_emoji = VALUES(avatar_emoji),
                                            sign = VALUES(sign), follower_count = VALUES(follower_count),
                                            like_count = VALUES(like_count)
                    """, id, "up_" + id, nickname, emoji, sign, level, followers, likes, medal);
        } catch (Exception e) {
            log.warn("写入 UP 主失败 id={}: {}", id, e.getMessage());
        }
    }

    /** 读取所有 UP 主（个人空间 / 关注列表用） */
    public List<com.bili.demo.model.Up> loadUps() {
        try {
            return j().query("""
                    SELECT id, nickname, avatar_emoji, sign, follower_count, like_count, level, medal
                    FROM users WHERE id BETWEEN 1000 AND 2999 ORDER BY id
                    """, (rs, i) -> {
                com.bili.demo.model.Up up = new com.bili.demo.model.Up();
                up.id = rs.getLong("id");
                up.name = rs.getString("nickname");
                up.face = rs.getString("avatar_emoji");
                up.sign = rs.getString("sign");
                up.fans = rs.getLong("follower_count");
                up.likes = rs.getLong("like_count");
                up.level = "Lv" + rs.getInt("level");
                up.medal = rs.getString("medal");
                return up;
            });
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    public void updateBasic(long userId, String nickname, String sign, String avatarUrl, String emoji) {
        j().update("""
                UPDATE users SET nickname = COALESCE(?, nickname), sign = COALESCE(?, sign),
                                 avatar_url = ?, avatar_emoji = COALESCE(?, avatar_emoji)
                WHERE id = ?
                """, nickname, sign, avatarUrl, emoji, userId);
    }

    public void updateAvatar(long userId, String avatarUrl) {
        j().update("UPDATE users SET avatar_url = ? WHERE id = ?", avatarUrl, userId);
    }

    public void updateEmail(long userId, String email) {
        j().update("UPDATE users SET email = ?, email_verified = 1 WHERE id = ?", email, userId);
    }

    /** 读取所有 UP 主（个人空间 / 关注列表用）  */
    /** 把内存中的等级 / 经验 / 硬币等状态写回数据库 */
    public void updateStats(User u) {
        try {
            j().update("""
                    UPDATE users SET level = ?, exp = ?, exp_max = ?, coins = ?, b_coins = ?,
                                     following_count = ?, follower_count = ?, like_count = ?,
                                     vip = ?, vip_label = ?, medal = ?, join_days = ?,
                                     last_checkin = ?
                    WHERE id = ?
                    """,
                    u.level, u.exp, u.expMax, u.coins, u.bCoins, u.following, u.followers, u.likes,
                    u.vip ? 1 : 0, u.vipLabel, u.medal, u.joinDays,
                    (u.lastCheckin == null || u.lastCheckin.isBlank()) ? null : java.sql.Date.valueOf(u.lastCheckin),
                    u.id);
        } catch (Exception e) {
            log.warn("保存用户状态失败: {}", e.getMessage());
        }
    }

    // ==================================================================
    // user_tokens
    // ==================================================================

    public String createToken(long userId, String loginType, int days) {
        String token = randomToken(48);
        long expiresAt = System.currentTimeMillis() + days * 24L * 3600L * 1000L;

        // 内存里始终留一份：数据库没起来时「一键体验演示账号」也要能登录，
        // 否则整个登录流程会在最后一步写 token 时 500（进程重启即失效，演示够用）。
        tokenMemory.put(token, new long[]{userId, expiresAt});
        pruneTokens();

        if (db.available()) {
            try {
                j().update("INSERT INTO user_tokens(user_id, token, login_type, expires_at) VALUES (?, ?, ?, ?)",
                        userId, token, loginType, Timestamp.valueOf(LocalDateTime.now().plusDays(days)));
                j().update("DELETE FROM user_tokens WHERE expires_at < NOW()");
            } catch (Exception e) {
                log.warn(">>> 登录令牌写库失败，本次改用内存兜底：{}", e.getMessage());
            }
        }
        return token;
    }

    public Optional<Long> userIdByToken(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        if (db.available()) {
            try {
                List<Long> list = j().queryForList(
                        "SELECT user_id FROM user_tokens WHERE token = ? AND expires_at > NOW()", Long.class, token);
                if (!list.isEmpty()) {
                    return Optional.of(list.get(0));
                }
            } catch (Exception e) {
                // 落库查询失败就退回内存
            }
        }
        long[] entry = tokenMemory.get(token);
        if (entry == null || entry[1] < System.currentTimeMillis()) {
            return Optional.empty();
        }
        return Optional.of(entry[0]);
    }

    public void deleteToken(String token) {
        tokenMemory.remove(token);
        if (!db.available()) {
            return;
        }
        try {
            j().update("DELETE FROM user_tokens WHERE token = ?", token);
        } catch (Exception ignored) {
        }
    }

    public void deleteAllTokens(long userId) {
        try {
            j().update("DELETE FROM user_tokens WHERE user_id = ?", userId);
        } catch (Exception ignored) {
        }
    }

    // ==================================================================
    // captcha_codes
    // ==================================================================

    public void saveCaptcha(String key, String code, String purpose, String ip, int ttlSeconds) {
        // 内存里始终留一份：数据库中途挂掉时，刚发出去的验证码也还能用
        captchaMemory.put(key, new Code(code, purpose, ttlSeconds));
        prune(captchaMemory);

        if (!db.available()) {
            return;
        }
        try {
            j().update("""
                    INSERT INTO captcha_codes(captcha_key, code, purpose, client_ip, used, expires_at)
                    VALUES (?, ?, ?, ?, 0, ?)
                    """, key, code, purpose, ip, Timestamp.valueOf(LocalDateTime.now().plusSeconds(ttlSeconds)));
            j().update("DELETE FROM captcha_codes WHERE expires_at < DATE_SUB(NOW(), INTERVAL 1 DAY)");
        } catch (Exception e) {
            log.warn(">>> 图形验证码写库失败，本次改用内存兜底：{}", e.getMessage());
        }
    }

    /** 校验并消费图形验证码（一次性，重复使用会失败） */
    public boolean consumeCaptcha(String key, String code, String purpose) {
        if (key == null || code == null) {
            return false;
        }
        if (db.available()) {
            try {
                int n = j().update("""
                        UPDATE captcha_codes SET used = 1
                        WHERE captcha_key = ? AND purpose = ? AND used = 0 AND expires_at > NOW()
                          AND UPPER(code) = UPPER(?)
                        """, key, purpose, code.trim());
                if (n > 0) {
                    captchaMemory.remove(key);
                    return true;
                }
                // 校验失败也把该 key 作废，避免被暴力枚举
                j().update("UPDATE captcha_codes SET used = 1 WHERE captcha_key = ? AND used = 0", key);
                captchaMemory.remove(key);
                return false;
            } catch (Exception e) {
                // 落库失败就退回内存校验
            }
        }
        return consumeMemory(captchaMemory, key, code, purpose);
    }

    // ==================================================================
    // email_verification_codes
    // ==================================================================

    public void saveEmailCode(String email, String code, String purpose, String ip, int ttlSeconds) {
        emailCodeMemory.put(memKey(email, purpose), new Code(code, purpose, ttlSeconds));
        prune(emailCodeMemory);

        if (!db.available()) {
            return;
        }
        try {
            j().update("""
                    INSERT INTO email_verification_codes(email, code, purpose, client_ip, used, expires_at)
                    VALUES (?, ?, ?, ?, 0, ?)
                    """, email, code, purpose, ip, Timestamp.valueOf(LocalDateTime.now().plusSeconds(ttlSeconds)));
            j().update("DELETE FROM email_verification_codes WHERE expires_at < DATE_SUB(NOW(), INTERVAL 1 DAY)");
        } catch (Exception e) {
            log.warn(">>> 邮箱验证码写库失败，本次改用内存兜底：{}", e.getMessage());
        }
    }

    private static String memKey(String email, String purpose) {
        return purpose + "\n" + email;
    }

    /** 校验内存里的一份验证码（一次性消费） */
    private boolean consumeMemory(Map<String, Code> store, String key, String code, String purpose) {
        Code c = store.get(key);
        if (c == null || !c.matches(code, purpose)) {
            // 校验失败也作废，避免被枚举
            if (c != null) {
                c.used = true;
            }
            return false;
        }
        c.used = true;
        return true;
    }

    /** 距上次给该邮箱发码过了多少秒（用于 60 秒频控），没有记录返回一个很大的数 */
    public long secondsSinceLastEmailCode(String email, String purpose) {
        try {
            Timestamp ts = j().queryForObject("""
                    SELECT MAX(created_at) FROM email_verification_codes WHERE email = ? AND purpose = ?
                    """, Timestamp.class, email, purpose);
            if (ts == null) {
                return Long.MAX_VALUE;
            }
            return java.time.Duration.between(ts.toLocalDateTime(), LocalDateTime.now()).getSeconds();
        } catch (Exception e) {
            return Long.MAX_VALUE;
        }
    }

    public boolean verifyEmailCode(String email, String code, String purpose) {
        if (email == null || code == null) {
            return false;
        }
        if (db.available()) {
            try {
                int n = j().update("""
                        UPDATE email_verification_codes SET used = 1
                        WHERE email = ? AND purpose = ? AND used = 0 AND expires_at > NOW() AND code = ?
                        ORDER BY id DESC LIMIT 1
                        """, email, purpose, code.trim());
                if (n > 0) {
                    emailCodeMemory.remove(memKey(email, purpose));
                    return true;
                }
                j().update("""
                        UPDATE email_verification_codes SET used = 1
                        WHERE email = ? AND purpose = ? AND used = 0
                        """, email, purpose);
                emailCodeMemory.remove(memKey(email, purpose));
                return false;
            } catch (Exception e) {
                // 落库失败就退回内存校验
            }
        }
        return consumeMemory(emailCodeMemory, memKey(email, purpose), code, purpose);
    }

    /** 仅供开发环境直接取最新验证码（前端「开发模式提示」用） */
    public String latestEmailCode(String email, String purpose) {
        try {
            return j().queryForObject("""
                    SELECT code FROM email_verification_codes
                    WHERE email = ? AND purpose = ? AND used = 0 AND expires_at > NOW()
                    ORDER BY id DESC LIMIT 1
                    """, String.class, email, purpose);
        } catch (Exception e) {
            return null;
        }
    }


    // ==================================================================
    // qr_login_sessions
    // ==================================================================

    public void createQrSession(String qrId, String ip, int ttlSeconds) {
        // 内存里始终留一份：没有数据库时二维码登录也要能完整走通
        qrMemory.put(qrId, new QrSession(ip, ttlSeconds));
        pruneQr();

        if (!db.available()) {
            return;
        }
        try {
            j().update("""
                    INSERT INTO qr_login_sessions(qr_id, status, client_ip, expires_at)
                    VALUES (?, 'WAITING', ?, ?)
                    """, qrId, ip, Timestamp.valueOf(LocalDateTime.now().plusSeconds(ttlSeconds)));
            j().update("DELETE FROM qr_login_sessions WHERE expires_at < DATE_SUB(NOW(), INTERVAL 1 DAY)");
        } catch (Exception e) {
            log.warn(">>> 二维码会话写库失败，本次改用内存兜底：{}", e.getMessage());
        }
    }

    /** 返回 [status, token]；过期的会话在这里被自动标记为 EXPIRED */
    public Map<String, Object> qrStatus(String qrId) {
        if (db.available()) {
            try {
                j().update("""
                        UPDATE qr_login_sessions SET status = 'EXPIRED'
                        WHERE qr_id = ? AND status IN ('WAITING','SCANNED') AND expires_at <= NOW()
                        """, qrId);
                List<Map<String, Object>> rows = j().queryForList("""
                        SELECT status, token, user_id,
                               TIMESTAMPDIFF(SECOND, NOW(), expires_at) AS left_seconds
                        FROM qr_login_sessions WHERE qr_id = ?
                        """, qrId);
                if (!rows.isEmpty()) {
                    Map<String, Object> row = rows.get(0);
                    String status = String.valueOf(row.get("status"));
                    Map<String, Object> data = new LinkedHashMap<>();
                    data.put("status", status);
                    data.put("leftSeconds", row.get("left_seconds"));
                    if ("CONFIRMED".equals(status) && row.get("token") != null) {
                        data.put("token", row.get("token"));
                        data.put("userId", row.get("user_id"));
                    }
                    data.put("message", qrMessage(status));
                    return data;
                }
            } catch (Exception e) {
                // 查询失败就退回内存
            }
        }
        return qrStatusFromMemory(qrId);
    }

    /** 扫描：只有 WAITING 可以进入 SCANNED */
    public boolean markQrScanned(String qrId) {
        if (db.available()) {
            try {
                if (j().update("""
                        UPDATE qr_login_sessions SET status = 'SCANNED', scanned_at = NOW()
                        WHERE qr_id = ? AND status = 'WAITING' AND expires_at > NOW()
                        """, qrId) > 0) {
                    return true;
                }
            } catch (Exception e) {
                // 落库失败就退回内存
            }
        }
        return qrMemoryScan(qrId);
    }

    public boolean markQrConfirmed(String qrId, long userId, String token) {
        if (db.available()) {
            try {
                if (j().update("""
                        UPDATE qr_login_sessions SET status = 'CONFIRMED', user_id = ?, token = ?, confirmed_at = NOW()
                        WHERE qr_id = ? AND status IN ('WAITING','SCANNED') AND expires_at > NOW()
                        """, userId, token, qrId) > 0) {
                    return true;
                }
            } catch (Exception e) {
                // 落库失败就退回内存
            }
        }
        QrSession s = liveQr(qrId);
        if (s == null || "CONFIRMED".equals(s.status) || "CANCELED".equals(s.status)) {
            return false;
        }
        s.status = "CONFIRMED";
        s.userId = userId;
        s.token = token;
        return true;
    }

    public boolean markQrCanceled(String qrId) {
        if (db.available()) {
            try {
                if (j().update("""
                        UPDATE qr_login_sessions SET status = 'CANCELED'
                        WHERE qr_id = ? AND status IN ('WAITING','SCANNED')
                        """, qrId) > 0) {
                    return true;
                }
            } catch (Exception e) {
                // 落库失败就退回内存
            }
        }
        QrSession s = qrMemory.get(qrId);
        if (s == null || "CONFIRMED".equals(s.status) || "CANCELED".equals(s.status)) {
            return false;
        }
        s.status = "CANCELED";
        return true;
    }

    /** 手机端扫码后要显示的信息（不返回 token） */
    public Map<String, Object> qrInfo(String qrId) {
        if (db.available()) {
            try {
                List<Map<String, Object>> rows = j().queryForList("""
                        SELECT status, client_ip, TIMESTAMPDIFF(SECOND, NOW(), expires_at) AS left_seconds
                        FROM qr_login_sessions WHERE qr_id = ?
                        """, qrId);
                if (!rows.isEmpty()) {
                    return new LinkedHashMap<>(rows.get(0));
                }
            } catch (Exception e) {
                // 查询失败就退回内存
            }
        }
        Map<String, Object> data = new LinkedHashMap<>();
        QrSession s = qrMemory.get(qrId);
        if (s == null) {
            data.put("status", "EXPIRED");
            return data;
        }
        data.put("status", s.status);
        data.put("client_ip", s.ip);
        data.put("left_seconds", Math.max(0, (s.expiresAt - System.currentTimeMillis()) / 1000));
        return data;
    }

    // ---------------- 二维码会话的内存实现 ----------------

    /** 一个内存里的二维码会话 */
    private static final class QrSession {
        final String ip;
        final long expiresAt;
        volatile String status = "WAITING";
        volatile Long userId;
        volatile String token;

        QrSession(String ip, int ttlSeconds) {
            this.ip = ip;
            this.expiresAt = System.currentTimeMillis() + ttlSeconds * 1000L;
        }

        boolean live() {
            return expiresAt > System.currentTimeMillis();
        }
    }

    /** 取出一个还没过期、也没结束的会话；过期的顺手标成 EXPIRED */
    private QrSession liveQr(String qrId) {
        QrSession s = qrMemory.get(qrId);
        if (s == null) {
            return null;
        }
        if (!s.live() && ("WAITING".equals(s.status) || "SCANNED".equals(s.status))) {
            s.status = "EXPIRED";
        }
        return s;
    }

    private Map<String, Object> qrStatusFromMemory(String qrId) {
        Map<String, Object> data = new LinkedHashMap<>();
        QrSession s = liveQr(qrId);
        if (s == null) {
            data.put("status", "EXPIRED");
            data.put("message", "二维码不存在或已失效");
            return data;
        }
        data.put("status", s.status);
        data.put("leftSeconds", Math.max(0, (s.expiresAt - System.currentTimeMillis()) / 1000));
        if ("CONFIRMED".equals(s.status) && s.token != null) {
            data.put("token", s.token);
            data.put("userId", s.userId);
        }
        data.put("message", qrMessage(s.status));
        return data;
    }

    private boolean qrMemoryScan(String qrId) {
        QrSession s = liveQr(qrId);
        if (s == null || !s.live() || !"WAITING".equals(s.status)) {
            return false;
        }
        s.status = "SCANNED";
        return true;
    }

    private static String qrMessage(String status) {
        return switch (status) {
            case "WAITING" -> "二维码已生成，等待扫码";
            case "SCANNED" -> "已扫码，请在手机上确认";
            case "CONFIRMED" -> "已确认，正在登录";
            case "CANCELED" -> "本次登录已取消";
            default -> "二维码已过期，请点击刷新";
        };
    }

    /** 清掉早就过期的会话，避免内存无限增长 */
    private void pruneQr() {
        if (qrMemory.size() < 128) {
            return;
        }
        long now = System.currentTimeMillis();
        qrMemory.entrySet().removeIf(e -> e.getValue().expiresAt < now);
    }

    // ==================================================================
    // user_settings
    // ==================================================================

    public Map<String, Object> settings(long userId) {
        Map<String, Object> data = new LinkedHashMap<>();
        try {
            List<Map<String, Object>> rows = j().queryForList("""
                    SELECT theme, autoplay, default_quality, danmaku_on, danmaku_opacity, danmaku_fontsize,
                           danmaku_speed, danmaku_area, show_history, email_notify
                    FROM user_settings WHERE user_id = ?
                    """, userId);
            if (rows.isEmpty()) {
                j().update("INSERT INTO user_settings(user_id) VALUES (?)", userId);
                rows = j().queryForList("""
                        SELECT theme, autoplay, default_quality, danmaku_on, danmaku_opacity, danmaku_fontsize,
                               danmaku_speed, danmaku_area, show_history, email_notify
                        FROM user_settings WHERE user_id = ?
                        """, userId);
            }
            data.putAll(rows.get(0));
        } catch (Exception e) {
            data.put("theme", "light");
        }
        return data;
    }

    public void saveSettings(long userId, Map<String, Object> s) {
        try {
            j().update("INSERT INTO user_settings(user_id) VALUES (?) ON DUPLICATE KEY UPDATE user_id = user_id", userId);
            j().update("""
                    UPDATE user_settings SET
                      theme = COALESCE(?, theme),
                      autoplay = COALESCE(?, autoplay),
                      default_quality = COALESCE(?, default_quality),
                      danmaku_on = COALESCE(?, danmaku_on),
                      danmaku_opacity = COALESCE(?, danmaku_opacity),
                      danmaku_fontsize = COALESCE(?, danmaku_fontsize),
                      danmaku_speed = COALESCE(?, danmaku_speed),
                      danmaku_area = COALESCE(?, danmaku_area),
                      show_history = COALESCE(?, show_history),
                      email_notify = COALESCE(?, email_notify)
                    WHERE user_id = ?
                    """,
                    s.get("theme"), toIntOrNull(s.get("autoplay")), s.get("defaultQuality"),
                    toIntOrNull(s.get("danmakuOn")), toDoubleOrNull(s.get("danmakuOpacity")),
                    toIntOrNull(s.get("danmakuFontSize")), toIntOrNull(s.get("danmakuSpeed")),
                    s.get("danmakuArea"), toIntOrNull(s.get("showHistory")), toIntOrNull(s.get("emailNotify")),
                    userId);
        } catch (Exception e) {
            log.warn("保存用户设置失败: {}", e.getMessage());
        }
    }

    private Integer toIntOrNull(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof Boolean b) {
            return b ? 1 : 0;
        }
        try {
            return Integer.parseInt(String.valueOf(o));
        } catch (Exception e) {
            return null;
        }
    }

    private Double toDoubleOrNull(Object o) {
        if (o == null) {
            return null;
        }
        try {
            return Double.parseDouble(String.valueOf(o));
        } catch (Exception e) {
            return null;
        }
    }

    // ==================================================================
    // messages
    // ==================================================================

    public void addMessage(long userId, String type, String title, String content, String link) {
        try {
            j().update("INSERT INTO messages(user_id, type, title, content, link) VALUES (?, ?, ?, ?, ?)",
                    userId, type, title, content, link);
        } catch (Exception ignored) {
        }
    }

    public List<Map<String, Object>> messages(long userId, String type, int limit) {
        try {
            String sql = """
                    SELECT id, type, title, content, link, is_read, created_at
                    FROM messages WHERE user_id = ?
                    """ + (type == null || type.isBlank() || "ALL".equalsIgnoreCase(type) ? "" : " AND type = ? ")
                    + " ORDER BY id DESC LIMIT ?";
            Object[] args = (type == null || type.isBlank() || "ALL".equalsIgnoreCase(type))
                    ? new Object[]{userId, limit}
                    : new Object[]{userId, type, limit};
            return j().queryForList(sql, args);
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    public int unreadCount(long userId) {
        try {
            Integer n = j().queryForObject(
                    "SELECT COUNT(*) FROM messages WHERE user_id = ? AND is_read = 0", Integer.class, userId);
            return n == null ? 0 : n;
        } catch (Exception e) {
            return 0;
        }
    }

    public void markMessageRead(long userId, long id) {
        try {
            if (id <= 0) {
                j().update("UPDATE messages SET is_read = 1 WHERE user_id = ?", userId);
            } else {
                j().update("UPDATE messages SET is_read = 1 WHERE user_id = ? AND id = ?", userId, id);
            }
        } catch (Exception ignored) {
        }
    }

    // ==================================================================
    // watch_history
    // ==================================================================

    public void upsertHistory(long userId, long videoId, int progressSec) {
        try {
            j().update("""
                    INSERT INTO watch_history(user_id, video_id, progress_sec) VALUES (?, ?, ?)
                    ON DUPLICATE KEY UPDATE progress_sec = VALUES(progress_sec), watched_at = NOW()
                    """, userId, videoId, Math.max(0, progressSec));
        } catch (Exception ignored) {
        }
    }

    public List<Map<String, Object>> history(long userId, int limit) {
        try {
            return j().queryForList("""
                    SELECT h.video_id, h.progress_sec, h.watched_at,
                           v.title, v.duration, v.cover_path, v.cover_color1, v.cover_color2,
                           v.cover_emoji, v.cover_text, v.up_name, v.views, v.danmaku_count, v.bvid
                    FROM watch_history h JOIN videos v ON v.id = h.video_id
                    WHERE h.user_id = ?
                    ORDER BY h.watched_at DESC LIMIT ?
                    """, userId, limit);
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    public void deleteHistory(long userId, long videoId) {
        try {
            if (videoId <= 0) {
                j().update("DELETE FROM watch_history WHERE user_id = ?", userId);
            } else {
                j().update("DELETE FROM watch_history WHERE user_id = ? AND video_id = ?", userId, videoId);
            }
        } catch (Exception ignored) {
        }
    }

    // ==================================================================
    // favorite_folders / favorite_items
    // ==================================================================

    public long defaultFolderId(long userId) {
        try {
            List<Long> ids = j().queryForList(
                    "SELECT id FROM favorite_folders WHERE user_id = ? ORDER BY is_default DESC, id ASC LIMIT 1",
                    Long.class, userId);
            if (!ids.isEmpty()) {
                return ids.get(0);
            }
            j().update("INSERT INTO favorite_folders(user_id, name, is_default) VALUES (?, '默认收藏夹', 1)", userId);
            Long id = j().queryForObject("SELECT LAST_INSERT_ID()", Long.class);
            return id == null ? 0 : id;
        } catch (Exception e) {
            return 0;
        }
    }

    /** 收藏 / 取消收藏，返回操作后是否已收藏 */
    public boolean toggleFavorite(long userId, long videoId) {
        long folderId = defaultFolderId(userId);
        if (folderId <= 0) {
            return false;
        }
        try {
            int deleted = j().update("DELETE FROM favorite_items WHERE folder_id = ? AND video_id = ?", folderId, videoId);
            if (deleted > 0) {
                return false;
            }
            j().update("INSERT INTO favorite_items(folder_id, video_id) VALUES (?, ?)", folderId, videoId);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public List<Map<String, Object>> favorites(long userId, int limit, long folderId) {        try {
            long fid = folderId > 0 ? folderId : defaultFolderId(userId);
            return j().queryForList("""
                    SELECT f.id AS item_id, f.created_at AS fav_at, v.id, v.bvid, v.title, v.duration,
                           v.cover_path, v.cover_color1, v.cover_color2, v.cover_emoji, v.cover_text,
                           v.up_name, v.views, v.danmaku_count, v.category_id
                    FROM favorite_items f JOIN videos v ON v.id = f.video_id
                    WHERE f.folder_id = ? ORDER BY f.id DESC LIMIT ?
                    """, fid, limit);
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    public List<Map<String, Object>> favoriteFolders(long userId) {
        try {
            return j().queryForList("""
                    SELECT f.id, f.name, f.is_default, f.is_public,
                           (SELECT COUNT(*) FROM favorite_items i WHERE i.folder_id = f.id) AS item_count
                    FROM favorite_folders f WHERE f.user_id = ? ORDER BY f.is_default DESC, f.id ASC
                    """, userId);
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    /** 从收藏夹移除（取消收藏） */
    public void removeFavorite(long folderId, long videoId) {
        try {
            j().update("DELETE FROM favorite_items WHERE folder_id = ? AND video_id = ?", folderId, videoId);
        } catch (Exception ignored) {
        }
    }

    public long createFolder(long userId, String name, boolean isPublic) {        long id = defaultFolderId(userId);
        if (id <= 0) {
            return 0;
        }
        KeyHolder kh = new GeneratedKeyHolder();
        j().update(conn -> {
            PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO favorite_folders(user_id, name, is_public) VALUES (?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, userId);
            ps.setString(2, name);
            ps.setInt(3, isPublic ? 1 : 0);
            return ps;
        }, kh);
        Number key = kh.getKey();
        return key == null ? 0 : key.longValue();
    }

    public void deleteFolder(long userId, long folderId) {
        j().update("DELETE FROM favorite_folders WHERE id = ? AND user_id = ? AND is_default = 0", folderId, userId);
    }

    // ==================================================================
    // follows
    // ==================================================================

    public boolean toggleFollow(long userId, long upId) {
        try {
            int deleted = j().update("DELETE FROM follows WHERE user_id = ? AND up_id = ?", userId, upId);
            if (deleted > 0) {
                j().update("UPDATE users SET following_count = GREATEST(0, following_count - 1) WHERE id = ?", userId);
                j().update("UPDATE users SET follower_count = GREATEST(0, follower_count - 1) WHERE id = ?", upId);
                return false;
            }
            j().update("INSERT INTO follows(user_id, up_id) VALUES (?, ?)", userId, upId);
            j().update("UPDATE users SET following_count = following_count + 1 WHERE id = ?", userId);
            j().update("UPDATE users SET follower_count = follower_count + 1 WHERE id = ?", upId);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** 我关注了哪些 UP 主（只查 users 表里存在的那些） */
    public List<Long> followingIds(long userId) {
        try {
            return j().queryForList("SELECT up_id FROM follows WHERE user_id = ?", Long.class, userId);
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    public List<Map<String, Object>> followingUsers(long userId) {
        try {
            return j().queryForList("""
                    SELECT u.id, u.nickname, u.avatar_emoji, u.sign, u.follower_count, u.level, f.created_at
                    FROM follows f JOIN users u ON u.id = f.up_id
                    WHERE f.user_id = ? ORDER BY f.id DESC
                    """, userId);
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    // ==================================================================
    // bangumi_follows
    // ==================================================================

    public boolean toggleBangumiFollow(long userId, long bangumiId) {
        try {
            int deleted = j().update("DELETE FROM bangumi_follows WHERE user_id = ? AND bangumi_id = ?",
                    userId, bangumiId);
            if (deleted > 0) {
                j().update("UPDATE bangumi SET followers = GREATEST(0, followers - 1) WHERE id = ?", bangumiId);
                return false;
            }
            j().update("INSERT INTO bangumi_follows(user_id, bangumi_id) VALUES (?, ?)", userId, bangumiId);
            j().update("UPDATE bangumi SET followers = followers + 1 WHERE id = ?", bangumiId);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public List<Long> followedBangumiIds(long userId) {
        try {
            return j().queryForList("SELECT bangumi_id FROM bangumi_follows WHERE user_id = ?", Long.class, userId);
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    // ==================================================================
    // video_uploads（投稿）
    // ==================================================================

    public long insertUpload(long userId, String title, Integer categoryId, String fileName, String filePath,
                             String coverPath, String status) {
        KeyHolder kh = new GeneratedKeyHolder();
        j().update(conn -> {
            PreparedStatement ps = conn.prepareStatement("""
                    INSERT INTO video_uploads(user_id, title, category_id, file_name, file_path, cover_path, status, progress)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, userId);
            ps.setString(2, title);
            if (categoryId == null) {
                ps.setNull(3, java.sql.Types.INTEGER);
            } else {
                ps.setInt(3, categoryId);
            }
            ps.setString(4, fileName);
            ps.setString(5, filePath);
            ps.setString(6, coverPath);
            ps.setString(7, status);
            ps.setInt(8, "PUBLISHED".equals(status) ? 100 : 10);
            return ps;
        }, kh);
        Number key = kh.getKey();
        return key == null ? 0 : key.longValue();
    }

    public List<Map<String, Object>> uploads(long userId) {
        try {
            return j().queryForList("""
                    SELECT u.id, u.title, u.file_name, u.file_path, u.cover_path, u.status, u.progress,
                           u.video_id, u.created_at, c.name AS category_name
                    FROM video_uploads u LEFT JOIN categories c ON c.id = u.category_id
                    WHERE u.user_id = ? ORDER BY u.id DESC
                    """, userId);
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    public void publishUpload(long uploadId, long videoId) {
        j().update("UPDATE video_uploads SET status = 'PUBLISHED', progress = 100, video_id = ? WHERE id = ?",
                videoId, uploadId);
    }

    public void deleteUpload(long userId, long uploadId) {
        j().update("DELETE FROM video_uploads WHERE id = ? AND user_id = ?", uploadId, userId);
    }

    // ==================================================================
    // search_history
    // ==================================================================

    public void addSearchHistory(long userId, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return;
        }
        try {
            j().update("""
                    INSERT INTO search_history(user_id, keyword) VALUES (?, ?)
                    ON DUPLICATE KEY UPDATE created_at = NOW()
                    """, userId, keyword.trim().substring(0, Math.min(80, keyword.trim().length())));
        } catch (Exception ignored) {
        }
    }

    public List<String> searchHistory(long userId, int limit) {
        try {
            return j().queryForList("""
                    SELECT keyword FROM search_history WHERE user_id = ? ORDER BY created_at DESC LIMIT ?
                    """, String.class, userId, limit);
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    public void clearSearchHistory(long userId) {
        try {
            j().update("DELETE FROM search_history WHERE user_id = ?", userId);
        } catch (Exception ignored) {
        }
    }

    // ==================================================================
    // login_logs
    // ==================================================================

    public void logLogin(Long userId, String username, String type, boolean success, String reason,
                         String ip, String ua) {
        try {
            j().update("""
                    INSERT INTO login_logs(user_id, username, login_type, success, fail_reason, client_ip, user_agent)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    """, userId, username, type, success ? 1 : 0, reason, ip,
                    ua == null ? null : ua.substring(0, Math.min(255, ua.length())));
        } catch (Exception ignored) {
        }
    }

    // ==================================================================

    public static String randomToken(int length) {
        String chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        java.util.Random rnd = new java.util.Random();
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(rnd.nextInt(chars.length())));
        }
        return sb.toString();
    }
}
