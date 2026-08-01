package com.community.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.community.auth.dto.HouseBindRequest;
import com.community.auth.entity.CommunityHouse;
import com.community.auth.entity.HouseholdResident;
import com.community.auth.entity.SysUser;
import com.community.auth.entity.UserHouseBind;
import com.community.auth.mapper.CommunityHouseMapper;
import com.community.auth.mapper.HouseholdResidentMapper;
import com.community.auth.mapper.SysUserMapper;
import com.community.auth.mapper.UserHouseBindMapper;
import com.community.common.exception.BizException;
import com.community.common.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class HouseService {

    private final CommunityHouseMapper houseMapper;
    private final UserHouseBindMapper bindMapper;
    private final HouseholdResidentMapper householdMapper;
    private final SysUserMapper userMapper;

    public List<Map<String, Object>> listMine() {
        Long userId = requireUserId();
        Long tenantId = requireTenantId();
        syncBindsFromHousehold(userId, tenantId);
        List<UserHouseBind> binds = bindMapper.selectList(new LambdaQueryWrapper<UserHouseBind>()
                .eq(UserHouseBind::getUserId, userId)
                .eq(UserHouseBind::getTenantId, tenantId)
                .orderByDesc(UserHouseBind::getIsDefault)
                .orderByAsc(UserHouseBind::getId));
        return toHouseMaps(binds, userId);
    }

    public Map<String, Object> current() {
        List<Map<String, Object>> list = listMine();
        if (list.isEmpty()) {
            return Map.of("bound", false, "houses", List.of());
        }
        Map<String, Object> current = list.stream()
                .filter(h -> Boolean.TRUE.equals(h.get("isDefault")) || Objects.equals(h.get("isDefault"), 1))
                .findFirst()
                .orElse(list.get(0));
        Map<String, Object> resp = new HashMap<>();
        resp.put("bound", true);
        resp.put("currentHouseId", current.get("id"));
        resp.put("currentHouse", current);
        resp.put("houses", list);
        return resp;
    }

    /** 本租户可选楼栋（禁止跨社区） */
    public List<String> listBuildings() {
        Long tenantId = requireTenantId();
        List<CommunityHouse> houses = activeHouses(tenantId);
        Set<String> set = new LinkedHashSet<>();
        for (CommunityHouse h : houses) {
            if (StringUtils.hasText(h.getBuilding())) {
                set.add(h.getBuilding());
            }
        }
        return new ArrayList<>(set);
    }

    public List<String> listUnits(String building) {
        if (!StringUtils.hasText(building)) {
            throw new BizException("请先选择楼栋");
        }
        Long tenantId = requireTenantId();
        List<CommunityHouse> houses = houseMapper.selectList(new LambdaQueryWrapper<CommunityHouse>()
                .eq(CommunityHouse::getTenantId, tenantId)
                .eq(CommunityHouse::getBuilding, building)
                .eq(CommunityHouse::getStatus, 1)
                .orderByAsc(CommunityHouse::getUnit));
        Set<String> set = new LinkedHashSet<>();
        for (CommunityHouse h : houses) {
            if (StringUtils.hasText(h.getUnit())) {
                set.add(h.getUnit());
            }
        }
        return new ArrayList<>(set);
    }

    public List<Map<String, Object>> listRooms(String building, String unit) {
        if (!StringUtils.hasText(building) || !StringUtils.hasText(unit)) {
            throw new BizException("请先选择楼栋和单元");
        }
        Long tenantId = requireTenantId();
        Long userId = requireUserId();
        List<CommunityHouse> houses = houseMapper.selectList(new LambdaQueryWrapper<CommunityHouse>()
                .eq(CommunityHouse::getTenantId, tenantId)
                .eq(CommunityHouse::getBuilding, building)
                .eq(CommunityHouse::getUnit, unit)
                .eq(CommunityHouse::getStatus, 1)
                .orderByAsc(CommunityHouse::getRoomNo));
        Set<Long> boundIds = bindMapper.selectList(new LambdaQueryWrapper<UserHouseBind>()
                        .eq(UserHouseBind::getUserId, userId)
                        .eq(UserHouseBind::getTenantId, tenantId))
                .stream().map(UserHouseBind::getHouseId).collect(Collectors.toSet());
        List<Map<String, Object>> list = new ArrayList<>();
        for (CommunityHouse h : houses) {
            Map<String, Object> m = houseBrief(h);
            m.put("bound", boundIds.contains(h.getId()));
            list.add(m);
        }
        return list;
    }

    @Transactional
    public Map<String, Object> bind(HouseBindRequest req) {
        Long userId = requireUserId();
        Long tenantId = requireTenantId();
        CommunityHouse house = resolveHouse(req, tenantId);
        if (!Objects.equals(house.getTenantId(), tenantId)) {
            throw new BizException(403, "禁止跨社区绑定房屋");
        }
        Long exists = bindMapper.selectCount(new LambdaQueryWrapper<UserHouseBind>()
                .eq(UserHouseBind::getUserId, userId)
                .eq(UserHouseBind::getHouseId, house.getId()));
        if (exists != null && exists > 0) {
            throw new BizException("该房屋已绑定");
        }
        long count = bindMapper.selectCount(new LambdaQueryWrapper<UserHouseBind>()
                .eq(UserHouseBind::getUserId, userId)
                .eq(UserHouseBind::getTenantId, tenantId));
        UserHouseBind bind = new UserHouseBind();
        bind.setTenantId(tenantId);
        bind.setUserId(userId);
        bind.setHouseId(house.getId());
        bind.setIsDefault(count == 0 ? 1 : 0);
        bind.setCreateTime(LocalDateTime.now());
        bindMapper.insert(bind);
        return houseBrief(house, bind.getIsDefault());
    }

    @Transactional
    public void unbind(Long houseId) {
        Long userId = requireUserId();
        Long tenantId = requireTenantId();
        UserHouseBind bind = bindMapper.selectOne(new LambdaQueryWrapper<UserHouseBind>()
                .eq(UserHouseBind::getUserId, userId)
                .eq(UserHouseBind::getTenantId, tenantId)
                .eq(UserHouseBind::getHouseId, houseId));
        if (bind == null) {
            throw new BizException(404, "未绑定该房屋");
        }
        long count = bindMapper.selectCount(new LambdaQueryWrapper<UserHouseBind>()
                .eq(UserHouseBind::getUserId, userId)
                .eq(UserHouseBind::getTenantId, tenantId));
        if (count <= 1) {
            throw new BizException("至少保留一套绑定房屋，不能全部解绑");
        }
        boolean wasDefault = bind.getIsDefault() != null && bind.getIsDefault() == 1;
        bindMapper.deleteById(bind.getId());
        if (wasDefault) {
            UserHouseBind next = bindMapper.selectOne(new LambdaQueryWrapper<UserHouseBind>()
                    .eq(UserHouseBind::getUserId, userId)
                    .eq(UserHouseBind::getTenantId, tenantId)
                    .orderByAsc(UserHouseBind::getId)
                    .last("limit 1"));
            if (next != null) {
                next.setIsDefault(1);
                bindMapper.updateById(next);
            }
        }
    }

    @Transactional
    public Map<String, Object> setDefault(Long houseId) {
        Long userId = requireUserId();
        Long tenantId = requireTenantId();
        UserHouseBind bind = bindMapper.selectOne(new LambdaQueryWrapper<UserHouseBind>()
                .eq(UserHouseBind::getUserId, userId)
                .eq(UserHouseBind::getTenantId, tenantId)
                .eq(UserHouseBind::getHouseId, houseId));
        if (bind == null) {
            throw new BizException(404, "未绑定该房屋");
        }
        bindMapper.update(null, new LambdaUpdateWrapper<UserHouseBind>()
                .eq(UserHouseBind::getUserId, userId)
                .eq(UserHouseBind::getTenantId, tenantId)
                .set(UserHouseBind::getIsDefault, 0));
        bind.setIsDefault(1);
        bindMapper.updateById(bind);
        CommunityHouse house = houseMapper.selectById(houseId);
        if (house == null || !Objects.equals(house.getTenantId(), tenantId)) {
            throw new BizException(403, "房屋不属于当前社区");
        }
        return houseBrief(house, 1);
    }

    /** 登录结果附加房屋信息 */
    public void fillLoginHouses(Long userId, Long tenantId, Map<String, Object> resp) {
        if (userId == null || tenantId == null) {
            resp.put("houses", List.of());
            resp.put("currentHouseId", null);
            return;
        }
        // 物业已录入住户但尚未绑定时：登录自动补齐绑定
        syncBindsFromHousehold(userId, tenantId);
        List<UserHouseBind> binds = bindMapper.selectList(new LambdaQueryWrapper<UserHouseBind>()
                .eq(UserHouseBind::getUserId, userId)
                .eq(UserHouseBind::getTenantId, tenantId)
                .orderByDesc(UserHouseBind::getIsDefault)
                .orderByAsc(UserHouseBind::getId));
        List<Map<String, Object>> houses = toHouseMaps(binds, userId);
        resp.put("houses", houses);
        Map<String, Object> cur = houses.stream()
                .filter(h -> Objects.equals(h.get("isDefault"), 1) || Boolean.TRUE.equals(h.get("isDefault")))
                .findFirst()
                .orElse(houses.isEmpty() ? null : houses.get(0));
        resp.put("currentHouseId", cur == null ? null : cur.get("id"));
        resp.put("currentHouse", cur);
        resp.put("needBindHouse", houses.isEmpty());
        if (cur != null) {
            resp.put("identityType", cur.get("identityType"));
            resp.put("identityLabel", cur.get("identityLabel"));
            resp.put("leaseStart", cur.get("leaseStart"));
            resp.put("leaseEnd", cur.get("leaseEnd"));
            resp.put("leaseStatus", cur.get("leaseStatus"));
            resp.put("leaseStatusLabel", cur.get("leaseStatusLabel"));
        }
    }

    /** 按住户档案补绑房屋（租客/业主录入后居民登录即可看到） */
    public void syncBindsFromHousehold(Long userId, Long tenantId) {
        if (userId == null || tenantId == null) return;
        SysUser user = userMapper.selectById(userId);
        if (user == null || !StringUtils.hasText(user.getPhone())) return;
        List<HouseholdResident> rows = householdMapper.selectList(new LambdaQueryWrapper<HouseholdResident>()
                .eq(HouseholdResident::getTenantId, tenantId)
                .eq(HouseholdResident::getStatus, "ACTIVE")
                .and(w -> w.eq(HouseholdResident::getUserId, userId)
                        .or()
                        .eq(HouseholdResident::getPhone, user.getPhone())));
        for (HouseholdResident hr : rows) {
            if (hr.getHouseId() == null) continue;
            if (hr.getUserId() == null || !Objects.equals(hr.getUserId(), userId)) {
                hr.setUserId(userId);
                hr.setUpdateTime(LocalDateTime.now());
                householdMapper.updateById(hr);
            }
            Long cnt = bindMapper.selectCount(new LambdaQueryWrapper<UserHouseBind>()
                    .eq(UserHouseBind::getUserId, userId)
                    .eq(UserHouseBind::getHouseId, hr.getHouseId()));
            if (cnt != null && cnt > 0) continue;
            Long any = bindMapper.selectCount(new LambdaQueryWrapper<UserHouseBind>()
                    .eq(UserHouseBind::getUserId, userId)
                    .eq(UserHouseBind::getTenantId, tenantId));
            UserHouseBind b = new UserHouseBind();
            b.setTenantId(tenantId);
            b.setUserId(userId);
            b.setHouseId(hr.getHouseId());
            b.setIsDefault(any == null || any == 0 ? 1 : 0);
            b.setCreateTime(LocalDateTime.now());
            bindMapper.insert(b);
        }
    }

    private CommunityHouse resolveHouse(HouseBindRequest req, Long tenantId) {
        if (req.getHouseId() != null) {
            CommunityHouse h = houseMapper.selectById(req.getHouseId());
            if (h == null || h.getStatus() == null || h.getStatus() != 1) {
                throw new BizException(404, "房屋不存在");
            }
            return h;
        }
        if (!StringUtils.hasText(req.getBuilding()) || !StringUtils.hasText(req.getUnit())
                || !StringUtils.hasText(req.getRoomNo())) {
            throw new BizException("请选择完整的楼栋、单元、房号");
        }
        CommunityHouse h = houseMapper.selectOne(new LambdaQueryWrapper<CommunityHouse>()
                .eq(CommunityHouse::getTenantId, tenantId)
                .eq(CommunityHouse::getBuilding, req.getBuilding())
                .eq(CommunityHouse::getUnit, req.getUnit())
                .eq(CommunityHouse::getRoomNo, req.getRoomNo())
                .eq(CommunityHouse::getStatus, 1)
                .last("limit 1"));
        if (h == null) {
            throw new BizException(404, "当前社区下无此房屋，请重新选择");
        }
        return h;
    }

    /** 供迁入审批等模块解析房屋 */
    public CommunityHouse resolveHousePublic(HouseBindRequest req, Long tenantId) {
        return resolveHouse(req, tenantId);
    }

    private List<CommunityHouse> activeHouses(Long tenantId) {
        return houseMapper.selectList(new LambdaQueryWrapper<CommunityHouse>()
                .eq(CommunityHouse::getTenantId, tenantId)
                .eq(CommunityHouse::getStatus, 1)
                .orderByAsc(CommunityHouse::getBuilding)
                .orderByAsc(CommunityHouse::getUnit)
                .orderByAsc(CommunityHouse::getRoomNo));
    }

    /** 物业选房：本租户全部可用房源 */
    public List<Map<String, Object>> listCatalog() {
        Long tenantId = requireTenantId();
        return activeHouses(tenantId).stream().map(this::houseBrief).collect(Collectors.toList());
    }

    private List<Map<String, Object>> toHouseMaps(List<UserHouseBind> binds, Long userId) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (UserHouseBind b : binds) {
            CommunityHouse h = houseMapper.selectById(b.getHouseId());
            if (h == null) {
                continue;
            }
            Map<String, Object> m = houseBrief(h, b.getIsDefault());
            fillResidentIdentity(m, userId, h.getId(), h.getTenantId());
            list.add(m);
        }
        return list;
    }

    private void fillResidentIdentity(Map<String, Object> m, Long userId, Long houseId, Long tenantId) {
        if (userId == null || houseId == null) return;
        HouseholdResident hr = householdMapper.selectOne(new LambdaQueryWrapper<HouseholdResident>()
                .eq(HouseholdResident::getTenantId, tenantId)
                .eq(HouseholdResident::getHouseId, houseId)
                .eq(HouseholdResident::getUserId, userId)
                .ne(HouseholdResident::getStatus, "MOVED_OUT")
                .orderByDesc(HouseholdResident::getId)
                .last("limit 1"));
        if (hr == null) {
            SysUser user = userMapper.selectById(userId);
            if (user != null && StringUtils.hasText(user.getPhone())) {
                hr = householdMapper.selectOne(new LambdaQueryWrapper<HouseholdResident>()
                        .eq(HouseholdResident::getTenantId, tenantId)
                        .eq(HouseholdResident::getHouseId, houseId)
                        .eq(HouseholdResident::getPhone, user.getPhone())
                        .ne(HouseholdResident::getStatus, "MOVED_OUT")
                        .orderByDesc(HouseholdResident::getId)
                        .last("limit 1"));
            }
        }
        if (hr == null) {
            m.put("identityType", "OWNER");
            m.put("identityLabel", "业主");
            return;
        }
        boolean tenant = "TENANT".equalsIgnoreCase(hr.getIdentityType());
        m.put("identityType", tenant ? "TENANT" : "OWNER");
        m.put("identityLabel", tenant ? "租客" : "业主");
        m.put("residentName", hr.getName());
        m.put("residentPhone", hr.getPhone());
        m.put("residentStatus", hr.getStatus());
        if (hr.getLeaseStart() != null) {
            m.put("leaseStart", hr.getLeaseStart().toString());
        }
        if (hr.getLeaseEnd() != null) {
            m.put("leaseEnd", hr.getLeaseEnd().toString());
        }
        Map<String, String> lease = leaseStatus(hr.getLeaseStart(), hr.getLeaseEnd(), tenant);
        m.put("leaseStatus", lease.get("status"));
        m.put("leaseStatusLabel", lease.get("label"));
    }

    private static Map<String, String> leaseStatus(LocalDate start, LocalDate end, boolean isTenant) {
        Map<String, String> m = new HashMap<>();
        if (!isTenant) {
            m.put("status", "OWNER");
            m.put("label", "业主");
            return m;
        }
        if (end == null && start == null) {
            m.put("status", "UNSET");
            m.put("label", "未登记");
            return m;
        }
        LocalDate today = LocalDate.now();
        if (end != null && end.isBefore(today)) {
            m.put("status", "EXPIRED");
            m.put("label", "已到期");
            return m;
        }
        if (end != null && ChronoUnit.DAYS.between(today, end) <= 30) {
            m.put("status", "EXPIRING");
            m.put("label", "即将到期");
            return m;
        }
        m.put("status", "ACTIVE");
        m.put("label", "在租中");
        return m;
    }

    private Map<String, Object> houseBrief(CommunityHouse h) {
        return houseBrief(h, null);
    }

    private Map<String, Object> houseBrief(CommunityHouse h, Integer isDefault) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", h.getId());
        m.put("tenantId", h.getTenantId());
        m.put("communityName", h.getCommunityName());
        m.put("building", h.getBuilding());
        m.put("unit", h.getUnit());
        m.put("roomNo", h.getRoomNo());
        m.put("label", formatLabel(h));
        if (isDefault != null) {
            m.put("isDefault", isDefault);
        }
        return m;
    }

    public static String formatLabel(CommunityHouse h) {
        return h.getCommunityName() + " · " + h.getBuilding() + h.getUnit() + h.getRoomNo();
    }

    private Long requireUserId() {
        Long userId = TenantContext.getUserId();
        if (userId == null) {
            throw new BizException(401, "未登录");
        }
        return userId;
    }

    private Long requireTenantId() {
        Long tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            throw new BizException(400, "缺少租户信息");
        }
        return tenantId;
    }
}
