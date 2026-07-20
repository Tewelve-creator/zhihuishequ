package com.community.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.community.auth.dto.TenantCreateRequest;
import com.community.auth.entity.SaasOrder;
import com.community.auth.entity.SysPackage;
import com.community.auth.entity.SysTenant;
import com.community.auth.mapper.SaasOrderMapper;
import com.community.auth.mapper.SysTenantMapper;
import com.community.auth.vo.TenantLeaseVO;
import com.community.auth.vo.TenantStatsVO;
import com.community.common.constant.FeatureCodes;
import com.community.common.constant.RoleConstants;
import com.community.common.desensitize.DesensitizeUtil;
import com.community.common.exception.BizException;
import com.community.common.exception.ErrorCode;
import com.community.common.result.PageResult;
import com.community.common.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 租户管理：运营 CRUD/禁用；物业查看套餐与到期预警；运营仅汇总统计
 */
@Service
@RequiredArgsConstructor
public class TenantService {

    private final SysTenantMapper tenantMapper;
    private final SaasOrderMapper orderMapper;
    private final PackageService packageService;
    private final LeaseCacheService leaseCacheService;

    public PageResult<SysTenant> page(long page, long size, String keyword) {
        if (RoleConstants.isPlatformAdmin(TenantContext.getRole())) {
            TenantContext.setSkipTenant(true);
            Page<SysTenant> p = tenantMapper.selectPage(new Page<>(page, size),
                    new LambdaQueryWrapper<SysTenant>()
                            .like(StringUtils.hasText(keyword), SysTenant::getName, keyword)
                            .orderByDesc(SysTenant::getId));
            // 运营列表脱敏联系人手机，不返回隐私明细外的扩展
            for (SysTenant t : p.getRecords()) {
                t.setContactPhone(DesensitizeUtil.phone(t.getContactPhone()));
            }
            return PageResult.of(p.getCurrent(), p.getSize(), p.getTotal(), p.getRecords());
        }
        Long tid = TenantContext.getTenantId();
        if (tid == null) {
            return PageResult.empty(page, size);
        }
        SysTenant one = tenantMapper.selectById(tid);
        List<SysTenant> list = one == null ? Collections.emptyList() : List.of(one);
        return PageResult.of(page, size, list.size(), list);
    }

    public SysTenant getById(Long id) {
        assertCanAccessTenant(id);
        SysTenant t = tenantMapper.selectById(id);
        if (t == null) {
            throw new BizException(ErrorCode.TENANT_NOT_FOUND);
        }
        if (RoleConstants.isPlatformAdmin(TenantContext.getRole())) {
            t.setContactPhone(DesensitizeUtil.phone(t.getContactPhone()));
        }
        return t;
    }

    public SysTenant create(TenantCreateRequest req) {
        assertPlatform();
        SysTenant body = new SysTenant();
        body.setName(req.getName());
        body.setAddress(req.getAddress());
        body.setContactName(req.getContactName());
        body.setContactPhone(req.getContactPhone());
        body.setRemark(req.getRemark());
        body.setStatus("active");
        body.setCreateTime(LocalDateTime.now());
        body.setStorageLimitMb(1024);

        Long packageId = req.getPackageId() == null ? 1L : req.getPackageId();
        SysPackage pkg = packageService.getById(packageId);
        body.setPackageId(pkg.getId());
        body.setFeatures(packageService.featureCsv(pkg.getId()));
        body.setAiQuota(pkg.getAiQuota());
        body.setExpireTime(req.getExpireTime() != null ? req.getExpireTime() : LocalDateTime.now().plusDays(7));
        tenantMapper.insert(body);
        leaseCacheService.publish(body);
        return body;
    }

    /** 兼容旧接口 */
    public SysTenant create(SysTenant body) {
        TenantCreateRequest req = new TenantCreateRequest();
        req.setName(body.getName());
        req.setAddress(body.getAddress());
        req.setContactName(body.getContactName());
        req.setContactPhone(body.getContactPhone());
        req.setPackageId(body.getPackageId());
        req.setExpireTime(body.getExpireTime());
        req.setRemark(body.getRemark());
        return create(req);
    }

    public SysTenant update(SysTenant body) {
        assertPlatform();
        if (body.getId() == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "id 不能为空");
        }
        SysTenant old = tenantMapper.selectById(body.getId());
        if (old == null) {
            throw new BizException(ErrorCode.TENANT_NOT_FOUND);
        }
        if (body.getPackageId() != null && !body.getPackageId().equals(old.getPackageId())) {
            SysPackage pkg = packageService.getById(body.getPackageId());
            body.setFeatures(packageService.featureCsv(pkg.getId()));
            body.setAiQuota(pkg.getAiQuota());
        }
        body.setUpdateTime(LocalDateTime.now());
        tenantMapper.updateById(body);
        SysTenant updated = tenantMapper.selectById(body.getId());
        leaseCacheService.publish(updated);
        return updated;
    }

    public void setStatus(Long id, String status) {
        assertPlatform();
        if (!oneOf(status, "active", "disabled", "degraded", "frozen")) {
            throw new BizException(ErrorCode.BAD_REQUEST, "非法状态：active/degraded/frozen/disabled");
        }
        SysTenant t = tenantMapper.selectById(id);
        if (t == null) {
            throw new BizException(ErrorCode.TENANT_NOT_FOUND);
        }
        t.setStatus(status);
        // 到期降级 / 冻结：强制仅保留基础公告+报修
        if ("degraded".equals(status) || "frozen".equals(status)) {
            applyBasicOnly(t);
        }
        t.setUpdateTime(LocalDateTime.now());
        tenantMapper.updateById(t);
        leaseCacheService.publish(t);
    }

    /** 降级保底功能码 */
    public void applyBasicOnly(SysTenant t) {
        SysPackage free = packageService.findFreePackage();
        if (free != null) {
            t.setPackageId(free.getId());
            t.setAiQuota(free.getAiQuota() == null ? 0 : free.getAiQuota());
        }
        t.setFeatures(FeatureCodes.BASIC_REPAIR + "," + FeatureCodes.BASIC_NOTICE);
    }

    public void remove(Long id) {
        assertPlatform();
        tenantMapper.deleteById(id);
    }

    public List<SysTenant> listPublic() {
        TenantContext.setSkipTenant(true);
        try {
            return tenantMapper.selectList(new LambdaQueryWrapper<SysTenant>()
                    .eq(SysTenant::getStatus, "active")
                    .select(SysTenant::getId, SysTenant::getName, SysTenant::getAddress)
                    .orderByAsc(SysTenant::getId));
        } finally {
            TenantContext.setSkipTenant(false);
        }
    }

    /** 物业：查看自身套餐与到期预警 */
    public TenantLeaseVO myLease() {
        Long tid = TenantContext.getTenantId();
        if (RoleConstants.isPlatformAdmin(TenantContext.getRole()) && tid == null) {
            throw new BizException(ErrorCode.TENANT_REQUIRED, "请指定租户");
        }
        if (tid == null) {
            throw new BizException(ErrorCode.TENANT_REQUIRED);
        }
        return buildLease(tid);
    }

    public TenantLeaseVO leaseOf(Long tenantId) {
        assertCanAccessTenant(tenantId);
        return buildLease(tenantId);
    }

    /** 运营汇总统计（不含居民隐私明细） */
    public TenantStatsVO stats() {
        assertPlatform();
        TenantContext.setSkipTenant(true);
        try {
            TenantStatsVO vo = new TenantStatsVO();
            vo.setTotalTenants(tenantMapper.selectCount(null));
            vo.setActiveTenants(countByStatus("active"));
            vo.setDegradedTenants(countByStatus("degraded"));
            vo.setFrozenTenants(countByStatus("frozen"));
            vo.setDisabledTenants(countByStatus("disabled"));
            LocalDateTime deadline = LocalDateTime.now().plusDays(30);
            vo.setExpiringIn30Days(tenantMapper.selectCount(new LambdaQueryWrapper<SysTenant>()
                    .le(SysTenant::getExpireTime, deadline)
                    .ge(SysTenant::getExpireTime, LocalDateTime.now())
                    .ne(SysTenant::getStatus, "disabled")));
            vo.setPaidOrders(orderMapper.selectCount(new LambdaQueryWrapper<SaasOrder>()
                    .eq(SaasOrder::getStatus, "paid")));
            return vo;
        } finally {
            TenantContext.setSkipTenant(true);
        }
    }

    public List<Map<String, Object>> listExpiring(int withinDays) {
        assertPlatform();
        TenantContext.setSkipTenant(true);
        LocalDateTime deadline = LocalDateTime.now().plusDays(withinDays);
        List<SysTenant> list = tenantMapper.selectList(new LambdaQueryWrapper<SysTenant>()
                .le(SysTenant::getExpireTime, deadline)
                .ne(SysTenant::getStatus, "disabled")
                .orderByAsc(SysTenant::getExpireTime));
        List<Map<String, Object>> rows = new ArrayList<>();
        for (SysTenant t : list) {
            Map<String, Object> row = new HashMap<>();
            row.put("id", t.getId());
            row.put("name", t.getName());
            row.put("status", t.getStatus());
            row.put("packageId", t.getPackageId());
            row.put("expireTime", t.getExpireTime());
            row.put("daysLeft", t.getExpireTime() == null ? null
                    : ChronoUnit.DAYS.between(LocalDateTime.now(), t.getExpireTime()));
            rows.add(row);
        }
        return rows;
    }

    private TenantLeaseVO buildLease(Long tenantId) {
        TenantContext.setSkipTenant(true);
        try {
            SysTenant t = tenantMapper.selectById(tenantId);
            if (t == null) {
                throw new BizException(ErrorCode.TENANT_NOT_FOUND);
            }
            TenantLeaseVO vo = new TenantLeaseVO();
            vo.setTenantId(t.getId());
            vo.setTenantName(t.getName());
            vo.setStatus(t.getStatus());
            vo.setPackageId(t.getPackageId());
            vo.setFeatures(splitFeatures(t.getFeatures()));
            vo.setExpireTime(t.getExpireTime());
            vo.setAiQuota(t.getAiQuota());
            if (t.getPackageId() != null) {
                try {
                    SysPackage pkg = packageService.getById(t.getPackageId());
                    vo.setPackageName(pkg.getName());
                    vo.setPackageCode(pkg.getCode());
                } catch (Exception ignored) {
                    // ignore
                }
            }
            if (t.getExpireTime() != null) {
                long days = ChronoUnit.DAYS.between(LocalDateTime.now(), t.getExpireTime());
                vo.setDaysLeft(days);
                vo.setExpireWarning(days <= 30);
            } else {
                vo.setDaysLeft(null);
                vo.setExpireWarning(false);
            }
            return vo;
        } finally {
            TenantContext.setSkipTenant(RoleConstants.isPlatformAdmin(TenantContext.getRole()));
        }
    }

    private Long countByStatus(String status) {
        return tenantMapper.selectCount(new LambdaQueryWrapper<SysTenant>().eq(SysTenant::getStatus, status));
    }

    private List<String> splitFeatures(String csv) {
        if (!StringUtils.hasText(csv)) {
            return List.of();
        }
        return Arrays.stream(csv.split(",")).map(String::trim).filter(StringUtils::hasText).toList();
    }

    private void assertPlatform() {
        if (!RoleConstants.isPlatformAdmin(TenantContext.getRole())) {
            throw new BizException(ErrorCode.FORBIDDEN, "仅运营可管理租户");
        }
    }

    private void assertCanAccessTenant(Long id) {
        if (RoleConstants.isPlatformAdmin(TenantContext.getRole())) {
            return;
        }
        Long tid = TenantContext.getTenantId();
        if (tid == null || !tid.equals(id)) {
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
