package com.community.auth.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class TenantLeaseVO {
    private Long tenantId;
    private String tenantName;
    private String status;
    private Long packageId;
    private String packageName;
    private String packageCode;
    private List<String> features;
    private LocalDateTime expireTime;
    private Long daysLeft;
    private Boolean expireWarning;
    private Integer aiQuota;
    /** 今日 AI 调用次数（用于估算剩余额度） */
    private Long todayAiCalls;
    /** 当前套餐排序号（与运营端升级校验一致） */
    private Integer packageSortNo;
    /** 套餐默认功能码（租户 features 为空时回退展示） */
    private List<String> packageFeatures;
}
