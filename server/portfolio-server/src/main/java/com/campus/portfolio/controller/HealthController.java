package com.campus.portfolio.controller;

import com.campus.portfolio.common.Result;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
public class HealthController {

    private final JdbcTemplate jdbc;

    public HealthController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/health")
    public Result<Map<String, Object>> health() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("status", "UP");
        m.put("app", "portfolio");
        m.put("java", System.getProperty("java.version"));
        m.put("tz", System.getProperty("user.timezone"));
        try {
            Integer one = jdbc.queryForObject("SELECT 1", Integer.class);
            String db = jdbc.queryForObject("SELECT database()", String.class);
            Integer tables = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = ?",
                    Integer.class, db);
            m.put("db", one != null && one == 1 ? "UP" : "DOWN");
            m.put("dbName", db);
            m.put("tables", tables);
        } catch (Exception e) {
            m.put("db", "DOWN");
            m.put("dbError", e.getMessage());
        }
        return Result.ok(m);
    }
}
