package com.community.gateway.filter;

import cn.dev33.satoken.reactor.context.SaReactorSyncHolder;
import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpUtil;
import com.community.common.auth.AuthConstants;
import com.community.common.auth.LoginUser;
import com.community.common.constant.FeatureCodes;
import com.community.common.constant.RoleConstants;
import com.community.gateway.support.LeaseSnapshotReader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 套餐功能拦截器。
 * <p>默认 {@code gateway.package.enabled=true}，按 path→featureCode 校验当前租户有效功能。
 */
@Component
public class PackageDowngradeFilter implements GlobalFilter, Ordered {

    private final LeaseSnapshotReader leaseSnapshotReader;
    private final boolean enabled;

    /** path 前缀 -> 所需功能码（长路径优先匹配） */
    private final Map<String, String> pathFeature = new LinkedHashMap<>();

    public PackageDowngradeFilter(
            LeaseSnapshotReader leaseSnapshotReader,
            @Value("${gateway.package.enabled:true}") boolean enabled,
            @Value("${gateway.package.extra-rules:}") String extraRules) {
        this.leaseSnapshotReader = leaseSnapshotReader;
        this.enabled = enabled;
        // 高级 AI（长前缀在前）
        pathFeature.put("/ai/image", FeatureCodes.AI_IMAGE_REPAIR);
        pathFeature.put("/ai/emotion", FeatureCodes.AI_EMOTION);
        pathFeature.put("/ai/lost", FeatureCodes.AI_LOST_MATCH);
        pathFeature.put("/ai/chat", FeatureCodes.AI_CHAT);
        pathFeature.put("/ai/kb", FeatureCodes.AI_CHAT);
        pathFeature.put("/ai/audit", FeatureCodes.AI_CHAT);
        pathFeature.put("/ai/notice", FeatureCodes.AI_CHAT);
        pathFeature.put("/ai/schedule", FeatureCodes.AI_CHAT);
        pathFeature.put("/ai/", FeatureCodes.AI_CHAT);
        // 数据大屏
        pathFeature.put("/stats/tenant/dashboard", FeatureCodes.DATA_DASHBOARD);
        pathFeature.put("/stats/dashboard", FeatureCodes.DATA_DASHBOARD);
        pathFeature.put("/stats/cockpit", FeatureCodes.DATA_DASHBOARD);
        pathFeature.put("/stats/screen", FeatureCodes.DATA_DASHBOARD);
        pathFeature.put("/saas/screen", FeatureCodes.DATA_DASHBOARD);
        // Excel / 数据导出
        pathFeature.put("/stats/export", FeatureCodes.DATA_EXPORT);
        pathFeature.put("/stats/excel", FeatureCodes.DATA_EXPORT);
        pathFeature.put("/export", FeatureCodes.DATA_EXPORT);
        // 其它高阶
        pathFeature.put("/repair/vendor", FeatureCodes.VENDOR_MANAGE);
        pathFeature.put("/store/", FeatureCodes.VENDOR_MANAGE);
        pathFeature.put("/lost/", FeatureCodes.AI_LOST_MATCH);
        parseExtra(extraRules);
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!enabled) {
            return chain.filter(exchange);
        }
        String path = exchange.getRequest().getURI().getPath();
        String required = matchFeature(path);
        if (required == null) {
            return chain.filter(exchange);
        }

        return Mono.fromCallable(() -> {
                    SaReactorSyncHolder.setContext(exchange);
                    try {
                        if (!StpUtil.isLogin()) {
                            return null;
                        }
                        SaSession session = StpUtil.getSession(false);
                        if (session == null) {
                            return null;
                        }
                        Object obj = session.get(AuthConstants.SESSION_LOGIN_USER);
                        LoginUser lu = obj instanceof LoginUser u ? u : null;
                        return leaseSnapshotReader.overlay(lu);
                    } finally {
                        SaReactorSyncHolder.clearContext();
                    }
                })
                .flatMap(lu -> {
                    if (lu == null) {
                        return chain.filter(exchange);
                    }
                    if (RoleConstants.isPlatformAdmin(lu.getRole())) {
                        return chain.filter(exchange);
                    }
                    String status = lu.getTenantStatus() == null ? "active" : lu.getTenantStatus();
                    if ("frozen".equals(status) || "disabled".equals(status)) {
                        return writeFail(exchange, "租户已冻结/禁用，高级功能不可用，请续费解锁");
                    }
                    if ("degraded".equals(status)
                            && !FeatureCodes.BASIC_REPAIR.equals(required)
                            && !FeatureCodes.BASIC_NOTICE.equals(required)) {
                        return writeFail(exchange, "套餐已到期降级，仅保留公告与报修，请续费恢复");
                    }
                    if (!lu.hasFeature(required)) {
                        return writeFail(exchange, "当前套餐未开放该功能（需 " + required + "），请升级或续费");
                    }
                    return chain.filter(exchange);
                });
    }

    private String matchFeature(String path) {
        if (path == null) {
            return null;
        }
        String lower = path.toLowerCase();
        if (lower.contains("/export") || lower.endsWith("/excel")) {
            return FeatureCodes.DATA_EXPORT;
        }
        for (Map.Entry<String, String> e : pathFeature.entrySet()) {
            if (path.startsWith(e.getKey()) || path.contains(e.getKey())) {
                return e.getValue();
            }
        }
        return null;
    }

    private void parseExtra(String extraRules) {
        if (!StringUtils.hasText(extraRules)) {
            return;
        }
        for (String part : extraRules.split(",")) {
            String[] kv = part.trim().split("=");
            if (kv.length == 2 && StringUtils.hasText(kv[0]) && StringUtils.hasText(kv[1])) {
                pathFeature.put(kv[0].trim(), kv[1].trim());
            }
        }
    }

    private Mono<Void> writeFail(ServerWebExchange exchange, String msg) {
        exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String body = "{\"code\":40303,\"msg\":\"" + msg.replace("\"", "'")
                + "\",\"error\":\"FEATURE_NOT_AVAILABLE\",\"data\":null}";
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(bytes);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        return -90;
    }
}
