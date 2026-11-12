package com.campus.portfolio.data;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 首次启动灌入演示账号（已存在则跳过），密码统一 123456。
 * admin        管理员
 * 2025000001   学生 + 学委（林同学）
 * 1001001      辅导员
 * 13900001111  家长（林同学家长，与 2025000001 已建立关联）
 */
@Component
public class DataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);
    private final JdbcTemplate jdbc;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public DataInitializer(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            Long adminId = ensureUser("admin", "13800000000", "系统管理员", 1);
            ensureRole(adminId, "ADMIN", 1L);

            Long stuId = ensureUser("2025000001", "13800138000", "林同学", 1);
            ensureRole(stuId, "STUDENT", 4L);
            ensureRole(stuId, "MONITOR", 4L);
            ensureEmployee(stuId, "林同学", "2025000001", 1L, 4L, 2);

            Long teacherId = ensureUser("1001001", "13800138001", "辅导员王老师", 1);
            ensureRole(teacherId, "COUNSELOR", 2L);
            ensureEmployee(teacherId, "辅导员王老师", "1001001", 1L, 4L, 4);
            bindCounselor(teacherId);

            // 家长账号（演示家校联动用）：账号=手机号，与 2025000001 直接建立已通过关联
            Long parentId = ensureUser("13900001111", "13900001111", "林同学家长", 1);
            ensureRole(parentId, "PARENT", null);
            bindParent(parentId, "2025000001");

            log.info("演示账号就绪：admin / 2025000001 / 1001001 / 13900001111（家长），密码均为 123456");
        } catch (Exception e) {
            log.warn("演示数据初始化失败（可能表不存在）：{}", e.getMessage());
        }
    }

    private Long ensureUser(String account, String mobile, String showName, int guideDone) {
        var rows = jdbc.queryForList("SELECT id FROM sys_user WHERE account = ?", account);
        if (!rows.isEmpty()) {
            return ((Number) rows.get(0).get("id")).longValue();
        }
        jdbc.update("INSERT INTO sys_user (account, mobile, password_hash, show_name, gender, coin, gift, guide_done) "
                        + "VALUES (?, ?, ?, ?, 2, 100, 1, ?)",
                account, mobile, encoder.encode("123456"), showName, guideDone);
        return jdbc.queryForObject("SELECT id FROM sys_user WHERE account = ?", Long.class, account);
    }

    private void ensureRole(Long userId, String role, Long orgId) {
        Integer c = jdbc.queryForObject(
                "SELECT COUNT(*) FROM sys_user_role WHERE user_id = ? AND role_code = ? AND (org_id <=> ?)",
                Integer.class, userId, role, orgId);
        if (c == null || c == 0) {
            jdbc.update("INSERT INTO sys_user_role (user_id, role_code, org_id) VALUES (?, ?, ?)",
                    userId, role, orgId);
        }
    }

    private void ensureEmployee(Long userId, String name, String no, Long rootOrgId, Long orgId, int typeId) {
        Integer c = jdbc.queryForObject("SELECT COUNT(*) FROM org_employee WHERE user_id = ?",
                Integer.class, userId);
        Long empId;
        if (c == null || c == 0) {
            jdbc.update("INSERT INTO org_employee (user_id, name, employee_no, identity_type, identity_no, "
                            + "sex, root_org_id, hire_date, enable_relative) VALUES (?, ?, ?, 1, ?, 0, ?, CURDATE(), 1)",
                    userId, name, no, "350101200001021533", rootOrgId);
            empId = jdbc.queryForObject("SELECT id FROM org_employee WHERE user_id = ?", Long.class, userId);
        } else {
            empId = jdbc.queryForObject("SELECT id FROM org_employee WHERE user_id = ?", Long.class, userId);
        }
        Integer uc = jdbc.queryForObject(
                "SELECT COUNT(*) FROM org_employee_unit WHERE employee_id = ? AND org_id = ?",
                Integer.class, empId, orgId);
        if (uc == null || uc == 0) {
            jdbc.update("INSERT INTO org_employee_unit (employee_id, org_id, type_id, is_primary) VALUES (?, ?, ?, 1)",
                    empId, orgId, typeId);
        }
    }

    private void bindCounselor(Long teacherId) {
        Long empId = jdbc.queryForObject("SELECT id FROM org_employee WHERE user_id = ?", Long.class, teacherId);
        jdbc.update("UPDATE org_class SET counselor_id = ? WHERE org_id = 4 AND counselor_id IS NULL", empId);
    }

    /**
     * 家长与学生的关联：演示数据直接落成"已通过"，省得答辩时先演一遍申请→同意。
     * 有唯一键 uk_pair(parent_user_id, employee_id)，所以先查后插，重复启动不会报错。
     */
    private void bindParent(Long parentUserId, String studentEmployeeNo) {
        var emp = jdbc.queryForList("SELECT id FROM org_employee WHERE employee_no = ?", studentEmployeeNo);
        if (emp.isEmpty()) {
            log.warn("家长关联跳过：找不到学号 {} 的员工档案", studentEmployeeNo);
            return;
        }
        Long employeeId = ((Number) emp.get(0).get("id")).longValue();
        Integer exist = jdbc.queryForObject(
                "SELECT COUNT(*) FROM rel_relative_link WHERE parent_user_id = ? AND employee_id = ?",
                Integer.class, parentUserId, employeeId);
        if (exist == null || exist == 0) {
            jdbc.update("INSERT INTO rel_relative_link (parent_user_id, employee_id, apply_from, status, "
                            + "remark, handle_time, handle_user_id) VALUES (?, ?, 'STUDENT', 1, ?, NOW(), ?)",
                    parentUserId, employeeId, "演示数据：家长与孩子已关联", parentUserId);
        }
    }
}
