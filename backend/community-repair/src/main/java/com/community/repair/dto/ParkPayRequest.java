package com.community.repair.dto;

import lombok.Data;

/** 停车缴费 */
@Data
public class ParkPayRequest {
    private Long orderId;
    private String plateNo;
    /** MINI / EXIT / CASH */
    private String payChannel;
}
