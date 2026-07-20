package com.community.common.tenant;

import lombok.Data;

import java.io.Serializable;

/**
 * 租户套餐快照（features CSV + status），供网关拦截与前端菜单隐藏
 */
@Data
public class LeaseSnapshot implements Serializable {
    private Long tenantId;
    private String features;
    private String status;
    private String expireTime;
    private Long packageId;
}
