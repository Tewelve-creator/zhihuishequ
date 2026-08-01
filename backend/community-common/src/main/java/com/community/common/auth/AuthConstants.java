package com.community.common.auth;

/**
 * Sa-Token Session / Header 约定常量
 */
public final class AuthConstants {
    private AuthConstants() {
    }

    public static final String SESSION_LOGIN_USER = "loginUser";
    public static final String TOKEN_HEADER = "Authorization";
    public static final String TOKEN_PREFIX = "Bearer ";

    public static final String H_TENANT = "X-Tenant-Id";
    public static final String H_USER = "X-User-Id";
    public static final String H_ROLE = "X-User-Role";
    public static final String H_SKIP = "X-Skip-Tenant";
    public static final String H_CLIENT = "X-Client-Type";
    public static final String H_FEATURES = "X-Tenant-Features";
    public static final String H_TENANT_STATUS = "X-Tenant-Status";
    /** 居民当前选中房屋 */
    public static final String H_HOUSE = "X-House-Id";
    /** 商超店铺 ID（商户端隔离） */
    public static final String H_SHOP = "X-Shop-Id";
}
