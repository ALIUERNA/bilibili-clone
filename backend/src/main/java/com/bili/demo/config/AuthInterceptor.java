package com.bili.demo.config;

import com.bili.demo.auth.AuthService;
import com.bili.demo.auth.UserContext;
import com.bili.demo.model.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Optional;

/**
 * 登录状态拦截器：从请求里解析 token → 查出用户 → 放进 UserContext（ThreadLocal）。
 *
 * token 支持三种带法（前端默认用 Authorization: Bearer）：
 *   1. Authorization: Bearer <token>
 *   2. token: <token>
 *   3. ?token=<token>   （视频文件 / 封面这类直接由浏览器发出的请求会用）
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    private final AuthService authService;

    public AuthInterceptor(AuthService authService) {
        this.authService = authService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String token = resolveToken(request);
        if (token != null) {
            Optional<User> user = authService.userOfToken(token);
            user.ifPresent(u -> UserContext.set(u.id, token, u));
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        UserContext.clear();
    }

    private String resolveToken(HttpServletRequest request) {
        String auth = request.getHeader("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            return auth.substring(7).trim();
        }
        String token = request.getHeader("token");
        if (token != null && !token.isBlank()) {
            return token.trim();
        }
        String param = request.getParameter("token");
        if (param != null && !param.isBlank()) {
            return param.trim();
        }
        return null;
    }
}
