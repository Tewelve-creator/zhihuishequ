package com.community.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TenantCreateRequest {
    @NotBlank(message = "租户名称不能为空")
    private String name;
    private String address;
    private String contactName;
    private String contactPhone;
    private Long packageId;
    private LocalDateTime expireTime;
    private String remark;
}
