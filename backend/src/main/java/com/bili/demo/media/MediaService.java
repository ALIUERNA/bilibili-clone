package com.bili.demo.media;

import com.bili.demo.config.UploadPaths;
import com.bili.demo.data.DataStore;
import com.bili.demo.db.ContentRepository;
import com.bili.demo.db.DataSyncService;
import com.bili.demo.model.Video;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 媒体处理服务：真实视频 + FFmpeg 自动截帧封面。
 *
 * 启动时会做这几件事（全部可配置）：
 *   1. 确保 uploads/videos、uploads/covers、uploads/media 目录存在；
 *   2. 如果没有视频文件且 generate-samples=true，用 FFmpeg 生成一批演示视频，
 *      这样「真实播放 + 真实封面帧」这条链路开箱即可验证；
 *   3. 扫描 uploads/videos 里的 mp4/webm/mkv/mov，用 FFmpeg 截取第 3 秒一帧作为封面，
 *      写进 uploads/covers/<同名>.jpg，并把 video_path / cover_path / duration / 宽高写进 MySQL；
 *   4. 把信息同步回内存中的 Video 对象，前端立刻就能拿到 coverUrl / videoUrl。
 *
 * FFmpeg 查找顺序：bili.ffmpeg-path → PATH 里的 ffmpeg → python 的 imageio-ffmpeg（自带静态 ffmpeg）。
 */
@Service
public class MediaService {

    private static final Logger log = LoggerFactory.getLogger(MediaService.class);

    private static final List<String> VIDEO_EXT = List.of("mp4", "webm", "mkv", "mov", "m4v", "flv", "avi");
    private static final Pattern DURATION = Pattern.compile("Duration:\\s*(\\d+):(\\d+):(\\d+(?:\\.\\d+)?)");
    private static final Pattern RESOLUTION = Pattern.compile("(\\d{2,5})x(\\d{2,5})");

    /** 演示视频生成配方：name, lavfi 视频源, 时长(秒) */
    private static final String[][] SAMPLE_RECIPES = {
            {"sample-01", "testsrc2=size=1280x720:rate=25", "12"},
            {"sample-02", "rgbtestsrc=size=1280x720:rate=25", "10"},
            {"sample-03", "smptebars=size=1280x720:rate=25", "8"},
            {"sample-04", "gradients=size=1280x720:rate=25:speed=0.12", "12"},
            {"sample-05", "testsrc=size=1280x720:rate=25", "9"},
            {"sample-06", "haldclutsrc=level=6:size=1280x720:rate=25", "8"},
            {"sample-07", "yuvtestsrc=size=1280x720:rate=25", "8"},
            {"sample-08", "testsrc2=size=960x540:rate=30", "10"},
            {"sample-09", "gradients=size=1280x720:rate=25:speed=0.35", "10"},
            {"sample-10", "rgbtestsrc=size=960x540:rate=25", "7"},
    };

    @Value("${bili.ffmpeg-path:}")
    private String ffmpegPath;

    @Value("${bili.media.auto-scan:true}")
    private boolean autoScan;

    @Value("${bili.media.generate-samples:true}")
    private boolean generateSamples;

    @Value("${bili.media.cover-second:3}")
    private int coverSecond;

    private final DataStore store;
    private final ContentRepository contentRepo;
    private final DataSyncService sync;
    private final UploadPaths uploadPaths;

    private volatile String ffmpegExecutable = null;
    private volatile boolean ffmpegChecked = false;
    private volatile boolean scanning = false;

    public MediaService(DataStore store, ContentRepository contentRepo, DataSyncService sync,
                        UploadPaths uploadPaths) {
        this.store = store;
        this.contentRepo = contentRepo;
        this.sync = sync;
        this.uploadPaths = uploadPaths;
    }

    @PostConstruct
    public void init() {
        if (!autoScan) {
            return;
        }
        try {
            Map<String, Object> result = scan(true);
            log.info(">>> 媒体扫描完成：真实视频 {} 个 / 新生成封面 {} 张 / 已绑定稿件 {} 条",
                    result.get("videoFiles"), result.get("coversGenerated"), result.get("bound"));
        } catch (Exception e) {
            log.warn(">>> 媒体扫描失败（不影响演示）：{}", e.getMessage());
        }
    }

    // ==================================================================
    // 目录 & FFmpeg
    // ==================================================================

    public Path root() {
        return uploadPaths.root();
    }

    public Path videosDir() {
        return root().resolve("videos");
    }

    public Path coversDir() {
        return root().resolve("covers");
    }

    public Path mediaDir() {
        return root().resolve("media");
    }

    private void ensureDirs() throws Exception {
        Files.createDirectories(videosDir());
        Files.createDirectories(coversDir());
        Files.createDirectories(mediaDir());
        Files.createDirectories(root().resolve("avatars"));
    }

    /** 找一个可用的 ffmpeg，找不到返回 null（此时只做占位兜底，不影响视频播放） */
    public synchronized String ffmpeg() {
        if (ffmpegChecked) {
            return ffmpegExecutable;
        }
        ffmpegChecked = true;
        List<String> candidates = new ArrayList<>();
        if (ffmpegPath != null && !ffmpegPath.isBlank()) {
            candidates.add(ffmpegPath.trim());
        }
        candidates.add("ffmpeg");
        candidates.add("ffmpeg.exe");
        for (String c : candidates) {
            if (testFfmpeg(c)) {
                ffmpegExecutable = c;
                log.info(">>> 使用 FFmpeg：{}", c);
                return ffmpegExecutable;
            }
        }
        // 最后的杀手锏：Python 的 imageio-ffmpeg 自带一个静态 ffmpeg
        for (String python : new String[]{"python", "python3", "py"}) {
            try {
                ProcessBuilder pb = new ProcessBuilder(python, "-c",
                        "import imageio_ffmpeg,sys;sys.stdout.write(imageio_ffmpeg.get_ffmpeg_exe())");
                pb.redirectErrorStream(true);
                Process p = pb.start();
                String out;
                try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                    out = r.lines().collect(Collectors.joining()).trim();
                }
                p.waitFor(20, TimeUnit.SECONDS);
                if (out != null && !out.isBlank() && new File(out).exists() && testFfmpeg(out)) {
                    ffmpegExecutable = out;
                    log.info(">>> 使用 FFmpeg（imageio-ffmpeg）：{}", out);
                    return ffmpegExecutable;
                }
            } catch (Exception ignored) {
                // 试下一个
            }
        }
        log.warn(">>> 没有找到 FFmpeg，将只使用渐变占位封面（安装方式见 README：pip install imageio-ffmpeg）");
        return null;
    }

    private boolean testFfmpeg(String exe) {
        try {
            Process p = new ProcessBuilder(exe, "-version").redirectErrorStream(true).start();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                while (r.readLine() != null) {
                    // 读掉输出，避免管道阻塞
                }
            }
            return p.waitFor(15, TimeUnit.SECONDS) && p.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    // ==================================================================
    // 扫描 / 生成 / 绑定
    // ==================================================================

    public synchronized Map<String, Object> scan(boolean withSamples) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (scanning) {
            result.put("message", "正在扫描中，请稍后再试");
            return result;
        }
        scanning = true;
        try {
            ensureDirs();
            // 0) 清理历史遗留：重复绑定，以及「数据库里有绑定、但磁盘文件已经不存在」的失效绑定
            if (sync.mysqlMode()) {
                int duplicate = contentRepo.resetDuplicateBindings();
                int missing = clearMissingBindings();
                if (duplicate > 0 || missing > 0) {
                    if (duplicate > 0) {
                        log.info(">>> 已清理 {} 条重复绑定的稿件（同一个演示视频只保留一条）", duplicate);
                    }
                    if (missing > 0) {
                        log.info(">>> 已清理 {} 条失效媒体绑定（文件已不在 uploads 目录）", missing);
                    }
                    List<Video> fresh = contentRepo.loadVideos();
                    store.videos.clear();
                    store.videos.addAll(fresh);
                }
            }
            List<Path> files = listVideoFiles();
            // 没有视频文件（或还没生成齐）时，用 FFmpeg 补齐演示视频（已存在的会直接跳过）
            if (withSamples && generateSamples) {
                generateSampleVideos();
                files = listVideoFiles();
            }
            int covers = 0;
            // 1) 已有真实视频 → 补封面 + 更新时长宽高
            for (Path file : files) {
                String base = baseName(file);
                Path cover = coversDir().resolve(base + ".jpg");
                if (!Files.exists(cover) && ffmpeg() != null) {
                    if (extractCover(file, cover)) {
                        covers++;
                    }
                }
            }
            // 2) 把还没有真实视频的稿件绑定到视频文件
            int bound = bindVideosToFiles(files);
            if (bound > 0) {
                // 绑定完成后，让首页轮播优先展示真实封面帧
                store.reseedBannersForMedia();
            }

            result.put("success", true);
            result.put("ffmpeg", ffmpeg());
            result.put("videoFiles", files.size());
            result.put("coversGenerated", covers);
            result.put("bound", bound);
            result.put("videosDir", videosDir().toString());
            result.put("coversDir", coversDir().toString());
            result.put("message", files.isEmpty()
                    ? "没有找到视频文件：uploads/videos 为空，页面会使用渐变占位封面（不会出现空白）"
                    : "扫描完成");
            return result;
        } catch (Exception e) {
            log.warn("扫描媒体失败：{}", e.getMessage());
            result.put("success", false);
            result.put("message", "扫描失败：" + e.getMessage());
            return result;
        } finally {
            scanning = false;
        }
    }

    /**
     * 数据库里有绑定、但文件已经不在 uploads 目录的，清掉对应字段，
     * 避免前端拿到必然 404 的封面/视频地址。
     */
    private int clearMissingBindings() {
        int cleared = 0;
        for (Map<String, Object> row : contentRepo.mediaBindings()) {
            long id = ((Number) row.get("id")).longValue();
            String video = row.get("video_path") == null ? null : String.valueOf(row.get("video_path"));
            String cover = row.get("cover_path") == null ? null : String.valueOf(row.get("cover_path"));
            if (isMissingFile(video)) {
                contentRepo.clearVideoBinding(id);
                cleared++;
            }
            if (isMissingFile(cover)) {
                contentRepo.clearCoverBinding(id);
                cleared++;
            }
        }
        return cleared;
    }

    private boolean isMissingFile(String path) {
        if (path == null || path.isBlank()) {
            return false;
        }
        // 远程外链 / data URI 不检查本地文件
        if (path.startsWith("/") || path.startsWith("http://") || path.startsWith("https://")) {
            return false;
        }
        return !Files.exists(root().resolve(path).normalize());
    }

    private List<Path> listVideoFiles() throws Exception {
        if (!Files.exists(videosDir())) {
            return new ArrayList<>();
        }
        try (Stream<Path> stream = Files.list(videosDir())) {
            return stream.filter(Files::isRegularFile)
                    .filter(p -> VIDEO_EXT.contains(ext(p)))
                    .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .collect(Collectors.toList());
        }
    }

    private String ext(Path p) {
        String name = p.getFileName().toString().toLowerCase();
        int i = name.lastIndexOf('.');
        return i < 0 ? "" : name.substring(i + 1);
    }

    private String baseName(Path p) {
        String name = p.getFileName().toString();
        int i = name.lastIndexOf('.');
        return i < 0 ? name : name.substring(0, i);
    }

    /** 用 FFmpeg 截取视频某一帧作为封面（默认第 3 秒） */
    public boolean extractCover(Path video, Path cover) {
        String ffmpeg = ffmpeg();
        if (ffmpeg == null) {
            return false;
        }
        // 先按配置的秒数截，视频太短时退回第 0 秒
        for (int ss : new int[]{coverSecond, 1, 0}) {
            try {
                String[] cmd = {ffmpeg, "-y", "-ss", String.valueOf(ss), "-i", video.toString(),
                        "-frames:v", "1", "-vf", "scale=640:-2", "-q:v", "3", cover.toString()};
                Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
                drain(p);
                if (p.waitFor(60, TimeUnit.SECONDS) && p.exitValue() == 0 && Files.exists(cover)) {
                    return true;
                }
            } catch (Exception e) {
                log.debug("截帧失败 {}: {}", video, e.getMessage());
            }
        }
        return false;
    }

    /** 读取视频时长 / 分辨率 / 大小 */
    public Map<String, Object> probe(Path video) {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("duration", 0);
        info.put("width", 0);
        info.put("height", 0);
        try {
            info.put("fileSize", Files.size(video));
        } catch (Exception e) {
            info.put("fileSize", 0L);
        }
        String ffmpeg = ffmpeg();
        if (ffmpeg == null) {
            return info;
        }
        try {
            Process p = new ProcessBuilder(ffmpeg, "-i", video.toString()).redirectErrorStream(true).start();
            String text = drain(p);
            p.waitFor(30, TimeUnit.SECONDS);

            Matcher dm = DURATION.matcher(text);
            if (dm.find()) {
                int h = Integer.parseInt(dm.group(1));
                int m = Integer.parseInt(dm.group(2));
                double s = Double.parseDouble(dm.group(3));
                info.put("duration", (int) Math.round(h * 3600 + m * 60 + s));
            }
            Matcher rm = RESOLUTION.matcher(text);
            while (rm.find()) {
                int w = Integer.parseInt(rm.group(1));
                int h = Integer.parseInt(rm.group(2));
                if (w >= 160 && h >= 90) {
                    info.put("width", w);
                    info.put("height", h);
                    break;
                }
            }
        } catch (Exception e) {
            log.debug("探测视频信息失败 {}: {}", video, e.getMessage());
        }
        return info;
    }

    private String drain(Process p) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                sb.append(line).append('\n');
            }
        }
        return sb.toString();
    }

    /**
     * 把 uploads/videos 里的视频文件绑定到「还没有视频」的稿件上。
     * 一个视频文件对应一条稿件，文件不够时只有前几条稿件有真实视频，其余保持渐变占位封面。
     */
    private int bindVideosToFiles(List<Path> files) throws Exception {
        if (files.isEmpty()) {
            return 0;
        }
        // 已经把某个文件绑定过的，不要再绑定一次（否则每次启动都会多出几条「假视频」稿件）
        java.util.Set<String> used = sync.mysqlMode() ? contentRepo.usedVideoPaths() : new java.util.HashSet<>();

        List<Video> targets = new ArrayList<>();
        for (Video v : store.videos) {
            if (!v.playable) {
                targets.add(v);
            }
        }
        int bound = 0;
        int fileIndex = 0;
        for (Path file : files) {
            String relative = "videos/" + file.getFileName();
            if (used.contains(relative)) {
                continue;   // 这个文件已经绑定过了
            }
            if (fileIndex >= targets.size()) {
                break;
            }
            Video v = targets.get(fileIndex++);
            used.add(relative);
            String base = baseName(file);
            Path cover = coversDir().resolve(base + ".jpg");
            Map<String, Object> info = probe(file);
            int duration = (int) info.getOrDefault("duration", 0);
            int width = (int) info.getOrDefault("width", 0);
            int height = (int) info.getOrDefault("height", 0);
            long size = (long) info.getOrDefault("fileSize", 0L);

            String videoPath = "videos/" + file.getFileName();
            String coverPath = Files.exists(cover) ? "covers/" + cover.getFileName() : null;
            String coverSource = coverPath != null ? "FFMPEG_FRAME" : "PLACEHOLDER";

            if (sync.mysqlMode()) {
                contentRepo.updateVideoMedia(v.id, coverPath, coverPath, videoPath,
                        duration > 0 ? duration : v.duration, width, height, size, coverSource);
                contentRepo.upsertSource(v.id, width >= 1080 ? "1080P" : width >= 720 ? "720P" : "480P",
                        videoPath, width, height, size, true);
            }
            // 同步内存对象，前端立刻拿得到
            v.videoUrl = "/api/files/video/" + file.getFileName();
            v.playable = true;
            if (coverPath != null) {
                v.coverUrl = "/api/files/cover/" + cover.getFileName();
                v.posterUrl = v.coverUrl;
                v.coverSource = "FFMPEG_FRAME";
            }
            if (duration > 0) {
                v.duration = duration;
            }
            bound++;
        }
        if (bound > 0) {
            log.info(">>> 已为 {} 条稿件绑定真实视频（封面来自 FFmpeg 截帧）", bound);
        }
        return bound;
    }

    /** 用 FFmpeg 的 lavfi 测试源生成一批演示视频（没有视频文件时用） */
    public Map<String, Object> generateSampleVideos() {
        Map<String, Object> result = new LinkedHashMap<>();
        String ffmpeg = ffmpeg();
        if (ffmpeg == null) {
            result.put("success", false);
            result.put("message", "没有找到 FFmpeg，无法生成演示视频");
            return result;
        }
        int ok = 0;
        for (String[] recipe : SAMPLE_RECIPES) {
            Path out = videosDir().resolve(recipe[0] + ".mp4");
            if (Files.exists(out)) {
                ok++;
                continue;
            }
            try {
                // 视频用 lavfi 测试源，音频用正弦波，便于验证真实播放（有声音有画面）
                ProcessBuilder pb = new ProcessBuilder(ffmpeg, "-y",
                        "-f", "lavfi", "-i", recipe[1] + ":duration=" + recipe[2],
                        "-f", "lavfi", "-i", "sine=frequency=440:sample_rate=44100:duration=" + recipe[2],
                        "-c:v", "libx264", "-preset", "veryfast", "-crf", "30", "-pix_fmt", "yuv420p",
                        "-c:a", "aac", "-b:a", "64k", "-shortest", "-movflags", "+faststart",
                        out.toString());
                pb.redirectErrorStream(true);
                Process p = pb.start();
                drain(p);
                if (p.waitFor(120, TimeUnit.SECONDS) && p.exitValue() == 0 && Files.exists(out)) {
                    ok++;
                } else {
                    log.warn("生成演示视频失败：{}", recipe[0]);
                }
            } catch (Exception e) {
                log.warn("生成演示视频异常 {}: {}", recipe[0], e.getMessage());
            }
            // 顺便生成封面
            if (Files.exists(out)) {
                extractCover(out, coversDir().resolve(recipe[0] + ".jpg"));
            }
        }
        result.put("success", ok > 0);
        result.put("count", ok);
        result.put("message", "已生成 " + ok + " 个演示视频（含音轨，可用于验证真实播放与截帧封面）");
        return result;
    }

    /** 上传投稿的视频：保存文件 + 截帧 + 返回相对路径 */
    public Map<String, Object> saveUpload(org.springframework.web.multipart.MultipartFile file, String baseName) throws Exception {
        ensureDirs();
        String original = file.getOriginalFilename() == null ? "upload.mp4" : file.getOriginalFilename();
        String ext = ext(Paths.get(original));
        if (!VIDEO_EXT.contains(ext)) {
            ext = "mp4";
        }
        String name = baseName + "." + ext;
        Path target = videosDir().resolve(name);
        file.transferTo(target.toFile());

        Map<String, Object> info = probe(target);
        Path cover = coversDir().resolve(baseName + ".jpg");
        boolean coverOk = extractCover(target, cover);

        Map<String, Object> data = new LinkedHashMap<>(info);
        data.put("videoPath", "videos/" + name);
        data.put("fileName", name);
        data.put("coverPath", coverOk ? "covers/" + cover.getFileName() : null);
        data.put("coverUrl", coverOk ? "/api/files/cover/" + cover.getFileName() : null);
        data.put("videoUrl", "/api/files/video/" + name);
        return data;
    }

    public boolean ffmpegAvailable() {
        return ffmpeg() != null;
    }
}
