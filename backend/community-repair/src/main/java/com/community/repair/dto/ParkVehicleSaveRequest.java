package com.community.repair.dto;

import lombok.Data;

/** 车辆绑定 / 变更 / 名单 */
@Data
public class ParkVehicleSaveRequest {
    private Long id;
    private String plateNo;
    /** OWNER / VISITOR / GREEN */
    private String vehicleType;
    /** WHITE / BLACK / GREEN */
    private String listType;
    private Long houseId;
    private Long ownerUserId;
    private String ownerName;
    private String ownerPhone;
    private Long spaceId;
    private String brandModel;
    private String color;
    private String remark;
}
