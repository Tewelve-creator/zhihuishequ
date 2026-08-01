package com.community.common.feign.dto;

import lombok.Data;

@Data
public class AiBizConfirmRequest {
    /** Redis 中待确认动作令牌 */
    private String actionToken;
    private Long tenantId;
    private Long userId;
}
