package com.community.gateway.support;

import cn.hutool.json.JSONUtil;
import com.community.common.auth.LoginUser;
import com.community.common.constant.LeaseCacheKeys;
import com.community.common.tenant.LeaseSnapshot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 从 Redis 叠加最新套餐快照到 LoginUser（支付/到期即时生效）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LeaseSnapshotReader {

    private final StringRedisTemplate redisTemplate;

    public LoginUser overlay(LoginUser lu) {
        if (lu == null || lu.getTenantId() == null || lu.isPlatformAdmin()) {
            return lu;
        }
        try {
            String json = redisTemplate.opsForValue().get(LeaseCacheKeys.of(lu.getTenantId()));
            if (!StringUtils.hasText(json)) {
                return lu;
            }
            LeaseSnapshot snap = JSONUtil.toBean(json, LeaseSnapshot.class);
            if (snap == null) {
                return lu;
            }
            if (snap.getFeatures() != null) {
                lu.setFeatures(snap.getFeatures());
            }
            if (StringUtils.hasText(snap.getStatus())) {
                lu.setTenantStatus(snap.getStatus());
            }
            if (snap.getExpireTime() != null) {
                lu.setExpireTime(snap.getExpireTime());
            }
        } catch (Exception e) {
            log.debug("lease overlay skip: {}", e.getMessage());
        }
        return lu;
    }
}
