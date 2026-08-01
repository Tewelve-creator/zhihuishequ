package com.community.repair.dto;

import lombok.Data;

/** 地磁/AI 视频车位状态上报 */
@Data
public class ParkSpaceOccupyRequest {
    private Long spaceId;
    private String spaceNo;
    /** FREE / OCCUPIED */
    private String status;
    private String occupyingPlate;
}
