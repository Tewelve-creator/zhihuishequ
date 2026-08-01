package com.community.auth.dto;

import lombok.Data;

@Data
public class HouseholdSaveRequest {
    private Long id;
    private Long houseId;
    private Long userId;
    private String name;
    private String phone;
    /** OWNER / TENANT */
    private String identityType;
    /** ACTIVE / MOVING_OUT / MOVED_OUT */
    private String status;
    /** 租客租赁起止 yyyy-MM-dd */
    private String leaseStart;
    private String leaseEnd;
}
