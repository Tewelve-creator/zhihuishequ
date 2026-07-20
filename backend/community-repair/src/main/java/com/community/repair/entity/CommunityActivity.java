package com.community.repair.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("community_activity")
public class CommunityActivity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String title;
    private String content;
    private String location;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer maxPeople;
    private Integer signedCount;
    private Long publisherId;
    /** 1上架 0下架 */
    private Integer status;
    private LocalDateTime createTime;
}
