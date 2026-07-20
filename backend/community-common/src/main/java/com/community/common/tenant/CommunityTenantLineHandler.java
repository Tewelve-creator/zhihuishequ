package com.community.common.tenant;

import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.NullValue;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * MyBatis-Plus 多租户行级拦截：自动追加 tenant_id
 */
public class CommunityTenantLineHandler implements TenantLineHandler {

    /** 无 tenant_id 或不参与隔离的表 */
    private static final Set<String> IGNORE_TABLES = new HashSet<>(Arrays.asList(
            "sys_tenant",
            "sys_role",
            "sys_menu",
            "sys_role_menu",
            "sys_user_role",
            "sys_package",
            "sys_package_feature",
            "sys_sensitive_word",
            "global_ai_kb"
    ));

    @Override
    public Expression getTenantId() {
        Long tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            return new NullValue();
        }
        return new LongValue(tenantId);
    }

    @Override
    public String getTenantIdColumn() {
        return "tenant_id";
    }

    @Override
    public boolean ignoreTable(String tableName) {
        if (TenantContext.isSkipTenant()) {
            return true;
        }
        return IGNORE_TABLES.contains(tableName.toLowerCase());
    }
}
