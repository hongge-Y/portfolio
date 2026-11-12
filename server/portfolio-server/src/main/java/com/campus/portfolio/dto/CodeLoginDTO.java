package com.campus.portfolio.dto;

/**
 * 手机/邮箱验证码登录入参。
 */
public class CodeLoginDTO {

    /** 手机号或邮箱 */
    private String target;

    /** MOBILE / MAIL */
    private String type;

    /** 6 位数字验证码 */
    private String code;

    public String getTarget() { return target; }
    public void setTarget(String target) { this.target = target; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
}
