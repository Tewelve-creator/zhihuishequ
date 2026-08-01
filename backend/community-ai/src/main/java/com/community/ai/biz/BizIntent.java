package com.community.ai.biz;

/**
 * 首期可识别业务意图
 */
public enum BizIntent {
    VISIT_CREATE("访客预约登记", true),
    REPAIR_CREATE("发起报事报修", true),
    FEE_UNPAID("查询本人欠费账单", false),
    CAR_LIST("查询我的常驻车辆", false),
    VISIT_LIST("查询有效访客预约", false);

    private final String label;
    /** 是否写操作（写操作必须确认后才能落库） */
    private final boolean writeOp;

    BizIntent(String label, boolean writeOp) {
        this.label = label;
        this.writeOp = writeOp;
    }

    public String getLabel() {
        return label;
    }

    public boolean isWriteOp() {
        return writeOp;
    }
}
