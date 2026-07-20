package com.community.auth.vo;

import lombok.Data;

@Data
public class TenantStatsVO {
    private Long totalTenants;
    private Long activeTenants;
    private Long degradedTenants;
    private Long frozenTenants;
    private Long disabledTenants;
    private Long expiringIn30Days;
    private Long paidOrders;
}
