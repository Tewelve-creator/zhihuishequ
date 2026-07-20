package com.community.common.auth;

import com.community.common.constant.RoleConstants;
import com.community.common.tenant.TenantContext;
import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 登录用户快照（存入 Sa-Token Session）
 */
@Data
public class LoginUser implements Serializable {
    private Long userId;
    private Long tenantId;
    private String phone;
    private String nickname;
    private String role;
    private String clientType;
    private List<String> roleCodes = new ArrayList<>();
    private List<String> permissions = new ArrayList<>();
    private String features = "";
    private String tenantStatus = "active";
    private String expireTime;

    public boolean isPlatformAdmin() {
        return RoleConstants.isPlatformAdmin(role);
    }

    public boolean hasFeature(String code) {
        if (code == null || code.isBlank()) {
            return true;
        }
        if (features == null || features.isBlank()) {
            return false;
        }
        return Arrays.asList(features.split(",")).contains(code);
    }

    public void fillTenantContext() {
        TenantContext.setUserId(userId);
        TenantContext.setTenantId(tenantId);
        TenantContext.setRole(role);
        TenantContext.setClientType(clientType);
        TenantContext.setSkipTenant(isPlatformAdmin());
        TenantContext.setFeatures(features);
        TenantContext.setTenantStatus(tenantStatus);
    }
}
