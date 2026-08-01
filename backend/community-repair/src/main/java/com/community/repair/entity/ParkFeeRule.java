package com.community.repair.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 停车收费规则 */
@Data
@TableName("park_fee_rule")
public class ParkFeeRule {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String ruleName;
    private Integer freeMinutes;
    private BigDecimal firstHourFee;
    private Integer stepMinutes;
    private BigDecimal stepFee;
    private BigDecimal dayCap;
    private String nightStart;
    private String nightEnd;
    private BigDecimal nightDiscount;
    private BigDecimal monthlyFee;
    private BigDecimal yearlyFee;
    private Integer enabled;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
