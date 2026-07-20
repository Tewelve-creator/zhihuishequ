package com.community.auth.service;

import com.community.common.exception.BizException;
import com.community.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 短信验证码（演示：Redis 缓存，可固定码）
 */
@Service
@RequiredArgsConstructor
public class SmsService {

    private static final String KEY_PREFIX = "auth:sms:";

    private final StringRedisTemplate redisTemplate;

    @Value("${auth.sms.fixed-code:123456}")
    private String fixedCode;

    @Value("${auth.sms.ttl-seconds:300}")
    private long ttlSeconds;

    @Value("${auth.sms.use-fixed:true}")
    private boolean useFixed;

    public String sendCode(String phone) {
        if (!StringUtils.hasText(phone) || phone.length() < 11) {
            throw new BizException(ErrorCode.BAD_REQUEST, "手机号格式不正确");
        }
        String code = useFixed ? fixedCode : String.format("%06d", ThreadLocalRandom.current().nextInt(0, 1000000));
        redisTemplate.opsForValue().set(KEY_PREFIX + phone, code, Duration.ofSeconds(ttlSeconds));
        return code;
    }

    public void verify(String phone, String code) {
        if (!StringUtils.hasText(code)) {
            throw new BizException(ErrorCode.SMS_INVALID);
        }
        String cached = redisTemplate.opsForValue().get(KEY_PREFIX + phone);
        if (cached == null || !cached.equals(code.trim())) {
            throw new BizException(ErrorCode.SMS_INVALID);
        }
        redisTemplate.delete(KEY_PREFIX + phone);
    }
}
