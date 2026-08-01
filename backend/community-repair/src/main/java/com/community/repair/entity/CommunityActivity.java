package com.community.repair.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("community_activity")
public class CommunityActivity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String title;
    /** 邻里文体 / 公益志愿 / 物业通知 / 亲子活动 */
    private String category;
    private String coverUrl;
    private String content;
    /** AI 摘要 ≤100 字 */
    private String summary;
    /** 标签，逗号分隔，用于定向推送 */
    private String tags;
    private String targetRule;
    private String location;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime signupEnd;
    private Integer maxPeople;
    /** 1允许取消报名 0不允许 */
    private Integer allowCancel;
    private Integer signedCount;
    private Long publisherId;
    /** 1上架 0下架 */
    private Integer status;
    private Integer aiAuditPass;
    private Integer pushCount;
    private LocalDateTime createTime;
}
