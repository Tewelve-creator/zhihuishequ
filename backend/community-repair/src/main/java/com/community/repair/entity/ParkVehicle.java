package com.community.repair.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 车辆台账（白/黑/绿名单） */
@Data
@TableName("park_vehicle")
public class ParkVehicle {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    /** 车牌号（大写归一化） */
    private String plateNo;
    /** OWNER / VISITOR / GREEN */
    private String vehicleType;
    /** WHITE / BLACK / GREEN */
    private String listType;
    private Long houseId;
    private Long ownerUserId;
    private String ownerName;
    private String ownerPhone;
    private Long spaceId;
    private String brandModel;
    private String color;
    /** 1绑定 0解绑 */
    private Integer bindStatus;
    private String remark;
    private Long createBy;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
