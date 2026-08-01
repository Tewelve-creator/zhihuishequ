package com.community.common.feign.dto;

import lombok.Data;

@Data
public class AiImageRepairResponse {
    private String facilityType;
    private String damageLevel;
    private String suggestTitle;
    private String suggestDesc;
    private Double confidence;
    private String provider;
    /** 位置建议（楼道/单元门口等） */
    private String locationHint;
    /** 置信度 < 0.85 时建议用户手选 */
    private Boolean needManual;
}
