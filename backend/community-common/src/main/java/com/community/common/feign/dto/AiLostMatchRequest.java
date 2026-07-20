package com.community.common.feign.dto;

import lombok.Data;

@Data
public class AiLostMatchRequest {
    private Long tenantId;
    private String description;
    private String imageUrl;
}
