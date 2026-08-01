package com.community.ai.controller;

import com.community.ai.biz.AiBizButlerService;
import com.community.ai.entity.AiChatLog;
import com.community.ai.entity.AiKnowledge;
import com.community.ai.entity.AiScheduleRemind;
import com.community.ai.job.ScheduleRemindJob;
import com.community.ai.service.AiFeatureService;
import com.community.ai.service.AiService;
import com.community.ai.service.KbCategoryService;
import com.community.ai.service.KbDocService;
import com.community.ai.service.KbSettingsService;
import com.community.ai.service.LostBizService;
import com.community.common.feign.dto.*;
import com.community.common.result.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

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
    private final AiBizButlerService aiBizButlerService;
    private final ScheduleRemindJob scheduleRemindJob;
    private final KbDocService kbDocService;
    private final KbCategoryService kbCategoryService;
    private final KbSettingsService kbSettingsService;
    private final AiFeatureService aiFeatureService;
    private final LostBizService lostBizService;

    /** 1. AI 问答（居民/物业共用）：制度RAG + 业务指令双分支 */
    @PostMapping("/chat")
    public R<AiChatResponse> chat(@RequestBody AiChatRequest body) {
        return R.ok(aiService.chat(body));
    }

    /** 业务办理确认（禁止未确认写库） */
    @PostMapping("/chat/confirm")
    public R<AiChatResponse> confirmBiz(@RequestBody AiBizConfirmRequest body) {
        return R.ok(aiBizButlerService.confirm(body));
    }

    /** 取消当前业务办理会话 */
    @PostMapping("/chat/cancel")
    public R<AiChatResponse> cancelBiz(@RequestBody(required = false) AiBizConfirmRequest body) {
        return R.ok(aiBizButlerService.cancel(body == null ? new AiBizConfirmRequest() : body));
    }

    /** 物业：AI 业务操作日志（溯源） */
    @GetMapping("/biz-op-logs")
    public R<List<Map<String, Object>>> bizOpLogs(@RequestParam(defaultValue = "50") int limit) {
        return R.ok(aiBizButlerService.listOpLogs(limit));
    }

    /** 居民本人对话记录 */
    @GetMapping("/chat/logs")
    public R<List<AiChatLog>> chatLogs() {
        return R.ok(aiService.listMyChatLogs());
    }

    /** 物业：问答日志 */
    @GetMapping("/chat/admin-logs")
    public R<List<Map<String, Object>>> adminChatLogs(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer rating,
            @RequestParam(required = false) String source,
            @RequestParam(defaultValue = "100") int limit) {
        return R.ok(aiService.listAdminChatLogs(keyword, rating, source, limit));
    }

    /**
     * 物业：问答统计
     * @param topRange 高频问题时间范围 today|7d|30d（卡片始终为当日）
     */
    @GetMapping("/chat/admin-stats")
    public R<Map<String, Object>> adminChatStats(
            @RequestParam(required = false, defaultValue = "today") String topRange) {
        return R.ok(aiService.chatLogTodayStats(topRange));
    }

    /** 业主评价对话 */
    @PutMapping("/chat/logs/{id}/rate")
    public R<Map<String, Object>> rateChatLog(
            @PathVariable Long id, @RequestBody Map<String, Object> body) {
        Integer rating = body.get("rating") == null ? null : Integer.valueOf(String.valueOf(body.get("rating")));
        String remark = body.get("remark") == null ? null : String.valueOf(body.get("remark"));
        return R.ok(aiService.rateChatLog(id, rating, remark));
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
        m.put("aiAuditPass", true);
        return R.ok(m);
    }

    @GetMapping("/lost")
    public R<List<Map<String, Object>>> listLost(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String itemType,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String keyword) {
        return R.ok(aiService.listLost(status, itemType, category, keyword));
    }

    @GetMapping("/lost/categories")
    public R<List<Map<String, Object>>> lostCategories(
            @RequestParam(required = false) Boolean enabledOnly) {
        return R.ok(lostBizService.listCategories(enabledOnly));
    }

    @PostMapping("/lost/categories")
    public R<Map<String, Object>> saveLostCategory(@RequestBody Map<String, Object> body) {
        return R.ok(lostBizService.saveCategory(body));
    }

    @DeleteMapping("/lost/categories/{id}")
    public R<Void> deleteLostCategory(@PathVariable Long id) {
        lostBizService.deleteCategory(id);
        return R.ok();
    }

    @GetMapping("/lost/claims")
    public R<List<Map<String, Object>>> lostClaims(
            @RequestParam(required = false) Long lostId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword) {
        return R.ok(lostBizService.listClaims(lostId, status, keyword));
    }

    @PostMapping("/lost/{id}/claim")
    public R<Map<String, Object>> submitLostClaim(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, Object> body) {
        return R.ok(lostBizService.submitClaim(id, body));
    }

    @PutMapping("/lost/claims/{id}/verify")
    public R<Map<String, Object>> verifyLostClaim(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body) {
        return R.ok(lostBizService.verifyClaim(id, body));
    }

    @PutMapping("/lost/{id}/found")
    public R<Map<String, Object>> markLostFound(@PathVariable Long id) {
        return R.ok(aiService.markLostFound(id));
    }

    @DeleteMapping("/lost/{id}")
    public R<Void> deleteLost(@PathVariable Long id) {
        aiService.deleteLost(id);
        return R.ok();
    }

    /** 兼容部分环境对 DELETE 的限制 */
    @PostMapping("/lost/{id}/delete")
    public R<Void> deleteLostPost(@PathVariable Long id) {
        aiService.deleteLost(id);
        return R.ok();
    }

    /** Markdown / PDF / Word 知识库文档 */
    @GetMapping("/kb/docs")
    public R<List<Map<String, Object>>> kbDocs(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String docStatus) {
        return R.ok(kbDocService.listDocs(category, keyword, docStatus));
    }

    @GetMapping("/kb/docs/{id}")
    public R<Map<String, Object>> kbDoc(@PathVariable Long id) {
        return R.ok(kbDocService.getDoc(id));
    }

    @PostMapping("/kb/docs")
    public R<Map<String, Object>> uploadKbDoc(@RequestBody Map<String, String> body) {
        return R.ok(kbDocService.upload(
                body.get("title"), body.get("fileName"), body.get("content"), body.get("category")));
    }

    @PostMapping("/kb/docs/upload")
    public R<Map<String, Object>> uploadKbDocFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String category) {
        return R.ok(kbDocService.uploadFile(file, title, category));
    }

    @PutMapping("/kb/docs/{id}")
    public R<Map<String, Object>> updateKbDoc(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return R.ok(kbDocService.updateContent(id, body));
    }

    @PutMapping("/kb/docs/{id}/status")
    public R<Map<String, Object>> updateKbDocStatus(
            @PathVariable Long id, @RequestBody Map<String, Object> body) {
        String st = body.get("docStatus") == null ? null : String.valueOf(body.get("docStatus"));
        return R.ok(kbDocService.updateDocStatus(id, st));
    }

    @DeleteMapping("/kb/docs/{id}")
    public R<Void> deleteKbDoc(@PathVariable Long id) {
        kbDocService.delete(id);
        return R.ok();
    }

    @PostMapping("/kb/docs/batch-delete")
    public R<Map<String, Object>> batchDeleteKbDocs(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Object> raw = body.get("ids") instanceof List ? (List<Object>) body.get("ids") : List.of();
        List<Long> ids = raw.stream()
                .filter(java.util.Objects::nonNull)
                .map(o -> Long.parseLong(String.valueOf(o)))
                .toList();
        return R.ok(kbDocService.batchDelete(ids));
    }

    @PostMapping("/kb/docs/{id}/index")
    public R<Map<String, Object>> buildKbIndex(
            @PathVariable Long id, @RequestBody(required = false) Map<String, Object> body) {
        return R.ok(kbDocService.buildIndex(id, body));
    }

    @PostMapping("/kb/docs/batch-index")
    public R<Map<String, Object>> batchBuildKbIndex(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Object> raw = body.get("ids") instanceof List ? (List<Object>) body.get("ids") : List.of();
        List<Long> ids = raw.stream()
                .filter(java.util.Objects::nonNull)
                .map(o -> Long.parseLong(String.valueOf(o)))
                .toList();
        return R.ok(kbDocService.batchBuildIndex(ids, body));
    }

    @GetMapping("/kb/docs/{id}/logs")
    public R<List<Map<String, Object>>> kbIndexLogs(@PathVariable Long id) {
        return R.ok(kbDocService.listLogs(id));
    }

    @GetMapping("/kb/docs/{id}/chunks")
    public R<List<Map<String, Object>>> kbDocChunks(@PathVariable Long id) {
        return R.ok(kbDocService.listChunks(id));
    }

    /** 知识库分类 */
    @GetMapping("/kb/categories")
    public R<List<Map<String, Object>>> kbCategories(@RequestParam(required = false) Boolean enabledOnly) {
        return R.ok(kbCategoryService.list(enabledOnly));
    }

    @PostMapping("/kb/categories")
    public R<Map<String, Object>> saveKbCategory(@RequestBody Map<String, Object> body) {
        return R.ok(kbCategoryService.save(body));
    }

    @DeleteMapping("/kb/categories/{id}")
    public R<Void> deleteKbCategory(@PathVariable Long id) {
        kbCategoryService.delete(id);
        return R.ok();
    }

    /** 知识库设置 */
    @GetMapping("/kb/settings")
    public R<Map<String, Object>> getKbSettings() {
        return R.ok(kbSettingsService.get());
    }

    @PutMapping("/kb/settings")
    public R<Map<String, Object>> saveKbSettings(@RequestBody Map<String, Object> body) {
        return R.ok(kbSettingsService.save(body));
    }

    /** 租户知识库问答对 */
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
    public R<List<?>> mySchedule() {
        return R.ok(aiFeatureService.listMyScheduleEnhanced());
    }

    @PostMapping("/schedule/generate")
    public R<Map<String, Object>> generateSchedule() {
        return R.ok(aiFeatureService.generateSchedule());
    }

    @PostMapping("/schedule/conflict-check")
    public R<Map<String, Object>> conflictCheck() {
        return R.ok(aiFeatureService.conflictCheck());
    }

    /**
     * 物业：AI 日程台账列表
     * @param scheduleType 类型 FEE|ACTIVITY|MAINT|CONVENIENCE
     * @param conflict 1=仅冲突 0=无冲突
     */
    @GetMapping("/schedule/admin")
    public R<List<Map<String, Object>>> adminSchedule(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String scheduleType,
            @RequestParam(required = false) Integer conflict,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime,
            @RequestParam(defaultValue = "200") int limit) {
        return R.ok(aiFeatureService.adminSchedule(
                status, keyword, userId, scheduleType, conflict, startTime, endTime, limit));
    }

    /** 物业：日程类型分布统计（饼图） */
    @GetMapping("/schedule/admin-stats")
    public R<Map<String, Object>> adminScheduleStats() {
        return R.ok(aiFeatureService.adminScheduleStats());
    }

    /** 物业：日程详情（生成依据 / 画像 / 推送记录） */
    @GetMapping("/schedule/admin/{id}")
    public R<Map<String, Object>> adminScheduleDetail(@PathVariable Long id) {
        return R.ok(aiFeatureService.adminScheduleDetail(id));
    }

    @DeleteMapping("/schedule/{id}")
    public R<Void> deleteSchedule(@PathVariable Long id) {
        aiFeatureService.deleteSchedule(id);
        return R.ok();
    }

    /** 提前提醒配置（小时） */
    @GetMapping("/schedule/settings")
    public R<Map<String, Object>> scheduleSettings() {
        return R.ok(aiFeatureService.getScheduleSettings());
    }

    @PutMapping("/schedule/settings")
    public R<Map<String, Object>> saveScheduleSettings(@RequestBody Map<String, Object> body) {
        return R.ok(aiFeatureService.saveScheduleSettings(body));
    }

    /** 手动触发日程扫描（运营/联调） */
    @PostMapping("/schedule/dispatch")
    public R<Integer> dispatchSchedule() {
        return R.ok(scheduleRemindJob.runOnce());
    }

    /** 用户画像 */
    @GetMapping("/profile")
    public R<Map<String, Object>> getProfile() {
        return R.ok(aiFeatureService.getProfile());
    }

    @PutMapping("/profile")
    public R<Map<String, Object>> saveProfile(@RequestBody Map<String, Object> body) {
        return R.ok(aiFeatureService.saveProfile(body));
    }

    @PostMapping("/behavior")
    public R<Void> trackBehavior(@RequestBody Map<String, String> body) {
        aiFeatureService.trackBehavior(body.get("eventType"), body.get("bizId"));
        return R.ok();
    }

    /** 便民推荐 */
    @GetMapping("/recommend/services")
    public R<List<Map<String, Object>>> recommendServices() {
        return R.ok(aiFeatureService.recommendServices());
    }

    /**
     * 识图日志列表
     * @param needManual 1=需手选/人工复核 0=自动通过
     * @param confMin/confMax 置信度 0~1
     */
    @GetMapping("/image/logs")
    public R<List<Map<String, Object>>> imageLogs(
            @RequestParam(required = false) String facilityType,
            @RequestParam(required = false) String damageLevel,
            @RequestParam(required = false) Integer needManual,
            @RequestParam(required = false) Double confMin,
            @RequestParam(required = false) Double confMax,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime,
            @RequestParam(defaultValue = "100") int limit) {
        return R.ok(aiFeatureService.listImageLogs(
                facilityType, damageLevel, needManual, confMin, confMax, startTime, endTime, limit));
    }

    /** 识图统计：今日卡片 + 自动/人工分布 */
    @GetMapping("/image/admin-stats")
    public R<Map<String, Object>> imageAdminStats() {
        return R.ok(aiFeatureService.imageLogStats());
    }

    /** 识图详情 */
    @GetMapping("/image/logs/{id}")
    public R<Map<String, Object>> imageLogDetail(@PathVariable Long id) {
        return R.ok(aiFeatureService.imageLogDetail(id));
    }

    /** 删除识图日志 */
    @DeleteMapping("/image/logs/{id}")
    public R<Void> deleteImageLog(@PathVariable Long id) {
        aiFeatureService.deleteImageLog(id);
        return R.ok();
    }

    /** 情绪调解工单（原文仅授权物业查看，禁止导出外泄） */
    @GetMapping("/emotion/cases")
    public R<List<Map<String, Object>>> emotionCases(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String emotionTag,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(defaultValue = "80") int limit) {
        return R.ok(aiFeatureService.listEmotionCases(status, userId, keyword, emotionTag, startDate, endDate, limit));
    }

    @GetMapping("/emotion/cases/stats")
    public R<Map<String, Object>> emotionCaseStats() {
        return R.ok(aiFeatureService.emotionCaseStats());
    }

    @GetMapping("/emotion/my")
    public R<List<Map<String, Object>>> myEmotionCases() {
        return R.ok(aiFeatureService.myEmotionCases());
    }

    @PutMapping("/emotion/cases/{id}")
    public R<Map<String, Object>> updateEmotionCase(
            @PathVariable Long id, @RequestBody Map<String, Object> body) {
        return R.ok(aiFeatureService.updateEmotionCase(id, body));
    }

    @DeleteMapping("/emotion/cases/{id}")
    public R<Void> deleteEmotionCase(@PathVariable Long id) {
        aiFeatureService.deleteEmotionCase(id);
        return R.ok();
    }

    /** 小区敏感词库 */
    @GetMapping("/sensitive-words")
    public R<List<Map<String, Object>>> listSensitiveWords() {
        return R.ok(aiFeatureService.listTenantWords());
    }

    @PostMapping("/sensitive-words")
    public R<Map<String, Object>> addSensitiveWord(@RequestBody Map<String, String> body) {
        return R.ok(aiFeatureService.addTenantWord(body.get("word")));
    }

    @DeleteMapping("/sensitive-words/{id}")
    public R<Void> deleteSensitiveWord(@PathVariable Long id) {
        aiFeatureService.deleteTenantWord(id);
        return R.ok();
    }

    /** 公告定向推送 */
    @PostMapping("/notice/target-push")
    public R<Map<String, Object>> targetPush(@RequestBody Map<String, Object> body) {
        Long noticeId = body.get("noticeId") == null ? null : Long.valueOf(String.valueOf(body.get("noticeId")));
        String rule = body.get("targetRule") == null ? null : String.valueOf(body.get("targetRule"));
        return R.ok(aiFeatureService.targetPushNotice(noticeId, rule));
    }

    @GetMapping("/notice/push-logs")
    public R<List<Map<String, Object>>> pushLogs(
            @RequestParam(required = false) Long noticeId,
            @RequestParam(defaultValue = "50") int limit) {
        return R.ok(aiFeatureService.listPushLogs(noticeId, limit));
    }
}
