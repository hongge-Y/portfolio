package com.campus.portfolio.controller;

import com.campus.portfolio.common.Result;
import com.campus.portfolio.common.UserContext;
import com.campus.portfolio.service.MessageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "站内消息", description = "收发件箱 / 已读 / 未读数")
@RestController
@RequestMapping("/v1/message")
public class MessageController {

    private final MessageService messageService;

    public MessageController(MessageService messageService) {
        this.messageService = messageService;
    }

    @Operation(summary = "收件箱分页")
    @PostMapping("/inbox/search")
    public Result<Map<String, Object>> inbox(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        Map<String, Object> s = body.get("searchModel") instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
        return Result.ok(messageService.inbox(UserContext.require(),
                intOf(body.get("pageNum"), 1), intOf(body.get("pageSize"), 10), s));
    }

    @Operation(summary = "发件箱分页")
    @PostMapping("/search")
    public Result<Map<String, Object>> sent(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        Map<String, Object> s = body.get("searchModel") instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
        return Result.ok(messageService.sent(UserContext.require(),
                intOf(body.get("pageNum"), 1), intOf(body.get("pageSize"), 10), s));
    }

    @Operation(summary = "收件详情（自动置已读）")
    @GetMapping("/inbox/{id}")
    public Result<Map<String, Object>> inboxDetail(@PathVariable long id) {
        return Result.ok(messageService.inboxDetail(id, UserContext.require()));
    }

    @Operation(summary = "发件详情")
    @GetMapping("/{id}")
    public Result<Map<String, Object>> sentDetail(@PathVariable long id) {
        return Result.ok(messageService.sentDetail(id, UserContext.require()));
    }

    @Operation(summary = "新建消息（status=1 直接发布，0 存草稿）")
    @PostMapping
    public Result<Long> create(@RequestBody Map<String, Object> body) {
        boolean publish = intOf(body.get("status"), 0) == 1;
        return Result.ok(messageService.create(body, UserContext.require(), publish));
    }

    @Operation(summary = "发布草稿")
    @PostMapping("/{id}/publish")
    public Result<Void> publish(@PathVariable long id) {
        messageService.publish(id, UserContext.require());
        return Result.ok();
    }

    @Operation(summary = "标记已读")
    @PostMapping("/inbox/{id}/read")
    public Result<Void> read(@PathVariable long id) {
        messageService.read(id, UserContext.require());
        return Result.ok();
    }

    @Operation(summary = "删除收件")
    @DeleteMapping("/inbox/{id}")
    public Result<Void> deleteInbox(@PathVariable long id) {
        messageService.deleteInbox(id, UserContext.require());
        return Result.ok();
    }

    @Operation(summary = "删除发件")
    @DeleteMapping("/{id}")
    public Result<Void> deleteSent(@PathVariable long id) {
        messageService.deleteSent(id, UserContext.require());
        return Result.ok();
    }

    @Operation(summary = "未读数（总数 + 分类）")
    @GetMapping("/unread-count")
    public Result<Map<String, Object>> unread() {
        return Result.ok(messageService.unread(UserContext.require()));
    }

    @Operation(summary = "我收到过消息的发件人（筛选下拉）")
    @GetMapping("/senders")
    public Result<List<Map<String, Object>>> senders() {
        return Result.ok(messageService.senders(UserContext.require()));
    }

    private static int intOf(Object o, int def) {
        if (o == null) return def;
        if (o instanceof Number n) return n.intValue();
        try { return Integer.parseInt(String.valueOf(o)); } catch (NumberFormatException e) { return def; }
    }
}
