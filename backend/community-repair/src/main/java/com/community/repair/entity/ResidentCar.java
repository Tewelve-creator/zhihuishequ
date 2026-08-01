package com.community.repair.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 常驻住户车辆 */
@Data
@TableName("resident_car")
public class ResidentCar {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long houseId;
    private String houseLabel;
    private Long residentId;
    private Long userId;
    private String ownerName;
    private String ownerPhone;
    private String plateNo;
    private String brandModel;
    private String color;
    /** ACTIVE / DISABLED */
    private String status;
    private Long spaceId;
    private String spaceNo;
    private java.math.BigDecimal monthlyRent;
    /** 1 限制进出（长期欠费） */
    private Integer accessRestricted;
    private String remark;
    private Long createBy;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
