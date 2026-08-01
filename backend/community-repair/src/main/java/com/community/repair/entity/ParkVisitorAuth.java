package com.community.repair.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 访客临时通行授权 */
@Data
@TableName("park_visitor_auth")
public class ParkVisitorAuth {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String plateNo;
    private String authCode;
    private String qrToken;
    private Long ownerUserId;
    private Long houseId;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    /** ACTIVE / USED / EXPIRED / REVOKED */
    private String status;
    private Integer maxEntries;
    private Integer usedEntries;
    /** OWNER / GATE / SELF */
    private String source;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
