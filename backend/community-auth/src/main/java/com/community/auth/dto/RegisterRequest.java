package com.community.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 居民注册
 */
@Data
public class RegisterRequest {
    @NotBlank(message = "手机号不能为空")
    private String phone;
    @NotBlank(message = "密码不能为空")
    private String password;
    private String nickname;
    @NotNull(message = "请选择所属社区")
    private Long tenantId;
    private String roomNo;
}
