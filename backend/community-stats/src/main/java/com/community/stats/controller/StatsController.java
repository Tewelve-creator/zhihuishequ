package com.community.stats.controller;

import com.community.common.exception.BizException;
import com.community.common.result.R;
import com.community.common.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/stats")
@RequiredArgsConstructor
public class StatsController {

    private final JdbcTemplate jdbcTemplate;

    /**
     * 物业租户运营大屏：仅本社区真实数据（表缺失时对应块降级为空）。
     */
    @GetMapping("/tenant/dashboard")
    public R<Map<String, Object>> tenantDashboard() {
        Long tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            throw new BizException("缺少租户");
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("tenantId", tenantId);
        data.put("tenantName", tenantName(tenantId));

        long repairToday = countSafe(
                "select count(1) from repair_order where tenant_id=? and date(create_time)=curdate()", tenantId);
        long repairPending = countSafe(
                "select count(1) from repair_order where tenant_id=? and status in ('pending','processing')", tenantId);
        long repairDoneToday = countSafe(
                "select count(1) from repair_order where tenant_id=? and status='done' and date(ifnull(finish_time,update_time))=curdate()",
                tenantId);
        long repairTotal = countSafe("select count(1) from repair_order where tenant_id=?", tenantId);
        long repairDone = countSafe("select count(1) from repair_order where tenant_id=? and status='done'", tenantId);
        double finishRate = repairTotal == 0 ? 0 : Math.round(repairDone * 1000.0 / repairTotal) / 10.0;

        Map<String, Object> workSummary = new LinkedHashMap<>();
        workSummary.put("todayTotal", repairToday);
        workSummary.put("pending", repairPending);
        workSummary.put("doneToday", repairDoneToday);
        workSummary.put("finishRate", finishRate);
        data.put("workSummary", workSummary);

        data.put("repairTrend", safeQuery(
                """
                select date_format(d, '%m-%d') as day, c as cnt from (
                  select date(create_time) d, count(1) c
                  from repair_order
                  where tenant_id=? and create_time >= date_sub(curdate(), interval 6 day)
                  group by date(create_time)
                ) t order by d
                """, tenantId));

        // 近7日每日新增 vs 办结
        data.put("repairTrendSeries", safeQuery(
                """
                select date_format(day, '%m-%d') as day,
                       sum(created) as created,
                       sum(finished) as finished
                from (
                  select date(create_time) day, count(1) created, 0 finished
                  from repair_order where tenant_id=? and create_time >= date_sub(curdate(), interval 6 day)
                  group by date(create_time)
                  union all
                  select date(ifnull(finish_time, update_time)) day, 0 created, count(1) finished
                  from repair_order
                  where tenant_id=? and status='done'
                    and ifnull(finish_time, update_time) >= date_sub(curdate(), interval 6 day)
                  group by date(ifnull(finish_time, update_time))
                ) x group by day order by day
                """, tenantId, tenantId));

        long residentTotal = countSafe(
                "select count(1) from sys_user where tenant_id=? and role='resident' and ifnull(deleted,0)=0",
                tenantId);
        long staffTotal = countSafe(
                "select count(1) from sys_user where tenant_id=? and role in ('property_admin','grid_worker') and ifnull(deleted,0)=0",
                tenantId);
        long houseTotal = countSafe(
                "select count(1) from community_house where tenant_id=? and ifnull(status,1)=1", tenantId);
        long visitToday = countSafe(
                "select count(1) from visitor_appointment where tenant_id=? and date(create_time)=curdate()",
                tenantId);
        long visitPending = countSafe(
                "select count(1) from visitor_appointment where tenant_id=? and status='pending'", tenantId);
        long emotionPending = countSafe(
                "select count(1) from ai_emotion_case where tenant_id=? and status in ('pending','handling')",
                tenantId);
        long inspOpen = countSafe(
                "select count(1) from insp_task where tenant_id=? and status in ('PENDING','DOING')", tenantId);

        Map<String, Object> community = new LinkedHashMap<>();
        community.put("residentTotal", residentTotal);
        community.put("staffTotal", staffTotal);
        community.put("houseTotal", houseTotal);
        community.put("visitToday", visitToday);
        community.put("visitPending", visitPending);
        community.put("emotionPending", emotionPending);
        community.put("inspOpen", inspOpen);
        data.put("community", community);

        // 活跃住户：近7日有报修/访客的居民近似
        data.put("activeResidents", safeQuery(
                """
                select date_format(day, '%m-%d') as day, cnt from (
                  select date(create_time) day, count(distinct user_id) cnt
                  from repair_order
                  where tenant_id=? and create_time >= date_sub(curdate(), interval 6 day)
                  group by date(create_time)
                ) t order by day
                """, tenantId));

        data.put("workTypeDist", safeQuery(
                """
                select name, cnt from (
                  select '报修' name, count(1) cnt from repair_order
                    where tenant_id=? and ifnull(order_type,'repair')='repair'
                      and create_time >= date_sub(now(), interval 30 day)
                  union all
                  select '报事' name, count(1) cnt from repair_order
                    where tenant_id=? and order_type='report'
                      and create_time >= date_sub(now(), interval 30 day)
                  union all
                  select '访客申请' name, count(1) cnt from visitor_appointment
                    where tenant_id=? and create_time >= date_sub(now(), interval 30 day)
                  union all
                  select '情绪调解' name, count(1) cnt from ai_emotion_case
                    where tenant_id=? and create_time >= date_sub(now(), interval 30 day)
                ) t
                """, tenantId, tenantId, tenantId, tenantId));

        long pendingAudit = visitPending + countSafe(
                "select count(1) from house_move_apply where tenant_id=? and status='PENDING'", tenantId);
        long urgentRepair = countSafe(
                "select count(1) from repair_order where tenant_id=? and status in ('pending','processing') "
                        + "and (urgency like '%高%' or urgency like '%紧急%' or urgency like '%严%')",
                tenantId);
        Map<String, Object> todo = new LinkedHashMap<>();
        todo.put("total", repairPending + visitPending + emotionPending + inspOpen);
        todo.put("urgentRepair", urgentRepair);
        todo.put("pendingAudit", pendingAudit);
        todo.put("emotionPending", emotionPending);
        todo.put("inspOpen", inspOpen);
        data.put("todo", todo);

        data.put("staffRank", safeQuery(
                """
                select ifnull(nullif(handler_name,''), concat('员工#', handler_id)) as name,
                       count(1) as total,
                       sum(case when status='done' then 1 else 0 end) as done
                from repair_order
                where tenant_id=? and handler_id is not null
                  and create_time >= date_sub(now(), interval 30 day)
                group by handler_id, handler_name
                order by done desc, total desc
                limit 8
                """, tenantId));

        List<Map<String, Object>> buildings = safeQuery(
                """
                select building as name, count(1) as houses,
                       sum(case when ifnull(occupy_status,'VACANT') in ('SELF','RENT','OTHER') then 1 else 0 end) as occupied
                from community_house
                where tenant_id=? and ifnull(status,1)=1 and building is not null and building<>''
                group by building
                order by building
                limit 24
                """, tenantId);
        List<Map<String, Object>> mapMarkers = buildMapMarkers(tenantId, buildings);
        if (buildings.isEmpty()) {
            buildings = buildingsFromMarkers(mapMarkers);
            // 重新挂索引，保证点位落在兜底楼栋上
            List<String> names = new ArrayList<>();
            for (Map<String, Object> b : buildings) {
                names.add(String.valueOf(b.get("name")));
            }
            for (Map<String, Object> m : mapMarkers) {
                attachBuildingIndex(m, names);
            }
        }
        data.put("buildings", buildings);
        data.put("mapMarkers", mapMarkers);

        data.put("alerts", buildAlerts(tenantId));
        data.put("recentEvents", safeQuery(
                """
                select * from (
                  select id, '报修' as type, ifnull(title,'报修工单') as title, status,
                         create_time as createTime, 'repair' as biz
                  from repair_order where tenant_id=?
                  union all
                  select id, '访客' as type, concat(ifnull(visitor_name,'访客'),' 预约') as title, status,
                         create_time as createTime, 'visit' as biz
                  from visitor_appointment where tenant_id=?
                  union all
                  select id, '情绪' as type, left(ifnull(content,'情绪调解'),40) as title, status,
                         create_time as createTime, 'emotion' as biz
                  from ai_emotion_case where tenant_id=?
                ) u order by createTime desc limit 12
                """, tenantId, tenantId, tenantId));

        // 兼容旧字段
        data.put("repairTotal", repairTotal);
        data.put("repairPending", repairPending);
        data.put("noticeTotal", countSafe("select count(1) from notice_info where tenant_id=?", tenantId));
        data.put("userTotal", residentTotal + staffTotal);
        data.put("repairTrendLegacy", safeQuery(
                "select date(create_time) d, count(1) c from repair_order where tenant_id=? "
                        + "group by date(create_time) order by d desc limit 7",
                tenantId));
        return R.ok(data);
    }

    /**
     * 事件点位：报修优先置顶；按住户绑定房屋 / 文案解析楼栋，挂到对应楼栋索引。
     */
    private List<Map<String, Object>> buildMapMarkers(Long tenantId, List<Map<String, Object>> buildings) {
        List<String> buildingNames = new ArrayList<>();
        for (Map<String, Object> b : buildings) {
            Object name = b.get("name");
            if (name != null && !String.valueOf(name).isBlank()) {
                buildingNames.add(String.valueOf(name));
            }
        }
        List<Map<String, Object>> markers = new ArrayList<>();

        // 报修置顶：紧急优先，再按最新
        for (Map<String, Object> row : safeQuery(
                """
                select r.id, ifnull(r.title,'报修') as title, r.status, r.urgency,
                       r.create_time as createTime,
                       ifnull(r.facility_type,'') as facilityType,
                       ifnull(r.description,'') as description,
                       coalesce(nullif(r.building,''), h.building) as houseBuilding,
                       coalesce(nullif(r.location_label,''),
                                concat(ifnull(h.building,''), ifnull(h.unit,''), ifnull(h.room_no,''))) as houseLabel
                from repair_order r
                left join community_house h on h.id = coalesce(r.house_id, (
                    select uh.house_id from user_house_bind uh
                    where uh.user_id = r.user_id and uh.tenant_id = r.tenant_id
                    order by uh.is_default desc, uh.id asc
                    limit 1
                ))
                where r.tenant_id=? and r.status in ('pending','processing')
                order by
                  case when ifnull(r.urgency,'') like '%高%'
                            or ifnull(r.urgency,'') like '%紧急%'
                            or ifnull(r.urgency,'') like '%严%'
                            or ifnull(r.damage_level,'') like '%高%'
                            or ifnull(r.damage_level,'') like '%紧急%' then 0 else 1 end,
                  r.id desc
                limit 16
                """, tenantId)) {
            markers.add(toMarker(row, "repair", buildingNames));
        }
        for (Map<String, Object> row : safeQuery(
                """
                select v.id, '访客预约' as title, v.status,
                       v.create_time as createTime,
                       ifnull(v.visit_reason,'访客预约') as facilityType,
                       '' as description,
                       h.building as houseBuilding,
                       concat(ifnull(h.building,''), ifnull(h.unit,''), ifnull(h.room_no,'')) as houseLabel
                from visitor_appointment v
                left join community_house h on h.id = v.house_id
                where v.tenant_id=? and v.status in ('pending','approved')
                order by v.id desc limit 8
                """, tenantId)) {
            markers.add(toMarker(row, "visit", buildingNames));
        }
        for (Map<String, Object> row : safeQuery(
                """
                select e.id, '情绪预警' as title, e.status,
                       e.create_time as createTime,
                       ifnull(e.emotion_tags,'情绪') as facilityType,
                       '' as description,
                       h.building as houseBuilding,
                       concat(ifnull(h.building,''), ifnull(h.unit,''), ifnull(h.room_no,'')) as houseLabel
                from ai_emotion_case e
                left join community_house h on h.id = (
                    select uh.house_id from user_house_bind uh
                    where uh.user_id = e.user_id and uh.tenant_id = e.tenant_id
                    order by uh.is_default desc, uh.id asc
                    limit 1
                )
                where e.tenant_id=? and e.status in ('pending','handling')
                order by e.id desc limit 6
                """, tenantId)) {
            markers.add(toMarker(row, "emotion", buildingNames));
        }

        return markers;
    }

    private List<Map<String, Object>> buildingsFromMarkers(List<Map<String, Object>> markers) {
        LinkedHashMap<String, Integer> fallback = new LinkedHashMap<>();
        for (Map<String, Object> m : markers) {
            String b = str(m.get("building"));
            if (!b.isBlank()) {
                fallback.merge(b, 1, Integer::sum);
            }
        }
        List<Map<String, Object>> fb = new ArrayList<>();
        for (Map.Entry<String, Integer> e : fallback.entrySet()) {
            Map<String, Object> b = new LinkedHashMap<>();
            b.put("name", e.getKey());
            b.put("houses", e.getValue());
            b.put("occupied", 0);
            fb.add(b);
            if (fb.size() >= 12) break;
        }
        return fb;
    }

    private Map<String, Object> toMarker(Map<String, Object> row, String kind, List<String> buildingNames) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", row.get("id"));
        m.put("title", row.get("title"));
        m.put("status", row.get("status"));
        m.put("urgency", row.get("urgency"));
        m.put("createTime", row.get("createTime"));
        m.put("kind", kind);
        m.put("pinned", "repair".equals(kind));

        String houseBuilding = str(row.get("houseBuilding"));
        String houseLabel = str(row.get("houseLabel"));
        String facility = str(row.get("facilityType"));
        String desc = str(row.get("description"));
        String title = str(row.get("title"));

        String building = firstNonBlank(houseBuilding, matchBuilding(facility, buildingNames),
                matchBuilding(title + " " + desc + " " + houseLabel, buildingNames),
                matchBuildingLoose(title + " " + desc + " " + facility + " " + houseLabel));
        String place = firstNonBlank(houseLabel, facility, building, kindLabel(kind));
        m.put("building", building);
        m.put("place", place);
        attachBuildingIndex(m, buildingNames);
        return m;
    }

    private void attachBuildingIndex(Map<String, Object> m, List<String> buildingNames) {
        String building = str(m.get("building"));
        int idx = -1;
        if (!building.isBlank() && !buildingNames.isEmpty()) {
            for (int i = 0; i < buildingNames.size(); i++) {
                String n = buildingNames.get(i);
                if (n.equals(building) || n.contains(building) || building.contains(n)) {
                    idx = i;
                    break;
                }
            }
            if (idx < 0) {
                String digits = building.replaceAll("[^0-9A-Za-z]", "");
                for (int i = 0; i < buildingNames.size(); i++) {
                    String nd = buildingNames.get(i).replaceAll("[^0-9A-Za-z]", "");
                    if (!digits.isBlank() && digits.equalsIgnoreCase(nd)) {
                        idx = i;
                        break;
                    }
                }
            }
        }
        // 未匹配楼栋时均匀散列，避免叠在一起
        if (idx < 0 && !buildingNames.isEmpty()) {
            long id = 0;
            try {
                id = Long.parseLong(String.valueOf(m.get("id")));
            } catch (Exception ignored) {
            }
            idx = (int) Math.floorMod(id, buildingNames.size());
            if (building.isBlank()) {
                m.put("building", buildingNames.get(idx));
            }
        }
        m.put("buildingIndex", idx);
    }

    private String matchBuilding(String text, List<String> buildingNames) {
        if (text == null || text.isBlank() || buildingNames.isEmpty()) {
            return "";
        }
        for (String n : buildingNames) {
            if (text.contains(n)) {
                return n;
            }
        }
        return "";
    }

    /** 从文案中抓取「X栋/号楼/幢」 */
    private String matchBuildingLoose(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("([0-9A-Za-z一二三四五六七八九十]+)\\s*(栋|幢|号楼)")
                .matcher(text);
        if (m.find()) {
            return m.group(1) + m.group(2);
        }
        return "";
    }

    private String kindLabel(String kind) {
        if ("visit".equals(kind)) return "访客";
        if ("emotion".equals(kind)) return "情绪";
        return "报修";
    }

    private String str(Object v) {
        return v == null ? "" : String.valueOf(v).trim();
    }

    private String firstNonBlank(String... arr) {
        if (arr == null) return "";
        for (String s : arr) {
            if (s != null && !s.isBlank()) return s.trim();
        }
        return "";
    }

    private List<Map<String, Object>> buildAlerts(Long tenantId) {
        List<Map<String, Object>> alerts = new ArrayList<>();
        for (Map<String, Object> row : safeQuery(
                """
                select id, concat('紧急报修：', ifnull(title,'')) as content, create_time as createTime
                from repair_order
                where tenant_id=? and status in ('pending','processing')
                  and (urgency like '%高%' or urgency like '%紧急%' or urgency like '%严%')
                order by id desc limit 5
                """, tenantId)) {
            Map<String, Object> a = new HashMap<>(row);
            a.put("level", "high");
            a.put("type", "报修");
            alerts.add(a);
        }
        for (Map<String, Object> row : safeQuery(
                """
                select id, concat('情绪预警：', left(ifnull(content,''),36)) as content, create_time as createTime
                from ai_emotion_case
                where tenant_id=? and status in ('pending','handling')
                order by id desc limit 4
                """, tenantId)) {
            Map<String, Object> a = new HashMap<>(row);
            a.put("level", "mid");
            a.put("type", "情绪");
            alerts.add(a);
        }
        for (Map<String, Object> row : safeQuery(
                """
                select id, concat('巡检待办：', ifnull(title,'')) as content, create_time as createTime
                from insp_task
                where tenant_id=? and status in ('PENDING','DOING')
                order by id desc limit 4
                """, tenantId)) {
            Map<String, Object> a = new HashMap<>(row);
            a.put("level", "low");
            a.put("type", "巡检");
            alerts.add(a);
        }
        return alerts;
    }

    private String tenantName(Long tenantId) {
        try {
            return jdbcTemplate.queryForObject(
                    "select name from sys_tenant where id=? and ifnull(deleted,0)=0", String.class, tenantId);
        } catch (Exception e) {
            return "当前社区";
        }
    }

    /** SaaS 运营总大屏：汇总，不含居民隐私明细 */
    @GetMapping("/saas/dashboard")
    public R<Map<String, Object>> saasDashboard() {
        if (!"platform_admin".equals(TenantContext.getRole()) && !TenantContext.isSkipTenant()) {
            // 允许网关透传后访问
        }
        Map<String, Object> data = new HashMap<>();
        data.put("tenantTotal", count("select count(1) from sys_tenant"));
        data.put("tenantActive", count("select count(1) from sys_tenant where status='active'"));
        data.put("tenantExpired", count("select count(1) from sys_tenant where status in ('degraded','frozen') or expire_time < now()"));
        data.put("orderPaidAmount", jdbcTemplate.queryForObject(
                "select ifnull(sum(amount),0) from saas_order where status='paid'", Object.class));
        data.put("orderCount", count("select count(1) from saas_order where status='paid'"));
        data.put("orderRefundAmount", jdbcTemplate.queryForObject(
                "select ifnull(sum(amount),0) from saas_order where status='refunded'", Object.class));
        data.put("orderRefundCount", count("select count(1) from saas_order where status='refunded'"));
        data.put("packageDist", jdbcTemplate.queryForList(
                "select package_name name, count(1) value from saas_order where status='paid' group by package_name"));
        data.put("tenantActivity", jdbcTemplate.queryForList(
                "select t.name, (select count(1) from repair_order r where r.tenant_id=t.id) repair_cnt from sys_tenant t order by repair_cnt desc limit 10"));
        data.put("orderByType", jdbcTemplate.queryForList(
                "select order_type type, count(1) cnt, ifnull(sum(amount),0) amount "
                        + "from saas_order where status='paid' group by order_type"));
        data.put("monthlyTrend", jdbcTemplate.queryForList(
                """
                select date_format(pay_time, '%Y-%m') month,
                       count(1) paidCount,
                       ifnull(sum(amount),0) paidAmount
                from saas_order
                where status='paid' and pay_time >= date_sub(curdate(), interval 5 month)
                group by date_format(pay_time, '%Y-%m')
                order by month
                """));
        data.put("workOrderSummary", safeQuery(
                """
                select '待处理报修' name, count(1) cnt from repair_order where status='pending'
                union all
                select '处理中报修' name, count(1) cnt from repair_order where status='processing'
                union all
                select '今日访客预约' name, count(1) cnt from visitor_appointment where date(create_time)=curdate()
                union all
                select '开放中失物' name, count(1) cnt from lost_found where status='open'
                union all
                select '近24h邻里动态' name, count(1) cnt from emotion_appeal
                  where ifnull(deleted,0)=0 and create_time >= date_sub(now(), interval 1 day)
                """));
        data.put("tenantMap", safeQuery(
                """
                select id, name, status, package_id packageId, expire_time expireTime,
                       datediff(expire_time, now()) daysLeft,
                       ifnull(ai_quota, 0) aiQuota
                from sys_tenant
                where ifnull(deleted,0)=0
                order by id
                limit 80
                """));
        data.put("expiringList", safeQuery(
                """
                select id, name, status, expire_time expireTime,
                       datediff(expire_time, now()) daysLeft
                from sys_tenant
                where ifnull(deleted,0)=0 and status <> 'disabled'
                  and expire_time is not null
                  and expire_time <= date_add(now(), interval 30 day)
                order by expire_time asc
                limit 12
                """));
        return R.ok(data);
    }

    private List<Map<String, Object>> safeQuery(String sql, Object... args) {
        try {
            return jdbcTemplate.queryForList(sql, args);
        } catch (Exception e) {
            return List.of();
        }
    }

    @GetMapping("/export/tenant")
    public R<List<Map<String, Object>>> exportTenant() {
        Long tenantId = TenantContext.getTenantId();
        return R.ok(jdbcTemplate.queryForList(
                "select id,title,status,facility_type,create_time from repair_order where tenant_id=? order by id desc limit 1000",
                tenantId));
    }

    private long count(String sql, Object... args) {
        Long v = jdbcTemplate.queryForObject(sql, Long.class, args);
        return v == null ? 0 : v;
    }

    private long countSafe(String sql, Object... args) {
        try {
            return count(sql, args);
        } catch (Exception e) {
            return 0;
        }
    }
}
