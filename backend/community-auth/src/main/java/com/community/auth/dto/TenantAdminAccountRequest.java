package com.community.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 运营端为租户设置/重置物业管理端账号
 */
@Data
public class TenantAdminAccountRequest {
    /** 登录手机号（管理端账号） */
    @NotBlank(message = "管理端账号不能为空")
    private String adminPhone;
    /**
     * 密码：新建必填；已有账号时留空表示不改密码
     */
    private String adminPassword;
    private String adminNickname;
}
