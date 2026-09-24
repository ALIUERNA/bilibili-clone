package com.bili.demo.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 上传目录（uploads）统一解析，避免「项目根/uploads」和「backend/uploads」两份目录互相看不见。
 *
 * 解析顺序：
 *   1. bili.upload-dir（环境变量 BILI_UPLOAD_DIR）配成绝对路径 → 直接用；
 *   2. 配的是相对路径、且当前工作目录下该目录确实存在 → 用当前工作目录下的（兼容老启动方式）；
 *   3. 否则按代码运行位置推断：
 *      - java -jar backend/target/bili-web.jar → <项目根>/uploads
 *      - IDE / mvn（target/classes）           → <backend>/uploads
 *      - Docker（/app/app.jar）                → /app/uploads
 */
@Component
public class UploadPaths {

    private static final Logger log = LoggerFactory.getLogger(UploadPaths.class);

    private final Path root;

    public UploadPaths(@Value("${bili.upload-dir:./uploads}") String configured) {
        this.root = resolveRoot(configured);
        log.info(">>> 上传目录（uploads）：{}", root);
    }

    public Path root() {
        return root;
    }

    public Path resolve(String sub) {
        return root.resolve(sub).normalize();
    }

    private static Path resolveRoot(String configured) {
        String text = (configured == null || configured.isBlank()) ? "./uploads" : configured.trim();
        Path raw = Paths.get(text);
        if (raw.isAbsolute()) {
            return raw.normalize();
        }
        Path cwd = Paths.get("").toAbsolutePath().normalize();
        Path relative = cwd.resolve(raw).normalize();
        if (Files.isDirectory(relative)) {
            // 老行为：从项目根启动就服务 <项目根>/uploads，从 backend 启动就服务 <backend>/uploads
            return relative;
        }
        Path base = inferBaseDir();
        return (base == null ? relative : base.resolve(raw).normalize());
    }

    /** 根据 jar / classes 的位置推断 uploads 应该挂在哪个目录下 */
    private static Path inferBaseDir() {
        try {
            Path location = Paths.get(UploadPaths.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI());
            if (Files.isRegularFile(location)) {
                // 运行的是 jar：backend/target/bili-web.jar → 项目根；/app/app.jar → /app
                Path dir = location.getParent();
                if (dir != null && "target".equals(String.valueOf(dir.getFileName()))) {
                    Path backend = dir.getParent();
                    return backend != null && backend.getParent() != null ? backend.getParent() : dir;
                }
                return dir;
            }
            // 运行的是 classes 目录：backend/target/classes → backend
            if ("classes".equals(String.valueOf(location.getFileName()))) {
                Path target = location.getParent();
                Path backend = target == null ? null : target.getParent();
                if (backend != null) {
                    return backend;
                }
            }
            return location;
        } catch (Exception e) {
            return null;
        }
    }
}
