package com.community.auth.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.stp.StpUtil;
import com.community.auth.dto.OrderCreateRequest;
import com.community.auth.dto.OrderPayRequest;
import com.community.auth.dto.OrderRefundRequest;
import com.community.auth.entity.SaasOrder;
import com.community.auth.service.OrderService;
import com.community.common.constant.RoleConstants;
import com.community.common.exception.BizException;
import com.community.common.exception.ErrorCode;
import com.community.common.result.PageResult;
import com.community.common.result.R;
import com.community.common.tenant.TenantContext;
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
    public R<PageResult<SaasOrder>> page(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) Long tenantId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String orderType) {
        // 平台需订单列表权限；物业/网格员仅可查看本租户订单（服务层强制 tenantId）
        if (RoleConstants.isPlatformAdmin(TenantContext.getRole())) {
            StpUtil.checkPermission("auth:order:list");
        } else if (!RoleConstants.isTenantStaff(TenantContext.getRole())) {
            throw new BizException(ErrorCode.FORBIDDEN, "无权查看订单");
        }
        return R.ok(orderService.page(page, size, tenantId, status, orderType));
    }

    /** 新购 / 续费 / 升级下单；物业可提交续费、升级申请（pending） */
    @PostMapping
    @SaCheckLogin
    public R<SaasOrder> create(@Valid @RequestBody OrderCreateRequest req) {
        if (RoleConstants.isPlatformAdmin(TenantContext.getRole())) {
            StpUtil.checkPermission("auth:order:create");
        } else if (!RoleConstants.isTenantStaff(TenantContext.getRole())) {
            throw new BizException(ErrorCode.FORBIDDEN, "无权创建订单");
        }
        return R.ok(orderService.create(req));
    }

    /**
     * 支付生效：平台运营或本社区物业均可。
     * body.payMethod = wechat | alipay | bank | mock（演示环境即时到账）
     */
    @PostMapping("/{id}/pay")
    @SaCheckLogin
    public R<SaasOrder> pay(
            @PathVariable Long id, @RequestBody(required = false) OrderPayRequest body) {
        if (RoleConstants.isPlatformAdmin(TenantContext.getRole())) {
            StpUtil.checkPermission("auth:order:pay");
        } else if (!RoleConstants.isTenantStaff(TenantContext.getRole())) {
            throw new BizException(ErrorCode.FORBIDDEN, "无权支付订单");
        }
        String payMethod = body == null ? null : body.getPayMethod();
        return R.ok(orderService.pay(id, payMethod));
    }

    @PostMapping("/{id}/cancel")
    @SaCheckLogin
    public R<Void> cancel(@PathVariable Long id) {
        // 平台需权限；物业可取消本租户待支付申请
        if (RoleConstants.isPlatformAdmin(TenantContext.getRole())) {
            StpUtil.checkPermission("auth:order:create");
        } else if (!RoleConstants.isTenantStaff(TenantContext.getRole())) {
            throw new BizException(ErrorCode.FORBIDDEN, "无权取消订单");
        }
        orderService.cancel(id);
        return R.ok();
    }

    /** 平台运营退款 */
    @PostMapping("/{id}/refund")
    @SaCheckRole(RoleConstants.PLATFORM_ADMIN)
    public R<SaasOrder> refund(@PathVariable Long id, @RequestBody(required = false) OrderRefundRequest body) {
        String reason = body == null ? null : body.getReason();
        return R.ok(orderService.refund(id, reason));
    }
}
