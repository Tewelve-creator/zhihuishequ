package com.community.order.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.community.common.exception.BizException;
import com.community.common.tenant.TenantContext;
import com.community.order.entity.SaasOrder;
import com.community.order.mapper.SaasOrderMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final SaasOrderMapper orderMapper;
    private final JdbcTemplate jdbcTemplate;

    public SaasOrder create(Map<String, Object> body) {
        Long tenantId = body.get("tenantId") != null
                ? Long.valueOf(body.get("tenantId").toString())
                : TenantContext.getTenantId();
        Long packageId = Long.valueOf(body.get("packageId").toString());
        String cycle = String.valueOf(body.getOrDefault("cycle", "year"));

        Map<String, Object> pkg = jdbcTemplate.queryForMap("select * from sys_package where id = ?", packageId);
        BigDecimal amount = switch (cycle) {
            case "month" -> (BigDecimal) pkg.get("price_month");
            case "quarter" -> (BigDecimal) pkg.get("price_quarter");
            default -> (BigDecimal) pkg.get("price_year");
        };

        SaasOrder order = new SaasOrder();
        order.setTenantId(tenantId);
        order.setPackageId(packageId);
        order.setPackageName(String.valueOf(pkg.get("name")));
        order.setCycle(cycle);
        order.setAmount(amount);
        order.setPayStatus("unpaid");
        order.setFeatures(String.valueOf(pkg.get("features")));
        order.setCreateTime(LocalDateTime.now());
        orderMapper.insert(order);
        return order;
    }

    @Transactional
    public SaasOrder mockPay(Long orderId) {
        TenantContext.setSkipTenant(true);
        SaasOrder order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BizException("订单不存在");
        }
        if ("paid".equals(order.getPayStatus())) {
            return order;
        }
        int months = switch (order.getCycle()) {
            case "month" -> 1;
            case "quarter" -> 3;
            default -> 12;
        };
        LocalDateTime start = LocalDateTime.now();
        LocalDateTime end = start.plusMonths(months);
        order.setPayStatus("paid");
        order.setPayTime(start);
        order.setStartTime(start);
        order.setEndTime(end);
        orderMapper.updateById(order);

        jdbcTemplate.update(
                "update sys_tenant set package_id=?, features=?, status='active', expire_time=?, ai_quota=? where id=?",
                order.getPackageId(),
                order.getFeatures(),
                end,
                jdbcTemplate.queryForObject("select ai_quota from sys_package where id=?", Integer.class, order.getPackageId()),
                order.getTenantId()
        );
        return order;
    }

    public List<SaasOrder> list(Long tenantId) {
        if ("platform_admin".equals(TenantContext.getRole())) {
            TenantContext.setSkipTenant(true);
            return orderMapper.selectList(new LambdaQueryWrapper<SaasOrder>()
                    .eq(tenantId != null, SaasOrder::getTenantId, tenantId)
                    .orderByDesc(SaasOrder::getId));
        }
        return orderMapper.selectList(new LambdaQueryWrapper<SaasOrder>()
                .eq(SaasOrder::getTenantId, TenantContext.getTenantId())
                .orderByDesc(SaasOrder::getId));
    }
}
