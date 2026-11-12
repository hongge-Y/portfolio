package com.campus.portfolio.service;

import com.campus.portfolio.common.BizException;
import com.campus.portfolio.common.UserContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * 教务：课表展示 + 课程评价。
 * 课表数据来自导入（Excel → JSON → 本接口），不编造数据。
 */
@Service
public class EduService {

    private final JdbcTemplate jdbc;

    @Value("${portfolio.edu.current-term:2026-2027-1}")
    private String currentTerm;
    @Value("${portfolio.edu.term-start:2026-09-01}")
    private String termStart;

    public EduService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ------------------------------------------------------------ 学期

    public Map<String, Object> term() {
        LocalDate start = LocalDate.parse(termStart);
        int week = (int) (ChronoUnit.DAYS.between(start, LocalDate.now()) / 7) + 1;
        if (week < 1) week = 1;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("term", currentTerm);
        m.put("startDate", start.toString());
        m.put("weekNo", Math.min(week, 20));
        m.put("maxWeek", 20);
        return m;
    }

    // ------------------------------------------------------------ 课表

    public Map<String, Object> timetable(String term, Integer weekNo, UserContext ctx) {
        String t = term == null || term.isBlank() ? currentTerm : term;
        Map<String, Object> tm = term();
        int week = weekNo == null ? (int) tm.get("weekNo") : weekNo;

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("term", t);
        res.put("weekNo", week);
        res.put("maxWeek", tm.get("maxWeek"));

        if (ctx.getClassOrgId() == null) {
            res.put("items", List.of());
            res.put("emptyReason", "当前账号未归属班级（家长/教师账号无课表）");
            return res;
        }
        List<Map<String, Object>> rows = jdbc.query(
                "SELECT tt.id, tt.course_id AS courseId, c.name AS courseName, c.teacher_name AS teacherName, "
                        + "tt.week_day AS weekDay, tt.section_start AS sectionStart, tt.section_end AS sectionEnd, "
                        + "tt.location, tt.weeks "
                        + "FROM edu_timetable tt JOIN edu_course c ON c.id = tt.course_id "
                        + "WHERE tt.class_org_id = ? AND tt.term = ? ORDER BY tt.week_day, tt.section_start",
                (rs, i) -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", rs.getLong("id"));
                    m.put("courseId", rs.getLong("courseId"));
                    m.put("courseName", rs.getString("courseName"));
                    m.put("teacherName", rs.getString("teacherName"));
                    m.put("weekDay", rs.getInt("weekDay"));
                    m.put("sectionStart", rs.getInt("sectionStart"));
                    m.put("sectionEnd", rs.getInt("sectionEnd"));
                    m.put("location", rs.getString("location"));
                    m.put("weeks", rs.getString("weeks"));
                    m.put("color", colors[(int) (rs.getLong("courseId") % colors.length)]);
                    return m;
                }, ctx.getClassOrgId(), t);

        List<Map<String, Object>> items = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            if (weekMatches((String) r.get("weeks"), week)) items.add(r);
        }
        res.put("items", items);
        if (items.isEmpty()) res.put("emptyReason", "本周没有课程安排");
        return res;
    }

    /** 解析 "1-16" / "1-8,10-16" / "1,3,5" */
    private boolean weekMatches(String weeks, int week) {
        if (weeks == null || weeks.isBlank()) return true;
        for (String part : weeks.split(",")) {
            String p = part.trim();
            if (p.isEmpty()) continue;
            if (p.contains("-")) {
                String[] ab = p.split("-");
                try {
                    int a = Integer.parseInt(ab[0].trim());
                    int b = Integer.parseInt(ab[1].trim());
                    if (week >= a && week <= b) return true;
                } catch (NumberFormatException ignore) { }
            } else {
                try {
                    if (Integer.parseInt(p) == week) return true;
                } catch (NumberFormatException ignore) { }
            }
        }
        return false;
    }

    /** 管理员批量导入课表（覆盖该班级该学期） */
    @Transactional
    public Map<String, Object> importTimetable(Map<String, Object> body) {
        String term = str(body.get("term"));
        Long classOrgId = longOf(body.get("classOrgId"));
        if (term == null || classOrgId == null) throw BizException.badRequest("term 与 classOrgId 必填");
        Object itemsObj = body.get("items");
        if (!(itemsObj instanceof List<?> items)) throw BizException.badRequest("items 必须是数组");

        jdbc.update("DELETE FROM edu_timetable WHERE class_org_id = ? AND term = ?", classOrgId, term);
        int ok = 0, fail = 0;
        for (Object o : items) {
            if (!(o instanceof Map<?, ?> raw)) { fail++; continue; }
            @SuppressWarnings("unchecked")
            Map<String, Object> item = (Map<String, Object>) raw;
            try {
                String code = str(item.get("courseCode"));
                String name = str(item.get("courseName"));
                if (code == null || code.isBlank() || name == null || name.isBlank()) { fail++; continue; }
                Long courseId = jdbc.query("SELECT id FROM edu_course WHERE course_code = ? AND term = ?",
                        rs -> rs.next() ? rs.getLong(1) : null, code, term);
                if (courseId == null) {
                    jdbc.update("INSERT INTO edu_course (course_code, name, teacher_name, credit, class_org_id, term) "
                                    + "VALUES (?,?,?,?,?,?)",
                            code, name, str(item.get("teacherName")), item.get("credit"), classOrgId, term);
                    courseId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
                }
                jdbc.update("INSERT INTO edu_timetable (course_id, class_org_id, term, week_day, section_start, "
                                + "section_end, location, weeks) VALUES (?,?,?,?,?,?,?,?)",
                        courseId, classOrgId, term, intOf(item.get("weekDay"), 1),
                        intOf(item.get("sectionStart"), 1), intOf(item.get("sectionEnd"), 2),
                        str(item.get("location")), str(item.get("weeks")) == null ? "1-16" : str(item.get("weeks")));
                ok++;
            } catch (Exception e) {
                fail++;
            }
        }
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("success", ok);
        res.put("failed", fail);
        res.put("term", term);
        res.put("classOrgId", classOrgId);
        return res;
    }

    // ------------------------------------------------------------ 课程评价

    public List<Map<String, Object>> evalOptions(String term, UserContext ctx) {
        String t = term == null || term.isBlank() ? currentTerm : term;
        if (ctx.getClassOrgId() == null) return List.of();
        return jdbc.query("SELECT c.id AS courseId, c.name AS courseName, c.teacher_name AS teacherName, "
                        + "c.credit, CASE WHEN e.id IS NULL THEN 0 ELSE 1 END AS evaluated, "
                        + "e.score_total AS scoreTotal, e.content AS myContent, e.anonymous AS anonymous "
                        + "FROM edu_course c LEFT JOIN edu_eval e ON e.course_id = c.id AND e.employee_id = ? "
                        + "WHERE c.class_org_id = ? AND c.term = ? AND c.deleted = 0 ORDER BY c.course_code",
                (rs, i) -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("courseId", rs.getLong("courseId"));
                    m.put("courseName", rs.getString("courseName"));
                    m.put("teacherName", rs.getString("teacherName"));
                    m.put("credit", rs.getBigDecimal("credit"));
                    m.put("evaluated", rs.getInt("evaluated") == 1);
                    m.put("scoreTotal", rs.getBigDecimal("scoreTotal"));
                    m.put("myContent", rs.getString("myContent"));
                    return m;
                }, ctx.getEmployeeId() == null ? -1L : ctx.getEmployeeId(), ctx.getClassOrgId(), t);
    }

    @Transactional
    public Map<String, Object> submitEval(Map<String, Object> body, UserContext ctx) {
        if (ctx.getEmployeeId() == null) throw BizException.badRequest("只有学生可以评价课程");
        Long courseId = longOf(body.get("courseId"));
        if (courseId == null) throw BizException.badRequest("courseId 必填");
        int s1 = intOf(body.get("scoreTeach"), 0);
        int s2 = intOf(body.get("scoreContent"), 0);
        int s3 = intOf(body.get("scoreAtmos"), 0);
        for (int s : new int[]{s1, s2, s3}) {
            if (s < 1 || s > 5) throw BizException.badRequest("三个维度评分都要在 1~5 之间");
        }
        String term = str(body.get("term"));
        if (term == null || term.isBlank()) term = currentTerm;
        Integer inClass = jdbc.queryForObject("SELECT COUNT(*) FROM edu_course WHERE id = ? AND class_org_id = ? AND term = ? AND deleted = 0",
                Integer.class, courseId, ctx.getClassOrgId() == null ? -1L : ctx.getClassOrgId(), term);
        if (inClass == null || inClass == 0) throw BizException.badRequest("该课程不在你的班级课表中");

        double total = Math.round((s1 + s2 + s3) / 3.0 * 2 * 10) / 10.0;
        boolean anonymous = !Boolean.FALSE.equals(body.get("anonymous"));
        Integer exist = jdbc.queryForObject("SELECT COUNT(*) FROM edu_eval WHERE employee_id = ? AND course_id = ? AND term = ?",
                Integer.class, ctx.getEmployeeId(), courseId, term);
        if (exist != null && exist > 0) {
            jdbc.update("UPDATE edu_eval SET score_teach=?, score_content=?, score_atmos=?, score_total=?, "
                            + "content=?, anonymous=? WHERE employee_id=? AND course_id=? AND term=?",
                    s1, s2, s3, total, str(body.get("content")), anonymous ? 1 : 0,
                    ctx.getEmployeeId(), courseId, term);
        } else {
            jdbc.update("INSERT INTO edu_eval (employee_id, course_id, term, score_teach, score_content, score_atmos, "
                            + "score_total, content, anonymous) VALUES (?,?,?,?,?,?,?,?,?)",
                    ctx.getEmployeeId(), courseId, term, s1, s2, s3, total, str(body.get("content")), anonymous ? 1 : 0);
        }
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("courseId", courseId);
        res.put("scoreTotal", total);
        return res;
    }

    public List<Map<String, Object>> myEvals(String term, UserContext ctx) {
        String t = term == null || term.isBlank() ? currentTerm : term;
        return jdbc.query("SELECT e.course_id AS courseId, c.name AS courseName, e.score_teach AS scoreTeach, "
                        + "e.score_content AS scoreContent, e.score_atmos AS scoreAtmos, e.score_total AS scoreTotal, "
                        + "e.content, e.anonymous, e.create_time AS createTime "
                        + "FROM edu_eval e JOIN edu_course c ON c.id = e.course_id "
                        + "WHERE e.employee_id = ? AND e.term = ? ORDER BY e.id DESC",
                (rs, i) -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("courseId", rs.getLong("courseId"));
                    m.put("courseName", rs.getString("courseName"));
                    m.put("scoreTeach", rs.getInt("scoreTeach"));
                    m.put("scoreContent", rs.getInt("scoreContent"));
                    m.put("scoreAtmos", rs.getInt("scoreAtmos"));
                    m.put("scoreTotal", rs.getBigDecimal("scoreTotal"));
                    m.put("content", rs.getString("content"));
                    m.put("anonymous", rs.getInt("anonymous") == 1);
                    m.put("createTime", rs.getObject("createTime", LocalDateTime.class));
                    return m;
                }, ctx.getEmployeeId() == null ? -1L : ctx.getEmployeeId(), t);
    }

    /** 后台：聚合统计（不返回学生身份） */
    public Map<String, Object> evalStats(long courseId) {
        Map<String, Object> m = jdbc.queryForMap("SELECT COUNT(*) AS cnt, ROUND(AVG(score_teach),2) AS avgTeach, "
                + "ROUND(AVG(score_content),2) AS avgContent, ROUND(AVG(score_atmos),2) AS avgAtmos, "
                + "ROUND(AVG(score_total),1) AS avgTotal FROM edu_eval WHERE course_id = ? AND status = 1", courseId);
        return m;
    }

    // ------------------------------------------------------------ 工具

    private static final String[] colors = {"#5B8FF9", "#61DDAA", "#65789B", "#F6BD16", "#7262FD", "#78D3F8"};

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
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
