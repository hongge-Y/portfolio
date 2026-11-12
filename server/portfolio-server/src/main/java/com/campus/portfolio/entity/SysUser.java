package com.campus.portfolio.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 用户账号（表 sys_user）
 */
@TableName("sys_user")
public class SysUser {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String account;
    private String mobile;
    private String mail;
    private String passwordHash;
    private String avatar;
    private String showName;
    private String nickName;
    private LocalDate birthday;
    private Integer gender;
    private Integer coin;
    private Integer gift;
    private Integer isVip;
    private Integer pet;
    private Integer petHide;
    private Integer guideDone;
    private Integer status;
    private LocalDateTime lastLoginTime;
    private LocalDateTime createTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getAccount() { return account; }
    public void setAccount(String account) { this.account = account; }
    public String getMobile() { return mobile; }
    public void setMobile(String mobile) { this.mobile = mobile; }
    public String getMail() { return mail; }
    public void setMail(String mail) { this.mail = mail; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public String getAvatar() { return avatar; }
    public void setAvatar(String avatar) { this.avatar = avatar; }
    public String getShowName() { return showName; }
    public void setShowName(String showName) { this.showName = showName; }
    public String getNickName() { return nickName; }
    public void setNickName(String nickName) { this.nickName = nickName; }
    public LocalDate getBirthday() { return birthday; }
    public void setBirthday(LocalDate birthday) { this.birthday = birthday; }
    public Integer getGender() { return gender; }
    public void setGender(Integer gender) { this.gender = gender; }
    public Integer getCoin() { return coin; }
    public void setCoin(Integer coin) { this.coin = coin; }
    public Integer getGift() { return gift; }
    public void setGift(Integer gift) { this.gift = gift; }
    public Integer getIsVip() { return isVip; }
    public void setIsVip(Integer isVip) { this.isVip = isVip; }
    public Integer getPet() { return pet; }
    public void setPet(Integer pet) { this.pet = pet; }
    public Integer getPetHide() { return petHide; }
    public void setPetHide(Integer petHide) { this.petHide = petHide; }
    public Integer getGuideDone() { return guideDone; }
    public void setGuideDone(Integer guideDone) { this.guideDone = guideDone; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public LocalDateTime getLastLoginTime() { return lastLoginTime; }
    public void setLastLoginTime(LocalDateTime lastLoginTime) { this.lastLoginTime = lastLoginTime; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
}
