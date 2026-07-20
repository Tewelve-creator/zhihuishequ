package com.community.order.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("saas_order")
public class SaasOrder {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long packageId;
    private String packageName;
    private String cycle;
    private BigDecimal amount;
    private String payStatus;
    private LocalDateTime payTime;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String features;
    private LocalDateTime createTime;
}
