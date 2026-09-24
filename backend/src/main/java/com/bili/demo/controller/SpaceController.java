package com.bili.demo.controller;

import com.bili.demo.data.DataStore;
import com.bili.demo.db.UserRepository;
import com.bili.demo.model.Dynamic;
import com.bili.demo.model.PageResult;
import com.bili.demo.model.Up;
import com.bili.demo.model.User;
import com.bili.demo.model.Video;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 个人空间 / 动态 / 搜索 相关接口。
 */
@RestController
@RequestMapping("/api")
public class SpaceController {

    private final DataStore store;
    private final UserRepository userRepo;

    public SpaceController(DataStore store, UserRepository userRepo) {
        this.store = store;
        this.userRepo = userRepo;
    }

    /** 个人空间（UP主主页；内置 UP 主之外，也支持真实注册用户） */
    @GetMapping("/spaces/{upId}")
    public ResponseEntity<?> space(@PathVariable long upId) {
        Up up = store.findUp(upId);
        if (up == null) {
            up = fromDatabaseUser(upId);
        }
        if (up == null) {
            return ResponseEntity.status(404).body(Map.of("message", "用户不存在"));
        }
        List<Video> own = new ArrayList<>();
        for (Video v : store.videos) {
            if (v.upId == upId) {
                own.add(v);
            }
        }
        own.sort((a, b) -> Long.compare(b.views, a.views));

        List<Dynamic> dyn = new ArrayList<>();
        for (Dynamic d : store.dynamics) {
            if (d.upId == upId) {
                dyn.add(d);
            }
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("up", up);
        data.put("videos", own);
        data.put("dynamics", dyn);
        data.put("totalViews", own.stream().mapToLong(v -> v.views).sum());
        return ResponseEntity.ok(data);
    }

    /** 真实注册用户（users 表）转成空间页需要的 UP 结构；内存模式下或查不到返回 null */
    private Up fromDatabaseUser(long id) {
        User user = userRepo.findById(id).orElse(null);
        if (user == null) {
            return null;
        }
        Up up = new Up(user.id, user.name, user.face, user.sign);
        up.fans = user.followers;
        up.likes = user.likes;
        up.level = "Lv" + user.level;
        up.medal = user.medal;
        up.videoCount = (int) store.videos.stream().filter(v -> v.upId == id).count();
        return up;
    }

    /** 全站动态流 */
    @GetMapping("/dynamics")
    public PageResult<Dynamic> dynamics(@RequestParam(defaultValue = "1") int page,
                                        @RequestParam(defaultValue = "10") int size) {
        List<Dynamic> all = new ArrayList<>(store.dynamics);
        all.sort((a, b) -> Long.compare(b.id, a.id));
        return PageResult.of(all, page, size);
    }

    /** 搜索 */
    @GetMapping("/search")
    public Map<String, Object> search(@RequestParam(defaultValue = "") String keyword,
                                      @RequestParam(defaultValue = "1") int page,
                                      @RequestParam(defaultValue = "24") int size) {
        List<Video> hits = store.search(keyword);
        PageResult<Video> paged = PageResult.of(hits, page, size);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("keyword", keyword);
        data.put("items", paged.items);
        data.put("total", paged.total);
        data.put("page", paged.page);
        data.put("hasMore", paged.hasMore);
        // 顺带返回命中的 UP 主，模仿 B 站的「相关用户」
        Set<Up> users = new LinkedHashSet<>();
        for (Video v : hits) {
            Up up = store.findUp(v.upId);
            if (up != null && (keyword.isBlank() || up.name.contains(keyword) || hits.size() <= 6)) {
                users.add(up);
            }
        }
        data.put("users", new ArrayList<>(users).subList(0, Math.min(3, users.size())));
        return data;
    }

    /** 搜索联想词 */
    @GetMapping("/search/suggest")
    public List<String> suggest(@RequestParam(defaultValue = "") String keyword) {
        List<String> result = new ArrayList<>();
        if (keyword.isBlank()) {
            return store.hotSearch.subList(0, 6);
        }
        String kw = keyword.toLowerCase();
        for (Video v : store.videos) {
            if (v.title.toLowerCase().contains(kw)) {
                result.add(v.title);
            }
            if (result.size() >= 8) {
                break;
            }
        }
        if (result.isEmpty()) {
            result.add(keyword + " 相关视频");
            result.add(keyword + " 教程");
        }
        return result;
    }
}
