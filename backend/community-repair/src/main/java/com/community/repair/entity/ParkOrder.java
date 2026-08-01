package com.community.repair.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 停车计费订单 */
@Data
@TableName("park_order")
public class ParkOrder {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String orderNo;
    private String plateNo;
    private Long vehicleId;
    private Long spaceId;
    private LocalDateTime enterTime;
    private LocalDateTime exitTime;
    private Integer freeMinutes;
    private Integer durationMinutes;
    private BigDecimal amount;
    private BigDecimal paidAmount;
    /** PARKING / UNPAID / PAID / CLOSED */
    private String status;
    private String payChannel;
    private LocalDateTime payTime;
    private String feeDetail;
    /** 1欠费需拦截 */
    private Integer arrearsFlag;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
