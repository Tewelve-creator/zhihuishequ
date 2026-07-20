package com.community.auth.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaCheckRole;
import com.community.auth.dto.TenantCreateRequest;
import com.community.auth.entity.SysTenant;
import com.community.auth.job.TenantExpireJob;
import com.community.auth.service.TenantService;
import com.community.auth.vo.TenantLeaseVO;
import com.community.auth.vo.TenantStatsVO;
import com.community.common.constant.RoleConstants;
import com.community.common.result.PageResult;
import com.community.common.result.R;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/auth/tenants")
@RequiredArgsConstructor
public class TenantController {

    private final TenantService tenantService;
    private final TenantExpireJob tenantExpireJob;

    @GetMapping("/public")
    public R<List<SysTenant>> listPublic() {
        return R.ok(tenantService.listPublic());
    }

    @GetMapping
    @SaCheckLogin
    public R<PageResult<SysTenant>> page(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String keyword) {
        return R.ok(tenantService.page(page, size, keyword));
    }

    @GetMapping("/stats")
    @SaCheckRole(RoleConstants.PLATFORM_ADMIN)
    public R<TenantStatsVO> stats() {
        return R.ok(tenantService.stats());
    }

    @GetMapping("/expiring")
    @SaCheckRole(RoleConstants.PLATFORM_ADMIN)
    public R<List<Map<String, Object>>> expiring(@RequestParam(defaultValue = "30") int withinDays) {
        return R.ok(tenantService.listExpiring(withinDays));
    }

    /** 物业：自身套餐 / 到期预警 */
    @GetMapping("/lease/mine")
    @SaCheckLogin
    public R<TenantLeaseVO> myLease() {
        return R.ok(tenantService.myLease());
    }

    @GetMapping("/{id}/lease")
    @SaCheckLogin
    public R<TenantLeaseVO> lease(@PathVariable Long id) {
        return R.ok(tenantService.leaseOf(id));
    }

    @GetMapping("/{id}")
    @SaCheckLogin
    public R<SysTenant> detail(@PathVariable Long id) {
        return R.ok(tenantService.getById(id));
    }

    @PostMapping
    @SaCheckRole(RoleConstants.PLATFORM_ADMIN)
    @SaCheckPermission("auth:tenant:add")
    public R<SysTenant> create(@Valid @RequestBody TenantCreateRequest body) {
        return R.ok(tenantService.create(body));
    }

    @PutMapping
    @SaCheckRole(RoleConstants.PLATFORM_ADMIN)
    @SaCheckPermission("auth:tenant:edit")
    public R<SysTenant> update(@RequestBody SysTenant body) {
        return R.ok(tenantService.update(body));
    }

    /** 启用/禁用租户 */
    @PutMapping("/{id}/status")
    @SaCheckRole(RoleConstants.PLATFORM_ADMIN)
    @SaCheckPermission("auth:tenant:edit")
    public R<Void> setStatus(@PathVariable Long id, @RequestParam String status) {
        tenantService.setStatus(id, status);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    @SaCheckRole(RoleConstants.PLATFORM_ADMIN)
    @SaCheckPermission("auth:tenant:remove")
    public R<Void> remove(@PathVariable Long id) {
        tenantService.remove(id);
        return R.ok();
    }

    /** 手动触发到期扫描（运营） */
    @PostMapping("/expire-scan")
    @SaCheckRole(RoleConstants.PLATFORM_ADMIN)
    public R<Integer> expireScan() {
        return R.ok(tenantExpireJob.doScan());
    }
}
