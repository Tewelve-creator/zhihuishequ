package com.community.auth.config;

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.stp.StpInterface;
import com.community.auth.mapper.SysMenuMapper;
import com.community.auth.mapper.SysUserRoleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Collections;
import java.util.List;

@Configuration
@RequiredArgsConstructor
public class SaTokenConfig implements WebMvcConfigurer {

    private final SysMenuMapper menuMapper;
    private final SysUserRoleMapper userRoleMapper;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new SaInterceptor())
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/auth/login",
                        "/auth/register",
                        "/auth/sms",
                        "/auth/sms/send",
                        "/auth/tenants/public",
                        "/auth/packages/public",
                        "/error",
                        "/actuator/**"
                );
    }

    @Bean
    public StpInterface stpInterface() {
        return new StpInterface() {
            @Override
            public List<String> getPermissionList(Object loginId, String loginType) {
                Long userId = Long.parseLong(String.valueOf(loginId));
                List<String> perms = menuMapper.selectPermsByUserId(userId);
                return perms == null ? Collections.emptyList() : perms;
            }

            @Override
            public List<String> getRoleList(Object loginId, String loginType) {
                Long userId = Long.parseLong(String.valueOf(loginId));
                List<String> roles = userRoleMapper.selectRoleCodesByUserId(userId);
                return roles == null ? Collections.emptyList() : roles;
            }
        };
    }
}
