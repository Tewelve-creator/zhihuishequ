package com.community.repair.store;

import com.community.common.result.PageResult;
import com.community.common.result.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 商户营销资金账户（独立于 SaaS 租户套餐订购账务）
 */
@RestController
@RequestMapping("/store/wallet")
@RequiredArgsConstructor
public class MerchantWalletController {

    private final MerchantWalletService walletService;

    @GetMapping("/accounts")
    public R<List<Map<String, Object>>> accounts(
            @RequestParam(required = false) Long tenantId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Boolean warnOnly) {
        return R.ok(walletService.listAccounts(tenantId, keyword, warnOnly));
    }

    @GetMapping("/accounts/{merchantId}")
    public R<Map<String, Object>> account(@PathVariable Long merchantId) {
        return R.ok(walletService.accountDetail(merchantId));
    }

    @GetMapping("/warn-summary")
    public R<Map<String, Object>> warnSummary() {
        return R.ok(walletService.warnSummary());
    }

    @PostMapping("/merchants")
    public R<Map<String, Object>> createMerchant(@RequestBody Map<String, Object> body) {
        return R.ok(walletService.createMerchant(body));
    }

    @PutMapping("/accounts/{merchantId}/warn-threshold")
    public R<Map<String, Object>> warnThreshold(
            @PathVariable Long merchantId, @RequestBody Map<String, Object> body) {
        BigDecimal th = body.get("warnThreshold") == null
                ? null
                : new BigDecimal(String.valueOf(body.get("warnThreshold")));
        return R.ok(walletService.updateWarnThreshold(merchantId, th));
    }

    @PostMapping("/accounts/{merchantId}/recharge")
    public R<Map<String, Object>> recharge(
            @PathVariable Long merchantId, @RequestBody Map<String, Object> body) {
        BigDecimal amount = new BigDecimal(String.valueOf(body.get("amount")));
        String remark = body.get("remark") == null ? null : String.valueOf(body.get("remark"));
        return R.ok(walletService.recharge(merchantId, amount, remark));
    }

    @PostMapping("/accounts/{merchantId}/consume")
    public R<Map<String, Object>> consume(
            @PathVariable Long merchantId, @RequestBody Map<String, Object> body) {
        BigDecimal amount = new BigDecimal(String.valueOf(body.get("amount")));
        String remark = body.get("remark") == null ? null : String.valueOf(body.get("remark"));
        return R.ok(walletService.consume(merchantId, amount, remark));
    }

    @PostMapping("/accounts/{merchantId}/withdraw")
    public R<Map<String, Object>> withdraw(
            @PathVariable Long merchantId, @RequestBody Map<String, Object> body) {
        BigDecimal amount = new BigDecimal(String.valueOf(body.get("amount")));
        String remark = body.get("remark") == null ? null : String.valueOf(body.get("remark"));
        return R.ok(walletService.applyWithdraw(merchantId, amount, remark));
    }

    @GetMapping("/ledger")
    public R<PageResult<Map<String, Object>>> ledger(
            @RequestParam(required = false) Long tenantId,
            @RequestParam(required = false) Long merchantId,
            @RequestParam(required = false) String bizType,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        return R.ok(walletService.pageLedger(tenantId, merchantId, bizType, keyword, page, size));
    }

    @GetMapping("/withdraws")
    public R<List<Map<String, Object>>> withdraws(
            @RequestParam(required = false) Long tenantId,
            @RequestParam(required = false) Long merchantId,
            @RequestParam(required = false) String status) {
        return R.ok(walletService.listWithdraws(tenantId, merchantId, status));
    }

    @PutMapping("/withdraws/{id}/audit")
    public R<Map<String, Object>> audit(
            @PathVariable Long id, @RequestBody Map<String, Object> body) {
        String status = body.get("status") == null ? null : String.valueOf(body.get("status"));
        String remark = body.get("auditRemark") == null ? null : String.valueOf(body.get("auditRemark"));
        return R.ok(walletService.auditWithdraw(id, status, remark));
    }
}
