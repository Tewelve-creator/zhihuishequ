package com.community.repair.platform;

import com.community.common.result.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/platform/hub")
@RequiredArgsConstructor
public class PlatformHubController {

    private final PlatformHubService hubService;

    @GetMapping("/risk/summary")
    public R<Map<String, Object>> riskSummary() {
        return R.ok(hubService.riskSummary());
    }

    @GetMapping("/risk/repairs")
    public R<List<Map<String, Object>>> riskRepairs(
            @RequestParam(required = false) String level,
            @RequestParam(required = false) Long tenantId,
            @RequestParam(required = false) String keyword) {
        return R.ok(hubService.riskRepairOrders(level, tenantId, keyword));
    }

    @PostMapping("/notices/dispatch")
    public R<Map<String, Object>> dispatchNotice(@RequestBody Map<String, Object> body) {
        return R.ok(hubService.publishGlobalNotice(body));
    }

    @GetMapping("/notices/dispatch")
    public R<List<Map<String, Object>>> listNoticeDispatch() {
        return R.ok(hubService.listNoticeDispatches());
    }

    @PostMapping("/activities/dispatch")
    public R<Map<String, Object>> dispatchActivity(@RequestBody Map<String, Object> body) {
        return R.ok(hubService.dispatchActivity(body));
    }

    @GetMapping("/activities/dispatch")
    public R<List<Map<String, Object>>> listActivityDispatch() {
        return R.ok(hubService.listActivityDispatches());
    }

    @GetMapping("/appeals/inspect")
    public R<List<Map<String, Object>>> inspectAppeals(
            @RequestParam(required = false) Long tenantId,
            @RequestParam(required = false) String filter,
            @RequestParam(required = false) String keyword) {
        return R.ok(hubService.inspectAppeals(tenantId, filter, keyword));
    }

    @PutMapping("/appeals/{id}/handle")
    public R<Map<String, Object>> handleAppeal(
            @PathVariable Long id, @RequestBody Map<String, Object> body) {
        String action = body.get("action") == null ? null : String.valueOf(body.get("action"));
        String reason = body.get("reason") == null ? null : String.valueOf(body.get("reason"));
        return R.ok(hubService.handleAppealViolation(id, action, reason));
    }

    @GetMapping("/reports/ops")
    public R<Map<String, Object>> reportOps(@RequestParam(required = false) Integer days) {
        return R.ok(hubService.reportOps(days));
    }

    @GetMapping("/reports/store")
    public R<Map<String, Object>> reportStore(@RequestParam(required = false) Integer days) {
        return R.ok(hubService.reportStore(days));
    }

    /** 商户营收：社区×商户总营收与社区提成（平台不抽成） */
    @GetMapping("/reports/merchant-revenue")
    public R<Map<String, Object>> reportMerchantRevenue(
            @RequestParam(required = false) Integer days,
            @RequestParam(required = false) Long tenantId,
            @RequestParam(required = false) Long shopId) {
        return R.ok(hubService.reportMerchantRevenue(days, tenantId, shopId));
    }

    @GetMapping("/reports/ai")
    public R<Map<String, Object>> reportAi(@RequestParam(required = false) Integer days) {
        return R.ok(hubService.reportAi(days));
    }
}
