package com.campus.portfolio.service;

import com.campus.portfolio.common.BizException;
import com.campus.portfolio.common.UserContext;
import com.campus.portfolio.dto.LeaveApplyDTO;
import com.campus.portfolio.dto.LeaveApproveDTO;
import com.campus.portfolio.dto.LeaveQueryDTO;
import com.campus.portfolio.entity.LeaveApprove;
import com.campus.portfolio.entity.LeaveAttachment;
import com.campus.portfolio.entity.LeaveRequest;
import com.campus.portfolio.mapper.LeaveApproveMapper;
import com.campus.portfolio.mapper.LeaveAttachmentMapper;
import com.campus.portfolio.mapper.LeaveRequestMapper;
import com.campus.portfolio.vo.LeaveVO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 请假闭环：提交 → 分级审批 → 销假 / 取消 / 改期。
 * 规则集中在本类，前端通过 GET /leave/rule 取同一份配置做预校验。
 */
@Service
public class LeaveService {

    private final JdbcTemplate jdbc;
    private final LeaveRequestMapper leaveMapper;
    private final LeaveApproveMapper approveMapper;
    private final LeaveAttachmentMapper attachMapper;
    private final MessageService messageService;

    @Value("${portfolio.leave.max-in-flight:3}")
    private int maxInFlight;
    @Value("${portfolio.leave.max-days:7}")
    private int maxDays;
    @Value("${portfolio.leave.secretary-threshold-days:3}")
    private int secretaryThreshold;

    public LeaveService(JdbcTemplate jdbc, LeaveRequestMapper leaveMapper,
                        LeaveApproveMapper approveMapper, LeaveAttachmentMapper attachMapper,
                        MessageService messageService) {
        this.jdbc = jdbc;
        this.leaveMapper = leaveMapper;
        this.approveMapper = approveMapper;
        this.attachMapper = attachMapper;
        this.messageService = messageService;
    }

    // ---------------------------------------------------------------- 规则

    public Map<String, Object> rule() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("maxInFlight", maxInFlight);
        m.put("maxDays", maxDays);
        m.put("secretaryThresholdDays", secretaryThreshold);
        m.put("types", List.of(Map.of("value", 1, "label", "病假"), Map.of("value", 2, "label", "事假")));
        List<Map<String, Object>> dict = new ArrayList<>();
        for (Map.Entry<Integer, String> e : statusText().entrySet()) {
            dict.add(Map.of("value", e.getKey(), "label", e.getValue()));
        }
        m.put("statusDict", dict);
        return m;
    }

    private Map<Integer, String> statusText() {
        Map<Integer, String> m = new LinkedHashMap<>();
        m.put(10, "待辅导员审批");
        m.put(11, "待书记审批");
        m.put(20, "已通过待销假");
        m.put(21, "已销假");
        m.put(30, "已驳回");
        m.put(40, "已取消");
        m.put(50, "超期未销假");
        return m;
    }

    // ---------------------------------------------------------------- 提交

    @Transactional
    public Map<String, Object> apply(LeaveApplyDTO dto, UserContext ctx) {
        if (ctx.getEmployeeId() == null) {
            throw BizException.badRequest("当前账号未绑定员工档案，无法请假");
        }
        if (!dto.getStartTime().isBefore(dto.getEndTime())) {
            throw BizException.badRequest("结束时间必须晚于开始时间");
        }
        BigDecimal days = calcDays(dto.getStartTime(), dto.getEndTime());
        if (days.doubleValue() > maxDays) {
            throw BizException.badRequest("LEAVE_TOO_LONG",
                    "请假 " + days + " 天，超过 " + maxDays + " 天上限，请前往一站式服务中心办理");
        }

        LeaveRequest editing = null;
        if (dto.getId() != null) {
            editing = requireOwn(dto.getId(), ctx);
            if (editing.getStatus() != 30) {
                throw BizException.badRequest("LEAVE_STATUS_INVALID", "只有被驳回的假条可以修改后重新提交");
            }
        }

        int inFlight = countInFlight(ctx.getEmployeeId(), dto.getId());
        if (inFlight >= maxInFlight) {
            throw BizException.badRequest("LEAVE_IN_FLIGHT_LIMIT",
                    "已有 " + inFlight + " 条请假在流程中（上限 " + maxInFlight + " 条）");
        }
        int conflicts = countConflict(ctx.getEmployeeId(), dto.getId(), dto.getStartTime(), dto.getEndTime());
        if (conflicts > 0) {
            throw BizException.badRequest("LEAVE_TIME_CONFLICT", "该时间段与已有请假重叠");
        }

        Long classOrgId = ctx.getClassOrgId();
        Long collegeOrgId = ctx.getCollegeOrgId();
        if (classOrgId == null) {
            throw BizException.badRequest("当前账号未归属班级，无法提交请假");
        }

        LeaveRequest entity = editing != null ? editing : new LeaveRequest();
        entity.setEmployeeId(ctx.getEmployeeId());
        entity.setClassOrgId(classOrgId);
        entity.setCollegeOrgId(collegeOrgId == null ? 1L : collegeOrgId);
        entity.setType(dto.getType());
        entity.setStartTime(dto.getStartTime());
        entity.setEndTime(dto.getEndTime());
        entity.setDays(days);
        entity.setReason(dto.getReason());
        entity.setStatus(10);
        entity.setCurrentStep(1);
        if (editing == null) {
            leaveMapper.insert(entity);
        } else {
            jdbc.update("UPDATE leave_request SET type = ?, start_time = ?, end_time = ?, days = ?, reason = ?, "
                            + "status = 10, current_step = 1, version = version + 1 WHERE id = ?",
                    entity.getType(), entity.getStartTime(), entity.getEndTime(), entity.getDays(),
                    entity.getReason(), entity.getId());
            jdbc.update("DELETE FROM leave_approve WHERE leave_id = ?", entity.getId());
        }

        List<Map<String, Object>> flow = buildFlow(classOrgId, days);
        for (int i = 0; i < flow.size(); i++) {
            Map<String, Object> f = flow.get(i);
            LeaveApprove node = new LeaveApprove();
            node.setLeaveId(entity.getId());
            node.setStep(i + 1);
            node.setApproverRole((String) f.get("role"));
            node.setApproverId((Long) f.get("approverId"));
            node.setApproverName((String) f.get("approverName"));
            node.setResult(0);
            node.setStatus(0);
            approveMapper.insert(node);
        }

        if (dto.getAttachments() != null) {
            jdbc.update("DELETE FROM leave_attachment WHERE leave_id = ?", entity.getId());
            for (LeaveApplyDTO.Attachment a : dto.getAttachments()) {
                if (a.getFileUrl() == null || a.getFileUrl().isBlank()) continue;
                LeaveAttachment att = new LeaveAttachment();
                att.setLeaveId(entity.getId());
                att.setFileUrl(a.getFileUrl());
                att.setFileName(a.getFileName());
                attachMapper.insert(att);
            }
        }

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("id", entity.getId());
        res.put("days", days);
        res.put("status", 10);
        res.put("statusText", "待辅导员审批");
        res.put("approveFlow", flow.stream().map(f -> f.get("approverName")).toList());

        // 站内提醒：通知第一级审批人
        if (!flow.isEmpty()) {
            Long firstApprover = (Long) flow.get(0).get("approverId");
            messageService.notifyEmployeeOnce(firstApprover, "新的请假申请",
                    "<p>" + ctx.getShowName() + " 提交了 " + days + " 天请假（"
                            + (dto.getType() == 1 ? "病假" : "事假") + "），请及时审批。</p>", 1);
        }
        return res;
    }

    /** 审批链路：≤3 天辅导员；3~7 天辅导员 + 书记 */
    private List<Map<String, Object>> buildFlow(Long classOrgId, BigDecimal days) {
        Long counselorId = queryLong(
                "SELECT counselor_id FROM org_class WHERE org_id = ? AND counselor_id IS NOT NULL", classOrgId);
        if (counselorId == null) {
            throw BizException.badRequest("LEAVE_NO_COUNSELOR", "本班尚未配置辅导员，请联系教务");
        }
        List<Map<String, Object>> flow = new ArrayList<>();
        flow.add(node("COUNSELOR", counselorId));

        if (days.doubleValue() > secretaryThreshold) {
            Map<String, Object> sec = jdbc.query(
                    "SELECT e.id, e.name FROM sys_user_role r JOIN org_employee e ON e.user_id = r.user_id "
                            + "WHERE r.role_code = 'SECRETARY' AND e.deleted = 0 LIMIT 1",
                    rs -> rs.next() ? Map.of("id", rs.getLong("id"), "name", rs.getString("name")) : null);
            if (sec == null) {
                throw BizException.badRequest("LEAVE_NO_SECRETARY", "请假超过 3 天需书记审批，但系统未配置书记账号");
            }
            flow.add(node("SECRETARY", ((Number) sec.get("id")).longValue()));
        }
        return flow;
    }

    private Map<String, Object> node(String role, Long approverId) {
        String name = jdbc.query(
                "SELECT name FROM org_employee WHERE id = ?",
                rs -> rs.next() ? rs.getString(1) : "审批人", approverId);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("role", role);
        m.put("approverId", approverId);
        m.put("approverName", name);
        return m;
    }

    // ---------------------------------------------------------------- 取消 / 销假 / 改期

    @Transactional
    public void cancel(Long id, UserContext ctx) {
        LeaveRequest l = requireOwn(id, ctx);
        if (l.getStatus() != 10 && l.getStatus() != 11 && l.getStatus() != 30) {
            throw BizException.badRequest("LEAVE_STATUS_INVALID", "只有未审批或已驳回的请假可以取消");
        }
        int n = jdbc.update("UPDATE leave_request SET status = 40, version = version + 1 "
                + "WHERE id = ? AND deleted = 0 AND status IN (10,11,30)", id);
        if (n == 0) {
            throw BizException.badRequest("LEAVE_STATUS_INVALID", "该请假已被处理，请刷新后重试");
        }
        jdbc.update("UPDATE leave_approve SET status = 2 WHERE leave_id = ? AND status = 0", id);
    }

    @Transactional
    public Map<String, Object> returnFromLeave(Long id, LocalDateTime endTime, UserContext ctx) {
        LeaveRequest l = requireOwn(id, ctx);
        if (l.getStatus() != 20) {
            throw BizException.badRequest("LEAVE_STATUS_INVALID", "只有已通过待销假的请假可以销假");
        }
        LocalDateTime actual = endTime == null ? LocalDateTime.now() : endTime;
        if (actual.isBefore(l.getStartTime())) {
            throw BizException.badRequest("销假时间不能早于请假开始时间");
        }
        LocalDateTime newEnd = (endTime != null && endTime.isBefore(l.getEndTime())) ? endTime : l.getEndTime();
        BigDecimal newDays = calcDays(l.getStartTime(), newEnd);
        // 销假时间晚于结束时间（超期）时，按结束时间记录，避免 end_time 被拉长
        LocalDateTime recordTime = actual.isAfter(l.getEndTime()) ? l.getEndTime() : actual;
        int n = jdbc.update("UPDATE leave_request SET status = 21, return_time = ?, end_time = ?, days = ?, "
                + "version = version + 1 WHERE id = ? AND status = 20", recordTime, newEnd, newDays, id);
        if (n == 0) {
            throw BizException.badRequest("LEAVE_STATUS_INVALID", "该请假已被处理，请刷新后重试");
        }
        l.setEndTime(newEnd);
        l.setDays(newDays);
        l.setReturnTime(actual);

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("id", id);
        res.put("status", 21);
        res.put("statusText", "已销假");
        res.put("returnTime", l.getReturnTime());

        Long counselor = queryLong("SELECT counselor_id FROM org_class WHERE org_id = ?", l.getClassOrgId());
        messageService.notifyEmployeeOnce(counselor, "学生已销假",
                "<p>" + ctx.getShowName() + " 已销假（请假 " + l.getDays() + " 天）。</p>", 0);
        return res;
    }

    @Transactional
    public Map<String, Object> extend(Long id, LocalDateTime newEnd, UserContext ctx) {
        if (newEnd == null) {
            throw BizException.badRequest("新的结束时间不能为空");
        }
        LeaveRequest l = requireOwn(id, ctx);
        if (l.getStatus() != 20 && l.getStatus() != 21) {
            throw BizException.badRequest("LEAVE_STATUS_INVALID", "只有已通过的请假可以修改结束时间");
        }
        if (!newEnd.isAfter(l.getStartTime())) {
            throw BizException.badRequest("结束时间必须晚于开始时间");
        }
        BigDecimal days = calcDays(l.getStartTime(), newEnd);
        if (days.doubleValue() > maxDays) {
            throw BizException.badRequest("LEAVE_TOO_LONG", "修改后为 " + days + " 天，超过 " + maxDays + " 天上限");
        }
        int n = jdbc.update("UPDATE leave_request SET end_time = ?, days = ?, status = 10, current_step = 1, "
                + "return_time = NULL, version = version + 1 WHERE id = ? AND status IN (20,21)", newEnd, days, id);
        if (n == 0) {
            throw BizException.badRequest("LEAVE_STATUS_INVALID", "该请假已被处理，请刷新后重试");
        }
        l.setEndTime(newEnd);
        l.setDays(days);

        jdbc.update("UPDATE leave_approve SET status = 2 WHERE leave_id = ? AND status = 0", id);
        List<Map<String, Object>> flow = buildFlow(l.getClassOrgId(), days);
        int baseStep = queryInt("SELECT COALESCE(MAX(step),0) FROM leave_approve WHERE leave_id = ?", id);
        for (int i = 0; i < flow.size(); i++) {
            Map<String, Object> f = flow.get(i);
            LeaveApprove node = new LeaveApprove();
            node.setLeaveId(id);
            node.setStep(baseStep + i + 1);
            node.setApproverRole((String) f.get("role"));
            node.setApproverId((Long) f.get("approverId"));
            node.setApproverName((String) f.get("approverName"));
            node.setResult(0);
            node.setStatus(0);
            approveMapper.insert(node);
        }
        // 让当前待审节点回到第 1 步语义，避免历史 step 干扰
        jdbc.update("UPDATE leave_approve SET step = step - ? WHERE leave_id = ? AND status = 0", baseStep, id);

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("id", id);
        res.put("days", days);
        res.put("status", 10);
        res.put("statusText", "待辅导员审批");
        return res;
    }

    // ---------------------------------------------------------------- 查询

    public Map<String, Object> personalPage(LeaveQueryDTO q, UserContext ctx) {
        if (ctx.getEmployeeId() == null) {
            return emptyPage(q);
        }
        StringBuilder where = new StringBuilder(" WHERE l.deleted = 0 AND l.employee_id = ? ");
        List<Object> args = new ArrayList<>();
        args.add(ctx.getEmployeeId());
        appendFilters(where, args, q.getSearchModel());
        return pageQuery(where.toString(), args, q, false);
    }

    /** 学委 / 辅导员看班级：默认只看已通过（20/21），学委不出理由与附件 */
    public Map<String, Object> classPage(LeaveQueryDTO q, UserContext ctx) {
        boolean privileged = ctx.hasRole("COUNSELOR") || ctx.hasRole("SECRETARY") || ctx.hasRole("ADMIN");
        boolean monitor = ctx.hasRole("MONITOR");
        if (!privileged && !monitor) {
            throw BizException.forbidden("只有学委或辅导员可以查看班级请假");
        }
        // 数据范围分两档：
        //   书记 / 管理员 → 按 orgPath 前缀放开到「学院 / 全校」（org_unit.path 物化路径）
        //   辅导员 / 学委 → 只到本班，精确匹配 class_org_id
        boolean prefixScope = ctx.hasRole("SECRETARY") || ctx.hasRole("ADMIN");
        StringBuilder where = new StringBuilder(" WHERE l.deleted = 0 ");
        List<Object> args = new ArrayList<>();
        if (prefixScope) {
            String path = ctx.getOrgPath() == null || ctx.getOrgPath().isBlank() ? "/" : ctx.getOrgPath();
            where.append(" AND l.class_org_id IN (SELECT u.id FROM org_unit u WHERE u.deleted = 0 AND u.path LIKE CONCAT(?, '%')) ");
            args.add(path);
        } else {
            if (ctx.getClassOrgId() == null) {
                throw BizException.forbidden("当前账号未归属班级");
            }
            where.append(" AND l.class_org_id = ? ");
            args.add(ctx.getClassOrgId());
        }
        LeaveQueryDTO.Search s = q.getSearchModel();
        boolean masked = monitor && !privileged;
        if (s == null || s.getStatus() == null) {
            where.append(" AND l.status IN (20,21) ");
        }
        appendFilters(where, args, s);
        return pageQuery(where.toString(), args, q, masked);
    }

    /** 待我审批 / 我已处理 */
    public Map<String, Object> approvePage(LeaveQueryDTO q, UserContext ctx) {
        if (ctx.getEmployeeId() == null) {
            throw BizException.forbidden("当前账号未绑定员工档案");
        }
        boolean handled = q.getSearchModel() != null && Boolean.FALSE.equals(q.getSearchModel().getPendingOnly());
        String nodeFilter = handled ? " AND a.status IN (1,2) " : " AND a.status = 0 AND l.status IN (10,11) ";

        String baseFrom = " FROM leave_approve a "
                + " JOIN leave_request l ON l.id = a.leave_id AND l.deleted = 0 "
                + " JOIN org_employee e ON e.id = l.employee_id "
                + " LEFT JOIN org_unit ou ON ou.id = l.class_org_id "
                + " WHERE a.approver_id = ? " + nodeFilter;
        String countSql = "SELECT COUNT(DISTINCT l.id) " + baseFrom;
        Integer total = jdbc.queryForObject(countSql, Integer.class, ctx.getEmployeeId());

        int pageNum = Math.max(q.getPageNum(), 1);
        int pageSize = Math.min(Math.max(q.getPageSize(), 1), 100);
        String sql = "SELECT l.*, e.name AS employee_name, e.employee_no, ou.name AS class_name, a.step AS node_step, a.status AS node_status "
                + baseFrom + " ORDER BY l.create_time DESC LIMIT ? OFFSET ?";
        List<LeaveVO> list = jdbc.query(sql, (rs, i) -> map(rs, false), ctx.getEmployeeId(), pageSize,
                (pageNum - 1) * pageSize);

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("pageNum", pageNum);
        res.put("pageSize", pageSize);
        res.put("total", total == null ? 0 : total);
        res.put("list", list);
        return res;
    }

    public LeaveVO detail(Long id, UserContext ctx) {
        LeaveVO vo = jdbc.query("SELECT l.*, e.name AS employee_name, e.employee_no, ou.name AS class_name "
                        + "FROM leave_request l JOIN org_employee e ON e.id = l.employee_id "
                        + "LEFT JOIN org_unit ou ON ou.id = l.class_org_id "
                        + "WHERE l.id = ? AND l.deleted = 0",
                rs -> rs.next() ? map(rs, false) : null, id);
        if (vo == null) {
            throw BizException.notFound("请假记录不存在");
        }
        boolean owner = ctx.getEmployeeId() != null && ctx.getEmployeeId().equals(vo.getEmployeeId());
        boolean approver = queryInt("SELECT COUNT(*) FROM leave_approve WHERE leave_id = ? AND approver_id = ?",
                id, ctx.getEmployeeId() == null ? -1L : ctx.getEmployeeId()) > 0;
        boolean monitorOfClass = ctx.hasRole("MONITOR") && Objects.equals(ctx.getClassOrgId(), vo.getClassOrgId());
        // 管理身份也要落在自己的数据范围内：管理员全校，书记/辅导员只能看 orgPath 前缀内的班级
        // （否则任意辅导员都能按 id 翻看别院学生的事由与附件）
        boolean staff = false;
        if (ctx.hasRole("ADMIN")) {
            staff = true;
        } else if (ctx.hasRole("COUNSELOR") || ctx.hasRole("SECRETARY")) {
            String myPath = ctx.getOrgPath();
            String classPath = orgPathOf(vo.getClassOrgId());
            staff = myPath != null && !myPath.isBlank() && classPath != null && classPath.startsWith(myPath);
        }

        if (!owner && !approver && !monitorOfClass && !staff) {
            throw BizException.forbidden("无权查看该请假");
        }
        // 学委且非本人/非审批人 → 脱敏
        if (monitorOfClass && !owner && !approver && !staff) {
            vo.setReason(null);
            vo.setAttachments(null);
        } else {
            vo.setAttachments(attachments(id));
        }
        vo.setApprovals(approvals(id));
        return vo;
    }

    /** 取机构物化路径（/1/2/3/4/），用于行级范围判断 */
    private String orgPathOf(Long orgId) {
        if (orgId == null) return null;
        List<String> rows = jdbc.query("SELECT path FROM org_unit WHERE id = ? AND deleted = 0",
                (rs, i) -> rs.getString(1), orgId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public List<LeaveVO.Attachment> attachments(Long leaveId) {
        return jdbc.query("SELECT file_url, file_name FROM leave_attachment WHERE leave_id = ?",
                (rs, i) -> {
                    LeaveVO.Attachment a = new LeaveVO.Attachment();
                    a.setFileUrl(rs.getString("file_url"));
                    a.setFileName(rs.getString("file_name"));
                    return a;
                }, leaveId);
    }

    public List<LeaveVO.ApprovalNode> approvals(Long leaveId) {
        return jdbc.query("SELECT * FROM leave_approve WHERE leave_id = ? ORDER BY step, id",
                (rs, i) -> {
                    LeaveVO.ApprovalNode n = new LeaveVO.ApprovalNode();
                    n.setStep(rs.getInt("step"));
                    n.setApproverRole(rs.getString("approver_role"));
                    n.setApproverName(rs.getString("approver_name"));
                    int result = rs.getInt("result");
                    n.setResult(result);
                    n.setResultText(result == 1 ? "批准" : result == 2 ? "驳回" : "待处理");
                    n.setReason(rs.getString("reason"));
                    n.setStatus(rs.getInt("status"));
                    n.setHandleTime(rs.getObject("handle_time", LocalDateTime.class));
                    return n;
                }, leaveId);
    }

    // ---------------------------------------------------------------- 审批

    @Transactional
    public Map<String, Object> approve(LeaveApproveDTO dto, UserContext ctx) {
        if (ctx.getEmployeeId() == null) {
            throw BizException.forbidden("当前账号未绑定员工档案");
        }
        if (dto.getResult() != 1 && dto.getResult() != 2) {
            throw BizException.badRequest("审批结果只能是 1(批准) 或 2(驳回)");
        }
        LeaveRequest l = leaveMapper.selectById(dto.getLeaveId());
        if (l == null) {
            throw BizException.notFound("请假记录不存在");
        }
        if (l.getStatus() != 10 && l.getStatus() != 11) {
            throw BizException.badRequest("LEAVE_STATUS_INVALID", "该请假当前状态不可审批");
        }
        Map<String, Object> pending = jdbc.query(
                "SELECT * FROM leave_approve WHERE leave_id = ? AND step = ? AND status = 0",
                rs -> rs.next() ? Map.of("id", rs.getLong("id"), "approverId", rs.getLong("approver_id")) : null,
                l.getId(), l.getCurrentStep());
        if (pending == null) {
            throw BizException.badRequest("该请假没有待处理的审批节点");
        }
        if (!pending.get("approverId").equals(ctx.getEmployeeId())) {
            throw BizException.forbidden("当前待审批人不是你");
        }
        if (dto.getResult() == 2 && (dto.getReason() == null || dto.getReason().isBlank())) {
            throw BizException.badRequest("驳回必须填写原因");
        }

        jdbc.update("UPDATE leave_approve SET result = ?, reason = ?, status = 1, handle_time = NOW() WHERE id = ?",
                dto.getResult(), dto.getReason(), pending.get("id"));

        Map<String, Object> res = new LinkedHashMap<>();
        if (dto.getResult() == 2) {
            jdbc.update("UPDATE leave_request SET status = 30, version = version + 1 WHERE id = ?", l.getId());
            res.put("status", 30);
            res.put("statusText", "已驳回");
            messageService.notifyEmployeeOnce(l.getEmployeeId(), "请假被驳回",
                    "<p>你的请假申请被驳回，原因：" + (dto.getReason() == null ? "未填写" : dto.getReason()) + "</p>", 2);
        } else {
            Integer next = jdbc.query(
                    "SELECT MIN(step) FROM leave_approve WHERE leave_id = ? AND status = 0 AND step > ?",
                    rs -> rs.next() ? (rs.getObject(1) == null ? null : ((Number) rs.getObject(1)).intValue()) : null,
                    l.getId(), l.getCurrentStep());
            if (next == null) {
                jdbc.update("UPDATE leave_request SET status = 20, version = version + 1 WHERE id = ?", l.getId());
                res.put("status", 20);
                res.put("statusText", "已通过待销假");
                messageService.notifyEmployeeOnce(l.getEmployeeId(), "请假已通过",
                        "<p>你的请假已审批通过，请在结束后及时销假。</p>", 0);
            } else {
                jdbc.update("UPDATE leave_request SET status = 11, current_step = ?, version = version + 1 WHERE id = ?",
                        next, l.getId());
                res.put("status", 11);
                res.put("statusText", "待书记审批");
                Long nextApprover = queryLong("SELECT approver_id FROM leave_approve WHERE leave_id = ? AND step = ?",
                        l.getId(), next);
                messageService.notifyEmployeeOnce(nextApprover, "待审批请假（书记）",
                        "<p>有一条 " + l.getDays() + " 天的请假待你审批。</p>", 1);
            }
            res.put("nextStep", next);
        }
        res.put("leaveId", l.getId());
        return res;
    }

    // ---------------------------------------------------------------- 工具

    private void appendFilters(StringBuilder where, List<Object> args, LeaveQueryDTO.Search s) {
        if (s == null) return;
        if (s.getStatus() != null) {
            where.append(" AND l.status = ? ");
            args.add(s.getStatus());
        }
        if (s.getType() != null) {
            where.append(" AND l.type = ? ");
            args.add(s.getType());
        }
        if (s.getStartTime() != null && !s.getStartTime().isBlank()) {
            where.append(" AND l.start_time >= ? ");
            args.add(s.getStartTime() + " 00:00:00");
        }
        if (s.getEndTime() != null && !s.getEndTime().isBlank()) {
            where.append(" AND l.end_time <= ? ");
            args.add(s.getEndTime() + " 23:59:59");
        }
    }

    private Map<String, Object> pageQuery(String where, List<Object> args, LeaveQueryDTO q, boolean masked) {
        String countSql = "SELECT COUNT(*) FROM leave_request l " + where;
        Integer total = jdbc.queryForObject(countSql, Integer.class, args.toArray());

        int pageNum = Math.max(q.getPageNum(), 1);
        int pageSize = Math.min(Math.max(q.getPageSize(), 1), 100);
        String sql = "SELECT l.*, e.name AS employee_name, e.employee_no, ou.name AS class_name "
                + "FROM leave_request l JOIN org_employee e ON e.id = l.employee_id "
                + "LEFT JOIN org_unit ou ON ou.id = l.class_org_id " + where
                + " ORDER BY l.create_time DESC LIMIT ? OFFSET ?";
        List<Object> a = new ArrayList<>(args);
        a.add(pageSize);
        a.add((pageNum - 1) * pageSize);
        List<LeaveVO> list = jdbc.query(sql, (rs, i) -> map(rs, masked), a.toArray());

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("pageNum", pageNum);
        res.put("pageSize", pageSize);
        res.put("total", total == null ? 0 : total);
        res.put("list", list);
        return res;
    }

    private LeaveVO map(ResultSet rs, boolean masked) throws SQLException {
        LeaveVO v = new LeaveVO();
        v.setId(rs.getLong("id"));
        v.setEmployeeId(rs.getLong("employee_id"));
        v.setClassOrgId(rs.getLong("class_org_id"));
        v.setType(rs.getInt("type"));
        v.setTypeText(rs.getInt("type") == 1 ? "病假" : "事假");
        v.setStartTime(rs.getObject("start_time", LocalDateTime.class));
        v.setEndTime(rs.getObject("end_time", LocalDateTime.class));
        v.setDays(rs.getBigDecimal("days"));
        v.setStatus(rs.getInt("status"));
        v.setStatusText(statusText().getOrDefault(rs.getInt("status"), "未知"));
        v.setCurrentStep(rs.getInt("current_step"));
        v.setReturnTime(rs.getObject("return_time", LocalDateTime.class));
        v.setCreateTime(rs.getObject("create_time", LocalDateTime.class));
        if (!masked) {
            v.setReason(rs.getString("reason"));
        }
        try {
            v.setEmployeeName(rs.getString("employee_name"));
            v.setEmployeeNo(rs.getString("employee_no"));
            v.setClassName(rs.getString("class_name"));
        } catch (SQLException ignore) {
            // 某些查询未带 join 字段
        }
        return v;
    }

    private LeaveRequest requireOwn(Long id, UserContext ctx) {
        LeaveRequest l = leaveMapper.selectById(id);
        if (l == null) {
            throw BizException.notFound("请假记录不存在");
        }
        if (ctx.getEmployeeId() == null || !l.getEmployeeId().equals(ctx.getEmployeeId())) {
            throw BizException.forbidden("只能操作自己的请假");
        }
        return l;
    }

    private int countInFlight(Long employeeId, Long excludeId) {
        Integer c = jdbc.queryForObject(
                "SELECT COUNT(*) FROM leave_request WHERE employee_id = ? AND deleted = 0 "
                        + "AND status IN (10,11,20) AND id <> ?",
                Integer.class, employeeId, excludeId == null ? -1L : excludeId);
        return c == null ? 0 : c;
    }

    private int countConflict(Long employeeId, Long excludeId, LocalDateTime start, LocalDateTime end) {
        Integer c = jdbc.queryForObject(
                "SELECT COUNT(*) FROM leave_request WHERE employee_id = ? AND deleted = 0 "
                        + "AND status NOT IN (30,40) AND id <> ? AND start_time < ? AND end_time > ?",
                Integer.class, employeeId, excludeId == null ? -1L : excludeId, end, start);
        return c == null ? 0 : c;
    }

    private BigDecimal calcDays(LocalDateTime start, LocalDateTime end) {
        long minutes = Duration.between(start, end).toMinutes();
        if (minutes <= 0) {
            throw BizException.badRequest("结束时间必须晚于开始时间");
        }
        double days = Math.ceil(minutes / (60.0 * 24));
        return BigDecimal.valueOf(days).setScale(1, RoundingMode.HALF_UP);
    }

    private Long queryLong(String sql, Object... args) {
        Number v = jdbc.query(sql, rs -> rs.next() ? (Number) rs.getObject(1) : null, args);
        return v == null ? null : v.longValue();
    }

    private int queryInt(String sql, Object... args) {
        Number v = jdbc.query(sql, rs -> rs.next() ? (Number) rs.getObject(1) : null, args);
        return v == null ? 0 : v.intValue();
    }

    private Map<String, Object> emptyPage(LeaveQueryDTO q) {
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("pageNum", q.getPageNum());
        res.put("pageSize", q.getPageSize());
        res.put("total", 0);
        res.put("list", List.of());
        return res;
    }
}
