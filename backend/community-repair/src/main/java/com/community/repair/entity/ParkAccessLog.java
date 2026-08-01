package com.community.repair.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 车辆进出通行日志 */
@Data
@TableName("park_access_log")
public class ParkAccessLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String plateNo;
    /** IN / OUT */
    private String direction;
    private String gateCode;
    private String gateName;
    /** ALLOW / DENY */
    private String passResult;
    private String passReason;
    private Long vehicleId;
    private Long spaceId;
    private Long authId;
    private Long orderId;
    private String snapUrl;
    private LocalDateTime passTime;
    private LocalDateTime createTime;
}
