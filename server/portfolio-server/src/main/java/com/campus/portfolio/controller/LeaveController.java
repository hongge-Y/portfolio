package com.campus.portfolio.controller;

import com.campus.portfolio.common.BizException;
import com.campus.portfolio.common.Result;
import com.campus.portfolio.common.UserContext;
import com.campus.portfolio.dto.LeaveApplyDTO;
import com.campus.portfolio.dto.LeaveApproveDTO;
import com.campus.portfolio.dto.LeaveQueryDTO;
import com.campus.portfolio.service.LeaveService;
import com.campus.portfolio.service.LeaveShareService;
import com.campus.portfolio.vo.LeaveVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Tag(name = "请假", description = "申请 / 审批 / 销假 / 取消 / 改期 / 班级查看")
@RestController
@RequestMapping("/v1/leave")
public class LeaveController {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final LeaveService leaveService;
    private final LeaveShareService leaveShareService;

    public LeaveController(LeaveService leaveService, LeaveShareService leaveShareService) {
        this.leaveService = leaveService;
        this.leaveShareService = leaveShareService;
    }

    @Operation(summary = "学委分享本班已通过请假给授课老师（生成 24h 免登录链接）")
    @PostMapping("/share")
    public Result<Map<String, Object>> share(@RequestBody Map<String, Object> body) {
        List<Long> ids = new java.util.ArrayList<>();
        Object raw = body.get("leaveIds");
        if (raw instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof Number n) ids.add(n.longValue());
                else if (o != null) ids.add(Long.valueOf(String.valueOf(o)));
            }
        }
        Long teacherId = body.get("teacherEmployeeId") == null ? null
                : Long.valueOf(String.valueOf(body.get("teacherEmployeeId")));
        return Result.ok(leaveShareService.create(UserContext.require(), ids, teacherId));
    }

    @Operation(summary = "请假规则（前端预校验用）")
    @GetMapping("/rule")
    public Result<Map<String, Object>> rule() {
        return Result.ok(leaveService.rule());
    }

    @Operation(summary = "提交 / 修改后重新提交")
    @PostMapping("/apply")
    public Result<Map<String, Object>> apply(@Valid @RequestBody LeaveApplyDTO dto) {
        return Result.ok(leaveService.apply(dto, UserContext.require()));
    }

    @Operation(summary = "取消请假（仅未审批或已驳回）")
    @PostMapping("/{id}/cancel")
    public Result<Void> cancel(@PathVariable Long id) {
        leaveService.cancel(id, UserContext.require());
        return Result.ok();
    }

    @Operation(summary = "销假（仅已通过待销假）")
    @PostMapping("/{id}/return")
    public Result<Map<String, Object>> returnFromLeave(@PathVariable Long id,
                                                       @RequestBody(required = false) Map<String, String> body) {
        LocalDateTime end = body == null || body.get("endTime") == null || body.get("endTime").isBlank()
                ? null : LocalDateTime.parse(body.get("endTime"), FMT);
        return Result.ok(leaveService.returnFromLeave(id, end, UserContext.require()));
    }

    @Operation(summary = "修改结束时间并重新审批")
    @PostMapping("/{id}/extend")
    public Result<Map<String, Object>> extend(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String end = body.get("endTime");
        if (end == null || end.isBlank()) {
            throw BizException.badRequest("新的结束时间不能为空");
        }
        return Result.ok(leaveService.extend(id, LocalDateTime.parse(end, FMT), UserContext.require()));
    }

    @Operation(summary = "我的请假分页")
    @PostMapping("/personal/search")
    public Result<Map<String, Object>> personal(@RequestBody LeaveQueryDTO q) {
        return Result.ok(leaveService.personalPage(q, UserContext.require()));
    }

    @Operation(summary = "班级已通过请假（学委/辅导员；学委脱敏）")
    @PostMapping("/class/search")
    public Result<Map<String, Object>> classPage(@RequestBody LeaveQueryDTO q) {
        return Result.ok(leaveService.classPage(q, UserContext.require()));
    }

    @Operation(summary = "待我审批 / 我已处理")
    @PostMapping("/approve/search")
    public Result<Map<String, Object>> approvePage(@RequestBody LeaveQueryDTO q) {
        return Result.ok(leaveService.approvePage(q, UserContext.require()));
    }

    @Operation(summary = "请假详情")
    @GetMapping("/{id:[0-9]+}")
    public Result<LeaveVO> detail(@PathVariable Long id) {
        return Result.ok(leaveService.detail(id, UserContext.require()));
    }

    @Operation(summary = "审批时详情（含申请人姓名与附件）")
    @GetMapping("/{id:[0-9]+}/approve-detail")
    public Result<LeaveVO> approveDetail(@PathVariable Long id) {
        return Result.ok(leaveService.detail(id, UserContext.require()));
    }

    @Operation(summary = "审批（1 批准 / 2 驳回）")
    @PostMapping("/approve")
    public Result<Map<String, Object>> approve(@Valid @RequestBody LeaveApproveDTO dto) {
        return Result.ok(leaveService.approve(dto, UserContext.require()));
    }

    @Operation(summary = "我的附件（调试用）")
    @GetMapping("/{id:[0-9]+}/attachments")
    public Result<List<LeaveVO.Attachment>> attachments(@PathVariable Long id) {
        leaveService.detail(id, UserContext.require());
        return Result.ok(leaveService.attachments(id));
    }
}
