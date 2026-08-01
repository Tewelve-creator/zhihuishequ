package com.community.repair.dto;

import lombok.Data;

@Data
public class EquipDeviceSaveRequest {
    private Long id;
    private String name;
    private String location;
    private String deviceType;
    /** 设备数量（同型号同位置合并计数） */
    private Integer quantity;
    /** MONTH / QUARTER / HALF_YEAR / YEAR */
    private String cycleType;
    private String vendorName;
    private String contactName;
    private String contactPhone;
    private String lastMaintDate;
    private String nextMaintDate;
    private String remark;
}
