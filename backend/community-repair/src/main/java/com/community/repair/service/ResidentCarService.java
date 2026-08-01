package com.community.repair.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.community.common.constant.RoleConstants;
import com.community.common.exception.BizException;
import com.community.common.tenant.TenantContext;
import com.community.repair.dto.ResidentCarSaveRequest;
import com.community.repair.entity.ResidentCar;
import com.community.repair.mapper.ResidentCarMapper;
import com.community.repair.util.PlateNoUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 常驻住户车辆：绑定房屋、同社区车牌唯一、迁出停用；网格员只读、管理员可编辑
 */
@Service
@RequiredArgsConstructor
public class ResidentCarService {

    public static final String ST_ACTIVE = "ACTIVE";
    public static final String ST_DISABLED = "DISABLED";

    private final ResidentCarMapper carMapper;
    private final JdbcTemplate jdbc;

    private void ensureCarExt() {
        try {
            addColIfMissing("resident_car", "space_id", "bigint null");
            addColIfMissing("resident_car", "space_no", "varchar(32) null");
            addColIfMissing("resident_car", "monthly_rent", "decimal(10,2) null");
            addColIfMissing("resident_car", "access_restricted", "tinyint not null default 0");
        } catch (Exception ignored) {
        }
    }

    private void addColIfMissing(String table, String col, String def) {
        Integer c = jdbc.queryForObject(
                "select count(1) from information_schema.columns where table_schema=database() "
                        + "and table_name=? and column_name=?",
                Integer.class, table, col);
        if (c == null || c == 0) {
            jdbc.execute("alter table " + table + " add column " + col + " " + def);
        }
    }

    public List<ResidentCar> list(Long houseId, Long residentId, Long userId, String status, String keyword) {
        assertCanView();
        ensureCarExt();
        Long tid = requireTenant();
        LambdaQueryWrapper<ResidentCar> q = new LambdaQueryWrapper<ResidentCar>()
                .eq(ResidentCar::getTenantId, tid)
                .orderByDesc(ResidentCar::getId);
        if (isResident()) {
            q.eq(ResidentCar::getUserId, TenantContext.getUserId())
                    .eq(ResidentCar::getStatus, ST_ACTIVE);
            Long hid = houseId != null ? houseId : TenantContext.getHouseId();
            if (hid != null) q.eq(ResidentCar::getHouseId, hid);
        } else {
            if (houseId != null) q.eq(ResidentCar::getHouseId, houseId);
            if (residentId != null) q.eq(ResidentCar::getResidentId, residentId);
            if (userId != null) q.eq(ResidentCar::getUserId, userId);
            if (StringUtils.hasText(status)) q.eq(ResidentCar::getStatus, status);
        }
        if (StringUtils.hasText(keyword)) {
            String k = keyword.trim();
            q.and(w -> w.like(ResidentCar::getPlateNo, k)
                    .or().like(ResidentCar::getOwnerName, k)
                    .or().like(ResidentCar::getOwnerPhone, k)
                    .or().like(ResidentCar::getHouseLabel, k)
                    .or().like(ResidentCar::getSpaceNo, k));
        }
        return carMapper.selectList(q);
    }

    @Transactional
    public ResidentCar save(ResidentCarSaveRequest req) {
        assertCanEdit();
        ensureCarExt();
        PlateNoUtils.validate(req.getPlateNo());
        String plate = PlateNoUtils.normalize(req.getPlateNo());
        Long tid = requireTenant();
        LocalDateTime now = LocalDateTime.now();

        if (isResident()) {
            // 居民只能为自己当前房屋新增/改自己的车
            req.setUserId(TenantContext.getUserId());
            if (req.getHouseId() == null) {
                req.setHouseId(TenantContext.getHouseId());
            }
            assertResidentOwnsHouse(req.getHouseId());
            fillOwnerFromUser(req);
        } else {
            assertCanManage();
        }
        if (req.getHouseId() == null) {
            throw new BizException("请选择绑定房屋");
        }
        String houseLabel = resolveHouseLabel(tid, req.getHouseId());
        String spaceNo = req.getSpaceNo();
        if (req.getSpaceId() != null && !StringUtils.hasText(spaceNo)) {
            try {
                List<Map<String, Object>> sp = jdbc.queryForList(
                        "select space_no from park_space where id=? and tenant_id=? limit 1",
                        req.getSpaceId(), tid);
                if (!sp.isEmpty()) spaceNo = String.valueOf(sp.get(0).get("space_no"));
            } catch (Exception ignored) {
            }
        }

        // 同社区车牌不可重复（含已停用的仍占号，避免冲突；停用车可改号后重新启用）
        ResidentCar plateHit = carMapper.selectOne(new LambdaQueryWrapper<ResidentCar>()
                .eq(ResidentCar::getTenantId, tid)
                .eq(ResidentCar::getPlateNo, plate)
                .last("limit 1"));
        if (plateHit != null && (req.getId() == null || !Objects.equals(plateHit.getId(), req.getId()))) {
            throw new BizException("该车牌已在本社区登记，不可重复");
        }

        if (req.getId() != null) {
            ResidentCar old = requireCar(req.getId());
            if (isResident() && !Objects.equals(old.getUserId(), TenantContext.getUserId())) {
                throw new BizException(403, "只能修改本人车辆");
            }
            if (isResident() && ST_DISABLED.equals(old.getStatus())) {
                throw new BizException("车辆已停用，无法修改");
            }
            old.setHouseId(req.getHouseId());
            old.setHouseLabel(houseLabel);
            old.setResidentId(req.getResidentId() != null ? req.getResidentId() : old.getResidentId());
            old.setUserId(req.getUserId() != null ? req.getUserId() : old.getUserId());
            old.setOwnerName(req.getOwnerName());
            old.setOwnerPhone(req.getOwnerPhone());
            old.setPlateNo(plate);
            old.setBrandModel(req.getBrandModel());
            old.setColor(req.getColor());
            old.setSpaceId(req.getSpaceId());
            old.setSpaceNo(spaceNo);
            old.setMonthlyRent(req.getMonthlyRent());
            old.setRemark(req.getRemark());
            if (!isResident() && ST_DISABLED.equals(old.getStatus())) {
                old.setStatus(ST_ACTIVE);
            }
            old.setUpdateTime(now);
            carMapper.updateById(old);
            syncWhitelist(old);
            return old;
        }

        ResidentCar car = new ResidentCar();
        car.setTenantId(tid);
        car.setHouseId(req.getHouseId());
        car.setHouseLabel(houseLabel);
        car.setResidentId(req.getResidentId());
        car.setUserId(req.getUserId() != null ? req.getUserId() : TenantContext.getUserId());
        car.setOwnerName(req.getOwnerName());
        car.setOwnerPhone(req.getOwnerPhone());
        car.setPlateNo(plate);
        car.setBrandModel(req.getBrandModel());
        car.setColor(req.getColor());
        car.setSpaceId(req.getSpaceId());
        car.setSpaceNo(spaceNo);
        car.setMonthlyRent(req.getMonthlyRent());
        car.setAccessRestricted(0);
        car.setStatus(ST_ACTIVE);
        car.setRemark(req.getRemark());
        car.setCreateBy(TenantContext.getUserId());
        car.setCreateTime(now);
        carMapper.insert(car);
        syncWhitelist(car);
        return car;
    }

    /** 限制/恢复进出权限（长期欠费联动） */
    @Transactional
    public ResidentCar setAccessRestricted(Long id, boolean restricted) {
        assertCanManage();
        ensureCarExt();
        ResidentCar car = requireCar(id);
        car.setAccessRestricted(restricted ? 1 : 0);
        car.setUpdateTime(LocalDateTime.now());
        carMapper.updateById(car);
        try {
            if (restricted) {
                jdbc.update(
                        "insert into park_vehicle(tenant_id,plate_no,vehicle_type,list_type,house_id,owner_user_id,owner_name,owner_phone,space_id,brand_model,color,bind_status,remark,create_time) "
                                + "values(?,?,?,?,?,?,?,?,?,?,?,1,'欠费限行',now()) "
                                + "on duplicate key update list_type='BLACK', bind_status=1, remark='欠费限行', update_time=now()",
                        car.getTenantId(), car.getPlateNo(), "OWNER", "BLACK", car.getHouseId(), car.getUserId(),
                        car.getOwnerName(), car.getOwnerPhone(), car.getSpaceId(), car.getBrandModel(), car.getColor());
            } else {
                jdbc.update(
                        "update park_vehicle set list_type='WHITE', remark=null, update_time=now() "
                                + "where tenant_id=? and plate_no=?",
                        car.getTenantId(), car.getPlateNo());
            }
        } catch (Exception ignored) {
        }
        return car;
    }

    private void syncWhitelist(ResidentCar car) {
        if (car == null || !ST_ACTIVE.equals(car.getStatus())) return;
        if (Integer.valueOf(1).equals(car.getAccessRestricted())) return;
        try {
            jdbc.update(
                    "insert into park_vehicle(tenant_id,plate_no,vehicle_type,list_type,house_id,owner_user_id,owner_name,owner_phone,space_id,brand_model,color,bind_status,create_time) "
                            + "values(?,?,?,?,?,?,?,?,?,?,?,1,now()) "
                            + "on duplicate key update list_type=if(list_type='BLACK',list_type,'WHITE'), bind_status=1, "
                            + "house_id=values(house_id), owner_name=values(owner_name), space_id=values(space_id), update_time=now()",
                    car.getTenantId(), car.getPlateNo(), "OWNER", "WHITE", car.getHouseId(), car.getUserId(),
                    car.getOwnerName(), car.getOwnerPhone(), car.getSpaceId(), car.getBrandModel(), car.getColor());
        } catch (Exception ignored) {
        }
    }

    @Transactional
    public void deleteOrDisable(Long id) {
        assertCanEdit();
        ResidentCar car = requireCar(id);
        if (isResident()) {
            if (!Objects.equals(car.getUserId(), TenantContext.getUserId())) {
                throw new BizException(403, "只能删除本人车辆");
            }
            // 居民解绑：物理删除以便车牌可被他人使用；管理员迁出用停用
            carMapper.deleteById(id);
            return;
        }
        assertCanManage();
        car.setStatus(ST_DISABLED);
        car.setUpdateTime(LocalDateTime.now());
        carMapper.updateById(car);
    }

    /** 住户迁出：按房屋+用户（或仅房屋）停用全部常驻车辆 */
    @Transactional
    public int disableByMoveOut(Long tenantId, Long houseId, Long userId) {
        if (tenantId == null || houseId == null) return 0;
        LambdaQueryWrapper<ResidentCar> q = new LambdaQueryWrapper<ResidentCar>()
                .eq(ResidentCar::getTenantId, tenantId)
                .eq(ResidentCar::getHouseId, houseId)
                .eq(ResidentCar::getStatus, ST_ACTIVE);
        if (userId != null) {
            q.eq(ResidentCar::getUserId, userId);
        }
        List<ResidentCar> list = carMapper.selectList(q);
        LocalDateTime now = LocalDateTime.now();
        for (ResidentCar c : list) {
            c.setStatus(ST_DISABLED);
            c.setUpdateTime(now);
            carMapper.updateById(c);
        }
        return list.size();
    }

    public Map<String, Object> optionsForHouse(Long houseId) {
        assertCanView();
        List<ResidentCar> cars = list(houseId, null, null, ST_ACTIVE, null);
        Map<String, Object> m = new HashMap<>();
        m.put("cars", cars);
        return m;
    }

    private void fillOwnerFromUser(ResidentCarSaveRequest req) {
        try {
            List<Map<String, Object>> rows = jdbc.queryForList(
                    "select nickname, phone from sys_user where id=? limit 1", TenantContext.getUserId());
            if (!rows.isEmpty()) {
                if (!StringUtils.hasText(req.getOwnerName())) {
                    req.setOwnerName(str(rows.get(0).get("nickname")));
                }
                if (!StringUtils.hasText(req.getOwnerPhone())) {
                    req.setOwnerPhone(str(rows.get(0).get("phone")));
                }
            }
        } catch (Exception ignored) {
        }
    }

    private void assertResidentOwnsHouse(Long houseId) {
        if (houseId == null) throw new BizException("请先绑定房屋");
        Long cnt = jdbc.queryForObject(
                "select count(1) from user_house_bind where tenant_id=? and user_id=? and house_id=?",
                Long.class, requireTenant(), TenantContext.getUserId(), houseId);
        if (cnt == null || cnt == 0) {
            throw new BizException(403, "房屋未绑定，无法登记车辆");
        }
    }

    private String resolveHouseLabel(Long tid, Long houseId) {
        try {
            List<Map<String, Object>> rows = jdbc.queryForList(
                    "select building, unit, room_no as roomNo, community_name as communityName from community_house "
                            + "where id=? and tenant_id=? limit 1",
                    houseId, tid);
            if (rows.isEmpty()) throw new BizException(404, "房屋不存在");
            Map<String, Object> h = rows.get(0);
            return str(h.get("building")) + str(h.get("unit")) + str(h.get("roomNo"));
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(404, "房屋不存在");
        }
    }

    private ResidentCar requireCar(Long id) {
        ResidentCar c = carMapper.selectById(id);
        if (c == null || !Objects.equals(c.getTenantId(), requireTenant())) {
            throw new BizException(404, "车辆不存在");
        }
        return c;
    }

    private Long requireTenant() {
        Long tid = TenantContext.getTenantId();
        if (tid == null) throw new BizException(40001, "缺少租户上下文");
        return tid;
    }

    private boolean isResident() {
        return RoleConstants.RESIDENT.equals(TenantContext.getRole());
    }

    private void assertCanView() {
        String role = TenantContext.getRole();
        if (RoleConstants.isPropertyAdmin(role) || RoleConstants.isGridWorker(role)
                || RoleConstants.isPlatformAdmin(role) || isResident()) {
            return;
        }
        throw new BizException(403, "无权查看车辆");
    }

    /** 居民可维护本人车辆；物业仅管理员可写 */
    private void assertCanEdit() {
        if (isResident()) return;
        assertCanManage();
    }

    private void assertCanManage() {
        String role = TenantContext.getRole();
        if (RoleConstants.isPropertyAdmin(role) || RoleConstants.isPlatformAdmin(role)) {
            return;
        }
        throw new BizException(403, "网格员仅可查看车辆，不可编辑");
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }
}
