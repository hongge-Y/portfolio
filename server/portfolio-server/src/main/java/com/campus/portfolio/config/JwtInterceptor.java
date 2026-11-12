package com.campus.portfolio.config;

import com.campus.portfolio.common.BizException;
import com.campus.portfolio.common.JwtUtil;
import com.campus.portfolio.common.UserContext;
import com.campus.portfolio.service.AuthService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 解析 Authorization: Bearer <token>，装配登录上下文。
 */
public class JwtInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;
    private final AuthService authService;

    public JwtInterceptor(JwtUtil jwtUtil, AuthService authService) {
        this.jwtUtil = jwtUtil;
        this.authService = authService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            throw BizException.unauthorized("请先登录");
        }
        String token = header.substring(7).trim();
        Claims claims = jwtUtil.parse(token);
        Long userId = Long.valueOf(claims.getSubject());
        UserContext.set(authService.contextOf(userId));
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        UserContext.clear();
    }
}
