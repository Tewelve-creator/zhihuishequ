package com.community.repair.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 车位台账与占用状态 */
@Data
@TableName("park_space")
public class ParkSpace {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String spaceNo;
    private String areaCode;
    /** OWNED / RENT / PUBLIC */
    private String spaceType;
    /** FREE / OCCUPIED / RESERVED / FAULT */
    private String status;
    private Long ownerUserId;
    private Long houseId;
    private String bindPlateNo;
    /** GEOMAG / AI_VIDEO / NONE */
    private String detectorType;
    private String detectorId;
    private BigDecimal mapX;
    private BigDecimal mapY;
    private String occupyingPlate;
    private LocalDateTime occupyTime;
    /** 1开启固定车位防占用 */
    private Integer antiOccupy;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    /** 占用/绑定业主姓名（非表字段，列表展示用） */
    @TableField(exist = false)
    private String ownerName;
    /** 绑定的业主车辆 ID */
    @TableField(exist = false)
    private Long bindCarId;
}
