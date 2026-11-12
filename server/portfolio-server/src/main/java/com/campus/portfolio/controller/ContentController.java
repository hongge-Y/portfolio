package com.campus.portfolio.controller;

import com.campus.portfolio.common.BizException;
import com.campus.portfolio.common.Result;
import com.campus.portfolio.common.UserContext;
import com.campus.portfolio.service.ContentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "内容", description = "新闻 / 广告 / 赛事 / 红黑榜 / 点赞")
@RestController
@RequestMapping("/v1/publish")
public class ContentController {

    private final ContentService contentService;

    public ContentController(ContentService contentService) {
        this.contentService = contentService;
    }

    private void requireAdmin() {
        if (!UserContext.require().hasRole("ADMIN")) {
            throw BizException.forbidden("只有管理员可以执行该操作");
        }
    }

    // ---------------- 新闻 ----------------

    @Operation(summary = "前台新闻列表（按组织可见性过滤）")
    @GetMapping("/news/portal")
    public Result<Map<String, Object>> newsPortal(@RequestParam(defaultValue = "1") int pageNum,
                                                  @RequestParam(defaultValue = "10") int pageSize) {
        return Result.ok(contentService.newsPortal(pageNum, pageSize, UserContext.require()));
    }

    @Operation(summary = "前台新闻详情")
    @GetMapping("/news/portal/{id}")
    public Result<Map<String, Object>> newsDetail(@PathVariable long id) {
        return Result.ok(contentService.newsDetail(id, UserContext.require()));
    }

    @Operation(summary = "后台保存新闻")
    @PostMapping("/news")
    public Result<Long> saveNews(@RequestBody Map<String, Object> body) {
        requireAdmin();
        return Result.ok(contentService.saveNews(body, UserContext.require()));
    }

    @Operation(summary = "后台新闻分页")
    @PostMapping("/news/search")
    public Result<Map<String, Object>> newsSearch(@RequestBody Map<String, Object> body) {
        requireAdmin();
        return Result.ok(contentService.newsSearch(body));
    }

    @Operation(summary = "删除新闻")
    @DeleteMapping("/news/{id}")
    public Result<Void> deleteNews(@PathVariable long id) {
        requireAdmin();
        contentService.deleteNews(id);
        return Result.ok();
    }

    // ---------------- 广告 ----------------

    @Operation(summary = "前台当前可见广告")
    @GetMapping("/ad/portal")
    public Result<List<Map<String, Object>>> adPortal() {
        return Result.ok(contentService.adPortal(UserContext.require()));
    }

    @Operation(summary = "后台保存广告")
    @PostMapping("/ad")
    public Result<Long> saveAd(@RequestBody Map<String, Object> body) {
        requireAdmin();
        return Result.ok(contentService.saveAd(body));
    }

    @Operation(summary = "后台广告分页")
    @PostMapping("/ad/search")
    public Result<Map<String, Object>> adSearch(@RequestBody Map<String, Object> body) {
        requireAdmin();
        return Result.ok(contentService.adSearch(body));
    }

    @Operation(summary = "删除广告")
    @DeleteMapping("/ad/{id}")
    public Result<Void> deleteAd(@PathVariable long id) {
        requireAdmin();
        contentService.deleteAd(id);
        return Result.ok();
    }

    // ---------------- 红黑榜 ----------------

    @Operation(summary = "红黑榜标签（喜优奖赞 / 警退缺罚）")
    @GetMapping("/rank/label")
    public Result<List<Map<String, Object>>> labels(@RequestParam(required = false) Boolean red) {
        return Result.ok(contentService.rankLabels(red));
    }

    @Operation(summary = "我的（或孩子的）红黑榜")
    @GetMapping("/rank/portal")
    public Result<Map<String, Object>> rankPortal(@RequestParam(required = false) Boolean red,
                                                  @RequestParam(defaultValue = "1") int pageNum,
                                                  @RequestParam(defaultValue = "10") int pageSize) {
        return Result.ok(contentService.rankPortal(red, pageNum, pageSize, UserContext.require()));
    }

    @Operation(summary = "红黑榜详情")
    @GetMapping("/rank/portal/{id}")
    public Result<Map<String, Object>> rankDetail(@PathVariable long id) {
        return Result.ok(contentService.rankDetail(id, UserContext.require()));
    }

    @Operation(summary = "后台保存红黑榜")
    @PostMapping("/rank")
    public Result<Long> saveRank(@RequestBody Map<String, Object> body) {
        requireAdmin();
        return Result.ok(contentService.saveRank(body, UserContext.require()));
    }

    @Operation(summary = "后台红黑榜分页")
    @PostMapping("/rank/search")
    public Result<Map<String, Object>> rankSearch(@RequestBody Map<String, Object> body) {
        requireAdmin();
        return Result.ok(contentService.rankSearch(body));
    }

    @Operation(summary = "删除红黑榜")
    @DeleteMapping("/rank/{id}")
    public Result<Void> deleteRank(@PathVariable long id) {
        requireAdmin();
        contentService.deleteRank(id);
        return Result.ok();
    }

    // ---------------- 赛事 ----------------

    @Operation(summary = "前台赛事列表")
    @GetMapping("/match/portal")
    public Result<Map<String, Object>> matchPortal(@RequestParam(defaultValue = "1") int pageNum,
                                                   @RequestParam(defaultValue = "10") int pageSize) {
        return Result.ok(contentService.matchPortal(pageNum, pageSize, UserContext.require()));
    }

    @Operation(summary = "前台赛事详情")
    @GetMapping("/match/portal/{id}")
    public Result<Map<String, Object>> matchDetail(@PathVariable long id) {
        return Result.ok(contentService.matchDetail(id, UserContext.require()));
    }

    @Operation(summary = "后台保存赛事")
    @PostMapping("/match")
    public Result<Long> saveMatch(@RequestBody Map<String, Object> body) {
        requireAdmin();
        return Result.ok(contentService.saveMatch(body));
    }

    @Operation(summary = "后台赛事分页")
    @PostMapping("/match/search")
    public Result<Map<String, Object>> matchSearch(@RequestBody Map<String, Object> body) {
        requireAdmin();
        return Result.ok(contentService.matchSearch(body));
    }

    @Operation(summary = "删除赛事")
    @DeleteMapping("/match/{id}")
    public Result<Void> deleteMatch(@PathVariable long id) {
        requireAdmin();
        contentService.deleteMatch(id);
        return Result.ok();
    }

    @Operation(summary = "赛事报名")
    @PostMapping("/match/{id}/register")
    public Result<Long> register(@PathVariable long id, @RequestBody(required = false) Map<String, Object> body) {
        return Result.ok(contentService.registerMatch(id, body == null ? Map.of() : body, UserContext.require()));
    }

    // ---------------- 点赞 ----------------

    @Operation(summary = "点赞 / 取消点赞")
    @PostMapping("/like")
    public Result<Map<String, Object>> like(@RequestBody Map<String, Object> body) {
        String type = String.valueOf(body.get("targetType"));
        long targetId = Long.parseLong(String.valueOf(body.get("targetId")));
        boolean on = !Boolean.FALSE.equals(body.get("liked"));
        return Result.ok(contentService.like(type, targetId, UserContext.require(), on));
    }
}
