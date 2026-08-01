package com.community.repair.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("appeal_like")
public class AppealLike {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long appealId;
    private Long userId;
    private LocalDateTime createTime;
}
