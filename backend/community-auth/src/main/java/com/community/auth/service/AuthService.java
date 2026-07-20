package com.community.auth.service;

import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.SaLoginModel;
import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.crypto.digest.DigestUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.community.auth.dto.LoginRequest;
import com.community.auth.dto.RegisterRequest;
import com.community.auth.entity.SysRole;
import com.community.auth.entity.SysTenant;
import com.community.auth.entity.SysUser;
import com.community.auth.entity.SysUserRole;
import com.community.auth.mapper.SysMenuMapper;
import com.community.auth.mapper.SysRoleMapper;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysMenuMapper menuMapper;
    private final SysTenantMapper tenantMapper;
    private final SmsService smsService;
    private final LoginLogService loginLogService;
    private final LeaseCacheService leaseCacheService;
    private final PackageService packageService;

    private static final String DEFAULT_FEATURES =
            "basic_repair,basic_notice,ai_chat,ai_image_repair,ai_emotion,ai_lost_match,data_dashboard,data_export";

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

            LoginUser loginUser = buildLoginUser(user, clientType);
            StpUtil.login(user.getId(), new SaLoginModel().setDevice(clientType));
            StpUtil.getSession().set(AuthConstants.SESSION_LOGIN_USER, loginUser);
            loginUser.fillTenantContext();

            loginLogService.record(user.getTenantId(), user.getId(), user.getPhone(), clientType,
                    loginType, clientIp(), true, "OK");

            Map<String, Object> resp = new HashMap<>();
            resp.put("token", StpUtil.getTokenValue());
            resp.put("tokenName", StpUtil.getTokenName());
            resp.put("userId", user.getId());
            resp.put("tenantId", user.getTenantId());
            resp.put("role", user.getRole());
            resp.put("nickname", user.getNickname());
            resp.put("clientType", clientType);
            resp.put("permissions", loginUser.getPermissions());
            resp.put("roles", loginUser.getRoleCodes());
            resp.put("features", loginUser.getFeatures());
            resp.put("tenantStatus", loginUser.getTenantStatus());
            resp.put("expireTime", loginUser.getExpireTime());
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
        Map<String, Object> map = new HashMap<>();
        map.put("id", lu.getUserId());
        map.put("phone", DesensitizeUtil.phone(lu.getPhone()));
        map.put("nickname", lu.getNickname());
        map.put("role", lu.getRole());
        map.put("tenantId", lu.getTenantId());
        map.put("clientType", lu.getClientType());
        map.put("permissions", lu.getPermissions());
        map.put("roles", lu.getRoleCodes());
        map.put("features", lu.getFeatures());
        map.put("tenantStatus", lu.getTenantStatus());
        map.put("expireTime", lu.getExpireTime());
        return map;
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
                || RoleConstants.GRID_WORKER.equals(role);
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
        List<String> tokens = StpUtil.getTokenValueListByLoginId(lu.getUserId());
        List<Map<String, Object>> list = new ArrayList<>();
        if (tokens == null) {
            return list;
        }
        String current = StpUtil.getTokenValue();
        for (String token : tokens) {
            Map<String, Object> row = new HashMap<>();
            row.put("token", token);
            row.put("userId", lu.getUserId());
            row.put("current", token != null && token.equals(current));
            list.add(row);
        }
        return list;
    }

    public void kickSession(String token) {
        StpUtil.checkPermission("auth:session:list");
        StpUtil.logoutByTokenValue(token);
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
            String feats = t.getFeatures();
            if (!StringUtils.hasText(feats) && t.getPackageId() != null) {
                try {
                    feats = packageService.featureCsv(t.getPackageId());
                } catch (Exception ignored) {
                    // 套餐功能表未初始化时忽略
                }
            }
            if (!StringUtils.hasText(feats)) {
                feats = DEFAULT_FEATURES;
            }
            lu.setFeatures(feats);
            lu.setTenantStatus(t.getStatus() == null ? "active" : t.getStatus());
            if (t.getExpireTime() != null) {
                lu.setExpireTime(t.getExpireTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            }
            try {
                leaseCacheService.publish(t);
            } catch (Exception ignored) {
            }
        } catch (Exception e) {
            // 缺列/查库失败时不阻断登录
            if (!StringUtils.hasText(lu.getFeatures())) {
                lu.setFeatures(DEFAULT_FEATURES);
            }
            if (!StringUtils.hasText(lu.getTenantStatus())) {
                lu.setTenantStatus("active");
            }
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
