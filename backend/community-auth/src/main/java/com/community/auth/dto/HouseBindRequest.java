package com.community.auth.dto;

import lombok.Data;

@Data
public class HouseBindRequest {
    private Long houseId;
    private String building;
    private String unit;
    private String roomNo;
    /** OWNER / TENANT，默认 OWNER */
    private String identityType;
    /** 申请备注 */
    private String remark;
}
