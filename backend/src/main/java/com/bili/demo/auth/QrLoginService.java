package com.bili.demo.auth;

import com.bili.demo.db.UserRepository;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 二维码登录。
 *
 * 状态机（和 B 站一致）：
 *   WAITING（待扫描）→ SCANNED（已扫描）→ CONFIRMED（已确认，返回 token）
 *                ↘ EXPIRED（已过期，可刷新） ↘ CANCELED（已取消）
 *
 * 二维码内容 = {base}/#/qr/{qrId}，手机扫码后打开「确认登录」页面；
 * 桌面端用 poll 轮询取状态，确认后拿到一次性 token。
 * 本项目还提供「模拟手机」接口（/api/auth/qr/scan + /confirm），
 * 没有手机也能在浏览器里完整走通整套流程。
 */
@Service
public class QrLoginService {

    private static final Logger log = LoggerFactory.getLogger(QrLoginService.class);

    /** 二维码有效期（秒） */
    @Value("${bili.auth.qr-ttl:120}")
    private int ttlSeconds;

    /** 二维码里写的站点地址，扫码后打开的确认页；留空则用请求头里的 Host 自动推断 */
    @Value("${bili.auth.qr-base-url:}")
    private String qrBaseUrl;

    private final UserRepository userRepo;

    public QrLoginService(UserRepository userRepo) {
        this.userRepo = userRepo;
    }

    public Map<String, Object> create(String requestBaseUrl, String ip) {
        String qrId = UUID.randomUUID().toString().replace("-", "");
        userRepo.createQrSession(qrId, ip, ttlSeconds);

        String base = (qrBaseUrl != null && !qrBaseUrl.isBlank()) ? qrBaseUrl : requestBaseUrl;
        String scanUrl = base + "/#/qr/" + qrId;
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("qrId", qrId);
        data.put("scanUrl", scanUrl);
        data.put("image", renderQrCode(scanUrl, 240));
        data.put("status", "WAITING");
        data.put("expiresIn", ttlSeconds);
        data.put("message", "请使用a哩a哩客户端扫码登录");
        return data;
    }

    public Map<String, Object> status(String qrId) {
        return userRepo.qrStatus(qrId);
    }

    public Map<String, Object> scan(String qrId) {
        Map<String, Object> data = new LinkedHashMap<>();
        Map<String, Object> info = userRepo.qrInfo(qrId);
        String status = String.valueOf(info.getOrDefault("status", "EXPIRED"));
        if ("WAITING".equals(status)) {
            userRepo.markQrScanned(qrId);
            data.put("success", true);
            data.put("status", "SCANNED");
            data.put("message", "已扫码，请在手机上点击确认");
        } else if ("SCANNED".equals(status)) {
            data.put("success", true);
            data.put("status", "SCANNED");
            data.put("message", "已经扫过码了，请在手机上确认");
        } else {
            data.put("success", false);
            data.put("status", status);
            data.put("message", "EXPIRED".equals(status) ? "二维码已过期，请刷新" : "当前状态不能扫码：" + status);
        }
        return data;
    }

    public Map<String, Object> confirm(String qrId, Long userId, String token) {
        Map<String, Object> data = new LinkedHashMap<>();
        boolean ok = userRepo.markQrConfirmed(qrId, userId, token);
        data.put("success", ok);
        data.put("status", ok ? "CONFIRMED" : "EXPIRED");
        data.put("message", ok ? "确认成功，桌面端会自动登录" : "二维码已过期或被取消，请刷新");
        return data;
    }

    public Map<String, Object> cancel(String qrId) {
        boolean ok = userRepo.markQrCanceled(qrId);
        return Map.of("success", ok, "status", "CANCELED", "message", ok ? "已取消本次登录" : "无法取消当前会话");
    }

    /** 生成二维码 PNG，返回 data:image/png;base64,... */
    private String renderQrCode(String content, int size) {
        try {
            Map<EncodeHintType, Object> hints = new LinkedHashMap<>();
            hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
            hints.put(EncodeHintType.MARGIN, 1);
            BitMatrix matrix = new QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size, hints);
            try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                MatrixToImageWriter.writeToStream(matrix, "PNG", out);
                return "data:image/png;base64," + Base64.getEncoder().encodeToString(out.toByteArray());
            }
        } catch (Exception e) {
            log.error("二维码生成失败", e);
            return "";
        }
    }
}
