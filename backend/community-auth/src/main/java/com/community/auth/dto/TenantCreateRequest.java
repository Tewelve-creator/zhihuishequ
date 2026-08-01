package com.community.auth.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
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
    @JsonDeserialize(using = FlexibleLocalDateTimeDeserializer.class)
    private LocalDateTime expireTime;
    private String remark;

    /** 物业管理端登录手机号（账号） */
    private String adminPhone;
    /** 物业管理端登录密码（明文，后端 MD5 存储） */
    private String adminPassword;
    /** 管理员显示名，默认用联系人或手机号 */
    private String adminNickname;
}
