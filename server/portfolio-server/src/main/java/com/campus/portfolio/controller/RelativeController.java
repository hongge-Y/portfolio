package com.campus.portfolio.controller;

import com.campus.portfolio.common.Result;
import com.campus.portfolio.common.UserContext;
import com.campus.portfolio.service.RelativeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "家长关联", description = "双向申请 / 同意 / 拒绝 / 解除")
@RestController
@RequestMapping("/v1/relative")
public class RelativeController {

    private final RelativeService relativeService;

    public RelativeController(RelativeService relativeService) {
        this.relativeService = relativeService;
    }

    @Operation(summary = "家长用学号申请关联")
    @PostMapping("/apply")
    public Result<Map<String, Object>> apply(@RequestBody Map<String, Object> body) {
        return Result.ok(relativeService.applyByParent(body, UserContext.require()));
    }

    @Operation(summary = "学生用家长手机号申请关联")
    @PostMapping("/apply-by-student")
    public Result<Map<String, Object>> applyByStudent(@RequestBody Map<String, Object> body) {
        return Result.ok(relativeService.applyByStudent(body, UserContext.require()));
    }

    @Operation(summary = "我的关联申请列表")
    @PostMapping("/search")
    public Result<Map<String, Object>> search(@RequestBody Map<String, Object> body) {
        return Result.ok(relativeService.search(body, UserContext.require()));
    }

    @Operation(summary = "同意")
    @PostMapping("/{id}/agree")
    public Result<Map<String, Object>> agree(@PathVariable long id) {
        return Result.ok(relativeService.agree(id, UserContext.require()));
    }

    @Operation(summary = "拒绝")
    @PostMapping("/{id}/reject")
    public Result<Map<String, Object>> reject(@PathVariable long id, @RequestBody(required = false) Map<String, Object> body) {
        return Result.ok(relativeService.reject(id, body == null ? Map.of() : body, UserContext.require()));
    }

    @Operation(summary = "解除关联")
    @PostMapping("/{id}/unbind")
    public Result<Map<String, Object>> unbind(@PathVariable long id) {
        return Result.ok(relativeService.unbind(id, UserContext.require()));
    }

    @Operation(summary = "家长视角：已关联的孩子")
    @GetMapping("/children")
    public Result<List<Map<String, Object>>> children() {
        return Result.ok(relativeService.children(UserContext.require()));
    }

    @Operation(summary = "待我确认的申请数（红点）")
    @GetMapping("/pending-count")
    public Result<Integer> pending() {
        return Result.ok(relativeService.pendingCount(UserContext.require()));
    }
}
