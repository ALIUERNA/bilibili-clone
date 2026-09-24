package com.bili.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * a哩a哩网站的 Spring Boot 启动类。
 *
 * 启动后访问 http://localhost:8080 即可看到网页。
 */
@SpringBootApplication
public class BiliApplication {

    public static void main(String[] args) {
        System.out.println("""

                ====================================================
                  a哩a哩 - 后端启动中...
                  启动完成后请用浏览器打开: http://localhost:8080
                ====================================================
                """);
        SpringApplication.run(BiliApplication.class, args);
    }
}
