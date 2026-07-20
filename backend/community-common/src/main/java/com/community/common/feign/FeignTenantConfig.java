package com.community.common.feign;

import com.community.common.auth.AuthConstants;
import com.community.common.tenant.TenantContext;
import feign.RequestInterceptor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Feign 透传租户/用户头，保证 AI 侧知识库隔离
 */
@Configuration
@ConditionalOnClass(RequestInterceptor.class)
public class FeignTenantConfig {

    @Bean
    public RequestInterceptor tenantFeignInterceptor() {
        return template -> {
            Long tid = TenantContext.getTenantId();
            Long uid = TenantContext.getUserId();
            String role = TenantContext.getRole();
            if (tid != null) {
                template.header(AuthConstants.H_TENANT, String.valueOf(tid));
            }
            if (uid != null) {
                template.header(AuthConstants.H_USER, String.valueOf(uid));
            }
            if (role != null) {
                template.header(AuthConstants.H_ROLE, role);
            }
            if (TenantContext.getClientType() != null) {
                template.header(AuthConstants.H_CLIENT, TenantContext.getClientType());
            }
            if (TenantContext.getFeatures() != null) {
                template.header(AuthConstants.H_FEATURES, TenantContext.getFeatures());
            }
        };
    }
}
