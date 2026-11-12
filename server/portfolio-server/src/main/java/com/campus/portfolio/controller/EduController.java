package com.campus.portfolio.controller;

import com.campus.portfolio.common.BizException;
import com.campus.portfolio.common.Result;
import com.campus.portfolio.common.UserContext;
import com.campus.portfolio.service.EduService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "教务", description = "课表 / 课程评价")
@RestController
@RequestMapping("/v1/edu")
public class EduController {

    private final EduService eduService;

    public EduController(EduService eduService) {
        this.eduService = eduService;
    }

    private void requireAdmin() {
        if (!UserContext.require().hasRole("ADMIN")) throw BizException.forbidden("只有管理员可以执行该操作");
    }

    @Operation(summary = "当前学期与周次")
    @GetMapping("/term/current")
    public Result<Map<String, Object>> term() {
        return Result.ok(eduService.term());
    }

    @Operation(summary = "我的课表（按班级 + 周次）")
    @GetMapping("/timetable")
    public Result<Map<String, Object>> timetable(@RequestParam(required = false) String term,
                                                 @RequestParam(required = false) Integer weekNo) {
        return Result.ok(eduService.timetable(term, weekNo, UserContext.require()));
    }

    @Operation(summary = "管理员导入课表（覆盖式）")
    @PostMapping("/timetable/import")
    public Result<Map<String, Object>> importTimetable(@RequestBody Map<String, Object> body) {
        requireAdmin();
        return Result.ok(eduService.importTimetable(body));
    }

    @Operation(summary = "我可评价的课程")
    @GetMapping("/eval/options")
    public Result<List<Map<String, Object>>> options(@RequestParam(required = false) String term) {
        return Result.ok(eduService.evalOptions(term, UserContext.require()));
    }

    @Operation(summary = "提交课程评价")
    @PostMapping("/eval")
    public Result<Map<String, Object>> submit(@RequestBody Map<String, Object> body) {
        return Result.ok(eduService.submitEval(body, UserContext.require()));
    }

    @Operation(summary = "我的评价记录")
    @GetMapping("/eval/my")
    public Result<List<Map<String, Object>>> my(@RequestParam(required = false) String term) {
        return Result.ok(eduService.myEvals(term, UserContext.require()));
    }

    @Operation(summary = "课程评价聚合统计（不含学生身份）")
    @GetMapping("/eval/stats")
    public Result<Map<String, Object>> stats(@RequestParam long courseId) {
        requireAdmin();
        return Result.ok(eduService.evalStats(courseId));
    }
}
