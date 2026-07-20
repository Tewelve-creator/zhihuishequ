package com.community.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class OrderCreateRequest {
    /** 运营代下单时可指定；物业侧取当前租户 */
    private Long tenantId;
    @NotNull(message = "套餐不能为空")
    private Long packageId;
    /** month / quarter / year */
    @NotBlank(message = "租赁周期不能为空")
    private String cycle;
    /** new / renew / upgrade */
    @NotBlank(message = "订单类型不能为空")
    private String orderType;
}
