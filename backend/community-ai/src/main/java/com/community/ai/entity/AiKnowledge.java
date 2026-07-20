package com.community.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ai_knowledge")
public class AiKnowledge {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String category;
    private String question;
    private String answer;
    private Integer hitCount;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
