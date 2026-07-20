package com.community.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 多端登录：password 或 smsCode 二选一
 */
@Data
public class LoginRequest {
    @NotBlank(message = "手机号不能为空")
    private String phone;
    /** 密码登录必填 */
    private String password;
    /** 验证码登录必填 */
    private String smsCode;
    /** password / sms，默认 password */
    private String loginType;
    private Long tenantId;
    /** APP / ADMIN，默认 APP */
    private String clientType;
}
