package com.campus.portfolio.dto;

/**
 * 发送手机/邮箱验证码入参。图形验证码为必填（防止被当短信轰炸接口刷）。
 */
public class CodeSendDTO {

    /** 手机号或邮箱 */
    private String target;

    /** MOBILE / MAIL */
    private String type;

    private String captchaId;

    private String captchaCode;

    public String getTarget() { return target; }
    public void setTarget(String target) { this.target = target; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getCaptchaId() { return captchaId; }
    public void setCaptchaId(String captchaId) { this.captchaId = captchaId; }
    public String getCaptchaCode() { return captchaCode; }
    public void setCaptchaCode(String captchaCode) { this.captchaCode = captchaCode; }
}
