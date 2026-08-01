package com.community.auth.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaCheckRole;
import com.community.auth.dto.LoginRequest;
import com.community.auth.dto.RegisterRequest;
import com.community.auth.dto.RoleMenusRequest;
import com.community.auth.dto.SmsSendRequest;
import com.community.auth.entity.SysRole;
import com.community.auth.entity.SysUser;
import com.community.auth.service.AuthService;
import com.community.auth.service.LoginLogService;
import com.community.auth.service.MessageService;
import com.community.auth.service.SmsService;
import com.community.common.constant.RoleConstants;
import com.community.common.result.PageResult;
import com.community.common.result.R;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final SmsService smsService;
    private final MessageService messageService;
    private final LoginLogService loginLogService;

    @PostMapping("/login")
    public R<Map<String, Object>> login(@Valid @RequestBody LoginRequest req) {
        return R.ok(authService.login(req));
    }

    @PostMapping("/logout")
    @SaCheckLogin
    public R<Void> logout() {
        authService.logout();
        return R.ok();
    }

    /** 同账号切换社区，刷新租户套餐与有效功能码 */
    @PostMapping("/switch-community")
    @SaCheckLogin
    public R<Map<String, Object>> switchCommunity(@RequestBody Map<String, Object> body) {
        Object tid = body == null ? null : body.get("tenantId");
        if (tid == null) {
            return R.fail(com.community.common.exception.ErrorCode.TENANT_REQUIRED);
        }
        Long tenantId = Long.valueOf(String.valueOf(tid));
        return R.ok(authService.switchCommunity(tenantId));
    }

    @PostMapping("/register")
    public R<Map<String, Object>> register(@Valid @RequestBody RegisterRequest req) {
        return R.ok(authService.register(req));
    }

    /** 发送短信验证码（演示返回码，生产应只返回成功） */
    @PostMapping("/sms/send")
    public R<Map<String, String>> sendSms(@Valid @RequestBody SmsSendRequest req) {
        String code = smsService.sendCode(req.getPhone());
        Map<String, String> map = new HashMap<>();
        map.put("phone", req.getPhone());
        map.put("code", code);
        map.put("hint", "演示环境返回验证码，正式环境请删除 code 字段");
        return R.ok(map);
    }

    /** 兼容旧接口：GET /auth/sms?phone= */
    @GetMapping("/sms")
    public R<String> sms(@RequestParam String phone) {
        return R.ok(smsService.sendCode(phone));
    }

    @GetMapping("/profile")
    @SaCheckLogin
    public R<Map<String, Object>> profile() {
        return R.ok(authService.profile());
    }

    /** 修改昵称 / 头像：body { nickname?, avatar? } */
    @PutMapping("/profile")
    @SaCheckLogin
    public R<Map<String, Object>> updateProfile(@RequestBody Map<String, String> body) {
        return R.ok(authService.updateProfile(body));
    }

    /** 兼容部分网关/客户端不支持 PUT 的情况 */
    @PostMapping("/profile/update")
    @SaCheckLogin
    public R<Map<String, Object>> updateProfilePost(@RequestBody Map<String, String> body) {
        return R.ok(authService.updateProfile(body));
    }

    @PutMapping("/password")
    @SaCheckLogin
    public R<Void> changePassword(@RequestBody Map<String, String> body) {
        authService.changePassword(body.get("oldPassword"), body.get("newPassword"));
        return R.ok();
    }

    @GetMapping("/users")
    @SaCheckPermission("auth:user:list")
    public R<PageResult<Map<String, Object>>> users(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) Boolean staffOnly) {
        return R.ok(authService.pageUsers(page, size, role, staffOnly));
    }

    @PostMapping("/users")
    @SaCheckPermission("auth:user:add")
    public R<SysUser> createUser(@RequestBody SysUser body) {
        if (RoleConstants.PLATFORM_ADMIN.equals(body.getRole())) {
            return R.ok(authService.createPlatformAdmin(body));
        }
        return R.ok(authService.createStaff(body));
    }

    @PutMapping("/users/{id}/status")
    @SaCheckPermission("auth:user:status")
    public R<Void> status(@PathVariable Long id, @RequestParam Integer status) {
        authService.updateUserStatus(id, status);
        return R.ok();
    }

    /** 重置物业工作人员密码为默认 123456 */
    @PutMapping("/users/{id}/password/reset")
    @SaCheckPermission("auth:user:add")
    public R<Void> resetPassword(@PathVariable Long id) {
        authService.resetStaffPassword(id);
        return R.ok();
    }

    /** 删除物业工作人员账号 */
    @DeleteMapping("/users/{id}")
    @SaCheckPermission("auth:user:status")
    public R<Void> deleteUser(@PathVariable Long id) {
        authService.deleteStaffUser(id);
        return R.ok();
    }

    @GetMapping("/roles")
    @SaCheckLogin
    public R<List<SysRole>> roles() {
        return R.ok(authService.listRoles());
    }

    @GetMapping("/roles/{id}/menus")
    @SaCheckRole(RoleConstants.PLATFORM_ADMIN)
    public R<List<Long>> roleMenus(@PathVariable Long id) {
        return R.ok(authService.listRoleMenuIds(id));
    }

    @PutMapping("/roles/{id}/menus")
    @SaCheckRole(RoleConstants.PLATFORM_ADMIN)
    public R<Void> saveRoleMenus(@PathVariable Long id, @RequestBody RoleMenusRequest body) {
        authService.saveRoleMenus(id, body == null ? null : body.getMenuIds());
        return R.ok();
    }

    @GetMapping("/menus/tree")
    @SaCheckLogin
    public R<List<Map<String, Object>>> menuTree(@RequestParam(required = false) String clientType) {
        return R.ok(authService.menuTree(clientType));
    }

    /** 全量菜单树（角色权限配置） */
    @GetMapping("/menus/all")
    @SaCheckRole(RoleConstants.PLATFORM_ADMIN)
    public R<List<Map<String, Object>>> allMenus(@RequestParam(required = false) String clientType) {
        return R.ok(authService.allMenuTree(clientType));
    }

    @GetMapping("/permissions")
    @SaCheckLogin
    public R<List<String>> permissions() {
        return R.ok(authService.myPermissions());
    }

    @GetMapping("/messages")
    @SaCheckLogin
    public R<List<Map<String, Object>>> messages() {
        return R.ok(messageService.myMessages());
    }

    @GetMapping("/messages/unread-count")
    @SaCheckLogin
    public R<Map<String, Object>> unreadCount() {
        Map<String, Object> m = new HashMap<>();
        m.put("count", messageService.unreadCount());
        return R.ok(m);
    }

    @PutMapping("/messages/{id}/read")
    @SaCheckLogin
    public R<Void> readMessage(@PathVariable Long id) {
        messageService.markRead(id);
        return R.ok();
    }

    @PutMapping("/messages/read-all")
    @SaCheckLogin
    public R<Map<String, Object>> readAllMessages() {
        Map<String, Object> m = new HashMap<>();
        m.put("count", messageService.markAllRead());
        return R.ok(m);
    }

    @GetMapping("/login-logs")
    @SaCheckLogin
    public R<PageResult<Map<String, Object>>> loginLogs(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size) {
        return R.ok(loginLogService.page(page, size));
    }

    @GetMapping("/sessions")
    @SaCheckPermission("auth:session:list")
    public R<List<Map<String, Object>>> sessions() {
        return R.ok(authService.listSessions());
    }

    @DeleteMapping("/sessions/{token}")
    @SaCheckPermission("auth:session:list")
    public R<Void> kick(@PathVariable String token) {
        authService.kickSession(token);
        return R.ok();
    }
}
