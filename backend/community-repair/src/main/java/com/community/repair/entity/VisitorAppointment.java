package com.community.repair.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("visitor_appointment")
public class VisitorAppointment {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long userId;
    private String visitorName;
    private String visitorPhone;
    private String idCard;
    private String visitReason;
    private LocalDateTime visitTime;
    private Integer visitorCount;
    /** pending / approved / rejected / checked_in / cancelled */
    private String status;
    private String remark;
    private LocalDateTime createTime;
}
