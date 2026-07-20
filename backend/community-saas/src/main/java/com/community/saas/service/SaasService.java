package com.community.saas.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.community.common.exception.BizException;
import com.community.common.tenant.TenantContext;
import com.community.saas.entity.SysPackage;
import com.community.saas.entity.SysTenant;
import com.community.saas.mapper.SysPackageMapper;
import com.community.saas.mapper.SysTenantMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SaasService {

    private final SysTenantMapper tenantMapper;
    private final SysPackageMapper packageMapper;
    private final JdbcTemplate jdbcTemplate;

    public List<SysPackage> publicPackages() {
        return packageMapper.selectList(new LambdaQueryWrapper<SysPackage>()
                .eq(SysPackage::getStatus, 1)
                .orderByAsc(SysPackage::getId));
    }

    public List<SysTenant> listTenants() {
        requirePlatform();
        TenantContext.setSkipTenant(true);
        return tenantMapper.selectList(new LambdaQueryWrapper<SysTenant>().orderByDesc(SysTenant::getId));
    }

    public SysTenant createTenant(SysTenant tenant) {
        requirePlatform();
        TenantContext.setSkipTenant(true);
        if (tenant.getPackageId() != null) {
            SysPackage pkg = packageMapper.selectById(tenant.getPackageId());
            if (pkg != null) {
                tenant.setFeatures(pkg.getFeatures());
                tenant.setAiQuota(pkg.getAiQuota());
            }
        }
        if (tenant.getStatus() == null) {
            tenant.setStatus("active");
        }
        if (tenant.getExpireTime() == null) {
            tenant.setExpireTime(LocalDateTime.now().plusDays(7));
        }
        if (tenant.getStorageLimitMb() == null) {
            tenant.setStorageLimitMb(1024);
        }
        tenant.setCreateTime(LocalDateTime.now());
        tenantMapper.insert(tenant);
        return tenant;
    }

    public SysTenant updateTenant(SysTenant tenant) {
        requirePlatform();
        TenantContext.setSkipTenant(true);
        tenantMapper.updateById(tenant);
        return tenantMapper.selectById(tenant.getId());
    }

    public void deleteTenant(Long id) {
        requirePlatform();
        TenantContext.setSkipTenant(true);
        tenantMapper.deleteById(id);
    }

    public Map<String, Object> currentTenant() {
        Long tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            throw new BizException("无租户信息");
        }
        TenantContext.setSkipTenant(true);
        SysTenant t = tenantMapper.selectById(tenantId);
        SysPackage pkg = t.getPackageId() == null ? null : packageMapper.selectById(t.getPackageId());
        Map<String, Object> map = new HashMap<>();
        map.put("tenant", t);
        map.put("package", pkg);
        map.put("daysLeft", t.getExpireTime() == null ? null :
                java.time.Duration.between(LocalDateTime.now(), t.getExpireTime()).toDays());
        return map;
    }

    public SysPackage savePackage(SysPackage pkg) {
        requirePlatform();
        if (pkg.getId() == null) {
            if (pkg.getStatus() == null) {
                pkg.setStatus(1);
            }
            packageMapper.insert(pkg);
        } else {
            packageMapper.updateById(pkg);
        }
        return pkg;
    }

    /** 30 天内到期或已过期租户（运营续费提醒列表） */
    public List<SysTenant> listExpiring(int withinDays) {
        requirePlatform();
        TenantContext.setSkipTenant(true);
        LocalDateTime deadline = LocalDateTime.now().plusDays(withinDays);
        return tenantMapper.selectList(new LambdaQueryWrapper<SysTenant>()
                .le(SysTenant::getExpireTime, deadline)
                .ne(SysTenant::getStatus, "disabled")
                .orderByAsc(SysTenant::getExpireTime));
    }

    public List<String> listSensitiveWords() {
        return jdbcTemplate.queryForList("select word from sys_sensitive_word order by id", String.class);
    }

    public void addSensitiveWord(String word) {
        requirePlatform();
        if (word == null || word.isBlank()) {
            throw new BizException("敏感词不能为空");
        }
        Integer c = jdbcTemplate.queryForObject(
                "select count(1) from sys_sensitive_word where word=?", Integer.class, word.trim());
        if (c != null && c > 0) {
            return;
        }
        jdbcTemplate.update("insert into sys_sensitive_word(word) values(?)", word.trim());
    }

    private void requirePlatform() {
        if (!"platform_admin".equals(TenantContext.getRole()) && !TenantContext.isSkipTenant()) {
            // 网关已透传 role；本地直连时允许跳过
            String role = TenantContext.getRole();
            if (role != null && !"platform_admin".equals(role)) {
                throw new BizException(403, "仅平台运营可操作");
            }
        }
    }
}
