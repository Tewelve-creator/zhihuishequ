package com.community.gateway.filter;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.reactor.context.SaReactorSyncHolder;
import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpUtil;
import com.community.common.auth.AuthConstants;
import com.community.common.auth.LoginUser;
import com.community.common.constant.RoleConstants;
import com.community.gateway.support.LeaseSnapshotReader;
import lombok.RequiredArgsConstructor;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 网关统一鉴权：校验 Sa-Token，透传用户/租户头，禁止跨租户访问
 */
@Component
@RequiredArgsConstructor
public class AuthGlobalFilter implements GlobalFilter, Ordered {

    private final LeaseSnapshotReader leaseSnapshotReader;

    private static final List<String> WHITE = List.of(
            "/auth/login",
            "/auth/register",
            "/auth/sms",
            "/auth/sms/send",
            "/auth/tenants/public",
            "/auth/tenants/resolve-by-phone",
            "/auth/packages/public",
            "/saas/packages/public",
            "/file/static",
            "/fee/alipay/notify",
            "/fee/alipay/return",
            "/visit/pass/info",
            "/visit/pass/qr",
            "/visit/pass/verify",
            "/actuator"
    );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        if (isWhite(path)) {
            return chain.filter(exchange);
        }

        return Mono.fromCallable(() -> {
                    SaReactorSyncHolder.setContext(exchange);
                    try {
                        StpUtil.checkLogin();
                        return leaseSnapshotReader.overlay(resolveLoginUser());
                    } finally {
                        SaReactorSyncHolder.clearContext();
                    }
                })
                .flatMap(lu -> {
                    // 以 Token 内租户为准：客户端头不一致时覆盖，不再弹「禁止跨社区访问」
                    String headerTenant = exchange.getRequest().getHeaders().getFirst(AuthConstants.H_TENANT);

                    ServerHttpRequest.Builder builder = exchange.getRequest().mutate()
                            .header(AuthConstants.H_USER, String.valueOf(lu.getUserId()))
                            .header(AuthConstants.H_ROLE, lu.getRole() == null ? "" : lu.getRole())
                            .header(AuthConstants.H_CLIENT, lu.getClientType() == null ? "" : lu.getClientType())
                            .header(AuthConstants.H_FEATURES, lu.getFeatures() == null ? "" : lu.getFeatures())
                            .header(AuthConstants.H_TENANT_STATUS, lu.getTenantStatus() == null ? "active" : lu.getTenantStatus());
                    if (lu.isPlatformAdmin()) {
                        builder.header(AuthConstants.H_SKIP, "1");
                        if (headerTenant != null && !headerTenant.isBlank()) {
                            builder.header(AuthConstants.H_TENANT, headerTenant);
                        }
                    } else if (lu.getTenantId() != null) {
                        builder.header(AuthConstants.H_TENANT, String.valueOf(lu.getTenantId()));
                        builder.header(AuthConstants.H_SKIP, "0");
                    }
                    if (lu.getShopId() != null) {
                        builder.header(AuthConstants.H_SHOP, String.valueOf(lu.getShopId()));
                    }
                    return chain.filter(exchange.mutate().request(builder.build()).build());
                })
                .onErrorResume(NotLoginException.class, e ->
                        writeFail(exchange, HttpStatus.UNAUTHORIZED, "未登录或登录已失效"))
                .onErrorResume(e ->
                        writeFail(exchange, HttpStatus.UNAUTHORIZED, e.getMessage() == null ? "鉴权失败" : e.getMessage()));
    }

    private LoginUser resolveLoginUser() {
        SaSession session = StpUtil.getSession(false);
        if (session != null) {
            Object obj = session.get(AuthConstants.SESSION_LOGIN_USER);
            if (obj instanceof LoginUser lu) {
                return lu;
            }
        }
        // Session 异常时最小可用信息
        LoginUser fallback = new LoginUser();
        fallback.setUserId(Long.parseLong(String.valueOf(StpUtil.getLoginId())));
        fallback.setRole(RoleConstants.RESIDENT);
        return fallback;
    }

    private boolean isWhite(String path) {
        for (String w : WHITE) {
            if (path.startsWith(w) || path.contains(w)) {
                return true;
            }
        }
        return false;
    }

    private Mono<Void> writeFail(ServerWebExchange exchange, HttpStatus status, String msg) {
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String body = "{\"code\":" + status.value() + ",\"msg\":\"" + msg + "\",\"data\":null}";
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(bytes);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        return -100;
    }
}
