package com.community.repair.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("notice_info")
public class NoticeInfo {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String title;
    private String content;
    private String summary;
    private String targetTag;
    private Long publisherId;
    /** 1=启用 0=下架 */
    private Integer status;
    /** 对外可见时间（可定时） */
    private LocalDateTime publishTime;
    private LocalDateTime createTime;

    /** 非表字段：已发布 / 定时待发 / 已下架 */
    @TableField(exist = false)
    private String publishState;
}
