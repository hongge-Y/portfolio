package com.campus.portfolio;

import com.campus.portfolio.data.DataInitializer;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableConfigurationProperties
@EnableScheduling
public class PortfolioApplication {

    public static void main(String[] args) {
        SpringApplication.run(PortfolioApplication.class, args);
        System.out.println("""

                ==========================================================
                  Portfolio 后端已启动
                  接口地址 : http://localhost:8106/api
                  接口文档 : http://localhost:8106/api/swagger-ui.html
                  健康检查 : http://localhost:8106/api/health
                ==========================================================
                """);
    }
}
