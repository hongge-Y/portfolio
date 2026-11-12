package com.campus.portfolio.service;

import com.campus.portfolio.common.BizException;
import com.campus.portfolio.common.UserContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 奖学金：计划 → 学生申请 → 辅导员/院系/学生处三级审批 → 排名 → 公示 → 发放。
 * 计分：total = gpa * gpaWeight + 红榜数 * redWeight − 黑榜数 * blackWeight（权重来自 plan.rule_json）
 */
@Service
public class ScholarshipService {

    private final JdbcTemplate jdbc;
    private final ContentService contentService;
    private final MessageService messageService;

    public ScholarshipService(JdbcTemplate jdbc, ContentService contentService, MessageService messageService) {
        this.jdbc = jdbc;
        this.contentService = contentService;
        this.messageService = messageService;
    }

    // ------------------------------------------------------------ 计划

    public List<Map<String, Object>> availablePlans(UserContext ctx) {
        List<Long> scope = contentService.orgScope(ctx);
        if (ctx.getClassOrgId() == null || scope.isEmpty()) return List.of();
        String in = String.join(",", Collections.nCopies(scope.size(), "?"));
        List<Object> args = new ArrayList<>(scope);
        return jdbc.query("SELECT p.id, p.name, p.term, p.quota, p.amount, p.apply_start AS applyStart, "
                        + "p.apply_end AS applyEnd, p.status, p.rule_json AS ruleJson, p.org_id AS orgId, "
                        + "CASE WHEN NOW() BETWEEN p.apply_start AND p.apply_end AND p.status = 1 THEN 1 ELSE 0 END AS canApply, "
                        + "(SELECT COUNT(*) FROM sch_apply a WHERE a.plan_id = p.id AND a.employee_id = ?) AS applied "
                        + "FROM sch_plan p WHERE p.deleted = 0 AND p.status IN (1,2,3) AND p.org_id IN (" + in + ") "
                        + "ORDER BY p.status, p.apply_end DESC",
                (rs, i) -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", rs.getLong("id"));
                    m.put("name", rs.getString("name"));
                    m.put("term", rs.getString("term"));
                    m.put("quota", rs.getInt("quota"));
                    m.put("amount", rs.getBigDecimal("amount"));
                    m.put("applyStart", rs.getObject("applyStart", LocalDateTime.class));
                    m.put("applyEnd", rs.getObject("applyEnd", LocalDateTime.class));
                    m.put("status", rs.getInt("status"));
                    m.put("statusText", planStatusText(rs.getInt("status")));
                    m.put("canApply", rs.getInt("canApply") == 1);
                    m.put("applied", rs.getInt("applied") > 0);
                    return m;
                }, concat(ctx.getEmployeeId(), args).toArray());
    }

    public long savePlan(Map<String, Object> body) {
        String name = str(body.get("name"));
        if (name == null || name.isBlank()) throw BizException.badRequest("计划名称不能为空");
        Long orgId = longOf(body.get("orgId"));
        if (orgId == null) throw BizException.badRequest("orgId 必填");
        Long id = longOf(body.get("id"));
        String start = str(body.get("applyStart"));
        String end = str(body.get("applyEnd"));
        if (id == null) {
            jdbc.update("INSERT INTO sch_plan (name, org_id, term, quota, amount, apply_start, apply_end, rule_json, status) "
                            + "VALUES (?,?,?,?,?,?,?,?,?)",
                    name, orgId, str(body.get("term")), intOf(body.get("quota"), 1),
                    body.get("amount"), start, end,
                    body.get("ruleJson") == null ? null : String.valueOf(body.get("ruleJson")),
                    intOf(body.get("status"), 0));
            return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        }
        jdbc.update("UPDATE sch_plan SET name=?, org_id=?, term=?, quota=?, amount=?, apply_start=?, apply_end=?, "
                        + "rule_json=?, status=? WHERE id=?",
                name, orgId, str(body.get("term")), intOf(body.get("quota"), 1), body.get("amount"),
                start, end, body.get("ruleJson") == null ? null : String.valueOf(body.get("ruleJson")),
                intOf(body.get("status"), 0), id);
        return id;
    }

    public Map<String, Object> planSearch(Map<String, Object> body) {
        int pageNum = intOf(body.get("pageNum"), 1);
        int pageSize = intOf(body.get("pageSize"), 10);
        Integer total = jdbc.queryForObject("SELECT COUNT(*) FROM sch_plan WHERE deleted = 0", Integer.class);
        List<Map<String, Object>> list = jdbc.query(
                "SELECT p.id, p.name, p.term, p.quota, p.amount, p.status, p.org_id AS orgId, ou.name AS orgName, "
                        + "p.apply_start AS applyStart, p.apply_end AS applyEnd, "
                        + "(SELECT COUNT(*) FROM sch_apply a WHERE a.plan_id = p.id) AS applyCount "
                        + "FROM sch_plan p LEFT JOIN org_unit ou ON ou.id = p.org_id "
                        + "WHERE p.deleted = 0 ORDER BY p.id DESC LIMIT ? OFFSET ?",
                (rs, i) -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", rs.getLong("id"));
                    m.put("name", rs.getString("name"));
                    m.put("term", rs.getString("term"));
                    m.put("quota", rs.getInt("quota"));
                    m.put("amount", rs.getBigDecimal("amount"));
                    m.put("status", rs.getInt("status"));
                    m.put("statusText", planStatusText(rs.getInt("status")));
                    m.put("orgName", rs.getString("orgName"));
                    m.put("applyStart", rs.getObject("applyStart", LocalDateTime.class));
                    m.put("applyEnd", rs.getObject("applyEnd", LocalDateTime.class));
                    m.put("applyCount", rs.getInt("applyCount"));
                    return m;
                }, Math.min(pageSize, 100), (Math.max(pageNum, 1) - 1) * pageSize);

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("pageNum", pageNum);
        res.put("pageSize", pageSize);
        res.put("total", total == null ? 0 : total);
        res.put("list", list);
        return res;
    }

    public Map<String, Object> planDetail(long id) {
        Map<String, Object> m = one("SELECT p.id, p.name, p.term, p.quota, p.amount, p.status, p.org_id AS orgId, "
                + "p.apply_start AS applyStart, p.apply_end AS applyEnd, p.rule_json AS ruleJson, ou.name AS orgName "
                + "FROM sch_plan p LEFT JOIN org_unit ou ON ou.id = p.org_id WHERE p.id = ? AND p.deleted = 0", id);
        if (m == null) throw BizException.notFound("奖学金计划不存在");
        m.put("statusText", planStatusText(((Number) m.get("status")).intValue()));
        return m;
    }

    // ------------------------------------------------------------ 申请

    @Transactional
    public Map<String, Object> apply(Map<String, Object> body, UserContext ctx) {
        if (ctx.getEmployeeId() == null) throw BizException.badRequest("只有学生可以申请奖学金");
        Long planId = longOf(body.get("planId"));
        if (planId == null) throw BizException.badRequest("planId 必填");

        Map<String, Object> plan = one("SELECT * FROM sch_plan WHERE id = ? AND deleted = 0", planId);
        if (plan == null) throw BizException.notFound("计划不存在");
        if (((Number) plan.get("status")).intValue() != 1) {
            throw BizException.badRequest("SCH_PLAN_CLOSED", "该计划当前不在报名阶段");
        }
        Integer inWindow = jdbc.queryForObject(
                "SELECT COUNT(*) FROM sch_plan WHERE id = ? AND NOW() BETWEEN apply_start AND apply_end",
                Integer.class, planId);
        if (inWindow == null || inWindow == 0) {
            throw BizException.badRequest("SCH_PLAN_CLOSED", "不在报名时间范围内");
        }
        Integer exist = jdbc.queryForObject("SELECT COUNT(*) FROM sch_apply WHERE plan_id = ? AND employee_id = ?",
                Integer.class, planId, ctx.getEmployeeId());
        if (exist != null && exist > 0) throw BizException.badRequest("你已经提交过该计划的申请");

        BigDecimal gpa = decimalOf(body.get("gpa"));
        if (gpa == null || gpa.compareTo(BigDecimal.ZERO) < 0 || gpa.compareTo(new BigDecimal("5")) > 0) {
            throw BizException.badRequest("gpa 需在 0~5 之间");
        }
        Map<String, Double> weights = weights(plan);
        double gpaW = weights.get("gpaWeight");
        double redW = weights.get("redWeight");
        double blackW = weights.get("blackWeight");

        Integer red = jdbc.queryForObject("SELECT COUNT(*) FROM pub_rank WHERE employee_id = ? AND red = 1 AND status = 1 AND deleted = 0",
                Integer.class, ctx.getEmployeeId());
        Integer black = jdbc.queryForObject("SELECT COUNT(*) FROM pub_rank WHERE employee_id = ? AND red = 0 AND status = 1 AND deleted = 0",
                Integer.class, ctx.getEmployeeId());
        double extra = (red == null ? 0 : red) * redW - (black == null ? 0 : black) * blackW;
        BigDecimal extraScore = BigDecimal.valueOf(extra).setScale(2, RoundingMode.HALF_UP);
        BigDecimal total = gpa.multiply(BigDecimal.valueOf(gpaW)).add(extraScore).setScale(2, RoundingMode.HALF_UP);

        jdbc.update("INSERT INTO sch_apply (plan_id, employee_id, class_org_id, gpa, extra_score, total_score, "
                        + "materials, statement, status, current_step) VALUES (?,?,?,?,?,?,?,?,10,1)",
                planId, ctx.getEmployeeId(), ctx.getClassOrgId(), gpa, extraScore, total,
                body.get("materials") == null ? null : String.valueOf(body.get("materials")),
                str(body.get("statement")));
        Long applyId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);

        // 建立三级审批节点
        Long counselor = ctx.getClassOrgId() == null ? null
                : jdbc.query("SELECT counselor_id FROM org_class WHERE org_id = ?",
                rs -> rs.next() && rs.getObject(1) != null ? ((Number) rs.getObject(1)).longValue() : null,
                ctx.getClassOrgId());
        insertNode(applyId, 1, "COUNSELOR", counselor);
        Long secretary = jdbc.query("SELECT e.id FROM sys_user_role r JOIN org_employee e ON e.user_id = r.user_id "
                + "WHERE r.role_code = 'SECRETARY' AND e.deleted = 0 LIMIT 1",
                rs -> rs.next() ? rs.getLong(1) : null);
        insertNode(applyId, 2, "SECRETARY", secretary);
        insertNode(applyId, 3, "ADMIN", null);

        if (counselor != null) {
            messageService.notifyEmployee(counselor, "新的奖学金申请",
                    "<p>" + ctx.getShowName() + " 提交了奖学金申请，请及时审批。</p>", 1);
        }
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("id", applyId);
        res.put("gpa", gpa);
        res.put("extraScore", extraScore);
        res.put("totalScore", total);
        res.put("status", 10);
        res.put("statusText", "待辅导员审批");
        return res;
    }

    private void insertNode(Long applyId, int step, String role, Long approverId) {
        jdbc.update("INSERT INTO sch_approve (apply_id, step, approver_id, result) VALUES (?,?,?,0)",
                applyId, step, approverId);
    }

    public Map<String, Object> myApplies(UserContext ctx, int pageNum, int pageSize) {
        if (ctx.getEmployeeId() == null) return emptyPage(pageNum, pageSize);
        Integer total = jdbc.queryForObject("SELECT COUNT(*) FROM sch_apply WHERE employee_id = ?",
                Integer.class, ctx.getEmployeeId());
        List<Map<String, Object>> list = jdbc.query(
                "SELECT a.id, a.plan_id AS planId, p.name AS planName, p.amount, a.gpa, a.extra_score AS extraScore, "
                        + "a.total_score AS totalScore, a.status, a.rank_no AS rankNo, a.create_time AS createTime, "
                        + "a.statement, a.materials "
                        + "FROM sch_apply a JOIN sch_plan p ON p.id = a.plan_id "
                        + "WHERE a.employee_id = ? ORDER BY a.id DESC LIMIT ? OFFSET ?",
                (rs, i) -> applyRow(rs), ctx.getEmployeeId(), Math.min(pageSize, 100),
                (Math.max(pageNum, 1) - 1) * pageSize);
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("pageNum", pageNum);
        res.put("pageSize", pageSize);
        res.put("total", total == null ? 0 : total);
        res.put("list", list);
        return res;
    }

    /** 目标班级是否落在当前用户的 orgPath 前缀内（行级数据范围） */
    private boolean inMyScope(UserContext ctx, Long classOrgId) {
        String myPath = ctx.getOrgPath();
        if (myPath == null || myPath.isBlank() || classOrgId == null) return false;
        java.util.List<String> rows = jdbc.query("SELECT path FROM org_unit WHERE id = ? AND deleted = 0",
                (rs, i) -> rs.getString(1), classOrgId);
        return !rows.isEmpty() && rows.get(0) != null && rows.get(0).startsWith(myPath);
    }

    public Map<String, Object> applyDetail(long id, UserContext ctx) {
        Map<String, Object> m = one("SELECT a.*, p.name AS planName, p.amount, p.quota, e.name AS employeeName, "
                + "e.employee_no AS employeeNo, ou.name AS className "
                + "FROM sch_apply a JOIN sch_plan p ON p.id = a.plan_id "
                + "JOIN org_employee e ON e.id = a.employee_id "
                + "LEFT JOIN org_unit ou ON ou.id = a.class_org_id WHERE a.id = ?", id);
        if (m == null) throw BizException.notFound("申请不存在");
        long employeeId = ((Number) m.get("employee_id")).longValue();
        boolean owner = ctx.getEmployeeId() != null && ctx.getEmployeeId() == employeeId;
        // 与请假详情同一套行级范围：管理员全校，辅导员/书记只在自己 orgPath 前缀内
        boolean staff = false;
        if (ctx.hasRole("ADMIN")) {
            staff = true;
        } else if (ctx.hasRole("COUNSELOR") || ctx.hasRole("SECRETARY")) {
            Object cls = m.get("class_org_id");
            staff = inMyScope(ctx, cls == null ? null : ((Number) cls).longValue());
        }
        boolean approver = jdbc.queryForObject("SELECT COUNT(*) FROM sch_approve WHERE apply_id = ? AND approver_id = ?",
                Integer.class, id, ctx.getEmployeeId() == null ? -1L : ctx.getEmployeeId()) > 0;
        if (!owner && !staff && !approver) throw BizException.forbidden("无权查看该申请");

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("id", ((Number) m.get("id")).longValue());
        res.put("planId", ((Number) m.get("plan_id")).longValue());
        res.put("planName", m.get("planName"));
        res.put("employeeName", m.get("employeeName"));
        res.put("employeeNo", m.get("employeeNo"));
        res.put("className", m.get("className"));
        res.put("gpa", m.get("gpa"));
        res.put("extraScore", m.get("extra_score"));
        res.put("totalScore", m.get("total_score"));
        res.put("statement", m.get("statement"));
        res.put("materials", m.get("materials"));
        res.put("rankNo", m.get("rank_no"));
        int st = ((Number) m.get("status")).intValue();
        res.put("status", st);
        res.put("statusText", applyStatusText(st));
        res.put("approvals", jdbc.query("SELECT step, approver_id AS approverId, result, reason, "
                        + "handle_time AS handleTime FROM sch_approve WHERE apply_id = ? ORDER BY step, id",
                (rs, i) -> {
                    Map<String, Object> n = new LinkedHashMap<>();
                    n.put("step", rs.getInt("step"));
                    n.put("approverId", rs.getObject("approverId"));
                    n.put("result", rs.getInt("result"));
                    n.put("resultText", rs.getInt("result") == 1 ? "通过" : rs.getInt("result") == 2 ? "驳回" : "待处理");
                    n.put("reason", rs.getString("reason"));
                    n.put("handleTime", rs.getObject("handleTime", LocalDateTime.class));
                    return n;
                }, id));
        return res;
    }

    @Transactional
    public void cancelApply(long id, UserContext ctx) {
        Map<String, Object> m = one("SELECT employee_id, status FROM sch_apply WHERE id = ?", id);
        if (m == null) throw BizException.notFound("申请不存在");
        if (ctx.getEmployeeId() == null || ((Number) m.get("employee_id")).longValue() != ctx.getEmployeeId()) {
            throw BizException.forbidden("只能撤回自己的申请");
        }
        int st = ((Number) m.get("status")).intValue();
        if (st != 10) throw BizException.badRequest("只有待审批状态可以撤回");
        jdbc.update("DELETE FROM sch_apply WHERE id = ?", id);
    }

    // ------------------------------------------------------------ 审批

    public Map<String, Object> approvePage(Map<String, Object> body, UserContext ctx) {
        int pageNum = intOf(body.get("pageNum"), 1);
        int pageSize = intOf(body.get("pageSize"), 20);
        boolean admin = ctx.hasRole("ADMIN");
        StringBuilder where = new StringBuilder(" FROM sch_apply a JOIN sch_plan p ON p.id = a.plan_id "
                + "JOIN org_employee e ON e.id = a.employee_id WHERE a.status IN (10,20,30) ");
        List<Object> args = new ArrayList<>();
        if (admin) {
            where.append(" AND EXISTS (SELECT 1 FROM sch_approve s WHERE s.apply_id = a.id AND s.step = a.current_step "
                    + "AND s.result = 0 AND s.approver_id IS NULL) ");
        } else {
            where.append(" AND EXISTS (SELECT 1 FROM sch_approve s WHERE s.apply_id = a.id AND s.step = a.current_step "
                    + "AND s.result = 0 AND s.approver_id = ?) ");
            args.add(ctx.getEmployeeId() == null ? -1L : ctx.getEmployeeId());
        }
        Integer total = jdbc.queryForObject("SELECT COUNT(*)" + where, Integer.class, args.toArray());
        List<Object> a = new ArrayList<>(args);
        a.add(Math.min(pageSize, 100));
        a.add((Math.max(pageNum, 1) - 1) * pageSize);
        List<Map<String, Object>> list = jdbc.query(
                "SELECT a.id, a.plan_id AS planId, p.name AS planName, e.name AS employeeName, e.employee_no AS employeeNo, "
                        + "a.gpa, a.extra_score AS extraScore, a.total_score AS totalScore, a.status, a.current_step AS currentStep, "
                        + "a.statement" + where + " ORDER BY a.total_score DESC, a.id LIMIT ? OFFSET ?",
                (rs, i) -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", rs.getLong("id"));
                    m.put("planId", rs.getLong("planId"));
                    m.put("planName", rs.getString("planName"));
                    m.put("employeeName", rs.getString("employeeName"));
                    m.put("employeeNo", rs.getString("employeeNo"));
                    m.put("gpa", rs.getBigDecimal("gpa"));
                    m.put("extraScore", rs.getBigDecimal("extraScore"));
                    m.put("totalScore", rs.getBigDecimal("totalScore"));
                    m.put("status", rs.getInt("status"));
                    m.put("statusText", applyStatusText(rs.getInt("status")));
                    m.put("currentStep", rs.getInt("currentStep"));
                    m.put("statement", rs.getString("statement"));
                    return m;
                }, a.toArray());
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("pageNum", pageNum);
        res.put("pageSize", pageSize);
        res.put("total", total == null ? 0 : total);
        res.put("list", list);
        return res;
    }

    @Transactional
    public Map<String, Object> approve(Map<String, Object> body, UserContext ctx) {
        Long applyId = longOf(body.get("applyId"));
        if (applyId == null) throw BizException.badRequest("applyId 必填");
        int result = intOf(body.get("result"), 0);
        if (result != 1 && result != 2) throw BizException.badRequest("result 只能是 1(通过) 或 2(驳回)");

        Map<String, Object> apply = one("SELECT * FROM sch_apply WHERE id = ?", applyId);
        if (apply == null) throw BizException.notFound("申请不存在");
        int status = ((Number) apply.get("status")).intValue();
        if (status != 10 && status != 20 && status != 30) throw BizException.badRequest("该申请当前不可审批");
        int step = ((Number) apply.get("current_step")).intValue();

        Map<String, Object> node = one("SELECT * FROM sch_approve WHERE apply_id = ? AND step = ? AND result = 0",
                applyId, step);
        if (node == null) throw BizException.badRequest("没有待处理审批节点");
        Object nodeApprover = node.get("approver_id");
        if (nodeApprover == null) {
            if (!ctx.hasRole("ADMIN")) throw BizException.forbidden("该节点需学生处（管理员）审批");
        } else if (ctx.getEmployeeId() == null || ((Number) nodeApprover).longValue() != ctx.getEmployeeId()) {
            throw BizException.forbidden("当前待审批人不是你");
        }
        if (result == 2 && (str(body.get("reason")) == null || str(body.get("reason")).isBlank())) {
            throw BizException.badRequest("驳回必须填写原因");
        }
        jdbc.update("UPDATE sch_approve SET result = ?, reason = ?, handle_time = NOW() WHERE id = ?",
                result, str(body.get("reason")), node.get("id"));

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("applyId", applyId);
        if (result == 2) {
            jdbc.update("UPDATE sch_apply SET status = 50 WHERE id = ?", applyId);
            res.put("status", 50);
            res.put("statusText", "已驳回");
            messageService.notifyEmployee(((Number) apply.get("employee_id")).longValue(), "奖学金申请被驳回",
                    "<p>原因：" + str(body.get("reason")) + "</p>", 2);
        } else {
            Integer nextStep = jdbc.query("SELECT MIN(step) FROM sch_approve WHERE apply_id = ? AND result = 0 AND step > ?",
                    rs -> rs.next() ? (Integer) rs.getObject(1) : null, applyId, step);
            if (nextStep == null) {
                jdbc.update("UPDATE sch_apply SET status = 40 WHERE id = ?", applyId);
                res.put("status", 40);
                res.put("statusText", "审批通过");
                messageService.notifyEmployee(((Number) apply.get("employee_id")).longValue(), "奖学金申请已通过",
                        "<p>你的奖学金申请已全部审批通过，等待公示。</p>", 0);
            } else {
                int newStatus = nextStep == 2 ? 20 : 30;
                jdbc.update("UPDATE sch_apply SET status = ?, current_step = ? WHERE id = ?", newStatus, nextStep, applyId);
                res.put("status", newStatus);
                res.put("statusText", nextStep == 2 ? "待院系审批" : "待学生处审批");
                Long nextApprover = jdbc.query("SELECT approver_id FROM sch_approve WHERE apply_id = ? AND step = ?",
                        rs -> rs.next() && rs.getObject(1) != null ? ((Number) rs.getObject(1)).longValue() : null,
                        applyId, nextStep);
                if (nextApprover != null) {
                    messageService.notifyEmployee(nextApprover, "待审批奖学金申请",
                            "<p>有一条奖学金申请待你审批。</p>", 1);
                }
            }
        }
        return res;
    }

    // ------------------------------------------------------------ 排名 / 公示 / 发放

    @Transactional
    public Map<String, Object> rank(long planId, Long quotaOverride) {
        Map<String, Object> plan = one("SELECT * FROM sch_plan WHERE id = ? AND deleted = 0", planId);
        if (plan == null) throw BizException.notFound("计划不存在");
        int quota = quotaOverride == null ? ((Number) plan.get("quota")).intValue() : quotaOverride.intValue();

        List<Map<String, Object>> list = jdbc.query(
                "SELECT a.id, e.name AS employeeName, e.employee_no AS employeeNo, a.gpa, a.extra_score AS extraScore, "
                        + "a.total_score AS totalScore FROM sch_apply a JOIN org_employee e ON e.id = a.employee_id "
                        + "WHERE a.plan_id = ? AND a.status IN (30,40,60,70) "
                        + "ORDER BY a.total_score DESC, a.gpa DESC, a.create_time ASC, a.id", (rs, i) -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("applyId", rs.getLong("id"));
                    m.put("employeeName", rs.getString("employeeName"));
                    m.put("employeeNo", rs.getString("employeeNo"));
                    m.put("gpa", rs.getBigDecimal("gpa"));
                    m.put("extraScore", rs.getBigDecimal("extraScore"));
                    m.put("totalScore", rs.getBigDecimal("totalScore"));
                    m.put("rankNo", i + 1);
                    m.put("inQuota", i < quota);
                    return m;
                }, planId);

        for (Map<String, Object> m : list) {
            jdbc.update("UPDATE sch_apply SET rank_no = ? WHERE id = ?", m.get("rankNo"), m.get("applyId"));
        }
        jdbc.update("UPDATE sch_plan SET status = 2 WHERE id = ?", planId);
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("planId", planId);
        res.put("quota", quota);
        res.put("list", list);
        return res;
    }

    @Transactional
    public Map<String, Object> publicity(long planId, Map<String, Object> body) {
        Map<String, Object> plan = one("SELECT * FROM sch_plan WHERE id = ? AND deleted = 0", planId);
        if (plan == null) throw BizException.notFound("计划不存在");
        Integer ranked = jdbc.queryForObject("SELECT COUNT(*) FROM sch_apply WHERE plan_id = ? AND rank_no IS NOT NULL",
                Integer.class, planId);
        if (ranked == null || ranked == 0) throw BizException.badRequest("请先生成排名");
        int quota = ((Number) plan.get("quota")).intValue();

        String start = str(body.get("startTime"));
        String end = str(body.get("endTime"));
        if (start == null) start = LocalDateTime.now().toString().replace('T', ' ');
        if (end == null) end = LocalDateTime.now().plusDays(3).toString().replace('T', ' ');

        List<Map<String, Object>> winners = jdbc.query(
                "SELECT a.id AS applyId, a.rank_no AS rankNo, e.name AS employeeName, e.employee_no AS employeeNo, "
                        + "a.total_score AS totalScore FROM sch_apply a JOIN org_employee e ON e.id = a.employee_id "
                        + "WHERE a.plan_id = ? AND a.rank_no <= ? ORDER BY a.rank_no", (rs, i) -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("applyId", rs.getLong("applyId"));
                    m.put("rankNo", rs.getInt("rankNo"));
                    m.put("employeeName", rs.getString("employeeName"));
                    m.put("employeeNo", rs.getString("employeeNo"));
                    m.put("totalScore", rs.getBigDecimal("totalScore"));
                    return m;
                }, planId, quota);

        jdbc.update("INSERT INTO sch_publicity (plan_id, start_time, end_time, result_json) VALUES (?,?,?,?)",
                planId, start, end, toJson(winners));
        for (Map<String, Object> w : winners) {
            jdbc.update("UPDATE sch_apply SET status = 60 WHERE id = ?", w.get("applyId"));
        }
        jdbc.update("UPDATE sch_plan SET status = 3 WHERE id = ?", planId);

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("planId", planId);
        res.put("startTime", start);
        res.put("endTime", end);
        res.put("winners", winners);
        return res;
    }

    @Transactional
    public Map<String, Object> grant(long planId) {
        int n = jdbc.update("UPDATE sch_apply SET status = 70 WHERE plan_id = ? AND status = 60", planId);
        jdbc.update("UPDATE sch_plan SET status = 4 WHERE id = ?", planId);
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("planId", planId);
        res.put("granted", n);
        return res;
    }

    public Map<String, Object> publicityList(Long planId) {
        Long pid = planId;
        if (pid == null) {
            pid = jdbc.query("SELECT plan_id FROM sch_publicity ORDER BY id DESC LIMIT 1",
                    rs -> rs.next() ? ((Number) rs.getObject(1)).longValue() : null);
            if (pid == null) {
                Map<String, Object> empty = new LinkedHashMap<>();
                empty.put("winners", List.of());
                return empty;
            }
        }
        Map<String, Object> p = one("SELECT * FROM sch_publicity WHERE plan_id = ? ORDER BY id DESC LIMIT 1", pid);
        if (p == null) throw BizException.notFound("该计划暂无公示");
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("planId", pid);
        res.put("startTime", p.get("start_time"));
        res.put("endTime", p.get("end_time"));
        res.put("winners", parseJson(String.valueOf(p.get("result_json"))));
        return res;
    }

    // ------------------------------------------------------------ 工具

    private Map<String, Object> applyRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", rs.getLong("id"));
        m.put("planId", rs.getLong("planId"));
        m.put("planName", rs.getString("planName"));
        m.put("amount", rs.getBigDecimal("amount"));
        m.put("gpa", rs.getBigDecimal("gpa"));
        m.put("extraScore", rs.getBigDecimal("extraScore"));
        m.put("totalScore", rs.getBigDecimal("totalScore"));
        int st = rs.getInt("status");
        m.put("status", st);
        m.put("statusText", applyStatusText(st));
        m.put("rankNo", rs.getObject("rankNo"));
        m.put("createTime", rs.getObject("createTime", LocalDateTime.class));
        return m;
    }

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

    private Map<String, Double> weights(Map<String, Object> plan) {
        Map<String, Double> w = new HashMap<>();
        w.put("gpaWeight", 6.0);
        w.put("redWeight", 2.0);
        w.put("blackWeight", 3.0);
        String json = str(plan.get("rule_json"));
        if (json != null) {
            // 极简解析，避免引入额外依赖：{"gpaWeight":6,"redWeight":2,"blackWeight":3}
            w.put("gpaWeight", findNumber(json, "gpaWeight", 6.0));
            w.put("redWeight", findNumber(json, "redWeight", 2.0));
            w.put("blackWeight", findNumber(json, "blackWeight", 3.0));
        }
        return w;
    }

    private static double findNumber(String json, String key, double def) {
        int i = json.indexOf('"' + key + '"');
        if (i < 0) return def;
        int colon = json.indexOf(':', i);
        if (colon < 0) return def;
        int end = colon + 1;
        while (end < json.length() && ",}\" ".indexOf(json.charAt(end)) < 0) end++;
        try {
            return Double.parseDouble(json.substring(colon + 1, end).trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private String toJson(List<Map<String, Object>> list) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            Map<String, Object> m = list.get(i);
            if (i > 0) sb.append(',');
            sb.append('{')
                    .append("\"rankNo\":").append(m.get("rankNo")).append(',')
                    .append("\"employeeName\":\"").append(m.get("employeeName")).append("\",")
                    .append("\"employeeNo\":\"").append(m.get("employeeNo")).append("\",")
                    .append("\"totalScore\":").append(m.get("totalScore"))
                    .append('}');
        }
        return sb.append(']').toString();
    }

    private List<Map<String, Object>> parseJson(String json) {
        List<Map<String, Object>> list = new ArrayList<>();
        if (json == null || json.length() < 3) return list;
        // 仅解析本类生成的固定结构
        String body = json.trim();
        body = body.substring(1, body.length() - 1);
        for (String item : body.split("\\},\\{")) {
            String it = item.replace("{", "").replace("}", "");
            Map<String, Object> m = new LinkedHashMap<>();
            for (String kv : it.split(",")) {
                String[] p = kv.split(":", 2);
                if (p.length < 2) continue;
                String k = p[0].replace("\"", "").trim();
                String v = p[1].replace("\"", "").trim();
                if ("rankNo".equals(k)) m.put(k, Integer.parseInt(v));
                else if ("totalScore".equals(k)) m.put(k, new BigDecimal(v));
                else m.put(k, v);
            }
            list.add(m);
        }
        return list;
    }

    private Map<String, Object> emptyPage(int pageNum, int pageSize) {
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("pageNum", pageNum);
        res.put("pageSize", pageSize);
        res.put("total", 0);
        res.put("list", List.of());
        return res;
    }

    private static List<Object> concat(Object first, List<Object> rest) {
        List<Object> all = new ArrayList<>();
        all.add(first);
        all.addAll(rest);
        return all;
    }

    public static String planStatusText(int s) {
        return switch (s) {
            case 0 -> "草稿";
            case 1 -> "报名中";
            case 2 -> "评审中";
            case 3 -> "公示中";
            default -> "已结束";
        };
    }

    public static String applyStatusText(int s) {
        return switch (s) {
            case 10 -> "待辅导员审批";
            case 20 -> "待院系审批";
            case 30 -> "待学生处审批";
            case 40 -> "审批通过";
            case 50 -> "已驳回";
            case 60 -> "公示中";
            case 70 -> "已发放";
            default -> "未知";
        };
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static BigDecimal decimalOf(Object o) {
        if (o == null || "".equals(o)) return null;
        try {
            return new BigDecimal(String.valueOf(o));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Long longOf(Object o) {
        if (o == null || "".equals(o)) return null;
        if (o instanceof Number n) return n.longValue();
        try { return Long.parseLong(String.valueOf(o)); } catch (NumberFormatException e) { return null; }
    }

    private static int intOf(Object o, int def) {
        if (o == null) return def;
        if (o instanceof Number n) return n.intValue();
        try { return Integer.parseInt(String.valueOf(o)); } catch (NumberFormatException e) { return def; }
    }
}
