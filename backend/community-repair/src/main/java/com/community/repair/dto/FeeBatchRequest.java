package com.community.repair.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class FeeBatchRequest {
    private String feeType;
    private BigDecimal amount;
    private String period;
    private String dueDate;
    private String remark;
    /** 指定房屋；空则对本租户全部有效住户批量生成 */
    private List<Long> houseIds;
}
