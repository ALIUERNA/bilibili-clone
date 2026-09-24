package com.bili.demo.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;

/**
 * Web 配置：
 *  1. 跨域（方便 Vite 开发服务器 5173 调试 8080）；
 *  2. 登录拦截器（/api/** 解析 token，静态文件与认证接口放行）；
 *  3. 媒体文件静态映射：
 *     /api/files/video/**  → uploads/videos/   （真实视频；Spring 的 ResourceHandler 支持 HTTP Range，可拖进度条）
 *     /api/files/cover/**  → uploads/covers/   （FFmpeg 截出来的封面帧）
 *     /api/files/media/**  → uploads/media/    （兜底占位图等素材）
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;
    private final UploadPaths uploadPaths;

    public WebConfig(AuthInterceptor authInterceptor, UploadPaths uploadPaths) {
        this.authInterceptor = authInterceptor;
        this.uploadPaths = uploadPaths;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/api/**")
                // 认证接口用来拿登录态，媒体文件由浏览器直接请求，都不需要拦截
                .excludePathPatterns("/api/files/**", "/api/auth/**");
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path base = uploadPaths.root();
        String root = base.toUri().toString();

        registry.addResourceHandler("/api/files/video/**")
                .addResourceLocations(root + "videos/")
                .setCachePeriod(3600);
        registry.addResourceHandler("/api/files/cover/**")
                .addResourceLocations(root + "covers/")
                .setCachePeriod(3600);
        registry.addResourceHandler("/api/files/media/**")
                .addResourceLocations(root + "media/")
                .setCachePeriod(86400);
        registry.addResourceHandler("/api/files/bangumi/**")
                .addResourceLocations(root + "bangumi/")
                .setCachePeriod(3600);
    }
}
