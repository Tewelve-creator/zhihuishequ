package com.community.auth.entity;

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
    private String orderNo;
    private Long tenantId;
    private Long packageId;
    private String packageName;
    /** month / quarter / year */
    private String cycle;
    private BigDecimal amount;
    /** new / renew / upgrade */
    private String orderType;
    /** pending / paid / cancelled */
    private String status;
    private LocalDateTime payTime;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Long createBy;
    private LocalDateTime createTime;
}
