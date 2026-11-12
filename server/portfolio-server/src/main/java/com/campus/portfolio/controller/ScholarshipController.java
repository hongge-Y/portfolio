package com.campus.portfolio.controller;

import com.campus.portfolio.common.BizException;
import com.campus.portfolio.common.Result;
import com.campus.portfolio.common.UserContext;
import com.campus.portfolio.service.ScholarshipService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "奖学金", description = "计划 / 申请 / 三级审批 / 排名 / 公示 / 发放")
@RestController
@RequestMapping("/v1/scholarship")
public class ScholarshipController {

    private final ScholarshipService service;

    public ScholarshipController(ScholarshipService service) {
        this.service = service;
    }

    private void requireAdmin() {
        if (!UserContext.require().hasRole("ADMIN")) throw BizException.forbidden("只有管理员可以执行该操作");
    }

    @Operation(summary = "我可申请的计划")
    @GetMapping("/plan/available")
    public Result<List<Map<String, Object>>> available() {
        return Result.ok(service.availablePlans(UserContext.require()));
    }

    @Operation(summary = "管理员保存计划")
    @PostMapping("/plan")
    public Result<Long> savePlan(@RequestBody Map<String, Object> body) {
        requireAdmin();
        return Result.ok(service.savePlan(body));
    }

    @Operation(summary = "后台计划分页")
    @PostMapping("/plan/search")
    public Result<Map<String, Object>> planSearch(@RequestBody Map<String, Object> body) {
        requireAdmin();
        return Result.ok(service.planSearch(body));
    }

    @Operation(summary = "计划详情")
    @GetMapping("/plan/{id}")
    public Result<Map<String, Object>> planDetail(@PathVariable long id) {
        return Result.ok(service.planDetail(id));
    }

    @Operation(summary = "提交申请")
    @PostMapping("/apply")
    public Result<Map<String, Object>> apply(@RequestBody Map<String, Object> body) {
        return Result.ok(service.apply(body, UserContext.require()));
    }

    @Operation(summary = "我的申请")
    @PostMapping("/apply/my")
    public Result<Map<String, Object>> myApplies(@RequestBody Map<String, Object> body) {
        return Result.ok(service.myApplies(UserContext.require(),
                intOf(body.get("pageNum"), 1), intOf(body.get("pageSize"), 10)));
    }

    @Operation(summary = "申请详情（含审批轨迹）")
    @GetMapping("/apply/{id}")
    public Result<Map<String, Object>> applyDetail(@PathVariable long id) {
        return Result.ok(service.applyDetail(id, UserContext.require()));
    }

    @Operation(summary = "撤回申请（仅待审批）")
    @PostMapping("/apply/{id}/cancel")
    public Result<Void> cancel(@PathVariable long id) {
        service.cancelApply(id, UserContext.require());
        return Result.ok();
    }

    @Operation(summary = "待我审批")
    @PostMapping("/approve/search")
    public Result<Map<String, Object>> approvePage(@RequestBody Map<String, Object> body) {
        return Result.ok(service.approvePage(body, UserContext.require()));
    }

    @Operation(summary = "审批（1 通过 / 2 驳回）")
    @PostMapping("/approve")
    public Result<Map<String, Object>> approve(@RequestBody Map<String, Object> body) {
        return Result.ok(service.approve(body, UserContext.require()));
    }

    @Operation(summary = "生成排名")
    @PostMapping("/plan/{id}/rank")
    public Result<Map<String, Object>> rank(@PathVariable long id,
                                            @RequestBody(required = false) Map<String, Object> body) {
        requireAdmin();
        Long quota = body == null || body.get("quota") == null ? null : Long.valueOf(String.valueOf(body.get("quota")));
        return Result.ok(service.rank(id, quota));
    }

    @Operation(summary = "发起公示")
    @PostMapping("/plan/{id}/publicity")
    public Result<Map<String, Object>> publicity(@PathVariable long id,
                                                 @RequestBody(required = false) Map<String, Object> body) {
        requireAdmin();
        return Result.ok(service.publicity(id, body == null ? Map.of() : body));
    }

    @Operation(summary = "公示名单")
    @GetMapping("/publicity")
    public Result<Map<String, Object>> publicityList(@RequestParam(required = false) Long planId) {
        return Result.ok(service.publicityList(planId));
    }

    @Operation(summary = "发放")
    @PostMapping("/plan/{id}/grant")
    public Result<Map<String, Object>> grant(@PathVariable long id) {
        requireAdmin();
        return Result.ok(service.grant(id));
    }

    @Operation(summary = "我的获奖记录")
    @GetMapping("/result/my")
    public Result<Map<String, Object>> myResults() {
        return Result.ok(service.myApplies(UserContext.require(), 1, 50));
    }

    private static int intOf(Object o, int def) {
        if (o == null) return def;
        if (o instanceof Number n) return n.intValue();
        try { return Integer.parseInt(String.valueOf(o)); } catch (NumberFormatException e) { return def; }
    }
}
