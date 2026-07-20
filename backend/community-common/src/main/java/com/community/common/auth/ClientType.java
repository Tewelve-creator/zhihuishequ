package com.community.common.auth;

/**
 * 多端登录客户端类型
 */
public final class ClientType {
    private ClientType() {
    }

    public static final String APP = "APP";
    public static final String ADMIN = "ADMIN";

    public static String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            return APP;
        }
        String v = raw.trim().toUpperCase();
        if (ADMIN.equals(v) || "WEB".equals(v) || "PC".equals(v)) {
            return ADMIN;
        }
        return APP;
    }
}
