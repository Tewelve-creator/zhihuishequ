package com.community.repair.store;

import com.community.common.result.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/store/mall/coupons")
@RequiredArgsConstructor
public class ShopCouponController {

    private final ShopCouponService shopCouponService;

    @GetMapping
    public R<List<Map<String, Object>>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status) {
        return R.ok(shopCouponService.listCoupons(keyword, status));
    }

    @GetMapping("/{id}")
    public R<Map<String, Object>> detail(@PathVariable Long id) {
        return R.ok(shopCouponService.getCoupon(id));
    }

    @PostMapping
    public R<Map<String, Object>> save(@RequestBody Map<String, Object> body) {
        return R.ok(shopCouponService.saveCoupon(body));
    }

    @PutMapping("/{id}/off")
    public R<Map<String, Object>> off(@PathVariable Long id) {
        return R.ok(shopCouponService.offShelf(id));
    }

    @GetMapping("/{id}/claims")
    public R<List<Map<String, Object>>> claims(@PathVariable Long id) {
        return R.ok(shopCouponService.listClaims(id));
    }
}
