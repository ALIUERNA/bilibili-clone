package com.bili.demo.controller;

import com.bili.demo.auth.AuthService;
import com.bili.demo.auth.UserContext;
import com.bili.demo.data.UserStore;
import com.bili.demo.model.User;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 登录 / 退出 / 当前用户 / 改资料 / 上传头像 / 签到。
 *
 * 真正的账号体系在 AuthController（/api/auth/**）：图形验证码、邮箱验证码、二维码登录。
 * 这里保留原来的接口，并且都基于「当前登录用户」——未登录时自动回退到演示账号，老功能不受影响。
 */
@RestController
public class UserController {

    /** 允许上传的图片类型 */
    private static final List<String> ALLOWED_EXT = List.of("png", "jpg", "jpeg", "gif", "webp", "bmp");
    private static final long MAX_AVATAR_SIZE = 5 * 1024 * 1024L;   // 5MB

    private final UserStore userStore;
    private final AuthService authService;

    public UserController(UserStore userStore, AuthService authService) {
        this.userStore = userStore;
        this.authService = authService;
    }

    /**
     * 一键登录（演示用）：给演示账号签发一个真实 token。
     * 正式登录请看 POST /api/auth/login/password、/api/auth/login/email 和二维码接口。
     */
    @PostMapping("/api/user/login")
    public ResponseEntity<?> login() {
        User demo = userStore.resolve(UserStore.DEMO_USER_ID);
        return ResponseEntity.ok(authService.successLogin(demo, "DEMO", "127.0.0.1", "demo", "已使用演示账号登录"));
    }

    @PostMapping("/api/user/logout")
    public Map<String, Object> logout() {
        authService.logout(UserContext.token());
        return Map.of("success", true, "message", "已退出登录");
    }

    /** 当前登录用户（含经验进度） */
    @GetMapping("/api/user/me")
    public Map<String, Object> me() {
        return userStore.stats();
    }

    /** 修改昵称 / 个性签名 */
    @PostMapping("/api/user/profile")
    public ResponseEntity<?> updateProfile(@RequestBody Map<String, Object> body) {
        String name = body.get("name") == null ? null : String.valueOf(body.get("name"));
        String sign = body.get("sign") == null ? null : String.valueOf(body.get("sign"));
        if (name != null && name.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "昵称不能为空"));
        }
        Map<String, Object> data = userStore.updateProfile(name, sign);
        data.put("user", userStore.get());
        data.put("message", "资料已更新");
        return ResponseEntity.ok(data);
    }

    /** 上传头像（multipart/form-data，字段名 file） */
    @PostMapping("/api/user/avatar")
    public ResponseEntity<?> uploadAvatar(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "没有选择文件"));
        }
        if (file.getSize() > MAX_AVATAR_SIZE) {
            return ResponseEntity.badRequest().body(Map.of("message", "图片太大了，请上传 5MB 以内的图片"));
        }
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase();
        if (!contentType.startsWith("image/")) {
            return ResponseEntity.badRequest().body(Map.of("message", "只能上传图片文件（png / jpg / gif / webp）"));
        }

        String ext = extensionOf(file.getOriginalFilename(), contentType);
        if (!ALLOWED_EXT.contains(ext)) {
            return ResponseEntity.badRequest().body(Map.of("message", "不支持的图片格式：" + ext));
        }

        try {
            Path dir = userStore.uploadPath().resolve("avatars");
            Files.createDirectories(dir);
            String fileName = "avatar-" + System.currentTimeMillis() + "-" + (int) (Math.random() * 1000) + "." + ext;
            Path target = dir.resolve(fileName);
            file.transferTo(target.toFile());

            String url = "/api/files/avatar/" + fileName;
            userStore.setAvatarUrl(url);

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("faceUrl", url);
            data.put("user", userStore.get());
            data.put("message", "头像上传成功");
            return ResponseEntity.ok(data);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("message", "头像保存失败：" + e.getMessage()));
        }
    }

    /** 改回 emoji 头像（删掉已上传的头像图片） */
    @DeleteMapping("/api/user/avatar")
    public Map<String, Object> clearAvatar() {
        String old = userStore.get().faceUrl;
        userStore.clearAvatar();
        if (old != null && old.startsWith("/api/files/avatar/")) {
            try {
                Path file = userStore.uploadPath().resolve("avatars").resolve(old.substring(old.lastIndexOf('/') + 1));
                Files.deleteIfExists(file);
            } catch (Exception ignored) {
                // 文件删不掉也不影响使用
            }
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("user", userStore.get());
        data.put("message", "已改回表情头像");
        return data;
    }

    /** 读取上传的头像（存的是磁盘文件，所以要自己开一个读取接口） */
    @GetMapping("/api/files/avatar/{fileName}")
    public ResponseEntity<Resource> avatarFile(@PathVariable String fileName) {
        // 防止 ../ 穿越目录
        if (fileName.contains("..") || fileName.contains("/") || fileName.contains("\\")) {
            return ResponseEntity.badRequest().build();
        }
        Path path = userStore.uploadPath().resolve("avatars").resolve(fileName);
        if (!Files.exists(path)) {
            return ResponseEntity.notFound().build();
        }
        Resource resource = new FileSystemResource(path.toFile());
        MediaType type = MediaType.IMAGE_PNG;
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            type = MediaType.IMAGE_JPEG;
        } else if (lower.endsWith(".gif")) {
            type = MediaType.IMAGE_GIF;
        } else if (lower.endsWith(".webp")) {
            type = MediaType.parseMediaType("image/webp");
        }
        return ResponseEntity.ok()
                .contentType(type)
                // 头像文件名带时间戳，不会重复，可以放心长缓存
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=31536000")
                .body(resource);
    }

    /** 每日签到：+10 经验，+5 硬币 */
    @PostMapping("/api/user/checkin")
    public Map<String, Object> checkin() {
        return userStore.checkin();
    }

    private String extensionOf(String originalName, String contentType) {
        if (originalName != null && originalName.contains(".")) {
            String ext = originalName.substring(originalName.lastIndexOf('.') + 1).toLowerCase();
            if (!ext.isBlank()) {
                return ext;
            }
        }
        return switch (contentType) {
            case "image/png" -> "png";
            case "image/jpeg", "image/jpg" -> "jpg";
            case "image/gif" -> "gif";
            case "image/webp" -> "webp";
            case "image/bmp" -> "bmp";
            default -> "png";
        };
    }

    /** 需要时给其它 Controller 用 */
    public UserStore store() {
        return userStore;
    }
}
