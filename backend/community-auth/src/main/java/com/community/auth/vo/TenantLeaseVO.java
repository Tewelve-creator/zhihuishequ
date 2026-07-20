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
}
