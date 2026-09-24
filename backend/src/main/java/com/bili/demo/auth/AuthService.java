package com.bili.demo.auth;

import com.bili.demo.db.DataSyncService;
import com.bili.demo.db.UserRepository;
import com.bili.demo.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 账号体系：注册 / 密码登录 / 邮箱验证码登录 / 找回密码 / 令牌签发。
 * 密码一律 BCrypt 哈希后入库（users.password_hash），数据库里查不到明文。
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepo;
    private final CaptchaService captchaService;
    private final EmailCodeService emailCodeService;
    private final RateLimiter rateLimiter;
    private final DataSyncService sync;

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public AuthService(UserRepository userRepo, CaptchaService captchaService,
                       EmailCodeService emailCodeService, RateLimiter rateLimiter, DataSyncService sync) {
        this.userRepo = userRepo;
        this.captchaService = captchaService;
        this.emailCodeService = emailCodeService;
        this.rateLimiter = rateLimiter;
        this.sync = sync;
    }

    // ==================================================================
    // 注册
    // ==================================================================

    public Map<String, Object> register(String username, String email, String password,
                                        String emailCode, String ip, String ua) {
        Map<String, Object> data = new LinkedHashMap<>();
        if (!sync.mysqlMode()) {
            return fail(data, "数据库未连接，暂时无法注册（请先启动 MySQL 后重启服务）");
        }
        String u = trim(username);
        String mail = trim(email);
        if (u == null || u.length() < 4 || u.length() > 20) {
            return fail(data, "账号需要 4~20 个字符");
        }
        if (!u.matches("^[A-Za-z0-9_\\u4e00-\\u9fa5]+$")) {
            return fail(data, "账号只能包含中英文、数字和下划线");
        }
        if (mail == null || !mail.contains("@")) {
            return fail(data, "请输入正确的邮箱");
        }
        if (password == null || password.length() < 6) {
            return fail(data, "密码至少 6 位");
        }
        if (!rateLimiter.allow("register:" + ip, 10, 3600)) {
            return fail(data, "注册过于频繁，请稍后再试");
        }
        if (userRepo.existsUsername(u)) {
            return fail(data, "该账号已被注册，换一个试试");
        }
        if (userRepo.existsEmail(mail)) {
            return fail(data, "该邮箱已被注册，可直接用邮箱验证码登录");
        }
        boolean verified = emailCode != null && !emailCode.isBlank()
                && emailCodeService.verify(mail, emailCode, "REGISTER");
        if (!verified) {
            return fail(data, "邮箱验证码不正确或已过期");
        }

        long id = userRepo.insertUser(u, mail, encoder.encode(password), u, randomEmoji(), "EMAIL");
        userRepo.logLogin(id, u, "REGISTER", true, null, ip, ua);
        User user = userRepo.findById(id).orElseThrow();
        return successLogin(user, "EMAIL", ip, ua, "注册成功，已自动登录");
    }

    // ==================================================================
    // 登录：账号 + 密码 + 图形验证码
    // ==================================================================

    public Map<String, Object> loginByPassword(String account, String password, String captchaKey,
                                               String captchaCode, String ip, String ua) {
        Map<String, Object> data = new LinkedHashMap<>();
        if (!sync.mysqlMode()) {
            return fail(data, "数据库未连接，无法登录（演示模式可直接使用内置账号）");
        }
        String acc = trim(account);
        if (acc == null || password == null || password.isEmpty()) {
            return fail(data, "请输入账号和密码");
        }
        if (!rateLimiter.allow("pw:" + ip, 30, 300)) {
            return fail(data, "尝试过于频繁，请 5 分钟后再试");
        }
        if (captchaKey == null || captchaCode == null || captchaCode.isBlank()) {
            return fail(data, "请输入图形验证码");
        }
        if (!captchaService.verify(captchaKey, captchaCode, "LOGIN")) {
            data.put("success", false);
            data.put("message", "图形验证码错误或已失效");
            data.put("needCaptcha", true);
            userRepo.logLogin(null, acc, "PASSWORD", false, "图形验证码错误", ip, ua);
            return data;
        }

        Optional<User> found = acc.contains("@") ? userRepo.findByEmail(acc) : userRepo.findByUsername(acc);
        if (found.isEmpty()) {
            userRepo.logLogin(null, acc, "PASSWORD", false, "账号不存在", ip, ua);
            return fail(data, "账号或密码错误");
        }
        User user = found.get();
        String hash = userRepo.passwordHash(user.id);
        if (hash == null || !encoder.matches(password, hash)) {
            userRepo.logLogin(user.id, acc, "PASSWORD", false, "密码错误", ip, ua);
            return fail(data, "账号或密码错误");
        }
        userRepo.logLogin(user.id, acc, "PASSWORD", true, null, ip, ua);
        return successLogin(user, "PASSWORD", ip, ua, "登录成功");
    }

    /** 修改密码（已登录） */
    public Map<String, Object> changePassword(long userId, String oldPassword, String newPassword) {
        Map<String, Object> data = new LinkedHashMap<>();
        if (newPassword == null || newPassword.length() < 6) {
            return fail(data, "新密码至少 6 位");
        }
        String hash = userRepo.passwordHash(userId);
        if (hash != null && !hash.isBlank()) {
            if (oldPassword == null || !encoder.matches(oldPassword, hash)) {
                return fail(data, "原密码不正确");
            }
        }
        userRepo.updatePassword(userId, encoder.encode(newPassword));
        userRepo.deleteAllTokens(userId);
        data.put("success", true);
        data.put("message", "密码已修改，其它设备需要重新登录");
        return data;
    }

    // ==================================================================
    // 登录：邮箱验证码（不存在则自动注册）
    // ==================================================================

    public Map<String, Object> loginByEmail(String email, String code, String ip, String ua) {
        Map<String, Object> data = new LinkedHashMap<>();
        if (!sync.mysqlMode()) {
            return fail(data, "数据库未连接，无法登录");
        }
        String mail = trim(email);
        if (mail == null || code == null || code.isBlank()) {
            return fail(data, "请输入邮箱和验证码");
        }
        if (!rateLimiter.allow("email-login:" + ip, 30, 300)) {
            return fail(data, "尝试过于频繁，请稍后再试");
        }
        if (!emailCodeService.verify(mail, code, "LOGIN")) {
            userRepo.logLogin(null, mail, "EMAIL", false, "验证码错误", ip, ua);
            return fail(data, "邮箱验证码不正确或已过期");
        }
        Optional<User> found = userRepo.findByEmail(mail);
        User user;
        String message;
        if (found.isPresent()) {
            user = found.get();
            message = "登录成功";
        } else {
            // 邮箱验证通过 = 邮箱归属确认，直接注册一个账号
            String username = "bili_" + mail.substring(0, Math.min(8, mail.indexOf('@')))
                    + (int) (Math.random() * 9000 + 1000);
            long id = userRepo.insertUser(username, mail, null, username, randomEmoji(), "EMAIL");
            user = userRepo.findById(id).orElseThrow();
            message = "首次使用邮箱登录，已自动创建账号";
        }
        userRepo.logLogin(user.id, mail, "EMAIL", true, null, ip, ua);
        return successLogin(user, "EMAIL", ip, ua, message);
    }

    // ==================================================================
    // 找回密码
    // ==================================================================

    public Map<String, Object> resetPassword(String email, String code, String newPassword, String ip) {
        Map<String, Object> data = new LinkedHashMap<>();
        if (!sync.mysqlMode()) {
            return fail(data, "数据库未连接，无法重置密码");
        }
        String mail = trim(email);
        if (mail == null || code == null || newPassword == null || newPassword.length() < 6) {
            return fail(data, "请填写邮箱、验证码和至少 6 位的新密码");
        }
        if (!rateLimiter.allow("reset:" + ip, 20, 3600)) {
            return fail(data, "操作过于频繁，请稍后再试");
        }
        if (!emailCodeService.verify(mail, code, "RESET")) {
            return fail(data, "邮箱验证码不正确或已过期");
        }
        Optional<User> found = userRepo.findByEmail(mail);
        if (found.isEmpty()) {
            return fail(data, "该邮箱还没有注册过账号");
        }
        userRepo.updatePassword(found.get().id, encoder.encode(newPassword));
        userRepo.deleteAllTokens(found.get().id);
        userRepo.logLogin(found.get().id, mail, "RESET", true, null, ip, null);
        data.put("success", true);
        data.put("message", "密码已重置，请用新密码登录");
        return data;
    }

    // ==================================================================
    // 令牌
    // ==================================================================

    /** 登录成功：签发 token + 返回用户信息 */
    public Map<String, Object> successLogin(User user, String loginType, String ip, String ua, String message) {
        String token = userRepo.createToken(user.id, loginType, 7);
        userRepo.touchLogin(user.id, ip);
        user = userRepo.findById(user.id).orElse(user);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("success", true);
        data.put("message", message);
        data.put("token", token);
        data.put("loginType", loginType);
        data.put("user", user);
        data.put("expPercent", user.expMax == 0 ? 0 : Math.round(user.exp * 1000.0 / user.expMax) / 10.0);
        data.put("nextLevelExp", Math.max(0, user.expMax - user.exp));
        return data;
    }

    public Optional<User> userOfToken(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        return userRepo.userIdByToken(token).flatMap(userRepo::findById);
    }

    /** 校验账号密码（二维码「手机端确认」时复用），成功返回用户 */
    public Optional<User> verifyCredentials(String account, String password) {
        String acc = trim(account);
        if (acc == null || password == null) {
            return Optional.empty();
        }
        Optional<User> found = acc.contains("@") ? userRepo.findByEmail(acc) : userRepo.findByUsername(acc);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        String hash = userRepo.passwordHash(found.get().id);
        if (hash == null || !encoder.matches(password, hash)) {
            return Optional.empty();
        }
        return found;
    }

    /** 二维码确认登录成功后签发令牌 */
    public String issueToken(long userId, String loginType) {
        return userRepo.createToken(userId, loginType, 7);
    }

    public void logout(String token) {
        if (token != null) {
            userRepo.deleteToken(token);
        }
    }

    private Map<String, Object> fail(Map<String, Object> data, String message) {
        data.put("success", false);
        data.put("message", message);
        return data;
    }

    private String trim(String s) {
        return s == null ? null : s.trim();
    }

    private String randomEmoji() {
        String[] emojis = {"😀", "😎", "🐱", "🐼", "🦊", "🐧", "🍅", "🌸", "🚀", "🎮"};
        return emojis[(int) (Math.random() * emojis.length)];
    }
}
