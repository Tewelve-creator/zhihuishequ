package com.community.auth.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AiQuotaUpdateRequest {
    @NotNull(message = "aiQuota 不能为空")
    private Integer aiQuota;
}
