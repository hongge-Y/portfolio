package com.campus.portfolio.service;

import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 图形验证码 + 登录失败计数（本机内存实现，单机部署够用；多实例需换 Redis，见 02 篇）。
 * <p>
 * F-01：连续 5 次密码错误后，后续登录必须携带验证码。
 * 验证码 5 分钟过期、一次性消费（校验失败即作废，必须重新获取）。
 */
@Service
public class CaptchaService {

    /** 去掉 0/O/1/I/Z 等易混字符 */
    private static final String CHARS = "ABCDEFGHJKLMNPQRSTUVWXY23456789";
    private static final long TTL_MS = 5 * 60 * 1000L;
    private static final int MAX_FAIL = 5;
    private static final SecureRandom RND = new SecureRandom();

    private static final class Item {
        final String code;
        final long expireAt;

        Item(String code, long expireAt) {
            this.code = code;
            this.expireAt = expireAt;
        }
    }

    private static final class Attempt {
        int fails = 0;
        long lastFail = 0L;
    }

    private final Map<String, Item> store = new ConcurrentHashMap<>();
    private final Map<String, Attempt> attempts = new ConcurrentHashMap<>();

    /** 生成验证码：返回 captchaId + svg（base64，可直接塞进 img src） */
    public Map<String, Object> create() {
        purgeExpired();
        StringBuilder code = new StringBuilder(4);
        for (int i = 0; i < 4; i++) {
            code.append(CHARS.charAt(RND.nextInt(CHARS.length())));
        }
        String id = randomHex(16);
        store.put(id, new Item(code.toString(), System.currentTimeMillis() + TTL_MS));

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("captchaId", id);
        m.put("svg", Base64.getEncoder().encodeToString(svg(code.toString()).getBytes(StandardCharsets.UTF_8)));
        m.put("expiresIn", TTL_MS / 1000);
        m.put("length", 4);
        return m;
    }

    /** 校验并消费；无论成功失败该验证码都作废 */
    public boolean verify(String captchaId, String input) {
        if (captchaId == null || input == null) return false;
        Item item = store.remove(captchaId.trim());
        if (item == null || item.expireAt < System.currentTimeMillis()) return false;
        return item.code.equalsIgnoreCase(input.trim());
    }

    /** 该账号是否已被要求输入验证码 */
    public boolean needCaptcha(String account) {
        if (account == null) return false;
        Attempt a = attempts.get(account.trim().toLowerCase());
        return a != null && a.fails >= MAX_FAIL;
    }

    public int failCount(String account) {
        if (account == null) return 0;
        Attempt a = attempts.get(account.trim().toLowerCase());
        return a == null ? 0 : a.fails;
    }

    public int remainBeforeCaptcha(String account) {
        return Math.max(0, MAX_FAIL - failCount(account));
    }

    public void markFail(String account) {
        if (account == null) return;
        String key = account.trim().toLowerCase();
        Attempt a = attempts.computeIfAbsent(key, k -> new Attempt());
        synchronized (a) {
            a.fails++;
            a.lastFail = System.currentTimeMillis();
        }
    }

    public void clearFail(String account) {
        if (account == null) return;
        attempts.remove(account.trim().toLowerCase());
    }

    // ---------------- 内部 ----------------

    private void purgeExpired() {
        long now = System.currentTimeMillis();
        store.entrySet().removeIf(e -> e.getValue().expireAt < now);
        // 失败计数超过 30 分钟没有新失败就清零，避免长期锁验证码
        attempts.entrySet().removeIf(e -> now - e.getValue().lastFail > 30 * 60 * 1000L);
    }

    private String randomHex(int len) {
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            sb.append(Integer.toHexString(RND.nextInt(16)));
        }
        return sb.toString();
    }

    /** 纯字符串拼 SVG：歪斜字符 + 干扰线 + 干扰点，不引任何三方库 */
    private String svg(String code) {
        StringBuilder sb = new StringBuilder(1024);
        sb.append("<svg xmlns='http://www.w3.org/2000/svg' width='110' height='40' viewBox='0 0 110 40'>");
        sb.append("<rect width='110' height='40' rx='6' fill='#f1f5f9'/>");
        for (int i = 0; i < 3; i++) {
            sb.append("<path d='M").append(RND.nextInt(10)).append(' ').append(RND.nextInt(40))
                    .append(" Q").append(20 + RND.nextInt(50)).append(' ').append(RND.nextInt(40))
                    .append(' ').append(60 + RND.nextInt(50)).append(' ').append(RND.nextInt(40))
                    .append("' stroke='").append(color()).append("' stroke-width='1' fill='none' opacity='0.6'/>");
        }
        for (int i = 0; i < 18; i++) {
            sb.append("<circle cx='").append(RND.nextInt(110)).append("' cy='").append(RND.nextInt(40))
                    .append("' r='1' fill='").append(color()).append("' opacity='0.5'/>");
        }
        for (int i = 0; i < code.length(); i++) {
            int x = 14 + i * 24;
            int y = 27 + RND.nextInt(4) - 2;
            int rotate = RND.nextInt(41) - 20;
            sb.append("<text x='").append(x).append("' y='").append(y)
                    .append("' font-family='Consolas,monospace' font-size='24' font-weight='700'")
                    .append(" fill='").append(color()).append("' transform='rotate(")
                    .append(rotate).append(' ').append(x).append(' ').append(y).append(")'>")
                    .append(code.charAt(i)).append("</text>");
        }
        sb.append("</svg>");
        return sb.toString();
    }

    private String color() {
        String[] palette = {"#0f172a", "#1d4ed8", "#0e7490", "#7c3aed", "#b45309", "#be123c"};
        return palette[RND.nextInt(palette.length)];
    }
}
