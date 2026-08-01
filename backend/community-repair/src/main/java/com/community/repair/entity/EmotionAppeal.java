package com.community.repair.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 邻里倾诉 / 社区朋友圈动态（复用 emotion_appeal 表）
 * status=normal 表示朋友圈动态；pending/processing/done 为历史 AI 工单
 */
@Data
@TableName("emotion_appeal")
public class EmotionAppeal {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long userId;
    private Long houseId;
    private String houseLabel;
    private String nickname;
    private String content;
    /** JSON 数组字符串，如 ["/file/static/1/a.jpg"]，最多 9 张 */
    private String imageUrls;
    private Integer likeCount;
    private Integer commentCount;
    /** 0 正常 / 1 已删除（软删，后台可查） */
    private Integer deleted;
    private String deleteReason;
    private Long deletedBy;
    private java.time.LocalDateTime deletedTime;
    /** 1 置顶 */
    private Integer pinned;
    /** 1 屏蔽（前台不可见） */
    private Integer hidden;
    private String sentiment;
    private Double score;
    /** normal=朋友圈；pending/processing/done=历史工单 */
    private String status;
    private Long handlerId;
    private String handleRemark;
    private LocalDateTime createTime;
}
