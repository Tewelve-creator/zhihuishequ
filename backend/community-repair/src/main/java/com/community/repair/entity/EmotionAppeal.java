package com.community.repair.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 邻里情绪/诉求工单（负向情绪推送网格员） */
@Data
@TableName("emotion_appeal")
public class EmotionAppeal {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long userId;
    private String content;
    private String sentiment;
    private Double score;
    /** pending / processing / done */
    private String status;
    private Long handlerId;
    private String handleRemark;
    private LocalDateTime createTime;
}
