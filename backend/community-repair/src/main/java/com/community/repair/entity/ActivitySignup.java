package com.community.repair.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("activity_signup")
public class ActivitySignup {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long activityId;
    private Long userId;
    private String userName;
    private String phone;
    /** signed / cancelled / checked */
    private String status;
    private LocalDateTime createTime;
}
