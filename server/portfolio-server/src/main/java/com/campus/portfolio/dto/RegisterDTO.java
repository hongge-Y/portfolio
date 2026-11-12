package com.campus.portfolio.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 学生自助注册入参。
 * <p>
 * 只开放学生角色：注册即绑定一个班级（org_unit.type=4），
 * 后续请假、奖学金、课表都靠这条班级归属走 orgPath 数据范围。
 */
public class RegisterDTO {

    /** 学号：8-12 位数字，登录账号 */
    @NotBlank(message = "学号不能为空")
    private String account;

    /** 姓名：2-20 字，会写进 org_employee.name 用于审批单展示 */
    @NotBlank(message = "姓名不能为空")
    private String showName;

    /** 登录密码：6-20 位，BCrypt 存储 */
    @NotBlank(message = "密码不能为空")
    private String password;

    /** 手机号：选填，填了就必须唯一（可用于短信验证码登录） */
    private String mobile;

    /** 邮箱：选填，唯一 */
    private String mail;

    /** 所在班级 org_unit.id（type=4） */
    private Long classId;

    public String getAccount() { return account; }
    public void setAccount(String account) { this.account = account; }

    public String getShowName() { return showName; }
    public void setShowName(String showName) { this.showName = showName; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getMobile() { return mobile; }
    public void setMobile(String mobile) { this.mobile = mobile; }

    public String getMail() { return mail; }
    public void setMail(String mail) { this.mail = mail; }

    public Long getClassId() { return classId; }
    public void setClassId(Long classId) { this.classId = classId; }
}
