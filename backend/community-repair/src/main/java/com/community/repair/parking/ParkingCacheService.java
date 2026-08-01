package com.community.repair.parking;

import com.community.repair.entity.ParkVehicle;
import com.community.repair.entity.ParkVisitorAuth;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 停车 Redis 缓存：白/黑/绿名单、临时访客授权、车位状态、欠费车牌
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ParkingCacheService {

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    /* ---------- 名单 ---------- */

    public void refreshListCache(Long tenantId, String plateNo, String listType, boolean bind) {
        if (!StringUtils.hasText(plateNo) || tenantId == null) return;
        String plate = plateNo.trim().toUpperCase();
        redis.opsForSet().remove(ParkingRedisKeys.white(tenantId), plate);
        redis.opsForSet().remove(ParkingRedisKeys.black(tenantId), plate);
        redis.opsForSet().remove(ParkingRedisKeys.green(tenantId), plate);
        if (!bind) return;
        if ("BLACK".equalsIgnoreCase(listType)) {
            redis.opsForSet().add(ParkingRedisKeys.black(tenantId), plate);
        } else if ("GREEN".equalsIgnoreCase(listType)) {
            redis.opsForSet().add(ParkingRedisKeys.green(tenantId), plate);
        } else {
            redis.opsForSet().add(ParkingRedisKeys.white(tenantId), plate);
        }
    }

    public void removeFromAllLists(Long tenantId, String plateNo) {
        if (!StringUtils.hasText(plateNo)) return;
        String plate = plateNo.trim().toUpperCase();
        redis.opsForSet().remove(ParkingRedisKeys.white(tenantId), plate);
        redis.opsForSet().remove(ParkingRedisKeys.black(tenantId), plate);
        redis.opsForSet().remove(ParkingRedisKeys.green(tenantId), plate);
    }

    public boolean isBlack(Long tenantId, String plate) {
        Boolean m = redis.opsForSet().isMember(ParkingRedisKeys.black(tenantId), plate);
        return Boolean.TRUE.equals(m);
    }

    public boolean isGreen(Long tenantId, String plate) {
        Boolean m = redis.opsForSet().isMember(ParkingRedisKeys.green(tenantId), plate);
        return Boolean.TRUE.equals(m);
    }

    public boolean isWhite(Long tenantId, String plate) {
        Boolean m = redis.opsForSet().isMember(ParkingRedisKeys.white(tenantId), plate);
        return Boolean.TRUE.equals(m);
    }

    public void warmVehicle(ParkVehicle v) {
        if (v == null || v.getTenantId() == null) return;
        refreshListCache(v.getTenantId(), v.getPlateNo(), v.getListType(),
                v.getBindStatus() != null && v.getBindStatus() == 1);
    }

    /* ---------- 访客临时权限 ---------- */

    public void cacheVisitorAuth(ParkVisitorAuth auth) {
        if (auth == null || !StringUtils.hasText(auth.getPlateNo())) return;
        long seconds = Duration.between(LocalDateTime.now(), auth.getEndTime()).getSeconds();
        if (seconds <= 0) {
            redis.delete(ParkingRedisKeys.visitor(auth.getTenantId(), auth.getPlateNo().toUpperCase()));
            return;
        }
        Map<String, Object> m = new HashMap<>();
        m.put("authId", auth.getId());
        m.put("plateNo", auth.getPlateNo().toUpperCase());
        m.put("endTime", auth.getEndTime().toString());
        m.put("maxEntries", auth.getMaxEntries());
        m.put("usedEntries", auth.getUsedEntries());
        m.put("status", auth.getStatus());
        try {
            redis.opsForValue().set(
                    ParkingRedisKeys.visitor(auth.getTenantId(), auth.getPlateNo().toUpperCase()),
                    objectMapper.writeValueAsString(m),
                    seconds, TimeUnit.SECONDS);
        } catch (JsonProcessingException e) {
            log.warn("cache visitor auth fail: {}", e.getMessage());
        }
    }

    public void evictVisitorAuth(Long tenantId, String plateNo) {
        if (!StringUtils.hasText(plateNo)) return;
        redis.delete(ParkingRedisKeys.visitor(tenantId, plateNo.trim().toUpperCase()));
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> getVisitorAuth(Long tenantId, String plate) {
        String json = redis.opsForValue().get(ParkingRedisKeys.visitor(tenantId, plate));
        if (!StringUtils.hasText(json)) return null;
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (Exception e) {
            return null;
        }
    }

    /* ---------- 车位状态 ---------- */

    public void setSpaceStatus(Long tenantId, Long spaceId, String status) {
        if (tenantId == null || spaceId == null) return;
        redis.opsForHash().put(ParkingRedisKeys.spaceHash(tenantId), String.valueOf(spaceId), status);
    }

    public String getSpaceStatus(Long tenantId, Long spaceId) {
        Object v = redis.opsForHash().get(ParkingRedisKeys.spaceHash(tenantId), String.valueOf(spaceId));
        return v == null ? null : String.valueOf(v);
    }

    public Map<Object, Object> allSpaceStatus(Long tenantId) {
        return redis.opsForHash().entries(ParkingRedisKeys.spaceHash(tenantId));
    }

    /* ---------- 欠费 ---------- */

    public void markArrears(Long tenantId, String plate, boolean arrears) {
        String p = plate.trim().toUpperCase();
        if (arrears) {
            redis.opsForSet().add(ParkingRedisKeys.arrears(tenantId), p);
        } else {
            redis.opsForSet().remove(ParkingRedisKeys.arrears(tenantId), p);
        }
    }

    public boolean hasArrears(Long tenantId, String plate) {
        Boolean m = redis.opsForSet().isMember(ParkingRedisKeys.arrears(tenantId), plate);
        return Boolean.TRUE.equals(m);
    }

    public Set<String> listWhite(Long tenantId) {
        return redis.opsForSet().members(ParkingRedisKeys.white(tenantId));
    }
}
