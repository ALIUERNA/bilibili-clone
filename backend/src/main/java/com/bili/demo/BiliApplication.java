package com.bili.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 仿哔哩哔哩（Bilibili）风格网站的 Spring Boot 启动类。
 *
 * 启动后访问 http://localhost:8080 即可看到网页。
 * 所有数据都是内存中的模拟数据，不需要安装数据库。
 */
@SpringBootApplication
public class BiliApplication {

    public static void main(String[] args) {
        System.out.println("""

                ====================================================
                  哔哩哔哩仿站 - 后端启动中...
                  启动完成后请用浏览器打开: http://localhost:8080
                ====================================================
                """);
        SpringApplication.run(BiliApplication.class, args);
    }
}
