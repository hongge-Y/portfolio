package com.campus.portfolio.vo;

import java.util.List;

public class LoginVO {

    private Long userId;
    private String account;
    private String showName;
    private String avatar;
    private String accessToken;
    private String expiresIn;
    private boolean guideDone;
    private List<String> roles;
    /** 本次登录方式：PASSWORD / MOBILE / MAIL */
    private String loginType;

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getAccount() { return account; }
    public void setAccount(String account) { this.account = account; }
    public String getShowName() { return showName; }
    public void setShowName(String showName) { this.showName = showName; }
    public String getAvatar() { return avatar; }
    public void setAvatar(String avatar) { this.avatar = avatar; }
    public String getAccessToken() { return accessToken; }
    public void setAccessToken(String accessToken) { this.accessToken = accessToken; }
    public String getExpiresIn() { return expiresIn; }
    public void setExpiresIn(String expiresIn) { this.expiresIn = expiresIn; }
    public boolean isGuideDone() { return guideDone; }
    public void setGuideDone(boolean guideDone) { this.guideDone = guideDone; }
    public List<String> getRoles() { return roles; }
    public void setRoles(List<String> roles) { this.roles = roles; }
    public String getLoginType() { return loginType; }
    public void setLoginType(String loginType) { this.loginType = loginType; }
}
