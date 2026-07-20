package com.community.auth.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class PackageSaveRequest {
    private Long id;
    private String name;
    private String code;
    private BigDecimal priceMonth;
    private BigDecimal priceQuarter;
    private BigDecimal priceYear;
    private Integer aiQuota;
    private Integer status;
    private Integer sortNo;
    private String remark;
    /** 功能码列表 */
    private List<String> featureCodes;
}
