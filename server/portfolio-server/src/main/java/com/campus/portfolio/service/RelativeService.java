package com.campus.portfolio.service;

import com.campus.portfolio.common.BizException;
import com.campus.portfolio.common.UserContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 家长-学生关联：双向申请、同意/拒绝/解除，全程不经过学校工作人员。
 * 一个家长可关联多个学生，一个学生可被多个家长关联。
 */
@Service
public class RelativeService {

    private final JdbcTemplate jdbc;
    private final MessageService messageService;

    public RelativeService(JdbcTemplate jdbc, MessageService messageService) {
        this.jdbc = jdbc;
        this.messageService = messageService;
    }

    // ------------------------------------------------------------ 申请

    /** 家长用学号申请关联学生 */
    @Transactional
    public Map<String, Object> applyByParent(Map<String, Object> body, UserContext ctx) {
        String employeeNo = str(body.get("employeeNo"));
        if (employeeNo == null || employeeNo.isBlank()) {
            throw BizException.badRequest("请填写学生学号");
        }
        Map<String, Object> emp = one("SELECT id, user_id, name, employee_no, enable_relative "
                + "FROM org_employee WHERE employee_no = ? AND deleted = 0", employeeNo.trim());
        if (emp == null) throw BizException.badRequest("未找到该学号的学生");
        if (((Number) emp.get("enable_relative")).intValue() == 0) {
            throw BizException.badRequest("该学生已关闭家长关联");
        }
        long employeeId = ((Number) emp.get("id")).longValue();
        long studentUserId = ((Number) emp.get("user_id")).longValue();

        Map<String, Object> exist = one("SELECT id, status FROM rel_relative_link "
                + "WHERE parent_user_id = ? AND employee_id = ?", ctx.getUserId(), employeeId);
        long id;
        if (exist != null) {
            int status = ((Number) exist.get("status")).intValue();
            if (status == 0 || status == 1) {
                throw new BizException(400, "RELATIVE_EXISTS", "已存在关联或待确认的申请");
            }
            id = ((Number) exist.get("id")).longValue();
            jdbc.update("UPDATE rel_relative_link SET status = 0, apply_from = 'PARENT', remark = ?, "
                    + "apply_time = NOW(), handle_time = NULL, handle_user_id = NULL WHERE id = ?",
                    str(body.get("remark")), id);
        } else {
            jdbc.update("INSERT INTO rel_relative_link (parent_user_id, employee_id, apply_from, status, remark) "
                    + "VALUES (?, ?, 'PARENT', 0, ?)", ctx.getUserId(), employeeId, str(body.get("remark")));
            id = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        }
        messageService.notify(List.of(studentUserId), 1, "家长关联申请",
                "<p>有家长申请与你建立关联，请到「个人中心 → 家长关联」确认。</p>", 1);

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("id", id);
        res.put("status", 0);
        res.put("statusText", "待学生确认");
        res.put("studentName", emp.get("name"));
        return res;
    }

    /** 学生用家长手机号发起申请 */
    @Transactional
    public Map<String, Object> applyByStudent(Map<String, Object> body, UserContext ctx) {
        String mobile = str(body.get("parentMobile"));
        if (mobile == null || mobile.isBlank()) throw BizException.badRequest("请填写家长手机号");
        if (ctx.getEmployeeId() == null) throw BizException.badRequest("当前账号未绑定学生档案");

        Map<String, Object> parent = one("SELECT id, show_name FROM sys_user WHERE mobile = ? AND deleted = 0", mobile.trim());
        if (parent == null) throw BizException.badRequest("该手机号尚未注册本系统");
        long parentUserId = ((Number) parent.get("id")).longValue();
        if (parentUserId == ctx.getUserId()) throw BizException.badRequest("不能与自己关联");

        Map<String, Object> exist = one("SELECT id, status FROM rel_relative_link WHERE parent_user_id = ? AND employee_id = ?",
                parentUserId, ctx.getEmployeeId());
        long id;
        if (exist != null) {
            int status = ((Number) exist.get("status")).intValue();
            if (status == 0 || status == 1) throw new BizException(400, "RELATIVE_EXISTS", "已存在关联或待确认的申请");
            id = ((Number) exist.get("id")).longValue();
            jdbc.update("UPDATE rel_relative_link SET status = 0, apply_from = 'STUDENT', remark = ?, apply_time = NOW(), "
                    + "handle_time = NULL WHERE id = ?", str(body.get("remark")), id);
        } else {
            jdbc.update("INSERT INTO rel_relative_link (parent_user_id, employee_id, apply_from, status, remark) "
                    + "VALUES (?, ?, 'STUDENT', 0, ?)", parentUserId, ctx.getEmployeeId(), str(body.get("remark")));
            id = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        }
        // 确保家长账号具备 PARENT 角色
        Integer hasRole = jdbc.queryForObject("SELECT COUNT(*) FROM sys_user_role WHERE user_id = ? AND role_code = 'PARENT'",
                Integer.class, parentUserId);
        if (hasRole == null || hasRole == 0) {
            jdbc.update("INSERT INTO sys_user_role (user_id, role_code, org_id) VALUES (?, 'PARENT', NULL)", parentUserId);
        }
        messageService.notify(List.of(parentUserId), 1, "孩子申请与你关联",
                "<p>" + ctx.getShowName() + " 申请与你建立家长关联，请确认。</p>", 1);

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("id", id);
        res.put("status", 0);
        res.put("statusText", "待家长确认");
        return res;
    }

    // ------------------------------------------------------------ 查询

    public Map<String, Object> search(Map<String, Object> body, UserContext ctx) {
        int pageNum = intOf(body.get("pageNum"), 1);
        int pageSize = intOf(body.get("pageSize"), 20);
        @SuppressWarnings("unchecked")
        Map<String, Object> s = body.get("searchModel") instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();

        // 我作为家长看我的申请，或我作为学生看与我相关的申请
        StringBuilder where = new StringBuilder(" WHERE r.id > 0");
        List<Object> args = new ArrayList<>();
        if (ctx.getEmployeeId() != null && ctx.hasRole("PARENT")) {
            where.append(" AND (r.parent_user_id = ? OR r.employee_id = ?)");
            args.add(ctx.getUserId());
            args.add(ctx.getEmployeeId());
        } else if (ctx.getEmployeeId() != null) {
            where.append(" AND r.employee_id = ?");
            args.add(ctx.getEmployeeId());
        } else {
            where.append(" AND r.parent_user_id = ?");
            args.add(ctx.getUserId());
        }
        if (s.get("status") != null) {
            where.append(" AND r.status = ?");
            args.add(intOf(s.get("status"), 0));
        }
        if (s.get("applyFrom") != null) {
            where.append(" AND r.apply_from = ?");
            args.add(str(s.get("applyFrom")));
        }
        Integer total = jdbc.queryForObject("SELECT COUNT(*) FROM rel_relative_link r" + where, Integer.class, args.toArray());
        List<Object> a = new ArrayList<>(args);
        a.add(Math.min(Math.max(pageSize, 1), 100));
        a.add((Math.max(pageNum, 1) - 1) * pageSize);

        List<Map<String, Object>> list = jdbc.query(
                "SELECT r.id, r.apply_from AS applyFrom, r.status, r.remark, r.apply_time AS applyTime, "
                        + "r.handle_time AS handleTime, r.parent_user_id AS parentUserId, "
                        + "r.employee_id AS employeeId, e.name AS studentName, e.employee_no AS studentNo, "
                        + "u.show_name AS parentName, u.mobile AS parentMobile, ou.name AS className "
                        + "FROM rel_relative_link r "
                        + "JOIN org_employee e ON e.id = r.employee_id "
                        + "JOIN sys_user u ON u.id = r.parent_user_id "
                        + "LEFT JOIN org_employee_unit eu ON eu.employee_id = e.id "
                        + "LEFT JOIN org_unit ou ON ou.id = eu.org_id " + where
                        + " ORDER BY r.status = 0 DESC, r.apply_time DESC LIMIT ? OFFSET ?",
                (rs, i) -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", rs.getLong("id"));
                    m.put("applyFrom", rs.getString("applyFrom"));
                    int st = rs.getInt("status");
                    m.put("status", st);
                    m.put("statusText", switch (st) {
                        case 0 -> "待确认";
                        case 1 -> "已关联";
                        case 2 -> "已拒绝";
                        default -> "已解除";
                    });
                    m.put("remark", rs.getString("remark"));
                    m.put("applyTime", rs.getObject("applyTime", LocalDateTime.class));
                    m.put("handleTime", rs.getObject("handleTime", LocalDateTime.class));
                    m.put("parentUserId", rs.getLong("parentUserId"));
                    m.put("employeeId", rs.getLong("employeeId"));
                    m.put("studentName", maskName(rs.getString("studentName")));
                    m.put("studentNo", rs.getString("studentNo"));
                    m.put("parentName", maskName(rs.getString("parentName")));
                    m.put("parentMobile", maskMobile(rs.getString("parentMobile")));
                    m.put("className", rs.getString("className"));
                    m.put("mine", "PARENT".equals(rs.getString("applyFrom"))
                            ? rs.getLong("parentUserId") == ctx.getUserId()
                            : rs.getLong("employeeId") == (ctx.getEmployeeId() == null ? -1 : ctx.getEmployeeId()));
                    return m;
                }, a.toArray());

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("pageNum", pageNum);
        res.put("pageSize", pageSize);
        res.put("total", total == null ? 0 : total);
        res.put("list", list);
        return res;
    }

    public List<Map<String, Object>> children(UserContext ctx) {
        return jdbc.query("SELECT e.id AS employeeId, e.name, e.employee_no AS employeeNo, "
                        + "ou.name AS className, r.id AS linkId "
                        + "FROM rel_relative_link r JOIN org_employee e ON e.id = r.employee_id "
                        + "LEFT JOIN org_employee_unit eu ON eu.employee_id = e.id "
                        + "LEFT JOIN org_unit ou ON ou.id = eu.org_id "
                        + "WHERE r.parent_user_id = ? AND r.status = 1 ORDER BY r.id",
                (rs, i) -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("employeeId", rs.getLong("employeeId"));
                    m.put("name", rs.getString("name"));
                    m.put("employeeNo", rs.getString("employeeNo"));
                    m.put("className", rs.getString("className"));
                    m.put("linkId", rs.getLong("linkId"));
                    return m;
                }, ctx.getUserId());
    }

    public int pendingCount(UserContext ctx) {
        Integer c = jdbc.queryForObject("SELECT COUNT(*) FROM rel_relative_link r "
                        + "WHERE r.status = 0 AND ("
                        + " (r.apply_from = 'PARENT' AND r.employee_id = ?) "
                        + " OR (r.apply_from = 'STUDENT' AND r.parent_user_id = ?))",
                Integer.class, ctx.getEmployeeId() == null ? -1L : ctx.getEmployeeId(), ctx.getUserId());
        return c == null ? 0 : c;
    }

    // ------------------------------------------------------------ 同意 / 拒绝 / 解除

    @Transactional
    public Map<String, Object> agree(long id, UserContext ctx) {
        return handle(id, ctx, 1);
    }

    @Transactional
    public Map<String, Object> reject(long id, Map<String, Object> body, UserContext ctx) {
        Map<String, Object> res = handle(id, ctx, 2);
        if (str(body.get("remark")) != null) {
            jdbc.update("UPDATE rel_relative_link SET remark = ? WHERE id = ?", str(body.get("remark")), id);
        }
        return res;
    }

    private Map<String, Object> handle(long id, UserContext ctx, int targetStatus) {
        Map<String, Object> link = one("SELECT * FROM rel_relative_link WHERE id = ?", id);
        if (link == null) throw BizException.notFound("关联申请不存在");
        if (((Number) link.get("status")).intValue() != 0) {
            throw BizException.badRequest("该申请已处理");
        }
        long parentUserId = ((Number) link.get("parent_user_id")).longValue();
        long employeeId = ((Number) link.get("employee_id")).longValue();
        String applyFrom = (String) link.get("apply_from");

        boolean iAmParent = parentUserId == ctx.getUserId();
        boolean iAmStudent = ctx.getEmployeeId() != null && ctx.getEmployeeId() == employeeId;
        if ("PARENT".equals(applyFrom) ? !iAmStudent : !iAmParent) {
            throw BizException.forbidden("只能由被申请方确认");
        }
        jdbc.update("UPDATE rel_relative_link SET status = ?, handle_time = NOW(), handle_user_id = ? WHERE id = ?",
                targetStatus, ctx.getUserId(), id);

        Long otherUserId = "PARENT".equals(applyFrom)
                ? parentUserId
                : jdbc.query("SELECT user_id FROM org_employee WHERE id = ?", rs -> rs.next() ? rs.getLong(1) : null, employeeId);
        if (otherUserId != null) {
            messageService.notify(List.of(otherUserId), 1,
                    targetStatus == 1 ? "家长关联已确认" : "家长关联被拒绝",
                    targetStatus == 1
                            ? "<p>关联已建立，你现在可以看到对方的成长记录。</p>"
                            : "<p>对方拒绝了关联申请。</p>", targetStatus == 1 ? 0 : 1);
        }
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("id", id);
        res.put("status", targetStatus);
        res.put("statusText", targetStatus == 1 ? "已关联" : "已拒绝");
        return res;
    }

    @Transactional
    public Map<String, Object> unbind(long id, UserContext ctx) {
        Map<String, Object> link = one("SELECT * FROM rel_relative_link WHERE id = ?", id);
        if (link == null) throw BizException.notFound("关联不存在");
        if (((Number) link.get("status")).intValue() != 1) {
            throw BizException.badRequest("当前不是已关联状态");
        }
        long parentUserId = ((Number) link.get("parent_user_id")).longValue();
        long employeeId = ((Number) link.get("employee_id")).longValue();
        boolean iAmParent = parentUserId == ctx.getUserId();
        boolean iAmStudent = ctx.getEmployeeId() != null && ctx.getEmployeeId() == employeeId;
        if (!iAmParent && !iAmStudent) throw BizException.forbidden("无权解除该关联");

        jdbc.update("UPDATE rel_relative_link SET status = 3, handle_time = NOW(), handle_user_id = ? WHERE id = ?",
                ctx.getUserId(), id);
        Long otherUserId = iAmParent
                ? jdbc.query("SELECT user_id FROM org_employee WHERE id = ?", rs -> rs.next() ? rs.getLong(1) : null, employeeId)
                : parentUserId;
        if (otherUserId != null) {
            messageService.notify(List.of(otherUserId), 1, "家长关联已解除",
                    "<p>对方已解除关联，之后将看不到相关成长记录。</p>", 1);
        }
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("id", id);
        res.put("status", 3);
        res.put("statusText", "已解除");
        return res;
    }

    // ------------------------------------------------------------ 工具

    private Map<String, Object> one(String sql, Object... args) {
        List<Map<String, Object>> list = jdbc.query(sql, (rs, i) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            var md = rs.getMetaData();
            for (int c = 1; c <= md.getColumnCount(); c++) {
                m.put(md.getColumnLabel(c), rs.getObject(c));
            }
            return m;
        }, args);
        return list.isEmpty() ? null : list.get(0);
    }

    private static String maskName(String name) {
        if (name == null || name.length() <= 1) return name;
        return name.charAt(0) + "*".repeat(name.length() - 1);
    }

    private static String maskMobile(String mobile) {
        if (mobile == null || mobile.length() < 7) return mobile;
        return mobile.substring(0, 3) + "****" + mobile.substring(mobile.length() - 4);
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static int intOf(Object o, int def) {
        if (o == null) return def;
        if (o instanceof Number n) return n.intValue();
        try { return Integer.parseInt(String.valueOf(o)); } catch (NumberFormatException e) { return def; }
    }
}
