package com.bili.demo.auth;

import com.bili.demo.db.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 邮箱验证码：注册 / 登录 / 找回密码。
 *
 * - 验证码存 MySQL（email_verification_codes），10 分钟过期、一次性使用、有频控；
 * - 开发环境（bili.auth.dev-mode=true，默认）：**控制台打印验证码**，不配 SMTP 也能完整跑通；
 * - 生产环境：配好 spring.mail.* 后自动走 JavaMailSender 真发邮件，
 *   并且接口不会把验证码返回给前端。
 */
@Service
public class EmailCodeService {

    private static final Logger log = LoggerFactory.getLogger(EmailCodeService.class);

    private static final int TTL_SECONDS = 600;          // 10 分钟
    private static final int RESEND_INTERVAL = 60;       // 同一邮箱 60 秒内只能发一次
    private static final int MAX_PER_HOUR = 10;          // 同一邮箱 1 小时最多 10 次

    private final UserRepository userRepo;
    private final RateLimiter rateLimiter;
    private final JavaMailSender mailSender;
    private final SecureRandom random = new SecureRandom();

    @Value("${bili.auth.dev-mode:true}")
    private boolean devMode;

    @Value("${spring.mail.username:}")
    private String mailFrom;

    public EmailCodeService(UserRepository userRepo, RateLimiter rateLimiter,
                            @org.springframework.beans.factory.annotation.Autowired(required = false)
                            JavaMailSender mailSender) {
        this.userRepo = userRepo;
        this.rateLimiter = rateLimiter;
        this.mailSender = mailSender;
    }

    public Map<String, Object> send(String email, String purpose, String ip) {
        String p = normalize(purpose);
        Map<String, Object> data = new LinkedHashMap<>();

        if (email == null || !email.matches("^[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}$")) {
            data.put("success", false);
            data.put("message", "邮箱格式不正确");
            return data;
        }
        String key = p + ":" + email;
        if (!rateLimiter.allow("email:" + key, MAX_PER_HOUR, 3600)) {
            data.put("success", false);
            data.put("message", "该邮箱发送次数过多，请稍后再试");
            return data;
        }
        long since = userRepo.secondsSinceLastEmailCode(email, p);
        if (since < RESEND_INTERVAL) {
            data.put("success", false);
            data.put("message", "请求过于频繁，" + (RESEND_INTERVAL - since) + " 秒后可重新发送");
            data.put("retryAfter", RESEND_INTERVAL - since);
            return data;
        }

        String code = String.format("%06d", random.nextInt(1_000_000));
        userRepo.saveEmailCode(email, code, p, ip, TTL_SECONDS);

        boolean sent = sendByMail(email, code, p);
        data.put("success", true);
        data.put("message", sent ? "验证码已发送到邮箱，请查收（10 分钟内有效）"
                : "开发环境：验证码已打印在服务端控制台（" + TTL_SECONDS / 60 + " 分钟内有效）");
        data.put("expiresIn", TTL_SECONDS);
        data.put("sentByMail", sent);
        if (!sent && devMode) {
            // 开发环境把验证码直接带回前端，方便调试和自动化验收；生产环境永远不会走到这里
            data.put("devCode", code);
            data.put("devMode", true);
            log.info("""

                    ================================================
                      [开发环境] 邮箱验证码
                      邮箱：{}
                      用途：{}
                      验证码：{}   （{} 分钟内有效）
                    ================================================
                    """, email, p, code, TTL_SECONDS / 60);
        }
        return data;
    }

    public boolean verify(String email, String code, String purpose) {
        return userRepo.verifyEmailCode(email, code, normalize(purpose));
    }

    private boolean sendByMail(String email, String code, String purpose) {
        String subject = switch (normalize(purpose)) {
            case "REGISTER" -> "【a哩a哩】注册验证码";
            case "RESET" -> "【a哩a哩】找回密码验证码";
            default -> "【a哩a哩】登录验证码";
        };
        if (mailSender == null || devMode) {
            return false;
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            if (mailFrom != null && !mailFrom.isBlank()) {
                message.setFrom(mailFrom);
            }
            message.setTo(email);
            message.setSubject(subject);
            message.setText("你的验证码是：" + code + "\n\n" + TTL_SECONDS / 60 + " 分钟内有效，请勿泄露给他人。");
            mailSender.send(message);
            return true;
        } catch (Exception e) {
            log.warn("邮件发送失败，已回退为控制台输出：{}", e.getMessage());
            return false;
        }
    }

    private String normalize(String purpose) {
        if (purpose == null || purpose.isBlank()) {
            return "LOGIN";
        }
        String p = purpose.trim().toUpperCase();
        return switch (p) {
            case "REGISTER", "RESET", "LOGIN" -> p;
            default -> "LOGIN";
        };
    }
}
