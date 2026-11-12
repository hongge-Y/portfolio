package com.campus.portfolio.controller;

import com.campus.portfolio.common.Result;
import com.campus.portfolio.common.UserContext;
import com.campus.portfolio.dto.CodeLoginDTO;
import com.campus.portfolio.dto.CodeSendDTO;
import com.campus.portfolio.dto.LoginDTO;
import com.campus.portfolio.dto.RegisterDTO;
import com.campus.portfolio.service.AuthService;
import com.campus.portfolio.service.CaptchaService;
import com.campus.portfolio.service.RsaService;
import com.campus.portfolio.vo.LoginVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@Tag(name = "认证", description = "登录 / 当前用户 / 退出")
@RestController
@RequestMapping("/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final CaptchaService captchaService;
    private final RsaService rsaService;

    public AuthController(AuthService authService, CaptchaService captchaService, RsaService rsaService) {
        this.authService = authService;
        this.captchaService = captchaService;
        this.rsaService = rsaService;
    }

    @Operation(summary = "图形验证码（5 分钟有效，一次性）")
    @GetMapping("/captcha")
    public Result<Map<String, Object>> captcha() {
        return Result.ok(captchaService.create());
    }

    @Operation(summary = "登录公钥（前端 jsencrypt 用它加密密码）")
    @GetMapping("/public-key")
    public Result<Map<String, Object>> publicKey() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("publicKey", rsaService.publicKeyPem());
        m.put("alg", "RSA/ECB/PKCS1Padding");
        m.put("keySize", 2048);
        m.put("windowSeconds", 300);
        return Result.ok(m);
    }

    @Operation(summary = "发送手机/邮箱验证码（需先通过图形验证码）")
    @PostMapping("/code/send")
    public Result<Map<String, Object>> sendCode(@RequestBody CodeSendDTO dto) {
        return Result.ok(authService.sendCode(dto));
    }

    @Operation(summary = "验证码登录（手机/邮箱）")
    @PostMapping("/login/code")
    public Result<LoginVO> loginByCode(@RequestBody CodeLoginDTO dto, HttpServletRequest request) {
        return Result.ok(authService.loginByCode(dto, clientIp(request), request.getHeader("User-Agent")));
    }

    @Operation(summary = "当前账号是否已被要求输入验证码")
    @GetMapping("/captcha-state")
    public Result<Map<String, Object>> captchaState(@RequestParam String account) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("needCaptcha", captchaService.needCaptcha(account));
        m.put("failCount", captchaService.failCount(account));
        m.put("remainBeforeCaptcha", captchaService.remainBeforeCaptcha(account));
        m.put("loginType", authService.detectLoginType(account, null));
        return Result.ok(m);
    }

    @Operation(summary = "学生自助注册（学号 + 姓名 + 班级 + 密码）")
    @PostMapping("/register")
    public Result<Map<String, Object>> register(@Valid @RequestBody RegisterDTO dto) {
        return Result.ok(authService.register(dto));
    }

    @Operation(summary = "登录（账号/手机/邮箱三方式自动识别）")
    @PostMapping("/login/password")
    public Result<LoginVO> loginByPassword(@Valid @RequestBody LoginDTO dto, HttpServletRequest request) {
        String ip = clientIp(request);
        String ua = request.getHeader("User-Agent");
        return Result.ok(authService.login(dto, ip, ua));
    }

    /** 取真实客户端 IP（经过 vite 代理时 X-Forwarded-For 才有值） */
    private String clientIp(HttpServletRequest request) {
        String xf = request.getHeader("X-Forwarded-For");
        if (xf != null && !xf.isBlank()) return xf.split(",")[0].trim();
        String real = request.getHeader("X-Real-IP");
        if (real != null && !real.isBlank()) return real.trim();
        return request.getRemoteAddr();
    }

    @Operation(summary = "当前用户上下文（前端权限唯一来源）")
    @GetMapping("/me")
    public Result<Map<String, Object>> me() {
        UserContext ctx = UserContext.require();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("userId", ctx.getUserId());
        m.put("account", ctx.getAccount());
        m.put("name", ctx.getShowName());
        m.put("employeeId", ctx.getEmployeeId());
        m.put("employeeNo", ctx.getEmployeeNo());
        m.put("roles", ctx.getRoles());
        m.put("permissions", ctx.getPermissions());
        m.put("classOrgId", ctx.getClassOrgId());
        m.put("collegeOrgId", ctx.getCollegeOrgId());
        m.put("rootOrgId", ctx.getRootOrgId());
        m.put("orgPath", ctx.getOrgPath());
        m.put("isMonitor", ctx.hasRole("MONITOR"));
        m.put("isParent", ctx.hasRole("PARENT"));
        return Result.ok(m);
    }
}
