package com.campus.portfolio.controller;

import com.campus.portfolio.common.Result;
import com.campus.portfolio.common.UserContext;
import com.campus.portfolio.service.ProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 个人中心（F-03 / F-04 / F-05 / F-26 / F-28）。
 * 全部接口都需要登录；用户身份只从 UserContext 取，前端传 userId 一律忽略。
 */
@Tag(name = "个人中心", description = "资料 / 安全 / 引导 / 组织与角色 / 今日任务 / 我的风险 / 登录记录")
@RestController
@RequestMapping("/v1/profile")
public class ProfileController {

    private final ProfileService profileService;

    public ProfileController(ProfileService profileService) {
        this.profileService = profileService;
    }

    @Operation(summary = "我的资料")
    @GetMapping("/me")
    public Result<Map<String, Object>> me() {
        return Result.ok(profileService.detail(UserContext.require()));
    }

    @Operation(summary = "修改资料（showName/nickName/birthday/gender/pet/petHide/avatar）")
    @PutMapping("/me")
    public Result<Map<String, Object>> update(@RequestBody Map<String, Object> body) {
        return Result.ok(profileService.update(UserContext.require(), body));
    }

    @Operation(summary = "修改密码")
    @PostMapping("/password")
    public Result<Map<String, Object>> password(@RequestBody Map<String, String> body) {
        profileService.changePassword(UserContext.require(), body.get("oldPassword"), body.get("newPassword"));
        return Result.ok(Map.of("ok", true));
    }

    @Operation(summary = "绑定手机 / 邮箱")
    @PostMapping("/bind")
    public Result<Map<String, Object>> bind(@RequestBody Map<String, String> body) {
        return Result.ok(profileService.bind(UserContext.require(),
                body.get("type"), body.get("value"), body.get("password")));
    }

    @Operation(summary = "完成新手引导（选小宠，首次送光粒与礼盒）")
    @PostMapping("/guide")
    public Result<Map<String, Object>> guide(@RequestBody Map<String, Object> body) {
        Integer pet = body.get("pet") == null ? null : Integer.valueOf(String.valueOf(body.get("pet")));
        return Result.ok(profileService.finishGuide(UserContext.require(), pet));
    }

    @Operation(summary = "组织与角色")
    @GetMapping("/org")
    public Result<Map<String, Object>> org() {
        return Result.ok(profileService.org(UserContext.require()));
    }

    @Operation(summary = "今日任务")
    @GetMapping("/today")
    public Result<List<Map<String, Object>>> today() {
        return Result.ok(profileService.todayTasks(UserContext.require()));
    }

    @Operation(summary = "提交今日任务")
    @PostMapping("/today/{id}/submit")
    public Result<Map<String, Object>> submitTask(@PathVariable long id, @RequestBody Map<String, String> body) {
        profileService.submitTask(UserContext.require(), id, body.get("text"));
        return Result.ok(Map.of("ok", true));
    }

    @Operation(summary = "我的风险预警")
    @GetMapping("/risks")
    public Result<List<Map<String, Object>>> risks() {
        return Result.ok(profileService.risks(UserContext.require()));
    }

    @Operation(summary = "近期登录记录")
    @GetMapping("/login-logs")
    public Result<List<Map<String, Object>>> loginLogs() {
        return Result.ok(profileService.loginLogs(UserContext.require()));
    }
}
