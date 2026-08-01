package com.community.repair.controller;

import com.community.common.result.R;
import com.community.repair.config.AlipayProperties;
import com.community.repair.dto.FeeBatchRequest;
import com.community.repair.dto.FeeBillSaveRequest;
import com.community.repair.dto.FeePayActionRequest;
import com.community.repair.dto.FeePayCreateRequest;
import com.community.repair.dto.FeeReceiptNoticeRequest;
import com.community.repair.service.AlipaySandboxService;
import com.community.repair.service.FeeBillService;
import com.community.repair.store.StoreService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 物业费账单：PC 缴费管理 + 居民 App 在线缴费 + 支付宝沙盒回调
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/fee")
public class PropertyFeeController {

    private final FeeBillService feeBillService;
    private final StoreService storeService;
    private final AlipaySandboxService alipaySandboxService;
    private final AlipayProperties alipayProperties;

    /* ---------- PC 物业端 ---------- */

    @GetMapping("/bills")
    public R<List<Map<String, Object>>> listBills(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String period) {
        return R.ok(feeBillService.manageList(status, keyword, period));
    }

    @PostMapping("/bills")
    public R<Map<String, Object>> createBill(@RequestBody FeeBillSaveRequest req) {
        req.setId(null);
        return R.ok(feeBillService.saveBill(req));
    }

    @PutMapping("/bills/{id}")
    public R<Map<String, Object>> updateBill(@PathVariable Long id, @RequestBody FeeBillSaveRequest req) {
        req.setId(id);
        return R.ok(feeBillService.saveBill(req));
    }

    @DeleteMapping("/bills/{id}")
    public R<Void> deleteBill(@PathVariable Long id) {
        feeBillService.deleteBill(id);
        return R.ok();
    }

    @PostMapping("/bills/batch")
    public R<Map<String, Object>> batchCreate(@RequestBody FeeBatchRequest req) {
        int n = feeBillService.batchCreate(req);
        Map<String, Object> m = new HashMap<>();
        m.put("count", n);
        return R.ok(m);
    }

    @GetMapping("/houses")
    public R<List<Map<String, Object>>> houses() {
        return R.ok(feeBillService.houseOptions());
    }

    @GetMapping("/houses/{houseId}/payers")
    public R<List<Map<String, Object>>> payers(@PathVariable Long houseId) {
        return R.ok(feeBillService.payersByHouse(houseId));
    }

    @GetMapping("/payments")
    public R<List<Map<String, Object>>> listPayments(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String payChannel) {
        return R.ok(feeBillService.managePayments(startDate, endDate, keyword, payChannel));
    }

    @GetMapping("/arrears")
    public R<Map<String, Object>> arrears(
            @RequestParam(required = false) String building,
            @RequestParam(required = false) Boolean overdueOnly,
            @RequestParam(required = false) String keyword) {
        return R.ok(feeBillService.arrearsSummary(building, overdueOnly, keyword));
    }

    @GetMapping("/receipt-notices")
    public R<List<Map<String, Object>>> listReceiptNotices() {
        return R.ok(feeBillService.listReceiptNotices());
    }

    @PostMapping("/receipt-notices")
    public R<Map<String, Object>> createReceiptNotice(@RequestBody FeeReceiptNoticeRequest req) {
        return R.ok(feeBillService.createReceiptNotice(req));
    }

    /* ---------- 居民 App ---------- */

    @GetMapping("/resident/summary")
    public R<Map<String, Object>> residentSummary(@RequestParam(required = false) Long houseId) {
        return R.ok(feeBillService.residentSummary(houseId));
    }

    @GetMapping("/resident/bills")
    public R<List<Map<String, Object>>> residentBills(
            @RequestParam(required = false) Long houseId,
            @RequestParam(required = false, defaultValue = "all") String tab) {
        return R.ok(feeBillService.residentBills(houseId, tab));
    }

    @GetMapping("/resident/bills/{id}")
    public R<Map<String, Object>> residentBillDetail(@PathVariable Long id) {
        return R.ok(feeBillService.residentBillDetail(id));
    }

    /** 取消支付中：账单恢复待缴，可重新发起缴费 */
    @PostMapping("/resident/bills/{id}/cancel-paying")
    public R<Map<String, Object>> cancelPaying(@PathVariable Long id) {
        return R.ok(feeBillService.cancelPayingBill(id));
    }

    @PostMapping("/resident/orders")
    public R<Map<String, Object>> createOrder(@RequestBody FeePayCreateRequest req) {
        return R.ok(feeBillService.createOrder(req));
    }

    @PostMapping("/resident/orders/{orderNo}/pay")
    public R<Map<String, Object>> payAction(
            @PathVariable String orderNo, @RequestBody(required = false) FeePayActionRequest req) {
        if (req == null) {
            req = new FeePayActionRequest();
            req.setAction("success");
        }
        return R.ok(feeBillService.payAction(orderNo, req));
    }

    /** 调起支付宝沙盒收银台（返回 payForm HTML） */
    @PostMapping("/resident/orders/{orderNo}/alipay")
    public R<Map<String, Object>> createAlipay(
            @PathVariable String orderNo,
            @RequestParam(required = false, defaultValue = "page") String product) {
        return R.ok(feeBillService.createAlipayPay(orderNo, product));
    }

    /** 主动向支付宝查单并落账（本地无公网异步通知时使用） */
    @PostMapping("/resident/orders/{orderNo}/alipay/sync")
    public R<Map<String, Object>> syncAlipay(@PathVariable String orderNo) {
        return R.ok(feeBillService.syncAlipayPaid(orderNo));
    }

    @GetMapping("/resident/orders/{orderNo}")
    public R<Map<String, Object>> getOrder(@PathVariable String orderNo) {
        return R.ok(feeBillService.getOrder(orderNo));
    }

    @GetMapping("/resident/orders")
    public R<List<Map<String, Object>>> listOrders(
            @RequestParam(required = false) Long houseId,
            @RequestParam(required = false) String status) {
        return R.ok(feeBillService.listOrders(houseId, status));
    }

    @GetMapping("/resident/orders/{orderNo}/receipt")
    public R<Map<String, Object>> receipt(@PathVariable String orderNo) {
        return R.ok(feeBillService.receipt(orderNo));
    }

    @GetMapping("/alipay/status")
    public R<Map<String, Object>> alipayStatus() {
        Map<String, Object> m = new HashMap<>();
        m.put("enabled", alipayProperties.isEnabled());
        m.put("sandbox", alipayProperties.isSandbox());
        m.put("ready", alipaySandboxService.ready());
        m.put("appIdConfigured", alipayProperties.getAppId() != null && !alipayProperties.getAppId().isBlank());
        return R.ok(m);
    }

    /* ---------- 支付宝回调（免登录，网关白名单；商城订单号 ST* 分流） ---------- */

    @PostMapping("/alipay/notify")
    public String alipayNotify(HttpServletRequest request) {
        Map<String, String> params = new HashMap<>();
        request.getParameterMap().forEach((k, v) -> {
            if (v != null && v.length > 0) {
                params.put(k, v[0]);
            }
        });
        String orderNo = params.get("out_trade_no");
        if (isStoreOrderNo(orderNo)) {
            return storeService.handleAlipayNotify(params);
        }
        return feeBillService.handleAlipayNotify(params);
    }

    @GetMapping("/alipay/return")
    public void alipayReturn(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Map<String, String> params = new HashMap<>();
        request.getParameterMap().forEach((k, v) -> {
            if (v != null && v.length > 0) {
                params.put(k, v[0]);
            }
        });
        String orderNo = params.get("out_trade_no");
        String url = isStoreOrderNo(orderNo)
                ? storeService.handleAlipayReturn(params)
                : feeBillService.handleAlipayReturn(params);
        response.sendRedirect(url);
    }

    private static boolean isStoreOrderNo(String orderNo) {
        return StringUtils.hasText(orderNo) && orderNo.startsWith("ST");
    }
}
