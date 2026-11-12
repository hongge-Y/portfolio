package com.campus.portfolio.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.campus.portfolio.common.BizException;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.util.Base64;
import java.util.Map;

/**
 * 登录报文 RSA 加密（F-01 / 01 篇非功能需求「RSA 加密登录体」）。
 * <p>
 * 启动时生成一对 2048 位密钥，前端用 /v1/auth/public-key 取公钥（jsencrypt 默认 PKCS#1 v1.5），
 * 提交 sign = RSA(JSON.stringify({password, timestamp}))，后端用私钥解开并校验时间窗口（±5 分钟）。
 * 密钥只存在内存里：重启后旧 sign 自然失效，符合一次性口令的语义，也省掉密钥文件管理。
 */
@Service
public class RsaService {

    private static final long WINDOW_MS = 5 * 60 * 1000L;
    private static final ObjectMapper JSON = new ObjectMapper();

    private final KeyPair keyPair;
    private final String publicKeyPem;

    public RsaService() {
        try {
            KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
            gen.initialize(2048);
            this.keyPair = gen.generateKeyPair();
            this.publicKeyPem = toPem(keyPair.getPublic());
        } catch (Exception e) {
            throw new IllegalStateException("初始化登录 RSA 密钥失败", e);
        }
    }

    /** 给前端的 PEM 公钥（含 BEGIN/END 头，jsencrypt.setPublicKey 直接可用） */
    public String publicKeyPem() {
        return publicKeyPem;
    }

    /**
     * 解开 sign，返回 { password, timestamp }
     */
    public Map<String, Object> decryptSign(String sign) {
        try {
            Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
            cipher.init(Cipher.DECRYPT_MODE, keyPair.getPrivate());
            byte[] plain = cipher.doFinal(Base64.getDecoder().decode(sign));
            String json = new String(plain, StandardCharsets.UTF_8);
            Map<String, Object> m = JSON.readValue(json, Map.class);

            Object ts = m.get("timestamp");
            long time = ts instanceof Number n ? n.longValue() : Long.parseLong(String.valueOf(ts));
            if (Math.abs(System.currentTimeMillis() - time) > WINDOW_MS) {
                throw new BizException(400, "SIGN_EXPIRED", "登录请求已过期，请刷新页面后重试");
            }
            Object pwd = m.get("password");
            if (pwd == null || String.valueOf(pwd).isBlank()) {
                throw new BizException(400, "SIGN_INVALID", "登录报文缺少密码");
            }
            return m;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(400, "SIGN_INVALID", "登录报文解析失败，请刷新页面后重试");
        }
    }

    private String toPem(PublicKey key) {
        String base64 = Base64.getEncoder().encodeToString(key.getEncoded());
        StringBuilder sb = new StringBuilder();
        sb.append("-----BEGIN PUBLIC KEY-----\n");
        for (int i = 0; i < base64.length(); i += 64) {
            sb.append(base64, i, Math.min(i + 64, base64.length())).append('\n');
        }
        sb.append("-----END PUBLIC KEY-----");
        return sb.toString();
    }
}
