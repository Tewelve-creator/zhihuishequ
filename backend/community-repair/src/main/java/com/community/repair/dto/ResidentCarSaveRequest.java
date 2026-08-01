package com.community.repair.dto;

import lombok.Data;

/** 常驻车辆新增/编辑 */
@Data
public class ResidentCarSaveRequest {
    private Long id;
    private Long houseId;
    private Long residentId;
    private Long userId;
    private String ownerName;
    private String ownerPhone;
    private String plateNo;
    private String brandModel;
    private String color;
    private Long spaceId;
    private String spaceNo;
    /** 月租金额 */
    private java.math.BigDecimal monthlyRent;
    private String remark;
}
