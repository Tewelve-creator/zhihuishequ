package com.community.common.constant;

/**
 * 角色编码
 */
public final class RoleConstants {
    private RoleConstants() {
    }

    /** 居民 */
    public static final String RESIDENT = "resident";
    /** 物业管理员 */
    public static final String PROPERTY_ADMIN = "property_admin";
    /** 社区网格员 */
    public static final String GRID_WORKER = "grid_worker";
    /** SaaS 平台运营 */
    public static final String PLATFORM_ADMIN = "platform_admin";
    /** 社区商超商户（独立商户后台） */
    public static final String STORE_MERCHANT = "store_merchant";

    public static boolean isPlatformAdmin(String role) {
        return PLATFORM_ADMIN.equals(role);
    }

    public static boolean isTenantStaff(String role) {
        return PROPERTY_ADMIN.equals(role) || GRID_WORKER.equals(role);
    }

    public static boolean isPropertyAdmin(String role) {
        return PROPERTY_ADMIN.equals(role);
    }

    public static boolean isGridWorker(String role) {
        return GRID_WORKER.equals(role);
    }

    public static boolean isStoreMerchant(String role) {
        return STORE_MERCHANT.equals(role);
    }

    public static boolean isValid(String role) {
        return RESIDENT.equals(role)
                || PROPERTY_ADMIN.equals(role)
                || GRID_WORKER.equals(role)
                || PLATFORM_ADMIN.equals(role)
                || STORE_MERCHANT.equals(role);
    }
}
