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
}
