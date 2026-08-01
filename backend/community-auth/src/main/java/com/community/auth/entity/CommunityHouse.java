package com.community.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("community_house")
public class CommunityHouse {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String communityName;
    private String building;
    private String unit;
    private String roomNo;
    /** 户型，如 两室一厅 */
    private String layoutType;
    /** 建筑面积㎡ */
    private java.math.BigDecimal areaSqm;
    /** VACANT/SELF/RENT/OTHER */
    private String occupyStatus;
    private Integer status;
    private LocalDateTime createTime;
}
