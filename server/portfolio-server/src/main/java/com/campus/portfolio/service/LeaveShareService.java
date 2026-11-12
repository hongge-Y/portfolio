package com.campus.portfolio.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.campus.portfolio.common.BizException;
import com.campus.portfolio.common.UserContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * F-15 学委把本班已通过的请假分享给授课老师。
 * <p>
 * 规则（对应 01 篇 7.3 验收口径）：
 * ① 只有本班学委能生成，且只能分享「本班 + 已通过(20/21)」的假条；
 * ② 分享内容落 leave_share.snapshot_json，是快照——生成后原假条再变也不影响已发出的链接；
 * ③ 链接免登录可看、24 小时失效、只含脱敏信息（姓名打码、无理由、无附件）。
 */
@Service
public class LeaveShareService {

    private static final SecureRandom RND = new SecureRandom();
    private static final int EXPIRE_HOURS = 24;
    private static final ObjectMapper JSON = new ObjectMapper();

    private final JdbcTemplate jdbc;

    public LeaveShareService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 学委生成分享链接 */
    public Map<String, Object> create(UserContext ctx, List<Long> leaveIds, Long teacherEmployeeId) {
        if (ctx.getEmployeeId() == null) throw BizException.forbidden("当前账号未绑定员工档案");
        if (!ctx.hasRole("MONITOR") && !ctx.hasRole("ADMIN")) {
            throw BizException.forbidden("只有学委可以分享班级请假");
        }
        if (leaveIds == null || leaveIds.isEmpty()) throw BizException.badRequest("请先勾选要分享的请假记录");
        if (leaveIds.size() > 100) throw BizException.badRequest("一次最多分享 100 条");

        List<Map<String, Object>> items = new ArrayList<>();
        Long classOrgId = null;
        for (Long id : leaveIds) {
            List<Map<String, Object>> rows = jdbc.queryForList(
                    "SELECT lr.id, lr.class_org_id AS classOrgId, lr.type, lr.start_time AS startTime, "
                            + "lr.end_time AS endTime, lr.days, lr.status, e.name AS studentName, ou.name AS className "
                            + "FROM leave_request lr "
                            + "JOIN org_employee e ON e.id = lr.employee_id "
                            + "LEFT JOIN org_unit ou ON ou.id = lr.class_org_id "
                            + "WHERE lr.id = ? AND lr.deleted = 0", id);
            if (rows.isEmpty()) throw BizException.notFound("请假记录不存在：" + id);
            Map<String, Object> r = rows.get(0);

            long orgId = ((Number) r.get("classOrgId")).longValue();
            if (!ctx.hasRole("ADMIN") && (ctx.getClassOrgId() == null || ctx.getClassOrgId() != orgId)) {
                throw BizException.forbidden("只能分享本班的请假记录");
            }
            int status = ((Number) r.get("status")).intValue();
            if (status != 20 && status != 21) throw BizException.badRequest("只能分享已通过或已销假的请假");

            classOrgId = orgId;
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("leaveId", ((Number) r.get("id")).longValue());
            item.put("studentName", maskName((String) r.get("studentName")));
            item.put("className", r.get("className"));
            item.put("type", ((Number) r.get("type")).intValue() == 1 ? "病假" : "事假");
            item.put("startTime", String.valueOf(r.get("startTime")));
            item.put("endTime", String.valueOf(r.get("endTime")));
            item.put("days", r.get("days"));
            item.put("status", status);
            item.put("statusText", status == 21 ? "已销假" : "已通过");
            items.add(item);
        }

        String token = randomToken();
        LocalDateTime expire = LocalDateTime.now().plusHours(EXPIRE_HOURS);
        String snapshot;
        try {
            snapshot = JSON.writeValueAsString(items);
        } catch (Exception e) {
            throw BizException.badRequest("生成分享快照失败");
        }

        jdbc.update("INSERT INTO leave_share (class_org_id, leave_ids, snapshot_json, teacher_employee_id, "
                        + "share_token, expire_time, share_by) VALUES (?, ?, CAST(? AS JSON), ?, ?, ?, ?)",
                classOrgId, joinIds(leaveIds), snapshot, teacherEmployeeId, token, expire, ctx.getEmployeeId());

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("token", token);
        m.put("path", "/share/leave/" + token);
        m.put("count", items.size());
        m.put("expireTime", expire.toString().replace('T', ' '));
        m.put("expireHours", EXPIRE_HOURS);
        return m;
    }

    /** 免登录查看（脱敏快照） */
    public Map<String, Object> view(String token) {
        if (token == null || token.isBlank()) throw BizException.notFound("分享链接无效");
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT s.share_token, s.snapshot_json, s.expire_time, s.create_time, ou.name AS className, "
                        + "e.name AS shareByName FROM leave_share s "
                        + "LEFT JOIN org_unit ou ON ou.id = s.class_org_id "
                        + "LEFT JOIN org_employee e ON e.id = s.share_by "
                        + "WHERE s.share_token = ?", token.trim());
        if (rows.isEmpty()) throw BizException.notFound("分享链接不存在或已被删除");

        Map<String, Object> r = rows.get(0);
        Object expireObj = r.get("expire_time");
        if (expireObj != null) {
            LocalDateTime expire = (expireObj instanceof LocalDateTime dt)
                    ? dt : LocalDateTime.parse(String.valueOf(expireObj).replace(' ', 'T'));
            if (expire.isBefore(LocalDateTime.now())) {
                throw new BizException(404, "SHARE_EXPIRED", "分享链接已过期（有效期 24 小时），请联系学委重新分享");
            }
        }

        List<?> items;
        try {
            items = JSON.readValue(String.valueOf(r.get("snapshot_json")), List.class);
        } catch (Exception e) {
            items = List.of();
        }

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("className", r.get("className"));
        m.put("shareBy", r.get("shareByName"));
        m.put("expireTime", String.valueOf(r.get("expire_time")));
        m.put("items", items);
        m.put("safeNote", "本页面为脱敏快照：不含请假理由与附件，仅用于任课老师核对学生是否在假。");
        return m;
    }

    // ---------------- 工具 ----------------

    private String maskName(String name) {
        if (name == null || name.isBlank()) return "**";
        if (name.length() == 1) return name + "*";
        if (name.length() == 2) return name.charAt(0) + "*";
        return name.charAt(0) + "*" + name.charAt(name.length() - 1);
    }

    private String joinIds(List<Long> ids) {
        StringBuilder sb = new StringBuilder();
        for (Long id : ids) {
            if (sb.length() > 0) sb.append(',');
            sb.append(id);
        }
        String s = sb.toString();
        return s.length() > 500 ? s.substring(0, 500) : s;
    }

    private String randomToken() {
        byte[] buf = new byte[16];
        RND.nextBytes(buf);
        StringBuilder sb = new StringBuilder(32);
        for (byte b : buf) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
