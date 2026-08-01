package com.community.repair.dto;

import lombok.Data;

@Data
public class InspCheckinRequest {
    private Long taskPointId;
    private Double latitude;
    private Double longitude;
    /** 逗号分隔或 JSON 数组字符串 */
    private String photoUrls;
    private Boolean hasHazard;
    private String hazardDesc;
    private String remark;
}
