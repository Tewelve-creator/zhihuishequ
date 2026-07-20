package com.community.order.controller;

import com.community.common.result.R;
import com.community.order.entity.SaasOrder;
import com.community.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/order")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping("/create")
    public R<SaasOrder> create(@RequestBody Map<String, Object> body) {
        return R.ok(orderService.create(body));
    }

    @PostMapping("/{id}/pay")
    public R<SaasOrder> pay(@PathVariable Long id) {
        return R.ok(orderService.mockPay(id));
    }

    @GetMapping("/list")
    public R<List<SaasOrder>> list(@RequestParam(required = false) Long tenantId) {
        return R.ok(orderService.list(tenantId));
    }
}
