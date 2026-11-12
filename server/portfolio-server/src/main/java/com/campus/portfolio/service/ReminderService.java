package com.campus.portfolio.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * F-16 提醒机制：定时任务每小时扫一次。
 * <p>
 * ① 请假到期超 24 小时仍未销假 → 置为 50 超期未销假，并分别通知学生与辅导员；
 * ② 审批待处理超 24 小时 → 通知当前待审批人（同一人 24 小时内只提醒一次，防轰炸）。
 * <p>
 * 销假提醒辅导员已在 LeaveService.returnLeave 里同步发出，这里不重复。
 */
@Service
public class ReminderService {

    private static final Logger log = LoggerFactory.getLogger(ReminderService.class);

    /** 单次最多处理条数，避免一次扫太多把库压住 */
    private static final int BATCH = 200;

    private final JdbcTemplate jdbc;
    private final MessageService messageService;

    public ReminderService(JdbcTemplate jdbc, MessageService messageService) {
        this.jdbc = jdbc;
        this.messageService = messageService;
    }

    /** 每小时第 5 分执行（cron: 秒 分 时 日 月 周） */
    @Scheduled(cron = "0 5 * * * ?")
    public void hourly() {
        try {
            log.info("定时提醒执行结果：{}", runAll());
        } catch (Exception e) {
            log.error("定时提醒执行失败", e);
        }
    }

    @Transactional
    public Map<String, Object> runAll() {
        int overdue = markOverdue();
        int pending = remindPendingApprovals();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("overdueMarked", overdue);
        m.put("pendingReminded", pending);
        m.put("runAt", LocalDateTime.now().toString());
        return m;
    }

    // ------------------------------------------------------------ ① 超期未销假

    private int markOverdue() {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT lr.id, lr.employee_id AS employeeId, lr.end_time AS endTime, lr.days, "
                        + "e.user_id AS studentUserId, e.name AS studentName, "
                        + "(SELECT la.approver_id FROM leave_approve la WHERE la.leave_id = lr.id "
                        + " ORDER BY la.step LIMIT 1) AS counselorEmpId "
                        + "FROM leave_request lr JOIN org_employee e ON e.id = lr.employee_id "
                        + "WHERE lr.deleted = 0 AND lr.status = 20 "
                        + "AND lr.end_time < DATE_SUB(NOW(), INTERVAL 24 HOUR) "
                        + "LIMIT " + BATCH);

        int n = 0;
        for (Map<String, Object> r : rows) {
            long id = ((Number) r.get("id")).longValue();
            int updated = jdbc.update(
                    "UPDATE leave_request SET status = 50, overdue_warn = 1, update_time = NOW() "
                            + "WHERE id = ? AND status = 20", id);
            if (updated == 0) continue;
            n++;

            String student = str(r.get("studentName"));
            String endTime = String.valueOf(r.get("endTime"));

            Object studentUserId = r.get("studentUserId");
            if (studentUserId instanceof Number uid) {
                messageService.notify(List.of(uid.longValue()), 1, "请假超期未销假",
                        student + " 的请假已于 " + endTime + " 到期，超过 24 小时未销假，已标记为「超期未销假」。"
                                + "请尽快在请假详情页销假，否则将计入风险记录。", 2);
            }
            Object counselorEmpId = r.get("counselorEmpId");
            if (counselorEmpId instanceof Number cid) {
                messageService.notifyEmployee(cid.longValue(), "学生超期未销假",
                        student + " 的请假（到期 " + endTime + "）超过 24 小时未销假，系统已自动标记为超期，请跟进处理。", 2);
            }
        }
        return n;
    }

    // ------------------------------------------------------------ ② 审批超 24h

    private int remindPendingApprovals() {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT lr.id, lr.start_time AS startTime, lr.end_time AS endTime, lr.days, lr.status, "
                        + "e.name AS studentName, "
                        + "(SELECT la.approver_id FROM leave_approve la WHERE la.leave_id = lr.id AND la.result = 0 "
                        + " ORDER BY la.step LIMIT 1) AS approverEmpId, "
                        + "(SELECT eu.user_id FROM leave_approve la JOIN org_employee eu ON eu.id = la.approver_id "
                        + " WHERE la.leave_id = lr.id AND la.result = 0 ORDER BY la.step LIMIT 1) AS approverUserId, "
                        + "(SELECT la.approver_role FROM leave_approve la WHERE la.leave_id = lr.id AND la.result = 0 "
                        + " ORDER BY la.step LIMIT 1) AS approverRole "
                        + "FROM leave_request lr JOIN org_employee e ON e.id = lr.employee_id "
                        + "WHERE lr.deleted = 0 AND lr.status IN (10, 11) "
                        + "AND lr.update_time < DATE_SUB(NOW(), INTERVAL 24 HOUR) "
                        + "LIMIT " + BATCH);

        int n = 0;
        for (Map<String, Object> r : rows) {
            Object uidObj = r.get("approverUserId");
            if (!(uidObj instanceof Number uid)) continue;
            long approverUserId = uid.longValue();

            // 24 小时内已提醒过就跳过
            Integer already = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM msg_notification n JOIN msg_receive rc ON rc.notification_id = n.id "
                            + "WHERE rc.to_user_id = ? AND n.title = '请假待审批超 24 小时' "
                            + "AND n.create_time > DATE_SUB(NOW(), INTERVAL 24 HOUR)",
                    Integer.class, approverUserId);
            if (already != null && already > 0) continue;

            String role = "SECRETARY".equals(String.valueOf(r.get("approverRole"))) ? "书记" : "辅导员";
            messageService.notify(List.of(approverUserId), 1, "请假待审批超 24 小时",
                    "有一条来自 " + r.get("studentName") + " 的请假（" + r.get("startTime") + " ~ " + r.get("endTime")
                            + "，共 " + r.get("days") + " 天）已等待 " + role + " 审批超过 24 小时，请尽快处理。", 1);
            n++;
        }
        return n;
    }

    private String str(Object o) {
        return o == null ? "该学生" : String.valueOf(o);
    }
}
