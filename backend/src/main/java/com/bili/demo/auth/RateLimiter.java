package com.bili.demo.auth;

import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 极简频率限制器（滑动窗口，只放在内存里）。
 *
 * 说明：验证码 / 登录这类接口必须限流，否则会被刷爆。
 * 这里是单机内存实现，够用且不引入 Redis；如果将来要多机部署，
 * 把 allow() 换成 Redis 的 INCR + EXPIRE 即可，调用方不用改。
 */
@Component
public class RateLimiter {

    private final Map<String, Deque<Long>> hits = new ConcurrentHashMap<>();

    /**
     * @param key        限流维度，例如 "email:LOGIN:a@b.com" 或 "ip:127.0.0.1"
     * @param max        窗口内最大次数
     * @param windowSecs 窗口长度（秒）
     * @return true = 允许；false = 触发限流
     */
    public boolean allow(String key, int max, int windowSecs) {
        long now = System.currentTimeMillis();
        long windowStart = now - windowSecs * 1000L;
        Deque<Long> deque = hits.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (deque) {
            while (!deque.isEmpty() && deque.peekFirst() < windowStart) {
                deque.pollFirst();
            }
            if (deque.size() >= max) {
                return false;
            }
            deque.addLast(now);
            if (hits.size() > 5000) {
                // 防止内存无限增长
                hits.entrySet().removeIf(e -> e.getValue().isEmpty());
            }
            return true;
        }
    }

    /** 还需要等多少秒才能再次请求（用于给前端友好提示） */
    public long retryAfterSeconds(String key, int max, int windowSecs) {
        Deque<Long> deque = hits.get(key);
        if (deque == null) {
            return 0;
        }
        synchronized (deque) {
            while (!deque.isEmpty() && deque.peekFirst() < System.currentTimeMillis() - windowSecs * 1000L) {
                deque.pollFirst();
            }
            if (deque.size() < max) {
                return 0;
            }
            Long oldest = deque.peekFirst();
            long pass = (oldest == null ? 0 : (oldest + windowSecs * 1000L - System.currentTimeMillis()) / 1000);
            return Math.max(1, pass);
        }
    }

    public void reset(String key) {
        hits.remove(key);
    }
}
