package com.community.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class OpsConfigSaveRequest {
    @NotBlank(message = "configKey 不能为空")
    private String configKey;
    private String configValue;
    private String remark;
}
