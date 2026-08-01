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
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private static final Set<String> PAY_METHODS = Set.of("wechat", "alipay", "bank", "mock");

    private final SaasOrderMapper orderMapper;
    private final SysTenantMapper tenantMapper;
    private final PackageService packageService;
    private final LeaseCacheService leaseCacheService;
    private final MessageService messageService;
    private final OperLogService operLogService;
    private final JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void ensurePayMethodColumn() {
        try {
            Integer n = jdbcTemplate.queryForObject(
                    "select count(1) from information_schema.columns where table_schema=database() "
                            + "and table_name='saas_order' and column_name='pay_method'",
                    Integer.class);
            if (n == null || n == 0) {
                jdbcTemplate.execute(
                        "alter table saas_order add column pay_method varchar(32) null "
                                + "comment 'wechat/alipay/bank/mock' after status");
            }
        } catch (Exception e) {
            log.warn("ensure saas_order.pay_method skip: {}", e.getMessage());
        }
    }

    public PageResult<SaasOrder> page(long page, long size, Long tenantId, String status, String orderType) {
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
                            .eq(StringUtils.hasText(orderType), SaasOrder::getOrderType, orderType)
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
        boolean platform = RoleConstants.isPlatformAdmin(TenantContext.getRole());
        if (!platform) {
            // 物业可自助续费 / 升级下单，支付后立即生效，无需运营审核
            if (!oneOf(orderType, "renew", "upgrade")) {
                throw new BizException(ErrorCode.FORBIDDEN, "物业端仅可续费或升级");
            }
        }
        if ("upgrade".equals(orderType)) {
            assertUpgradeAllowed(tenantId, pkg);
        }
        assertNoDuplicatePending(tenantId, pkg.getId(), orderType);

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

    private void assertNoDuplicatePending(Long tenantId, Long packageId, String orderType) {
        Long cnt = orderMapper.selectCount(new LambdaQueryWrapper<SaasOrder>()
                .eq(SaasOrder::getTenantId, tenantId)
                .eq(SaasOrder::getPackageId, packageId)
                .eq(SaasOrder::getOrderType, orderType)
                .eq(SaasOrder::getStatus, "pending"));
        if (cnt != null && cnt > 0) {
            throw new BizException(ErrorCode.BAD_REQUEST, "已有相同待支付订单，请先完成支付或撤销后再试");
        }
    }

    /** 兼容旧调用 */
    @Transactional(rollbackFor = Exception.class)
    public SaasOrder mockPay(Long orderId) {
        return pay(orderId, "mock");
    }

    /**
     * 支付并立即生效（演示：微信/支付宝/银行卡/模拟均即时到账，写入 pay_method 供对账）。
     */
    @Transactional(rollbackFor = Exception.class)
    public SaasOrder pay(Long orderId, String payMethod) {
        String method = normalizePayMethod(payMethod);
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
            if ("cancelled".equals(order.getStatus()) || "refunded".equals(order.getStatus())) {
                throw new BizException(ErrorCode.BAD_REQUEST, "订单状态不可支付");
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
            order.setPayMethod(method);
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
            leaseCacheService.publish(tenant);
            String title = switch (order.getOrderType()) {
                case "upgrade" -> "套餐升级已生效";
                case "new" -> "套餐开通已生效";
                default -> "续费支付成功";
            };
            messageService.pushToPropertyAdmins(
                    tenant.getId(),
                    title,
                    String.format("已开通「%s」，支付方式 %s，有效期至 %s，功能权限已同步。",
                            pkg.getName(), payMethodLabel(method), end),
                    "renew");
            return order;
        } finally {
            TenantContext.setSkipTenant(RoleConstants.isPlatformAdmin(TenantContext.getRole()));
        }
    }

    private static String normalizePayMethod(String raw) {
        if (!StringUtils.hasText(raw)) {
            return "mock";
        }
        String m = raw.trim().toLowerCase(Locale.ROOT);
        if ("wx".equals(m) || "weixin".equals(m)) {
            m = "wechat";
        }
        if (!PAY_METHODS.contains(m)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "支付方式仅支持 wechat/alipay/bank/mock");
        }
        return m;
    }

    private static String payMethodLabel(String method) {
        return switch (method) {
            case "wechat" -> "微信支付";
            case "alipay" -> "支付宝";
            case "bank" -> "银行卡";
            default -> "模拟支付";
        };
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

    /** 平台运营退款：仅 paid 订单 */
    @Transactional(rollbackFor = Exception.class)
    public SaasOrder refund(Long orderId, String reason) {
        if (!RoleConstants.isPlatformAdmin(TenantContext.getRole())) {
            throw new BizException(ErrorCode.FORBIDDEN, "仅平台运营可退款");
        }
        TenantContext.setSkipTenant(true);
        try {
            SaasOrder order = orderMapper.selectById(orderId);
            if (order == null) {
                throw new BizException(ErrorCode.ORDER_NOT_FOUND);
            }
            if (!"paid".equals(order.getStatus())) {
                throw new BizException(ErrorCode.BAD_REQUEST, "仅已支付订单可退款");
            }
            LocalDateTime now = LocalDateTime.now();
            order.setStatus("refunded");
            order.setRefundTime(now);
            if (StringUtils.hasText(reason)) {
                order.setRefundReason(reason.trim());
            }
            orderMapper.updateById(order);

            String detail = String.format("订单 %s 退款%s", order.getOrderNo(),
                    StringUtils.hasText(reason) ? "：" + reason.trim() : "");
            operLogService.write("order", "refund", detail);
            try {
                messageService.pushToPropertyAdmins(
                        order.getTenantId(),
                        "订单退款通知",
                        String.format("订单 %s（%s）已退款。", order.getOrderNo(), order.getPackageName()),
                        "refund");
            } catch (Exception ignored) {
                // 消息可选
            }
            return order;
        } finally {
            TenantContext.setSkipTenant(true);
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
