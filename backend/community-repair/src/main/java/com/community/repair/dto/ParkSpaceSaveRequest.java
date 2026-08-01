package com.community.repair.dto;

import lombok.Data;

import java.math.BigDecimal;

/** 车位新增/编辑 */
@Data
public class ParkSpaceSaveRequest {
    private Long id;
    private String spaceNo;
    private String areaCode;
    /** OWNED / RENT / PUBLIC */
    private String spaceType;
    /** FREE / OCCUPIED / RESERVED / FAULT — 管理员可改 */
    private String status;
    private Long ownerUserId;
    private Long houseId;
    private String bindPlateNo;
    private String detectorType;
    private String detectorId;
    private BigDecimal mapX;
    private BigDecimal mapY;
    private Integer antiOccupy;
    private String remark;
}
