package com.community.repair.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 临时访客预约（免审核：提交即已通过）
 * 状态：approved / checked_in / expired / cancelled
 */
@Data
@TableName("visitor_appointment")
public class VisitorAppointment {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long userId;
    private Long houseId;
    private String visitorName;
    private String visitorPhone;
    /** 临时访客车牌（驾车时填写） */
    private String plateNo;
    /** 1驾车 0否 */
    private Integer driveFlag;
    /** @deprecated 不再采集身份证，保留列兼容旧数据 */
    private String idCard;
    private String visitReason;
    private LocalDateTime visitTime;
    /** 通行有效截止 */
    private LocalDateTime expireTime;
    private Integer visitorCount;
    /** approved / checked_in / expired / cancelled */
    private String status;
    private LocalDateTime checkinTime;
    private Long checkinBy;
    private String remark;
    private LocalDateTime createTime;
    /** 通行二维码 token */
    private String passToken;
    /** ONCE=单次通行 */
    private String accessMode;
    /** 是否已扫码通行 */
    private Integer passUsed;

    /* ===== 展示字段（非表列） ===== */
    @TableField(exist = false)
    private String statusLabel;
    @TableField(exist = false)
    private String statusTone;
    @TableField(exist = false)
    private String hostHouse;
    @TableField(exist = false)
    private String communityName;
    @TableField(exist = false)
    private String hostNickname;
    @TableField(exist = false)
    private Long remainSeconds;
    @TableField(exist = false)
    private Boolean canCancel;
    @TableField(exist = false)
    private String passUrl;
}
