package com.community.repair.controller;

import com.community.common.result.R;
import com.community.repair.service.FeedbackService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/feedback")
@RequiredArgsConstructor
public class FeedbackController {

    private final FeedbackService feedbackService;

    @GetMapping("/list")
    public R<List<Map<String, Object>>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword) {
        return R.ok(feedbackService.list(status, keyword));
    }

    /** 居民：我提交的意见反馈 */
    @GetMapping("/mine")
    public R<List<Map<String, Object>>> mine() {
        return R.ok(feedbackService.listMine());
    }

    @GetMapping("/{id}")
    public R<Map<String, Object>> detail(@PathVariable Long id) {
        return R.ok(feedbackService.detail(id));
    }

    @GetMapping("/{id}/logs")
    public R<List<Map<String, Object>>> logs(@PathVariable Long id) {
        return R.ok(feedbackService.listLogs(id));
    }

    @PutMapping("/{id}/handle")
    public R<Map<String, Object>> handle(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return R.ok(feedbackService.handle(id, body));
    }

    /** 通过意见反馈并转入报事工单列表 */
    @PutMapping("/{id}/approve")
    public R<Map<String, Object>> approve(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, Object> body) {
        return R.ok(feedbackService.approve(id, body));
    }

    @PostMapping
    public R<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        return R.ok(feedbackService.create(body));
    }
}
