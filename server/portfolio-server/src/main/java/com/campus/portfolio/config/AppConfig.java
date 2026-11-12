package com.campus.portfolio.config;

import com.campus.portfolio.common.JwtUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppConfig {

    @Value("${portfolio.jwt.secret}")
    private String jwtSecret;

    @Value("${portfolio.jwt.access-ttl:7200}")
    private long accessTtl;

    @Bean
    public JwtUtil jwtUtil() {
        if (jwtSecret == null || jwtSecret.length() < 32) {
            throw new IllegalStateException("portfolio.jwt.secret 至少 32 个字符");
        }
        return new JwtUtil(jwtSecret, accessTtl);
    }
}
