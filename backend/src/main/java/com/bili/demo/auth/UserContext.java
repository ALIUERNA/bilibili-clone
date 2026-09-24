package com.bili.demo.auth;

import com.bili.demo.model.User;

/**
 * 当前请求的登录用户（ThreadLocal）。
 * 由 AuthInterceptor 在请求进入时写入，请求结束时清理。
 * 没有登录时返回 null，UserStore 会自动回退到演示账号，保证老功能可用。
 */
public final class UserContext {

    private static final ThreadLocal<Long> USER_ID = new ThreadLocal<>();
    private static final ThreadLocal<String> TOKEN = new ThreadLocal<>();
    private static final ThreadLocal<User> USER = new ThreadLocal<>();

    private UserContext() {
    }

    public static void set(Long userId, String token, User user) {
        USER_ID.set(userId);
        TOKEN.set(token);
        USER.set(user);
    }

    public static Long userId() {
        return USER_ID.get();
    }

    public static String token() {
        return TOKEN.get();
    }

    public static User user() {
        return USER.get();
    }

    public static boolean loggedIn() {
        return USER_ID.get() != null;
    }

    public static void clear() {
        USER_ID.remove();
        TOKEN.remove();
        USER.remove();
    }
}
