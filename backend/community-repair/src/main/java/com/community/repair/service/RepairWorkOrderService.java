package com.community.repair.service;

import com.community.common.constant.RoleConstants;
import com.community.common.exception.BizException;
import com.community.common.tenant.TenantContext;
import com.community.repair.entity.RepairOrder;
import com.community.repair.mapper.RepairOrderMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 报事报修：分类、统计、手动分派相关
 */
@Service
@RequiredArgsConstructor
public class RepairWorkOrderService {

    private final JdbcTemplate jdbcTemplate;
    private final RepairOrderMapper repairOrderMapper;
    private final RepairNotifyService repairNotifyService;

    private void requireTenant() {
        if (TenantContext.getTenantId() == null) {
            throw new BizException(400, "缺少社区租户");
        }
    }

    private void assertStaff() {
        String role = TenantContext.getRole();
        if (!RoleConstants.isTenantStaff(role) && !RoleConstants.isPlatformAdmin(role)) {
            throw new BizException(403, "仅物业/网格员可操作");
        }
    }

    private void assertAdmin() {
        String role = TenantContext.getRole();
        if (!RoleConstants.isPropertyAdmin(role) && !RoleConstants.isPlatformAdmin(role)) {
            throw new BizException(403, "仅物业管理员可操作");
        }
    }

    /* ========== 分类 ========== */

    public List<Map<String, Object>> listCategories(String orderType, Boolean enabledOnly) {
        requireTenant();
        String sql = "select id,tenant_id as tenantId,name,order_type as orderType,urgency_default as urgencyDefault,"
                + "sort_no as sortNo,status,remark,create_time as createTime from repair_category where tenant_id=? ";
        List<Object> args = new ArrayList<>();
        args.add(TenantContext.getTenantId());
        if (StringUtils.hasText(orderType)) {
            sql += "and order_type=? ";
            args.add(orderType);
        }
        if (Boolean.TRUE.equals(enabledOnly)) {
            sql += "and status=1 ";
        }
        sql += "order by sort_no asc, id asc";
        return jdbcTemplate.queryForList(sql, args.toArray());
    }

    public Map<String, Object> saveCategory(Map<String, Object> body) {
        assertAdmin();
        requireTenant();
        Long tid = TenantContext.getTenantId();
        Long id = toLong(body.get("id"));
        String name = str(body.get("name"), null);
        if (!StringUtils.hasText(name)) {
            throw new BizException(400, "类目名称不能为空");
        }
        String orderType = str(body.get("orderType"), "repair");
        if (!"report".equals(orderType)) {
            orderType = "repair";
        }
        String urgency = str(body.get("urgencyDefault"), "中");
        int sortNo = toInt(body.get("sortNo"), 0);
        int status = toInt(body.get("status"), 1);
        String remark = str(body.get("remark"), null);
        if (id != null && id > 0) {
            jdbcTemplate.update(
                    "update repair_category set name=?,order_type=?,urgency_default=?,sort_no=?,status=?,remark=? where id=? and tenant_id=?",
                    name, orderType, urgency, sortNo, status, remark, id, tid);
        } else {
            jdbcTemplate.update(
                    "insert into repair_category(tenant_id,name,order_type,urgency_default,sort_no,status,remark,create_time) values(?,?,?,?,?,?,?,now())",
                    tid, name, orderType, urgency, sortNo, status, remark);
            id = jdbcTemplate.queryForObject("select last_insert_id()", Long.class);
        }
        return jdbcTemplate.queryForMap(
                "select id,tenant_id as tenantId,name,order_type as orderType,urgency_default as urgencyDefault,"
                        + "sort_no as sortNo,status,remark,create_time as createTime from repair_category where id=?",
                id);
    }

    public void deleteCategory(Long id) {
        assertAdmin();
        requireTenant();
        jdbcTemplate.update("delete from repair_category where id=? and tenant_id=?", id, TenantContext.getTenantId());
    }

    /* ========== 统计 ========== */

    public Map<String, Object> stats(String startDate, String endDate) {
        assertStaff();
        requireTenant();
        Long tid = TenantContext.getTenantId();
        String rangeSql = buildCreateTimeRangeSql(startDate, endDate);
        List<Object> rangeArgs = buildCreateTimeRangeArgs(startDate, endDate);

        Map<String, Object> m = new LinkedHashMap<>();
        int total = count(tid, null, null, rangeSql, rangeArgs);
        int done = count(tid, "done", null, rangeSql, rangeArgs);
        int pending = count(tid, "pending", null, rangeSql, rangeArgs);
        int processing = count(tid, "processing", null, rangeSql, rangeArgs);
        int repairCnt = count(tid, null, "repair", rangeSql, rangeArgs);
        int reportCnt = count(tid, null, "report", rangeSql, rangeArgs);
        m.put("total", total);
        m.put("done", done);
        m.put("pending", pending);
        m.put("processing", processing);
        m.put("repairCount", repairCnt);
        m.put("reportCount", reportCnt);
        m.put("finishRate", total == 0 ? 0 : Math.round(done * 1000.0 / total) / 10.0);
        m.put("startDate", startDate);
        m.put("endDate", endDate);

        // 平均处理时效（小时）：筛选期内已办结工单
        Double avgHours = 0.0;
        try {
            String sql = "select create_time,accept_time,finish_time from repair_order where tenant_id=? and status='done' and finish_time is not null"
                    + rangeSql + " order by id desc limit 500";
            List<Object> args = new ArrayList<>();
            args.add(tid);
            args.addAll(rangeArgs);
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, args.toArray());
            double sum = 0;
            int n = 0;
            for (Map<String, Object> r : rows) {
                LocalDateTime start = toTime(r.get("accept_time"));
                if (start == null) {
                    start = toTime(r.get("create_time"));
                }
                LocalDateTime end = toTime(r.get("finish_time"));
                if (start != null && end != null && !end.isBefore(start)) {
                    sum += Duration.between(start, end).toMinutes() / 60.0;
                    n++;
                }
            }
            if (n > 0) {
                avgHours = Math.round(sum / n * 10) / 10.0;
            }
        } catch (Exception ignored) {
        }
        m.put("avgHandleHours", avgHours);

        List<Map<String, Object>> byCategory = new ArrayList<>();
        try {
            String sql = "select ifnull(nullif(category_name,''), ifnull(facility_type,'未分类')) as name, count(1) as value "
                    + "from repair_order where tenant_id=?" + rangeSql
                    + " group by name order by value desc limit 20";
            List<Object> args = new ArrayList<>();
            args.add(tid);
            args.addAll(rangeArgs);
            byCategory = jdbcTemplate.queryForList(sql, args.toArray());
        } catch (Exception ignored) {
        }
        m.put("byCategory", byCategory);

        // 紧急等级统一：低/中/高/未设置（兼容历史 轻度/中度/未标 等）
        List<Map<String, Object>> byUrgency = new ArrayList<>();
        try {
            String sql = "select ifnull(nullif(urgency,''), ifnull(damage_level,'')) as name, count(1) as value "
                    + "from repair_order where tenant_id=?" + rangeSql + " group by name";
            List<Object> args = new ArrayList<>();
            args.add(tid);
            args.addAll(rangeArgs);
            Map<String, Integer> merged = new LinkedHashMap<>();
            for (Map<String, Object> row : jdbcTemplate.queryForList(sql, args.toArray())) {
                String label = normalizeUrgencyLabel(str(row.get("name"), null));
                int v = toInt(row.get("value"), 0);
                merged.put(label, merged.getOrDefault(label, 0) + v);
            }
            // 固定展示顺序：高 → 中 → 低 → 未设置
            for (String key : List.of("高", "中", "低", "未设置")) {
                if (merged.containsKey(key)) {
                    byUrgency.add(Map.of("name", key, "value", merged.get(key)));
                    merged.remove(key);
                }
            }
            for (Map.Entry<String, Integer> e : merged.entrySet()) {
                byUrgency.add(Map.of("name", e.getKey(), "value", e.getValue()));
            }
        } catch (Exception ignored) {
        }
        m.put("byUrgency", byUrgency);

        List<Map<String, Object>> byType = new ArrayList<>();
        byType.add(Map.of("name", "报修", "value", repairCnt));
        byType.add(Map.of("name", "报事", "value", reportCnt));
        m.put("byType", byType);
        return m;
    }

    /** 与工单分派页一致：轻度→低，中度→中，未标→未设置 */
    private static String normalizeUrgencyLabel(String raw) {
        if (!StringUtils.hasText(raw)) {
            return "未设置";
        }
        String s = raw.trim();
        if ("未标".equals(s) || "未设置".equals(s) || "—".equals(s) || "-".equals(s)) {
            return "未设置";
        }
        if (s.contains("严重") || s.contains("重度") || "高".equals(s)) {
            return "高";
        }
        if (s.contains("轻微") || s.contains("轻度") || "低".equals(s)) {
            return "低";
        }
        if (s.contains("中度") || "中".equals(s)) {
            return "中";
        }
        return s;
    }

    private static String buildCreateTimeRangeSql(String startDate, String endDate) {
        StringBuilder sb = new StringBuilder();
        if (StringUtils.hasText(startDate)) {
            sb.append(" and create_time >= ?");
        }
        if (StringUtils.hasText(endDate)) {
            sb.append(" and create_time < date_add(?, interval 1 day)");
        }
        return sb.toString();
    }

    private static List<Object> buildCreateTimeRangeArgs(String startDate, String endDate) {
        List<Object> args = new ArrayList<>();
        if (StringUtils.hasText(startDate)) {
            args.add(startDate.trim());
        }
        if (StringUtils.hasText(endDate)) {
            args.add(endDate.trim());
        }
        return args;
    }

    private int count(Long tid, String status, String orderType, String rangeSql, List<Object> rangeArgs) {
        try {
            String sql = "select count(1) from repair_order where tenant_id=?" + rangeSql;
            List<Object> args = new ArrayList<>();
            args.add(tid);
            args.addAll(rangeArgs);
            if (status != null) {
                sql += " and status=?";
                args.add(status);
            }
            if (orderType != null) {
                sql += " and ifnull(order_type,'repair')=?";
                args.add(orderType);
            }
            Integer n = jdbcTemplate.queryForObject(sql, Integer.class, args.toArray());
            return n == null ? 0 : n;
        } catch (Exception e) {
            return 0;
        }
    }

    /* ========== 从情绪案创建报事工单 ========== */

    public Long createReportFromEmotion(Long emotionCaseId, Long userId, String title, String content,
                                        String sentiment, String emotionTags, String urgency) {
        requireTenant();
        Long tid = TenantContext.getTenantId();
        try {
            Integer exists = jdbcTemplate.queryForObject(
                    "select count(1) from repair_order where tenant_id=? and emotion_case_id=?",
                    Integer.class, tid, emotionCaseId);
            if (exists != null && exists > 0) {
                return null;
            }
        } catch (Exception ignored) {
        }
        RepairOrder order = new RepairOrder();
        order.setTenantId(tid);
        order.setUserId(userId);
        order.setTitle(StringUtils.hasText(title) ? title : "邻里报事：" + (sentiment == null ? "诉求" : sentiment));
        order.setDescription(content);
        order.setOrderType("report");
        order.setUrgency(StringUtils.hasText(urgency) ? urgency : ("negative".equals(sentiment) ? "高" : "中"));
        order.setFacilityType("邻里纠纷");
        order.setCategoryName("邻里纠纷");
        order.setDamageLevel(order.getUrgency());
        order.setEmotionCaseId(emotionCaseId);
        order.setEmotionTags(emotionTags);
        order.setSentiment(sentiment);
        order.setAiTag("情绪预警:" + (emotionTags == null ? sentiment : emotionTags));
        order.setStatus("pending");
        order.setCreateTime(LocalDateTime.now());
        order.setUpdateTime(LocalDateTime.now());
        fillOrderLocation(order, null, null);
        // 尝试匹配报事类目
        try {
            List<Map<String, Object>> cats = jdbcTemplate.queryForList(
                    "select id,name,urgency_default from repair_category where tenant_id=? and order_type='report' and status=1 order by sort_no limit 5",
                    tid);
            for (Map<String, Object> c : cats) {
                String name = str(c.get("name"), "");
                if (content != null && content.contains(name.substring(0, Math.min(2, name.length())))) {
                    order.setCategoryId(toLong(c.get("id")));
                    order.setCategoryName(name);
                    order.setFacilityType(name);
                    if (c.get("urgency_default") != null) {
                        order.setUrgency(String.valueOf(c.get("urgency_default")));
                    }
                    break;
                }
            }
            if (order.getCategoryId() == null && !cats.isEmpty()) {
                order.setCategoryId(toLong(cats.get(0).get("id")));
                order.setCategoryName(str(cats.get(0).get("name"), "邻里纠纷"));
                order.setFacilityType(order.getCategoryName());
            }
        } catch (Exception ignored) {
        }
        repairOrderMapper.insert(order);
        // 已取消自动分派：新工单保持未指派，由物业手动指派
        repairNotifyService.notifyStaffNewOrder(order);
        return order.getId();
    }

    /**
     * 意见反馈「通过」后转入报事工单列表；同一反馈不重复建单。
     */
    public Long createReportFromFeedback(Long feedbackId, Long userId, String title, String content,
                                         String feedbackType, String imageUrl, String houseLabel) {
        requireTenant();
        Long tid = TenantContext.getTenantId();
        try {
            Integer exists = jdbcTemplate.queryForObject(
                    "select count(1) from repair_order where tenant_id=? and feedback_id=?",
                    Integer.class, tid, feedbackId);
            if (exists != null && exists > 0) {
                Long oid = jdbcTemplate.queryForObject(
                        "select id from repair_order where tenant_id=? and feedback_id=? order by id desc limit 1",
                        Long.class, tid, feedbackId);
                return oid;
            }
        } catch (Exception ignored) {
        }
        String type = StringUtils.hasText(feedbackType) ? feedbackType.trim() : "意见反馈";
        String t = StringUtils.hasText(title) ? title.trim() : ("意见反馈：" + type);
        StringBuilder desc = new StringBuilder();
        if (StringUtils.hasText(houseLabel)) {
            desc.append("房屋：").append(houseLabel.trim()).append('\n');
        }
        desc.append("反馈类型：").append(type).append('\n');
        if (StringUtils.hasText(content)) {
            desc.append(content.trim());
        }
        RepairOrder order = new RepairOrder();
        order.setTenantId(tid);
        order.setUserId(userId);
        order.setTitle(t.length() > 64 ? t.substring(0, 64) : t);
        order.setDescription(desc.toString());
        order.setOrderType("report");
        order.setUrgency("中");
        order.setFacilityType(type);
        order.setCategoryName(type);
        order.setDamageLevel("中");
        order.setFeedbackId(feedbackId);
        order.setAiTag("意见反馈");
        order.setImageUrl(StringUtils.hasText(imageUrl) ? imageUrl.trim() : null);
        order.setStatus("pending");
        order.setCreateTime(LocalDateTime.now());
        order.setUpdateTime(LocalDateTime.now());
        fillOrderLocation(order, null, houseLabel);
        try {
            List<Map<String, Object>> cats = jdbcTemplate.queryForList(
                    "select id,name,urgency_default from repair_category where tenant_id=? and order_type='report' and status=1 order by sort_no",
                    tid);
            for (Map<String, Object> c : cats) {
                String name = str(c.get("name"), "");
                if (name.equals(type) || (content != null && content.contains(name))) {
                    order.setCategoryId(toLong(c.get("id")));
                    order.setCategoryName(name);
                    order.setFacilityType(name);
                    if (c.get("urgency_default") != null) {
                        order.setUrgency(String.valueOf(c.get("urgency_default")));
                    }
                    break;
                }
            }
            if (order.getCategoryId() == null && !cats.isEmpty()) {
                // 尝试匹配「意见/投诉/建议」类目，否则用第一条报事类目
                for (Map<String, Object> c : cats) {
                    String name = str(c.get("name"), "");
                    if (name.contains("意见") || name.contains("投诉") || name.contains("建议")) {
                        order.setCategoryId(toLong(c.get("id")));
                        order.setCategoryName(name);
                        order.setFacilityType(name);
                        break;
                    }
                }
            }
        } catch (Exception ignored) {
        }
        repairOrderMapper.insert(order);
        repairNotifyService.notifyStaffNewOrder(order);
        return order.getId();
    }

    /* ========== 位置 ========== */

    /**
     * 写入位置：入户类「X栋Y单元Z」；公区类（电梯/路灯等）「X栋Y单元 · 电梯」，不带房号。
     */
    public void fillOrderLocation(RepairOrder order, Long houseIdOverride, String locationHintOverride) {
        if (order == null) return;
        Long tid = order.getTenantId() != null ? order.getTenantId() : TenantContext.getTenantId();
        Long houseId = houseIdOverride != null ? houseIdOverride : order.getHouseId();
        if (houseId == null) {
            houseId = TenantContext.getHouseId();
        }
        String hint = firstNonBlank(locationHintOverride, order.getLocationHint());
        String textBlob = firstNonBlank(hint, order.getLocationLabel(), order.getTitle(), order.getDescription());

        applyHouseArchive(order, tid, houseId);
        parseAddressInto(order, textBlob);
        applyPublicFacilityLocation(order, hint);
        normalizeAddressFields(order);
        order.setLocationLabel(buildLocationLabel(order, hint));
        order.setLocationHint(null);
    }

    /** 列表展示：公区不带房号 */
    public void enrichLocationDisplay(RepairOrder order) {
        if (order == null) return;
        parseAddressInto(order, firstNonBlank(order.getLocationLabel(), order.getTitle(), order.getDescription()));
        String old = order.getLocationLabel();
        String spot = "";
        if (StringUtils.hasText(old) && old.contains("·")) {
            spot = old.substring(old.indexOf('·') + 1).trim();
        }
        applyPublicFacilityLocation(order, spot);
        normalizeAddressFields(order);
        order.setLocationLabel(buildLocationLabel(order, spot));
    }

    /**
     * 启动时回填全部工单位置（几栋几单元+房号），写回数据库。
     */
    public int backfillAllLocations() {
        List<Map<String, Object>> rows;
        try {
            rows = jdbcTemplate.queryForList(
                    "select id, tenant_id, user_id, house_id, building, unit, room_no, location_label, "
                            + "title, description, facility_type, category_name "
                            + "from repair_order order by id");
        } catch (Exception e) {
            return 0;
        }
        if (rows.isEmpty()) return 0;

        Map<Long, List<Map<String, Object>>> housesByTenant = new HashMap<>();
        int updated = 0;
        for (Map<String, Object> row : rows) {
            RepairOrder o = new RepairOrder();
            o.setId(toLong(row.get("id")));
            o.setTenantId(toLong(row.get("tenant_id")));
            o.setUserId(toLong(row.get("user_id")));
            o.setHouseId(toLong(row.get("house_id")));
            o.setBuilding(str(row.get("building"), null));
            o.setUnit(str(row.get("unit"), null));
            o.setRoomNo(str(row.get("room_no"), null));
            o.setLocationLabel(str(row.get("location_label"), null));
            o.setTitle(str(row.get("title"), null));
            o.setDescription(str(row.get("description"), null));
            o.setFacilityType(str(row.get("facility_type"), null));
            o.setCategoryName(str(row.get("category_name"), null));

            // 1) 已有 house_id
            applyHouseArchive(o, o.getTenantId(), o.getHouseId());
            // 2) 用户默认/任一绑定房屋
            if (!hasFullAddress(o) && o.getUserId() != null && o.getTenantId() != null) {
                Long hid = findUserHouseId(o.getTenantId(), o.getUserId());
                if (hid != null) {
                    o.setHouseId(hid);
                    applyHouseArchive(o, o.getTenantId(), hid);
                }
            }
            // 3) 标题/详情解析「2栋1单元101」
            parseAddressInto(o, firstNonBlank(o.getLocationLabel(), o.getTitle(), o.getDescription()));
            // 4) 仍缺则按租户房屋轮询补齐（保证每条都有几栋几单元）
            if (!hasFullAddress(o) && o.getTenantId() != null) {
                List<Map<String, Object>> houses = housesByTenant.computeIfAbsent(o.getTenantId(), this::listTenantHouses);
                if (!houses.isEmpty()) {
                    Map<String, Object> h = houses.get((int) (Math.floorMod(o.getId() == null ? 0 : o.getId(), houses.size())));
                    if (o.getHouseId() == null) o.setHouseId(toLong(h.get("id")));
                    if (!StringUtils.hasText(o.getBuilding())) o.setBuilding(str(h.get("building"), null));
                    if (!StringUtils.hasText(o.getUnit())) o.setUnit(str(h.get("unit"), null));
                    // 入户类才补房号
                    if (!isPublicFacility(o) && !StringUtils.hasText(o.getRoomNo())) {
                        o.setRoomNo(str(h.get("room_no"), null));
                    }
                }
            }
            // 5) 兜底楼栋单元；公区不写房号
            if (!StringUtils.hasText(o.getBuilding())) o.setBuilding("1栋");
            if (!StringUtils.hasText(o.getUnit())) o.setUnit("1单元");
            String oldLabel = str(row.get("location_label"), null);
            String spot = "";
            if (StringUtils.hasText(oldLabel) && oldLabel.contains("·")) {
                spot = oldLabel.substring(oldLabel.indexOf('·') + 1).trim();
            }
            applyPublicFacilityLocation(o, spot);
            if (!isPublicFacility(o) && !StringUtils.hasText(o.getRoomNo())) {
                o.setRoomNo("101");
            }
            normalizeAddressFields(o);
            String label = buildLocationLabel(o, spot);
            if (!StringUtils.hasText(label)) {
                continue;
            }
            try {
                int n = jdbcTemplate.update(
                        "update repair_order set house_id=?, building=?, unit=?, room_no=?, location_label=? where id=?",
                        o.getHouseId(), o.getBuilding(), o.getUnit(), o.getRoomNo(),
                        label.length() > 128 ? label.substring(0, 128) : label, o.getId());
                if (n > 0) updated++;
            } catch (Exception ignored) {
            }
        }
        return updated;
    }

    /** 电梯/路灯/门禁等公区：去掉房号，点位用分类名 */
    private static void applyPublicFacilityLocation(RepairOrder order, String spotHint) {
        if (!isPublicFacility(order)) return;
        order.setRoomNo(null);
        // 无具体点位时，用分类名作为公区标识（如 · 电梯）
        if (!StringUtils.hasText(spotHint) || !isSpotHint(spotHint)) {
            String cat = firstNonBlank(order.getCategoryName(), order.getFacilityType());
            if (StringUtils.hasText(cat) && isPublicKeyword(cat)) {
                // 写入 locationHint 通道：buildLocationLabel 用 spot
                order.setLocationHint(simplifyPublicSpot(cat));
            }
        }
    }

    private static String buildLocationLabel(RepairOrder order, String spotHint) {
        boolean pub = isPublicFacility(order);
        String housePart = pub
                ? joinHouse(order.getBuilding(), order.getUnit(), null)
                : joinHouse(order.getBuilding(), order.getUnit(), order.getRoomNo());
        String spot = firstNonBlank(
                isSpotHint(spotHint) ? spotHint : null,
                order.getLocationHint(),
                pub ? simplifyPublicSpot(firstNonBlank(order.getCategoryName(), order.getFacilityType())) : null);
        if (!StringUtils.hasText(housePart)) return null;
        String label = StringUtils.hasText(spot) ? housePart + " · " + spot : housePart;
        return label.length() > 128 ? label.substring(0, 128) : label;
    }

    /** 公区设施：电梯、路灯、门禁、公共照明/排水等，位置不落到具体房号 */
    static boolean isPublicFacility(RepairOrder order) {
        if (order == null) return false;
        String blob = String.join(" ",
                nullToEmpty(order.getCategoryName()),
                nullToEmpty(order.getFacilityType()),
                nullToEmpty(order.getTitle()),
                nullToEmpty(order.getAiTag()));
        return isPublicKeyword(blob);
    }

    private static boolean isPublicKeyword(String text) {
        if (!StringUtils.hasText(text)) return false;
        String t = text.trim();
        return t.contains("电梯") || t.contains("路灯") || t.contains("门禁") || t.contains("单元门")
                || t.contains("公共") || t.contains("公区") || t.contains("健身") || t.contains("消防")
                || t.contains("照明") || t.contains("排水") || t.contains("楼道") || t.contains("外墙")
                || t.contains("停车场") || t.contains("车库") || t.contains("绿化") || t.contains("监控");
    }

    private static String simplifyPublicSpot(String cat) {
        if (!StringUtils.hasText(cat)) return null;
        String c = cat.trim();
        if (c.contains("电梯")) return "电梯";
        if (c.contains("路灯")) return "路灯";
        if (c.contains("门禁") || c.contains("单元门")) return "门禁";
        if (c.contains("照明")) return "照明";
        if (c.contains("排水")) return "排水";
        if (c.contains("健身")) return "健身区";
        if (c.contains("消防")) return "消防";
        if (c.contains("公共") || c.contains("公区")) return "公共区域";
        if (c.contains("楼道")) return "楼道";
        return c.length() > 12 ? c.substring(0, 12) : c;
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private void applyHouseArchive(RepairOrder order, Long tid, Long houseId) {
        if (houseId == null || tid == null) return;
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "select id, building, unit, room_no from community_house where id=? and tenant_id=? limit 1",
                    houseId, tid);
            if (rows.isEmpty()) return;
            Map<String, Object> h = rows.get(0);
            order.setHouseId(houseId);
            if (!StringUtils.hasText(order.getBuilding())) order.setBuilding(str(h.get("building"), null));
            if (!StringUtils.hasText(order.getUnit())) order.setUnit(str(h.get("unit"), null));
            if (!StringUtils.hasText(order.getRoomNo())) order.setRoomNo(str(h.get("room_no"), null));
        } catch (Exception ignored) {
        }
    }

    private Long findUserHouseId(Long tid, Long userId) {
        try {
            return jdbcTemplate.queryForObject(
                    "select house_id from user_house_bind where tenant_id=? and user_id=? "
                            + "order by is_default desc, id asc limit 1",
                    Long.class, tid, userId);
        } catch (Exception e) {
            return null;
        }
    }

    private List<Map<String, Object>> listTenantHouses(Long tid) {
        try {
            return jdbcTemplate.queryForList(
                    "select id, building, unit, room_no from community_house "
                            + "where tenant_id=? and ifnull(status,1)=1 order by building, unit, room_no",
                    tid);
        } catch (Exception e) {
            return List.of();
        }
    }

    private static boolean hasFullAddress(RepairOrder o) {
        return StringUtils.hasText(o.getBuilding()) && StringUtils.hasText(o.getUnit());
    }

    /** 从文案解析 几栋 / 几单元 / 房号 */
    private static void parseAddressInto(RepairOrder order, String text) {
        if (!StringUtils.hasText(text)) return;
        if (!StringUtils.hasText(order.getBuilding())) {
            String b = matchBuilding(text);
            if (StringUtils.hasText(b)) order.setBuilding(b);
        }
        if (!StringUtils.hasText(order.getUnit())) {
            java.util.regex.Matcher um = java.util.regex.Pattern
                    .compile("([0-9A-Za-z一二三四五六七八九十]+)\\s*单元")
                    .matcher(text);
            if (um.find()) {
                order.setUnit(um.group(1) + "单元");
            }
        }
        if (!StringUtils.hasText(order.getRoomNo())) {
            java.util.regex.Matcher rm = java.util.regex.Pattern
                    .compile("单元\\s*([0-9]{2,4})|(?:^|[^0-9])([0-9]{3,4})\\s*(?:室|房)")
                    .matcher(text);
            if (rm.find()) {
                String rn = firstNonBlank(rm.group(1), rm.group(2));
                if (StringUtils.hasText(rn)) order.setRoomNo(rn);
            }
        }
        java.util.regex.Matcher loc = java.util.regex.Pattern
                .compile("位置[:：]\\s*([^\\n·]+)")
                .matcher(text);
        if (loc.find()) {
            parseAddressInto(order, loc.group(1).trim());
        }
    }

    private static void normalizeAddressFields(RepairOrder order) {
        if (order == null) return;
        order.setBuilding(normalizeBuilding(order.getBuilding()));
        order.setUnit(normalizeUnit(order.getUnit()));
        if (StringUtils.hasText(order.getRoomNo())) {
            order.setRoomNo(order.getRoomNo().trim().replaceAll("[室房]", ""));
        }
    }

    private static String normalizeBuilding(String building) {
        if (!StringUtils.hasText(building)) return null;
        String b = building.trim();
        if (b.endsWith("栋") || b.endsWith("幢") || b.endsWith("号楼")) return b;
        if (b.matches("(?i)[0-9A-Za-z一二三四五六七八九十]+")) return b + "栋";
        return b;
    }

    private static String normalizeUnit(String unit) {
        if (!StringUtils.hasText(unit)) return null;
        String u = unit.trim();
        if (u.endsWith("单元")) return u;
        if (u.matches("(?i)[0-9A-Za-z一二三四五六七八九十]+")) return u + "单元";
        return u;
    }

    private static boolean isSpotHint(String hint) {
        if (!StringUtils.hasText(hint)) return false;
        String h = hint.trim();
        if (h.contains("栋") || h.contains("幢") || h.contains("号楼") || h.contains("单元")) return false;
        return h.contains("楼道") || h.contains("电梯") || h.contains("门口") || h.contains("地下室")
                || h.contains("健身") || h.contains("公共") || h.contains("室外") || h.equals("其他");
    }

    private static String matchBuilding(String text) {
        if (!StringUtils.hasText(text)) return null;
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("([0-9A-Za-z一二三四五六七八九十]+)\\s*(栋|幢|号楼)")
                .matcher(text);
        if (m.find()) {
            return m.group(1) + m.group(2);
        }
        return null;
    }

    private static String joinHouse(String building, String unit, String roomNo) {
        StringBuilder sb = new StringBuilder();
        if (StringUtils.hasText(building)) sb.append(building.trim());
        if (StringUtils.hasText(unit)) sb.append(unit.trim());
        if (StringUtils.hasText(roomNo)) sb.append(roomNo.trim());
        return sb.length() == 0 ? null : sb.toString();
    }

    private static String firstNonBlank(String... arr) {
        if (arr == null) return null;
        for (String s : arr) {
            if (StringUtils.hasText(s)) return s.trim();
        }
        return null;
    }

    /* ========== helpers ========== */

    private static String str(Object v, String def) {
        if (v == null) return def;
        String s = String.valueOf(v).trim();
        return s.isEmpty() ? def : s;
    }

    private static int toInt(Object v, int def) {
        if (v == null) return def;
        if (v instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(String.valueOf(v));
        } catch (Exception e) {
            return def;
        }
    }

    private static Long toLong(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return n.longValue();
        try {
            return Long.parseLong(String.valueOf(v));
        } catch (Exception e) {
            return null;
        }
    }

    private static LocalDateTime toTime(Object v) {
        if (v == null) return null;
        if (v instanceof LocalDateTime ldt) return ldt;
        if (v instanceof java.sql.Timestamp ts) return ts.toLocalDateTime();
        String s = String.valueOf(v).trim().replace(' ', 'T');
        try {
            if (s.length() >= 19) {
                return LocalDateTime.parse(s.substring(0, 19), DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            }
        } catch (Exception ignored) {
        }
        return null;
    }
}
