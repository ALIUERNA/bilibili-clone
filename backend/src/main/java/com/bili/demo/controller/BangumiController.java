package com.bili.demo.controller;

import com.bili.demo.data.DataStore;
import com.bili.demo.model.Bangumi;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 番剧（追番）相关接口。
 */
@RestController
@RequestMapping("/api/bangumi")
public class BangumiController {

    private final DataStore store;

    public BangumiController(DataStore store) {
        this.store = store;
    }

    /**
     * 番剧列表
     * 例：/api/bangumi?area=国产&status=连载中
     */
    @GetMapping
    public List<Bangumi> list(@RequestParam(required = false) String area,
                              @RequestParam(required = false) String status) {
        List<Bangumi> result = new ArrayList<>();
        for (Bangumi b : store.bangumis) {
            if (area != null && !area.isBlank() && !area.equals(b.area)) {
                continue;
            }
            if (status != null && !status.isBlank() && !status.equals(b.status)) {
                continue;
            }
            result.add(b);
        }
        return result;
    }

    /** 追番 / 取消追番 */
    @PostMapping("/{id}/follow")
    public ResponseEntity<?> follow(@PathVariable long id) {
        for (Bangumi b : store.bangumis) {
            if (b.id == id) {
                b.followed = !b.followed;
                b.followers += b.followed ? 1 : -1;
                return ResponseEntity.ok(Map.of("followed", b.followed, "followers", b.followers));
            }
        }
        return ResponseEntity.status(404).body(Map.of("message", "番剧不存在"));
    }
}
