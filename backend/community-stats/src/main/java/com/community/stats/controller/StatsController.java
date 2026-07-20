package com.community.stats.controller;

import com.community.common.exception.BizException;
import com.community.common.result.R;
import com.community.common.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/stats")
@RequiredArgsConstructor
public class StatsController {

    private final JdbcTemplate jdbcTemplate;

    /** 物业租户大屏：仅本社区 */
    @GetMapping("/tenant/dashboard")
    public R<Map<String, Object>> tenantDashboard() {
        Long tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            throw new BizException("缺少租户");
        }
        Map<String, Object> data = new HashMap<>();
        data.put("repairTotal", count("select count(1) from repair_order where tenant_id=?", tenantId));
        data.put("repairPending", count("select count(1) from repair_order where tenant_id=? and status='pending'", tenantId));
        data.put("noticeTotal", count("select count(1) from notice_info where tenant_id=?", tenantId));
        data.put("userTotal", count("select count(1) from sys_user where tenant_id=?", tenantId));
        data.put("aiKbTotal", count("select count(1) from ai_knowledge where tenant_id=?", tenantId));
        data.put("repairTrend", jdbcTemplate.queryForList(
                "select date(create_time) d, count(1) c from repair_order where tenant_id=? group by date(create_time) order by d desc limit 7",
                tenantId));
        return R.ok(data);
    }

    /** SaaS 运营总大屏：汇总，不含居民隐私明细 */
    @GetMapping("/saas/dashboard")
    public R<Map<String, Object>> saasDashboard() {
        if (!"platform_admin".equals(TenantContext.getRole()) && !TenantContext.isSkipTenant()) {
            // 允许网关透传后访问
        }
        Map<String, Object> data = new HashMap<>();
        data.put("tenantTotal", count("select count(1) from sys_tenant"));
        data.put("tenantActive", count("select count(1) from sys_tenant where status='active'"));
        data.put("tenantExpired", count("select count(1) from sys_tenant where status in ('degraded','frozen') or expire_time < now()"));
        data.put("orderPaidAmount", jdbcTemplate.queryForObject(
                "select ifnull(sum(amount),0) from saas_order where pay_status='paid'", Object.class));
        data.put("orderCount", count("select count(1) from saas_order where pay_status='paid'"));
        data.put("packageDist", jdbcTemplate.queryForList(
                "select package_name name, count(1) value from saas_order where pay_status='paid' group by package_name"));
        data.put("tenantActivity", jdbcTemplate.queryForList(
                "select t.name, (select count(1) from repair_order r where r.tenant_id=t.id) repair_cnt from sys_tenant t order by repair_cnt desc limit 10"));
        return R.ok(data);
    }

    @GetMapping("/export/tenant")
    public R<List<Map<String, Object>>> exportTenant() {
        Long tenantId = TenantContext.getTenantId();
        return R.ok(jdbcTemplate.queryForList(
                "select id,title,status,facility_type,create_time from repair_order where tenant_id=? order by id desc limit 1000",
                tenantId));
    }

    private long count(String sql, Object... args) {
        Long v = jdbcTemplate.queryForObject(sql, Long.class, args);
        return v == null ? 0 : v;
    }
}
