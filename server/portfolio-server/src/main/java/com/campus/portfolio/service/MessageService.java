package com.campus.portfolio.service;

import com.campus.portfolio.common.BizException;
import com.campus.portfolio.common.UserContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 站内消息：发件箱 / 收件箱 / 已读 / 未读数 / 分类统计。
 * 其他模块通过 notify() 触发（请假审批等）。
 */
@Service
public class MessageService {

    private final JdbcTemplate jdbc;

    public MessageService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ------------------------------------------------------------ 发送

    @Transactional
    public long create(Map<String, Object> body, UserContext ctx, boolean publishNow) {
        String title = str(body.get("title"));
        String content = str(body.get("content"));
        if (title == null || title.isBlank()) throw BizException.badRequest("标题不能为空");
        if (content == null || content.isBlank()) throw BizException.badRequest("正文不能为空");

        List<Long> toIds = toLongList(body.get("toUserIds"));
        if (toIds.isEmpty()) throw BizException.badRequest("收件人不能为空");

        int type = toInt(body.get("type"), 2);
        int level = toInt(body.get("level"), 0);
        int status = publishNow ? 1 : toInt(body.get("status"), 0);

        KeyHolder kh = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO msg_notification (type, sender_id, title, level, content, status, send_time, to_user_ids) "
                            + "VALUES (?,?,?,?,?,?,?,?)", new String[]{"id"});
            ps.setInt(1, type);
            ps.setLong(2, ctx.getUserId());
            ps.setString(3, title);
            ps.setInt(4, level);
            ps.setString(5, content);
            ps.setInt(6, status);
            ps.setString(7, status == 1 ? now() : null);
            ps.setString(8, join(toIds));
            return ps;
        }, kh);
        Long id = kh.getKey() == null ? null : kh.getKey().longValue();
        if (id == null) throw BizException.badRequest("消息保存失败");

        if (status == 1) {
            for (Long uid : toIds) {
                jdbc.update("INSERT IGNORE INTO msg_receive (notification_id, to_user_id) VALUES (?, ?)", id, uid);
            }
        }
        return id;
    }

    @Transactional
    public void publish(long id, UserContext ctx) {
        Map<String, Object> n = one("SELECT * FROM msg_notification WHERE id = ? AND deleted = 0", id);
        if (n == null) throw BizException.notFound("消息不存在");
        if (((Number) n.get("status")).intValue() == 1) return;
        jdbc.update("UPDATE msg_notification SET status = 1, send_time = ? WHERE id = ?", now(), id);
        List<Long> toIds = parseIds((String) n.get("to_user_ids"));
        for (Long uid : toIds) {
            jdbc.update("INSERT IGNORE INTO msg_receive (notification_id, to_user_id) VALUES (?, ?)", id, uid);
        }
    }

    /** 系统/业务提醒：按 userId 直接投递，立即发布 */
    @Transactional
    public void notify(List<Long> userIds, int type, String title, String content, int level) {
        if (userIds == null || userIds.isEmpty()) return;
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", title);
        body.put("content", content);
        body.put("toUserIds", userIds);
        body.put("type", type);
        body.put("level", level);
        body.put("status", 1);
        create0(body, null, true);
    }

    /** 按 employeeId 提醒（自动转 userId） */
    public void notifyEmployee(Long employeeId, String title, String content, int level) {
        if (employeeId == null) return;
        Long uid = jdbc.query("SELECT user_id FROM org_employee WHERE id = ?",
                rs -> rs.next() ? rs.getLong(1) : null, employeeId);
        if (uid != null) notify(List.of(uid), 1, title, content, level);
    }

    /**
     * 幂等提醒：同一个人、同标题、同正文，在 windowMinutes 内只发一次。
     * <p>
     * 为什么需要：学生连点提交、审批人反复点通过，都会重复触发提醒，
     * 收件箱里很快堆成一排一模一样的"新的请假申请"。这里在写入前查一次近邻。
     */
    public void notifyEmployeeOnce(Long employeeId, String title, String content, int level) {
        notifyEmployeeOnce(employeeId, title, content, level, 10);
    }

    public void notifyEmployeeOnce(Long employeeId, String title, String content, int level, int windowMinutes) {
        if (employeeId == null) return;
        Long uid = jdbc.query("SELECT user_id FROM org_employee WHERE id = ?",
                rs -> rs.next() ? rs.getLong(1) : null, employeeId);
        if (uid == null) return;
        Integer dup = jdbc.queryForObject(
                "SELECT COUNT(*) FROM msg_receive r JOIN msg_notification n ON n.id = r.notification_id "
                        + "WHERE r.to_user_id = ? AND n.deleted = 0 AND n.title = ? AND n.content = ? "
                        + "AND n.create_time >= DATE_SUB(NOW(), INTERVAL ? MINUTE)",
                Integer.class, uid, title, content, windowMinutes);
        if (dup != null && dup > 0) return;
        notify(java.util.List.of(uid), 1, title, content, level);
    }

    private long create0(Map<String, Object> body, UserContext ctx, boolean publishNow) {
        String title = str(body.get("title"));
        String content = str(body.get("content"));
        List<Long> toIds = toLongList(body.get("toUserIds"));
        if (title == null || title.isBlank() || content == null || toIds.isEmpty()) {
            return 0;
        }
        int type = toInt(body.get("type"), 1);
        int level = toInt(body.get("level"), 0);
        jdbc.update("INSERT INTO msg_notification (type, sender_id, title, level, content, status, send_time, to_user_ids) "
                        + "VALUES (?,?,?,?,?,?,?,?)",
                type, null,
                title, level, content, 1, now(), join(toIds));
        Long id = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        for (Long uid : toIds) {
            jdbc.update("INSERT IGNORE INTO msg_receive (notification_id, to_user_id) VALUES (?, ?)", id, uid);
        }
        return id == null ? 0 : id;
    }

    // ------------------------------------------------------------ 查询

    public Map<String, Object> inbox(UserContext ctx, int pageNum, int pageSize, Map<String, Object> s) {
        StringBuilder where = new StringBuilder(" WHERE r.deleted = 0 AND r.to_user_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(ctx.getUserId());
        String title = str(s.get("title"));
        if (title != null && !title.isBlank()) {
            where.append(" AND n.title LIKE ?");
            args.add("%" + title + "%");
        }
        if (s.get("level") != null) {
            where.append(" AND n.level = ?");
            args.add(toInt(s.get("level"), 0));
        }
        if (s.get("fromId") != null) {
            where.append(" AND n.sender_id = ?");
            args.add(toLong(s.get("fromId")));
        }
        if (s.get("readStatus") != null) {
            where.append(" AND r.read_status = ?");
            args.add(toInt(s.get("readStatus"), 0));
        }
        if (str(s.get("startTime")) != null && !str(s.get("startTime")).isBlank()) {
            where.append(" AND r.create_time >= ?");
            args.add(str(s.get("startTime")) + " 00:00:00");
        }
        if (str(s.get("endTime")) != null && !str(s.get("endTime")).isBlank()) {
            where.append(" AND r.create_time <= ?");
            args.add(str(s.get("endTime")) + " 23:59:59");
        }
        String from = " FROM msg_receive r JOIN msg_notification n ON n.id = r.notification_id";
        Integer total = jdbc.queryForObject("SELECT COUNT(*)" + from + where, Integer.class, args.toArray());
        List<Object> a = new ArrayList<>(args);
        a.add(limit(pageSize));
        a.add(offset(pageNum, pageSize));
        List<Map<String, Object>> list = jdbc.query(
                "SELECT r.id, r.notification_id AS notificationId, r.read_status AS readStatus, "
                        + "r.create_time AS createTime, n.title, n.level, n.type, n.sender_id AS fromId, "
                        + "u.show_name AS fromName" + from
                        + " LEFT JOIN sys_user u ON u.id = n.sender_id" + where
                        + " ORDER BY r.id DESC LIMIT ? OFFSET ?", (rs, i) -> row(rs), a.toArray());
        return page(pageNum, pageSize, total == null ? 0 : total, list);
    }

    public Map<String, Object> sent(UserContext ctx, int pageNum, int pageSize, Map<String, Object> s) {
        StringBuilder where = new StringBuilder(" WHERE n.deleted = 0 AND n.sender_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(ctx.getUserId());
        String title = str(s.get("title"));
        if (title != null && !title.isBlank()) {
            where.append(" AND n.title LIKE ?");
            args.add("%" + title + "%");
        }
        if (s.get("status") != null) {
            where.append(" AND n.status = ?");
            args.add(toInt(s.get("status"), 0));
        }
        Integer total = jdbc.queryForObject("SELECT COUNT(*) FROM msg_notification n" + where, Integer.class, args.toArray());
        List<Object> a = new ArrayList<>(args);
        a.add(limit(pageSize));
        a.add(offset(pageNum, pageSize));
        List<Map<String, Object>> list = jdbc.query(
                "SELECT n.id, n.title, n.level, n.type, n.status, n.send_time AS sendTime, n.to_user_ids AS toUserIds "
                        + "FROM msg_notification n" + where + " ORDER BY n.id DESC LIMIT ? OFFSET ?",
                (rs, i) -> row(rs), a.toArray());
        return page(pageNum, pageSize, total == null ? 0 : total, list);
    }

    @Transactional
    public Map<String, Object> inboxDetail(long id, UserContext ctx) {
        Map<String, Object> m = one("SELECT r.id, r.notification_id AS notificationId, r.read_status AS readStatus, "
                + "r.create_time AS createTime, n.title, n.level, n.type, n.content, n.send_time AS sendTime, "
                + "u.show_name AS fromName FROM msg_receive r "
                + "JOIN msg_notification n ON n.id = r.notification_id "
                + "LEFT JOIN sys_user u ON u.id = n.sender_id "
                + "WHERE r.id = ? AND r.to_user_id = ?", id, ctx.getUserId());
        if (m == null) throw BizException.notFound("消息不存在");
        if (((Number) m.get("readStatus")).intValue() == 0) {
            jdbc.update("UPDATE msg_receive SET read_status = 1, read_time = ? WHERE id = ?", now(), id);
            m.put("readStatus", 1);
        }
        return m;
    }

    public Map<String, Object> sentDetail(long id, UserContext ctx) {
        Map<String, Object> m = one("SELECT n.id, n.title, n.level, n.type, n.content, n.status, "
                + "n.send_time AS sendTime, n.to_user_ids AS toUserIds FROM msg_notification n "
                + "WHERE n.id = ? AND n.sender_id = ? AND n.deleted = 0", id, ctx.getUserId());
        if (m == null) throw BizException.notFound("消息不存在");
        return m;
    }

    public Map<String, Object> unread(UserContext ctx) {
        Integer total = jdbc.queryForObject("SELECT COUNT(*) FROM msg_receive WHERE to_user_id = ? AND read_status = 0 "
                + "AND deleted = 0", Integer.class, ctx.getUserId());
        List<Map<String, Object>> byType = jdbc.query("SELECT n.type, COUNT(*) AS cnt FROM msg_receive r "
                + "JOIN msg_notification n ON n.id = r.notification_id "
                + "WHERE r.to_user_id = ? AND r.read_status = 0 AND r.deleted = 0 GROUP BY n.type",
                (rs, i) -> Map.<String, Object>of("type", rs.getInt("type"), "count", rs.getInt("cnt")), ctx.getUserId());
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("total", total == null ? 0 : total);
        res.put("byType", byType);
        return res;
    }

    public List<Map<String, Object>> senders(UserContext ctx) {
        return jdbc.query("SELECT DISTINCT n.sender_id AS id, u.show_name AS name FROM msg_receive r "
                        + "JOIN msg_notification n ON n.id = r.notification_id "
                        + "LEFT JOIN sys_user u ON u.id = n.sender_id "
                        + "WHERE r.to_user_id = ? ORDER BY n.sender_id", (rs, i) -> row(rs), ctx.getUserId());
    }

    public void read(long id, UserContext ctx) {
        jdbc.update("UPDATE msg_receive SET read_status = 1, read_time = ? WHERE id = ? AND to_user_id = ?",
                now(), id, ctx.getUserId());
    }

    public void deleteInbox(long id, UserContext ctx) {
        jdbc.update("UPDATE msg_receive SET deleted = 1 WHERE id = ? AND to_user_id = ?", id, ctx.getUserId());
    }

    public void deleteSent(long id, UserContext ctx) {
        jdbc.update("UPDATE msg_notification SET deleted = 1 WHERE id = ? AND sender_id = ?", id, ctx.getUserId());
    }

    // ------------------------------------------------------------ 工具

    private Map<String, Object> one(String sql, Object... args) {
        List<Map<String, Object>> list = jdbc.query(sql, (rs, i) -> row(rs), args);
        return list.isEmpty() ? null : list.get(0);
    }

    private static Map<String, Object> row(ResultSet rs) throws SQLException {
        Map<String, Object> m = new LinkedHashMap<>();
        ResultSetMetaData md = rs.getMetaData();
        for (int i = 1; i <= md.getColumnCount(); i++) {
            String label = md.getColumnLabel(i);
            Object v = md.getColumnType(i) == Types.TIMESTAMP
                    ? rs.getObject(i, LocalDateTime.class)
                    : rs.getObject(i);
            m.put(label, v);
        }
        return m;
    }

    private Map<String, Object> page(int pageNum, int pageSize, int total, List<?> list) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("pageNum", pageNum);
        m.put("pageSize", pageSize);
        m.put("total", total);
        m.put("list", list);
        return m;
    }

    private String now() {
        return LocalDateTime.now().toString().replace('T', ' ');
    }

    private int limit(int pageSize) {
        return Math.min(Math.max(pageSize, 1), 100);
    }

    private int offset(int pageNum, int pageSize) {
        return (Math.max(pageNum, 1) - 1) * limit(pageSize);
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static Long toLong(Object o) {
        if (o == null || "".equals(o)) return null;
        if (o instanceof Number n) return n.longValue();
        try { return Long.parseLong(String.valueOf(o)); } catch (NumberFormatException e) { return null; }
    }

    private static Integer toInt(Object o, int def) {
        if (o == null || "".equals(o)) return def;
        if (o instanceof Number n) return n.intValue();
        try { return Integer.parseInt(String.valueOf(o)); } catch (NumberFormatException e) { return def; }
    }

    private static String join(List<Long> ids) {
        StringBuilder sb = new StringBuilder();
        for (Long id : ids) {
            if (sb.length() > 0) sb.append(',');
            sb.append(id);
        }
        return sb.toString();
    }

    private static List<Long> parseIds(String s) {
        List<Long> list = new ArrayList<>();
        if (s == null) return list;
        for (String p : s.split(",")) {
            if (!p.isBlank()) {
                try { list.add(Long.parseLong(p.trim())); } catch (NumberFormatException ignore) { }
            }
        }
        return list;
    }

    private static List<Long> toLongList(Object o) {
        List<Long> list = new ArrayList<>();
        if (o == null) return list;
        if (o instanceof Collection<?> c) {
            for (Object x : c) {
                Long v = toLong(x);
                if (v != null) list.add(v);
            }
        } else {
            String s = String.valueOf(o);
            if (!s.isBlank()) list.addAll(parseIds(s));
        }
        return list;
    }
}
