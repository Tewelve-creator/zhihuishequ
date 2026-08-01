package com.community.repair.dto;

import lombok.Data;

@Data
public class EquipMaintRecordRequest {
    private Long deviceId;
    private String maintDate;
    private String content;
    private String photoUrls;
    private String attachmentUrls;
    private String workerName;
    private String remark;
    /** 完成后是否按周期顺延下次维保日期，默认 true */
    private Boolean advanceNext;
}
