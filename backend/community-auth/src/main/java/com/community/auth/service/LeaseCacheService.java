package com.community.auth.service;

import cn.hutool.json.JSONUtil;
import com.community.auth.entity.SysTenant;
import com.community.common.constant.LeaseCacheKeys;
import com.community.common.tenant.LeaseSnapshot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;

/**
 * 将租户套餐/状态写入 Redis，供 Gateway 实时拦截（无需用户重新登录）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LeaseCacheService {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final StringRedisTemplate redisTemplate;

    public void publish(SysTenant t) {
        if (t == null || t.getId() == null) {
            return;
        }
        try {
            LeaseSnapshot snap = new LeaseSnapshot();
            snap.setTenantId(t.getId());
            snap.setFeatures(t.getFeatures() == null ? "" : t.getFeatures());
            snap.setStatus(t.getStatus() == null ? "active" : t.getStatus());
            snap.setPackageId(t.getPackageId());
            if (t.getExpireTime() != null) {
                snap.setExpireTime(t.getExpireTime().format(FMT));
            }
            redisTemplate.opsForValue().set(
                    LeaseCacheKeys.of(t.getId()),
                    JSONUtil.toJsonStr(snap),
                    40, TimeUnit.DAYS);
        } catch (Exception e) {
            log.warn("publish lease cache fail tenantId={}: {}", t.getId(), e.getMessage());
        }
    }
}
