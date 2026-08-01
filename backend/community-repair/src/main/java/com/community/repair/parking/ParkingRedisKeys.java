package com.community.repair.parking;

/**
 * 停车 Redis Key 约定（租户隔离）
 * <pre>
 * park:white:{tenantId}           Set  白名单车牌
 * park:black:{tenantId}           Set  黑名单车牌
 * park:green:{tenantId}           Set  绿通车牌（消防/急救）
 * park:visitor:{tenantId}:{plate} String JSON 临时访客授权（TTL=授权剩余时长）
 * park:space:{tenantId}           Hash  spaceId -> FREE|OCCUPIED|...
 * park:arrears:{tenantId}         Set  欠费车牌（进场拦截）
 * </pre>
 */
public final class ParkingRedisKeys {

    private ParkingRedisKeys() {
    }

    public static String white(Long tenantId) {
        return "park:white:" + tenantId;
    }

    public static String black(Long tenantId) {
        return "park:black:" + tenantId;
    }

    public static String green(Long tenantId) {
        return "park:green:" + tenantId;
    }

    public static String visitor(Long tenantId, String plateNo) {
        return "park:visitor:" + tenantId + ":" + plateNo;
    }

    public static String spaceHash(Long tenantId) {
        return "park:space:" + tenantId;
    }

    public static String arrears(Long tenantId) {
        return "park:arrears:" + tenantId;
    }
}
