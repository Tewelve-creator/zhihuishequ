package com.community.ai.biz;

import com.alibaba.fastjson2.JSON;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class BizSessionStore {

    private static final String KEY_SESSION = "ai:biz:session:";
    private static final String KEY_TOKEN = "ai:biz:token:";
    private static final long TTL_MIN = 30;

    private final StringRedisTemplate redisTemplate;

    public BizSession get(Long tenantId, Long userId) {
        String raw = redisTemplate.opsForValue().get(sessionKey(tenantId, userId));
        if (raw == null) return null;
        return JSON.parseObject(raw, BizSession.class);
    }

    public void save(BizSession session) {
        String json = JSON.toJSONString(session);
        redisTemplate.opsForValue().set(sessionKey(session.getTenantId(), session.getUserId()),
                json, TTL_MIN, TimeUnit.MINUTES);
        if (session.getActionToken() != null) {
            redisTemplate.opsForValue().set(KEY_TOKEN + session.getActionToken(), json, TTL_MIN, TimeUnit.MINUTES);
        }
    }

    public BizSession getByToken(String token) {
        if (token == null) return null;
        String raw = redisTemplate.opsForValue().get(KEY_TOKEN + token);
        if (raw == null) return null;
        return JSON.parseObject(raw, BizSession.class);
    }

    public void clear(Long tenantId, Long userId) {
        BizSession s = get(tenantId, userId);
        if (s != null && s.getActionToken() != null) {
            redisTemplate.delete(KEY_TOKEN + s.getActionToken());
        }
        redisTemplate.delete(sessionKey(tenantId, userId));
    }

    public String newToken() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private String sessionKey(Long tid, Long uid) {
        return KEY_SESSION + tid + ":" + uid;
    }
}
