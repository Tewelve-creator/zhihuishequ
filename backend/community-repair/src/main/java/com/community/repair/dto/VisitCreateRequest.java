package com.community.repair.dto;

import lombok.Data;

/**
 * 居民提交临时访客预约（免审核，直接生效）
 */
@Data
public class VisitCreateRequest {
    /** 必填 */
    private String visitorName;
    /** 选填 */
    private String visitorPhone;
    /** 是否驾车来访 */
    private Boolean driveFlag;
    /** 驾车时必填 */
    private String plateNo;
    private String visitReason;
    /** 预约到访开始时间 yyyy-MM-dd HH:mm:ss 或 ISO */
    private String visitTime;
    /** 来访结束/通行截止时间（可选，默认到访后12小时） */
    private String expireTime;
    private Integer visitorCount;
    private Long houseId;
    private String remark;
}
