package com.community.repair.dto;

import lombok.Data;

/** 道闸识别抬杆请求（设备侧调用） */
@Data
public class ParkGatePassRequest {
    private String plateNo;
    /** IN / OUT */
    private String direction;
    private String gateCode;
    private String gateName;
    private String snapUrl;
}
