package com.community.repair.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class FeeReceiptNoticeRequest {
    private String title;
    private String content;
    private String scope;
    private String amountHint;
    /** 选中业主：houseId / userId / owner / room / phone / unpaidAmount */
    private List<Map<String, Object>> targets;
}
