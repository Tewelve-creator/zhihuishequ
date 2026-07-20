package com.community.repair.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("repair_order")
public class RepairOrder {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long userId;
    /** 接单/被指派物业用户 */
    private Long handlerId;
    /** 处理人姓名（非表字段，列表回填） */
    @TableField(exist = false)
    private String handlerName;
    private String title;
    private String description;
    private String facilityType;
    private String damageLevel;
    /** AI 识别故障标签 */
    private String aiTag;
    private String imageUrl;
    /** 维修完工照片 */
    private String finishImageUrl;
    private String status;
    private String handleRemark;
    private LocalDateTime acceptTime;
    private LocalDateTime finishTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
