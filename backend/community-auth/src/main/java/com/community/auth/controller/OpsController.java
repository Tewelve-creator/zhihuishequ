package com.community.auth.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import com.community.auth.dto.OpsConfigSaveRequest;
import com.community.auth.service.OpsService;
import com.community.common.constant.RoleConstants;
import com.community.common.result.PageResult;
import com.community.common.result.R;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/auth/ops")
@RequiredArgsConstructor
@SaCheckRole(RoleConstants.PLATFORM_ADMIN)
public class OpsController {

    private final OpsService opsService;

    @GetMapping("/ai-quota")
    public R<List<Map<String, Object>>> aiQuota() {
        return R.ok(opsService.aiQuotaOverview());
    }

    @GetMapping("/finance")
    public R<Map<String, Object>> finance() {
        return R.ok(opsService.financeOverview());
    }

    @GetMapping("/health")
    public R<List<Map<String, Object>>> health() {
        return R.ok(opsService.healthCheck());
    }

    @GetMapping("/config")
    public R<List<Map<String, Object>>> listConfig() {
        return R.ok(opsService.listConfig());
    }

    @PostMapping("/config")
    public R<Void> saveConfig(@Valid @RequestBody OpsConfigSaveRequest req) {
        opsService.upsertConfig(req.getConfigKey(), req.getConfigValue(), req.getRemark());
        return R.ok();
    }

    @GetMapping("/logs")
    public R<PageResult<Map<String, Object>>> logs(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        return R.ok(opsService.pageOperLogs(page, size));
    }

    /* ---------- 平台敏感词库 ---------- */

    @GetMapping("/sensitive-words")
    public R<List<Map<String, Object>>> sensitiveWords() {
        return R.ok(opsService.listSensitiveWords());
    }

    @PostMapping("/sensitive-words")
    public R<Void> addSensitiveWord(@RequestBody Map<String, String> body) {
        opsService.addSensitiveWord(body == null ? null : body.get("word"));
        return R.ok();
    }

    @DeleteMapping("/sensitive-words/{id}")
    public R<Void> deleteSensitiveWord(@PathVariable Long id) {
        opsService.deleteSensitiveWord(id);
        return R.ok();
    }

    /* ---------- 全局 AI 参数（存 sys_config） ---------- */

    @GetMapping("/ai-config")
    public R<List<Map<String, Object>>> aiConfig() {
        return R.ok(opsService.listAiConfig());
    }

    @PostMapping("/ai-config")
    public R<Void> saveAiConfig(@RequestBody Map<String, Object> body) {
        opsService.saveAiConfig(body);
        return R.ok();
    }

    /* ---------- AI 接口调用监控 ---------- */

    @GetMapping("/ai-calls/stats")
    public R<Map<String, Object>> aiCallStats(
            @RequestParam(defaultValue = "7") int days) {
        return R.ok(opsService.aiCallStats(days));
    }

    @GetMapping("/ai-calls")
    public R<PageResult<Map<String, Object>>> aiCalls(
            @RequestParam(required = false) Long tenantId,
            @RequestParam(required = false) String scene,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        return R.ok(opsService.pageAiCalls(tenantId, scene, page, size));
    }

    /* ---------- 租户权限模板 ---------- */

    @GetMapping("/feature-templates")
    public R<List<Map<String, Object>>> featureTemplates() {
        return R.ok(opsService.listFeatureTemplates());
    }

    @PostMapping("/feature-templates")
    public R<Map<String, Object>> saveFeatureTemplate(@RequestBody Map<String, Object> body) {
        return R.ok(opsService.saveFeatureTemplate(body));
    }

    @DeleteMapping("/feature-templates/{id}")
    public R<Void> deleteFeatureTemplate(@PathVariable Long id) {
        opsService.deleteFeatureTemplate(id);
        return R.ok();
    }

    @PostMapping("/feature-templates/{id}/apply")
    public R<Map<String, Object>> applyFeatureTemplate(
            @PathVariable Long id, @RequestBody Map<String, Object> body) {
        return R.ok(opsService.applyFeatureTemplate(id, body));
    }
}
