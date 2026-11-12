package com.campus.portfolio.common;

import java.util.ArrayList;
import java.util.List;

/**
 * 登录用户上下文：一次登录组装，随请求在线程内存活。
 */
public class UserContext {

    private Long userId;
    private Long employeeId;
    private String employeeNo;
    private String account;
    private String showName;
    private List<String> roles = new ArrayList<>();
    private Long classOrgId;
    private Long collegeOrgId;
    private Long rootOrgId;
    private String orgPath;
    private List<String> permissions = new ArrayList<>();

    /** 线程级持有，由 JwtInterceptor 设置/清理 */
    private static final ThreadLocal<UserContext> HOLDER = new ThreadLocal<>();

    public static void set(UserContext ctx) { HOLDER.set(ctx); }
    public static UserContext get() { return HOLDER.get(); }
    public static void clear() { HOLDER.remove(); }

    /** 取当前用户，未登录抛 401 */
    public static UserContext require() {
        UserContext ctx = HOLDER.get();
        if (ctx == null) {
            throw BizException.unauthorized("请先登录");
        }
        return ctx;
    }

    public boolean hasRole(String role) {
        return roles != null && roles.contains(role);
    }

    public boolean hasPermission(String perm) {
        return permissions != null && permissions.contains(perm);
    }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getEmployeeId() { return employeeId; }
    public void setEmployeeId(Long employeeId) { this.employeeId = employeeId; }
    public String getEmployeeNo() { return employeeNo; }
    public void setEmployeeNo(String employeeNo) { this.employeeNo = employeeNo; }
    public String getAccount() { return account; }
    public void setAccount(String account) { this.account = account; }
    public String getShowName() { return showName; }
    public void setShowName(String showName) { this.showName = showName; }
    public List<String> getRoles() { return roles; }
    public void setRoles(List<String> roles) { this.roles = roles; }
    public Long getClassOrgId() { return classOrgId; }
    public void setClassOrgId(Long classOrgId) { this.classOrgId = classOrgId; }
    public Long getCollegeOrgId() { return collegeOrgId; }
    public void setCollegeOrgId(Long collegeOrgId) { this.collegeOrgId = collegeOrgId; }
    public Long getRootOrgId() { return rootOrgId; }
    public void setRootOrgId(Long rootOrgId) { this.rootOrgId = rootOrgId; }
    public String getOrgPath() { return orgPath; }
    public void setOrgPath(String orgPath) { this.orgPath = orgPath; }
    public List<String> getPermissions() { return permissions; }
    public void setPermissions(List<String> permissions) { this.permissions = permissions; }
}
