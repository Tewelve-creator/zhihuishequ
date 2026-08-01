package com.community.repair.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.community.common.constant.RoleConstants;
import com.community.common.desensitize.DesensitizeUtil;
import com.community.common.exception.BizException;
import com.community.common.tenant.TenantContext;
import com.community.repair.dto.*;
import com.community.repair.entity.*;
import com.community.repair.mapper.*;
import com.community.repair.parking.ParkingCacheService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 车辆停车管理：通行决策、车位占用、计费缴费、访客授权、挪车
 */
@Service
@RequiredArgsConstructor
public class ParkingService {

    private final ParkVehicleMapper vehicleMapper;
    private final ParkSpaceMapper spaceMapper;
    private final ParkAccessLogMapper accessLogMapper;
    private final ParkOrderMapper orderMapper;
    private final ParkVisitorAuthMapper visitorAuthMapper;
    private final ParkFeeRuleMapper feeRuleMapper;
    private final ResidentCarMapper residentCarMapper;
    private final ParkingCacheService cache;
    private final JdbcTemplate jdbc;

    /* ===================== 车辆台账 ===================== */

    public List<ParkVehicle> listVehicles(String keyword, String listType) {
        assertStaffOrResident();
        LambdaQueryWrapper<ParkVehicle> q = new LambdaQueryWrapper<ParkVehicle>()
                .eq(ParkVehicle::getBindStatus, 1)
                .orderByDesc(ParkVehicle::getId);
        if (RoleConstants.RESIDENT.equals(TenantContext.getRole())) {
            q.eq(ParkVehicle::getOwnerUserId, TenantContext.getUserId());
        }
        if (StringUtils.hasText(listType)) {
            q.eq(ParkVehicle::getListType, listType.trim().toUpperCase());
        }
        if (StringUtils.hasText(keyword)) {
            String k = keyword.trim();
            q.and(w -> w.like(ParkVehicle::getPlateNo, k)
                    .or().like(ParkVehicle::getOwnerName, k)
                    .or().like(ParkVehicle::getOwnerPhone, k));
        }
        return vehicleMapper.selectList(q);
    }

    @Transactional
    public ParkVehicle saveVehicle(ParkVehicleSaveRequest req) {
        boolean resident = isResident();
        if (resident) {
            // 业主仅可维护本人白名单车辆
            req.setListType("WHITE");
            req.setVehicleType("OWNER");
            req.setOwnerUserId(TenantContext.getUserId());
        } else {
            assertCanManage();
        }
        if (!StringUtils.hasText(req.getPlateNo())) {
            throw new BizException("请填写车牌号");
        }
        String plate = normalizePlate(req.getPlateNo());
        Long tid = requireTenant();
        LocalDateTime now = LocalDateTime.now();

        if (req.getId() != null) {
            ParkVehicle old = requireVehicle(req.getId());
            if (resident && !Objects.equals(old.getOwnerUserId(), TenantContext.getUserId())) {
                throw new BizException(403, "只能修改本人车辆");
            }
            old.setPlateNo(plate);
            old.setVehicleType(defaultStr(req.getVehicleType(), old.getVehicleType()));
            old.setListType(defaultStr(req.getListType(), old.getListType()));
            old.setHouseId(req.getHouseId() != null ? req.getHouseId() : old.getHouseId());
            old.setOwnerUserId(req.getOwnerUserId() != null ? req.getOwnerUserId() : old.getOwnerUserId());
            old.setOwnerName(req.getOwnerName());
            old.setOwnerPhone(req.getOwnerPhone());
            old.setSpaceId(req.getSpaceId());
            old.setBrandModel(req.getBrandModel());
            old.setColor(req.getColor());
            old.setRemark(req.getRemark());
            old.setUpdateTime(now);
            vehicleMapper.updateById(old);
            cache.warmVehicle(old);
            return old;
        }

        // 同车牌已存在：重新绑定
        ParkVehicle exist = vehicleMapper.selectOne(new LambdaQueryWrapper<ParkVehicle>()
                .eq(ParkVehicle::getPlateNo, plate).last("limit 1"));
        if (exist != null) {
            if (exist.getBindStatus() != null && exist.getBindStatus() == 1
                    && !Objects.equals(exist.getOwnerUserId(), TenantContext.getUserId())
                    && resident) {
                throw new BizException("该车牌已被其他用户绑定");
            }
            exist.setBindStatus(1);
            exist.setVehicleType(defaultStr(req.getVehicleType(), "OWNER"));
            exist.setListType(defaultStr(req.getListType(), "WHITE"));
            exist.setHouseId(req.getHouseId());
            exist.setOwnerUserId(req.getOwnerUserId() != null ? req.getOwnerUserId() : TenantContext.getUserId());
            exist.setOwnerName(req.getOwnerName());
            exist.setOwnerPhone(req.getOwnerPhone());
            exist.setSpaceId(req.getSpaceId());
            exist.setBrandModel(req.getBrandModel());
            exist.setColor(req.getColor());
            exist.setRemark(req.getRemark());
            exist.setUpdateTime(now);
            vehicleMapper.updateById(exist);
            cache.warmVehicle(exist);
            return exist;
        }

        ParkVehicle v = new ParkVehicle();
        v.setTenantId(tid);
        v.setPlateNo(plate);
        v.setVehicleType(defaultStr(req.getVehicleType(), "OWNER"));
        v.setListType(defaultStr(req.getListType(), "WHITE"));
        v.setHouseId(req.getHouseId());
        v.setOwnerUserId(req.getOwnerUserId() != null ? req.getOwnerUserId() : TenantContext.getUserId());
        v.setOwnerName(req.getOwnerName());
        v.setOwnerPhone(req.getOwnerPhone());
        v.setSpaceId(req.getSpaceId());
        v.setBrandModel(req.getBrandModel());
        v.setColor(req.getColor());
        v.setBindStatus(1);
        v.setRemark(req.getRemark());
        v.setCreateBy(TenantContext.getUserId());
        v.setCreateTime(now);
        vehicleMapper.insert(v);
        cache.warmVehicle(v);
        return v;
    }

    @Transactional
    public void unbindVehicle(Long id) {
        ParkVehicle v = requireVehicle(id);
        if (isResident() && !Objects.equals(v.getOwnerUserId(), TenantContext.getUserId())) {
            throw new BizException(403, "只能解绑本人车辆");
        }
        if (!isResident()) {
            assertCanManage();
        }
        v.setBindStatus(0);
        v.setUpdateTime(LocalDateTime.now());
        vehicleMapper.updateById(v);
        cache.removeFromAllLists(requireTenant(), v.getPlateNo());
    }

    /* ===================== 车位 ===================== */

    public List<ParkSpace> listSpaces(String areaCode, String status, String spaceType) {
        assertStaffOrResident();
        LambdaQueryWrapper<ParkSpace> q = new LambdaQueryWrapper<ParkSpace>().orderByAsc(ParkSpace::getSpaceNo);
        if (StringUtils.hasText(areaCode)) q.eq(ParkSpace::getAreaCode, areaCode);
        if (StringUtils.hasText(status)) q.eq(ParkSpace::getStatus, status);
        if (StringUtils.hasText(spaceType)) q.eq(ParkSpace::getSpaceType, spaceType);
        List<ParkSpace> list = spaceMapper.selectList(q);
        // 用 Redis 状态覆盖（检测器实时）
        Map<Object, Object> cached = cache.allSpaceStatus(requireTenant());
        if (cached != null && !cached.isEmpty()) {
            for (ParkSpace s : list) {
                Object st = cached.get(String.valueOf(s.getId()));
                if (st != null) s.setStatus(String.valueOf(st));
            }
        }
        enrichOwnerInfo(list);
        return list;
    }

    private void enrichOwnerInfo(List<ParkSpace> list) {
        if (list == null || list.isEmpty()) return;
        Long tid = requireTenant();
        try {
            List<Map<String, Object>> cars = jdbc.queryForList(
                    "select id, space_id, plate_no, owner_name from resident_car "
                            + "where tenant_id=? and status='ACTIVE'",
                    tid);
            Map<Long, Map<String, Object>> bySpace = new HashMap<>();
            Map<String, Map<String, Object>> byPlate = new HashMap<>();
            for (Map<String, Object> c : cars) {
                Long sid = toLong(c.get("space_id"));
                if (sid != null) bySpace.put(sid, c);
                String plate = c.get("plate_no") == null ? null : String.valueOf(c.get("plate_no"));
                if (StringUtils.hasText(plate)) byPlate.put(plate.toUpperCase(Locale.ROOT), c);
            }
            for (ParkSpace s : list) {
                Map<String, Object> hit = bySpace.get(s.getId());
                if (hit == null && StringUtils.hasText(s.getBindPlateNo())) {
                    hit = byPlate.get(s.getBindPlateNo().toUpperCase(Locale.ROOT));
                }
                if (hit != null) {
                    s.setOwnerName(hit.get("owner_name") == null ? null : String.valueOf(hit.get("owner_name")));
                    s.setBindCarId(toLong(hit.get("id")));
                    if (!StringUtils.hasText(s.getBindPlateNo()) && hit.get("plate_no") != null) {
                        s.setBindPlateNo(String.valueOf(hit.get("plate_no")));
                    }
                }
            }
        } catch (Exception ignored) {
            /* resident_car 表缺失时忽略 */
        }
    }

    /** 将业主车辆绑定到车位：写入车牌/业主，状态改为占用 */
    @Transactional
    public ParkSpace bindResidentCar(Long spaceId, Long carId) {
        assertCanManage();
        if (spaceId == null || carId == null) {
            throw new BizException(400, "请选择车位与车辆");
        }
        ParkSpace space = requireSpace(spaceId);
        ResidentCar car = residentCarMapper.selectById(carId);
        if (car == null || !Objects.equals(car.getTenantId(), requireTenant())) {
            throw new BizException(404, "车辆不存在");
        }
        if (!"ACTIVE".equalsIgnoreCase(defaultStr(car.getStatus(), "ACTIVE"))) {
            throw new BizException(400, "车辆未启用，无法绑定");
        }
        String plate = normalizePlate(car.getPlateNo());
        if (!StringUtils.hasText(plate)) {
            throw new BizException(400, "车辆车牌无效");
        }

        // 若该车已绑其他车位，先解绑
        if (car.getSpaceId() != null && !Objects.equals(car.getSpaceId(), spaceId)) {
            try {
                ParkSpace old = requireSpace(car.getSpaceId());
                clearSpaceBinding(old);
            } catch (Exception ignored) {
                /* 旧车位可能已删 */
            }
        }
        // 若本车位已绑其他车辆，清掉旧绑定
        try {
            List<ResidentCar> oldCars = residentCarMapper.selectList(new LambdaQueryWrapper<ResidentCar>()
                    .eq(ResidentCar::getTenantId, requireTenant())
                    .eq(ResidentCar::getSpaceId, spaceId));
            for (ResidentCar oc : oldCars) {
                if (!Objects.equals(oc.getId(), carId)) {
                    oc.setSpaceId(null);
                    oc.setSpaceNo(null);
                    oc.setUpdateTime(LocalDateTime.now());
                    residentCarMapper.updateById(oc);
                }
            }
        } catch (Exception ignored) {
            /* ignore */
        }

        car.setSpaceId(space.getId());
        car.setSpaceNo(space.getSpaceNo());
        car.setUpdateTime(LocalDateTime.now());
        residentCarMapper.updateById(car);

        space.setBindPlateNo(plate);
        space.setOwnerUserId(car.getUserId());
        space.setHouseId(car.getHouseId());
        space.setStatus("OCCUPIED");
        space.setOccupyingPlate(plate);
        space.setOccupyTime(LocalDateTime.now());
        space.setUpdateTime(LocalDateTime.now());
        spaceMapper.updateById(space);
        cache.setSpaceStatus(requireTenant(), space.getId(), "OCCUPIED");

        space.setOwnerName(car.getOwnerName());
        space.setBindCarId(car.getId());
        return space;
    }

    private void clearSpaceBinding(ParkSpace space) {
        if (space == null) return;
        space.setBindPlateNo(null);
        space.setOwnerUserId(null);
        space.setStatus("FREE");
        space.setOccupyingPlate(null);
        space.setOccupyTime(null);
        space.setUpdateTime(LocalDateTime.now());
        spaceMapper.updateById(space);
        cache.setSpaceStatus(requireTenant(), space.getId(), "FREE");
    }

    /** 空余车位分布图数据 */
    public Map<String, Object> spaceMap() {
        assertStaffOrResident();
        List<ParkSpace> all = listSpaces(null, null, null);
        long free = all.stream().filter(s -> "FREE".equals(s.getStatus())).count();
        long occupied = all.stream().filter(s -> "OCCUPIED".equals(s.getStatus())).count();
        Map<String, Object> m = new HashMap<>();
        m.put("total", all.size());
        m.put("free", free);
        m.put("occupied", occupied);
        m.put("spaces", all);
        return m;
    }

    @Transactional
    public ParkSpace saveSpace(ParkSpaceSaveRequest req) {
        assertCanManage();
        if (!StringUtils.hasText(req.getSpaceNo())) throw new BizException("请填写车位编号");
        LocalDateTime now = LocalDateTime.now();
        if (req.getId() != null) {
            ParkSpace s = requireSpace(req.getId());
            fillSpace(s, req);
            s.setUpdateTime(now);
            spaceMapper.updateById(s);
            cache.setSpaceStatus(requireTenant(), s.getId(), s.getStatus());
            return s;
        }
        ParkSpace s = new ParkSpace();
        s.setTenantId(requireTenant());
        fillSpace(s, req);
        s.setStatus("FREE");
        s.setCreateTime(now);
        spaceMapper.insert(s);
        cache.setSpaceStatus(requireTenant(), s.getId(), "FREE");
        return s;
    }

    private void fillSpace(ParkSpace s, ParkSpaceSaveRequest req) {
        s.setSpaceNo(req.getSpaceNo().trim());
        s.setAreaCode(req.getAreaCode());
        s.setSpaceType(defaultStr(req.getSpaceType(), "PUBLIC"));
        s.setOwnerUserId(req.getOwnerUserId());
        s.setHouseId(req.getHouseId());
        s.setBindPlateNo(StringUtils.hasText(req.getBindPlateNo()) ? normalizePlate(req.getBindPlateNo()) : null);
        s.setDetectorType(req.getDetectorType());
        s.setDetectorId(req.getDetectorId());
        s.setMapX(req.getMapX());
        s.setMapY(req.getMapY());
        s.setAntiOccupy(req.getAntiOccupy() == null ? 0 : req.getAntiOccupy());
        s.setRemark(req.getRemark());
        if (StringUtils.hasText(req.getStatus())) {
            s.setStatus(req.getStatus().trim().toUpperCase());
        }
    }

    @Transactional
    public void deleteSpace(Long id) {
        assertCanManage();
        ParkSpace s = requireSpace(id);
        if ("OCCUPIED".equals(s.getStatus())) {
            throw new BizException("车位占用中，不可删除");
        }
        spaceMapper.deleteById(id);
    }

    /** 地磁 / AI 视频上报车位占用；固定车位被外来车占用则告警 */
    @Transactional
    public ParkSpace reportOccupy(ParkSpaceOccupyRequest req) {
        assertStaffView();
        ParkSpace space = null;
        if (req.getSpaceId() != null) {
            space = requireSpace(req.getSpaceId());
        } else if (StringUtils.hasText(req.getSpaceNo())) {
            space = spaceMapper.selectOne(new LambdaQueryWrapper<ParkSpace>()
                    .eq(ParkSpace::getSpaceNo, req.getSpaceNo().trim()).last("limit 1"));
        }
        if (space == null) throw new BizException(404, "车位不存在");

        String status = defaultStr(req.getStatus(), "OCCUPIED").toUpperCase();
        String plate = StringUtils.hasText(req.getOccupyingPlate()) ? normalizePlate(req.getOccupyingPlate()) : null;
        space.setStatus(status);
        space.setOccupyingPlate("OCCUPIED".equals(status) ? plate : null);
        space.setOccupyTime("OCCUPIED".equals(status) ? LocalDateTime.now() : null);
        space.setUpdateTime(LocalDateTime.now());
        spaceMapper.updateById(space);
        cache.setSpaceStatus(requireTenant(), space.getId(), status);

        // 防占用：外来车牌占用产权/租赁车位
        if ("OCCUPIED".equals(status)
                && space.getAntiOccupy() != null && space.getAntiOccupy() == 1
                && StringUtils.hasText(space.getBindPlateNo())
                && StringUtils.hasText(plate)
                && !space.getBindPlateNo().equalsIgnoreCase(plate)) {
            notifyAntiOccupy(space, plate);
        }
        return space;
    }

    private void notifyAntiOccupy(ParkSpace space, String plate) {
        String title = "车位被占用提醒";
        String content = "您的车位 " + space.getSpaceNo() + " 被外来车辆 " + plate + " 占用，请及时联系挪车。";
        Long tid = requireTenant();
        // 通知车位业主
        if (space.getOwnerUserId() != null) {
            insertMessage(tid, space.getOwnerUserId(), title, content, "park_occupy");
        }
        // 通知物业管理员
        try {
            List<Map<String, Object>> admins = jdbc.queryForList(
                    "select id from sys_user where tenant_id=? and role='property_admin' and status=1", tid);
            for (Map<String, Object> a : admins) {
                Long uid = ((Number) a.get("id")).longValue();
                insertMessage(tid, uid, "车位防占用告警",
                        "车位 " + space.getSpaceNo() + " 被 " + plate + " 占用（绑定车牌 "
                                + space.getBindPlateNo() + "）", "park_occupy");
            }
        } catch (Exception ignored) {
        }
    }

    /* ===================== 道闸通行 ===================== */

    /**
     * 车牌识别抬杆决策：黑名单拒 → 绿通优 → 欠费拦 → 白名单放 → 访客授权放 → 否则拒（或临停建单）
     */
    @Transactional
    public Map<String, Object> gatePass(ParkGatePassRequest req) {
        // 道闸设备回调：允许工作人员或携带租户头的系统调用
        assertStaffView();
        if (!StringUtils.hasText(req.getPlateNo())) throw new BizException("缺少车牌");
        String plate = normalizePlate(req.getPlateNo());
        String direction = defaultStr(req.getDirection(), "IN").toUpperCase();
        Long tid = requireTenant();
        LocalDateTime now = LocalDateTime.now();

        boolean allow = false;
        String reason;
        Long vehicleId = null;
        Long authId = null;
        Long orderId = null;

        if (cache.isBlack(tid, plate) || isDbList(plate, "BLACK")) {
            allow = false;
            reason = "黑名单禁止入场";
        } else if (cache.isGreen(tid, plate) || isDbList(plate, "GREEN")) {
            allow = true;
            reason = "绿色通道优先放行";
            vehicleId = findVehicleId(plate);
        } else if ("IN".equals(direction) && (cache.hasArrears(tid, plate) || hasUnpaidOrder(plate)
                || hasMonthlyParkingArrears(plate) || isAccessRestricted(plate))) {
            allow = false;
            reason = "存在欠费或限行，结清/恢复后方可通行";
        } else if (cache.isWhite(tid, plate) || isDbList(plate, "WHITE")) {
            allow = true;
            reason = "业主白名单无感抬杆";
            vehicleId = findVehicleId(plate);
        } else {
            Map<String, Object> visitor = cache.getVisitorAuth(tid, plate);
            ParkVisitorAuth dbAuth = null;
            if (visitor == null) {
                dbAuth = visitorAuthMapper.selectOne(new LambdaQueryWrapper<ParkVisitorAuth>()
                        .eq(ParkVisitorAuth::getPlateNo, plate)
                        .eq(ParkVisitorAuth::getStatus, "ACTIVE")
                        .ge(ParkVisitorAuth::getEndTime, now)
                        .le(ParkVisitorAuth::getStartTime, now)
                        .last("limit 1"));
                if (dbAuth != null) cache.cacheVisitorAuth(dbAuth);
            }
            if (visitor != null || dbAuth != null) {
                allow = true;
                reason = "访客临时授权通行";
                authId = visitor != null ? toLong(visitor.get("authId")) : dbAuth.getId();
                bumpVisitorUsage(authId);
            } else if ("IN".equals(direction)) {
                // 公共临停：建单后放行
                ParkOrder order = createParkingOrder(plate, null);
                orderId = order.getId();
                allow = true;
                reason = "临停计费进场";
            } else {
                allow = false;
                reason = "无有效通行权限";
            }
        }

        // 离场：结算订单
        if (allow && "OUT".equals(direction)) {
            ParkOrder open = findOpenOrder(plate);
            if (open != null) {
                settleOrderOnExit(open);
                orderId = open.getId();
                if ("UNPAID".equals(open.getStatus()) && open.getAmount().compareTo(BigDecimal.ZERO) > 0) {
                    // 演示：未缴费也先抬杆，标记欠费；也可改为 DENY
                    cache.markArrears(tid, plate, true);
                    reason = reason + "（待缴费¥" + open.getAmount() + "）";
                }
            }
        }
        if (allow && "IN".equals(direction) && orderId == null && !cache.isWhite(tid, plate) && !cache.isGreen(tid, plate)) {
            // 白名单业主通常不计临停费；访客/临停已建单
        }

        ParkAccessLog log = new ParkAccessLog();
        log.setTenantId(tid);
        log.setPlateNo(plate);
        log.setDirection(direction);
        log.setGateCode(req.getGateCode());
        log.setGateName(req.getGateName());
        log.setPassResult(allow ? "ALLOW" : "DENY");
        log.setPassReason(reason);
        log.setVehicleId(vehicleId);
        log.setAuthId(authId);
        log.setOrderId(orderId);
        log.setSnapUrl(req.getSnapUrl());
        log.setPassTime(now);
        log.setCreateTime(now);
        accessLogMapper.insert(log);

        Map<String, Object> resp = new HashMap<>();
        resp.put("allow", allow);
        resp.put("passResult", log.getPassResult());
        resp.put("passReason", reason);
        resp.put("plateNo", plate);
        resp.put("logId", log.getId());
        resp.put("orderId", orderId);
        resp.put("openBarrier", allow);
        return resp;
    }

    /* ===================== 访客授权 ===================== */

    @Transactional
    public ParkVisitorAuth createVisitorAuth(ParkVisitorAuthRequest req) {
        assertStaffOrResident();
        Long tid = requireTenant();
        int hours = req.getValidHours() == null || req.getValidHours() <= 0 ? 4 : req.getValidHours();
        LocalDateTime now = LocalDateTime.now();
        ParkVisitorAuth auth = new ParkVisitorAuth();
        auth.setTenantId(tid);
        if (StringUtils.hasText(req.getPlateNo())) {
            auth.setPlateNo(normalizePlate(req.getPlateNo()));
        }
        auth.setAuthCode(genCode("V"));
        auth.setQrToken(UUID.randomUUID().toString().replace("-", ""));
        auth.setOwnerUserId(TenantContext.getUserId());
        auth.setHouseId(req.getHouseId() != null ? req.getHouseId() : TenantContext.getHouseId());
        auth.setStartTime(now);
        auth.setEndTime(now.plusHours(hours));
        auth.setStatus("ACTIVE");
        auth.setMaxEntries(req.getMaxEntries() == null ? 2 : req.getMaxEntries());
        auth.setUsedEntries(0);
        auth.setSource(defaultStr(req.getSource(), isResident() ? "OWNER" : "GATE"));
        auth.setRemark(req.getRemark());
        auth.setCreateTime(now);
        visitorAuthMapper.insert(auth);
        if (StringUtils.hasText(auth.getPlateNo())) {
            cache.cacheVisitorAuth(auth);
        }
        return auth;
    }

    /** 访客自助扫码补录车牌 */
    @Transactional
    public ParkVisitorAuth selfRegister(ParkVisitorAuthRequest req) {
        if (!StringUtils.hasText(req.getQrToken()) || !StringUtils.hasText(req.getPlateNo())) {
            throw new BizException("请提供二维码令牌与车牌");
        }
        ParkVisitorAuth auth = visitorAuthMapper.selectOne(new LambdaQueryWrapper<ParkVisitorAuth>()
                .eq(ParkVisitorAuth::getQrToken, req.getQrToken().trim()).last("limit 1"));
        if (auth == null) throw new BizException(404, "授权二维码无效");
        if (!"ACTIVE".equals(auth.getStatus()) || auth.getEndTime().isBefore(LocalDateTime.now())) {
            throw new BizException("授权已失效");
        }
        auth.setPlateNo(normalizePlate(req.getPlateNo()));
        auth.setSource("SELF");
        auth.setUpdateTime(LocalDateTime.now());
        visitorAuthMapper.updateById(auth);
        cache.cacheVisitorAuth(auth);
        return auth;
    }

    public List<ParkVisitorAuth> listVisitorAuth(String status) {
        assertStaffOrResident();
        LambdaQueryWrapper<ParkVisitorAuth> q = new LambdaQueryWrapper<ParkVisitorAuth>()
                .orderByDesc(ParkVisitorAuth::getId);
        if (isResident()) {
            q.eq(ParkVisitorAuth::getOwnerUserId, TenantContext.getUserId());
        }
        if (StringUtils.hasText(status)) q.eq(ParkVisitorAuth::getStatus, status);
        return visitorAuthMapper.selectList(q);
    }

    @Transactional
    public void revokeVisitorAuth(Long id) {
        ParkVisitorAuth auth = visitorAuthMapper.selectById(id);
        if (auth == null) throw new BizException(404, "授权不存在");
        if (isResident()
                && !Objects.equals(auth.getOwnerUserId(), TenantContext.getUserId())) {
            throw new BizException(403, "无权撤销");
        }
        auth.setStatus("REVOKED");
        auth.setUpdateTime(LocalDateTime.now());
        visitorAuthMapper.updateById(auth);
        if (StringUtils.hasText(auth.getPlateNo())) {
            cache.evictVisitorAuth(requireTenant(), auth.getPlateNo());
        }
    }

    /**
     * 访客预约驾车：写入临时通行授权 + 临时通行记录（预约登记）。
     * 未到访开始时间不写 Redis，避免提前抬杆。
     */
    @Transactional
    public void syncFromVisitAppointment(VisitorAppointment visit) {
        if (visit == null || visit.getId() == null) return;
        if (visit.getDriveFlag() == null || visit.getDriveFlag() != 1) return;
        if (!StringUtils.hasText(visit.getPlateNo())) return;

        Long tid = visit.getTenantId() != null ? visit.getTenantId() : requireTenant();
        String plate = normalizePlate(visit.getPlateNo());
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime start = visit.getVisitTime() != null ? visit.getVisitTime() : now;
        LocalDateTime end = visit.getExpireTime() != null ? visit.getExpireTime() : start.plusHours(12);
        if (!end.isAfter(start)) {
            end = start.plusHours(12);
        }

        // 同预约重复同步时先撤销旧授权
        revokeAuthByVisitId(tid, visit.getId(), false);

        ParkVisitorAuth auth = new ParkVisitorAuth();
        auth.setTenantId(tid);
        auth.setPlateNo(plate);
        auth.setAuthCode(genCode("V"));
        auth.setQrToken(UUID.randomUUID().toString().replace("-", ""));
        auth.setOwnerUserId(visit.getUserId());
        auth.setHouseId(visit.getHouseId());
        auth.setStartTime(start);
        auth.setEndTime(end);
        auth.setStatus("ACTIVE");
        int entries = visit.getVisitorCount() == null ? 2 : Math.max(2, visit.getVisitorCount());
        auth.setMaxEntries(entries);
        auth.setUsedEntries(0);
        auth.setSource("VISIT");
        auth.setRemark(visitRemark(visit.getId(), visit.getVisitorName(), visit.getVisitReason()));
        auth.setCreateTime(now);
        visitorAuthMapper.insert(auth);
        if (!start.isAfter(now)) {
            cache.cacheVisitorAuth(auth);
        }

        ParkAccessLog log = new ParkAccessLog();
        log.setTenantId(tid);
        log.setPlateNo(plate);
        log.setDirection("IN");
        log.setGateCode("VISIT");
        log.setGateName("访客预约");
        log.setPassResult("ALLOW");
        log.setPassReason("访客预约登记 · " + defaultStr(visit.getVisitorName(), "访客")
                + (StringUtils.hasText(visit.getVisitReason()) ? " · " + visit.getVisitReason() : "")
                + " · 有效至 " + end.format(DateTimeFormatter.ofPattern("MM-dd HH:mm")));
        log.setAuthId(auth.getId());
        log.setPassTime(now);
        log.setCreateTime(now);
        accessLogMapper.insert(log);
    }

    /** 预约撤销/删除时作废对应临时车牌授权 */
    @Transactional
    public void revokeFromVisitAppointment(VisitorAppointment visit) {
        if (visit == null || visit.getId() == null) return;
        Long tid = visit.getTenantId() != null ? visit.getTenantId() : TenantContext.getTenantId();
        revokeAuthByVisitId(tid, visit.getId(), true);
    }

    private void revokeAuthByVisitId(Long tid, Long visitId, boolean writeLog) {
        if (tid == null || visitId == null) return;
        String marker = "visitId=" + visitId;
        List<ParkVisitorAuth> list = visitorAuthMapper.selectList(new LambdaQueryWrapper<ParkVisitorAuth>()
                .eq(ParkVisitorAuth::getTenantId, tid)
                .eq(ParkVisitorAuth::getSource, "VISIT")
                .eq(ParkVisitorAuth::getStatus, "ACTIVE")
                .like(ParkVisitorAuth::getRemark, marker));
        LocalDateTime now = LocalDateTime.now();
        for (ParkVisitorAuth auth : list) {
            auth.setStatus("REVOKED");
            auth.setUpdateTime(now);
            visitorAuthMapper.updateById(auth);
            if (StringUtils.hasText(auth.getPlateNo())) {
                cache.evictVisitorAuth(tid, auth.getPlateNo());
            }
            if (writeLog && StringUtils.hasText(auth.getPlateNo())) {
                ParkAccessLog log = new ParkAccessLog();
                log.setTenantId(tid);
                log.setPlateNo(auth.getPlateNo());
                log.setDirection("OUT");
                log.setGateCode("VISIT");
                log.setGateName("访客预约");
                log.setPassResult("DENY");
                log.setPassReason("访客预约已撤销/删除，临时车牌授权失效");
                log.setAuthId(auth.getId());
                log.setPassTime(now);
                log.setCreateTime(now);
                accessLogMapper.insert(log);
            }
        }
    }

    private String visitRemark(Long visitId, String visitorName, String reason) {
        return "visitId=" + visitId
                + " | " + defaultStr(visitorName, "访客")
                + (StringUtils.hasText(reason) ? " | " + reason : "");
    }

    /* ===================== 订单 / 缴费 ===================== */

    public List<ParkOrder> listOrders(String plateNo, String status) {
        assertStaffOrResident();
        LambdaQueryWrapper<ParkOrder> q = new LambdaQueryWrapper<ParkOrder>().orderByDesc(ParkOrder::getId);
        if (StringUtils.hasText(plateNo)) q.eq(ParkOrder::getPlateNo, normalizePlate(plateNo));
        if (StringUtils.hasText(status)) q.eq(ParkOrder::getStatus, status);
        return orderMapper.selectList(q);
    }

    public ParkOrder calcFee(String plateNo) {
        assertStaffOrResident();
        ParkOrder open = findOpenOrder(normalizePlate(plateNo));
        if (open == null) throw new BizException("无在场停车订单");
        settleOrderOnExit(open);
        return open;
    }

    @Transactional
    public ParkOrder pay(ParkPayRequest req) {
        assertStaffOrResident();
        ParkOrder order;
        if (req.getOrderId() != null) {
            order = orderMapper.selectById(req.getOrderId());
        } else if (StringUtils.hasText(req.getPlateNo())) {
            order = findOpenOrder(normalizePlate(req.getPlateNo()));
            if (order == null) {
                order = orderMapper.selectOne(new LambdaQueryWrapper<ParkOrder>()
                        .eq(ParkOrder::getPlateNo, normalizePlate(req.getPlateNo()))
                        .eq(ParkOrder::getStatus, "UNPAID")
                        .orderByDesc(ParkOrder::getId).last("limit 1"));
            }
        } else {
            throw new BizException("请指定订单或车牌");
        }
        if (order == null) throw new BizException(404, "订单不存在");
        if ("PAID".equals(order.getStatus()) || "CLOSED".equals(order.getStatus())) {
            return order;
        }
        if ("PARKING".equals(order.getStatus())) {
            settleOrderOnExit(order);
        }
        order.setPaidAmount(order.getAmount());
        order.setStatus("PAID");
        order.setPayChannel(defaultStr(req.getPayChannel(), "MINI"));
        order.setPayTime(LocalDateTime.now());
        order.setArrearsFlag(0);
        order.setUpdateTime(LocalDateTime.now());
        orderMapper.updateById(order);
        cache.markArrears(requireTenant(), order.getPlateNo(), false);
        return order;
    }

    /* ===================== 通行日志 / 挪车 / 统计 ===================== */

    public List<ParkAccessLog> listAccessLogs(String plateNo, String from, String to,
                                              String direction, String passResult) {
        assertStaffView();
        LambdaQueryWrapper<ParkAccessLog> q = new LambdaQueryWrapper<ParkAccessLog>()
                .orderByDesc(ParkAccessLog::getPassTime)
                .last("limit 500");
        if (StringUtils.hasText(plateNo)) q.eq(ParkAccessLog::getPlateNo, normalizePlate(plateNo));
        if (StringUtils.hasText(direction)) q.eq(ParkAccessLog::getDirection, direction.trim().toUpperCase());
        if (StringUtils.hasText(passResult)) q.eq(ParkAccessLog::getPassResult, passResult.trim().toUpperCase());
        if (StringUtils.hasText(from)) {
            try {
                q.ge(ParkAccessLog::getPassTime, LocalDateTime.parse(from.replace(" ", "T")));
            } catch (Exception ignored) {
            }
        }
        if (StringUtils.hasText(to)) {
            try {
                q.le(ParkAccessLog::getPassTime, LocalDateTime.parse(to.replace(" ", "T")));
            } catch (Exception ignored) {
            }
        }
        return accessLogMapper.selectList(q);
    }

    /**
     * 停车收费台账：月租(fee_bill PARKING) + 临停(park_order)
     * feeKind: MONTHLY | TEMP | ALL
     */
    public List<Map<String, Object>> feeLedger(String feeKind, String keyword, String status) {
        assertStaffView();
        Long tid = requireTenant();
        String kind = StringUtils.hasText(feeKind) ? feeKind.trim().toUpperCase() : "ALL";
        List<Map<String, Object>> out = new ArrayList<>();

        if ("ALL".equals(kind) || "MONTHLY".equals(kind)) {
            try {
                StringBuilder sql = new StringBuilder(
                        "select id, plate_no as plateNo, house_label as houseLabel, amount, period, "
                                + "due_date as dueDate, status, paid_time as paidTime, create_time as createTime, "
                                + "'MONTHLY' as feeKind, '月租' as feeKindLabel "
                                + "from fee_bill where tenant_id=? and fee_type='PARKING' ");
                List<Object> args = new ArrayList<>();
                args.add(tid);
                if (StringUtils.hasText(status)) {
                    sql.append("and status=? ");
                    args.add(status.trim().toUpperCase());
                }
                if (StringUtils.hasText(keyword)) {
                    sql.append("and (plate_no like ? or house_label like ? or period like ?) ");
                    String k = "%" + keyword.trim() + "%";
                    args.add(k); args.add(k); args.add(k);
                }
                sql.append("order by id desc limit 300");
                List<Map<String, Object>> rows = jdbc.queryForList(sql.toString(), args.toArray());
                for (Map<String, Object> r : rows) {
                    String st = str(r.get("status"));
                    r.put("statusLabel", switch (st) {
                        case "PAID" -> "已缴费";
                        case "OVERDUE" -> "已逾期";
                        default -> "待缴费";
                    });
                    fmtMapTime(r, "dueDate");
                    fmtMapTime(r, "paidTime");
                    fmtMapTime(r, "createTime");
                    out.add(r);
                }
            } catch (Exception ignored) {
            }
        }

        if ("ALL".equals(kind) || "TEMP".equals(kind)) {
            LambdaQueryWrapper<ParkOrder> q = new LambdaQueryWrapper<ParkOrder>().orderByDesc(ParkOrder::getId).last("limit 300");
            if (StringUtils.hasText(status)) {
                String st = status.trim().toUpperCase();
                if ("UNPAID".equals(st) || "PAID".equals(st) || "PARKING".equals(st) || "CLOSED".equals(st)) {
                    q.eq(ParkOrder::getStatus, st);
                }
            }
            if (StringUtils.hasText(keyword)) {
                q.like(ParkOrder::getPlateNo, keyword.trim());
            }
            for (ParkOrder o : orderMapper.selectList(q)) {
                Map<String, Object> r = new LinkedHashMap<>();
                r.put("id", o.getId());
                r.put("orderNo", o.getOrderNo());
                r.put("plateNo", o.getPlateNo());
                r.put("amount", o.getAmount());
                r.put("paidAmount", o.getPaidAmount());
                r.put("status", o.getStatus());
                r.put("statusLabel", switch (o.getStatus() == null ? "" : o.getStatus()) {
                    case "PAID" -> "已缴费";
                    case "UNPAID" -> "待缴费";
                    case "PARKING" -> "在场计费";
                    default -> "已关闭";
                });
                r.put("feeKind", "TEMP");
                r.put("feeKindLabel", "临时停车");
                r.put("period", o.getEnterTime() != null ? o.getEnterTime().toString().replace('T', ' ').substring(0, 16) : null);
                r.put("enterTime", o.getEnterTime());
                r.put("exitTime", o.getExitTime());
                r.put("payTime", o.getPayTime());
                r.put("durationMinutes", o.getDurationMinutes());
                r.put("createTime", o.getCreateTime());
                out.add(r);
            }
        }
        out.sort((a, b) -> {
            Object ia = a.get("id");
            Object ib = b.get("id");
            long la = ia instanceof Number ? ((Number) ia).longValue() : 0;
            long lb = ib instanceof Number ? ((Number) ib).longValue() : 0;
            return Long.compare(lb, la);
        });
        return out.size() > 400 ? out.subList(0, 400) : out;
    }

    /** 同步长期月租欠费 → 限行标记与道闸欠费缓存（欠费超过30天） */
    public Map<String, Object> syncArrearsRestrict() {
        assertCanManage();
        Long tid = requireTenant();
        int marked = 0;
        try {
            List<Map<String, Object>> rows = jdbc.queryForList(
                    "select distinct plate_no as plateNo from fee_bill "
                            + "where tenant_id=? and fee_type='PARKING' and status in ('UNPAID','OVERDUE') "
                            + "and plate_no is not null and plate_no<>'' "
                            + "and due_date is not null and due_date < date_sub(curdate(), interval 30 day)",
                    tid);
            for (Map<String, Object> r : rows) {
                String plate = normalizePlate(String.valueOf(r.get("plateNo")));
                if (!StringUtils.hasText(plate)) continue;
                cache.markArrears(tid, plate, true);
                jdbc.update(
                        "update resident_car set access_restricted=1, update_time=now() where tenant_id=? and plate_no=? and status='ACTIVE'",
                        tid, plate);
                marked++;
            }
        } catch (Exception e) {
            throw new BizException("同步欠费限行失败：" + e.getMessage());
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("marked", marked);
        m.put("message", "已同步 " + marked + " 个长期欠费车牌限行");
        return m;
    }

    private void fmtMapTime(Map<String, Object> r, String key) {
        Object v = r.get(key);
        if (v != null) {
            String s = String.valueOf(v).replace('T', ' ');
            r.put(key, s.length() > 19 ? s.substring(0, 19) : s);
        }
    }

    /** 匿名一键挪车：不暴露车主完整手机号 */
    @Transactional
    public Map<String, Object> moveCar(ParkMoveCarRequest req) {
        assertStaffOrResident();
        ParkVehicle vehicle = null;
        ParkSpace space = null;
        if (StringUtils.hasText(req.getSpaceNo())) {
            space = spaceMapper.selectOne(new LambdaQueryWrapper<ParkSpace>()
                    .eq(ParkSpace::getSpaceNo, req.getSpaceNo().trim()).last("limit 1"));
            if (space != null && StringUtils.hasText(space.getOccupyingPlate())) {
                vehicle = findVehicle(space.getOccupyingPlate());
            } else if (space != null && StringUtils.hasText(space.getBindPlateNo())) {
                vehicle = findVehicle(space.getBindPlateNo());
            }
        }
        if (vehicle == null && StringUtils.hasText(req.getPlateNo())) {
            vehicle = findVehicle(normalizePlate(req.getPlateNo()));
        }
        if (vehicle == null || vehicle.getOwnerUserId() == null) {
            throw new BizException("未找到可通知的车主");
        }
        String masked = DesensitizeUtil.phone(vehicle.getOwnerPhone());
        String msg = StringUtils.hasText(req.getMessage()) ? req.getMessage() : "您好，有人请您挪车，请尽快处理。";
        insertMessage(requireTenant(), vehicle.getOwnerUserId(), "挪车提醒", msg, "park_move");
        // 物业侧可见完整信息用于跟进
        if (RoleConstants.isPropertyAdmin(TenantContext.getRole()) || RoleConstants.isPlatformAdmin(TenantContext.getRole())) {
            Map<String, Object> m = new HashMap<>();
            m.put("notified", true);
            m.put("ownerPhone", vehicle.getOwnerPhone());
            m.put("plateNo", vehicle.getPlateNo());
            return m;
        }
        Map<String, Object> m = new HashMap<>();
        m.put("notified", true);
        m.put("ownerPhoneMasked", masked);
        m.put("tip", "已通知车主，对方手机号已脱敏保护");
        return m;
    }

    public Map<String, Object> stats() {
        assertStaffView();
        Long tid = requireTenant();
        Map<String, Object> m = new HashMap<>();
        m.put("vehicleCount", vehicleMapper.selectCount(new LambdaQueryWrapper<ParkVehicle>().eq(ParkVehicle::getBindStatus, 1)));
        m.put("spaceFree", spaceMapper.selectCount(new LambdaQueryWrapper<ParkSpace>().eq(ParkSpace::getStatus, "FREE")));
        m.put("spaceOccupied", spaceMapper.selectCount(new LambdaQueryWrapper<ParkSpace>().eq(ParkSpace::getStatus, "OCCUPIED")));
        m.put("parkingNow", orderMapper.selectCount(new LambdaQueryWrapper<ParkOrder>().eq(ParkOrder::getStatus, "PARKING")));
        m.put("unpaidCount", orderMapper.selectCount(new LambdaQueryWrapper<ParkOrder>().eq(ParkOrder::getStatus, "UNPAID")));
        try {
            BigDecimal todayAmt = jdbc.queryForObject(
                    "select ifnull(sum(paid_amount),0) from park_order where tenant_id=? and status='PAID' and date(pay_time)=curdate()",
                    BigDecimal.class, tid);
            m.put("todayIncome", todayAmt);
        } catch (Exception e) {
            m.put("todayIncome", BigDecimal.ZERO);
        }
        m.put("todayPass", accessLogMapper.selectCount(new LambdaQueryWrapper<ParkAccessLog>()
                .ge(ParkAccessLog::getPassTime, LocalDateTime.now().toLocalDate().atStartOfDay())));
        return m;
    }

    /** 启动后/管理端触发：把 DB 名单预热进 Redis */
    public Map<String, Object> warmCache() {
        assertCanManage();
        Long tid = requireTenant();
        List<ParkVehicle> list = vehicleMapper.selectList(new LambdaQueryWrapper<ParkVehicle>()
                .eq(ParkVehicle::getBindStatus, 1));
        for (ParkVehicle v : list) cache.warmVehicle(v);
        List<ParkSpace> spaces = spaceMapper.selectList(null);
        for (ParkSpace s : spaces) cache.setSpaceStatus(tid, s.getId(), s.getStatus());
        List<ParkOrder> unpaid = orderMapper.selectList(new LambdaQueryWrapper<ParkOrder>()
                .eq(ParkOrder::getStatus, "UNPAID"));
        for (ParkOrder o : unpaid) cache.markArrears(tid, o.getPlateNo(), true);
        int monthly = 0;
        try {
            List<Map<String, Object>> rows = jdbc.queryForList(
                    "select distinct plate_no as plateNo from fee_bill "
                            + "where tenant_id=? and fee_type='PARKING' and status in ('UNPAID','OVERDUE') "
                            + "and plate_no is not null and plate_no<>'' "
                            + "and due_date is not null and due_date < date_sub(curdate(), interval 30 day)",
                    tid);
            for (Map<String, Object> r : rows) {
                String plate = normalizePlate(String.valueOf(r.get("plateNo")));
                cache.markArrears(tid, plate, true);
                monthly++;
            }
        } catch (Exception ignored) {
        }
        Map<String, Object> m = new HashMap<>();
        m.put("vehicles", list.size());
        m.put("spaces", spaces.size());
        m.put("arrears", unpaid.size());
        m.put("monthlyArrears", monthly);
        return m;
    }

    /* ===================== 计费 ===================== */

    private ParkOrder createParkingOrder(String plate, Long spaceId) {
        ParkFeeRule rule = currentRule();
        ParkOrder o = new ParkOrder();
        o.setTenantId(requireTenant());
        o.setOrderNo(genCode("P"));
        o.setPlateNo(plate);
        o.setVehicleId(findVehicleId(plate));
        o.setSpaceId(spaceId);
        o.setEnterTime(LocalDateTime.now());
        o.setFreeMinutes(rule.getFreeMinutes() == null ? 15 : rule.getFreeMinutes());
        o.setAmount(BigDecimal.ZERO);
        o.setPaidAmount(BigDecimal.ZERO);
        o.setStatus("PARKING");
        o.setArrearsFlag(0);
        o.setCreateTime(LocalDateTime.now());
        orderMapper.insert(o);
        return o;
    }

    private void settleOrderOnExit(ParkOrder order) {
        if (order.getExitTime() == null) {
            order.setExitTime(LocalDateTime.now());
        }
        ParkFeeRule rule = currentRule();
        long minutes = Duration.between(order.getEnterTime(), order.getExitTime()).toMinutes();
        if (minutes < 0) minutes = 0;
        order.setDurationMinutes((int) minutes);
        int free = rule.getFreeMinutes() == null ? 15 : rule.getFreeMinutes();
        BigDecimal amount = BigDecimal.ZERO;
        String detail;
        if (minutes <= free) {
            detail = "免费时长内";
        } else {
            amount = calcAmount(minutes, rule, order.getEnterTime(), order.getExitTime());
            detail = "计费" + minutes + "分钟";
        }
        // 白名单业主月租车：金额置零
        if (cache.isWhite(requireTenant(), order.getPlateNo())) {
            amount = BigDecimal.ZERO;
            detail = "业主月租/白名单免计费";
            order.setStatus("CLOSED");
        } else if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            order.setStatus("CLOSED");
        } else {
            order.setStatus("UNPAID");
            order.setArrearsFlag(1);
        }
        order.setAmount(amount.setScale(2, RoundingMode.HALF_UP));
        order.setFeeDetail(detail);
        order.setUpdateTime(LocalDateTime.now());
        orderMapper.updateById(order);
    }

    private BigDecimal calcAmount(long minutes, ParkFeeRule rule, LocalDateTime enter, LocalDateTime exit) {
        BigDecimal first = rule.getFirstHourFee() == null ? new BigDecimal("5") : rule.getFirstHourFee();
        int stepMin = rule.getStepMinutes() == null ? 60 : rule.getStepMinutes();
        BigDecimal stepFee = rule.getStepFee() == null ? new BigDecimal("3") : rule.getStepFee();
        BigDecimal cap = rule.getDayCap() == null ? new BigDecimal("40") : rule.getDayCap();

        long billable = Math.max(0, minutes);
        BigDecimal amount = first;
        if (billable > 60) {
            long extra = billable - 60;
            long steps = (extra + stepMin - 1) / stepMin;
            amount = amount.add(stepFee.multiply(BigDecimal.valueOf(steps)));
        }
        if (amount.compareTo(cap) > 0) amount = cap;

        // 夜间优惠：离场落在夜间时段则打折
        if (rule.getNightDiscount() != null && isNight(exit.toLocalTime(), rule.getNightStart(), rule.getNightEnd())) {
            amount = amount.multiply(rule.getNightDiscount());
        }
        return amount;
    }

    private boolean isNight(LocalTime t, String start, String end) {
        try {
            LocalTime s = LocalTime.parse(defaultStr(start, "22:00"));
            LocalTime e = LocalTime.parse(defaultStr(end, "07:00"));
            if (s.isBefore(e)) {
                return !t.isBefore(s) && t.isBefore(e);
            }
            // 跨午夜
            return !t.isBefore(s) || t.isBefore(e);
        } catch (Exception ex) {
            return false;
        }
    }

    private ParkFeeRule currentRule() {
        ParkFeeRule rule = feeRuleMapper.selectOne(new LambdaQueryWrapper<ParkFeeRule>()
                .eq(ParkFeeRule::getEnabled, 1).orderByDesc(ParkFeeRule::getId).last("limit 1"));
        if (rule == null) {
            rule = new ParkFeeRule();
            rule.setFreeMinutes(15);
            rule.setFirstHourFee(new BigDecimal("5"));
            rule.setStepMinutes(60);
            rule.setStepFee(new BigDecimal("3"));
            rule.setDayCap(new BigDecimal("40"));
            rule.setNightStart("22:00");
            rule.setNightEnd("07:00");
            rule.setNightDiscount(new BigDecimal("0.5"));
        }
        return rule;
    }

    /* ===================== helpers ===================== */

    private void bumpVisitorUsage(Long authId) {
        if (authId == null) return;
        ParkVisitorAuth auth = visitorAuthMapper.selectById(authId);
        if (auth == null) return;
        int used = (auth.getUsedEntries() == null ? 0 : auth.getUsedEntries()) + 1;
        auth.setUsedEntries(used);
        if (auth.getMaxEntries() != null && used >= auth.getMaxEntries()) {
            auth.setStatus("USED");
            if (StringUtils.hasText(auth.getPlateNo())) {
                cache.evictVisitorAuth(requireTenant(), auth.getPlateNo());
            }
        } else if (StringUtils.hasText(auth.getPlateNo())) {
            cache.cacheVisitorAuth(auth);
        }
        auth.setUpdateTime(LocalDateTime.now());
        visitorAuthMapper.updateById(auth);
    }

    private boolean isDbList(String plate, String listType) {
        Long c = vehicleMapper.selectCount(new LambdaQueryWrapper<ParkVehicle>()
                .eq(ParkVehicle::getPlateNo, plate)
                .eq(ParkVehicle::getListType, listType)
                .eq(ParkVehicle::getBindStatus, 1));
        return c != null && c > 0;
    }

    private boolean hasUnpaidOrder(String plate) {
        Long c = orderMapper.selectCount(new LambdaQueryWrapper<ParkOrder>()
                .eq(ParkOrder::getPlateNo, plate)
                .eq(ParkOrder::getStatus, "UNPAID"));
        return c != null && c > 0;
    }

    private boolean hasMonthlyParkingArrears(String plate) {
        try {
            Integer n = jdbc.queryForObject(
                    "select count(1) from fee_bill where tenant_id=? and fee_type='PARKING' and plate_no=? "
                            + "and status in ('UNPAID','OVERDUE') and due_date is not null "
                            + "and due_date < date_sub(curdate(), interval 30 day)",
                    Integer.class, requireTenant(), plate);
            return n != null && n > 0;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isAccessRestricted(String plate) {
        try {
            Integer n = jdbc.queryForObject(
                    "select count(1) from resident_car where tenant_id=? and plate_no=? and status='ACTIVE' and access_restricted=1",
                    Integer.class, requireTenant(), plate);
            return n != null && n > 0;
        } catch (Exception e) {
            return false;
        }
    }

    private String str(Object o) {
        return o == null ? null : String.valueOf(o).trim();
    }

    private ParkOrder findOpenOrder(String plate) {
        return orderMapper.selectOne(new LambdaQueryWrapper<ParkOrder>()
                .eq(ParkOrder::getPlateNo, plate)
                .eq(ParkOrder::getStatus, "PARKING")
                .orderByDesc(ParkOrder::getId).last("limit 1"));
    }

    private Long findVehicleId(String plate) {
        ParkVehicle v = findVehicle(plate);
        return v == null ? null : v.getId();
    }

    private ParkVehicle findVehicle(String plate) {
        return vehicleMapper.selectOne(new LambdaQueryWrapper<ParkVehicle>()
                .eq(ParkVehicle::getPlateNo, plate)
                .eq(ParkVehicle::getBindStatus, 1)
                .last("limit 1"));
    }

    private ParkVehicle requireVehicle(Long id) {
        ParkVehicle v = vehicleMapper.selectById(id);
        if (v == null) throw new BizException(404, "车辆不存在");
        return v;
    }

    private ParkSpace requireSpace(Long id) {
        ParkSpace s = spaceMapper.selectById(id);
        if (s == null) throw new BizException(404, "车位不存在");
        return s;
    }

    private void insertMessage(Long tid, Long uid, String title, String content, String type) {
        try {
            jdbc.update(
                    "insert into sys_message(tenant_id,user_id,title,content,msg_type,read_flag,create_time) values(?,?,?,?,?,?,now())",
                    tid, uid, title, content, type, 0);
        } catch (Exception ignored) {
        }
    }

    private String normalizePlate(String plate) {
        return plate.trim().toUpperCase().replace(" ", "");
    }

    private String genCode(String prefix) {
        return prefix + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + String.format("%04d", new Random().nextInt(10000));
    }

    private String defaultStr(String v, String d) {
        return StringUtils.hasText(v) ? v.trim() : d;
    }

    private Long toLong(Object o) {
        if (o == null) return null;
        if (o instanceof Number) return ((Number) o).longValue();
        try {
            return Long.parseLong(String.valueOf(o));
        } catch (Exception e) {
            return null;
        }
    }

    private Long requireTenant() {
        Long tid = TenantContext.getTenantId();
        if (tid == null) throw new BizException(40001, "缺少租户上下文");
        return tid;
    }

    private boolean isResident() {
        return RoleConstants.RESIDENT.equals(TenantContext.getRole());
    }

    private void assertStaffView() {
        String role = TenantContext.getRole();
        if (RoleConstants.isPropertyAdmin(role) || RoleConstants.isGridWorker(role)
                || RoleConstants.isPlatformAdmin(role)) {
            return;
        }
        throw new BizException(403, "仅物业工作人员可操作");
    }

    private void assertCanManage() {
        String role = TenantContext.getRole();
        if (RoleConstants.isPropertyAdmin(role) || RoleConstants.isPlatformAdmin(role)) {
            return;
        }
        throw new BizException(403, "网格员无管理权限");
    }

    private void assertStaffOrResident() {
        String role = TenantContext.getRole();
        if (RoleConstants.isPropertyAdmin(role) || RoleConstants.isGridWorker(role)
                || RoleConstants.isPlatformAdmin(role) || isResident()) {
            return;
        }
        throw new BizException(403, "无权访问");
    }
}
