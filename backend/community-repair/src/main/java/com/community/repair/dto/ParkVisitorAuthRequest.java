package com.community.repair.dto;

import lombok.Data;

/** 访客临时授权 / 门岗登记 / 自助扫码 */
@Data
public class ParkVisitorAuthRequest {
    private String plateNo;
    private Long houseId;
    /** 有效小时数，默认 4 */
    private Integer validHours;
    private Integer maxEntries;
    /** OWNER / GATE / SELF */
    private String source;
    private String remark;
    /** 自助扫码时携带 qrToken */
    private String qrToken;
}
