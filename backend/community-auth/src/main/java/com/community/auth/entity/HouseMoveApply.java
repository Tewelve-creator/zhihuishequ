package com.community.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("house_move_apply")
public class HouseMoveApply {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String applyNo;
    private Long tenantId;
    private Long userId;
    private Long houseId;
    /** MOVE_IN / MOVE_OUT */
    private String applyType;
    /** OWNER / TENANT */
    private String identityType;
    private String applicantName;
    private String applicantPhone;
    private String houseLabel;
    /** PENDING / APPROVED / REJECTED */
    private String status;
    private String remark;
    private Long auditUserId;
    private String auditName;
    private String auditRemark;
    private LocalDateTime auditTime;
    private LocalDateTime createTime;
}
