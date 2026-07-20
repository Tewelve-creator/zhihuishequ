package com.community.saas.controller;

import com.community.common.feign.dto.AiAuditResponse;
import com.community.common.feign.dto.AiChatResponse;
import com.community.common.result.R;
import com.community.saas.entity.SysPackage;
import com.community.saas.entity.SysTenant;
import com.community.saas.job.TenantExpireJob;
import com.community.saas.service.AiFeignExampleService;
import com.community.saas.service.SaasService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/saas")
@RequiredArgsConstructor
public class SaasController {

    private final SaasService saasService;
    private final TenantExpireJob tenantExpireJob;
    private final AiFeignExampleService aiFeignExampleService;

    @GetMapping("/packages/public")
    public R<List<SysPackage>> publicPackages() {
        return R.ok(saasService.publicPackages());
    }

    @GetMapping("/packages")
    public R<List<SysPackage>> packages() {
        return R.ok(saasService.publicPackages());
    }

    @PostMapping("/packages")
    public R<SysPackage> savePackage(@RequestBody SysPackage pkg) {
        return R.ok(saasService.savePackage(pkg));
    }

    @GetMapping("/tenants")
    public R<List<SysTenant>> tenants() {
        return R.ok(saasService.listTenants());
    }

    @PostMapping("/tenants")
    public R<SysTenant> create(@RequestBody SysTenant tenant) {
        return R.ok(saasService.createTenant(tenant));
    }

    @PutMapping("/tenants")
    public R<SysTenant> update(@RequestBody SysTenant tenant) {
        return R.ok(saasService.updateTenant(tenant));
    }

    @DeleteMapping("/tenants/{id}")
    public R<Void> delete(@PathVariable Long id) {
        saasService.deleteTenant(id);
        return R.ok();
    }

    @GetMapping("/tenant/current")
    public R<Map<String, Object>> current() {
        return R.ok(saasService.currentTenant());
    }

    /** 手动触发到期扫描（演示/运维） */
    @PostMapping("/tenant/expire-scan")
    public R<Map<String, Object>> expireScan() {
        int n = tenantExpireJob.doScan();
        return R.ok(Map.of("changed", n));
    }

    @GetMapping("/tenants/expiring")
    public R<List<SysTenant>> expiring() {
        return R.ok(saasService.listExpiring(30));
    }

    @GetMapping("/sensitive-words")
    public R<List<String>> sensitiveWords() {
        return R.ok(saasService.listSensitiveWords());
    }

    @PostMapping("/sensitive-words")
    public R<Void> addSensitive(@RequestBody Map<String, String> body) {
        saasService.addSensitiveWord(body.get("word"));
        return R.ok();
    }

    /** Feign 示例：公告 AI 摘要预览 */
    @PostMapping("/ai/notice-preview")
    public R<AiAuditResponse> noticePreview(@RequestBody Map<String, String> body) {
        return R.ok(aiFeignExampleService.previewNotice(body.get("content")));
    }

    /** Feign 示例：代问 AI */
    @PostMapping("/ai/chat")
    public R<AiChatResponse> aiChat(@RequestBody Map<String, String> body) {
        return R.ok(aiFeignExampleService.proxyChat(body.get("question")));
    }
}
