package com.community.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.community.auth.entity.SysLoginLog;
import com.community.auth.mapper.SysLoginLogMapper;
import com.community.common.constant.RoleConstants;
import com.community.common.desensitize.DesensitizeUtil;
import com.community.common.exception.BizException;
import com.community.common.exception.ErrorCode;
import com.community.common.result.PageResult;
import com.community.common.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class LoginLogService {

    private final SysLoginLogMapper loginLogMapper;

    public void record(Long tenantId, Long userId, String phone, String clientType,
                       String loginType, String ip, boolean success, String msg) {
        TenantContext.setSkipTenant(true);
        try {
            SysLoginLog log = new SysLoginLog();
            log.setTenantId(tenantId);
            log.setUserId(userId);
            log.setPhone(phone);
            log.setClientType(clientType);
            log.setLoginType(loginType);
            log.setIp(ip);
            log.setSuccess(success ? 1 : 0);
            log.setMsg(msg);
            log.setCreateTime(LocalDateTime.now());
            loginLogMapper.insert(log);
        } finally {
            // 保持调用方 skip 状态由外层控制
        }
    }

    public PageResult<Map<String, Object>> page(long page, long size) {
        boolean platform = RoleConstants.isPlatformAdmin(TenantContext.getRole());
        if (!platform && !RoleConstants.PROPERTY_ADMIN.equals(TenantContext.getRole())) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        TenantContext.setSkipTenant(true);
        try {
            LambdaQueryWrapper<SysLoginLog> q = new LambdaQueryWrapper<SysLoginLog>()
                    .orderByDesc(SysLoginLog::getId);
            if (!platform) {
                q.eq(SysLoginLog::getTenantId, TenantContext.getTenantId());
            }
            Page<SysLoginLog> p = loginLogMapper.selectPage(new Page<>(page, size), q);
            List<Map<String, Object>> rows = new ArrayList<>();
            for (SysLoginLog log : p.getRecords()) {
                Map<String, Object> row = new HashMap<>();
                row.put("id", log.getId());
                row.put("tenantId", log.getTenantId());
                row.put("userId", log.getUserId());
                row.put("phone", DesensitizeUtil.phone(log.getPhone()));
                row.put("clientType", log.getClientType());
                row.put("loginType", log.getLoginType());
                row.put("ip", log.getIp());
                row.put("success", log.getSuccess());
                row.put("msg", log.getMsg());
                row.put("createTime", log.getCreateTime());
                rows.add(row);
            }
            return PageResult.of(p.getCurrent(), p.getSize(), p.getTotal(), rows);
        } finally {
            TenantContext.setSkipTenant(platform);
        }
    }
}
