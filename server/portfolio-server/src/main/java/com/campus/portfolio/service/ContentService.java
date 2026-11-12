package com.campus.portfolio.service;

import com.campus.portfolio.common.BizException;
import com.campus.portfolio.common.UserContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 内容：新闻 / 广告 / 赛事 / 红黑榜 / 点赞。
 * 可见性统一规则：内容.visible_org_id 必须落在「当前用户组织路径的祖先集合」内。
 * 家长：取所有已关联学生组织路径的并集；黑榜额外要求 relative_visible = 1。
 */
@Service
public class ContentService {

    private final JdbcTemplate jdbc;

    public ContentService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ------------------------------------------------------------ 可见性

    public List<Long> orgScope(UserContext ctx) {
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        addPath(ids, ctx.getOrgPath());
        if (ctx.hasRole("PARENT") && ctx.getUserId() != null) {
            jdbc.queryForList("SELECT ou.path FROM rel_relative_link r "
                    + "JOIN org_employee e ON e.id = r.employee_id "
                    + "JOIN org_employee_unit eu ON eu.employee_id = e.id "
                    + "JOIN org_unit ou ON ou.id = eu.org_id "
                    + "WHERE r.parent_user_id = ? AND r.status = 1", String.class, ctx.getUserId())
                    .forEach(p -> addPath(ids, p));
        }
        if (ids.isEmpty() && ctx.getRootOrgId() != null) {
            ids.add(ctx.getRootOrgId());
        }
        return new ArrayList<>(ids);
    }

    private void addPath(Set<Long> ids, String path) {
        if (path == null) return;
        for (String s : path.split("/")) {
            if (!s.isBlank()) {
                try { ids.add(Long.parseLong(s)); } catch (NumberFormatException ignore) { }
            }
        }
    }

    private String inSql(int n) {
        return String.join(",", Collections.nCopies(n, "?"));
    }

    /** 家长已关联的学生 employeeId（学生本人走自己的 employeeId） */
    private List<Long> childrenEmployeeIds(UserContext ctx) {
        if (!ctx.hasRole("PARENT") || ctx.getUserId() == null) return List.of();
        return jdbc.queryForList("SELECT employee_id FROM rel_relative_link WHERE parent_user_id = ? AND status = 1",
                Long.class, ctx.getUserId());
    }

    // ------------------------------------------------------------ 新闻

    public Map<String, Object> newsPortal(int pageNum, int pageSize, UserContext ctx) {
        boolean admin = ctx.hasRole("ADMIN");
        List<Long> scope = orgScope(ctx);
        if (!admin && scope.isEmpty()) return page(pageNum, pageSize, 0, List.of());
        List<Object> args = new ArrayList<>(admin ? List.of() : scope);
        String where = " FROM pub_news n WHERE n.deleted = 0 AND n.status = 1 "
                + "AND NOW() BETWEEN n.start_time AND n.end_time"
                + (admin ? "" : " AND n.visible_org_id IN (" + inSql(scope.size()) + ")");
        Integer total = jdbc.queryForObject("SELECT COUNT(*)" + where, Integer.class, args.toArray());
        List<Object> a = new ArrayList<>(args);
        a.add(limit(pageSize));
        a.add(offset(pageNum, pageSize));
        List<Map<String, Object>> list = jdbc.query(
                "SELECT n.id, n.title, n.sub_title AS subTitle, n.news_type AS newsType, n.cover_image AS coverImage, "
                        + "n.start_time AS startTime, n.end_time AS endTime, n.view_count AS viewCount, "
                        + "n.like_count AS likeCount" + where + " ORDER BY n.start_time DESC LIMIT ? OFFSET ?",
                (rs, i) -> row(rs), a.toArray());
        return page(pageNum, pageSize, total == null ? 0 : total, list);
    }

    public Map<String, Object> newsDetail(long id, UserContext ctx) {
        Map<String, Object> m = one("SELECT n.id, n.title, n.sub_title AS subTitle, n.news_type AS newsType, "
                + "n.cover_image AS coverImage, n.start_time AS startTime, n.end_time AS endTime, "
                + "n.view_count AS viewCount, n.like_count AS likeCount, c.content AS content, "
                + "n.visible_org_id AS visibleOrgId "
                + "FROM pub_news n LEFT JOIN pub_news_content c ON c.news_id = n.id "
                + "WHERE n.id = ? AND n.deleted = 0", id);
        if (m == null) throw BizException.notFound("新闻不存在");
        Long visible = ((Number) m.get("visibleOrgId")).longValue();
        assertVisible(visible, ctx);
        jdbc.update("UPDATE pub_news SET view_count = view_count + 1 WHERE id = ?", id);
        m.remove("visibleOrgId");
        m.put("liked", isLiked("NEWS", id, ctx));
        return m;
    }

    public long saveNews(Map<String, Object> body, UserContext ctx) {
        Long id = toLong(body.get("id"));
        String title = str(body.get("title"));
        if (title == null || title.isBlank()) throw BizException.badRequest("标题不能为空");
        Long visibleOrgId = toLong(body.get("visibleOrgId"));
        if (visibleOrgId == null) visibleOrgId = 1L;
        Integer status = toInt(body.get("status"), 0);
        Integer newsType = toInt(body.get("newsType"), 1);
        String start = str(body.get("startTime"));
        String end = str(body.get("endTime"));
        if (id == null) {
            KeyHolder kh = new GeneratedKeyHolder();
            Long vid = visibleOrgId;
            jdbc.update(con -> {
                PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO pub_news (title, sub_title, news_type, cover_image, visible_org_id, "
                                + "start_time, end_time, status, publisher_id) VALUES (?,?,?,?,?,?,?,?,?)",
                        new String[]{"id"});
                ps.setString(1, title);
                ps.setString(2, str(body.get("subTitle")));
                ps.setInt(3, newsType);
                ps.setString(4, str(body.get("coverImage")));
                ps.setLong(5, vid);
                ps.setString(6, start == null ? "2026-01-01 00:00:00" : start);
                ps.setString(7, end == null ? "2099-12-31 23:59:59" : end);
                ps.setInt(8, status);
                ps.setLong(9, ctx.getUserId() == null ? 1L : ctx.getUserId());
                return ps;
            }, kh);
            id = kh.getKey() == null ? null : kh.getKey().longValue();
        } else {
            jdbc.update("UPDATE pub_news SET title=?, sub_title=?, news_type=?, cover_image=?, visible_org_id=?, "
                            + "start_time=?, end_time=?, status=? WHERE id=?",
                    title, str(body.get("subTitle")), newsType, str(body.get("coverImage")), visibleOrgId,
                    start, end, status, id);
        }
        if (id == null) throw BizException.badRequest("保存失败");
        String content = str(body.get("content"));
        if (content != null) {
            Integer c = jdbc.queryForObject("SELECT COUNT(*) FROM pub_news_content WHERE news_id = ?", Integer.class, id);
            if (c == null || c == 0) {
                jdbc.update("INSERT INTO pub_news_content (news_id, content) VALUES (?, ?)", id, content);
            } else {
                jdbc.update("UPDATE pub_news_content SET content = ? WHERE news_id = ?", content, id);
            }
        }
        return id;
    }

    public Map<String, Object> newsSearch(Map<String, Object> body) {
        int pageNum = toInt(body.get("pageNum"), 1);
        int pageSize = toInt(body.get("pageSize"), 10);
        @SuppressWarnings("unchecked")
        Map<String, Object> s = body.get("searchModel") instanceof Map<?, ?> mm ? (Map<String, Object>) mm : Map.of();
        String where = " FROM pub_news n WHERE n.deleted = 0";
        List<Object> args = new ArrayList<>();
        if (str(s.get("title")) != null && !str(s.get("title")).isBlank()) {
            where += " AND n.title LIKE ?";
            args.add("%" + str(s.get("title")) + "%");
        }
        if (s.get("status") != null) {
            where += " AND n.status = ?";
            args.add(toInt(s.get("status"), 0));
        }
        Integer total = jdbc.queryForObject("SELECT COUNT(*)" + where, Integer.class, args.toArray());
        List<Object> a = new ArrayList<>(args);
        a.add(limit(pageSize));
        a.add(offset(pageNum, pageSize));
        List<Map<String, Object>> list = jdbc.query(
                "SELECT n.id, n.title, n.sub_title AS subTitle, n.news_type AS newsType, n.status, "
                        + "n.visible_org_id AS visibleOrgId, n.start_time AS startTime, n.end_time AS endTime, "
                        + "n.view_count AS viewCount" + where + " ORDER BY n.id DESC LIMIT ? OFFSET ?",
                (rs, i) -> row(rs), a.toArray());
        return page(pageNum, pageSize, total == null ? 0 : total, list);
    }

    public void deleteNews(long id) {
        jdbc.update("UPDATE pub_news SET deleted = 1 WHERE id = ?", id);
    }

    // ------------------------------------------------------------ 广告

    public List<Map<String, Object>> adPortal(UserContext ctx) {
        boolean admin = ctx.hasRole("ADMIN");
        List<Long> scope = orgScope(ctx);
        if (!admin && scope.isEmpty()) return List.of();
        List<Object> args = new ArrayList<>(admin ? List.of() : scope);
        String where = " FROM pub_ad a WHERE a.deleted = 0 AND a.status = 1 "
                + "AND NOW() BETWEEN a.start_time AND a.end_time"
                + (admin ? "" : " AND a.visible_org_id IN (" + inSql(scope.size()) + ")");
        return jdbc.query("SELECT a.id, a.title, a.sub_title AS subTitle, a.image, a.link_type AS linkType, "
                        + "a.link, a.start_time AS startTime, a.end_time AS endTime, a.sort" + where
                        + " ORDER BY a.sort, a.id DESC",
                (rs, i) -> row(rs), args.toArray());
    }

    public long saveAd(Map<String, Object> body) {
        Long id = toLong(body.get("id"));
        String image = str(body.get("image"));
        if (image == null || image.isBlank()) throw BizException.badRequest("广告图片不能为空");
        Long visibleOrgId = toLong(body.get("visibleOrgId"));
        if (id == null) {
            jdbc.update("INSERT INTO pub_ad (title, sub_title, image, link_type, link, start_time, end_time, status, "
                            + "visible_org_id, sort) VALUES (?,?,?,?,?,?,?,?,?,?)",
                    str(body.get("title")), str(body.get("subTitle")), image,
                    toInt(body.get("linkType"), 1), str(body.get("link")),
                    str(body.get("startTime")), str(body.get("endTime")),
                    toInt(body.get("status"), 1), visibleOrgId == null ? 1L : visibleOrgId, toInt(body.get("sort"), 0));
            return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        }
        jdbc.update("UPDATE pub_ad SET title=?, sub_title=?, image=?, link_type=?, link=?, start_time=?, end_time=?, "
                        + "status=?, visible_org_id=?, sort=? WHERE id=?",
                str(body.get("title")), str(body.get("subTitle")), image, toInt(body.get("linkType"), 1),
                str(body.get("link")), str(body.get("startTime")), str(body.get("endTime")),
                toInt(body.get("status"), 1), visibleOrgId == null ? 1L : visibleOrgId, toInt(body.get("sort"), 0), id);
        return id;
    }

    public Map<String, Object> adSearch(Map<String, Object> body) {
        int pageNum = toInt(body.get("pageNum"), 1);
        int pageSize = toInt(body.get("pageSize"), 10);
        Integer total = jdbc.queryForObject("SELECT COUNT(*) FROM pub_ad WHERE deleted = 0", Integer.class);
        List<Map<String, Object>> list = jdbc.query(
                "SELECT id, title, sub_title AS subTitle, image, link_type AS linkType, link, status, "
                        + "visible_org_id AS visibleOrgId, start_time AS startTime, end_time AS endTime, sort "
                        + "FROM pub_ad WHERE deleted = 0 ORDER BY id DESC LIMIT ? OFFSET ?",
                (rs, i) -> row(rs), limit(pageSize), offset(pageNum, pageSize));
        return page(pageNum, pageSize, total == null ? 0 : total, list);
    }

    public void deleteAd(long id) {
        jdbc.update("UPDATE pub_ad SET deleted = 1 WHERE id = ?", id);
    }

    // ------------------------------------------------------------ 红黑榜

    public List<Map<String, Object>> rankLabels(Boolean red) {
        String sql = "SELECT id, name, red FROM pub_rank_label WHERE 1=1 "
                + (red == null ? "" : "AND red = " + (red ? 1 : 0)) + " ORDER BY sort, id";
        return jdbc.query(sql, (rs, i) -> row(rs));
    }

    public Map<String, Object> rankPortal(Boolean red, int pageNum, int pageSize, UserContext ctx) {
        List<Long> empIds = new ArrayList<>();
        if (ctx.getEmployeeId() != null) empIds.add(ctx.getEmployeeId());
        empIds.addAll(childrenEmployeeIds(ctx));

        boolean staff = ctx.hasRole("ADMIN") || ctx.hasRole("COUNSELOR") || ctx.hasRole("SECRETARY");
        StringBuilder where = new StringBuilder(" FROM pub_rank r LEFT JOIN pub_rank_label l ON l.id = r.label_id "
                + "LEFT JOIN org_employee e ON e.id = r.employee_id WHERE r.deleted = 0 AND r.status = 1");
        List<Object> args = new ArrayList<>();
        if (red != null) {
            where.append(" AND r.red = ?");
            args.add(red ? 1 : 0);
        }
        if (!staff) {
            if (empIds.isEmpty()) return page(pageNum, pageSize, 0, List.of());
            where.append(" AND r.employee_id IN (").append(inSql(empIds.size())).append(")");
            args.addAll(empIds);
            // 家长看黑榜：只允许 relative_visible = 1
            if (!ctx.hasRole("STUDENT")) {
                where.append(" AND (r.red = 1 OR r.relative_visible = 1)");
            }
        }
        Integer total = jdbc.queryForObject("SELECT COUNT(*)" + where, Integer.class, args.toArray());
        List<Object> a = new ArrayList<>(args);
        a.add(limit(pageSize));
        a.add(offset(pageNum, pageSize));
        List<Map<String, Object>> list = jdbc.query(
                "SELECT r.id, r.title, r.sub_title AS subTitle, r.red, r.type, r.achievement, r.image, "
                        + "r.update_time AS updateTime, r.relative_visible AS relativeVisible, "
                        + "l.name AS labelName, e.name AS employeeName" + where
                        + " ORDER BY r.update_time DESC LIMIT ? OFFSET ?", (rs, i) -> row(rs), a.toArray());
        return page(pageNum, pageSize, total == null ? 0 : total, list);
    }

    public Map<String, Object> rankDetail(long id, UserContext ctx) {
        Map<String, Object> m = one("SELECT r.id, r.employee_id AS employeeId, r.title, r.sub_title AS subTitle, "
                + "r.red, r.type, r.label_id AS labelId, r.achievement, r.image, r.update_time AS updateTime, "
                + "r.relative_visible AS relativeVisible, r.like_count AS likeCount, e.name AS employeeName, "
                + "l.name AS labelName, c.content AS content "
                + "FROM pub_rank r LEFT JOIN pub_rank_content c ON c.rank_id = r.id "
                + "LEFT JOIN pub_rank_label l ON l.id = r.label_id "
                + "LEFT JOIN org_employee e ON e.id = r.employee_id "
                + "WHERE r.id = ? AND r.deleted = 0 AND r.status = 1", id);
        if (m == null) throw BizException.notFound("红黑榜记录不存在");
        long empId = ((Number) m.get("employeeId")).longValue();
        boolean mine = ctx.getEmployeeId() != null && ctx.getEmployeeId() == empId;
        boolean child = childrenEmployeeIds(ctx).contains(empId);
        boolean staff = ctx.hasRole("ADMIN") || ctx.hasRole("COUNSELOR") || ctx.hasRole("SECRETARY");
        if (!mine && !child && !staff) throw BizException.forbidden("无权查看该记录");
        if (child && !mine && !staff) {
            boolean red = ((Number) m.get("red")).intValue() == 1;
            boolean visible = ((Number) m.get("relativeVisible")).intValue() == 1;
            if (!red && !visible) throw BizException.forbidden("该记录未对学生家长开放");
        }
        m.put("liked", isLiked("RANK", id, ctx));
        return m;
    }

    public long saveRank(Map<String, Object> body, UserContext ctx) {
        Long id = toLong(body.get("id"));
        Long employeeId = toLong(body.get("employeeId"));
        if (employeeId == null) throw BizException.badRequest("employeeId 不能为空");
        String title = str(body.get("title"));
        if (title == null || title.isBlank()) throw BizException.badRequest("标题不能为空");
        boolean red = Boolean.TRUE.equals(body.get("red")) || "1".equals(String.valueOf(body.get("red")));
        String updateTime = str(body.get("updateTime"));
        if (id == null) {
            jdbc.update("INSERT INTO pub_rank (employee_id, title, sub_title, red, label_id, type, achievement, "
                            + "update_time, status, relative_visible, image, publisher_id) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",
                    employeeId, title, str(body.get("subTitle")), red ? 1 : 0, toLong(body.get("labelId")),
                    toInt(body.get("type"), 1), str(body.get("achievement")),
                    updateTime == null ? LocalDateTime.now().toString().replace('T', ' ') : updateTime,
                    toInt(body.get("status"), 1),
                    Boolean.FALSE.equals(body.get("relativeVisible")) ? 0 : 1,
                    str(body.get("image")), ctx.getUserId());
            id = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        } else {
            jdbc.update("UPDATE pub_rank SET employee_id=?, title=?, sub_title=?, red=?, label_id=?, type=?, "
                            + "achievement=?, update_time=?, status=?, relative_visible=?, image=? WHERE id=?",
                    employeeId, title, str(body.get("subTitle")), red ? 1 : 0, toLong(body.get("labelId")),
                    toInt(body.get("type"), 1), str(body.get("achievement")),
                    updateTime == null ? LocalDateTime.now().toString().replace('T', ' ') : updateTime,
                    toInt(body.get("status"), 1),
                    Boolean.FALSE.equals(body.get("relativeVisible")) ? 0 : 1,
                    str(body.get("image")), id);
        }
        String content = str(body.get("content"));
        if (content != null && id != null) {
            Integer c = jdbc.queryForObject("SELECT COUNT(*) FROM pub_rank_content WHERE rank_id = ?", Integer.class, id);
            if (c == null || c == 0) {
                jdbc.update("INSERT INTO pub_rank_content (rank_id, content) VALUES (?, ?)", id, content);
            } else {
                jdbc.update("UPDATE pub_rank_content SET content = ? WHERE rank_id = ?", content, id);
            }
        }
        return id == null ? 0 : id;
    }

    public Map<String, Object> rankSearch(Map<String, Object> body) {
        int pageNum = toInt(body.get("pageNum"), 1);
        int pageSize = toInt(body.get("pageSize"), 10);
        Integer total = jdbc.queryForObject("SELECT COUNT(*) FROM pub_rank WHERE deleted = 0", Integer.class);
        List<Map<String, Object>> list = jdbc.query(
                "SELECT r.id, r.employee_id AS employeeId, e.name AS employeeName, r.title, r.red, r.type, "
                        + "r.update_time AS updateTime, r.status, r.relative_visible AS relativeVisible, l.name AS labelName "
                        + "FROM pub_rank r LEFT JOIN org_employee e ON e.id = r.employee_id "
                        + "LEFT JOIN pub_rank_label l ON l.id = r.label_id "
                        + "WHERE r.deleted = 0 ORDER BY r.id DESC LIMIT ? OFFSET ?",
                (rs, i) -> row(rs), limit(pageSize), offset(pageNum, pageSize));
        return page(pageNum, pageSize, total == null ? 0 : total, list);
    }

    public void deleteRank(long id) {
        jdbc.update("UPDATE pub_rank SET deleted = 1 WHERE id = ?", id);
    }

    // ------------------------------------------------------------ 赛事

    public Map<String, Object> matchPortal(int pageNum, int pageSize, UserContext ctx) {
        boolean admin = ctx.hasRole("ADMIN");
        List<Long> scope = orgScope(ctx);
        if (!admin && scope.isEmpty()) return page(pageNum, pageSize, 0, List.of());
        List<Object> args = new ArrayList<>(admin ? List.of() : scope);
        String where = " FROM pub_match m WHERE m.deleted = 0 AND m.status = 1"
                + (admin ? "" : " AND m.visible_org_id IN (" + inSql(scope.size()) + ")");
        Integer total = jdbc.queryForObject("SELECT COUNT(*)" + where, Integer.class, args.toArray());
        List<Object> a = new ArrayList<>(args);
        a.add(limit(pageSize));
        a.add(offset(pageNum, pageSize));
        List<Map<String, Object>> list = jdbc.query(
                "SELECT m.id, m.title, m.sub_title AS subTitle, m.cover_image AS coverImage, "
                        + "m.start_show_time AS startShowTime, m.start_register_time AS startRegisterTime, "
                        + "m.end_register_time AS endRegisterTime, m.match_time AS matchTime, "
                        + "m.group_count AS groupCount, m.status" + where
                        + " ORDER BY m.match_time DESC LIMIT ? OFFSET ?", (rs, i) -> row(rs), a.toArray());
        for (Map<String, Object> m : list) {
            m.put("canRegister", canRegister(((Number) m.get("id")).longValue(), ctx));
        }
        return page(pageNum, pageSize, total == null ? 0 : total, list);
    }

    public Map<String, Object> matchDetail(long id, UserContext ctx) {
        Map<String, Object> m = one("SELECT m.id, m.title, m.sub_title AS subTitle, m.cover_image AS coverImage, "
                + "m.visible_org_id AS visibleOrgId, m.start_show_time AS startShowTime, "
                + "m.start_register_time AS startRegisterTime, m.end_register_time AS endRegisterTime, "
                + "m.match_time AS matchTime, m.group_count AS groupCount, m.status, c.content AS content "
                + "FROM pub_match m LEFT JOIN pub_match_content c ON c.match_id = m.id "
                + "WHERE m.id = ? AND m.deleted = 0", id);
        if (m == null) throw BizException.notFound("赛事不存在");
        assertVisible(((Number) m.get("visibleOrgId")).longValue(), ctx);
        m.remove("visibleOrgId");
        m.put("canRegister", canRegister(id, ctx));
        m.put("registered", isRegistered(id, ctx));
        return m;
    }

    private boolean canRegister(long matchId, UserContext ctx) {
        if (ctx.getEmployeeId() == null) return false;
        Integer c = jdbc.queryForObject("SELECT COUNT(*) FROM pub_match WHERE id = ? AND deleted = 0 AND status = 1 "
                + "AND NOW() BETWEEN start_register_time AND end_register_time", Integer.class, matchId);
        return c != null && c > 0 && !isRegistered(matchId, ctx);
    }

    private boolean isRegistered(long matchId, UserContext ctx) {
        if (ctx.getEmployeeId() == null) return false;
        Integer c = jdbc.queryForObject("SELECT COUNT(*) FROM pub_match_register WHERE match_id = ? "
                + "AND leader_employee_id = ? AND status <> 3", Integer.class, matchId, ctx.getEmployeeId());
        return c != null && c > 0;
    }

    public long saveMatch(Map<String, Object> body) {
        Long id = toLong(body.get("id"));
        String title = str(body.get("title"));
        if (title == null || title.isBlank()) throw BizException.badRequest("赛事标题不能为空");
        Long visibleOrgId = toLong(body.get("visibleOrgId"));
        if (id == null) {
            jdbc.update("INSERT INTO pub_match (title, sub_title, cover_image, visible_org_id, start_show_time, "
                            + "start_register_time, end_register_time, match_time, group_count, manager_id, status) "
                            + "VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                    title, str(body.get("subTitle")), str(body.get("coverImage")),
                    visibleOrgId == null ? 1L : visibleOrgId,
                    str(body.get("startShowTime")), str(body.get("startRegisterTime")),
                    str(body.get("endRegisterTime")), str(body.get("matchTime")),
                    toInt(body.get("groupCount"), 1), toLong(body.get("managerId")), toInt(body.get("status"), 1));
            id = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        } else {
            jdbc.update("UPDATE pub_match SET title=?, sub_title=?, cover_image=?, visible_org_id=?, start_show_time=?, "
                            + "start_register_time=?, end_register_time=?, match_time=?, group_count=?, status=? WHERE id=?",
                    title, str(body.get("subTitle")), str(body.get("coverImage")),
                    visibleOrgId == null ? 1L : visibleOrgId,
                    str(body.get("startShowTime")), str(body.get("startRegisterTime")),
                    str(body.get("endRegisterTime")), str(body.get("matchTime")),
                    toInt(body.get("groupCount"), 1), toInt(body.get("status"), 1), id);
        }
        String content = str(body.get("content"));
        if (content != null && id != null) {
            Integer c = jdbc.queryForObject("SELECT COUNT(*) FROM pub_match_content WHERE match_id = ?", Integer.class, id);
            if (c == null || c == 0) {
                jdbc.update("INSERT INTO pub_match_content (match_id, content) VALUES (?, ?)", id, content);
            } else {
                jdbc.update("UPDATE pub_match_content SET content = ? WHERE match_id = ?", content, id);
            }
        }
        return id == null ? 0 : id;
    }

    public Map<String, Object> matchSearch(Map<String, Object> body) {
        int pageNum = toInt(body.get("pageNum"), 1);
        int pageSize = toInt(body.get("pageSize"), 10);
        Integer total = jdbc.queryForObject("SELECT COUNT(*) FROM pub_match WHERE deleted = 0", Integer.class);
        List<Map<String, Object>> list = jdbc.query(
                "SELECT id, title, sub_title AS subTitle, visible_org_id AS visibleOrgId, status, "
                        + "start_register_time AS startRegisterTime, end_register_time AS endRegisterTime, "
                        + "match_time AS matchTime, group_count AS groupCount "
                        + "FROM pub_match WHERE deleted = 0 ORDER BY id DESC LIMIT ? OFFSET ?",
                (rs, i) -> row(rs), limit(pageSize), offset(pageNum, pageSize));
        return page(pageNum, pageSize, total == null ? 0 : total, list);
    }

    public void deleteMatch(long id) {
        jdbc.update("UPDATE pub_match SET deleted = 1 WHERE id = ?", id);
    }

    public long registerMatch(long matchId, Map<String, Object> body, UserContext ctx) {
        if (ctx.getEmployeeId() == null) throw BizException.badRequest("当前账号未绑定员工档案");
        if (!canRegister(matchId, ctx)) {
            throw BizException.badRequest("MATCH_REGISTER_CLOSED", "当前不在报名时间内，或你已报名");
        }
        jdbc.update("INSERT INTO pub_match_register (match_id, leader_employee_id, team_name, member_names, contact, "
                        + "remark, status) VALUES (?,?,?,?,?,?,0)",
                matchId, ctx.getEmployeeId(), str(body.get("teamName")), str(body.get("memberNames")),
                str(body.get("contact")), str(body.get("remark")));
        return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    // ------------------------------------------------------------ 点赞

    public Map<String, Object> like(String targetType, long targetId, UserContext ctx, boolean on) {
        if (!"NEWS".equals(targetType) && !"RANK".equals(targetType)) {
            throw BizException.badRequest("targetType 只能是 NEWS 或 RANK");
        }
        String table = "NEWS".equals(targetType) ? "pub_news" : "pub_rank";
        int changed;
        if (on) {
            changed = jdbc.update("INSERT IGNORE INTO pub_like (target_type, target_id, user_id) VALUES (?,?,?)",
                    targetType, targetId, ctx.getUserId());
            if (changed > 0) jdbc.update("UPDATE " + table + " SET like_count = like_count + 1 WHERE id = ?", targetId);
        } else {
            changed = jdbc.update("DELETE FROM pub_like WHERE target_type = ? AND target_id = ? AND user_id = ?",
                    targetType, targetId, ctx.getUserId());
            if (changed > 0) jdbc.update("UPDATE " + table + " SET like_count = GREATEST(like_count - 1, 0) WHERE id = ?", targetId);
        }
        Integer count = jdbc.queryForObject("SELECT like_count FROM " + table + " WHERE id = ?", Integer.class, targetId);
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("liked", on);
        res.put("likeCount", count == null ? 0 : count);
        return res;
    }

    private boolean isLiked(String type, long id, UserContext ctx) {
        if (ctx.getUserId() == null) return false;
        Integer c = jdbc.queryForObject("SELECT COUNT(*) FROM pub_like WHERE target_type = ? AND target_id = ? AND user_id = ?",
                Integer.class, type, id, ctx.getUserId());
        return c != null && c > 0;
    }

    // ------------------------------------------------------------ 工具

    private void assertVisible(long visibleOrgId, UserContext ctx) {
        List<Long> scope = orgScope(ctx);
        boolean admin = ctx.hasRole("ADMIN");
        if (!admin && !scope.contains(visibleOrgId)) {
            throw BizException.forbidden("无权查看该内容（不在你的组织可见范围）");
        }
    }

    private Map<String, Object> one(String sql, Object... args) {
        List<Map<String, Object>> list = jdbc.query(sql, (rs, i) -> row(rs), args);
        return list.isEmpty() ? null : list.get(0);
    }

    private static Map<String, Object> row(ResultSet rs) throws SQLException {
        Map<String, Object> m = new LinkedHashMap<>();
        ResultSetMetaData md = rs.getMetaData();
        for (int i = 1; i <= md.getColumnCount(); i++) {
            String label = md.getColumnLabel(i);
            Object v;
            if (md.getColumnType(i) == Types.DATE) {
                v = rs.getObject(i, LocalDate.class);
            } else if (md.getColumnType(i) == Types.TIMESTAMP) {
                v = rs.getObject(i, LocalDateTime.class);
            } else {
                v = rs.getObject(i);
            }
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
}
