package com.community.repair.store;

import com.community.common.result.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Objects;

@RestController
@RequestMapping("/store/mall")
@RequiredArgsConstructor
public class ShopMallController {

    private final ShopMallService shopMallService;

    /* ---- 物业：商户 ---- */

    @GetMapping("/shops")
    public R<List<Map<String, Object>>> shops(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status) {
        return R.ok(shopMallService.listShops(keyword, status));
    }

    @PostMapping("/shops")
    public R<Map<String, Object>> createShop(@RequestBody Map<String, Object> body) {
        return R.ok(shopMallService.createShop(body));
    }

    @GetMapping("/shops/{id}")
    public R<Map<String, Object>> shop(@PathVariable Long id) {
        return R.ok(shopMallService.getShop(id));
    }

    @PutMapping("/shops/{id}/status")
    public R<Map<String, Object>> shopStatus(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        int status = body.get("status") == null ? 0 : Integer.parseInt(String.valueOf(body.get("status")));
        return R.ok(shopMallService.updateShopStatus(id, status));
    }

    @PutMapping("/shops/{id}/password")
    public R<Map<String, Object>> resetPassword(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        String pwd = body.get("password") == null ? null : String.valueOf(body.get("password"));
        return R.ok(shopMallService.resetShopPassword(id, pwd));
    }

    @PutMapping("/shops/{id}/account")
    public R<Map<String, Object>> bindAccount(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return R.ok(shopMallService.bindShopAccount(id, body));
    }

    /* ---- 商户：本店 ---- */

    @GetMapping("/mine")
    public R<Map<String, Object>> mine() {
        return R.ok(shopMallService.myShop());
    }

    @PutMapping("/mine")
    public R<Map<String, Object>> updateMine(@RequestBody Map<String, Object> body) {
        return R.ok(shopMallService.updateMyShop(body));
    }

    @PutMapping("/mine/notify-settings")
    public R<Map<String, Object>> updateNotifySettings(@RequestBody Map<String, Object> body) {
        return R.ok(shopMallService.updateMyNotifySettings(body));
    }

    @GetMapping("/mine/revenue")
    public R<Map<String, Object>> myRevenue(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        return R.ok(shopMallService.myRevenue(startDate, endDate));
    }

    /* ---- 商品 / 分类 ---- */

    @GetMapping("/products")
    public R<List<Map<String, Object>>> products(
            @RequestParam(required = false) Long shopId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Boolean onSaleOnly,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Integer status) {
        return R.ok(shopMallService.listProducts(shopId, keyword, onSaleOnly, categoryId, status));
    }

    @PostMapping("/products")
    public R<Map<String, Object>> saveProduct(@RequestBody Map<String, Object> body) {
        return R.ok(shopMallService.saveProduct(body));
    }

    @PutMapping("/products/batch-status")
    public R<Integer> batchStatus(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Object> raw = body.get("ids") instanceof List ? (List<Object>) body.get("ids") : List.of();
        List<Long> ids = raw.stream()
                .filter(Objects::nonNull)
                .map(o -> Long.parseLong(String.valueOf(o)))
                .toList();
        int status = body.get("status") == null ? 0 : Integer.parseInt(String.valueOf(body.get("status")));
        return R.ok(shopMallService.batchUpdateStatus(ids, status));
    }

    @PutMapping("/products/batch-stock")
    public R<Integer> batchStock(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Object> raw = body.get("ids") instanceof List ? (List<Object>) body.get("ids") : List.of();
        List<Long> ids = raw.stream()
                .filter(Objects::nonNull)
                .map(o -> Long.parseLong(String.valueOf(o)))
                .toList();
        int stock = body.get("stock") == null ? 0 : Integer.parseInt(String.valueOf(body.get("stock")));
        return R.ok(shopMallService.batchUpdateStock(ids, stock));
    }

    @DeleteMapping("/products/{id}")
    public R<Void> deleteProduct(@PathVariable Long id) {
        shopMallService.deleteProduct(id);
        return R.ok();
    }

    @GetMapping("/categories")
    public R<List<Map<String, Object>>> categories(@RequestParam(required = false) Long shopId) {
        return R.ok(shopMallService.listCategories(shopId));
    }

    @PostMapping("/categories")
    public R<Map<String, Object>> saveCategory(@RequestBody Map<String, Object> body) {
        return R.ok(shopMallService.saveCategory(body));
    }

    /* ---- 订单 ---- */

    @GetMapping("/orders")
    public R<List<Map<String, Object>>> orders(
            @RequestParam(required = false) Long shopId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword) {
        return R.ok(shopMallService.listOrders(shopId, status, keyword));
    }

    @PutMapping("/orders/{id}/status")
    public R<Map<String, Object>> orderStatus(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        String action = body.get("action") == null
                ? (body.get("status") == null ? null : String.valueOf(body.get("status")))
                : String.valueOf(body.get("action"));
        return R.ok(shopMallService.updateOrderStatus(id, action));
    }
}
