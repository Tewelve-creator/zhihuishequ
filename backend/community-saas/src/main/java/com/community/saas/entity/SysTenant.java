package com.community.saas.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_tenant")
public class SysTenant {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String address;
    private String contactName;
    private String contactPhone;
    private Long packageId;
    private String features;
    /** active / degraded / frozen / disabled */
    private String status;
    private LocalDateTime expireTime;
    private Integer storageLimitMb;
    private Integer aiQuota;
    private LocalDateTime createTime;
}
