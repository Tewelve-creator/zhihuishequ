package com.community.repair.controller;

import com.community.common.result.R;
import com.community.repair.service.AppealMomentsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 社区动态 · 朋友圈 API
 *
 * <pre>
 * 住户端：
 *   GET    /appeal/feed                 朋友圈动态流
 *   POST   /appeal                      发布动态
 *   DELETE /appeal/{id}                 删除自己的动态
 *   GET    /appeal/{id}/comments        评论列表
 *   POST   /appeal/{id}/comments        发表评论
 *   DELETE /appeal/comments/{id}        删除自己的评论
 *   POST   /appeal/{id}/like            点赞/取消点赞
 *   GET    /appeal/mute/me              查询本人是否被禁言
 *   POST   /appeal/reports              举报 { targetType, targetId, reason }
 *
 * 物业后台：
 *   GET    /appeal/admin/list?filter=   all|normal|deleted|muted|hidden|pinned
 *   GET    /appeal/admin/{id}
 *   DELETE /appeal/admin/{id}
 *   PUT    /appeal/admin/{id}/pin       { pinned }
 *   PUT    /appeal/admin/{id}/hide      { hidden, reason }
 *   GET    /appeal/admin/comments
 *   DELETE /appeal/admin/comments/{id}
 *   GET    /appeal/admin/reports
 *   PUT    /appeal/admin/reports/{id}/handle
 *   POST   /appeal/admin/mute
 *   POST   /appeal/admin/unmute
 *   GET    /appeal/admin/op-logs
 * </pre>
 */
@RestController
@RequestMapping("/appeal")
@RequiredArgsConstructor
public class AppealController {

    private final AppealMomentsService momentsService;

    /* ---------- 住户端 ---------- */

    @GetMapping("/feed")
    public R<List<Map<String, Object>>> feed(@RequestParam(defaultValue = "50") int limit) {
        return R.ok(momentsService.feed(limit));
    }

    @PostMapping
    public R<Map<String, Object>> publish(@RequestBody Map<String, Object> body) {
        return R.ok(momentsService.publish(body));
    }

    @DeleteMapping("/{id}")
    public R<Void> deleteOwn(@PathVariable Long id) {
        momentsService.deleteOwnPost(id);
        return R.ok();
    }

    @GetMapping("/{id}/comments")
    public R<List<Map<String, Object>>> comments(@PathVariable Long id) {
        return R.ok(momentsService.listComments(id, false));
    }

    @PostMapping("/{id}/comments")
    public R<Map<String, Object>> addComment(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return R.ok(momentsService.addComment(id, body));
    }

    @DeleteMapping("/comments/{id}")
    public R<Void> deleteComment(@PathVariable Long id) {
        momentsService.deleteComment(id);
        return R.ok();
    }

    @PostMapping("/{id}/like")
    public R<Map<String, Object>> like(@PathVariable Long id) {
        return R.ok(momentsService.toggleLike(id));
    }

    @GetMapping("/mute/me")
    public R<Map<String, Object>> myMute() {
        return R.ok(momentsService.myMuteStatus());
    }

    @PostMapping("/reports")
    public R<Map<String, Object>> submitReport(@RequestBody Map<String, Object> body) {
        return R.ok(momentsService.submitReport(body));
    }

    /* ---------- 物业后台 ---------- */

    @GetMapping("/admin/list")
    public R<List<Map<String, Object>>> adminList(@RequestParam(required = false) String filter) {
        return R.ok(momentsService.adminList(filter));
    }

    @GetMapping("/admin/{id}")
    public R<Map<String, Object>> adminDetail(@PathVariable Long id) {
        return R.ok(momentsService.adminDetail(id));
    }

    @DeleteMapping("/admin/{id}")
    public R<Void> adminDeletePost(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> body) {
        String reason = body == null ? null : (body.get("reason") == null ? null : String.valueOf(body.get("reason")));
        momentsService.adminDeletePost(id, reason);
        return R.ok();
    }

    @DeleteMapping("/admin/comments/{id}")
    public R<Void> adminDeleteComment(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> body) {
        String reason = body == null ? null : (body.get("reason") == null ? null : String.valueOf(body.get("reason")));
        momentsService.adminDeleteComment(id, reason);
        return R.ok();
    }

    @PostMapping("/admin/mute")
    public R<Map<String, Object>> mute(@RequestBody Map<String, Object> body) {
        return R.ok(momentsService.muteUser(body));
    }

    @PostMapping("/admin/unmute")
    public R<Void> unmute(@RequestBody Map<String, Object> body) {
        Object uid = body == null ? null : body.get("userId");
        Long userId = uid == null ? null : Long.valueOf(String.valueOf(uid));
        momentsService.unmuteUser(userId);
        return R.ok();
    }

    @GetMapping("/admin/op-logs")
    public R<List<Map<String, Object>>> opLogs(@RequestParam(defaultValue = "80") int limit) {
        return R.ok(momentsService.listOpLogs(limit));
    }

    @PutMapping("/admin/{id}/pin")
    public R<Map<String, Object>> pin(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> body) {
        boolean pinned = body == null || body.get("pinned") == null || Boolean.TRUE.equals(body.get("pinned"))
                || "1".equals(String.valueOf(body.get("pinned")));
        return R.ok(momentsService.pinPost(id, pinned));
    }

    @PutMapping("/admin/{id}/hide")
    public R<Map<String, Object>> hide(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> body) {
        boolean hidden = body == null || body.get("hidden") == null || Boolean.TRUE.equals(body.get("hidden"))
                || "1".equals(String.valueOf(body.get("hidden")));
        String reason = body == null || body.get("reason") == null ? null : String.valueOf(body.get("reason"));
        return R.ok(momentsService.hidePost(id, hidden, reason));
    }

    @GetMapping("/admin/comments")
    public R<List<Map<String, Object>>> adminComments(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Boolean deletedOnly) {
        return R.ok(momentsService.adminCommentList(keyword, deletedOnly));
    }

    @GetMapping("/admin/reports")
    public R<List<Map<String, Object>>> listReports(@RequestParam(required = false) String status) {
        return R.ok(momentsService.listReports(status));
    }

    @PutMapping("/admin/reports/{id}/handle")
    public R<Map<String, Object>> handleReport(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return R.ok(momentsService.handleReport(id, body));
    }
}
