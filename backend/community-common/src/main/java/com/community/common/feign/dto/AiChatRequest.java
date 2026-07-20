package com.community.common.feign.dto;

import lombok.Data;

@Data
public class AiChatRequest {
    private Long tenantId;
    private Long userId;
    private String question;
}
