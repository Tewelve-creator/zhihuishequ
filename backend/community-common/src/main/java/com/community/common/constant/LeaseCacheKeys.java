package com.community.common.constant;

/**
 * 租户租赁快照 Redis Key（支付/到期后即时同步到网关）
 */
public final class LeaseCacheKeys {
    public static final String PREFIX = "saas:lease:";

    private LeaseCacheKeys() {
    }

    public static String of(Long tenantId) {
        return PREFIX + tenantId;
    }
}
