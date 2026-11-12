package com.campus.portfolio.controller;

import com.campus.portfolio.common.Result;
import com.campus.portfolio.service.LeaveShareService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

/**
 * 免登录公开页（F-15 分享短链等）。
 * 这些接口在 WebConfig 里放行，不能出现任何可反推学生身份的信息。
 */
@Tag(name = "公开页", description = "免登录查看：请假分享快照")
@RestController
@RequestMapping("/v1/public")
public class PublicController {

    private final LeaveShareService leaveShareService;
    private final JdbcTemplate jdbc;

    public PublicController(LeaveShareService leaveShareService, JdbcTemplate jdbc) {
        this.leaveShareService = leaveShareService;
        this.jdbc = jdbc;
    }

    @Operation(summary = "注册页用的班级选项（只含班级名，不含任何学生信息）")
    @GetMapping("/classes")
    public Result<List<Map<String, Object>>> classes() {
        return Result.ok(jdbc.queryForList(
                "SELECT u.id, u.name, p.name AS college FROM org_unit u "
                        + "LEFT JOIN org_unit p ON p.id = u.parent_id "
                        + "WHERE u.type = 4 AND u.deleted = 0 AND u.status = 1 ORDER BY u.sort, u.id"));
    }

    @Operation(summary = "查看请假分享快照（24h 有效，脱敏）")
    @GetMapping("/leave-share/{token}")
    public Result<Map<String, Object>> leaveShare(@PathVariable String token) {
        return Result.ok(leaveShareService.view(token));
    }
}
