package com.community.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.community.auth.entity.CommunityHouse;
import com.community.auth.entity.UserHouseBind;
import com.community.auth.mapper.CommunityHouseMapper;
import com.community.auth.mapper.UserHouseBindMapper;
import com.community.common.constant.RoleConstants;
import com.community.common.exception.BizException;
import com.community.common.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class HouseAdminService {

    private final CommunityHouseMapper houseMapper;
    private final UserHouseBindMapper bindMapper;
    private final JdbcTemplate jdbc;

    public List<Map<String, Object>> listHouses(String building, String unit, String keyword) {
        return listHouses(building, unit, keyword, null);
    }

    public List<Map<String, Object>> listHouses(String building, String unit, String keyword, String occupyStatus) {
        assertStaff();
        ensureHouseExtColumns();
        Long tid = requireTenant();
        StringBuilder sql = new StringBuilder(
                "select h.id, h.tenant_id, h.community_name, h.building, h.unit, h.room_no, h.status, h.create_time, "
                        + "h.layout_type, h.area_sqm, h.occupy_status, "
                        + "(select count(1) from user_house_bind b where b.house_id=h.id) as bind_count "
                        + "from community_house h where h.tenant_id=? ");
        List<Object> args = new ArrayList<>();
        args.add(tid);
        if (StringUtils.hasText(building)) {
            sql.append("and h.building=? ");
            args.add(building.trim());
        }
        if (StringUtils.hasText(unit)) {
            sql.append("and h.unit=? ");
            args.add(unit.trim());
        }
        if (StringUtils.hasText(occupyStatus)) {
            sql.append("and ifnull(h.occupy_status,'VACANT')=? ");
            args.add(occupyStatus.trim().toUpperCase());
        }
        if (StringUtils.hasText(keyword)) {
            sql.append("and (h.room_no like ? or h.building like ? or h.unit like ? or ifnull(h.layout_type,'') like ?) ");
            String k = "%" + keyword.trim() + "%";
            args.add(k);
            args.add(k);
            args.add(k);
            args.add(k);
        }
        sql.append("order by h.building, h.unit, h.room_no");
        List<Map<String, Object>> rows = jdbc.queryForList(sql.toString(), args.toArray());
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            out.add(toView(r));
        }
        return out;
    }

    /** 楼栋架构汇总 */
    public List<Map<String, Object>> buildingSummary() {
        assertStaff();
        ensureHouseExtColumns();
        ensureBuildingMetaTable();
        Long tid = requireTenant();
        List<Map<String, Object>> rows = jdbc.queryForList(
                "select building, "
                        + "count(distinct unit) as unitCount, "
                        + "count(1) as houseCount, "
                        + "sum(case when status=1 then 1 else 0 end) as activeCount, "
                        + "sum(case when ifnull(occupy_status,'VACANT')='VACANT' then 1 else 0 end) as vacantCount, "
                        + "sum(case when ifnull(occupy_status,'VACANT')='RENT' then 1 else 0 end) as rentCount, "
                        + "sum(case when ifnull(occupy_status,'VACANT')='SELF' then 1 else 0 end) as selfCount "
                        + "from community_house where tenant_id=? "
                        + "group by building order by building",
                tid);
        Map<String, Map<String, Object>> metaByBuilding = new HashMap<>();
        try {
            List<Map<String, Object>> metas = jdbc.queryForList(
                    "select building, floors, elevators, remark from community_building where tenant_id=?", tid);
            for (Map<String, Object> meta : metas) {
                metaByBuilding.put(String.valueOf(meta.get("building")), meta);
            }
        } catch (Exception ignored) {
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            String bld = String.valueOf(r.get("building"));
            int unitCount = toInt(r.get("unitCount"));
            int houseCount = toInt(r.get("houseCount"));
            int vacantCount = toInt(r.get("vacantCount"));
            int rentCount = toInt(r.get("rentCount"));
            int selfCount = toInt(r.get("selfCount"));
            m.put("building", bld);
            m.put("buildingName", bld);
            m.put("unitCount", unitCount);
            m.put("houseCount", houseCount);
            m.put("activeCount", r.get("activeCount"));
            m.put("vacantCount", vacantCount);
            m.put("rentCount", rentCount);
            m.put("selfCount", selfCount);
            m.put("occupiedCount", Math.max(0, houseCount - vacantCount));
            double rate = houseCount <= 0 ? 0 : (houseCount - vacantCount) * 100.0 / houseCount;
            m.put("occupancyRate", Math.round(rate * 10) / 10.0);
            Map<String, Object> meta = metaByBuilding.get(bld);
            int elevators = meta != null && meta.get("elevators") != null
                    ? toInt(meta.get("elevators"))
                    : Math.max(1, unitCount);
            int floors = meta != null && meta.get("floors") != null
                    ? toInt(meta.get("floors"))
                    : estimateFloors(tid, bld);
            m.put("elevatorCount", elevators);
            m.put("floors", floors);
            m.put("remark", meta == null ? null : meta.get("remark"));
            List<String> units = jdbc.queryForList(
                    "select distinct unit from community_house where tenant_id=? and building=? order by unit",
                    String.class, tid, bld);
            m.put("units", units);
            // 沙盘：按单元汇总
            List<Map<String, Object>> unitStats = new ArrayList<>();
            for (String u : units) {
                Map<String, Object> us = jdbc.queryForMap(
                        "select count(1) as houseCount, "
                                + "sum(case when ifnull(occupy_status,'VACANT')='VACANT' then 1 else 0 end) as vacantCount "
                                + "from community_house where tenant_id=? and building=? and unit=?",
                        tid, bld, u);
                Map<String, Object> um = new LinkedHashMap<>();
                um.put("unit", u);
                um.put("houseCount", toInt(us.get("houseCount")));
                um.put("vacantCount", toInt(us.get("vacantCount")));
                unitStats.add(um);
            }
            m.put("unitStats", unitStats);
            out.add(m);
        }
        return out;
    }

    private int estimateFloors(Long tid, String building) {
        try {
            List<String> rooms = jdbc.queryForList(
                    "select room_no from community_house where tenant_id=? and building=?",
                    String.class, tid, building);
            int max = 1;
            for (String room : rooms) {
                if (room == null) continue;
                String digits = room.replaceAll("\\D+", "");
                if (digits.length() >= 3) {
                    max = Math.max(max, Integer.parseInt(digits.substring(0, digits.length() - 2)));
                } else if (!digits.isEmpty()) {
                    max = Math.max(max, Integer.parseInt(digits));
                }
            }
            return max;
        } catch (Exception e) {
            return 1;
        }
    }

    public List<String> listBuildingsAdmin() {
        assertStaff();
        return jdbc.queryForList(
                "select distinct building from community_house where tenant_id=? order by building",
                String.class, requireTenant());
    }

    public List<String> listUnitsAdmin(String building) {
        assertStaff();
        if (!StringUtils.hasText(building)) throw new BizException("请选择楼栋");
        return jdbc.queryForList(
                "select distinct unit from community_house where tenant_id=? and building=? order by unit",
                String.class, requireTenant(), building.trim());
    }

    @Transactional
    public Map<String, Object> saveHouse(Map<String, Object> body) {
        assertAdmin();
        Long tid = requireTenant();
        String building = str(body.get("buildingName"), body.get("building"));
        String unit = str(body.get("unitName"), body.get("unit"));
        String room = str(body.get("houseNo"), body.get("roomNo"));
        if (!StringUtils.hasText(building) || !StringUtils.hasText(unit) || !StringUtils.hasText(room)) {
            throw new BizException("楼栋、单元、房号不能为空");
        }
        building = building.trim();
        unit = unit.trim();
        room = room.trim();
        int status = body.get("status") == null ? 1 : Integer.parseInt(String.valueOf(body.get("status")));
        Long id = body.get("id") == null ? null : Long.valueOf(String.valueOf(body.get("id")));
        String layoutType = str(body.get("layoutType"), body.get("layout"));
        java.math.BigDecimal areaSqm = toDecimal(body.get("areaSqm"), body.get("area"));
        String occupyStatus = normalizeOccupy(str(body.get("occupyStatus"), body.get("houseStatus")));

        ensureHouseExtColumns();
        if (id == null) {
            assertUnique(tid, building, unit, room, null);
            CommunityHouse h = new CommunityHouse();
            h.setTenantId(tid);
            h.setCommunityName(communityName(tid));
            h.setBuilding(building);
            h.setUnit(unit);
            h.setRoomNo(room);
            h.setLayoutType(layoutType);
            h.setAreaSqm(areaSqm);
            h.setOccupyStatus(occupyStatus);
            h.setStatus(status);
            h.setCreateTime(LocalDateTime.now());
            houseMapper.insert(h);
            return getOne(h.getId());
        }
        CommunityHouse h = houseMapper.selectById(id);
        if (h == null || !tid.equals(h.getTenantId())) throw new BizException("房屋不存在");
        assertUnique(tid, building, unit, room, id);
        h.setBuilding(building);
        h.setUnit(unit);
        h.setRoomNo(room);
        h.setLayoutType(layoutType);
        h.setAreaSqm(areaSqm);
        h.setOccupyStatus(occupyStatus);
        h.setStatus(status);
        houseMapper.updateById(h);
        return getOne(id);
    }

    /** 单元下批量导入房号：101,102,103 或 101-110 */
    @Transactional
    public Map<String, Object> batchImport(Map<String, Object> body) {
        assertAdmin();
        Long tid = requireTenant();
        String building = str(body.get("buildingName"), body.get("building"));
        String unit = str(body.get("unitName"), body.get("unit"));
        String roomsRaw = str(body.get("houseNos"), body.get("rooms"));
        if (!StringUtils.hasText(building) || !StringUtils.hasText(unit) || !StringUtils.hasText(roomsRaw)) {
            throw new BizException("楼栋、单元、房号列表不能为空");
        }
        List<String> rooms = parseRooms(roomsRaw);
        int ok = 0, skip = 0;
        for (String room : rooms) {
            Long exist = jdbc.queryForObject(
                    "select count(1) from community_house where tenant_id=? and building=? and unit=? and room_no=?",
                    Long.class, tid, building.trim(), unit.trim(), room);
            if (exist != null && exist > 0) {
                skip++;
                continue;
            }
            CommunityHouse h = new CommunityHouse();
            h.setTenantId(tid);
            h.setCommunityName(communityName(tid));
            h.setBuilding(building.trim());
            h.setUnit(unit.trim());
            h.setRoomNo(room);
            h.setStatus(1);
            h.setCreateTime(LocalDateTime.now());
            houseMapper.insert(h);
            ok++;
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("created", ok);
        m.put("skipped", skip);
        m.put("total", rooms.size());
        return m;
    }

    /** 仅登记楼栋名：写入占位单元「待配置」/房号「—」以便下拉可选；也可直接用 batch */
    @Transactional
    public Map<String, Object> ensureBuilding(Map<String, Object> body) {
        assertAdmin();
        String building = str(body.get("buildingName"), body.get("building"));
        if (!StringUtils.hasText(building)) throw new BizException("楼栋名称不能为空");
        Long tid = requireTenant();
        building = building.trim();
        Integer floors = body.get("floors") == null ? null : toInt(body.get("floors"));
        Integer elevators = body.get("elevators") == null && body.get("elevatorCount") == null
                ? null
                : toInt(body.get("elevators") != null ? body.get("elevators") : body.get("elevatorCount"));
        String remark = str(body.get("remark"));
        upsertBuildingMeta(tid, building, floors, elevators, remark);

        Long cnt = jdbc.queryForObject(
                "select count(1) from community_house where tenant_id=? and building=?",
                Long.class, tid, building);
        if (cnt != null && cnt > 0) {
            return Map.of("building", building, "exists", true);
        }
        Map<String, Object> create = new HashMap<>();
        create.put("building", building);
        create.put("unit", "1单元");
        create.put("roomNo", "101");
        create.put("status", 1);
        create.put("occupyStatus", "VACANT");
        saveHouse(create);
        return Map.of("building", building, "exists", false, "seeded", true);
    }

    private void upsertBuildingMeta(Long tid, String building, Integer floors, Integer elevators, String remark) {
        ensureBuildingMetaTable();
        Integer exist = jdbc.queryForObject(
                "select count(1) from community_building where tenant_id=? and building=?",
                Integer.class, tid, building);
        if (exist != null && exist > 0) {
            jdbc.update(
                    "update community_building set floors=ifnull(?,floors), elevators=ifnull(?,elevators), "
                            + "remark=ifnull(?,remark), update_time=now() where tenant_id=? and building=?",
                    floors, elevators, StringUtils.hasText(remark) ? remark : null, tid, building);
        } else {
            jdbc.update(
                    "insert into community_building(tenant_id,building,floors,elevators,remark) values(?,?,?,?,?)",
                    tid, building,
                    floors == null || floors < 1 ? 1 : floors,
                    elevators == null || elevators < 0 ? 1 : elevators,
                    remark);
        }
    }

    private void ensureBuildingMetaTable() {
        try {
            jdbc.execute(
                    "create table if not exists community_building ("
                            + "id bigint primary key auto_increment,"
                            + "tenant_id bigint not null,"
                            + "building varchar(64) not null,"
                            + "floors int null default 1,"
                            + "elevators int null default 1,"
                            + "remark varchar(255) null,"
                            + "create_time datetime default current_timestamp,"
                            + "update_time datetime default current_timestamp on update current_timestamp,"
                            + "unique key uk_tenant_building (tenant_id, building)"
                            + ") engine=InnoDB default charset=utf8mb4");
        } catch (Exception ignored) {
        }
    }

    @Transactional
    public void deleteHouse(Long id) {
        assertAdmin();
        Long tid = requireTenant();
        CommunityHouse h = houseMapper.selectById(id);
        if (h == null || !tid.equals(h.getTenantId())) throw new BizException("房屋不存在");
        Long binds = bindMapper.selectCount(new LambdaQueryWrapper<UserHouseBind>().eq(UserHouseBind::getHouseId, id));
        if (binds != null && binds > 0) {
            throw new BizException("该房屋已绑定住户，不能删除");
        }
        Long hh = jdbc.queryForObject(
                "select count(1) from household_resident where house_id=? and tenant_id=?", Long.class, id, tid);
        if (hh != null && hh > 0) {
            throw new BizException("该房屋已有住户档案，不能删除");
        }
        houseMapper.deleteById(id);
    }

    /** 登记单元：若楼栋下无该单元，则播种一个占位房号 */
    @Transactional
    public Map<String, Object> ensureUnit(Map<String, Object> body) {
        assertAdmin();
        String building = str(body.get("buildingName"), body.get("building"));
        String unit = str(body.get("unitName"), body.get("unit"));
        if (!StringUtils.hasText(building) || !StringUtils.hasText(unit)) {
            throw new BizException("楼栋与单元不能为空");
        }
        Long tid = requireTenant();
        Long cnt = jdbc.queryForObject(
                "select count(1) from community_house where tenant_id=? and building=? and unit=?",
                Long.class, tid, building.trim(), unit.trim());
        if (cnt != null && cnt > 0) {
            return Map.of("building", building.trim(), "unit", unit.trim(), "exists", true);
        }
        Map<String, Object> create = new HashMap<>();
        create.put("building", building.trim());
        create.put("unit", unit.trim());
        create.put("roomNo", "101");
        create.put("status", 1);
        create.put("occupyStatus", "VACANT");
        saveHouse(create);
        return Map.of("building", building.trim(), "unit", unit.trim(), "exists", false, "seeded", true);
    }

    private Map<String, Object> getOne(Long id) {
        List<Map<String, Object>> list = listHouses(null, null, null);
        return list.stream().filter(m -> id.equals(longVal(m.get("id")))).findFirst()
                .orElseThrow(() -> new BizException("房屋不存在"));
    }

    private Map<String, Object> toView(Map<String, Object> r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.get("id"));
        m.put("tenantId", r.get("tenant_id"));
        m.put("buildingName", r.get("building"));
        m.put("unitName", r.get("unit"));
        m.put("houseNo", r.get("room_no"));
        m.put("building", r.get("building"));
        m.put("unit", r.get("unit"));
        m.put("roomNo", r.get("room_no"));
        m.put("layoutType", r.get("layout_type"));
        m.put("areaSqm", r.get("area_sqm"));
        String occupy = r.get("occupy_status") == null ? "VACANT" : String.valueOf(r.get("occupy_status"));
        m.put("occupyStatus", occupy);
        m.put("occupyStatusLabel", occupyLabel(occupy));
        m.put("status", r.get("status"));
        m.put("statusLabel", Integer.valueOf(1).equals(toInt(r.get("status"))) ? "可用" : "停用");
        m.put("bindCount", r.get("bind_count"));
        m.put("bound", toInt(r.get("bind_count")) > 0);
        m.put("createdAt", r.get("create_time") == null ? null : String.valueOf(r.get("create_time")).replace('T', ' '));
        m.put("label", String.valueOf(r.get("building")) + String.valueOf(r.get("unit")) + String.valueOf(r.get("room_no")));
        return m;
    }

    private void ensureHouseExtColumns() {
        ensureColumn("community_house", "layout_type",
                "alter table community_house add column layout_type varchar(32) null");
        ensureColumn("community_house", "area_sqm",
                "alter table community_house add column area_sqm decimal(10,2) null");
        ensureColumn("community_house", "occupy_status",
                "alter table community_house add column occupy_status varchar(16) null default 'VACANT'");
    }

    private void ensureColumn(String table, String column, String alterSql) {
        try {
            Integer cnt = jdbc.queryForObject(
                    "select count(1) from information_schema.columns "
                            + "where table_schema = database() and table_name = ? and column_name = ?",
                    Integer.class, table, column);
            if (cnt != null && cnt > 0) return;
            jdbc.execute(alterSql);
        } catch (Exception ignored) {
        }
    }

    private static String occupyLabel(String s) {
        if ("SELF".equalsIgnoreCase(s)) return "自住";
        if ("RENT".equalsIgnoreCase(s)) return "出租";
        if ("OTHER".equalsIgnoreCase(s)) return "其他";
        return "空置";
    }

    private static String normalizeOccupy(String s) {
        if (!StringUtils.hasText(s)) return "VACANT";
        String u = s.trim().toUpperCase();
        if (u.contains("自住") || "SELF".equals(u)) return "SELF";
        if (u.contains("出租") || "RENT".equals(u)) return "RENT";
        if (u.contains("其他") || "OTHER".equals(u)) return "OTHER";
        return "VACANT";
    }

    private java.math.BigDecimal toDecimal(Object... vals) {
        for (Object v : vals) {
            if (v == null) continue;
            try {
                return new java.math.BigDecimal(String.valueOf(v).trim());
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private void assertUnique(Long tid, String building, String unit, String room, Long excludeId) {
        LambdaQueryWrapper<CommunityHouse> q = new LambdaQueryWrapper<CommunityHouse>()
                .eq(CommunityHouse::getTenantId, tid)
                .eq(CommunityHouse::getBuilding, building)
                .eq(CommunityHouse::getUnit, unit)
                .eq(CommunityHouse::getRoomNo, room);
        if (excludeId != null) q.ne(CommunityHouse::getId, excludeId);
        if (houseMapper.selectCount(q) > 0) {
            throw new BizException("房号已存在：" + building + unit + room);
        }
    }

    private List<String> parseRooms(String raw) {
        List<String> out = new ArrayList<>();
        for (String part : raw.replace("，", ",").split("[,\\n\\s]+")) {
            if (!StringUtils.hasText(part)) continue;
            part = part.trim();
            if (part.contains("-") && part.matches("\\d+-\\d+")) {
                String[] ab = part.split("-");
                int a = Integer.parseInt(ab[0]);
                int b = Integer.parseInt(ab[1]);
                if (a > b) { int t = a; a = b; b = t; }
                if (b - a > 200) throw new BizException("批量区间过大");
                for (int i = a; i <= b; i++) out.add(String.valueOf(i));
            } else {
                out.add(part);
            }
        }
        if (out.isEmpty()) throw new BizException("未解析到有效房号");
        return out;
    }

    private String communityName(Long tid) {
        try {
            String n = jdbc.queryForObject(
                    "select community_name from community_house where tenant_id=? and community_name is not null limit 1",
                    String.class, tid);
            if (StringUtils.hasText(n)) return n;
        } catch (Exception ignored) {
        }
        return tid != null && tid == 2L ? "星河湾社区" : "阳光花园社区";
    }

    private void assertStaff() {
        String role = TenantContext.getRole();
        if (!RoleConstants.isPropertyAdmin(role) && !RoleConstants.isGridWorker(role)
                && !RoleConstants.isPlatformAdmin(role)) {
            throw new BizException(403, "仅物业人员可查看");
        }
    }

    private void assertAdmin() {
        String role = TenantContext.getRole();
        if (!RoleConstants.isPropertyAdmin(role) && !RoleConstants.isPlatformAdmin(role)) {
            throw new BizException(403, "仅物业管理员可维护房屋");
        }
    }

    private Long requireTenant() {
        Long tid = TenantContext.getTenantId();
        if (tid == null) throw new BizException(40001, "缺少租户");
        return tid;
    }

    private String str(Object... vals) {
        for (Object v : vals) {
            if (v != null && StringUtils.hasText(String.valueOf(v))) return String.valueOf(v).trim();
        }
        return null;
    }

    private int toInt(Object o) {
        if (o == null) return 0;
        return Integer.parseInt(String.valueOf(o));
    }

    private Long longVal(Object o) {
        if (o == null) return null;
        return Long.valueOf(String.valueOf(o));
    }
}
