package com.community.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ai_schedule_remind")
public class AiScheduleRemind {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long userId;
    private String title;
    private String content;
    private LocalDateTime remindTime;
    private String status;
    private LocalDateTime createTime;
    private LocalDateTime sentTime;
    private String sourceType;
    private Long sourceId;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String jumpPath;
    private Integer conflictFlag;
}
