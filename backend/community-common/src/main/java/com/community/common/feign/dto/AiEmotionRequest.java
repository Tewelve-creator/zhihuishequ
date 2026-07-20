package com.community.common.feign.dto;

import lombok.Data;

@Data
public class AiEmotionRequest {
    private Long tenantId;
    private Long userId;
    private String text;
}
