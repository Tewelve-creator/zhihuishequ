package com.community.auth.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckPermission;
import com.community.auth.dto.OrderCreateRequest;
import com.community.auth.entity.SaasOrder;
import com.community.auth.service.OrderService;
import com.community.common.result.PageResult;
import com.community.common.result.R;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @GetMapping
    @SaCheckLogin
    @SaCheckPermission("auth:order:list")
    public R<PageResult<SaasOrder>> page(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) Long tenantId,
            @RequestParam(required = false) String status) {
        return R.ok(orderService.page(page, size, tenantId, status));
    }

    /** 新购 / 续费 / 升级下单 */
    @PostMapping
    @SaCheckLogin
    @SaCheckPermission("auth:order:create")
    public R<SaasOrder> create(@Valid @RequestBody OrderCreateRequest req) {
        return R.ok(orderService.create(req));
    }

    /** 演示支付（正式对接支付回调后替换） */
    @PostMapping("/{id}/pay")
    @SaCheckLogin
    @SaCheckPermission("auth:order:pay")
    public R<SaasOrder> pay(@PathVariable Long id) {
        return R.ok(orderService.mockPay(id));
    }

    @PostMapping("/{id}/cancel")
    @SaCheckLogin
    @SaCheckPermission("auth:order:create")
    public R<Void> cancel(@PathVariable Long id) {
        orderService.cancel(id);
        return R.ok();
    }
}
