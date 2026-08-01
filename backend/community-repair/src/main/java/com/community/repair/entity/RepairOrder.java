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

    /** repair=报修工单 report=报事工单 */
    private String orderType;
    /** 紧急等级：高/中/低 */
    private String urgency;
    private Long categoryId;
    private String categoryName;
    /** 关联 AI 情绪调解案 */
    private Long emotionCaseId;
    private String emotionTags;
    private String sentiment;
    /** 关联意见反馈 */
    private Long feedbackId;

    /** 关联房屋 */
    private Long houseId;
    /** 楼栋，如 2栋 */
    private String building;
    /** 单元，如 1单元 */
    private String unit;
    /** 房号，如 101 */
    private String roomNo;
    /** 展示用位置：2栋1单元101 / 电梯厅 */
    private String locationLabel;

    /** 请求别名：居民端 locationHint，落库到 locationLabel（可与房屋拼接） */
    @TableField(exist = false)
    private String locationHint;

    /** 非表字段：识图日志条数等 */
    @TableField(exist = false)
    private Integer aiImageLogCount;
    @TableField(exist = false)
    private String orderTypeLabel;
    @TableField(exist = false)
    private Boolean emotionAlert;
    @TableField(exist = false)
    private Boolean fromFeedback;
}
