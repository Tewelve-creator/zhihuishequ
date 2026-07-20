package com.community.auth.service;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.community.auth.dto.OrderCreateRequest;
import com.community.auth.entity.SaasOrder;
import com.community.auth.entity.SysPackage;
import com.community.auth.entity.SysTenant;
import com.community.auth.mapper.SaasOrderMapper;
import com.community.auth.mapper.SysTenantMapper;
import com.community.common.constant.RoleConstants;
import com.community.common.exception.BizException;
import com.community.common.exception.ErrorCode;
import com.community.common.result.PageResult;
import com.community.common.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final SaasOrderMapper orderMapper;
    private final SysTenantMapper tenantMapper;
    private final PackageService packageService;
    private final LeaseCacheService leaseCacheService;
    private final MessageService messageService;

    public PageResult<SaasOrder> page(long page, long size, Long tenantId, String status) {
        boolean platform = RoleConstants.isPlatformAdmin(TenantContext.getRole());
        if (!platform) {
            tenantId = TenantContext.getTenantId();
            if (tenantId == null) {
                return PageResult.empty(page, size);
            }
        }
        TenantContext.setSkipTenant(true);
        try {
            Page<SaasOrder> p = orderMapper.selectPage(new Page<>(page, size),
                    new LambdaQueryWrapper<SaasOrder>()
                            .eq(tenantId != null, SaasOrder::getTenantId, tenantId)
                            .eq(StringUtils.hasText(status), SaasOrder::getStatus, status)
                            .orderByDesc(SaasOrder::getId));
            return PageResult.of(p.getCurrent(), p.getSize(), p.getTotal(), p.getRecords());
        } finally {
            TenantContext.setSkipTenant(platform);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public SaasOrder create(OrderCreateRequest req) {
        Long tenantId = resolveTenantId(req.getTenantId());
        SysPackage pkg = packageService.getById(req.getPackageId());
        if (pkg.getStatus() != null && pkg.getStatus() == 0) {
            throw new BizException(ErrorCode.BAD_REQUEST, "套餐已下架");
        }
        String cycle = req.getCycle().trim().toLowerCase(Locale.ROOT);
        String orderType = req.getOrderType().trim().toLowerCase(Locale.ROOT);
        if (!oneOf(cycle, "month", "quarter", "year")) {
            throw new BizException(ErrorCode.BAD_REQUEST, "cycle 仅支持 month/quarter/year");
        }
        if (!oneOf(orderType, "new", "renew", "upgrade")) {
            throw new BizException(ErrorCode.BAD_REQUEST, "orderType 仅支持 new/renew/upgrade");
        }
        if ("upgrade".equals(orderType)) {
            assertUpgradeAllowed(tenantId, pkg);
        }

        SaasOrder order = new SaasOrder();
        order.setOrderNo("SO" + IdUtil.getSnowflakeNextIdStr());
        order.setTenantId(tenantId);
        order.setPackageId(pkg.getId());
        order.setPackageName(pkg.getName());
        order.setCycle(cycle);
        order.setAmount(calcAmount(pkg, cycle));
        order.setOrderType(orderType);
        order.setStatus("pending");
        order.setCreateBy(TenantContext.getUserId());
        order.setCreateTime(LocalDateTime.now());
        orderMapper.insert(order);
        return order;
    }

    /** 演示支付：直接标记已付并生效 */
    @Transactional(rollbackFor = Exception.class)
    public SaasOrder mockPay(Long orderId) {
        TenantContext.setSkipTenant(true);
        try {
            SaasOrder order = orderMapper.selectById(orderId);
            if (order == null) {
                throw new BizException(ErrorCode.ORDER_NOT_FOUND);
            }
            assertCanAccessOrder(order);
            if ("paid".equals(order.getStatus())) {
                return order;
            }
            if ("cancelled".equals(order.getStatus())) {
                throw new BizException(ErrorCode.BAD_REQUEST, "订单已取消");
            }
            SysTenant tenant = tenantMapper.selectById(order.getTenantId());
            if (tenant == null) {
                throw new BizException(ErrorCode.TENANT_NOT_FOUND);
            }
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime base = tenant.getExpireTime() != null && tenant.getExpireTime().isAfter(now)
                    && "renew".equals(order.getOrderType())
                    ? tenant.getExpireTime() : now;
            int months = switch (order.getCycle()) {
                case "quarter" -> 3;
                case "year" -> 12;
                default -> 1;
            };
            LocalDateTime end = base.plusMonths(months);

            order.setStatus("paid");
            order.setPayTime(now);
            order.setStartTime(base);
            order.setEndTime(end);
            orderMapper.updateById(order);

            SysPackage pkg = packageService.getById(order.getPackageId());
            tenant.setPackageId(pkg.getId());
            tenant.setFeatures(packageService.featureCsv(pkg.getId()));
            tenant.setAiQuota(pkg.getAiQuota());
            tenant.setExpireTime(end);
            tenant.setStatus("active");
            tenant.setUpdateTime(now);
            tenantMapper.updateById(tenant);
            // 支付成功：延长租期 + 解锁套餐功能码，并同步网关 Redis 快照
            leaseCacheService.publish(tenant);
            messageService.pushToPropertyAdmins(
                    tenant.getId(),
                    "续费支付成功",
                    String.format("已开通「%s」，有效期至 %s，高级功能已解锁。",
                            pkg.getName(), end),
                    "renew");
            return order;
        } finally {
            TenantContext.setSkipTenant(RoleConstants.isPlatformAdmin(TenantContext.getRole()));
        }
    }

    public void cancel(Long orderId) {
        TenantContext.setSkipTenant(true);
        try {
            SaasOrder order = orderMapper.selectById(orderId);
            if (order == null) {
                throw new BizException(ErrorCode.ORDER_NOT_FOUND);
            }
            assertCanAccessOrder(order);
            if ("paid".equals(order.getStatus())) {
                throw new BizException(ErrorCode.BAD_REQUEST, "已支付订单不可取消");
            }
            order.setStatus("cancelled");
            orderMapper.updateById(order);
        } finally {
            TenantContext.setSkipTenant(RoleConstants.isPlatformAdmin(TenantContext.getRole()));
        }
    }

    private void assertUpgradeAllowed(Long tenantId, SysPackage target) {
        TenantContext.setSkipTenant(true);
        try {
            SysTenant tenant = tenantMapper.selectById(tenantId);
            if (tenant == null || tenant.getPackageId() == null) {
                return;
            }
            SysPackage current = packageService.getById(tenant.getPackageId());
            int curSort = current.getSortNo() == null ? 0 : current.getSortNo();
            int tgtSort = target.getSortNo() == null ? 0 : target.getSortNo();
            if (tgtSort <= curSort) {
                throw new BizException(ErrorCode.BAD_REQUEST, "升级目标套餐须高于当前套餐");
            }
        } finally {
            TenantContext.setSkipTenant(RoleConstants.isPlatformAdmin(TenantContext.getRole()));
        }
    }

    private BigDecimal calcAmount(SysPackage pkg, String cycle) {
        return switch (cycle) {
            case "quarter" -> nz(pkg.getPriceQuarter());
            case "year" -> nz(pkg.getPriceYear());
            default -> nz(pkg.getPriceMonth());
        };
    }

    private BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private Long resolveTenantId(Long reqTenantId) {
        if (RoleConstants.isPlatformAdmin(TenantContext.getRole())) {
            if (reqTenantId == null) {
                throw new BizException(ErrorCode.TENANT_REQUIRED);
            }
            return reqTenantId;
        }
        Long tid = TenantContext.getTenantId();
        if (tid == null) {
            throw new BizException(ErrorCode.TENANT_REQUIRED);
        }
        if (reqTenantId != null && !tid.equals(reqTenantId)) {
            throw new BizException(ErrorCode.TENANT_MISMATCH);
        }
        return tid;
    }

    private void assertCanAccessOrder(SaasOrder order) {
        if (RoleConstants.isPlatformAdmin(TenantContext.getRole())) {
            return;
        }
        Long tid = TenantContext.getTenantId();
        if (tid == null || !tid.equals(order.getTenantId())) {
            throw new BizException(ErrorCode.TENANT_MISMATCH);
        }
    }

    private static boolean oneOf(String v, String... opts) {
        for (String o : opts) {
            if (o.equals(v)) {
                return true;
            }
        }
        return false;
    }
}
