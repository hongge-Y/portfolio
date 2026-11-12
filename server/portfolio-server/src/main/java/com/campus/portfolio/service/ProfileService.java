package com.campus.portfolio.service;

import com.campus.portfolio.common.BizException;
import com.campus.portfolio.common.UserContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 个人中心：F-03 资料维护 / F-04 账号安全 / F-05 引导收尾 / F-26 组织与角色·今日任务·我的风险 / F-28 登录记录。
 * 所有方法都以当前登录用户为边界，不接受前端传入 userId（防越权）。
 */
@Service
public class ProfileService {

    private static final Pattern MOBILE = Pattern.compile("^1\\d{10}$");
    private static final Pattern MAIL = Pattern.compile("^[\\w.+-]+@[\\w-]+(\\.[\\w-]+)+$");
    private static final Pattern PWD = Pattern.compile("^(?=.*[A-Za-z])(?=.*\\d).{8,32}$");

    private final JdbcTemplate jdbc;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    private final CaptchaService captcha;

    public ProfileService(JdbcTemplate jdbc, CaptchaService captcha) {
        this.jdbc = jdbc;
        this.captcha = captcha;
    }

    // ---------------- F-03 资料 ----------------

    public Map<String, Object> detail(UserContext ctx) {
        Map<String, Object> u = jdbc.queryForMap(
                "SELECT id, account, mobile, mail, show_name AS showName, nick_name AS nickName, "
                        + "birthday, gender, avatar, coin, gift, is_vip AS isVip, pet, pet_hide AS petHide, "
                        + "guide_done AS guideDone, status, last_login_time AS lastLoginTime, "
                        + "last_login_ip AS lastLoginIp, create_time AS createTime "
                        + "FROM sys_user WHERE id = ?", ctx.getUserId());

        Map<String, Object> m = new LinkedHashMap<>(u);
        m.put("mobile", maskMobile((String) u.get("mobile")));
        m.put("mail", maskMail((String) u.get("mail")));
        m.put("mobileBound", u.get("mobile") != null);
        m.put("mailBound", u.get("mail") != null);
        m.put("roles", ctx.getRoles());
        m.put("permissions", ctx.getPermissions());
        Integer loginCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM sys_login_log WHERE user_id = ? AND success = 1", Integer.class, ctx.getUserId());
        m.put("loginCount", loginCount == null ? 0 : loginCount);
        return m;
    }

    /** 只允许改这几个字段；传 null 表示不改 */
    public Map<String, Object> update(UserContext ctx, Map<String, Object> body) {
        Map<String, Object> set = new LinkedHashMap<>();

        String showName = str(body.get("showName"));
        if (showName != null) {
            if (showName.isBlank() || showName.length() > 32) {
                throw BizException.badRequest("昵称长度需在 1~32 个字符之间");
            }
            set.put("show_name", showName.trim());
        }
        if (body.containsKey("nickName")) {
            String nick = str(body.get("nickName"));
            if (nick != null && nick.length() > 32) throw BizException.badRequest("别名最多 32 个字符");
            set.put("nick_name", nick == null ? null : nick.trim());
        }
        if (body.containsKey("birthday")) {
            String b = str(body.get("birthday"));
            if (b == null || b.isBlank()) {
                set.put("birthday", null);
            } else {
                try {
                    LocalDate d = LocalDate.parse(b.trim());
                    if (d.isAfter(LocalDate.now())) throw BizException.badRequest("生日不能是未来日期");
                    set.put("birthday", d);
                } catch (java.time.format.DateTimeParseException e) {
                    throw BizException.badRequest("生日格式应为 yyyy-MM-dd");
                }
            }
        }
        if (body.containsKey("gender")) {
            Integer g = num(body.get("gender"));
            if (g == null || g < 0 || g > 2) throw BizException.badRequest("性别只能是 0未知/1男/2女");
            set.put("gender", g);
        }
        if (body.containsKey("pet")) {
            Integer pet = num(body.get("pet"));
            if (pet == null || pet < 0 || pet > 5) throw BizException.badRequest("小宠编号只能是 0~5");
            set.put("pet", pet);
        }
        if (body.containsKey("petHide")) {
            Integer hide = num(body.get("petHide"));
            set.put("pet_hide", hide != null && hide == 1 ? 1 : 0);
        }
        if (body.containsKey("avatar")) {
            String avatar = str(body.get("avatar"));
            if (avatar != null && avatar.length() > 255) throw BizException.badRequest("头像地址过长");
            if (avatar != null && !avatar.isBlank() && !avatar.startsWith("/avatar/") && !avatar.startsWith("/static/")) {
                throw BizException.badRequest("头像只能是预设头像或已上传文件");
            }
            set.put("avatar", avatar);
        }

        if (set.isEmpty()) throw BizException.badRequest("没有需要更新的字段");

        StringBuilder sql = new StringBuilder("UPDATE sys_user SET ");
        List<Object> args = new ArrayList<>();
        set.forEach((k, v) -> {
            sql.append(k).append(" = ?, ");
            args.add(v);
        });
        sql.setLength(sql.length() - 2);
        sql.append(" WHERE id = ?");
        args.add(ctx.getUserId());
        jdbc.update(sql.toString(), args.toArray());
        return detail(ctx);
    }

    // ---------------- F-04 账号安全 ----------------

    public void changePassword(UserContext ctx, String oldPassword, String newPassword) {
        if (oldPassword == null || newPassword == null) throw BizException.badRequest("请填写原密码与新密码");
        if (!PWD.matcher(newPassword).matches()) {
            throw BizException.badRequest("新密码需 8~32 位，且同时包含字母和数字");
        }
        if (oldPassword.equals(newPassword)) throw BizException.badRequest("新密码不能与原密码相同");

        String hash = jdbc.queryForObject("SELECT password_hash FROM sys_user WHERE id = ?", String.class, ctx.getUserId());
        if (hash == null || !encoder.matches(oldPassword, hash)) {
            throw new BizException(400, "PWD_WRONG", "原密码不正确");
        }
        jdbc.update("UPDATE sys_user SET password_hash = ? WHERE id = ?",
                encoder.encode(newPassword), ctx.getUserId());
        // 改密后让旧失败计数清零，避免用户被验证码卡住
        captcha.clearFail(ctx.getAccount());
    }

    public Map<String, Object> bind(UserContext ctx, String type, String value, String password) {
        String t = type == null ? "" : type.trim().toLowerCase();
        if (!t.equals("mobile") && !t.equals("mail")) throw BizException.badRequest("绑定类型只能是 mobile 或 mail");
        String v = value == null ? "" : value.trim();

        if (t.equals("mobile") && !MOBILE.matcher(v).matches()) throw BizException.badRequest("手机号格式不正确");
        if (t.equals("mail") && !MAIL.matcher(v).matches()) throw BizException.badRequest("邮箱格式不正确");

        String hash = jdbc.queryForObject("SELECT password_hash FROM sys_user WHERE id = ?", String.class, ctx.getUserId());
        if (hash == null || password == null || !encoder.matches(password, hash)) {
            throw new BizException(400, "PWD_WRONG", "请输入正确的登录密码");
        }

        String column = t.equals("mobile") ? "mobile" : "mail";
        Integer dup = jdbc.queryForObject(
                "SELECT COUNT(*) FROM sys_user WHERE " + column + " = ? AND id <> ? AND deleted = 0",
                Integer.class, v, ctx.getUserId());
        if (dup != null && dup > 0) {
            throw new BizException(400, "BIND_CONFLICT", t.equals("mobile") ? "该手机号已被其他账号绑定" : "该邮箱已被其他账号绑定");
        }
        jdbc.update("UPDATE sys_user SET " + column + " = ? WHERE id = ?", v, ctx.getUserId());

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", t);
        m.put("value", t.equals("mobile") ? maskMobile(v) : maskMail(v));
        m.put("ok", true);
        return m;
    }

    // ---------------- F-05 引导收尾 ----------------

    public Map<String, Object> finishGuide(UserContext ctx, Integer pet) {
        if (pet == null || pet < 1 || pet > 5) throw BizException.badRequest("请先选择一个 1~5 的小宠");
        Integer done = jdbc.queryForObject("SELECT guide_done FROM sys_user WHERE id = ?", Integer.class, ctx.getUserId());
        boolean first = done == null || done == 0;

        if (first) {
            // 首次完成引导：送 100 光粒 + 1 个礼盒
            jdbc.update("UPDATE sys_user SET pet = ?, guide_done = 1, coin = coin + 100, gift = gift + 1 WHERE id = ?",
                    pet, ctx.getUserId());
        } else {
            jdbc.update("UPDATE sys_user SET pet = ? WHERE id = ?", pet, ctx.getUserId());
        }

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("guideDone", true);
        m.put("firstTime", first);
        m.put("awardCoin", first ? 100 : 0);
        m.put("awardGift", first ? 1 : 0);
        m.put("pet", pet);
        return m;
    }

    // ---------------- F-26 组织与角色 ----------------

    public Map<String, Object> org(UserContext ctx) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("userId", ctx.getUserId());
        m.put("account", ctx.getAccount());

        List<Map<String, Object>> emps = jdbc.queryForList(
                "SELECT e.id AS employeeId, e.name, e.employee_no AS employeeNo, e.sex, e.hire_date AS hireDate, "
                        + "e.enable_relative AS enableRelative, root.name AS rootOrgName "
                        + "FROM org_employee e LEFT JOIN org_unit root ON root.id = e.root_org_id "
                        + "WHERE e.user_id = ? AND e.deleted = 0", ctx.getUserId());
        m.put("employee", emps.isEmpty() ? null : emps.get(0));

        List<Map<String, Object>> units = jdbc.queryForList(
                "SELECT ou.id AS orgId, ou.name AS orgName, ou.type AS orgType, ou.path, "
                        + "p.name AS positionName, t.name AS employeeType, eu.is_primary AS isPrimary "
                        + "FROM org_employee e "
                        + "JOIN org_employee_unit eu ON eu.employee_id = e.id "
                        + "JOIN org_unit ou ON ou.id = eu.org_id "
                        + "LEFT JOIN org_position p ON p.id = eu.position_id "
                        + "LEFT JOIN org_employee_type t ON t.id = eu.type_id "
                        + "WHERE e.user_id = ? AND e.deleted = 0 ORDER BY eu.is_primary DESC, ou.type", ctx.getUserId());
        m.put("units", units);

        List<Map<String, Object>> roles = jdbc.queryForList(
                "SELECT ur.role_code AS code, COALESCE(r.name, ur.role_code) AS name, ur.org_id AS orgId "
                        + "FROM sys_user_role ur LEFT JOIN sys_role r ON r.code = ur.role_code "
                        + "WHERE ur.user_id = ?", ctx.getUserId());
        m.put("roles", roles);
        m.put("permissions", ctx.getPermissions());
        return m;
    }

    // ---------------- F-26 今日任务 / 我的风险 ----------------

    public List<Map<String, Object>> todayTasks(UserContext ctx) {
        if (ctx.getEmployeeId() == null) return List.of();
        return jdbc.queryForList(
                "SELECT id, task_date AS taskDate, title, content, deadline, status, submit_text AS submitText, "
                        + "submit_time AS submitTime FROM task_today "
                        + "WHERE employee_id = ? AND task_date = CURDATE() ORDER BY id",
                ctx.getEmployeeId());
    }

    public void submitTask(UserContext ctx, long taskId, String text) {
        if (ctx.getEmployeeId() == null) throw BizException.forbidden("当前账号未绑定员工档案");
        if (text == null || text.isBlank()) throw BizException.badRequest("请填写任务提交内容");
        if (text.length() > 1000) throw BizException.badRequest("提交内容最多 1000 字");
        int n = jdbc.update(
                "UPDATE task_today SET status = 1, submit_text = ?, submit_time = ? "
                        + "WHERE id = ? AND employee_id = ? AND status = 0",
                text.trim(), LocalDateTime.now(), taskId, ctx.getEmployeeId());
        if (n == 0) throw BizException.badRequest("任务不存在，或已经提交过了");
    }

    public List<Map<String, Object>> risks(UserContext ctx) {
        if (ctx.getEmployeeId() == null) return List.of();
        return jdbc.queryForList(
                "SELECT id, risk_level AS riskLevel, overdue_count AS overdueCount, interview_time AS interviewTime, "
                        + "warn_time AS warnTime, status, remark, create_time AS createTime FROM risk_record "
                        + "WHERE employee_id = ? AND status = 0 ORDER BY risk_level DESC, id DESC",
                ctx.getEmployeeId());
    }

    // ---------------- F-28 登录记录 ----------------

    public List<Map<String, Object>> loginLogs(UserContext ctx) {
        return jdbc.queryForList(
                "SELECT id, account, login_type AS loginType, ip, ua, success, msg, create_time AS createTime "
                        + "FROM sys_login_log WHERE user_id = ? OR account = ? ORDER BY id DESC LIMIT 20",
                ctx.getUserId(), ctx.getAccount());
    }

    // ---------------- 工具 ----------------

    private String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private Integer num(Object o) {
        if (o == null) return null;
        if (o instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(String.valueOf(o).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String maskMobile(String m) {
        if (m == null || m.length() < 7) return m;
        return m.substring(0, 3) + "****" + m.substring(m.length() - 4);
    }

    private String maskMail(String m) {
        if (m == null || !m.contains("@")) return m;
        int at = m.indexOf('@');
        String name = m.substring(0, at);
        String head = name.length() <= 2 ? name.substring(0, 1) : name.substring(0, 2);
        return head + "***" + m.substring(at);
    }
}
