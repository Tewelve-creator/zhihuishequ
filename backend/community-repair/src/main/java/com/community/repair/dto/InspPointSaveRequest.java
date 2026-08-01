package com.community.repair.dto;

import lombok.Data;

@Data
public class InspPointSaveRequest {
    private Long id;
    private String name;
    /** FIRE/ELEVATOR/PARKING/GARDEN/GATE/PUMP/OTHER */
    private String pointType;
    private String locationDesc;
    private String address;
    private Double latitude;
    private Double longitude;
    private Integer status;
    /** 是否启用打卡二维码 */
    private Integer qrEnabled;
    private String remark;
}
