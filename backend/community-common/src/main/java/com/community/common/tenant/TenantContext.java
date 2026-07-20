package com.community.common.tenant;

/**
 * 请求级租户 / 用户上下文（由网关透传头或本服务登录填充）
 */
public final class TenantContext {
    private static final ThreadLocal<Long> TENANT_ID = new ThreadLocal<>();
    private static final ThreadLocal<Long> USER_ID = new ThreadLocal<>();
    private static final ThreadLocal<String> ROLE = new ThreadLocal<>();
    private static final ThreadLocal<String> CLIENT_TYPE = new ThreadLocal<>();
    private static final ThreadLocal<String> FEATURES = new ThreadLocal<>();
    private static final ThreadLocal<String> TENANT_STATUS = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> SKIP_TENANT = ThreadLocal.withInitial(() -> false);

    private TenantContext() {
    }

    public static void setTenantId(Long tenantId) {
        TENANT_ID.set(tenantId);
    }

    public static Long getTenantId() {
        return TENANT_ID.get();
    }

    public static void setUserId(Long userId) {
        USER_ID.set(userId);
    }

    public static Long getUserId() {
        return USER_ID.get();
    }

    public static void setRole(String role) {
        ROLE.set(role);
    }

    public static String getRole() {
        return ROLE.get();
    }

    public static void setClientType(String clientType) {
        CLIENT_TYPE.set(clientType);
    }

    public static String getClientType() {
        return CLIENT_TYPE.get();
    }

    public static void setFeatures(String features) {
        FEATURES.set(features);
    }

    public static String getFeatures() {
        return FEATURES.get();
    }

    public static void setTenantStatus(String status) {
        TENANT_STATUS.set(status);
    }

    public static String getTenantStatus() {
        return TENANT_STATUS.get();
    }

    public static void setSkipTenant(boolean skip) {
        SKIP_TENANT.set(skip);
    }

    public static boolean isSkipTenant() {
        return Boolean.TRUE.equals(SKIP_TENANT.get());
    }

    public static void clear() {
        TENANT_ID.remove();
        USER_ID.remove();
        ROLE.remove();
        CLIENT_TYPE.remove();
        FEATURES.remove();
        TENANT_STATUS.remove();
        SKIP_TENANT.remove();
    }
}
