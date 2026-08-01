package com.community.repair.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("appeal_user_mute")
public class AppealUserMute {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long userId;
    /** NULL = 永久禁言 */
    private LocalDateTime muteUntil;
    private String reason;
    private Long operatorId;
    private String operatorName;
    /** active / released */
    private String status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
