package com.community.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
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
    /** 功能码 CSV */
    private String features;
    private LocalDateTime expireTime;
    private Integer aiQuota;
    private Integer storageLimitMb;
    /** active / degraded / frozen / disabled */
    private String status;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}
