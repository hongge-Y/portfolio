package com.campus.portfolio.service;

import com.campus.portfolio.common.BizException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 手机/邮箱验证码（本机内存实现）。
 * <p>
 * 本机没有短信、邮件网关，所以走"演示模式"：验证码只写日志并随响应回传 demoCode，
 * 登录页据此在 toast 里显示"演示码 xxxxxx"，答辩/课堂演示不需要真发短信。
 * 上线时把 portfolio.auth.code-demo 改成 false，接阿里云短信/邮件推送即可（本类只改 send 一处）。
 * <p>
 * 规则：6 位数字、5 分钟有效、一次性消费、同一目标 60 秒内不能重发。
 */
@Service
public class VerifyCodeService {

    private static final Logger log = LoggerFactory.getLogger(VerifyCodeService.class);
    private static final SecureRandom RND = new SecureRandom();
    private static final long TTL_MS = 5 * 60 * 1000L;
    private static final long RESEND_MS = 60 * 1000L;

    private static final class Item {
        final String code;
        final long expireAt;
        final long sentAt;

        Item(String code, long expireAt, long sentAt) {
            this.code = code;
            this.expireAt = expireAt;
            this.sentAt = sentAt;
        }
    }

    private final Map<String, Item> store = new ConcurrentHashMap<>();

    private final boolean demoMode;

    public VerifyCodeService(@org.springframework.beans.factory.annotation.Value("${portfolio.auth.code-demo:true}") boolean demoMode) {
        this.demoMode = demoMode;
    }

    /**
     * 发送验证码。
     *
     * @param target 手机号或邮箱
     * @param type   MOBILE / MAIL
     */
    public Map<String, Object> send(String target, String type) {
        String t = normalize(target);
        String kind = normalizeType(type);
        if (t.isEmpty()) {
            throw BizException.badRequest(kind.equals("MAIL") ? "请输入邮箱地址" : "请输入手机号");
        }
        if (kind.equals("MOBILE") && !t.matches("^1\\d{10}$")) {
            throw BizException.badRequest("手机号格式不正确");
        }
        if (kind.equals("MAIL") && !t.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            throw BizException.badRequest("邮箱格式不正确");
        }

        purgeExpired();
        String key = kind + ":" + t.toLowerCase();
        long now = System.currentTimeMillis();
        Item old = store.get(key);
        if (old != null && now - old.sentAt < RESEND_MS) {
            long wait = (RESEND_MS - (now - old.sentAt)) / 1000 + 1;
            throw new BizException(429, "CODE_TOO_FREQ", "验证码已发送，请 " + wait + " 秒后再试");
        }

        String code = String.format("%06d", RND.nextInt(1_000_000));
        store.put(key, new Item(code, now + TTL_MS, now));

        if (demoMode) {
            log.info("【验证码·演示模式】{} → {}，{} 分钟内有效（未真发短信/邮件）",
                    kind.equals("MAIL") ? "邮箱" : "手机", t, TTL_MS / 60000);
        } else {
            // 接入真实网关的位置：短信 → 阿里云 dysmsapi；邮件 → 阿里云邮件推送 / SMTP
            log.warn("未接入短信/邮件网关，验证码未真正发送：{} {}", kind, t);
        }

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("target", mask(t));
        m.put("type", kind);
        m.put("expiresIn", TTL_MS / 1000);
        m.put("resendAfter", RESEND_MS / 1000);
        m.put("demo", demoMode);
        if (demoMode) {
            m.put("demoCode", code); // 演示模式才回传，生产必须删掉这一行
        }
        return m;
    }

    /** 校验并消费；一次性，错一次也作废 */
    public boolean verify(String target, String type, String code) {
        String key = normalizeType(type) + ":" + normalize(target).toLowerCase();
        Item item = store.get(key);
        if (item == null) return false;
        if (item.expireAt < System.currentTimeMillis()) {
            store.remove(key);
            return false;
        }
        if (code == null || !item.code.equals(code.trim())) {
            return false;
        }
        store.remove(key);
        return true;
    }

    private void purgeExpired() {
        long now = System.currentTimeMillis();
        store.entrySet().removeIf(e -> e.getValue().expireAt < now);
    }

    private String normalize(String s) {
        return s == null ? "" : s.trim();
    }

    private String normalizeType(String type) {
        String t = type == null ? "" : type.trim().toUpperCase();
        return t.equals("MAIL") || t.equals("EMAIL") ? "MAIL" : "MOBILE";
    }

    /** 日志与响应里都不出现完整手机号/邮箱 */
    private String mask(String s) {
        if (s.contains("@")) {
            int at = s.indexOf('@');
            String name = s.substring(0, at);
            String domain = s.substring(at);
            if (name.length() <= 2) return name.charAt(0) + "***" + domain;
            return name.substring(0, 2) + "***" + domain;
        }
        return s.length() >= 7 ? s.substring(0, 3) + "****" + s.substring(s.length() - 4) : s;
    }
}
