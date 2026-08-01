package com.community.common.tenant;

import com.community.common.auth.AuthConstants;
import com.community.common.constant.RoleConstants;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 从网关透传头恢复租户上下文
 */
public class TenantInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String tenant = request.getHeader(AuthConstants.H_TENANT);
        String user = request.getHeader(AuthConstants.H_USER);
        String house = request.getHeader(AuthConstants.H_HOUSE);
        String shop = request.getHeader(AuthConstants.H_SHOP);
        String role = request.getHeader(AuthConstants.H_ROLE);
        String skip = request.getHeader(AuthConstants.H_SKIP);
        String client = request.getHeader(AuthConstants.H_CLIENT);
        String features = request.getHeader(AuthConstants.H_FEATURES);
        String tenantStatus = request.getHeader(AuthConstants.H_TENANT_STATUS);

        if (tenant != null && !tenant.isBlank()) {
            TenantContext.setTenantId(Long.parseLong(tenant));
        }
        if (user != null && !user.isBlank()) {
            TenantContext.setUserId(Long.parseLong(user));
        }
        if (house != null && !house.isBlank()) {
            TenantContext.setHouseId(Long.parseLong(house));
        }
        if (shop != null && !shop.isBlank()) {
            TenantContext.setShopId(Long.parseLong(shop));
        }
        if (role != null && !role.isBlank()) {
            TenantContext.setRole(role);
        }
        if (client != null && !client.isBlank()) {
            TenantContext.setClientType(client);
        }
        if (features != null) {
            TenantContext.setFeatures(features);
        }
        if (tenantStatus != null && !tenantStatus.isBlank()) {
            TenantContext.setTenantStatus(tenantStatus);
        }
        if ("1".equals(skip) || RoleConstants.isPlatformAdmin(role)) {
            TenantContext.setSkipTenant(true);
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        TenantContext.clear();
    }
}
