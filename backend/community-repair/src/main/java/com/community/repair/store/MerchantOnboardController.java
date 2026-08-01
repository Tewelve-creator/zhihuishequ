package com.community.repair.store;

import com.community.common.result.PageResult;
import com.community.common.result.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 商圈商户入驻 + 交易佣金应收（≠ SaaS 套餐订购账务）
 */
@RestController
@RequestMapping("/store/onboard")
@RequiredArgsConstructor
public class MerchantOnboardController {

    private final MerchantOnboardService onboardService;

    @PostMapping("/applies")
    public R<Map<String, Object>> submit(@RequestBody Map<String, Object> body) {
        return R.ok(onboardService.submitApply(body));
    }

    @GetMapping("/applies")
    public R<List<Map<String, Object>>> list(
            @RequestParam(required = false) Long tenantId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword) {
        return R.ok(onboardService.listApplies(tenantId, status, keyword));
    }

    @GetMapping("/applies/{id}")
    public R<Map<String, Object>> detail(@PathVariable Long id) {
        return R.ok(onboardService.getApply(id));
    }

    @GetMapping("/applies/{id}/logs")
    public R<List<Map<String, Object>>> logs(@PathVariable Long id) {
        return R.ok(onboardService.listAuditLogs(id));
    }

    @GetMapping("/property-pending")
    public R<List<Map<String, Object>>> propertyPending(@RequestParam(required = false) String keyword) {
        return R.ok(onboardService.listPropertyPending(keyword));
    }

    @GetMapping("/platform-pending")
    public R<List<Map<String, Object>>> platformPending(@RequestParam(required = false) String keyword) {
        return R.ok(onboardService.listPlatformPending(keyword));
    }

    @PutMapping("/applies/{id}/property-audit")
    public R<Map<String, Object>> propertyAudit(
            @PathVariable Long id, @RequestBody Map<String, Object> body) {
        boolean approve = Boolean.TRUE.equals(body.get("approve"))
                || "true".equalsIgnoreCase(String.valueOf(body.get("approve")));
        String remark = body.get("remark") == null ? null : String.valueOf(body.get("remark"));
        return R.ok(onboardService.propertyAudit(id, approve, remark));
    }

    @PutMapping("/applies/{id}/platform-audit")
    public R<Map<String, Object>> platformAudit(
            @PathVariable Long id, @RequestBody Map<String, Object> body) {
        boolean approve = Boolean.TRUE.equals(body.get("approve"))
                || "true".equalsIgnoreCase(String.valueOf(body.get("approve")));
        String remark = body.get("remark") == null ? null : String.valueOf(body.get("remark"));
        return R.ok(onboardService.platformAudit(id, approve, remark));
    }

    @GetMapping("/overview")
    public R<List<Map<String, Object>>> overview(
            @RequestParam(required = false) Long tenantId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String onboardStatus,
            @RequestParam(required = false) String settleStatus) {
        return R.ok(onboardService.listOverview(tenantId, keyword, onboardStatus, settleStatus));
    }

    @GetMapping("/bills")
    public R<PageResult<Map<String, Object>>> bills(
            @RequestParam(required = false) Long tenantId,
            @RequestParam(required = false) Long merchantId,
            @RequestParam(required = false) String settleStatus,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        return R.ok(onboardService.pageBills(tenantId, merchantId, settleStatus, keyword, page, size));
    }

    @PostMapping("/bills/settle")
    @SuppressWarnings("unchecked")
    public R<Map<String, Object>> settle(@RequestBody Map<String, Object> body) {
        List<Long> ids = new java.util.ArrayList<>();
        Object raw = body.get("billIds");
        if (raw instanceof List<?> list) {
            for (Object o : list) {
                if (o != null) ids.add(Long.valueOf(String.valueOf(o)));
            }
        }
        String batchNo = body.get("batchNo") == null ? null : String.valueOf(body.get("batchNo"));
        int n = onboardService.settleBills(ids, batchNo);
        return R.ok(Map.of("settledCount", n));
    }

    @GetMapping("/profit-rules")
    public R<List<Map<String, Object>>> profitRules() {
        return R.ok(onboardService.listProfitRules());
    }

    @PostMapping("/profit-rules")
    public R<Map<String, Object>> saveProfitRule(@RequestBody Map<String, Object> body) {
        return R.ok(onboardService.saveProfitRule(body));
    }

    @DeleteMapping("/profit-rules/{id}")
    public R<Void> deleteProfitRule(@PathVariable Long id) {
        onboardService.deleteProfitRule(id);
        return R.ok();
    }
}
