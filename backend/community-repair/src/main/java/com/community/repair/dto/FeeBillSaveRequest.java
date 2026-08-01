package com.community.repair.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class FeeBillSaveRequest {
    private Long id;
    private Long houseId;
    private String payerName;
    private String payerPhone;
    private Long userId;
    /** PROPERTY / PARKING / SHARE / OTHER */
    private String feeType;
    private BigDecimal amount;
    private String period;
    private String dueDate;
    private String remark;
    /** 停车费关联车牌 */
    private String plateNo;
}
