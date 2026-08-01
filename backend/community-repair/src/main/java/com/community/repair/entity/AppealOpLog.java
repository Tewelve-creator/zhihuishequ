package com.community.repair.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("appeal_op_log")
public class AppealOpLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long operatorId;
    private String operatorName;
    /** DELETE_POST / DELETE_COMMENT / MUTE / UNMUTE */
    private String action;
    private Long targetUserId;
    private Long targetAppealId;
    private Long targetCommentId;
    private String detail;
    private LocalDateTime createTime;
}
