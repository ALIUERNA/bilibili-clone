package com.bili.demo.auth;

import com.bili.demo.db.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 图形验证码：后端生成图片（PNG，Base64 返回），存 MySQL，5 分钟过期且一次性使用。
 * 用 JDK 自带的 Java2D 画图，不依赖任何第三方验证码库。
 */
@Service
public class CaptchaService {

    private static final Logger log = LoggerFactory.getLogger(CaptchaService.class);

    /** 去掉了 0/O/1/I 这些容易看错的字符 */
    private static final char[] CHARS = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ".toCharArray();
    private static final int WIDTH = 120;
    private static final int HEIGHT = 40;
    private static final int TTL_SECONDS = 300;   // 5 分钟

    private final UserRepository userRepo;
    private final SecureRandom random = new SecureRandom();

    public CaptchaService(UserRepository userRepo) {
        this.userRepo = userRepo;
    }

    public Map<String, Object> generate(String purpose, String ip) {
        String code = randomCode(4);
        String key = UUID.randomUUID().toString().replace("-", "");
        userRepo.saveCaptcha(key, code, normalize(purpose), ip, TTL_SECONDS);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("captchaKey", key);
        data.put("image", "data:image/png;base64," + draw(code));
        data.put("expiresIn", TTL_SECONDS);
        data.put("length", code.length());
        return data;
    }

    /** 校验并消费（一次性）：重复使用同一个 captchaKey 会失败 */
    public boolean verify(String key, String code, String purpose) {
        return userRepo.consumeCaptcha(key, code, normalize(purpose));
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

    private String randomCode(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(CHARS[random.nextInt(CHARS.length)]);
        }
        return sb.toString();
    }

    /** 画验证码图片：渐变背景 + 干扰线 + 干扰点 + 旋转字符 */
    private String draw(String code) {
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            // 背景
            GradientPaint bg = new GradientPaint(0, 0, new Color(0xFFF5F8), WIDTH, HEIGHT, new Color(0xEAF6FF));
            g.setPaint(bg);
            g.fillRect(0, 0, WIDTH, HEIGHT);

            // 干扰线
            Color[] lineColors = {new Color(0xFB7299), new Color(0x00AEEC), new Color(0xC7C7C7)};
            for (int i = 0; i < 6; i++) {
                g.setColor(lineColors[i % lineColors.length]);
                g.setStroke(new BasicStroke(1.1f));
                int x1 = random.nextInt(WIDTH);
                int y1 = random.nextInt(HEIGHT);
                int x2 = random.nextInt(WIDTH);
                int y2 = random.nextInt(HEIGHT);
                g.drawLine(x1, y1, x2, y2);
            }

            // 干扰点
            for (int i = 0; i < 60; i++) {
                g.setColor(new Color(120 + random.nextInt(100), 120 + random.nextInt(100), 120 + random.nextInt(100)));
                g.fillRect(random.nextInt(WIDTH), random.nextInt(HEIGHT), 1, 1);
            }

            // 字符
            int step = (WIDTH - 16) / code.length();
            for (int i = 0; i < code.length(); i++) {
                double angle = (random.nextInt(50) - 25) * Math.PI / 180;
                int fontSize = 24 + random.nextInt(6);
                Font font = new Font("Arial", Font.BOLD, fontSize);
                g.setFont(font);
                g.setColor(new Color(30 + random.nextInt(80), 30 + random.nextInt(80), 30 + random.nextInt(80)));
                int x = 10 + i * step;
                int y = 30 + random.nextInt(4);
                g.rotate(angle, x + 8, y - 8);
                g.drawString(String.valueOf(code.charAt(i)), x, y);
                g.rotate(-angle, x + 8, y - 8);
            }
        } finally {
            g.dispose();
        }

        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", out);
            return Base64.getEncoder().encodeToString(out.toByteArray());
        } catch (Exception e) {
            log.error("验证码生成失败", e);
            return "";
        }
    }
}
