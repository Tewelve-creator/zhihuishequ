package com.community.repair.dto;

import lombok.Data;

import java.util.List;

@Data
public class FeePayCreateRequest {
    private List<Long> billIds;
    /** WECHAT / ALIPAY */
    private String payChannel;
    private Long houseId;
}
