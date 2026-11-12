package com.campus.portfolio.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 密码登录入参。
 * <p>
 * 密码有两种传法，二者取一：
 * <ul>
 *   <li>明文 {@code password}：内网联调 / 脚本冒烟用；</li>
 *   <li>密文 {@code sign}：前端用 /v1/auth/public-key 的公钥做 RSA(PKCS#1 v1.5)，
 *       内容是 JSON.stringify({password, timestamp})，后端解开并校验 ±5 分钟时间窗。
 *       登录页默认走这条路，网络里看不到明文口令。</li>
 * </ul>
 */
public class LoginDTO {

    @NotBlank(message = "账号不能为空")
    private String account;

    /** 明文密码（与 sign 二选一，由 AuthService 统一校验） */
    private String password;

    /** RSA 加密后的 {password, timestamp}，base64 */
    private String sign;

    /** 提交时拿到的公钥（当前实现只用于日志，便于排查密钥版本问题） */
    private String key;

    /** 客户端时间戳，须与 sign 内的 timestamp 一致 */
    private Long time;

    /** 可选：PASSWORD / MOBILE / MAIL，不传则服务端按 account 形态自动判断 */
    private String loginType;

    /** 图形验证码 id（GET /v1/auth/captcha 获取），连续 5 次失败后必传 */
    private String captchaId;

    /** 图形验证码内容 */
    private String captchaCode;

    public String getAccount() { return account; }
    public void setAccount(String account) { this.account = account; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getSign() { return sign; }
    public void setSign(String sign) { this.sign = sign; }
    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }
    public Long getTime() { return time; }
    public void setTime(Long time) { this.time = time; }
    public String getLoginType() { return loginType; }
    public void setLoginType(String loginType) { this.loginType = loginType; }
    public String getCaptchaId() { return captchaId; }
    public void setCaptchaId(String captchaId) { this.captchaId = captchaId; }
    public String getCaptchaCode() { return captchaCode; }
    public void setCaptchaCode(String captchaCode) { this.captchaCode = captchaCode; }
}
