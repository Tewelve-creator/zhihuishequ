package com.community.repair.controller;

import com.community.common.constant.RoleConstants;
import com.community.common.exception.BizException;
import com.community.common.result.R;
import com.community.common.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 物业费账单（手机物业端演示）
 * 网格员禁止查询缴费数据
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/fee")
public class PropertyFeeController {

    private final JdbcTemplate jdbcTemplate;

    @GetMapping("/bills")
    public R<List<Map<String, Object>>> listBills() {
        Long tid = TenantContext.getTenantId();
        if (tid == null) {
            throw new BizException(40001, "缺少租户上下文，禁止跨社区访问");
        }
        String role = TenantContext.getRole();
        if (RoleConstants.isGridWorker(role)) {
            throw new BizException(403, "网格员无物业费账单权限");
        }
        if (!RoleConstants.isPropertyAdmin(role)
                && !RoleConstants.isPlatformAdmin(role)
                && !RoleConstants.RESIDENT.equals(role)) {
            // staff 兼容
            if (!RoleConstants.isTenantStaff(role)) {
                throw new BizException(403, "无权查看账单");
            }
        }
        try {
            if (RoleConstants.RESIDENT.equals(role)) {
                Long uid = TenantContext.getUserId();
                return R.ok(jdbcTemplate.queryForList(
                        "select id, tenant_id as tenantId, user_id as userId, house_no as houseNo, title, amount, period, status, create_time as createTime "
                                + "from property_fee_bill where tenant_id=? and (user_id=? or user_id is null) order by id desc limit 50",
                        tid, uid));
            }
            return R.ok(jdbcTemplate.queryForList(
                    "select id, tenant_id as tenantId, user_id as userId, house_no as houseNo, title, amount, period, status, create_time as createTime "
                            + "from property_fee_bill where tenant_id=? order by id desc limit 100",
                    tid));
        } catch (Exception e) {
            return R.ok(Collections.emptyList());
        }
    }
}
