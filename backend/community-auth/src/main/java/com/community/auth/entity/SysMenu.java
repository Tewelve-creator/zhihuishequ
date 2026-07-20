package com.community.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_menu")
public class SysMenu {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long parentId;
    private String menuName;
    /** M/C/F */
    private String menuType;
    private String path;
    private String component;
    private String perms;
    private String icon;
    /** APP / ADMIN */
    private String clientType;
    private Integer sortNo;
    private Integer visible;
    private Integer status;
    private LocalDateTime createTime;
}
