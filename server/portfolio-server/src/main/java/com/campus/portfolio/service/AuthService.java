package com.campus.portfolio.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.portfolio.common.BizException;
import com.campus.portfolio.common.JwtUtil;
import com.campus.portfolio.common.UserContext;
import com.campus.portfolio.dto.CodeLoginDTO;
import com.campus.portfolio.dto.CodeSendDTO;
import com.campus.portfolio.dto.LoginDTO;
import com.campus.portfolio.entity.SysUser;
import com.campus.portfolio.dto.RegisterDTO;
import com.campus.portfolio.mapper.SysUserMapper;
import com.campus.portfolio.vo.LoginVO;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class AuthService {

    private final SysUserMapper userMapper;
    private final JdbcTemplate jdbc;
    private final JwtUtil jwtUtil;
    private final CaptchaService captcha;
    private final RsaService rsa;
    private final VerifyCodeService codes;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    private static final java.util.regex.Pattern MOBILE_PATTERN =
            java.util.regex.Pattern.compile("^1\\d{10}$");

    public AuthService(SysUserMapper userMapper, JdbcTemplate jdbc, JwtUtil jwtUtil,
                       CaptchaService captcha, RsaService rsa, VerifyCodeService codes) {
        this.userMapper = userMapper;
        this.jdbc = jdbc;
        this.jwtUtil = jwtUtil;
        this.captcha = captcha;
        this.rsa = rsa;
        this.codes = codes;
    }

    public LoginVO login(LoginDTO dto) {
        return login(dto, null, null);
    }

    /**
     * 登录（F-01）：account 支持 学号/工号 / 手机号 / 邮箱，自动识别；
     * 连续 5 次密码错误后强制图形验证码；每次尝试都写 sys_login_log（F-28）。
     */
    public LoginVO login(LoginDTO dto, String ip, String ua) {
        String account = dto.getAccount() == null ? "" : dto.getAccount().trim();
        String loginType = detectLoginType(account, dto.getLoginType());

        // 0) 密文口令：解开 sign 取出明文口令（明文登录仅为内网联调兼容）
        if (dto.getSign() != null && !dto.getSign().isBlank()) {
            java.util.Map<String, Object> plain = rsa.decryptSign(dto.getSign());
            dto.setPassword(String.valueOf(plain.get("password")));
        } else if (dto.getPassword() == null || dto.getPassword().isBlank()) {
            throw new BizException(400, "PASSWORD_REQUIRED", "密码不能为空");
        }

        // 1) 验证码闸门
        if (captcha.needCaptcha(account)) {
            if (dto.getCaptchaCode() == null || dto.getCaptchaCode().isBlank()) {
                throw new BizException(400, "CAPTCHA_REQUIRED", "密码连续错误过多，请输入图形验证码");
            }
            if (!captcha.verify(dto.getCaptchaId(), dto.getCaptchaCode())) {
                logLogin(null, account, loginType, ip, ua, false, "验证码错误");
                throw new BizException(400, "CAPTCHA_WRONG", "验证码错误或已过期，请点击刷新");
            }
        }

        // 2) 按登录方式找人
        SysUser user = findByLogin(account, loginType);

        // 3) 校验口令
        boolean pwdOk = user != null && user.getPasswordHash() != null
                && encoder.matches(dto.getPassword(), user.getPasswordHash());
        if (!pwdOk) {
            captcha.markFail(account);
            logLogin(user == null ? null : user.getId(), account, loginType, ip, ua, false, "账号或密码错误");
            int remain = captcha.remainBeforeCaptcha(account);
            throw new BizException(400, "LOGIN_FAIL", remain > 0
                    ? "账号或密码错误，再错 " + remain + " 次需要输入验证码"
                    : "账号或密码错误，已需要输入图形验证码");
        }
        if (user.getStatus() != null && user.getStatus() == 0) {
            logLogin(user.getId(), account, loginType, ip, ua, false, "账号已禁用");
            throw new BizException(400, "ACCOUNT_DISABLED", "账号已被禁用，请联系管理员");
        }

        // 4) 成功：清失败计数、记日志、更新最后登录
        captcha.clearFail(account);
        jdbc.update("UPDATE sys_user SET last_login_time = NOW(), last_login_ip = ? WHERE id = ?", ip, user.getId());
        logLogin(user.getId(), account, loginType, ip, ua, true, "登录成功");

        UserContext ctx = buildContext(user);
        String token = jwtUtil.createToken(ctx);

        LoginVO vo = new LoginVO();
        vo.setUserId(user.getId());
        vo.setAccount(user.getAccount());
        vo.setShowName(user.getShowName());
        vo.setAvatar(user.getAvatar());
        vo.setAccessToken(token);
        vo.setExpiresIn(String.valueOf(jwtUtil.getAccessTtlSeconds()));
        vo.setGuideDone(user.getGuideDone() != null && user.getGuideDone() == 1);
        vo.setRoles(ctx.getRoles());
        vo.setLoginType(loginType);
        return vo;
    }

    /**
     * 手机 / 邮箱验证码登录（登录页的另外两个 tab）。
     * 验证码由 {@link VerifyCodeService} 校验并当场消费；成功/失败都写 sys_login_log。
     */
    public LoginVO loginByCode(CodeLoginDTO dto, String ip, String ua) {
        String target = dto.getTarget() == null ? "" : dto.getTarget().trim();
        String type = dto.getType() == null ? "" : dto.getType().trim().toUpperCase();
        String loginType = (type.equals("MAIL") || type.equals("EMAIL")) ? "MAIL" : "MOBILE";
        String code = dto.getCode() == null ? "" : dto.getCode().trim();

        if (target.isEmpty()) throw BizException.badRequest("请输入手机号或邮箱");
        if (!code.matches("^\\d{6}$")) throw BizException.badRequest("请输入 6 位数字验证码");

        if (!codes.verify(target, loginType, code)) {
            logLogin(null, target, loginType, ip, ua, false, "验证码错误");
            throw new BizException(400, "CODE_WRONG", "验证码错误或已过期，请重新获取");
        }

        SysUser user = findByLogin(target, loginType);
        if (user == null) {
            logLogin(null, target, loginType, ip, ua, false, "账号未绑定");
            throw new BizException(400, "ACCOUNT_NOT_BOUND",
                    (loginType.equals("MAIL") ? "该邮箱" : "该手机号") + "未绑定账号，请先用密码登录");
        }
        if (user.getStatus() != null && user.getStatus() == 0) {
            logLogin(user.getId(), target, loginType, ip, ua, false, "账号已禁用");
            throw new BizException(400, "ACCOUNT_DISABLED", "账号已被禁用，请联系管理员");
        }
        captcha.clearFail(user.getAccount());
        return issue(user, loginType, ip, ua);
    }

    /** 签发 token + 写登录日志 + 更新最后登录（密码登录与验证码登录共用） */
    private LoginVO issue(SysUser user, String loginType, String ip, String ua) {
        jdbc.update("UPDATE sys_user SET last_login_time = NOW(), last_login_ip = ? WHERE id = ?", ip, user.getId());
        logLogin(user.getId(), user.getAccount(), loginType, ip, ua, true, "登录成功");

        UserContext ctx = buildContext(user);
        String token = jwtUtil.createToken(ctx);

        LoginVO vo = new LoginVO();
        vo.setUserId(user.getId());
        vo.setAccount(user.getAccount());
        vo.setShowName(user.getShowName());
        vo.setAvatar(user.getAvatar());
        vo.setAccessToken(token);
        vo.setExpiresIn(String.valueOf(jwtUtil.getAccessTtlSeconds()));
        vo.setGuideDone(user.getGuideDone() != null && user.getGuideDone() == 1);
        vo.setRoles(ctx.getRoles());
        vo.setLoginType(loginType);
        return vo;
    }

    /** 发验证码前先过图形码（防止被当短信轰炸接口刷） */
    /**
     * 学生自助注册：建账号 + 绑班级 + 建员工档案。
     * <p>
     * 为什么不复用 DataInitializer：那个是种子数据，非幂等且写死演示账号；
     * 注册必须自己校验唯一性、自己决定班级归属。
     */
    public Map<String, Object> register(RegisterDTO dto) {
        String account = trim(dto.getAccount(), 20);
        if (account == null || !account.matches("\\d{8,12}")) {
            throw new BizException(400, "REGISTER_ACCOUNT", "学号须为 8-12 位数字");
        }
        String name = trim(dto.getShowName(), 20);
        if (name == null || name.length() < 2) {
            throw new BizException(400, "REGISTER_NAME", "姓名至少 2 个字");
        }
        String pwd = dto.getPassword();
        if (pwd == null || pwd.length() < 6 || pwd.length() > 20) {
            throw new BizException(400, "REGISTER_PASSWORD", "密码长度需为 6-20 位");
        }
        String mobile = trim(dto.getMobile(), 20);
        if (mobile != null && !mobile.isEmpty() && !MOBILE_PATTERN.matcher(mobile).matches()) {
            throw new BizException(400, "REGISTER_MOBILE", "手机号格式不正确");
        }
        String mail = trim(dto.getMail(), 64);
        if (mail != null && mail.isEmpty()) mail = null;

        Long classId = dto.getClassId();
        if (classId == null) {
            throw new BizException(400, "REGISTER_CLASS", "请选择所在班级");
        }
        Integer cls = jdbc.queryForObject(
                "SELECT COUNT(*) FROM org_unit WHERE id = ? AND type = 4 AND deleted = 0 AND status = 1",
                Integer.class, classId);
        if (cls == null || cls == 0) {
            throw new BizException(400, "REGISTER_CLASS", "班级不存在或已停用");
        }

        Integer dup = jdbc.queryForObject("SELECT COUNT(*) FROM sys_user WHERE account = ?", Integer.class, account);
        if (dup != null && dup > 0) {
            throw new BizException(400, "REGISTER_DUP", "该学号已注册，请直接登录");
        }
        if (mobile != null && !mobile.isEmpty()) {
            Integer m = jdbc.queryForObject("SELECT COUNT(*) FROM sys_user WHERE mobile = ?", Integer.class, mobile);
            if (m != null && m > 0) {
                throw new BizException(400, "REGISTER_MOBILE_DUP", "该手机号已被其他账号使用");
            }
        }
        if (mail != null) {
            Integer m = jdbc.queryForObject("SELECT COUNT(*) FROM sys_user WHERE mail = ?", Integer.class, mail);
            if (m != null && m > 0) {
                throw new BizException(400, "REGISTER_MAIL_DUP", "该邮箱已被其他账号使用");
            }
        }

        jdbc.update("INSERT INTO sys_user (account, mobile, mail, password_hash, show_name, gender, coin, gift, guide_done) "
                        + "VALUES (?, ?, ?, ?, ?, 2, 0, 0, 0)",
                account, (mobile == null || mobile.isEmpty()) ? null : mobile, mail,
                encoder.encode(pwd), name);
        Long uid = jdbc.queryForObject("SELECT id FROM sys_user WHERE account = ?", Long.class, account);

        jdbc.update("INSERT INTO sys_user_role (user_id, role_code, org_id) VALUES (?, ?, ?)", uid, "STUDENT", classId);

        Long rootOrgId = jdbc.queryForObject(
                "SELECT id FROM org_unit WHERE type = 1 AND deleted = 0 ORDER BY id LIMIT 1", Long.class);
        jdbc.update("INSERT INTO org_employee (user_id, name, employee_no, identity_type, identity_no, sex, "
                        + "root_org_id, hire_date, enable_relative) VALUES (?, ?, ?, 1, ?, 0, ?, CURDATE(), 1)",
                uid, name, account, "S" + account, rootOrgId);
        Long empId = jdbc.queryForObject("SELECT id FROM org_employee WHERE user_id = ?", Long.class, uid);
        jdbc.update("INSERT INTO org_employee_unit (employee_id, org_id, type_id, is_primary) VALUES (?, ?, 2, 1)",
                empId, classId);

        logLogin(uid, account, "REGISTER", null, null, true, "学生自助注册");

        Map<String, Object> data = new java.util.LinkedHashMap<>();
        data.put("userId", uid);
        data.put("account", account);
        data.put("showName", name);
        data.put("classId", classId);
        return data;
    }

    public Map<String, Object> sendCode(CodeSendDTO dto) {
        if (dto.getCaptchaCode() == null || dto.getCaptchaCode().isBlank()) {
            throw new BizException(400, "CAPTCHA_REQUIRED", "请先输入图形验证码");
        }
        if (!captcha.verify(dto.getCaptchaId(), dto.getCaptchaCode())) {
            throw new BizException(400, "CAPTCHA_WRONG", "图形验证码错误或已过期，请点击刷新");
        }
        return codes.send(dto.getTarget(), dto.getType());
    }

    /** 登录标识形态判断：11 位 1 开头 → 手机；含 @ → 邮箱；其余按账号 */

    public String detectLoginType(String account, String hint) {
        if (hint != null && !hint.isBlank()) {
            String h = hint.trim().toUpperCase();
            if (h.equals("MOBILE") || h.equals("MAIL") || h.equals("PASSWORD")) return h;
        }
        if (account != null) {
            if (MOBILE_PATTERN.matcher(account).matches()) return "MOBILE";
            if (account.contains("@")) return "MAIL";
        }
        return "PASSWORD";
    }

    private SysUser findByLogin(String account, String loginType) {
        LambdaQueryWrapper<SysUser> w = new LambdaQueryWrapper<>();
        switch (loginType) {
            case "MOBILE" -> w.eq(SysUser::getMobile, account);
            case "MAIL" -> w.eq(SysUser::getMail, account);
            default -> w.eq(SysUser::getAccount, account);
        }
        return userMapper.selectOne(w);
    }

    /** 登录日志（失败不抛异常，日志问题不能挡住登录） */
    public void logLogin(Long userId, String account, String loginType, String ip, String ua,
                         boolean success, String msg) {
        try {
            jdbc.update("INSERT INTO sys_login_log (user_id, account, login_type, ip, ua, success, msg) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                    userId, trim(account, 64), loginType, trim(ip, 64), trim(ua, 255),
                    success ? 1 : 0, trim(msg, 255));
        } catch (Exception ignore) {
            // 日志失败不影响主流程
        }
    }

    private String trim(String v, int max) {
        if (v == null) return null;
        return v.length() <= max ? v : v.substring(0, max);
    }


    /** 组装登录上下文：角色 + 员工档案 + 班级组织 */
    public UserContext buildContext(SysUser user) {
        UserContext ctx = new UserContext();
        ctx.setUserId(user.getId());
        ctx.setAccount(user.getAccount());
        ctx.setShowName(user.getShowName());

        List<String> roles = jdbc.queryForList(
                "SELECT role_code FROM sys_user_role WHERE user_id = ?", String.class, user.getId());
        ctx.setRoles(new ArrayList<>(roles));
        ctx.setPermissions(buildPermissions(roles));

        List<Map<String, Object>> emps = jdbc.queryForList(
                "SELECT id, employee_no, root_org_id FROM org_employee WHERE user_id = ? AND deleted = 0",
                user.getId());
        if (!emps.isEmpty()) {
            Map<String, Object> emp = emps.get(0);
            ctx.setEmployeeId(((Number) emp.get("id")).longValue());
            ctx.setEmployeeNo((String) emp.get("employee_no"));
            ctx.setRootOrgId(((Number) emp.get("root_org_id")).longValue());

            List<Map<String, Object>> units = jdbc.queryForList(
                    "SELECT eu.org_id, u.path FROM org_employee_unit eu "
                            + "JOIN org_unit u ON u.id = eu.org_id "
                            + "WHERE eu.employee_id = ? ORDER BY eu.is_primary DESC LIMIT 1",
                    ctx.getEmployeeId());
            if (!units.isEmpty()) {
                Long orgId = ((Number) units.get(0).get("org_id")).longValue();
                String path = (String) units.get(0).get("path");
                ctx.setClassOrgId(orgId);
                ctx.setOrgPath(path);
                if (path != null && path.length() > 2) {
                    // path 形如 /1/2/3/4/ → ["", "1"(学校), "2"(学院), "3"(系), "4"(班)]
                    String[] parts = path.split("/");
                    if (parts.length > 2) {
                        ctx.setCollegeOrgId(Long.parseLong(parts[2]));
                    }
                }
            }
        }
        return ctx;
    }

    /** 角色 → 权限点（与 04 篇 4.1 对应） */
    private List<String> buildPermissions(List<String> roles) {
        List<String> p = new ArrayList<>();
        if (roles.contains("STUDENT")) {
            p.addAll(List.of("leave:apply", "rank:self:view", "edu:timetable:view",
                    "edu:eval:submit", "scholarship:apply", "message:send", "relative:confirm"));
        }
        if (roles.contains("MONITOR")) {
            p.addAll(List.of("leave:class:view", "leave:share"));
        }
        if (roles.contains("COUNSELOR") || roles.contains("SECRETARY")) {
            p.addAll(List.of("leave:approve", "leave:class:view"));
        }
        if (roles.contains("PARENT")) {
            p.addAll(List.of("rank:child:view", "relative:apply"));
        }
        if (roles.contains("ADMIN")) {
            p.addAll(List.of("admin:all", "publish:manage", "org:manage"));
        }
        return p.stream().distinct().toList();
    }

    public UserContext contextOf(Long userId) {
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw BizException.unauthorized("用户不存在");
        }
        return buildContext(user);
    }
}
