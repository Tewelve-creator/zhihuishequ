package com.community.auth.config;

import cn.dev33.satoken.stp.StpUtil;
import com.community.auth.service.AuthService;
import com.community.common.auth.AuthConstants;
import com.community.common.auth.LoginUser;
import com.community.common.tenant.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 已登录请求：从 Sa-Token Session 恢复 TenantContext（本服务直连时也生效）
 */
@Configuration
@RequiredArgsConstructor
@Order(50)
public class LoginUserContextConfig implements WebMvcConfigurer {

    private final AuthService authService;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
                // 优先使用网关透传头（TenantInterceptor 已处理）；若已登录且上下文为空则补齐
                if (StpUtil.isLogin() && TenantContext.getUserId() == null) {
                    try {
                        LoginUser lu = authService.requireLoginUser();
                        lu.fillTenantContext();
                    } catch (Exception ignored) {
                        // 白名单接口可能未登录
                    }
                } else if (StpUtil.isLogin() && TenantContext.getUserId() != null) {
                    // 网关已透传：仍以 Session 中的租户为准，防止客户端伪造跨租户（非运营）
                    try {
                        Object obj = StpUtil.getSession(false).get(AuthConstants.SESSION_LOGIN_USER);
                        if (obj instanceof LoginUser lu && !lu.isPlatformAdmin()) {
                            if (lu.getTenantId() != null) {
                                TenantContext.setTenantId(lu.getTenantId());
                            }
                            TenantContext.setRole(lu.getRole());
                            TenantContext.setSkipTenant(false);
                        }
                    } catch (Exception ignored) {
                    }
                }
                return true;
            }

            @Override
            public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
                // TenantInterceptor 也会 clear；此处双清安全
            }
        }).addPathPatterns("/auth/**");
    }
}
