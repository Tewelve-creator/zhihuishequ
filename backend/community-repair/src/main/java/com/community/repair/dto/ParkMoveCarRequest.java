package com.community.repair.dto;

import lombok.Data;

/** 匿名一键挪车 */
@Data
public class ParkMoveCarRequest {
    /** 被占用车位编号或车牌 */
    private String spaceNo;
    private String plateNo;
    private String message;
}
