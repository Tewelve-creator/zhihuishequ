package com.community.ai.controller;

import com.community.ai.entity.AiChatLog;
import com.community.ai.entity.AiKnowledge;
import com.community.ai.entity.AiScheduleRemind;
import com.community.ai.job.ScheduleRemindJob;
import com.community.ai.service.AiService;
import com.community.common.feign.dto.*;
import com.community.common.result.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 对外 REST（App/网关直连 + Feign 同源路径）
 */
@RestController
@RequestMapping("/ai")
@RequiredArgsConstructor
public class AiController {

    private final AiService aiService;
    private final ScheduleRemindJob scheduleRemindJob;

    /** 1. AI 问答（仅居民） */
    @PostMapping("/chat")
    public R<AiChatResponse> chat(@RequestBody AiChatRequest body) {
        return R.ok(aiService.chat(body));
    }

    /** 居民本人对话记录 */
    @GetMapping("/chat/logs")
    public R<List<AiChatLog>> chatLogs() {
        return R.ok(aiService.listMyChatLogs());
    }

    /** 2. 敏感词检测 + 摘要 */
    @PostMapping("/audit")
    public R<AiAuditResponse> audit(@RequestBody AiAuditRequest body) {
        return R.ok(aiService.audit(body));
    }

    /** 2. 公告 AI 摘要（Feign 专用别名） */
    @PostMapping("/notice/summary")
    public R<AiAuditResponse> noticeSummary(@RequestBody AiAuditRequest body) {
        return R.ok(aiService.noticeSummary(body));
    }

    /** 3. 报修识图 */
    @PostMapping("/image")
    public R<AiImageRepairResponse> image(@RequestBody AiImageRepairRequest body) {
        return R.ok(aiService.imageRepair(body));
    }

    /** 兼容旧 Body：{imageUrl} */
    @PostMapping("/image/simple")
    public R<AiImageRepairResponse> imageSimple(@RequestBody Map<String, String> body) {
        AiImageRepairRequest req = new AiImageRepairRequest();
        req.setImageUrl(body.get("imageUrl"));
        return R.ok(aiService.imageRepair(req));
    }

    /** 4. 情绪倾诉 */
    @PostMapping("/emotion")
    public R<AiEmotionResponse> emotion(@RequestBody AiEmotionRequest body) {
        return R.ok(aiService.emotion(body));
    }

    /** 5. 失物匹配 */
    @PostMapping("/lost/match")
    public R<AiLostMatchResponse> lostMatch(@RequestBody AiLostMatchRequest body) {
        return R.ok(aiService.lostMatch(body));
    }

    @PostMapping("/lost")
    public R<Map<String, Object>> publishLost(@RequestBody Map<String, String> body) {
        Long id = aiService.publishLost(body);
        Map<String, Object> m = new HashMap<>();
        m.put("id", id);
        return R.ok(m);
    }

    /** 租户知识库 */
    @GetMapping("/kb")
    public R<List<AiKnowledge>> listKb(@RequestParam(defaultValue = "false") boolean all) {
        return R.ok(all ? aiService.listKbAll() : aiService.listKb());
    }

    @PostMapping("/kb")
    public R<AiKnowledge> saveKb(@RequestBody AiKnowledge kb) {
        return R.ok(aiService.saveKb(kb));
    }

    @DeleteMapping("/kb/{id}")
    public R<Void> deleteKb(@PathVariable Long id) {
        aiService.deleteKb(id);
        return R.ok();
    }

    /** 物业：AI 调用日志（识图等） */
    @GetMapping("/call-logs")
    public R<List<Map<String, Object>>> callLogs(
            @RequestParam(required = false) String scene,
            @RequestParam(defaultValue = "30") int limit) {
        return R.ok(aiService.listCallLogs(scene, limit));
    }

    /** 物业：业主咨询统计（无聊天正文） */
    @GetMapping("/chat/stats")
    public R<Map<String, Object>> chatStats() {
        return R.ok(aiService.chatStats());
    }

    /** 6. 便民日程提醒 */
    @PostMapping("/schedule")
    public R<AiScheduleRemind> createSchedule(@RequestBody AiScheduleRemind body) {
        return R.ok(aiService.createRemind(body));
    }

    @GetMapping("/schedule")
    public R<List<AiScheduleRemind>> mySchedule() {
        return R.ok(aiService.myReminds());
    }

    /** 手动触发日程扫描（运营/联调） */
    @PostMapping("/schedule/dispatch")
    public R<Integer> dispatchSchedule() {
        return R.ok(scheduleRemindJob.runOnce());
    }
}
