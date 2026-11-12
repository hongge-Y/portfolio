package com.campus.portfolio.config;

import com.campus.portfolio.common.JwtUtil;
import com.campus.portfolio.service.AuthService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.File;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final JwtUtil jwtUtil;
    private final AuthService authService;

    @Value("${portfolio.file.upload-dir}")
    private String uploadDir;

    public WebConfig(JwtUtil jwtUtil, AuthService authService) {
        this.jwtUtil = jwtUtil;
        this.authService = authService;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new JwtInterceptor(jwtUtil, authService))
                .addPathPatterns("/v1/**")
                .excludePathPatterns(
                        "/health",
                        "/v1/auth/login/**",
                        "/v1/auth/register",
                        "/v1/auth/public-key",
                        "/v1/auth/captcha",
                        "/v1/auth/captcha/**",
                        "/v1/auth/captcha-state",
                        "/v1/auth/code/send",
                        "/v1/auth/refresh",
                        "/v1/auth/third/**",
                        "/v1/public/**",
                        "/v3/api-docs/**",
                        "/swagger-ui/**",
                        "/swagger-ui.html",
                        "/error"
                );
    }

    /** 上传文件静态映射：/static/** → 本机 upload 目录 */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String path = new File(uploadDir).getAbsolutePath();
        registry.addResourceHandler("/static/**")
                .addResourceLocations("file:" + path + File.separator);
    }
}
