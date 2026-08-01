package com.community.repair.service;

import com.community.common.constant.RoleConstants;
import com.community.common.exception.BizException;
import com.community.common.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 社区活动：分类、报名台账、统计、定向推送、报名同步日程
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ActivityBizService {

    private final JdbcTemplate jdbcTemplate;

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

    public List<Map<String, Object>> listCategories(Boolean enabledOnly) {
        requireTenant();
        String sql = "select id,tenant_id as tenantId,name,tags,sort_no as sortNo,status,remark,create_time as createTime "
                + "from activity_category where tenant_id=? ";
        List<Object> args = new ArrayList<>();
        args.add(TenantContext.getTenantId());
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
            throw new BizException(400, "分类名称不能为空");
        }
        String tags = str(body.get("tags"), "");
        int sortNo = toInt(body.get("sortNo"), 0);
        int status = toInt(body.get("status"), 1);
        String remark = str(body.get("remark"), null);
        if (id != null && id > 0) {
            jdbcTemplate.update(
                    "update activity_category set name=?,tags=?,sort_no=?,status=?,remark=? where id=? and tenant_id=?",
                    name, tags, sortNo, status, remark, id, tid);
        } else {
            jdbcTemplate.update(
                    "insert into activity_category(tenant_id,name,tags,sort_no,status,remark,create_time) values(?,?,?,?,?,?,now())",
                    tid, name, tags, sortNo, status, remark);
            id = jdbcTemplate.queryForObject("select last_insert_id()", Long.class);
        }
        return jdbcTemplate.queryForMap(
                "select id,tenant_id as tenantId,name,tags,sort_no as sortNo,status,remark,create_time as createTime from activity_category where id=?",
                id);
    }

    public void deleteCategory(Long id) {
        assertAdmin();
        requireTenant();
        jdbcTemplate.update("delete from activity_category where id=? and tenant_id=?", id, TenantContext.getTenantId());
    }

    /** 删除活动及其报名记录（按租户隔离） */
    public void deleteActivity(Long id) {
        assertAdmin();
        requireTenant();
        Long tid = TenantContext.getTenantId();
        Integer exists = jdbcTemplate.query(
                "select 1 from community_activity where id=? and tenant_id=? limit 1",
                rs -> rs.next() ? 1 : null, id, tid);
        if (exists == null) {
            throw new BizException(404, "活动不存在");
        }
        jdbcTemplate.update("delete from activity_signup where activity_id=? and tenant_id=?", id, tid);
        jdbcTemplate.update("delete from community_activity where id=? and tenant_id=?", id, tid);
    }

    /* ========== 报名台账 ========== */

    public List<Map<String, Object>> listAllSignups(Long activityId, String status, String keyword) {
        assertStaff();
        requireTenant();
        String sql = "select s.id,s.tenant_id as tenantId,s.activity_id as activityId,s.user_id as userId,"
                + "s.user_name as userName,s.phone,s.status,s.create_time as createTime,"
                + "a.title as activityTitle,a.category,a.start_time as startTime,a.end_time as endTime "
                + "from activity_signup s left join community_activity a on a.id=s.activity_id "
                + "where s.tenant_id=? ";
        List<Object> args = new ArrayList<>();
        args.add(TenantContext.getTenantId());
        if (activityId != null) {
            sql += "and s.activity_id=? ";
            args.add(activityId);
        }
        if (StringUtils.hasText(status)) {
            sql += "and s.status=? ";
            args.add(status);
        }
        if (StringUtils.hasText(keyword)) {
            sql += "and (s.user_name like ? or s.phone like ? or a.title like ?) ";
            String like = "%" + keyword.trim() + "%";
            args.add(like);
            args.add(like);
            args.add(like);
        }
        sql += "order by s.id desc limit 500";
        return jdbcTemplate.queryForList(sql, args.toArray());
    }

    public void cancelSignup(Long signupId, String remark) {
        assertStaff();
        requireTenant();
        Map<String, Object> row;
        try {
            row = jdbcTemplate.queryForMap(
                    "select * from activity_signup where id=? and tenant_id=?",
                    signupId, TenantContext.getTenantId());
        } catch (Exception e) {
            throw new BizException(404, "报名记录不存在");
        }
        if ("cancelled".equals(String.valueOf(row.get("status")))) {
            return;
        }
        jdbcTemplate.update("update activity_signup set status='cancelled' where id=?", signupId);
        Long activityId = toLong(row.get("activity_id"));
        if (activityId != null) {
            jdbcTemplate.update(
                    "update community_activity set signed_count=greatest(ifnull(signed_count,1)-1,0) where id=? and tenant_id=?",
                    activityId, TenantContext.getTenantId());
        }
    }

    /* ========== 统计 ========== */

    public Map<String, Object> stats() {
        assertStaff();
        requireTenant();
        Long tid = TenantContext.getTenantId();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("activityTotal", cnt("select count(1) from community_activity where tenant_id=?", tid));
        m.put("onlineTotal", cnt("select count(1) from community_activity where tenant_id=? and status=1", tid));
        m.put("signupTotal", cnt("select count(1) from activity_signup where tenant_id=? and status='signed'", tid));
        m.put("checkedTotal", cnt("select count(1) from activity_signup where tenant_id=? and status='checked'", tid));
        m.put("cancelledTotal", cnt("select count(1) from activity_signup where tenant_id=? and status='cancelled'", tid));

        List<Map<String, Object>> byCategory = new ArrayList<>();
        try {
            byCategory = jdbcTemplate.queryForList(
                    "select ifnull(nullif(category,''),'未分类') as name, count(1) as value "
                            + "from community_activity where tenant_id=? group by name order by value desc",
                    tid);
        } catch (Exception ignored) {
        }
        m.put("byCategory", byCategory);

        List<Map<String, Object>> topSignup = new ArrayList<>();
        try {
            topSignup = jdbcTemplate.queryForList(
                    "select id,title,ifnull(signed_count,0) as signedCount,ifnull(max_people,0) as maxPeople "
                            + "from community_activity where tenant_id=? order by ifnull(signed_count,0) desc limit 10",
                    tid);
        } catch (Exception ignored) {
        }
        m.put("topSignup", topSignup);

        List<Map<String, Object>> recentSignups = new ArrayList<>();
        try {
            recentSignups = jdbcTemplate.queryForList(
                    "select date(create_time) as day, count(1) as value from activity_signup "
                            + "where tenant_id=? and create_time>=date_sub(now(), interval 14 day) "
                            + "group by date(create_time) order by day",
                    tid);
        } catch (Exception ignored) {
        }
        m.put("recentSignups", recentSignups);
        return m;
    }

    /* ========== 定向推送 ========== */

    public Map<String, Object> targetPush(Long activityId, String targetRule) {
        assertAdmin();
        requireTenant();
        Long tid = TenantContext.getTenantId();
        Map<String, Object> act;
        try {
            act = jdbcTemplate.queryForMap("select * from community_activity where id=? and tenant_id=?", activityId, tid);
        } catch (Exception e) {
            throw new BizException(404, "活动不存在");
        }
        String title = str(act.get("title"), "社区活动");
        String summary = str(act.get("summary"), title);
        String rule = StringUtils.hasText(targetRule) ? targetRule : str(act.get("target_rule"), str(act.get("tags"), ""));
        List<Long> userIds = new ArrayList<>();
        try {
            List<Map<String, Object>> profiles = jdbcTemplate.queryForList(
                    "select * from ai_user_profile where tenant_id=?", tid);
            if (profiles.isEmpty()) {
                List<Map<String, Object>> users = jdbcTemplate.queryForList(
                        "select id from sys_user where tenant_id=? and role='resident' and status=1 limit 200", tid);
                for (Map<String, Object> u : users) {
                    userIds.add(toLong(u.get("id")));
                }
            } else {
                for (Map<String, Object> p : profiles) {
                    if (matchProfile(p, rule, str(act.get("tags"), ""), str(act.get("category"), ""))) {
                        userIds.add(toLong(p.get("user_id")));
                    }
                }
            }
        } catch (Exception e) {
            log.warn("target push profile fail: {}", e.getMessage());
        }
        int pushed = 0;
        for (Long uid : userIds) {
            if (uid == null) continue;
            try {
                jdbcTemplate.update(
                        "insert into sys_message(tenant_id,user_id,title,content,msg_type,read_flag,create_time) values(?,?,?,?,?,?,now())",
                        tid, uid, "活动推荐：" + title, summary, "activity_target", 0);
                try {
                    jdbcTemplate.update(
                            "insert into ai_notice_push_log(tenant_id,notice_id,user_id,reason,create_time) values(?,?,?,?,now())",
                            tid, activityId, uid, "activity:" + (StringUtils.hasText(rule) ? rule : "all"));
                } catch (Exception ignored) {
                }
                pushed++;
            } catch (Exception ignored) {
            }
        }
        try {
            jdbcTemplate.update(
                    "update community_activity set push_count=ifnull(push_count,0)+?, target_rule=ifnull(?,target_rule) where id=? and tenant_id=?",
                    pushed, StringUtils.hasText(targetRule) ? targetRule : null, activityId, tid);
        } catch (Exception ignored) {
        }
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("activityId", activityId);
        resp.put("pushed", pushed);
        return resp;
    }

    /** 平台端：全域商圈/社区活动总览（跨租户） */
    public List<Map<String, Object>> listPlatformActivities(Long tenantId, String keyword, String category) {
        if (!RoleConstants.isPlatformAdmin(TenantContext.getRole())) {
            throw new BizException(403, "仅平台运营可查看全域活动");
        }
        StringBuilder sql = new StringBuilder(
                "select a.id, a.tenant_id as tenantId, a.title, a.category, a.summary, a.tags, a.location, "
                        + "a.start_time as startTime, a.end_time as endTime, a.max_people as maxPeople, "
                        + "a.signed_count as signedCount, a.status, a.ai_audit_pass as aiAuditPass, "
                        + "a.push_count as pushCount, a.create_time as createTime, "
                        + "ifnull(t.name, concat('小区',a.tenant_id)) as tenantName "
                        + "from community_activity a "
                        + "left join sys_tenant t on t.id=a.tenant_id where 1=1 ");
        List<Object> args = new ArrayList<>();
        if (tenantId != null) {
            sql.append("and a.tenant_id=? ");
            args.add(tenantId);
        }
        if (StringUtils.hasText(category)) {
            sql.append("and a.category=? ");
            args.add(category.trim());
        } else {
            // 默认聚焦社区超市相关活动
            sql.append("and (ifnull(a.category,'') like '%超市%' or ifnull(a.title,'') like '%超市%' "
                    + "or ifnull(a.tags,'') like '%超市%' or ifnull(a.category,'') like '%促销%' "
                    + "or ifnull(a.tags,'') like '%商城%' or ifnull(a.tags,'') like '%商超%') ");
        }
        if (StringUtils.hasText(keyword)) {
            sql.append("and (a.title like ? or ifnull(a.summary,'') like ? or ifnull(t.name,'') like ?) ");
            String like = "%" + keyword.trim() + "%";
            args.add(like);
            args.add(like);
            args.add(like);
        }
        sql.append("order by a.id desc limit 500");
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql.toString(), args.toArray());
        LocalDateTime now = LocalDateTime.now();
        for (Map<String, Object> m : rows) {
            Object st = m.get("startTime");
            Object et = m.get("endTime");
            String phase = "未开始";
            try {
                if (et != null) {
                    LocalDateTime end = et instanceof java.sql.Timestamp ts
                            ? ts.toLocalDateTime() : LocalDateTime.parse(String.valueOf(et).replace(' ', 'T'));
                    if (!end.isAfter(now)) phase = "已结束";
                    else if (st != null) {
                        LocalDateTime start = st instanceof java.sql.Timestamp ts2
                                ? ts2.toLocalDateTime() : LocalDateTime.parse(String.valueOf(st).replace(' ', 'T'));
                        if (!start.isAfter(now)) phase = "进行中";
                    }
                }
            } catch (Exception ignored) {
            }
            m.put("phase", phase);
            m.put("statusLabel", toInt(m.get("status"), 1) == 1 ? "上架" : "下架");
            if (m.get("startTime") instanceof java.sql.Timestamp ts) {
                m.put("startTime", ts.toLocalDateTime().toString().replace('T', ' '));
            }
            if (m.get("endTime") instanceof java.sql.Timestamp ts) {
                m.put("endTime", ts.toLocalDateTime().toString().replace('T', ' '));
            }
            if (m.get("createTime") instanceof java.sql.Timestamp ts) {
                m.put("createTime", ts.toLocalDateTime().toString().replace('T', ' '));
            }
        }
        return rows;
    }

    public Map<String, Object> platformSetActivityStatus(Long id, int status) {
        if (!RoleConstants.isPlatformAdmin(TenantContext.getRole())) {
            throw new BizException(403, "仅平台运营可操作");
        }
        int n = jdbcTemplate.update("update community_activity set status=? where id=?", status, id);
        if (n == 0) throw new BizException(404, "活动不存在");
        return jdbcTemplate.queryForMap(
                "select id, tenant_id as tenantId, title, status from community_activity where id=?", id);
    }

    private boolean matchProfile(Map<String, Object> p, String rule, String tags, String category) {
        String age = str(p.get("age_band"), "");
        int hasChild = toInt(p.get("has_child"), 0);
        String interests = str(p.get("interests"), "");
        String blob = (rule + "," + tags + "," + category).toLowerCase(Locale.ROOT);
        if (!StringUtils.hasText(blob) || blob.contains("all")) {
            return true;
        }
        if (blob.contains("老年") || blob.contains("senior")) {
            return "senior".equals(age) || "elderly".equals(age);
        }
        if (blob.contains("亲子") || blob.contains("儿童") || blob.contains("child")) {
            return hasChild == 1;
        }
        for (String part : interests.split("[,，]")) {
            if (StringUtils.hasText(part) && blob.contains(part.trim().toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        for (String t : tags.split("[,，]")) {
            if (StringUtils.hasText(t) && interests.contains(t.trim())) {
                return true;
            }
        }
        return false;
    }

    /* ========== 报名同步 AI 日程 ========== */

    public void syncSignupToSchedule(Long activityId, Long userId) {
        if (activityId == null || userId == null) return;
        Long tid = TenantContext.getTenantId();
        if (tid == null) return;
        try {
            Map<String, Object> act = jdbcTemplate.queryForMap(
                    "select * from community_activity where id=? and tenant_id=?", activityId, tid);
            String title = "活动报名：" + str(act.get("title"), "");
            Object start = act.get("start_time");
            Object end = act.get("end_time");
            Object remind = act.get("signup_end");
            if (remind == null) remind = start;
            Integer exists = jdbcTemplate.queryForObject(
                    "select count(1) from ai_schedule_remind where tenant_id=? and user_id=? and source_type='ACTIVITY' and source_id=? and status in ('pending','sent')",
                    Integer.class, tid, userId, activityId);
            if (exists != null && exists > 0) return;
            jdbcTemplate.update(
                    "insert into ai_schedule_remind(tenant_id,user_id,title,content,remind_time,status,create_time,source_type,source_id,start_time,end_time,jump_path,conflict_flag) "
                            + "values(?,?,?,?,?,'pending',now(),'ACTIVITY',?,?,?,?,0)",
                    tid, userId, title,
                    "您已报名「" + str(act.get("title"), "") + "」，请按时参加",
                    remind, activityId, start, end,
                    "/pages/activity/detail?id=" + activityId);
        } catch (Exception e) {
            log.debug("sync signup schedule skip: {}", e.getMessage());
        }
    }

    private int cnt(String sql, Object... args) {
        try {
            Integer n = jdbcTemplate.queryForObject(sql, Integer.class, args);
            return n == null ? 0 : n;
        } catch (Exception e) {
            return 0;
        }
    }

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
}
