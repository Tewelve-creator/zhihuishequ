package com.community.repair.store;

import com.community.common.result.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/store")
@RequiredArgsConstructor
public class StoreController {

    private final StoreService storeService;
    private final ShopCouponService shopCouponService;

    @GetMapping("/info")
    public R<Map<String, Object>> info() {
        return R.ok(storeService.getStore());
    }

    /** 居民：营业中商户列表 */
    @GetMapping("/shops")
    public R<List<Map<String, Object>>> openShops() {
        return R.ok(storeService.listOpenShops());
    }

    /** 居民：店铺详情（封面/头像/简介） */
    @GetMapping("/shops/{id}")
    public R<Map<String, Object>> openShop(@PathVariable Long id) {
        return R.ok(storeService.getOpenShop(id));
    }

    @GetMapping("/shops/{id}/categories")
    public R<List<Map<String, Object>>> shopCategories(@PathVariable Long id) {
        return R.ok(storeService.listShopCategories(id));
    }

    @GetMapping("/shops/{id}/products")
    public R<List<Map<String, Object>>> shopProducts(
            @PathVariable Long id,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String keyword) {
        return R.ok(storeService.listShopProducts(id, categoryId, keyword));
    }

    /** 居民：店铺可领优惠券 */
    @GetMapping("/shops/{id}/coupons")
    public R<List<Map<String, Object>>> shopCoupons(@PathVariable Long id) {
        return R.ok(shopCouponService.listOpenShopCoupons(id));
    }

    /** 居民：我的可用优惠券（结算选用） */
    @GetMapping("/shops/{id}/coupons/mine")
    public R<List<Map<String, Object>>> myShopCoupons(
            @PathVariable Long id,
            @RequestParam(required = false) BigDecimal goodsAmount) {
        return R.ok(shopCouponService.listMyUsableCoupons(id, goodsAmount));
    }

    /** 居民：领取优惠券 */
    @PostMapping("/shops/{shopId}/coupons/{couponId}/claim")
    public R<Map<String, Object>> claimCoupon(@PathVariable Long shopId, @PathVariable Long couponId) {
        return R.ok(shopCouponService.claimForUser(shopId, couponId));
    }

    @PutMapping("/info")
    public R<Map<String, Object>> saveInfo(@RequestBody Map<String, Object> body) {
        return R.ok(storeService.saveStore(body));
    }

    @GetMapping("/categories")
    public R<List<Map<String, Object>>> categories(@RequestParam(defaultValue = "false") boolean all) {
        return R.ok(storeService.listCategories(all));
    }

    @PostMapping("/categories")
    public R<Map<String, Object>> saveCategory(@RequestBody Map<String, Object> body) {
        return R.ok(storeService.saveCategory(body));
    }

    @DeleteMapping("/categories/{id}")
    public R<Void> delCategory(@PathVariable Long id) {
        storeService.deleteCategory(id);
        return R.ok();
    }

    @GetMapping("/products")
    public R<List<Map<String, Object>>> products(
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Boolean onSaleOnly) {
        return R.ok(storeService.listProducts(categoryId, keyword, onSaleOnly));
    }

    @PostMapping("/products")
    public R<Map<String, Object>> saveProduct(@RequestBody Map<String, Object> body) {
        return R.ok(storeService.saveProduct(body));
    }

    @DeleteMapping("/products/{id}")
    public R<Void> delProduct(@PathVariable Long id) {
        storeService.deleteProduct(id);
        return R.ok();
    }

    @GetMapping("/cart")
    public R<List<Map<String, Object>>> cart() {
        return R.ok(storeService.listCart());
    }

    @PostMapping("/cart")
    public R<Void> addCart(@RequestBody Map<String, Object> body) {
        storeService.addCart(
                body.get("productId") == null ? null : Long.valueOf(String.valueOf(body.get("productId"))),
                body.get("quantity") == null ? 1 : Integer.valueOf(String.valueOf(body.get("quantity"))));
        return R.ok();
    }

    @PutMapping("/cart/{id}")
    public R<Void> updateCart(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Integer qty = body.get("quantity") == null ? null : Integer.valueOf(String.valueOf(body.get("quantity")));
        storeService.updateCart(id, qty);
        return R.ok();
    }

    @DeleteMapping("/cart")
    public R<Void> clearCart() {
        storeService.clearCart();
        return R.ok();
    }

    @PostMapping("/orders")
    public R<Map<String, Object>> createOrder(@RequestBody Map<String, Object> body) {
        return R.ok(storeService.createOrder(body));
    }

    @PostMapping("/orders/{orderNo}/pay")
    public R<Map<String, Object>> pay(@PathVariable String orderNo, @RequestBody(required = false) Map<String, Object> body) {
        String action = body == null ? "success" : String.valueOf(body.getOrDefault("action", "success"));
        String channel = body == null ? "MOCK" : String.valueOf(body.getOrDefault("channel",
                body.getOrDefault("payChannel", "MOCK")));
        return R.ok(storeService.payOrder(orderNo, action, channel));
    }

    /** 调起支付宝沙盒收银台 */
    @PostMapping("/orders/{orderNo}/alipay")
    public R<Map<String, Object>> createAlipay(
            @PathVariable String orderNo,
            @RequestParam(required = false, defaultValue = "page") String product) {
        return R.ok(storeService.createAlipayPay(orderNo, product));
    }

    /** 向支付宝查单并同步落账 */
    @PostMapping("/orders/{orderNo}/alipay/sync")
    public R<Map<String, Object>> syncAlipay(@PathVariable String orderNo) {
        return R.ok(storeService.syncAlipayPaid(orderNo));
    }

    @GetMapping("/orders")
    public R<List<Map<String, Object>>> orders(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword) {
        return R.ok(storeService.listOrders(status, keyword));
    }

    @GetMapping("/orders/{id}")
    public R<Map<String, Object>> orderDetail(@PathVariable Long id) {
        return R.ok(storeService.getOrder(id));
    }

    @PutMapping("/orders/{id}/cancel")
    public R<Map<String, Object>> cancel(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> body) {
        String reason = body == null ? null : (String) body.get("reason");
        return R.ok(storeService.cancelOrder(id, reason));
    }

    @PutMapping("/orders/{id}/action")
    public R<Map<String, Object>> action(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return R.ok(storeService.staffAction(id, String.valueOf(body.get("action"))));
    }

    @PostMapping("/orders/{id}/refund")
    public R<Map<String, Object>> refund(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> body) {
        String reason = body == null ? "居民申请退款" : String.valueOf(body.getOrDefault("reason", "居民申请退款"));
        return R.ok(storeService.applyRefund(id, reason));
    }

    @PutMapping("/orders/{id}/refund/audit")
    public R<Map<String, Object>> auditRefund(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        boolean approve = Boolean.TRUE.equals(body.get("approve")) || "APPROVED".equalsIgnoreCase(String.valueOf(body.get("status")));
        return R.ok(storeService.auditRefund(id, approve, body.get("remark") == null ? null : String.valueOf(body.get("remark"))));
    }

    /** 物业端直接退款 */
    @PutMapping("/orders/{id}/refund/direct")
    public R<Map<String, Object>> staffRefund(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> body) {
        String reason = body == null ? null : (body.get("reason") == null ? null : String.valueOf(body.get("reason")));
        return R.ok(storeService.staffRefund(id, reason));
    }

    @GetMapping("/settlement")
    public R<Map<String, Object>> settlement(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        return R.ok(storeService.settlementSummary(startDate, endDate));
    }

    @GetMapping("/refunds")
    public R<List<Map<String, Object>>> refunds() {
        return R.ok(storeService.listRefunds());
    }

    @GetMapping("/todo-summary")
    public R<Map<String, Object>> todo() {
        return R.ok(storeService.staffTodo());
    }
}
