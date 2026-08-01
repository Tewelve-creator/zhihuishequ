package com.community.auth.service;

import cn.hutool.crypto.digest.DigestUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.community.auth.dto.TenantAdminAccountRequest;
import com.community.auth.dto.TenantCreateRequest;
import com.community.auth.entity.SaasOrder;
import com.community.auth.entity.SysPackage;
import com.community.auth.entity.SysTenant;
import com.community.auth.entity.SysUser;
import com.community.auth.mapper.SaasOrderMapper;
import com.community.auth.mapper.SysTenantMapper;
import com.community.auth.mapper.SysUserMapper;
import com.community.auth.vo.TenantLeaseVO;
import com.community.auth.vo.TenantStatsVO;
import cn.dev33.satoken.stp.StpUtil;
import com.community.common.auth.AuthConstants;
import com.community.common.auth.LoginUser;
import com.community.common.constant.FeatureCodes;
import com.community.common.constant.RoleConstants;
import com.community.common.exception.BizException;
import com.community.common.exception.ErrorCode;
import com.community.common.result.PageResult;
import com.community.common.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
    private final SysUserMapper userMapper;
    private final PackageService packageService;
    private final LeaseCacheService leaseCacheService;
    private final OperLogService operLogService;
    private final JdbcTemplate jdbcTemplate;
    private final AuthService authService;

    public PageResult<SysTenant> page(long page, long size, String keyword) {
        if (RoleConstants.isPlatformAdmin(TenantContext.getRole())) {
            TenantContext.setSkipTenant(true);
            Page<SysTenant> p = tenantMapper.selectPage(new Page<>(page, size),
                    new LambdaQueryWrapper<SysTenant>()
                            .like(StringUtils.hasText(keyword), SysTenant::getName, keyword)
                            .orderByDesc(SysTenant::getId));
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
        return t;
    }

    @Transactional(rollbackFor = Exception.class)
    public SysTenant create(TenantCreateRequest req) {
        assertPlatform();
        String adminPhone = req.getAdminPhone() == null ? "" : req.getAdminPhone().trim();
        String adminPassword = req.getAdminPassword() == null ? "" : req.getAdminPassword().trim();
        if (!StringUtils.hasText(adminPhone)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "请设置管理端账号（手机号）");
        }
        if (!StringUtils.hasText(adminPassword) || adminPassword.length() < 6) {
            throw new BizException(ErrorCode.BAD_REQUEST, "管理端密码至少 6 位");
        }

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

        // 同步开通物业管理端账号
        SysUser staff = new SysUser();
        staff.setTenantId(body.getId());
        staff.setPhone(adminPhone);
        staff.setPassword(adminPassword);
        staff.setNickname(StringUtils.hasText(req.getAdminNickname())
                ? req.getAdminNickname().trim()
                : (StringUtils.hasText(req.getContactName()) ? req.getContactName() : adminPhone));
        staff.setRole(RoleConstants.PROPERTY_ADMIN);
        authService.createStaff(staff);

        operLogService.write("tenant", "create",
                String.format("开通租户 %s(%d)，管理端账号 %s", body.getName(), body.getId(), adminPhone));
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
        operLogService.write("tenant", "setStatus",
                String.format("租户 %s(%d) 状态 -> %s", t.getName(), id, status));
    }

    public SysTenant updateAiQuota(Long id, Integer aiQuota) {
        assertPlatform();
        if (aiQuota == null || aiQuota < 0) {
            throw new BizException(ErrorCode.BAD_REQUEST, "aiQuota 须为非负整数");
        }
        SysTenant t = tenantMapper.selectById(id);
        if (t == null) {
            throw new BizException(ErrorCode.TENANT_NOT_FOUND);
        }
        t.setAiQuota(aiQuota);
        t.setUpdateTime(LocalDateTime.now());
        tenantMapper.updateById(t);
        leaseCacheService.publish(t);
        operLogService.write("tenant", "updateAiQuota",
                String.format("租户 %s(%d) AI 额度 -> %d", t.getName(), id, aiQuota));
        return t;
    }

    /** 平台：单独授权租户功能码（不改套餐） */
    public SysTenant updateFeatures(Long id, List<String> featureCodes) {
        assertPlatform();
        SysTenant t = tenantMapper.selectById(id);
        if (t == null) {
            throw new BizException(ErrorCode.TENANT_NOT_FOUND);
        }
        if (featureCodes == null) {
            featureCodes = List.of();
        }
        String csv = featureCodes.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .collect(java.util.stream.Collectors.joining(","));
        t.setFeatures(csv);
        t.setUpdateTime(LocalDateTime.now());
        tenantMapper.updateById(t);
        leaseCacheService.publish(t);
        operLogService.write("tenant", "updateFeatures",
                String.format("租户 %s(%d) 功能授权 -> %s", t.getName(), id, csv));
        return t;
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

    /** 查询租户物业管理端账号（不含密码） */
    public Map<String, Object> getAdminAccount(Long tenantId) {
        assertPlatform();
        if (tenantMapper.selectById(tenantId) == null) {
            throw new BizException(ErrorCode.TENANT_NOT_FOUND);
        }
        TenantContext.setSkipTenant(true);
        try {
            SysUser admin = findPropertyAdmin(tenantId);
            Map<String, Object> row = new HashMap<>();
            row.put("tenantId", tenantId);
            if (admin == null) {
                row.put("exists", false);
                return row;
            }
            row.put("exists", true);
            row.put("userId", admin.getId());
            row.put("adminPhone", admin.getPhone());
            row.put("adminNickname", admin.getNickname());
            row.put("status", admin.getStatus());
            return row;
        } finally {
            TenantContext.setSkipTenant(true);
        }
    }

    /** 设置/重置租户物业管理端账号 */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> upsertAdminAccount(Long tenantId, TenantAdminAccountRequest req) {
        assertPlatform();
        if (tenantMapper.selectById(tenantId) == null) {
            throw new BizException(ErrorCode.TENANT_NOT_FOUND);
        }
        String phone = req.getAdminPhone() == null ? "" : req.getAdminPhone().trim();
        if (!StringUtils.hasText(phone)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "请填写管理端账号（手机号）");
        }
        String password = req.getAdminPassword() == null ? null : req.getAdminPassword().trim();
        String nickname = StringUtils.hasText(req.getAdminNickname()) ? req.getAdminNickname().trim() : null;

        TenantContext.setSkipTenant(true);
        try {
            SysUser admin = findPropertyAdmin(tenantId);
            if (admin == null) {
                if (!StringUtils.hasText(password) || password.length() < 6) {
                    throw new BizException(ErrorCode.BAD_REQUEST, "新建管理端账号时密码至少 6 位");
                }
                SysUser staff = new SysUser();
                staff.setTenantId(tenantId);
                staff.setPhone(phone);
                staff.setPassword(password);
                staff.setNickname(nickname != null ? nickname : phone);
                staff.setRole(RoleConstants.PROPERTY_ADMIN);
                authService.createStaff(staff);
            } else {
                // 手机号冲突（同租户其他账号）
                if (!phone.equals(admin.getPhone())) {
                    Long cnt = userMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                            .eq(SysUser::getPhone, phone)
                            .eq(SysUser::getTenantId, tenantId)
                            .ne(SysUser::getId, admin.getId()));
                    if (cnt != null && cnt > 0) {
                        throw new BizException(ErrorCode.PHONE_EXISTS);
                    }
                    admin.setPhone(phone);
                }
                if (nickname != null) {
                    admin.setNickname(nickname);
                }
                if (StringUtils.hasText(password)) {
                    if (password.length() < 6) {
                        throw new BizException(ErrorCode.BAD_REQUEST, "管理端密码至少 6 位");
                    }
                    admin.setPassword(DigestUtil.md5Hex(password));
                }
                admin.setUpdateTime(LocalDateTime.now());
                userMapper.updateById(admin);
            }
            operLogService.write("tenant", "upsertAdmin",
                    String.format("租户 %d 管理端账号 -> %s", tenantId, phone));
            return getAdminAccount(tenantId);
        } finally {
            TenantContext.setSkipTenant(true);
        }
    }

    private SysUser findPropertyAdmin(Long tenantId) {
        return userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getTenantId, tenantId)
                .eq(SysUser::getRole, RoleConstants.PROPERTY_ADMIN)
                .orderByAsc(SysUser::getId)
                .last("limit 1"));
    }

    public List<SysTenant> listPublic() {
        TenantContext.setSkipTenant(true);
        try {
            // 登录可选社区：排除停用；降级/冻结仍可登录以便续费/运维
            return tenantMapper.selectList(new LambdaQueryWrapper<SysTenant>()
                    .ne(SysTenant::getStatus, "disabled")
                    .select(SysTenant::getId, SysTenant::getName, SysTenant::getAddress, SysTenant::getStatus)
                    .orderByAsc(SysTenant::getId));
        } finally {
            TenantContext.setSkipTenant(false);
        }
    }

    /**
     * 登录页：根据账号自动识别所属社区（不返回密码等敏感信息）。
     * portal=property → 物业管理员/网格员；portal=merchant → 商户。
     */
    public List<Map<String, Object>> resolveCommunitiesByPhone(String phone, String portal) {
        if (!StringUtils.hasText(phone)) {
            return Collections.emptyList();
        }
        String p = phone.trim();
        String portalKey = StringUtils.hasText(portal) ? portal.trim().toLowerCase() : "property";
        TenantContext.setSkipTenant(true);
        try {
            List<SysUser> users = userMapper.selectList(new LambdaQueryWrapper<SysUser>()
                    .eq(SysUser::getPhone, p)
                    .eq(SysUser::getStatus, 1));
            if (users == null || users.isEmpty()) {
                return Collections.emptyList();
            }
            List<Map<String, Object>> out = new ArrayList<>();
            for (SysUser u : users) {
                String role = u.getRole();
                boolean match = "merchant".equals(portalKey)
                        ? RoleConstants.isStoreMerchant(role)
                        : RoleConstants.isTenantStaff(role);
                if (!match || u.getTenantId() == null) {
                    continue;
                }
                SysTenant t = tenantMapper.selectById(u.getTenantId());
                if (t == null || "disabled".equals(t.getStatus())) {
                    continue;
                }
                Map<String, Object> row = new HashMap<>();
                row.put("id", t.getId());
                row.put("name", t.getName());
                row.put("address", t.getAddress());
                row.put("status", t.getStatus());
                row.put("role", role);
                row.put("packageId", t.getPackageId());
                row.put("packageName", resolvePackageName(t.getPackageId()));
                try {
                    if (t.getPackageId() != null) {
                        SysPackage pkg = packageService.getById(t.getPackageId());
                        if (pkg != null) {
                            row.put("packageCode", pkg.getCode());
                        }
                    }
                } catch (Exception ignored) {
                }
                out.add(row);
            }
            return out;
        } finally {
            TenantContext.setSkipTenant(false);
        }
    }

    /** 已登录：按当前手机号列出可切换社区 */
    public List<Map<String, Object>> listMyCommunities(String portal) {
        StpUtil.checkLogin();
        Object sessionUser = StpUtil.getSession().get(AuthConstants.SESSION_LOGIN_USER);
        if (!(sessionUser instanceof LoginUser lu) || !StringUtils.hasText(lu.getPhone())) {
            return Collections.emptyList();
        }
        return resolveCommunitiesByPhone(lu.getPhone(), portal);
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
            row.put("packageName", resolvePackageName(t.getPackageId()));
            row.put("expireTime", t.getExpireTime());
            row.put("daysLeft", t.getExpireTime() == null ? null
                    : ChronoUnit.DAYS.between(LocalDateTime.now(), t.getExpireTime()));
            rows.add(row);
        }
        return rows;
    }

    private String resolvePackageName(Long packageId) {
        if (packageId == null) {
            return null;
        }
        try {
            return packageService.getById(packageId).getName();
        } catch (Exception e) {
            return null;
        }
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
            vo.setExpireTime(t.getExpireTime());
            vo.setAiQuota(t.getAiQuota());
            vo.setTodayAiCalls(countTodayAiCalls(tenantId));
            List<String> tenantFeatures = splitFeatures(t.getFeatures());
            if (t.getPackageId() != null) {
                try {
                    SysPackage pkg = packageService.getById(t.getPackageId());
                    vo.setPackageName(pkg.getName());
                    vo.setPackageCode(pkg.getCode());
                    vo.setPackageSortNo(pkg.getSortNo());
                    List<String> pkgFeatures = packageService.featureCodes(pkg.getId());
                    vo.setPackageFeatures(pkgFeatures == null ? List.of() : pkgFeatures);
                    // 与运营端一致：租户未单独授权时回退套餐默认功能
                    if (tenantFeatures.isEmpty() && pkgFeatures != null && !pkgFeatures.isEmpty()) {
                        tenantFeatures = pkgFeatures;
                    }
                    if (vo.getAiQuota() == null && pkg.getAiQuota() != null) {
                        vo.setAiQuota(pkg.getAiQuota());
                    }
                } catch (Exception ignored) {
                    // ignore
                }
            }
            vo.setFeatures(tenantFeatures);
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

    private Long countTodayAiCalls(Long tenantId) {
        if (tenantId == null) {
            return 0L;
        }
        try {
            Long n = jdbcTemplate.queryForObject(
                    "select count(1) from ai_call_log where tenant_id=? and date(create_time)=curdate()",
                    Long.class, tenantId);
            return n == null ? 0L : n;
        } catch (Exception e) {
            return 0L;
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
