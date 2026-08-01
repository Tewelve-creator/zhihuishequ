package com.community.common.constant;

/**
 * 套餐功能权限码（网关到期降级据此拦截）
 */
public final class FeatureCodes {
    public static final String BASIC_REPAIR = "basic_repair";
    public static final String BASIC_NOTICE = "basic_notice";
    public static final String AI_CHAT = "ai_chat";
    public static final String AI_IMAGE_REPAIR = "ai_image_repair";
    public static final String AI_EMOTION = "ai_emotion";
    public static final String AI_LOST_MATCH = "ai_lost_match";
    public static final String DATA_DASHBOARD = "data_dashboard";
    public static final String DATA_EXPORT = "data_export";
    /** 社区商超 / 商户运营（旗舰版） */
    public static final String VENDOR_MANAGE = "vendor_manage";
    /**
     * @deprecated 不再作为套餐功能卖点。多社区切换依赖同账号多租户绑定，勿按套餐解锁。
     */
    @Deprecated
    public static final String MULTI_COMMUNITY = "multi_community";

    private FeatureCodes() {
    }
}
