package com.campus.portfolio.controller;

import com.campus.portfolio.common.BizException;
import com.campus.portfolio.common.Result;
import com.campus.portfolio.common.UserContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Tag(name = "后台", description = "统计 / 员工查询")
@RestController
@RequestMapping("/v1/admin")
public class AdminController {

    private final JdbcTemplate jdbc;
    private final com.campus.portfolio.service.ReminderService reminderService;

    public AdminController(JdbcTemplate jdbc, com.campus.portfolio.service.ReminderService reminderService) {
        this.jdbc = jdbc;
        this.reminderService = reminderService;
    }

    private void requireAdmin() {
        if (!UserContext.require().hasRole("ADMIN")) throw BizException.forbidden("只有管理员可以访问后台");
    }

    @Operation(summary = "后台首页统计")
    @GetMapping("/stats")
    public Result<Map<String, Object>> stats() {
        requireAdmin();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("users", count("SELECT COUNT(*) FROM sys_user WHERE deleted = 0"));
        m.put("employees", count("SELECT COUNT(*) FROM org_employee WHERE deleted = 0"));
        m.put("classes", count("SELECT COUNT(*) FROM org_unit WHERE type = 4 AND deleted = 0"));
        m.put("leaves", count("SELECT COUNT(*) FROM leave_request WHERE deleted = 0"));
        m.put("pendingLeaves", count("SELECT COUNT(*) FROM leave_request WHERE deleted = 0 AND status IN (10,11)"));
        m.put("news", count("SELECT COUNT(*) FROM pub_news WHERE deleted = 0"));
        m.put("ads", count("SELECT COUNT(*) FROM pub_ad WHERE deleted = 0"));
        m.put("matches", count("SELECT COUNT(*) FROM pub_match WHERE deleted = 0"));
        m.put("ranks", count("SELECT COUNT(*) FROM pub_rank WHERE deleted = 0"));
        m.put("plans", count("SELECT COUNT(*) FROM sch_plan WHERE deleted = 0"));
        m.put("schApplies", count("SELECT COUNT(*) FROM sch_apply"));
        m.put("pendingSchApplies", count("SELECT COUNT(*) FROM sch_apply WHERE status IN (10,20,30)"));
        m.put("messages", count("SELECT COUNT(*) FROM msg_notification WHERE deleted = 0"));
        return Result.ok(m);
    }

    @Operation(summary = "按学号查员工（后台录入红黑榜/奖学金时用）")
    @GetMapping("/employee-lookup")
    public Result<Map<String, Object>> employeeLookup(@RequestParam String employeeNo) {
        requireAdmin();
        List<Map<String, Object>> list = jdbc.query(
                "SELECT e.id AS employeeId, e.name, e.employee_no AS employeeNo, ou.name AS className "
                        + "FROM org_employee e LEFT JOIN org_employee_unit eu ON eu.employee_id = e.id "
                        + "LEFT JOIN org_unit ou ON ou.id = eu.org_id "
                        + "WHERE e.employee_no = ? AND e.deleted = 0 LIMIT 1",
                (rs, i) -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("employeeId", rs.getLong("employeeId"));
                    m.put("name", rs.getString("name"));
                    m.put("employeeNo", rs.getString("employeeNo"));
                    m.put("className", rs.getString("className"));
                    return m;
                }, employeeNo.trim());
        if (list.isEmpty()) throw BizException.notFound("未找到该学号");
        return Result.ok(list.get(0));
    }

    @Operation(summary = "手动触发定时提醒（答辩演示用，平时由每小时定时任务执行）")
    @PostMapping("/job/remind")
    public Result<Map<String, Object>> runRemind() {
        requireAdmin();
        return Result.ok(reminderService.runAll());
    }

    @Operation(summary = "组织树（后台下拉用）")
    @GetMapping("/org-options")
    public Result<List<Map<String, Object>>> orgOptions() {
        requireAdmin();
        return Result.ok(jdbc.query(
                "SELECT id, name, type, parent_id AS parentId FROM org_unit WHERE deleted = 0 AND status = 1 "
                        + "ORDER BY type, sort, id",
                (rs, i) -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", rs.getLong("id"));
                    m.put("name", rs.getString("name"));
                    m.put("type", rs.getInt("type"));
                    m.put("parentId", rs.getObject("parentId"));
                    return m;
                }));
    }

    private int count(String sql) {
        Integer c = jdbc.queryForObject(sql, Integer.class);
        return c == null ? 0 : c;
    }
}
