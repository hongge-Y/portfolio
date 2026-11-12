package com.campus.portfolio.common;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 工具：Access 2h，Refresh 7d。
 * 密钥由 application.yml 的 portfolio.jwt.secret 注入（≥32 字符）。
 */
public class JwtUtil {

    private final SecretKey key;
    private final long accessTtlSeconds;

    public JwtUtil(String secret, long accessTtlSeconds) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTtlSeconds = accessTtlSeconds;
    }

    public String createToken(UserContext ctx) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .setSubject(String.valueOf(ctx.getUserId()))
                .claim("account", ctx.getAccount())
                .claim("name", ctx.getShowName())
                .claim("roles", String.join(",", ctx.getRoles()))
                .setIssuedAt(new Date(now))
                .setExpiration(new Date(now + accessTtlSeconds * 1000))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    /** 解析并校验，失败抛 BizException(401) */
    public Claims parse(String token) {
        try {
            return Jwts.parserBuilder().setSigningKey(key).build()
                    .parseClaimsJws(token).getBody();
        } catch (Exception e) {
            throw BizException.unauthorized("登录已过期，请重新登录");
        }
    }

    public long getAccessTtlSeconds() {
        return accessTtlSeconds;
    }
}
