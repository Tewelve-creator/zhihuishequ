package com.community.auth.service;

import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.SaLoginModel;
import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.crypto.digest.DigestUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.community.auth.dto.LoginRequest;
import com.community.auth.dto.RegisterRequest;
import com.community.auth.entity.SysPackage;
import com.community.auth.entity.SysRole;
import com.community.auth.entity.SysRoleMenu;
import com.community.auth.entity.SysTenant;
import com.community.auth.entity.SysUser;
import com.community.auth.entity.SysUserRole;
import com.community.auth.mapper.SysMenuMapper;
import com.community.auth.mapper.SysRoleMapper;
import com.community.auth.mapper.SysRoleMenuMapper;
import com.community.auth.mapper.SysTenantMapper;
import com.community.auth.mapper.SysUserMapper;
import com.community.auth.mapper.SysUserRoleMapper;
import com.community.common.auth.AuthConstants;
import com.community.common.auth.ClientType;
import com.community.common.auth.LoginUser;
import com.community.common.constant.RoleConstants;
import com.community.common.desensitize.DesensitizeUtil;
import com.community.common.exception.BizException;
import com.community.common.exception.ErrorCode;
import com.community.common.result.PageResult;
import com.community.common.tenant.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import jakarta.annotation.PostConstruct;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysRoleMenuMapper roleMenuMapper;
    private final SysMenuMapper menuMapper;
    private final SysTenantMapper tenantMapper;
    private final SmsService smsService;
    private final LoginLogService loginLogService;
    private final LeaseCacheService leaseCacheService;
    private final PackageService packageService;
    private final HouseService houseService;
    private final OperLogService operLogService;
    private final JdbcTemplate jdbcTemplate;

    private static final String DEFAULT_FEATURES =
            "basic_repair,basic_notice,ai_chat,ai_image_repair,ai_emotion,ai_lost_match,data_dashboard,data_export";

    @PostConstruct
    public void ensureMallShopSchema() {
        try {
            jdbcTemplate.execute("""
                    create table if not exists store_shop (
                      id bigint primary key auto_increment,
                      tenant_id bigint not null,
                      name varchar(128) not null,
                      contact_name varchar(64) null,
                      contact_phone varchar(32) null,
                      address varchar(255) null,
                      open_time varchar(16) default '08:00',
                      close_time varchar(16) default '22:00',
                      status tinyint not null default 1,
                      owner_user_id bigint null,
                      login_phone varchar(32) null,
                      remark varchar(255) null,
                      deleted tinyint not null default 0,
                      create_time datetime null,
                      update_time datetime null,
                      key idx_ss_tenant (tenant_id, status),
                      key idx_ss_owner (owner_user_id)
                    ) engine=InnoDB default charset=utf8mb4
                    """);
            Integer roleCnt = jdbcTemplate.queryForObject(
                    "select count(1) from sys_role where role_code=?", Integer.class, RoleConstants.STORE_MERCHANT);
            if (roleCnt == null || roleCnt == 0) {
                jdbcTemplate.update(
                        "insert into sys_role(role_name,role_code,remark) values('商超商户',?,'社区商超独立商户后台')",
                        RoleConstants.STORE_MERCHANT);
            }
        } catch (Exception ignored) {
            // schema may not be ready yet
        }
    }

    public Map<String, Object> login(LoginRequest req) {
        String clientType = ClientType.normalize(req.getClientType());
        String loginType = StringUtils.hasText(req.getLoginType())
                ? req.getLoginType().trim().toLowerCase(Locale.ROOT) : "password";
        SysUser user = null;
        try {
            user = findUserForLogin(req.getPhone(), req.getTenantId());
            if (user == null) {
                throw new BizException(ErrorCode.USER_NOT_FOUND);
            }
            if ("sms".equals(loginType)) {
                smsService.verify(req.getPhone(), req.getSmsCode());
            } else {
                if (!StringUtils.hasText(req.getPassword())) {
                    throw new BizException(ErrorCode.BAD_REQUEST, "密码不能为空");
                }
                String pwd = DigestUtil.md5Hex(req.getPassword());
                if (!pwd.equals(user.getPassword())) {
                    throw new BizException(ErrorCode.BAD_CREDENTIALS);
                }
            }
            if (user.getStatus() != null && user.getStatus() == 0) {
                throw new BizException(ErrorCode.USER_DISABLED);
            }
            if (ClientType.ADMIN.equals(clientType) && RoleConstants.RESIDENT.equals(user.getRole())) {
                throw new BizException(ErrorCode.FORBIDDEN, "居民请使用 App 登录");
            }
            assertTenantAllowLogin(user);
            Long shopId = resolveShopIdForMerchant(user);
            if (RoleConstants.isStoreMerchant(user.getRole())) {
                if (shopId == null) {
                    throw new BizException(ErrorCode.FORBIDDEN, "商户店铺未绑定，请联系物业");
                }
                assertShopEnabled(shopId);
            }

            LoginUser loginUser = buildLoginUser(user, clientType);
            loginUser.setShopId(shopId);
            StpUtil.login(user.getId(), new SaLoginModel().setDevice(clientType));
            StpUtil.getSession().set(AuthConstants.SESSION_LOGIN_USER, loginUser);
            loginUser.fillTenantContext();

            loginLogService.record(user.getTenantId(), user.getId(), user.getPhone(), clientType,
                    loginType, clientIp(), true, "OK");

            Map<String, Object> resp = buildAuthPayload(loginUser, user, shopId, clientType, true);
            houseService.fillLoginHouses(user.getId(), user.getTenantId(), resp);
            return resp;
        } catch (BizException ex) {
            loginLogService.record(user == null ? req.getTenantId() : user.getTenantId(),
                    user == null ? null : user.getId(),
                    req.getPhone(), clientType, loginType, clientIp(), false, ex.getMessage());
            throw ex;
        }
    }

    public void logout() {
        if (StpUtil.isLogin()) {
            StpUtil.logout();
        }
        TenantContext.clear();
    }

    public Map<String, Object> register(RegisterRequest req) {
        TenantContext.setSkipTenant(true);
        try {
            Long cnt = userMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                    .eq(SysUser::getPhone, req.getPhone())
                    .eq(SysUser::getTenantId, req.getTenantId()));
            if (cnt != null && cnt > 0) {
                throw new BizException(ErrorCode.PHONE_EXISTS);
            }
            SysUser user = new SysUser();
            user.setTenantId(req.getTenantId());
            user.setPhone(req.getPhone());
            user.setPassword(DigestUtil.md5Hex(req.getPassword()));
            user.setNickname(StringUtils.hasText(req.getNickname())
                    ? req.getNickname()
                    : "居民" + req.getPhone().substring(Math.max(0, req.getPhone().length() - 4)));
            user.setRole(RoleConstants.RESIDENT);
            user.setRoomNo(req.getRoomNo());
            user.setStatus(1);
            user.setCreateTime(LocalDateTime.now());
            userMapper.insert(user);
            bindRole(user.getId(), RoleConstants.RESIDENT);

            LoginRequest loginReq = new LoginRequest();
            loginReq.setPhone(req.getPhone());
            loginReq.setPassword(req.getPassword());
            loginReq.setTenantId(req.getTenantId());
            loginReq.setClientType(ClientType.APP);
            loginReq.setLoginType("password");
            return login(loginReq);
        } finally {
            TenantContext.setSkipTenant(false);
        }
    }

    public Map<String, Object> profile() {
        LoginUser lu = requireLoginUser();
        // 以库为准，避免改资料后 Session 未刷新时看不到头像
        SysUser user = userMapper.selectById(lu.getUserId());
        Map<String, Object> map = new HashMap<>();
        map.put("id", lu.getUserId());
        map.put("phone", DesensitizeUtil.phone(lu.getPhone()));
        map.put("nickname", user != null && StringUtils.hasText(user.getNickname()) ? user.getNickname() : lu.getNickname());
        map.put("avatar", user != null ? user.getAvatar() : lu.getAvatar());
        map.put("role", lu.getRole());
        map.put("tenantId", lu.getTenantId());
        map.put("clientType", lu.getClientType());
        map.put("permissions", lu.getPermissions());
        map.put("roles", lu.getRoleCodes());
        map.put("features", lu.getFeatures());
        map.put("effectiveFeatureCodes", splitFeatureList(lu.getFeatures()));
        map.put("packageCode", lu.getPackageCode());
        map.put("packageName", lu.getPackageName());
        map.put("tenantStatus", lu.getTenantStatus());
        map.put("expireTime", lu.getExpireTime());
        map.put("tenantName", resolveTenantName(lu.getTenantId()));
        // 当前房屋身份与租约（租客「我的」页展示）
        try {
            Map<String, Object> houseResp = houseService.current();
            if (Boolean.TRUE.equals(houseResp.get("bound"))) {
                Object cur = houseResp.get("currentHouse");
                if (cur instanceof Map<?, ?> cm) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> house = (Map<String, Object>) cm;
                    map.put("currentHouse", house);
                    map.put("identityType", house.get("identityType"));
                    map.put("identityLabel", house.get("identityLabel"));
                    map.put("leaseStart", house.get("leaseStart"));
                    map.put("leaseEnd", house.get("leaseEnd"));
                    map.put("leaseStatus", house.get("leaseStatus"));
                    map.put("leaseStatusLabel", house.get("leaseStatusLabel"));
                }
            }
        } catch (Exception ignored) {
        }
        return map;
    }

    /** 修改昵称 / 头像 */
    public Map<String, Object> updateProfile(Map<String, String> body) {
        LoginUser lu = requireLoginUser();
        SysUser user = userMapper.selectById(lu.getUserId());
        if (user == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        boolean changed = false;
        if (body != null && body.containsKey("nickname")) {
            String nick = body.get("nickname");
            if (!StringUtils.hasText(nick)) {
                throw new BizException(ErrorCode.BAD_REQUEST, "昵称不能为空");
            }
            nick = nick.trim();
            if (nick.length() > 32) {
                throw new BizException(ErrorCode.BAD_REQUEST, "昵称最多 32 个字");
            }
            user.setNickname(nick);
            lu.setNickname(nick);
            changed = true;
        }
        if (body != null && body.containsKey("avatar")) {
            String avatar = body.get("avatar");
            if (StringUtils.hasText(avatar)) {
                avatar = normalizeAvatar(avatar.trim());
            } else {
                avatar = "";
            }
            user.setAvatar(avatar);
            lu.setAvatar(StringUtils.hasText(avatar) ? avatar : null);
            changed = true;
        }
        if (!changed) {
            throw new BizException(ErrorCode.BAD_REQUEST, "请提供昵称或头像");
        }
        user.setUpdateTime(java.time.LocalDateTime.now());
        userMapper.updateById(user);
        StpUtil.getSession().set(AuthConstants.SESSION_LOGIN_USER, lu);
        Map<String, Object> map = new HashMap<>();
        map.put("nickname", user.getNickname());
        map.put("avatar", user.getAvatar());
        return map;
    }

    private String normalizeAvatar(String url) {
        String u = url;
        while (u.startsWith("/api/")) {
            u = u.substring(4);
        }
        if (u.startsWith("http://") || u.startsWith("https://")) {
            try {
                java.net.URI uri = java.net.URI.create(u);
                if (uri.getPath() != null) {
                    u = uri.getPath();
                }
            } catch (Exception ignored) {
            }
        }
        if (!u.startsWith("/")) {
            u = "/" + u;
        }
        return u;
    }

    /** App 修改密码 */
    public void changePassword(String oldPassword, String newPassword) {
        LoginUser lu = requireLoginUser();
        if (!StringUtils.hasText(oldPassword) || !StringUtils.hasText(newPassword)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "请填写原密码和新密码");
        }
        if (newPassword.length() < 6) {
            throw new BizException(ErrorCode.BAD_REQUEST, "新密码至少 6 位");
        }
        SysUser user = userMapper.selectById(lu.getUserId());
        if (user == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        String oldMd5 = DigestUtil.md5Hex(oldPassword);
        if (!oldMd5.equals(user.getPassword())) {
            throw new BizException(ErrorCode.BAD_REQUEST, "原密码不正确");
        }
        user.setPassword(DigestUtil.md5Hex(newPassword));
        userMapper.updateById(user);
    }

    public PageResult<Map<String, Object>> pageUsers(long page, long size, String role, Boolean staffOnly) {
        assertStaffOrPlatform();
        LambdaQueryWrapper<SysUser> q = new LambdaQueryWrapper<SysUser>()
                .eq(StringUtils.hasText(role), SysUser::getRole, role)
                .orderByDesc(SysUser::getId);
        if (Boolean.TRUE.equals(staffOnly)) {
            q.in(SysUser::getRole, RoleConstants.PROPERTY_ADMIN, RoleConstants.GRID_WORKER);
        }
        Page<SysUser> p = userMapper.selectPage(new Page<>(page, size), q);
        boolean fullPhone = Boolean.TRUE.equals(staffOnly)
                || RoleConstants.PROPERTY_ADMIN.equals(role)
                || RoleConstants.GRID_WORKER.equals(role)
                || RoleConstants.PLATFORM_ADMIN.equals(role);
        List<Map<String, Object>> list = new ArrayList<>();
        for (SysUser u : p.getRecords()) {
            Map<String, Object> row = new HashMap<>();
            row.put("id", u.getId());
            row.put("tenantId", u.getTenantId());
            row.put("phone", fullPhone ? u.getPhone() : DesensitizeUtil.phone(u.getPhone()));
            row.put("nickname", u.getNickname());
            row.put("role", u.getRole());
            row.put("roomNo", u.getRoomNo());
            row.put("status", u.getStatus());
            row.put("createTime", u.getCreateTime());
            list.add(row);
        }
        return PageResult.of(p.getCurrent(), p.getSize(), p.getTotal(), list);
    }

    @Transactional(rollbackFor = Exception.class)
    public SysUser createStaff(SysUser body) {
        assertPropertyOrPlatform();
        if (!StringUtils.hasText(body.getPhone()) || !StringUtils.hasText(body.getRole())) {
            throw new BizException(ErrorCode.BAD_REQUEST, "手机号和角色必填");
        }
        if (!RoleConstants.PROPERTY_ADMIN.equals(body.getRole())
                && !RoleConstants.GRID_WORKER.equals(body.getRole())) {
            throw new BizException(ErrorCode.ROLE_INVALID, "仅可创建物业管理员或网格员");
        }
        Long tenantId = TenantContext.getTenantId();
        if (RoleConstants.isPlatformAdmin(TenantContext.getRole())) {
            tenantId = body.getTenantId();
        }
        if (tenantId == null) {
            throw new BizException(ErrorCode.TENANT_REQUIRED);
        }
        TenantContext.setSkipTenant(true);
        try {
            Long cnt = userMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                    .eq(SysUser::getPhone, body.getPhone())
                    .eq(SysUser::getTenantId, tenantId));
            if (cnt != null && cnt > 0) {
                throw new BizException(ErrorCode.PHONE_EXISTS);
            }
            SysUser user = new SysUser();
            user.setTenantId(tenantId);
            user.setPhone(body.getPhone());
            user.setPassword(DigestUtil.md5Hex(
                    StringUtils.hasText(body.getPassword()) ? body.getPassword() : "123456"));
            user.setNickname(StringUtils.hasText(body.getNickname()) ? body.getNickname() : body.getPhone());
            user.setRole(body.getRole());
            user.setStatus(1);
            user.setCreateTime(LocalDateTime.now());
            userMapper.insert(user);
            bindRole(user.getId(), body.getRole());
            user.setPassword(null);
            return user;
        } finally {
            TenantContext.setSkipTenant(RoleConstants.isPlatformAdmin(TenantContext.getRole()));
        }
    }

    /** 平台运营账号开通（tenant_id 可空） */
    @Transactional(rollbackFor = Exception.class)
    public SysUser createPlatformAdmin(SysUser body) {
        if (!RoleConstants.isPlatformAdmin(TenantContext.getRole())) {
            throw new BizException(ErrorCode.FORBIDDEN, "仅平台运营可创建运营账号");
        }
        if (!StringUtils.hasText(body.getPhone())) {
            throw new BizException(ErrorCode.BAD_REQUEST, "手机号必填");
        }
        TenantContext.setSkipTenant(true);
        try {
            Long cnt = userMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                    .eq(SysUser::getPhone, body.getPhone())
                    .eq(SysUser::getRole, RoleConstants.PLATFORM_ADMIN));
            if (cnt != null && cnt > 0) {
                throw new BizException(ErrorCode.PHONE_EXISTS);
            }
            SysUser user = new SysUser();
            user.setTenantId(null);
            user.setPhone(body.getPhone().trim());
            user.setPassword(DigestUtil.md5Hex(
                    StringUtils.hasText(body.getPassword()) ? body.getPassword() : "123456"));
            user.setNickname(StringUtils.hasText(body.getNickname()) ? body.getNickname() : body.getPhone());
            user.setRole(RoleConstants.PLATFORM_ADMIN);
            user.setStatus(1);
            user.setCreateTime(LocalDateTime.now());
            userMapper.insert(user);
            bindRole(user.getId(), RoleConstants.PLATFORM_ADMIN);
            user.setPassword(null);
            return user;
        } finally {
            TenantContext.setSkipTenant(true);
        }
    }

    public void updateUserStatus(Long id, Integer status) {
        assertPropertyOrPlatform();
        SysUser u = requireStaffInTenant(id);
        if (Objects.equals(id, TenantContext.getUserId()) && status != null && status == 0) {
            throw new BizException(ErrorCode.BAD_REQUEST, "不能停用当前登录账号");
        }
        u.setStatus(status);
        u.setUpdateTime(LocalDateTime.now());
        userMapper.updateById(u);
    }

    /** 重置工作人员密码为 123456 */
    public void resetStaffPassword(Long id) {
        assertPropertyOrPlatform();
        SysUser u = requireStaffInTenant(id);
        u.setPassword(DigestUtil.md5Hex("123456"));
        u.setUpdateTime(LocalDateTime.now());
        userMapper.updateById(u);
    }

    /** 删除工作人员账号（逻辑删除，释放手机号以便重新开通） */
    public void deleteStaffUser(Long id) {
        assertPropertyOrPlatform();
        SysUser u = requireStaffInTenant(id);
        if (Objects.equals(id, TenantContext.getUserId())) {
            throw new BizException(ErrorCode.BAD_REQUEST, "不能删除当前登录账号");
        }
        if (!RoleConstants.isPlatformAdmin(TenantContext.getRole())
                && !RoleConstants.isTenantStaff(u.getRole())) {
            throw new BizException(ErrorCode.BAD_REQUEST, "仅可删除物业工作人员账号");
        }
        String phone = u.getPhone() == null ? "user" : u.getPhone();
        // 释放 uk_phone_tenant，避免删除后无法用同一手机号重建
        u.setPhone(phone + "_d" + id);
        u.setStatus(0);
        u.setUpdateTime(LocalDateTime.now());
        userMapper.updateById(u);
        userMapper.deleteById(id);
        userRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, id));
    }

    private SysUser requireStaffInTenant(Long id) {
        SysUser u = userMapper.selectById(id);
        if (u == null) {
            throw new BizException(ErrorCode.USER_NOT_FOUND);
        }
        if (!RoleConstants.isPlatformAdmin(TenantContext.getRole())) {
            if (!Objects.equals(u.getTenantId(), TenantContext.getTenantId())) {
                throw new BizException(ErrorCode.FORBIDDEN, "不能操作其他社区账号");
            }
            if (!RoleConstants.isTenantStaff(u.getRole())) {
                throw new BizException(ErrorCode.BAD_REQUEST, "仅可操作物业工作人员账号");
            }
        }
        return u;
    }

    public List<SysRole> listRoles() {
        StpUtil.checkLogin();
        return roleMapper.selectList(new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getStatus, 1)
                .orderByAsc(SysRole::getId));
    }

    public List<Map<String, Object>> menuTree(String clientType) {
        LoginUser lu = requireLoginUser();
        String ct = ClientType.normalize(clientType != null ? clientType : lu.getClientType());
        List<com.community.auth.entity.SysMenu> menus = menuMapper.selectMenusByUserId(lu.getUserId());
        List<com.community.auth.entity.SysMenu> filtered = menus.stream()
                .filter(m -> ct.equalsIgnoreCase(m.getClientType()))
                // 非运营：按套餐功能码隐藏无权限菜单
                .filter(m -> lu.isPlatformAdmin() || menuAllowedByFeature(lu, m))
                .toList();
        return buildMenuTree(filtered, 0L);
    }

    /** 全量菜单树（角色权限配置用，不按用户角色过滤） */
    public List<Map<String, Object>> allMenuTree(String clientType) {
        assertPlatformAdmin();
        String ct = ClientType.normalize(clientType != null ? clientType : ClientType.ADMIN);
        List<com.community.auth.entity.SysMenu> menus = menuMapper.selectList(
                new LambdaQueryWrapper<com.community.auth.entity.SysMenu>()
                        .eq(com.community.auth.entity.SysMenu::getStatus, 1)
                        .eq(com.community.auth.entity.SysMenu::getClientType, ct)
                        .orderByAsc(com.community.auth.entity.SysMenu::getSortNo)
                        .orderByAsc(com.community.auth.entity.SysMenu::getId));
        return buildMenuTree(menus, 0L);
    }

    public List<Long> listRoleMenuIds(Long roleId) {
        assertPlatformAdmin();
        if (roleId == null) {
            return List.of();
        }
        List<Long> ids = menuMapper.selectMenuIdsByRoleId(roleId);
        return ids == null ? List.of() : ids;
    }

    @Transactional(rollbackFor = Exception.class)
    public void saveRoleMenus(Long roleId, List<Long> menuIds) {
        assertPlatformAdmin();
        if (roleId == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "角色不能为空");
        }
        SysRole role = roleMapper.selectById(roleId);
        if (role == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "角色不存在");
        }
        roleMenuMapper.delete(new LambdaQueryWrapper<SysRoleMenu>().eq(SysRoleMenu::getRoleId, roleId));
        if (menuIds != null) {
            for (Long menuId : menuIds) {
                if (menuId == null) {
                    continue;
                }
                SysRoleMenu rm = new SysRoleMenu();
                rm.setRoleId(roleId);
                rm.setMenuId(menuId);
                roleMenuMapper.insert(rm);
            }
        }
        operLogService.write("角色权限", "绑定菜单",
                "roleId=" + roleId + ", menus=" + (menuIds == null ? 0 : menuIds.size()));
    }

    /** 菜单 path/perms → 功能码映射；无映射则默认可见 */
    private boolean menuAllowedByFeature(LoginUser lu, com.community.auth.entity.SysMenu m) {
        String need = resolveMenuFeature(m);
        if (need == null) {
            return true;
        }
        if ("frozen".equals(lu.getTenantStatus()) || "degraded".equals(lu.getTenantStatus())) {
            return "basic_repair".equals(need) || "basic_notice".equals(need);
        }
        return lu.hasFeature(need);
    }

    private String resolveMenuFeature(com.community.auth.entity.SysMenu m) {
        String path = m.getPath() == null ? "" : m.getPath().toLowerCase();
        String name = m.getMenuName() == null ? "" : m.getMenuName();
        String perms = m.getPerms() == null ? "" : m.getPerms().toLowerCase();
        if (path.contains("cockpit") || path.contains("screen") || path.contains("dashboard")
                || name.contains("大屏") || name.contains("驾驶舱") || perms.contains("stats:dashboard")) {
            return "data_dashboard";
        }
        if (path.contains("export") || name.contains("导出") || perms.contains("export")) {
            return "data_export";
        }
        if (path.contains("ai") || name.contains("AI") || perms.contains("ai:")) {
            return "ai_chat";
        }
        if (path.contains("repair") || name.contains("报修") || perms.contains("repair")) {
            return "basic_repair";
        }
        if (path.contains("notice") || name.contains("公告") || perms.contains("notice")) {
            return "basic_notice";
        }
        return null;
    }

    public List<String> myPermissions() {
        return requireLoginUser().getPermissions();
    }

    public List<Map<String, Object>> listSessions() {
        StpUtil.checkPermission("auth:session:list");
        LoginUser lu = requireLoginUser();
        if (lu.isPlatformAdmin()) {
            return listPlatformSessions();
        }
        return buildSessionRows(lu.getUserId(), StpUtil.getTokenValueListByLoginId(lu.getUserId()));
    }

    private List<Map<String, Object>> listPlatformSessions() {
        List<Map<String, Object>> list = new ArrayList<>();
        try {
            List<String> tokens = StpUtil.searchTokenValue("", 0, 300, true);
            if (tokens == null || tokens.isEmpty()) {
                return list;
            }
            String current = StpUtil.getTokenValue();
            for (String token : tokens) {
                if (!StringUtils.hasText(token)) {
                    continue;
                }
                Object loginId = StpUtil.getLoginIdByToken(token);
                if (loginId == null) {
                    continue;
                }
                Long userId;
                try {
                    userId = Long.valueOf(String.valueOf(loginId));
                } catch (Exception e) {
                    continue;
                }
                Map<String, Object> row = buildSessionRow(userId, token, current);
                if (row != null) {
                    list.add(row);
                }
            }
        } catch (Exception e) {
            // 回退：仅当前用户会话
            return buildSessionRows(luSafeId(), StpUtil.getTokenValueListByLoginId(luSafeId()));
        }
        return list;
    }

    private Long luSafeId() {
        try {
            return requireLoginUser().getUserId();
        } catch (Exception e) {
            return null;
        }
    }

    private List<Map<String, Object>> buildSessionRows(Long userId, List<String> tokens) {
        List<Map<String, Object>> list = new ArrayList<>();
        if (userId == null || tokens == null) {
            return list;
        }
        String current = StpUtil.getTokenValue();
        for (String token : tokens) {
            Map<String, Object> row = buildSessionRow(userId, token, current);
            if (row != null) {
                list.add(row);
            }
        }
        return list;
    }

    private Map<String, Object> buildSessionRow(Long userId, String token, String currentToken) {
        Map<String, Object> row = new HashMap<>();
        row.put("token", token);
        row.put("userId", userId);
        row.put("current", token != null && token.equals(currentToken));
        SysUser u = userMapper.selectById(userId);
        if (u != null) {
            row.put("nickname", u.getNickname());
            row.put("phone", u.getPhone());
            row.put("role", u.getRole());
        } else {
            row.put("nickname", null);
            row.put("phone", null);
            row.put("role", null);
        }
        try {
            SaSession session = StpUtil.getSessionByLoginId(userId, false);
            if (session != null) {
                long ts = session.getCreateTime();
                if (ts > 0) {
                    row.put("loginTime", java.time.Instant.ofEpochMilli(ts)
                            .atZone(java.time.ZoneId.of("Asia/Shanghai"))
                            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
                }
            }
        } catch (Exception ignored) {
            // ignore
        }
        return row;
    }

    public void kickSession(String token) {
        StpUtil.checkPermission("auth:session:list");
        StpUtil.logoutByTokenValue(token);
    }

    private void assertPlatformAdmin() {
        if (!RoleConstants.isPlatformAdmin(TenantContext.getRole())) {
            throw new BizException(ErrorCode.FORBIDDEN, "仅平台运营可操作");
        }
    }

    public LoginUser requireLoginUser() {
        StpUtil.checkLogin();
        SaSession session = StpUtil.getSession(false);
        if (session == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        Object obj = session.get(AuthConstants.SESSION_LOGIN_USER);
        LoginUser lu;
        if (obj instanceof LoginUser loginUser) {
            lu = loginUser;
            // 刷新租户套餐快照
            refreshTenantSnapshot(lu);
            session.set(AuthConstants.SESSION_LOGIN_USER, lu);
            // 同步 Redis，供网关即时拦截
            if (lu.getTenantId() != null) {
                TenantContext.setSkipTenant(true);
                try {
                    SysTenant t = tenantMapper.selectById(lu.getTenantId());
                    if (t != null) {
                        leaseCacheService.publish(t);
                    }
                } finally {
                    TenantContext.setSkipTenant(false);
                }
            }
        } else {
            Long userId = Long.parseLong(String.valueOf(StpUtil.getLoginId()));
            TenantContext.setSkipTenant(true);
            SysUser user = userMapper.selectById(userId);
            TenantContext.setSkipTenant(false);
            if (user == null) {
                throw new BizException(ErrorCode.UNAUTHORIZED);
            }
            lu = buildLoginUser(user, ClientType.APP);
            session.set(AuthConstants.SESSION_LOGIN_USER, lu);
        }
        lu.fillTenantContext();
        return lu;
    }

    private Long resolveShopIdForMerchant(SysUser user) {
        if (user == null || user.getId() == null || !RoleConstants.isStoreMerchant(user.getRole())) {
            return null;
        }
        try {
            return jdbcTemplate.queryForObject(
                    "select id from store_shop where owner_user_id=? and ifnull(deleted,0)=0 limit 1",
                    Long.class, user.getId());
        } catch (Exception e) {
            return null;
        }
    }

    private void assertShopEnabled(Long shopId) {
        try {
            Integer st = jdbcTemplate.queryForObject(
                    "select status from store_shop where id=?", Integer.class, shopId);
            if (st == null || st == 0) {
                throw new BizException(ErrorCode.USER_DISABLED, "商户已被物业停用，无法登录");
            }
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(ErrorCode.FORBIDDEN, "商户店铺不存在或已停用");
        }
    }

    private void assertTenantAllowLogin(SysUser user) {
        if (user.getTenantId() == null) {
            return;
        }
        TenantContext.setSkipTenant(true);
        try {
            SysTenant t = tenantMapper.selectById(user.getTenantId());
            if (t == null) {
                throw new BizException(ErrorCode.TENANT_NOT_FOUND);
            }
            if ("disabled".equals(t.getStatus())) {
                throw new BizException(ErrorCode.TENANT_DISABLED);
            }
        } finally {
            TenantContext.setSkipTenant(false);
        }
    }

    private SysUser findUserForLogin(String phone, Long tenantId) {
        TenantContext.setSkipTenant(true);
        try {
            if (tenantId != null) {
                SysUser u = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getPhone, phone)
                        .eq(SysUser::getTenantId, tenantId)
                        .last("limit 1"));
                if (u != null) {
                    return u;
                }
            }
            SysUser platform = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                    .eq(SysUser::getPhone, phone)
                    .eq(SysUser::getRole, RoleConstants.PLATFORM_ADMIN)
                    .last("limit 1"));
            if (platform != null) {
                return platform;
            }
            return userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                    .eq(SysUser::getPhone, phone)
                    .last("limit 1"));
        } finally {
            TenantContext.setSkipTenant(false);
        }
    }

    private LoginUser buildLoginUser(SysUser user, String clientType) {
        LoginUser lu = new LoginUser();
        lu.setUserId(user.getId());
        lu.setTenantId(user.getTenantId());
        lu.setPhone(user.getPhone());
        lu.setNickname(user.getNickname());
        lu.setAvatar(user.getAvatar());
        lu.setRole(user.getRole());
        lu.setClientType(clientType);
        List<String> roles = userRoleMapper.selectRoleCodesByUserId(user.getId());
        if (roles == null || roles.isEmpty()) {
            roles = List.of(user.getRole());
        }
        lu.setRoleCodes(roles);
        List<String> perms = menuMapper.selectPermsByUserId(user.getId());
        lu.setPermissions(perms == null ? new ArrayList<>() : perms);
        refreshTenantSnapshot(lu);
        return lu;
    }

    private void refreshTenantSnapshot(LoginUser lu) {
        if (lu.getTenantId() == null) {
            lu.setFeatures("");
            lu.setPackageCode("");
            lu.setPackageName("");
            lu.setTenantStatus("active");
            lu.setExpireTime(null);
            return;
        }
        TenantContext.setSkipTenant(true);
        try {
            SysTenant t = tenantMapper.selectById(lu.getTenantId());
            if (t == null) {
                return;
            }
            String packageFeats = "";
            if (t.getPackageId() != null) {
                try {
                    SysPackage pkg = packageService.getById(t.getPackageId());
                    if (pkg != null) {
                        lu.setPackageCode(pkg.getCode() == null ? "" : pkg.getCode());
                        lu.setPackageName(pkg.getName() == null ? "" : pkg.getName());
                        packageFeats = packageService.featureCsv(t.getPackageId());
                    }
                } catch (Exception ignored) {
                    // 套餐功能表未初始化时忽略
                }
            }
            // 有效功能：租户授权 features 优先，否则回退套餐默认
            String feats = StringUtils.hasText(t.getFeatures()) ? t.getFeatures() : packageFeats;
            if (!StringUtils.hasText(feats)) {
                feats = "basic_repair,basic_notice";
            }
            String status = t.getStatus() == null ? "active" : t.getStatus();
            if ("degraded".equals(status) || "frozen".equals(status)) {
                feats = "basic_repair,basic_notice";
            }
            lu.setFeatures(feats);
            lu.setTenantStatus(status);
            if (t.getExpireTime() != null) {
                lu.setExpireTime(t.getExpireTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            } else {
                lu.setExpireTime(null);
            }
            try {
                leaseCacheService.publish(t);
            } catch (Exception ignored) {
            }
        } catch (Exception e) {
            if (!StringUtils.hasText(lu.getFeatures())) {
                lu.setFeatures("basic_repair,basic_notice");
            }
            if (!StringUtils.hasText(lu.getTenantStatus())) {
                lu.setTenantStatus("active");
            }
        } finally {
            TenantContext.setSkipTenant(false);
        }
    }

    /**
     * 切换社区：同手机号下切换到另一租户账号，刷新 Token / 套餐 / 功能码。
     */
    public Map<String, Object> switchCommunity(Long tenantId) {
        LoginUser cur = requireLoginUser();
        if (tenantId == null) {
            throw new BizException(ErrorCode.TENANT_REQUIRED);
        }
        if (tenantId.equals(cur.getTenantId())) {
            SysUser self = userMapper.selectById(cur.getUserId());
            return buildAuthPayload(cur, self, cur.getShopId(), cur.getClientType(), true);
        }
        if (!StringUtils.hasText(cur.getPhone())) {
            throw new BizException(ErrorCode.FORBIDDEN, "无法切换社区");
        }
        TenantContext.setSkipTenant(true);
        SysUser target;
        try {
            target = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                    .eq(SysUser::getPhone, cur.getPhone())
                    .eq(SysUser::getTenantId, tenantId)
                    .eq(SysUser::getStatus, 1)
                    .last("limit 1"));
        } finally {
            TenantContext.setSkipTenant(false);
        }
        if (target == null) {
            throw new BizException(ErrorCode.FORBIDDEN, "当前账号无权进入该社区");
        }
        if (ClientType.ADMIN.equals(cur.getClientType()) && RoleConstants.RESIDENT.equals(target.getRole())) {
            throw new BizException(ErrorCode.FORBIDDEN, "居民请使用 App 登录");
        }
        assertTenantAllowLogin(target);
        Long shopId = resolveShopIdForMerchant(target);
        if (RoleConstants.isStoreMerchant(target.getRole())) {
            if (shopId == null) {
                throw new BizException(ErrorCode.FORBIDDEN, "商户店铺未绑定，请联系物业");
            }
            assertShopEnabled(shopId);
        }
        LoginUser loginUser = buildLoginUser(target, cur.getClientType());
        loginUser.setShopId(shopId);
        StpUtil.login(target.getId(), new SaLoginModel().setDevice(cur.getClientType()));
        StpUtil.getSession().set(AuthConstants.SESSION_LOGIN_USER, loginUser);
        loginUser.fillTenantContext();
        return buildAuthPayload(loginUser, target, shopId, cur.getClientType(), true);
    }

    private Map<String, Object> buildAuthPayload(
            LoginUser loginUser, SysUser user, Long shopId, String clientType, boolean withToken) {
        Map<String, Object> resp = new HashMap<>();
        if (withToken) {
            resp.put("token", StpUtil.getTokenValue());
            resp.put("tokenName", StpUtil.getTokenName());
        }
        resp.put("userId", loginUser.getUserId());
        resp.put("tenantId", loginUser.getTenantId());
        resp.put("shopId", shopId);
        resp.put("role", loginUser.getRole());
        resp.put("nickname", user != null ? user.getNickname() : loginUser.getNickname());
        resp.put("avatar", user != null ? user.getAvatar() : loginUser.getAvatar());
        resp.put("phone", loginUser.getPhone());
        resp.put("clientType", clientType);
        resp.put("permissions", loginUser.getPermissions());
        resp.put("roles", loginUser.getRoleCodes());
        resp.put("features", loginUser.getFeatures());
        resp.put("effectiveFeatureCodes", splitFeatureList(loginUser.getFeatures()));
        resp.put("packageCode", loginUser.getPackageCode());
        resp.put("packageName", loginUser.getPackageName());
        resp.put("tenantStatus", loginUser.getTenantStatus());
        resp.put("expireTime", loginUser.getExpireTime());
        resp.put("tenantName", resolveTenantName(loginUser.getTenantId()));
        return resp;
    }

    private List<String> splitFeatureList(String csv) {
        if (!StringUtils.hasText(csv)) {
            return List.of();
        }
        return Arrays.stream(csv.split(",")).map(String::trim).filter(StringUtils::hasText).toList();
    }

    private String resolveTenantName(Long tenantId) {
        if (tenantId == null) {
            return null;
        }
        TenantContext.setSkipTenant(true);
        try {
            SysTenant t = tenantMapper.selectById(tenantId);
            return t == null ? null : t.getName();
        } catch (Exception e) {
            return null;
        } finally {
            TenantContext.setSkipTenant(false);
        }
    }

    private void bindRole(Long userId, String roleCode) {
        SysRole role = roleMapper.selectOne(new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getRoleCode, roleCode)
                .last("limit 1"));
        if (role == null) {
            return;
        }
        SysUserRole ur = new SysUserRole();
        ur.setUserId(userId);
        ur.setRoleId(role.getId());
        userRoleMapper.insert(ur);
    }

    private List<Map<String, Object>> buildMenuTree(List<com.community.auth.entity.SysMenu> menus, Long parentId) {
        List<Map<String, Object>> tree = new ArrayList<>();
        for (com.community.auth.entity.SysMenu m : menus) {
            if (parentId.equals(m.getParentId() == null ? 0L : m.getParentId())) {
                Map<String, Object> node = new HashMap<>();
                node.put("id", m.getId());
                node.put("parentId", m.getParentId());
                node.put("menuName", m.getMenuName());
                node.put("title", m.getMenuName());
                node.put("menuType", m.getMenuType());
                node.put("path", m.getPath());
                node.put("component", m.getComponent());
                node.put("perms", m.getPerms());
                node.put("icon", m.getIcon());
                node.put("clientType", m.getClientType());
                node.put("children", buildMenuTree(menus, m.getId()));
                tree.add(node);
            }
        }
        return tree;
    }

    private String clientIp() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs == null) {
                return "";
            }
            HttpServletRequest request = attrs.getRequest();
            String xff = request.getHeader("X-Forwarded-For");
            if (StringUtils.hasText(xff)) {
                return xff.split(",")[0].trim();
            }
            return request.getRemoteAddr();
        } catch (Exception e) {
            return "";
        }
    }

    private void assertStaffOrPlatform() {
        StpUtil.checkLogin();
        String role = TenantContext.getRole();
        if (!RoleConstants.isPlatformAdmin(role) && !RoleConstants.isTenantStaff(role)) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
    }

    private void assertPropertyOrPlatform() {
        StpUtil.checkLogin();
        String role = TenantContext.getRole();
        if (!RoleConstants.isPlatformAdmin(role) && !RoleConstants.PROPERTY_ADMIN.equals(role)) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
    }
}
