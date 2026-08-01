package com.community.repair.dto;

import lombok.Data;

import java.util.List;

@Data
public class InspTaskSaveRequest {
    private Long id;
    private String title;
    private Long assigneeId;
    private String assigneeName;
    /** ONCE / DAILY / WEEKLY */
    private String cycleType;
    private String startTime;
    private String endTime;
    private String requirement;
    private String remark;
    private List<Long> pointIds;
}
