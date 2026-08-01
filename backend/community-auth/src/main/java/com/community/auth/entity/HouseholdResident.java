package com.community.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("household_resident")
public class HouseholdResident {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long userId;
    private Long houseId;
    private String name;
    private String phone;
    /** OWNER / TENANT */
    private String identityType;
    /** 租客租赁起止 */
    private java.time.LocalDate leaseStart;
    private java.time.LocalDate leaseEnd;
    /** ACTIVE / MOVING_OUT / MOVED_OUT */
    private String status;
    /** APPLY / MANUAL */
    private String source;
    private String houseLabel;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
