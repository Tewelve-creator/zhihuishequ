package com.community.common.feign.dto;

import lombok.Data;

@Data
public class AiAuditRequest {
    private Long tenantId;
    private String bizType;
    private String content;
}
