package com.community.common.feign.dto;

import lombok.Data;

@Data
public class AiImageRepairRequest {
    private Long tenantId;
    private Long userId;
    private String imageUrl;
}
