package com.bili.demo.controller;

import com.bili.demo.auth.*;
import com.bili.demo.db.DataSyncService;
import com.bili.demo.model.User;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 登录认证接口。
 *
 * 三种登录方式（全部可用、全部落 MySQL）：
 *   1. 账号密码 + 图形验证码： GET /api/auth/captcha + POST /api/auth/login/password
 *   2. 邮箱验证码：           POST /api/auth/email/send + POST /api/auth/login/email
 *   3. 二维码登录：           POST /api/auth/qr/create + GET /api/auth/qr/poll …
 *
 * 另外提供注册、找回密码、修改密码、退出登录。
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final CaptchaService captchaService;
    private final EmailCodeService emailCodeService;
    private final QrLoginService qrLoginService;
    private final RateLimiter rateLimiter;
    private final DataSyncService sync;
    private final com.bili.demo.db.UserRepository userRepository;

    @Value("${bili.auth.dev-mode:true}")
    private boolean devMode;

    public AuthController(AuthService authService, CaptchaService captchaService,
                          EmailCodeService emailCodeService, QrLoginService qrLoginService,
                          RateLimiter rateLimiter, DataSyncService sync,
                          com.bili.demo.db.UserRepository userRepository) {
        this.authService = authService;
        this.captchaService = captchaService;
        this.emailCodeService = emailCodeService;
        this.qrLoginService = qrLoginService;
        this.rateLimiter = rateLimiter;
        this.sync = sync;
        this.userRepository = userRepository;
    }

    /** 当前登录环境信息，前端用来决定是否显示「开发环境验证码」 */
    @GetMapping("/status")
    public Map<String, Object> status() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("mysqlMode", sync.mysqlMode());
        data.put("devMode", devMode);
        data.put("loginTypes", new String[]{"PASSWORD", "EMAIL", "QR"});
        data.put("loggedIn", UserContext.loggedIn());
        return data;
    }

    // ---------------- 1. 图形验证码 ----------------

    /** 生成图形验证码：返回 {captchaKey, image(data:image/png;base64), expiresIn} */
    @GetMapping("/captcha")
    public ResponseEntity<?> captcha(@RequestParam(defaultValue = "LOGIN") String purpose,
                                     HttpServletRequest request) {
        String ip = clientIp(request);
        if (!rateLimiter.allow("captcha:" + ip, 60, 60)) {
            return ResponseEntity.status(429).body(Map.of(
                    "success", false,
                    "message", "验证码请求过于频繁，请稍后再试"));
        }
        try {
            return ResponseEntity.ok(captchaService.generate(purpose, ip));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                    "success", false, "message", "验证码生成失败：" + e.getMessage()));
        }
    }

    // ---------------- 2. 邮箱验证码 ----------------

    /** 发送邮箱验证码：purpose = LOGIN / REGISTER / RESET */
    @PostMapping("/email/send")
    public ResponseEntity<?> sendEmailCode(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        String email = str(body.get("email"));
        String purpose = str(body.getOrDefault("purpose", "LOGIN"));
        String ip = clientIp(request);
        if (!rateLimiter.allow("email-ip:" + ip, 20, 3600)) {
            return ResponseEntity.status(429).body(Map.of(
                    "success", false, "message", "当前 IP 请求过于频繁，请稍后再试"));
        }
        Map<String, Object> data = emailCodeService.send(email, purpose, ip);
        return ResponseEntity.ok(data);
    }

    // ---------------- 3. 注册 ----------------

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        if (!sync.mysqlMode()) {
            return ResponseEntity.ok(Map.of("success", false, "message", "数据库未连接，无法注册"));
        }
        // 注册同样需要图形验证码，防止脚本批量注册
        if (!captchaService.verify(str(body.get("captchaKey")), str(body.get("captchaCode")), "REGISTER")) {
            return ResponseEntity.ok(Map.of("success", false, "message", "图形验证码错误或已失效", "needCaptcha", true));
        }
        Map<String, Object> data = authService.register(
                str(body.get("username")), str(body.get("email")), str(body.get("password")),
                str(body.get("emailCode")), clientIp(request), userAgent(request));
        return ResponseEntity.ok(data);
    }

    // ---------------- 4. 账号密码登录（图形验证码） ----------------

    @PostMapping("/login/password")
    public ResponseEntity<?> loginPassword(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        Map<String, Object> data = authService.loginByPassword(
                str(body.getOrDefault("account", body.get("username"))),
                str(body.get("password")),
                str(body.get("captchaKey")),
                str(body.get("captchaCode")),
                clientIp(request), userAgent(request));
        return ResponseEntity.ok(data);
    }

    // ---------------- 5. 邮箱验证码登录 ----------------

    @PostMapping("/login/email")
    public ResponseEntity<?> loginEmail(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        Map<String, Object> data = authService.loginByEmail(
                str(body.get("email")), str(body.get("code")),
                clientIp(request), userAgent(request));
        return ResponseEntity.ok(data);
    }

    // ---------------- 6. 找回密码 / 修改密码 ----------------

    @PostMapping("/password/reset")
    public ResponseEntity<?> resetPassword(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        if (!captchaService.verify(str(body.get("captchaKey")), str(body.get("captchaCode")), "RESET")) {
            return ResponseEntity.ok(Map.of("success", false, "message", "图形验证码错误或已失效", "needCaptcha", true));
        }
        return ResponseEntity.ok(authService.resetPassword(
                str(body.get("email")), str(body.get("code")), str(body.get("newPassword")), clientIp(request)));
    }

    @PostMapping("/password/change")
    public ResponseEntity<?> changePassword(@RequestBody Map<String, Object> body) {
        if (!UserContext.loggedIn()) {
            return ResponseEntity.status(401).body(Map.of("success", false, "message", "请先登录"));
        }
        return ResponseEntity.ok(authService.changePassword(UserContext.userId(),
                str(body.get("oldPassword")), str(body.get("newPassword"))));
    }

    /** 已登录用户绑定 / 换绑邮箱（需要新邮箱的验证码） */
    @PostMapping("/email/bind")
    public ResponseEntity<?> bindEmail(@RequestBody Map<String, Object> body) {
        if (!UserContext.loggedIn()) {
            return ResponseEntity.status(401).body(Map.of("success", false, "message", "请先登录"));
        }
        String email = str(body.get("email"));
        if (email == null || !emailCodeService.verify(email, str(body.get("code")), "REGISTER")) {
            return ResponseEntity.ok(Map.of("success", false, "message", "邮箱验证码不正确或已过期"));
        }
        if (userRepository.existsEmail(email)) {
            return ResponseEntity.ok(Map.of("success", false, "message", "该邮箱已被其它账号绑定"));
        }
        userRepository.updateEmail(UserContext.userId(), email);
        return ResponseEntity.ok(Map.of("success", true, "message", "邮箱绑定成功"));
    }

    // ---------------- 7. 退出登录 ----------------

    @PostMapping("/logout")
    public Map<String, Object> logout(HttpServletRequest request) {
        String token = UserContext.token();
        if (token == null) {
            String auth = request.getHeader("Authorization");
            token = auth != null && auth.startsWith("Bearer ") ? auth.substring(7) : null;
        }
        authService.logout(token);
        return Map.of("success", true, "message", "已退出登录");
    }

    // ---------------- 8. 二维码登录 ----------------

    /** 生成二维码：返回 qrId + 二维码图片（Base64）+ 过期时间 */
    @PostMapping("/qr/create")
    public Map<String, Object> qrCreate(HttpServletRequest request) {
        String ip = clientIp(request);
        if (!rateLimiter.allow("qr:" + ip, 60, 60)) {
            return Map.of("success", false, "message", "请求过于频繁，请稍后再试");
        }
        Map<String, Object> data = new LinkedHashMap<>(qrLoginService.create(baseUrl(request), ip));
        data.put("success", true);
        return data;
    }

    @GetMapping("/qr/create")
    public Map<String, Object> qrCreateGet(HttpServletRequest request) {
        return qrCreate(request);
    }

    /** 前端轮询：返回 WAITING / SCANNED / CONFIRMED(带 token) / EXPIRED / CANCELED */
    @GetMapping("/qr/poll")
    public Map<String, Object> qrPoll(@RequestParam String qrId) {
        Map<String, Object> data = new LinkedHashMap<>(qrLoginService.status(qrId));
        data.put("success", true);
        return data;
    }

    /** 模拟手机端「扫码」（真实场景由手机摄像头触发） */
    @PostMapping("/qr/scan")
    public Map<String, Object> qrScan(@RequestBody Map<String, Object> body) {
        return qrLoginService.scan(str(body.get("qrId")));
    }

    /**
     * 手机端「确认登录」。
     * - 桌面端已经登录：直接确认；
     * - 未登录：带上账号密码（就是手机上的一次正常登录）。
     */
    @PostMapping("/qr/confirm")
    public Map<String, Object> qrConfirm(@RequestBody Map<String, Object> body) {
        String qrId = str(body.get("qrId"));
        Long userId = UserContext.userId();
        if (userId == null) {
            Optional<User> user = authService.verifyCredentials(str(body.get("account")), str(body.get("password")));
            if (user.isEmpty()) {
                return Map.of("success", false, "message", "账号或密码错误，无法确认登录");
            }
            userId = user.get().id;
        }
        String token = authService.issueToken(userId, "QR");
        Map<String, Object> data = new LinkedHashMap<>(qrLoginService.confirm(qrId, userId, token));
        User u = authService.userOfToken(token).orElse(null);
        if (u != null) {
            data.put("user", u);
        }
        return data;
    }

    @PostMapping("/qr/cancel")
    public Map<String, Object> qrCancel(@RequestBody Map<String, Object> body) {
        return qrLoginService.cancel(str(body.get("qrId")));
    }

    // ---------------- 工具 ----------------

    private String str(Object o) {
        return o == null ? null : String.valueOf(o).trim();
    }

    private String clientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isBlank()) {
            return ip.split(",")[0].trim();
        }
        String real = request.getHeader("X-Real-IP");
        if (real != null && !real.isBlank()) {
            return real.trim();
        }
        return request.getRemoteAddr();
    }

    private String userAgent(HttpServletRequest request) {
        String ua = request.getHeader("User-Agent");
        return ua == null ? "" : ua;
    }

    /** 二维码里写入的站点地址（扫码后打开的确认页） */
    private String baseUrl(HttpServletRequest request) {
        String scheme = request.getHeader("X-Forwarded-Proto");
        if (scheme == null || scheme.isBlank()) {
            scheme = request.getScheme();
        }
        String host = request.getHeader("X-Forwarded-Host");
        if (host == null || host.isBlank()) {
            host = request.getHeader("Host");
        }
        if (host == null || host.isBlank()) {
            host = "localhost:" + request.getServerPort();
        }
        return scheme + "://" + host;
    }
}
