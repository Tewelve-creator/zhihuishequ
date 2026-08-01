package com.community.ai.service;

import com.community.common.constant.RoleConstants;
import com.community.common.exception.BizException;
import com.community.common.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * AI 特色：画像 / 日程生成与冲突 / 便民推荐 / 识图日志 / 情绪工单 / 词库 / 定向推送
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiFeatureService {

    private final JdbcTemplate jdbcTemplate;

    private void requireTenant() {
        if (TenantContext.getTenantId() == null) {
            throw new BizException(400, "缺少社区租户");
        }
    }

    private void assertStaff() {
        String role = TenantContext.getRole();
        if (!RoleConstants.isTenantStaff(role) && !RoleConstants.isPlatformAdmin(role)) {
            throw new BizException(403, "仅社区管理员/网格员可操作");
        }
    }

    /* ===================== 画像 ===================== */

    public Map<String, Object> getProfile() {
        requireTenant();
        Long tid = TenantContext.getTenantId();
        Long uid = TenantContext.getUserId();
        if (uid == null) {
            throw new BizException(401, "未登录");
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "select * from ai_user_profile where tenant_id=? and user_id=? limit 1", tid, uid);
        if (rows.isEmpty()) {
            Map<String, Object> empty = new LinkedHashMap<>();
            empty.put("tenantId", tid);
            empty.put("userId", uid);
            empty.put("ageBand", "adult");
            empty.put("interests", "便民,物业");
            empty.put("hasChild", 0);
            empty.put("livingNeeds", "");
            empty.put("building", "");
            return empty;
        }
        return mapProfile(rows.get(0));
    }

    public Map<String, Object> saveProfile(Map<String, Object> body) {
        requireTenant();
        Long tid = TenantContext.getTenantId();
        Long uid = TenantContext.getUserId();
        if (uid == null) {
            throw new BizException(401, "未登录");
        }
        String ageBand = str(body.get("ageBand"), "adult");
        String interests = str(body.get("interests"), "");
        int hasChild = toInt(body.get("hasChild"), 0);
        String livingNeeds = str(body.get("livingNeeds"), "");
        String building = str(body.get("building"), "");
        Integer exists = jdbcTemplate.queryForObject(
                "select count(1) from ai_user_profile where tenant_id=? and user_id=?", Integer.class, tid, uid);
        if (exists != null && exists > 0) {
            jdbcTemplate.update(
                    "update ai_user_profile set age_band=?,interests=?,has_child=?,living_needs=?,building=?,update_time=now() where tenant_id=? and user_id=?",
                    ageBand, interests, hasChild, livingNeeds, building, tid, uid);
        } else {
            jdbcTemplate.update(
                    "insert into ai_user_profile(tenant_id,user_id,age_band,interests,has_child,living_needs,building,create_time,update_time) values(?,?,?,?,?,?,?,now(),now())",
                    tid, uid, ageBand, interests, hasChild, livingNeeds, building);
        }
        return getProfile();
    }

    private Map<String, Object> mapProfile(Map<String, Object> row) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", row.get("id"));
        m.put("tenantId", row.get("tenant_id"));
        m.put("userId", row.get("user_id"));
        m.put("ageBand", row.get("age_band"));
        m.put("interests", row.get("interests"));
        m.put("hasChild", row.get("has_child"));
        m.put("livingNeeds", row.get("living_needs"));
        m.put("building", row.get("building"));
        return m;
    }

    public void trackBehavior(String eventType, String bizId) {
        requireTenant();
        Long uid = TenantContext.getUserId();
        if (uid == null || !StringUtils.hasText(eventType)) {
            return;
        }
        try {
            jdbcTemplate.update(
                    "insert into ai_behavior_event(tenant_id,user_id,event_type,biz_id,create_time) values(?,?,?,?,now())",
                    TenantContext.getTenantId(), uid, eventType, bizId);
        } catch (Exception e) {
            log.debug("track behavior skip: {}", e.getMessage());
        }
    }

    /* ===================== 日程生成 / 冲突 / 台账 ===================== */

    /**
     * 业主一键生成个性化日程：
     * 同步公告/活动/缴费/维保回访/访客预约，结合画像过滤，按配置提前提醒并检测冲突。
     */
    public Map<String, Object> generateSchedule() {
        requireTenant();
        Long tid = TenantContext.getTenantId();
        Long uid = TenantContext.getUserId();
        if (uid == null) {
            throw new BizException(401, "未登录");
        }
        Map<String, Object> profile = getProfile();
        Map<String, Integer> ahead = loadRemindAheadHours(tid);
        int created = 0;
        // 公告 → 归入便民/通知类（展示时映射）
        try {
            List<Map<String, Object>> notices = jdbcTemplate.queryForList(
                    "select id,title,summary,publish_time from notice_info where tenant_id=? and status=1 and (publish_time is null or publish_time<=now()) order by id desc limit 8",
                    tid);
            for (Map<String, Object> n : notices) {
                LocalDateTime base = toTime(n.get("publish_time"), LocalDateTime.now().plusHours(2));
                created += insertRemindIfAbsent(tid, uid, "NOTICE", toLong(n.get("id")),
                        "公告：" + str(n.get("title"), ""),
                        str(n.get("summary"), str(n.get("title"), "")),
                        base.minusHours(ahead.getOrDefault("NOTICE", 2)),
                        null, null, "/pages/notice/detail?id=" + n.get("id"));
            }
        } catch (Exception e) {
            log.debug("sync notice skip: {}", e.getMessage());
        }
        // 社区活动（画像过滤：亲子/老年）
        try {
            List<Map<String, Object>> acts = jdbcTemplate.queryForList(
                    "select id,title,signup_end,start_time,end_time from community_activity where tenant_id=? and status=1 order by id desc limit 8",
                    tid);
            String age = str(profile.get("ageBand"), "");
            int hasChild = toInt(profile.get("hasChild"), 0);
            for (Map<String, Object> a : acts) {
                String title = str(a.get("title"), "社区活动");
                boolean match = true;
                if (title.contains("老年") && !("senior".equals(age) || "elderly".equals(age))) {
                    match = false;
                }
                if ((title.contains("亲子") || title.contains("儿童")) && hasChild == 0) {
                    match = false;
                }
                if (!match) {
                    continue;
                }
                LocalDateTime eventAt = toTime(a.get("start_time"),
                        toTime(a.get("signup_end"), LocalDateTime.now().plusDays(1)));
                created += insertRemindIfAbsent(tid, uid, "ACTIVITY", toLong(a.get("id")),
                        "活动提醒：" + title,
                        "报名/活动时间将至，请留意日程冲突",
                        eventAt.minusHours(ahead.getOrDefault("ACTIVITY", 6)),
                        toTime(a.get("start_time"), null),
                        toTime(a.get("end_time"), null),
                        "/pages/activity/detail?id=" + a.get("id"));
            }
        } catch (Exception e) {
            log.debug("sync activity skip: {}", e.getMessage());
        }
        // 物业费提醒
        try {
            List<Map<String, Object>> fees = jdbcTemplate.queryForList(
                    "select id,fee_type,amount,period,due_date from fee_bill where tenant_id=? and user_id=? and status in ('UNPAID','unpaid','欠费') order by id desc limit 5",
                    tid, uid);
            for (Map<String, Object> f : fees) {
                LocalDateTime due = toTime(f.get("due_date"), LocalDateTime.now().plusDays(1));
                created += insertRemindIfAbsent(tid, uid, "FEE", toLong(f.get("id")),
                        "缴费提醒：" + str(f.get("fee_type"), "物业费"),
                        "待缴 ¥" + f.get("amount") + "，账期 " + str(f.get("period"), ""),
                        due.minusHours(ahead.getOrDefault("FEE", 24)),
                        null, null, "/pages/payment/index");
            }
        } catch (Exception e) {
            log.debug("sync fee skip: {}", e.getMessage());
        }
        // 维保/报修回访：已完工工单
        try {
            List<Map<String, Object>> repairs = jdbcTemplate.queryForList(
                    "select id,title,update_time from repair_order where tenant_id=? and user_id=? "
                            + "and status in ('DONE','done','FINISHED','finished','CLOSED','closed','已完成') "
                            + "order by id desc limit 5",
                    tid, uid);
            for (Map<String, Object> r : repairs) {
                LocalDateTime fin = toTime(r.get("update_time"), LocalDateTime.now());
                LocalDateTime visitAt = fin.plusDays(3);
                created += insertRemindIfAbsent(tid, uid, "MAINT", toLong(r.get("id")),
                        "维保回访：" + str(r.get("title"), "工单回访"),
                        "工单已完工，请确认服务满意度并反馈",
                        visitAt.minusHours(ahead.getOrDefault("MAINT", 24)),
                        visitAt, visitAt.plusHours(2),
                        "/pages/repair/detail?id=" + r.get("id"));
            }
        } catch (Exception e) {
            log.debug("sync maint skip: {}", e.getMessage());
        }
        // 便民预约：访客预约
        try {
            List<Map<String, Object>> visits = jdbcTemplate.queryForList(
                    "select id,visitor_name,visit_time,expire_time from visitor_appointment "
                            + "where tenant_id=? and user_id=? and status in ('approved','APPROVED') "
                            + "and visit_time>=now() order by visit_time asc limit 5",
                    tid, uid);
            for (Map<String, Object> v : visits) {
                LocalDateTime vt = toTime(v.get("visit_time"), LocalDateTime.now().plusDays(1));
                created += insertRemindIfAbsent(tid, uid, "CONVENIENCE", toLong(v.get("id")),
                        "便民预约：访客 " + str(v.get("visitor_name"), ""),
                        "访客预约将至，请留意通行与冲突",
                        vt.minusHours(ahead.getOrDefault("CONVENIENCE", 12)),
                        vt, toTime(v.get("expire_time"), vt.plusHours(2)),
                        "/pages/visitor/list");
            }
        } catch (Exception e) {
            log.debug("sync convenience skip: {}", e.getMessage());
        }
        // 画像个性化：老年便民提醒
        if ("senior".equals(str(profile.get("ageBand"), "")) || "elderly".equals(str(profile.get("ageBand"), ""))) {
            created += insertRemindIfAbsent(tid, uid, "CONVENIENCE", 0L,
                    "便民提醒：疫苗接种", "建议关注社区卫生服务中心接种安排",
                    LocalDateTime.now().plusDays(3).minusHours(ahead.getOrDefault("CONVENIENCE", 12)),
                    null, null, "/pages/notice/list");
        }
        int conflicts = markConflicts(tid, uid);
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("created", created);
        resp.put("conflicts", conflicts);
        resp.put("items", listMyScheduleEnhanced());
        return resp;
    }

    private int insertRemindIfAbsent(Long tid, Long uid, String sourceType, Long sourceId,
                                     String title, String content, LocalDateTime remindTime,
                                     LocalDateTime start, LocalDateTime end, String jump) {
        try {
            Integer n = jdbcTemplate.queryForObject(
                    "select count(1) from ai_schedule_remind where tenant_id=? and user_id=? and source_type=? and ifnull(source_id,0)=? and status in ('pending','sent')",
                    Integer.class, tid, uid, sourceType, sourceId == null ? 0L : sourceId);
            if (n != null && n > 0) {
                return 0;
            }
            jdbcTemplate.update(
                    "insert into ai_schedule_remind(tenant_id,user_id,title,content,remind_time,status,create_time,source_type,source_id,start_time,end_time,jump_path,conflict_flag) values(?,?,?,?,?,'pending',now(),?,?,?,?,?,0)",
                    tid, uid, title, content, remindTime, sourceType, sourceId, start, end, jump);
            return 1;
        } catch (Exception e) {
            log.debug("insert remind skip: {}", e.getMessage());
            return 0;
        }
    }

    public List<Map<String, Object>> listMyScheduleEnhanced() {
        requireTenant();
        Long uid = TenantContext.getUserId();
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "select * from ai_schedule_remind where tenant_id=? and user_id=? order by ifnull(start_time,remind_time) asc limit 80",
                TenantContext.getTenantId(), uid);
        List<Map<String, Object>> list = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            list.add(mapRemind(r));
        }
        return list;
    }

    public int markConflicts(Long tid, Long uid) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "select id,start_time,end_time,remind_time from ai_schedule_remind where tenant_id=? and user_id=? and status='pending'",
                tid, uid);
        Set<Long> conflictIds = new HashSet<>();
        for (int i = 0; i < rows.size(); i++) {
            LocalDateTime s1 = toTime(rows.get(i).get("start_time"), toTime(rows.get(i).get("remind_time"), null));
            LocalDateTime e1 = toTime(rows.get(i).get("end_time"), s1 == null ? null : s1.plusHours(2));
            if (s1 == null) continue;
            for (int j = i + 1; j < rows.size(); j++) {
                LocalDateTime s2 = toTime(rows.get(j).get("start_time"), toTime(rows.get(j).get("remind_time"), null));
                LocalDateTime e2 = toTime(rows.get(j).get("end_time"), s2 == null ? null : s2.plusHours(2));
                if (s2 == null) continue;
                if (s1.isBefore(e2) && s2.isBefore(e1)) {
                    conflictIds.add(toLong(rows.get(i).get("id")));
                    conflictIds.add(toLong(rows.get(j).get("id")));
                }
            }
        }
        for (Long id : conflictIds) {
            jdbcTemplate.update("update ai_schedule_remind set conflict_flag=1 where id=?", id);
        }
        jdbcTemplate.update(
                "update ai_schedule_remind set conflict_flag=0 where tenant_id=? and user_id=? and id not in ("
                        + (conflictIds.isEmpty() ? "0" : String.join(",", conflictIds.stream().map(String::valueOf).toList()))
                        + ")",
                tid, uid);
        if (!conflictIds.isEmpty()) {
            try {
                jdbcTemplate.update(
                        "insert into sys_message(tenant_id,user_id,title,content,msg_type,read_flag,create_time) values(?,?,?,?,?,?,now())",
                        tid, uid, "日程冲突提醒",
                        "检测到 " + conflictIds.size() + " 条日程存在时间冲突，请调整报名或预约时间。",
                        "schedule_conflict", 0);
            } catch (Exception ignored) {
            }
        }
        return conflictIds.size();
    }

    public Map<String, Object> conflictCheck() {
        requireTenant();
        int n = markConflicts(TenantContext.getTenantId(), TenantContext.getUserId());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("conflictCount", n);
        m.put("items", listMyScheduleEnhanced().stream().filter(x -> Objects.equals(x.get("conflictFlag"), 1)).toList());
        return m;
    }

    /**
     * 物业台账列表：支持业主/类型/冲突/时间区间筛选
     */
    public List<Map<String, Object>> adminSchedule(String status, String keyword, Long userId,
                                                   String scheduleType, Integer conflict,
                                                   String startTime, String endTime, int limit) {
        assertStaff();
        requireTenant();
        Long tid = TenantContext.getTenantId();
        StringBuilder sql = new StringBuilder(
                "select r.*, u.nickname as user_nickname, u.phone as user_phone "
                        + "from ai_schedule_remind r "
                        + "left join sys_user u on u.id=r.user_id "
                        + "where r.tenant_id=? ");
        List<Object> args = new ArrayList<>();
        args.add(tid);
        if (StringUtils.hasText(status)) {
            sql.append("and r.status=? ");
            args.add(status.trim());
        }
        if (userId != null && userId > 0) {
            sql.append("and r.user_id=? ");
            args.add(userId);
        }
        if (StringUtils.hasText(keyword)) {
            sql.append("and (u.nickname like ? or u.phone like ? or cast(r.user_id as char) like ? or r.title like ?) ");
            String kw = "%" + keyword.trim() + "%";
            args.add(kw);
            args.add(kw);
            args.add(kw);
            args.add(kw);
        }
        appendScheduleTypeFilter(sql, args, scheduleType);
        if (conflict != null) {
            sql.append("and ifnull(r.conflict_flag,0)=? ");
            args.add(conflict);
        }
        if (StringUtils.hasText(startTime)) {
            sql.append("and ifnull(r.start_time,r.remind_time) >= ? ");
            args.add(startTime.trim().replace('T', ' '));
        }
        if (StringUtils.hasText(endTime)) {
            sql.append("and ifnull(r.start_time,r.remind_time) <= ? ");
            args.add(endTime.trim().replace('T', ' '));
        }
        sql.append("order by ifnull(r.conflict_flag,0) desc, r.id desc limit ?");
        args.add(Math.min(Math.max(limit, 1), 500));
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql.toString(), args.toArray());
        List<Map<String, Object>> list = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            list.add(mapRemind(r));
        }
        return list;
    }

    /** 兼容旧签名 */
    public List<Map<String, Object>> adminSchedule(String status, int limit) {
        return adminSchedule(status, null, null, null, null, null, null, limit);
    }

    /**
     * 台账统计：总数 / 冲突数 / 待推送 + 类型分布（饼图）
     */
    public Map<String, Object> adminScheduleStats() {
        assertStaff();
        requireTenant();
        Long tid = TenantContext.getTenantId();
        Integer total = jdbcTemplate.queryForObject(
                "select count(1) from ai_schedule_remind where tenant_id=?", Integer.class, tid);
        Integer pending = jdbcTemplate.queryForObject(
                "select count(1) from ai_schedule_remind where tenant_id=? and status='pending'", Integer.class, tid);
        Integer conflict = jdbcTemplate.queryForObject(
                "select count(1) from ai_schedule_remind where tenant_id=? and ifnull(conflict_flag,0)=1", Integer.class, tid);
        Integer sent = jdbcTemplate.queryForObject(
                "select count(1) from ai_schedule_remind where tenant_id=? and status='sent'", Integer.class, tid);

        // 按展示类型聚合
        Map<String, Integer> typeCnt = new LinkedHashMap<>();
        typeCnt.put("FEE", 0);
        typeCnt.put("ACTIVITY", 0);
        typeCnt.put("MAINT", 0);
        typeCnt.put("CONVENIENCE", 0);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "select source_type, count(1) as cnt from ai_schedule_remind where tenant_id=? group by source_type",
                tid);
        for (Map<String, Object> r : rows) {
            String g = scheduleTypeGroup(str(r.get("source_type"), ""));
            typeCnt.put(g, typeCnt.getOrDefault(g, 0) + toInt(r.get("cnt"), 0));
        }
        List<Map<String, Object>> typeDist = new ArrayList<>();
        for (Map.Entry<String, Integer> e : typeCnt.entrySet()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("type", e.getKey());
            item.put("name", scheduleTypeLabel(e.getKey()));
            item.put("value", e.getValue());
            typeDist.add(item);
        }

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("total", total == null ? 0 : total);
        m.put("pending", pending == null ? 0 : pending);
        m.put("sent", sent == null ? 0 : sent);
        m.put("conflict", conflict == null ? 0 : conflict);
        m.put("typeDist", typeDist);
        return m;
    }

    /**
     * 日程详情：基础信息 + 生成依据 + 业主画像 + 推送记录
     */
    public Map<String, Object> adminScheduleDetail(Long id) {
        assertStaff();
        requireTenant();
        Long tid = TenantContext.getTenantId();
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "select r.*, u.nickname as user_nickname, u.phone as user_phone "
                        + "from ai_schedule_remind r left join sys_user u on u.id=r.user_id "
                        + "where r.id=? and r.tenant_id=? limit 1",
                id, tid);
        if (rows.isEmpty()) {
            throw new BizException(404, "日程不存在");
        }
        Map<String, Object> detail = mapRemind(rows.get(0));
        Long uid = toLong(detail.get("userId"));
        String sourceType = str(detail.get("sourceType"), "");
        Long sourceId = toLong(detail.get("sourceId"));

        // 生成依据：反查来源业务
        detail.put("sourceBasis", resolveSourceBasis(tid, sourceType, sourceId));

        // 业主画像标签
        Map<String, Object> profile = new LinkedHashMap<>();
        try {
            List<Map<String, Object>> pr = jdbcTemplate.queryForList(
                    "select * from ai_user_profile where tenant_id=? and user_id=? limit 1", tid, uid);
            if (!pr.isEmpty()) {
                profile = mapProfile(pr.get(0));
            }
        } catch (Exception ignored) {
        }
        detail.put("profile", profile);
        detail.put("profileTags", buildProfileTags(profile));

        // 推送记录：本条日程状态 + 关联站内消息
        List<Map<String, Object>> pushLogs = new ArrayList<>();
        Map<String, Object> selfPush = new LinkedHashMap<>();
        selfPush.put("channel", "schedule");
        selfPush.put("status", detail.get("status"));
        selfPush.put("remindTime", detail.get("remindTime"));
        selfPush.put("sentTime", detail.get("sentTime"));
        selfPush.put("remark", "sent".equals(str(detail.get("status"), "")) ? "已推送站内消息" : "待到期推送");
        pushLogs.add(selfPush);
        try {
            String titleLike = "%" + str(detail.get("title"), "") + "%";
            List<Map<String, Object>> msgs = jdbcTemplate.queryForList(
                    "select id,title,content,msg_type,read_flag,create_time from sys_message "
                            + "where tenant_id=? and user_id=? and msg_type in ('schedule','schedule_conflict') "
                            + "and (title like ? or content like ?) order by id desc limit 10",
                    tid, uid, titleLike, titleLike);
            for (Map<String, Object> msg : msgs) {
                Map<String, Object> p = new LinkedHashMap<>();
                p.put("channel", "sys_message");
                p.put("status", toInt(msg.get("read_flag"), 0) == 1 ? "read" : "unread");
                p.put("title", msg.get("title"));
                p.put("content", msg.get("content"));
                p.put("sentTime", msg.get("create_time"));
                p.put("msgType", msg.get("msg_type"));
                pushLogs.add(p);
            }
        } catch (Exception ignored) {
        }
        detail.put("pushLogs", pushLogs);
        return detail;
    }

    public void deleteSchedule(Long id) {
        assertStaff();
        requireTenant();
        int n = jdbcTemplate.update(
                "delete from ai_schedule_remind where id=? and tenant_id=?",
                id, TenantContext.getTenantId());
        if (n == 0) {
            throw new BizException(404, "日程不存在");
        }
    }

    /** 读取提前提醒配置（小时） */
    public Map<String, Object> getScheduleSettings() {
        assertStaff();
        requireTenant();
        ensureScheduleSettingsTable();
        Long tid = TenantContext.getTenantId();
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "select * from ai_schedule_settings where tenant_id=? limit 1", tid);
        Map<String, Object> m = defaultScheduleSettings();
        if (!rows.isEmpty()) {
            Map<String, Object> r = rows.get(0);
            m.put("aheadFeeHours", toInt(r.get("ahead_fee_hours"), 24));
            m.put("aheadActivityHours", toInt(r.get("ahead_activity_hours"), 6));
            m.put("aheadMaintHours", toInt(r.get("ahead_maint_hours"), 24));
            m.put("aheadConvenienceHours", toInt(r.get("ahead_convenience_hours"), 12));
            m.put("aheadNoticeHours", toInt(r.get("ahead_notice_hours"), 2));
        }
        return m;
    }

    public Map<String, Object> saveScheduleSettings(Map<String, Object> body) {
        assertStaff();
        requireTenant();
        ensureScheduleSettingsTable();
        Long tid = TenantContext.getTenantId();
        int fee = clampHours(body.get("aheadFeeHours"), 24);
        int act = clampHours(body.get("aheadActivityHours"), 6);
        int maint = clampHours(body.get("aheadMaintHours"), 24);
        int conv = clampHours(body.get("aheadConvenienceHours"), 12);
        int notice = clampHours(body.get("aheadNoticeHours"), 2);
        Integer exists = jdbcTemplate.queryForObject(
                "select count(1) from ai_schedule_settings where tenant_id=?", Integer.class, tid);
        if (exists != null && exists > 0) {
            jdbcTemplate.update(
                    "update ai_schedule_settings set ahead_fee_hours=?, ahead_activity_hours=?, ahead_maint_hours=?, "
                            + "ahead_convenience_hours=?, ahead_notice_hours=?, update_time=now() where tenant_id=?",
                    fee, act, maint, conv, notice, tid);
        } else {
            jdbcTemplate.update(
                    "insert into ai_schedule_settings(tenant_id,ahead_fee_hours,ahead_activity_hours,ahead_maint_hours,"
                            + "ahead_convenience_hours,ahead_notice_hours,update_time) values(?,?,?,?,?,?,now())",
                    tid, fee, act, maint, conv, notice);
        }
        return getScheduleSettings();
    }

    private Map<String, Object> mapRemind(Map<String, Object> r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.get("id"));
        m.put("tenantId", r.get("tenant_id"));
        m.put("userId", r.get("user_id"));
        m.put("userNickname", r.get("user_nickname"));
        m.put("userPhone", r.get("user_phone"));
        m.put("title", r.get("title"));
        m.put("content", r.get("content"));
        m.put("remindTime", r.get("remind_time"));
        String status = str(r.get("status"), "");
        m.put("status", status);
        m.put("statusLabel", "sent".equals(status) ? "已推送" : "待推送");
        String sourceType = str(r.get("source_type"), "");
        String typeGroup = scheduleTypeGroup(sourceType);
        m.put("sourceType", sourceType);
        m.put("scheduleType", typeGroup);
        m.put("scheduleTypeLabel", scheduleTypeLabel(typeGroup));
        m.put("sourceId", r.get("source_id"));
        m.put("startTime", r.get("start_time"));
        m.put("endTime", r.get("end_time"));
        m.put("jumpPath", r.get("jump_path"));
        int conflict = toInt(r.get("conflict_flag"), 0);
        m.put("conflictFlag", conflict);
        m.put("conflictLabel", conflict == 1 ? "冲突" : "正常");
        m.put("createTime", r.get("create_time"));
        m.put("sentTime", r.get("sent_time"));
        return m;
    }

    /** 展示类型：FEE / ACTIVITY / MAINT / CONVENIENCE */
    private static String scheduleTypeGroup(String sourceType) {
        if (!StringUtils.hasText(sourceType)) return "CONVENIENCE";
        String s = sourceType.trim().toUpperCase(Locale.ROOT);
        return switch (s) {
            case "FEE", "PROPERTY_FEE" -> "FEE";
            case "ACTIVITY" -> "ACTIVITY";
            case "MAINT", "MAINTENANCE", "REPAIR", "FOLLOWUP" -> "MAINT";
            case "CONVENIENCE", "VACCINE", "VISIT", "SERVICE" -> "CONVENIENCE";
            case "NOTICE" -> "CONVENIENCE"; // 公告归入便民提醒展示
            default -> "CONVENIENCE";
        };
    }

    private static String scheduleTypeLabel(String typeGroup) {
        return switch (scheduleTypeGroup(typeGroup)) {
            case "FEE" -> "物业费提醒";
            case "ACTIVITY" -> "社区活动";
            case "MAINT" -> "维保回访";
            default -> "便民预约";
        };
    }

    private void appendScheduleTypeFilter(StringBuilder sql, List<Object> args, String scheduleType) {
        if (!StringUtils.hasText(scheduleType)) return;
        String g = scheduleTypeGroup(scheduleType);
        switch (g) {
            case "FEE" -> sql.append("and r.source_type in ('FEE','PROPERTY_FEE') ");
            case "ACTIVITY" -> sql.append("and r.source_type='ACTIVITY' ");
            case "MAINT" -> sql.append("and r.source_type in ('MAINT','MAINTENANCE','REPAIR','FOLLOWUP') ");
            case "CONVENIENCE" -> sql.append(
                    "and (r.source_type in ('CONVENIENCE','VACCINE','VISIT','SERVICE','NOTICE') "
                            + "or r.source_type is null or r.source_type='') ");
            default -> {
            }
        }
    }

    private Map<String, Object> resolveSourceBasis(Long tid, String sourceType, Long sourceId) {
        Map<String, Object> basis = new LinkedHashMap<>();
        String g = scheduleTypeGroup(sourceType);
        basis.put("sourceType", sourceType);
        basis.put("scheduleType", g);
        basis.put("scheduleTypeLabel", scheduleTypeLabel(g));
        basis.put("sourceId", sourceId);
        if (sourceId == null || sourceId <= 0) {
            basis.put("summary", "由 AI 管家根据业主画像自动生成（无具体业务单据）");
            return basis;
        }
        try {
            if ("ACTIVITY".equalsIgnoreCase(sourceType)) {
                List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                        "select id,title,start_time,end_time,signup_end from community_activity where id=? and tenant_id=? limit 1",
                        sourceId, tid);
                if (!rows.isEmpty()) {
                    Map<String, Object> a = rows.get(0);
                    basis.put("bizType", "community_activity");
                    basis.put("bizTitle", a.get("title"));
                    basis.put("summary", "来自社区活动：「" + a.get("title") + "」");
                    basis.put("bizTime", a.get("start_time"));
                    return basis;
                }
            } else if ("FEE".equalsIgnoreCase(sourceType)) {
                List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                        "select id,fee_type,amount,period,due_date,status from fee_bill where id=? and tenant_id=? limit 1",
                        sourceId, tid);
                if (!rows.isEmpty()) {
                    Map<String, Object> f = rows.get(0);
                    basis.put("bizType", "fee_bill");
                    basis.put("bizTitle", f.get("fee_type"));
                    basis.put("summary", "来自缴费账单：¥" + f.get("amount") + " / " + f.get("period"));
                    basis.put("bizTime", f.get("due_date"));
                    return basis;
                }
            } else if ("MAINT".equalsIgnoreCase(sourceType) || "REPAIR".equalsIgnoreCase(sourceType)) {
                List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                        "select id,title,status,update_time from repair_order where id=? and tenant_id=? limit 1",
                        sourceId, tid);
                if (!rows.isEmpty()) {
                    Map<String, Object> o = rows.get(0);
                    basis.put("bizType", "repair_order");
                    basis.put("bizTitle", o.get("title"));
                    basis.put("summary", "来自报修/维保工单：「" + o.get("title") + "」回访");
                    basis.put("bizTime", o.get("update_time"));
                    return basis;
                }
            } else if ("CONVENIENCE".equalsIgnoreCase(sourceType) || "VISIT".equalsIgnoreCase(sourceType)) {
                List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                        "select id,visitor_name,visit_time,status from visitor_appointment where id=? and tenant_id=? limit 1",
                        sourceId, tid);
                if (!rows.isEmpty()) {
                    Map<String, Object> v = rows.get(0);
                    basis.put("bizType", "visitor_appointment");
                    basis.put("bizTitle", "访客 " + v.get("visitor_name"));
                    basis.put("summary", "来自访客预约：" + v.get("visitor_name"));
                    basis.put("bizTime", v.get("visit_time"));
                    return basis;
                }
            } else if ("NOTICE".equalsIgnoreCase(sourceType)) {
                List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                        "select id,title,publish_time from notice_info where id=? and tenant_id=? limit 1",
                        sourceId, tid);
                if (!rows.isEmpty()) {
                    Map<String, Object> n = rows.get(0);
                    basis.put("bizType", "notice_info");
                    basis.put("bizTitle", n.get("title"));
                    basis.put("summary", "来自社区通知：「" + n.get("title") + "」");
                    basis.put("bizTime", n.get("publish_time"));
                    return basis;
                }
            }
        } catch (Exception e) {
            log.debug("resolve source basis skip: {}", e.getMessage());
        }
        basis.put("summary", "来源单据已删除或不可用（source=" + sourceType + "#" + sourceId + "）");
        return basis;
    }

    private List<String> buildProfileTags(Map<String, Object> profile) {
        List<String> tags = new ArrayList<>();
        if (profile == null || profile.isEmpty()) {
            tags.add("暂无画像");
            return tags;
        }
        String age = str(profile.get("ageBand"), "");
        if ("youth".equals(age)) tags.add("青年");
        else if ("senior".equals(age) || "elderly".equals(age)) tags.add("老年");
        else if ("adult".equals(age)) tags.add("中年");
        if (toInt(profile.get("hasChild"), 0) == 1) tags.add("有孩家庭");
        String interests = str(profile.get("interests"), "");
        if (StringUtils.hasText(interests)) {
            for (String t : interests.split("[,，、\\s]+")) {
                if (StringUtils.hasText(t)) tags.add(t.trim());
            }
        }
        String building = str(profile.get("building"), "");
        if (StringUtils.hasText(building)) tags.add(building + "栋");
        String needs = str(profile.get("livingNeeds"), "");
        if (StringUtils.hasText(needs)) tags.add(needs);
        if (tags.isEmpty()) tags.add("默认画像");
        return tags;
    }

    private Map<String, Integer> loadRemindAheadHours(Long tid) {
        Map<String, Integer> m = new HashMap<>();
        m.put("FEE", 24);
        m.put("ACTIVITY", 6);
        m.put("MAINT", 24);
        m.put("CONVENIENCE", 12);
        m.put("NOTICE", 2);
        try {
            ensureScheduleSettingsTable();
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "select * from ai_schedule_settings where tenant_id=? limit 1", tid);
            if (!rows.isEmpty()) {
                Map<String, Object> r = rows.get(0);
                m.put("FEE", toInt(r.get("ahead_fee_hours"), 24));
                m.put("ACTIVITY", toInt(r.get("ahead_activity_hours"), 6));
                m.put("MAINT", toInt(r.get("ahead_maint_hours"), 24));
                m.put("CONVENIENCE", toInt(r.get("ahead_convenience_hours"), 12));
                m.put("NOTICE", toInt(r.get("ahead_notice_hours"), 2));
            }
        } catch (Exception e) {
            log.debug("load schedule settings skip: {}", e.getMessage());
        }
        return m;
    }

    private Map<String, Object> defaultScheduleSettings() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("aheadFeeHours", 24);
        m.put("aheadActivityHours", 6);
        m.put("aheadMaintHours", 24);
        m.put("aheadConvenienceHours", 12);
        m.put("aheadNoticeHours", 2);
        return m;
    }

    private void ensureScheduleSettingsTable() {
        try {
            jdbcTemplate.execute("""
                    create table if not exists ai_schedule_settings (
                      tenant_id bigint primary key,
                      ahead_fee_hours int not null default 24,
                      ahead_activity_hours int not null default 6,
                      ahead_maint_hours int not null default 24,
                      ahead_convenience_hours int not null default 12,
                      ahead_notice_hours int not null default 2,
                      update_time datetime null
                    )
                    """);
        } catch (Exception ignored) {
        }
    }

    private static int clampHours(Object v, int def) {
        int n = toInt(v, def);
        if (n < 0) return 0;
        return Math.min(n, 24 * 14);
    }

    /* ===================== 便民推荐 ===================== */

    public List<Map<String, Object>> recommendServices() {
        requireTenant();
        Map<String, Object> profile = getProfile();
        String interests = str(profile.get("interests"), "");
        List<Map<String, Object>> list = new ArrayList<>();
        list.add(rec("家政预约", "保洁/保姆到家服务", "家政", "/pages/mall/index", interests.contains("家政") ? 0.92 : 0.7));
        list.add(rec("家电维修", "空调/热水器等上门维修", "维修", "/pages/repair/create", interests.contains("维修") ? 0.9 : 0.68));
        list.add(rec("社区商铺", "邻里优选商品与服务", "商铺", "/pages/mall/index", 0.75));
        try {
            List<Map<String, Object>> stores = jdbcTemplate.queryForList(
                    "select id,name from store_merchant where tenant_id=? and status=1 order by id desc limit 3",
                    TenantContext.getTenantId());
            for (Map<String, Object> s : stores) {
                list.add(rec(str(s.get("name"), "社区商户"), "一键进入商城下单", "商铺",
                        "/pages/mall/index", 0.8));
            }
        } catch (Exception ignored) {
        }
        trackBehavior("recommend_view", null);
        list.sort((a, b) -> Double.compare(toDouble(b.get("score")), toDouble(a.get("score"))));
        return list;
    }

    private Map<String, Object> rec(String title, String desc, String type, String jump, double score) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("title", title);
        m.put("desc", desc);
        m.put("serviceType", type);
        m.put("jumpPath", jump);
        m.put("score", score);
        return m;
    }

    /* ===================== 识图日志 ===================== */

    public void saveImageLog(String imageUrl, String facility, String location, String damage,
                             Double confidence, String provider, boolean needManual, Long orderId) {
        try {
            jdbcTemplate.update(
                    "insert into ai_image_log(tenant_id,user_id,image_url,facility_type,location_hint,damage_level,confidence,provider,need_manual,order_id,create_time) values(?,?,?,?,?,?,?,?,?,?,now())",
                    TenantContext.getTenantId(), TenantContext.getUserId(), imageUrl, facility, location, damage,
                    confidence, provider, needManual ? 1 : 0, orderId);
        } catch (Exception e) {
            log.debug("image log skip: {}", e.getMessage());
        }
    }

    /** 兼容旧调用 */
    public List<Map<String, Object>> listImageLogs(int limit) {
        return listImageLogs(null, null, null, null, null, null, null, limit);
    }

    /**
     * 物业：识图日志列表（筛选：设施类型/损坏程度/识别模式/置信度区间/时间）
     */
    public List<Map<String, Object>> listImageLogs(String facilityType, String damageLevel,
                                                   Integer needManual, Double confMin, Double confMax,
                                                   String startTime, String endTime, int limit) {
        assertStaff();
        requireTenant();
        Long tid = TenantContext.getTenantId();
        StringBuilder sql = new StringBuilder(
                "select l.*, o.order_type as order_type, o.title as order_title "
                        + "from ai_image_log l "
                        + "left join repair_order o on o.id=l.order_id and o.tenant_id=l.tenant_id "
                        + "where l.tenant_id=? ");
        List<Object> args = new ArrayList<>();
        args.add(tid);
        if (StringUtils.hasText(facilityType)) {
            sql.append("and l.facility_type=? ");
            args.add(facilityType.trim());
        }
        if (StringUtils.hasText(damageLevel)) {
            sql.append("and l.damage_level=? ");
            args.add(damageLevel.trim());
        }
        if (needManual != null) {
            sql.append("and ifnull(l.need_manual,0)=? ");
            args.add(needManual);
        }
        if (confMin != null) {
            sql.append("and ifnull(l.confidence,0)>=? ");
            args.add(confMin);
        }
        if (confMax != null) {
            sql.append("and ifnull(l.confidence,0)<=? ");
            args.add(confMax);
        }
        if (StringUtils.hasText(startTime)) {
            sql.append("and l.create_time>=? ");
            args.add(startTime.trim().replace('T', ' '));
        }
        if (StringUtils.hasText(endTime)) {
            sql.append("and l.create_time<=? ");
            args.add(endTime.trim().replace('T', ' '));
        }
        sql.append("order by l.id desc limit ?");
        args.add(Math.min(Math.max(limit, 1), 500));
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql.toString(), args.toArray());
        List<Map<String, Object>> list = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            list.add(mapImageLog(r));
        }
        return list;
    }

    /**
     * 识图日志统计卡片 + 环形图（自动通过 / 人工复核）
     */
    public Map<String, Object> imageLogStats() {
        assertStaff();
        requireTenant();
        Long tid = TenantContext.getTenantId();
        Integer todayTotal = jdbcTemplate.queryForObject(
                "select count(1) from ai_image_log where tenant_id=? and date(create_time)=curdate()",
                Integer.class, tid);
        Integer autoPass = jdbcTemplate.queryForObject(
                "select count(1) from ai_image_log where tenant_id=? and date(create_time)=curdate() and ifnull(need_manual,0)=0",
                Integer.class, tid);
        Integer needReview = jdbcTemplate.queryForObject(
                "select count(1) from ai_image_log where tenant_id=? and date(create_time)=curdate() and ifnull(need_manual,0)=1",
                Integer.class, tid);
        Integer highConf = jdbcTemplate.queryForObject(
                "select count(1) from ai_image_log where tenant_id=? and date(create_time)=curdate() and ifnull(confidence,0)>=0.9",
                Integer.class, tid);

        // 环形图：当日分布；若当日无数据则回退全量，方便演示
        Integer autoAll = autoPass;
        Integer manualAll = needReview;
        if ((todayTotal == null || todayTotal == 0)) {
            autoAll = jdbcTemplate.queryForObject(
                    "select count(1) from ai_image_log where tenant_id=? and ifnull(need_manual,0)=0",
                    Integer.class, tid);
            manualAll = jdbcTemplate.queryForObject(
                    "select count(1) from ai_image_log where tenant_id=? and ifnull(need_manual,0)=1",
                    Integer.class, tid);
        }

        List<Map<String, Object>> modeDist = new ArrayList<>();
        Map<String, Object> a = new LinkedHashMap<>();
        a.put("name", "AI识别");
        a.put("value", autoAll == null ? 0 : autoAll);
        modeDist.add(a);
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("name", "人工识别");
        b.put("value", manualAll == null ? 0 : manualAll);
        modeDist.add(b);

        // 筛选项候选
        List<String> facilities = new ArrayList<>();
        List<String> damages = new ArrayList<>();
        try {
            facilities = jdbcTemplate.queryForList(
                    "select distinct facility_type from ai_image_log where tenant_id=? and facility_type is not null and facility_type<>'' order by facility_type",
                    String.class, tid);
            damages = jdbcTemplate.queryForList(
                    "select distinct damage_level from ai_image_log where tenant_id=? and damage_level is not null and damage_level<>'' order by damage_level",
                    String.class, tid);
        } catch (Exception ignored) {
        }

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("todayTotal", todayTotal == null ? 0 : todayTotal);
        m.put("autoPass", autoPass == null ? 0 : autoPass);
        m.put("needReview", needReview == null ? 0 : needReview);
        m.put("highConfidence", highConf == null ? 0 : highConf);
        m.put("modeDist", modeDist);
        m.put("facilityOptions", facilities);
        m.put("damageOptions", damages);
        return m;
    }

    /** 识图详情（含关联工单溯源） */
    public Map<String, Object> imageLogDetail(Long id) {
        assertStaff();
        requireTenant();
        Long tid = TenantContext.getTenantId();
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "select l.*, o.order_type as order_type, o.title as order_title, o.status as order_status "
                        + "from ai_image_log l "
                        + "left join repair_order o on o.id=l.order_id and o.tenant_id=l.tenant_id "
                        + "where l.id=? and l.tenant_id=? limit 1",
                id, tid);
        if (rows.isEmpty()) {
            throw new BizException(404, "识图日志不存在");
        }
        return mapImageLog(rows.get(0));
    }

    /** 物业删除识图日志 */
    public void deleteImageLog(Long id) {
        assertStaff();
        requireTenant();
        int n = jdbcTemplate.update(
                "delete from ai_image_log where id=? and tenant_id=?",
                id, TenantContext.getTenantId());
        if (n == 0) {
            throw new BizException(404, "识图日志不存在");
        }
    }

    private Map<String, Object> mapImageLog(Map<String, Object> r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.get("id"));
        m.put("userId", r.get("user_id"));
        m.put("imageUrl", r.get("image_url"));
        m.put("facilityType", r.get("facility_type"));
        m.put("locationHint", r.get("location_hint"));
        m.put("damageLevel", r.get("damage_level"));
        Object conf = r.get("confidence");
        m.put("confidence", conf);
        double confVal = toDouble(conf);
        m.put("confidencePct", Math.round(confVal * 100));
        m.put("confidenceLevel", confVal >= 0.9 ? "high" : (confVal >= 0.7 ? "mid" : "low"));
        m.put("provider", r.get("provider"));
        int need = toInt(r.get("need_manual"), 0);
        m.put("needManual", need);
        m.put("modeLabel", need == 1 ? "人工识别" : "AI识别");
        Long orderId = toLong(r.get("order_id"));
        m.put("orderId", orderId > 0 ? orderId : null);
        String orderType = str(r.get("order_type"), "repair");
        m.put("orderType", orderType);
        m.put("orderTitle", r.get("order_title"));
        m.put("orderStatus", r.get("order_status"));
        if (orderId > 0) {
            m.put("orderNo", ("report".equalsIgnoreCase(orderType) ? "BS" : "BX") + orderId);
            m.put("orderPath", "report".equalsIgnoreCase(orderType)
                    ? "/property/report?id=" + orderId
                    : "/property/repair?id=" + orderId);
        }
        m.put("createTime", r.get("create_time"));
        return m;
    }

    /* ===================== 情绪工单 ===================== */

    public Long createEmotionCase(String content, String sentiment, String tags, Double score, String advice, boolean needGrid) {
        requireTenant();
        Long tid = TenantContext.getTenantId();
        Long uid = TenantContext.getUserId();
        jdbcTemplate.update(
                "insert into ai_emotion_case(tenant_id,user_id,content,sentiment,emotion_tags,score,advice,status,create_time,update_time) values(?,?,?,?,?,?,?,?,now(),now())",
                tid, uid, content, sentiment, tags, score, advice, needGrid ? "pending" : "closed");
        Long id = jdbcTemplate.queryForObject("select last_insert_id()", Long.class);
        if (needGrid) {
            try {
                List<Map<String, Object>> grids = jdbcTemplate.queryForList(
                        "select id from sys_user where tenant_id=? and role in ('grid_worker','property_admin') and status=1 limit 20",
                        tid);
                for (Map<String, Object> g : grids) {
                    jdbcTemplate.update(
                            "insert into sys_message(tenant_id,user_id,title,content,msg_type,read_flag,create_time) values(?,?,?,?,?,?,now())",
                            tid, g.get("id"), "情绪调解工单#" + id,
                            "居民情绪倾诉触发调解，请及时查看（隐私隔离，仅员工可见原文）。",
                            "emotion_case", 0);
                }
            } catch (Exception e) {
                log.warn("emotion notify fail: {}", e.getMessage());
            }
            // 同步生成报事工单（民生事件），绑定小区
            try {
                Integer exists = jdbcTemplate.queryForObject(
                        "select count(1) from repair_order where tenant_id=? and emotion_case_id=?",
                        Integer.class, tid, id);
                if (exists == null || exists == 0) {
                    String urgency = (score != null && score <= -0.7) ? "高" : "中";
                    jdbcTemplate.update(
                            "insert into repair_order(tenant_id,user_id,title,description,facility_type,damage_level,ai_tag,status,"
                                    + "order_type,urgency,category_name,emotion_case_id,emotion_tags,sentiment,create_time,update_time) "
                                    + "values(?,?,?,?,?,?,?,'pending','report',?,?,?,?,?,now(),now())",
                            tid, uid,
                            "邻里报事：" + (sentiment == null ? "诉求" : sentiment),
                            content,
                            "邻里纠纷",
                            urgency,
                            "情绪预警:" + (tags == null ? sentiment : tags),
                            urgency,
                            "邻里纠纷",
                            id, tags, sentiment);
                }
            } catch (Exception e) {
                log.warn("create report order from emotion skip: {}", e.getMessage());
            }
        }
        return id;
    }

    /**
     * 情绪调解工单列表。
     * 隐私：居民对话原文仅授权物业人员查看，禁止导出外泄。
     */
    public List<Map<String, Object>> listEmotionCases(
            String status, Long userId, String keyword, String emotionTag, String startDate, String endDate, int limit) {
        assertStaff();
        requireTenant();
        String sql = "select c.*, u.nickname as user_nickname, u.phone as user_phone "
                + "from ai_emotion_case c "
                + "left join sys_user u on u.id=c.user_id "
                + "where c.tenant_id=? ";
        List<Object> args = new ArrayList<>();
        args.add(TenantContext.getTenantId());
        if (StringUtils.hasText(status)) {
            if ("closed".equals(status) || "done".equals(status)) {
                sql += "and c.status in ('closed','done') ";
            } else if ("handling".equals(status) || "processing".equals(status)) {
                sql += "and c.status in ('handling','processing') ";
            } else {
                sql += "and c.status=? ";
                args.add(status);
            }
        }
        if (userId != null && userId > 0) {
            sql += "and c.user_id=? ";
            args.add(userId);
        }
        if (StringUtils.hasText(keyword)) {
            String kw = "%" + keyword.trim() + "%";
            sql += "and (ifnull(u.nickname,'') like ? or cast(c.user_id as char) like ? or ifnull(u.phone,'') like ?) ";
            args.add(kw);
            args.add(kw);
            args.add(kw);
        }
        if (StringUtils.hasText(emotionTag)) {
            sql += "and ifnull(c.emotion_tags,'') like ? ";
            args.add("%" + emotionTag.trim() + "%");
        }
        if (StringUtils.hasText(startDate)) {
            sql += "and c.create_time >= ? ";
            args.add(startDate.trim().length() <= 10 ? startDate.trim() + " 00:00:00" : startDate.trim());
        }
        if (StringUtils.hasText(endDate)) {
            sql += "and c.create_time <= ? ";
            args.add(endDate.trim().length() <= 10 ? endDate.trim() + " 23:59:59" : endDate.trim());
        }
        sql += "order by c.id desc limit ?";
        args.add(Math.min(Math.max(limit, 1), 200));
        return mapEmotionRows(jdbcTemplate.queryForList(sql, args.toArray()), true);
    }

    /** 顶部统计卡片：总数 / 待处理 / 已办结 / 高危（愤怒/激烈冲突） */
    public Map<String, Object> emotionCaseStats() {
        assertStaff();
        requireTenant();
        Long tid = TenantContext.getTenantId();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("total", cntEmotion("select count(1) from ai_emotion_case where tenant_id=?", tid));
        m.put("pending", cntEmotion("select count(1) from ai_emotion_case where tenant_id=? and status=?", tid, "pending"));
        m.put("handling", cntEmotion(
                "select count(1) from ai_emotion_case where tenant_id=? and status in ('handling','processing')", tid));
        m.put("closed", cntEmotion(
                "select count(1) from ai_emotion_case where tenant_id=? and status in ('closed','done')", tid));
        m.put("highRisk", cntEmotion(
                "select count(1) from ai_emotion_case where tenant_id=? and ("
                        + "ifnull(emotion_tags,'') like '%愤怒%' or ifnull(emotion_tags,'') like '%激烈冲突%')",
                tid));
        return m;
    }

    private int cntEmotion(String sql, Object... args) {
        try {
            Integer n = jdbcTemplate.queryForObject(sql, Integer.class, args);
            return n == null ? 0 : n;
        } catch (Exception e) {
            return 0;
        }
    }

    public List<Map<String, Object>> myEmotionCases() {
        requireTenant();
        Long uid = TenantContext.getUserId();
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "select c.*, u.nickname as user_nickname, u.phone as user_phone "
                        + "from ai_emotion_case c left join sys_user u on u.id=c.user_id "
                        + "where c.tenant_id=? and c.user_id=? order by c.id desc limit 50",
                TenantContext.getTenantId(), uid);
        return mapEmotionRows(rows, true);
    }

    /** 删除情绪调解工单（仅物业；原文敏感，删除后不可恢复） */
    public void deleteEmotionCase(Long id) {
        assertStaff();
        requireTenant();
        int n = jdbcTemplate.update(
                "delete from ai_emotion_case where id=? and tenant_id=?",
                id, TenantContext.getTenantId());
        if (n <= 0) {
            throw new BizException(404, "工单不存在");
        }
    }

    /**
     * 受理：status=handling + mediateRecord；结案：status=closed + handleResult。
     * 隐私：居民对话原文仅授权物业人员查看，禁止导出外泄。
     */
    public Map<String, Object> updateEmotionCase(Long id, Map<String, Object> body) {
        assertStaff();
        requireTenant();
        String status = str(body.get("status"), null);
        String remark = str(body.get("handleRemark"), null);
        String mediate = str(body.get("mediateRecord"), null);
        String result = str(body.get("handleResult"), null);
        // 兼容旧字段：未单独传调解记录时，用 handleRemark 写入 mediate_record
        if (!StringUtils.hasText(mediate) && StringUtils.hasText(remark)
                && ("handling".equals(status) || "processing".equals(status))) {
            mediate = remark;
        }
        if (!StringUtils.hasText(result) && StringUtils.hasText(remark)
                && ("closed".equals(status) || "done".equals(status))) {
            result = remark;
        }
        jdbcTemplate.update(
                "update ai_emotion_case set "
                        + "status=ifnull(?,status),"
                        + "handle_remark=ifnull(?,handle_remark),"
                        + "mediate_record=ifnull(?,mediate_record),"
                        + "handle_result=ifnull(?,handle_result),"
                        + "grid_id=?,update_time=now() where id=? and tenant_id=?",
                status, remark, mediate, result,
                TenantContext.getUserId(), id, TenantContext.getTenantId());
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "select c.*, u.nickname as user_nickname, u.phone as user_phone "
                        + "from ai_emotion_case c left join sys_user u on u.id=c.user_id "
                        + "where c.id=? and c.tenant_id=?",
                id, TenantContext.getTenantId());
        if (rows.isEmpty()) {
            throw new BizException(404, "工单不存在");
        }
        return mapEmotionRows(rows, true).get(0);
    }

    private List<Map<String, Object>> mapEmotionRows(List<Map<String, Object>> rows, boolean withContent) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r.get("id"));
            m.put("userId", r.get("user_id"));
            String nick = str(r.get("user_nickname"), null);
            m.put("userNickname", nick);
            m.put("userName", StringUtils.hasText(nick) ? nick : ("居民#" + r.get("user_id")));
            m.put("userPhone", maskPhone(str(r.get("user_phone"), null)));
            // 居民对话原文：仅授权物业人员查看，禁止导出外泄
            if (withContent) {
                m.put("content", r.get("content"));
            }
            m.put("sentiment", r.get("sentiment"));
            m.put("emotionTags", r.get("emotion_tags"));
            m.put("score", r.get("score"));
            m.put("advice", r.get("advice"));
            m.put("status", r.get("status"));
            m.put("gridId", r.get("grid_id"));
            m.put("handleRemark", r.get("handle_remark"));
            m.put("mediateRecord", r.get("mediate_record"));
            m.put("handleResult", r.get("handle_result"));
            m.put("createTime", r.get("create_time"));
            m.put("updateTime", r.get("update_time"));
            boolean highRisk = isHighRiskEmotion(str(r.get("emotion_tags"), ""), str(r.get("sentiment"), ""));
            m.put("highRisk", highRisk);
            m.put("riskLevel", highRisk ? "高危" : riskLevelByScore(r.get("score")));
            list.add(m);
        }
        return list;
    }

    private static String maskPhone(String phone) {
        if (!StringUtils.hasText(phone) || phone.length() < 7) {
            return phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    private static boolean isHighRiskEmotion(String tags, String sentiment) {
        String t = tags == null ? "" : tags;
        return t.contains("愤怒") || t.contains("激烈冲突");
    }

    private static String riskLevelByScore(Object scoreObj) {
        if (scoreObj == null) return "一般";
        try {
            double s = Double.parseDouble(String.valueOf(scoreObj));
            if (s <= -0.7) return "较高";
            if (s <= -0.3) return "中等";
            return "一般";
        } catch (Exception e) {
            return "一般";
        }
    }

    /* ===================== 敏感词（小区词库） ===================== */

    public List<String> loadMergedSensitiveWords() {
        List<String> words = new ArrayList<>();
        try {
            words.addAll(jdbcTemplate.queryForList("select word from sys_sensitive_word", String.class));
        } catch (Exception ignored) {
        }
        Long tid = TenantContext.getTenantId();
        if (tid != null) {
            try {
                words.addAll(jdbcTemplate.queryForList(
                        "select word from ai_sensitive_word where tenant_id=?", String.class, tid));
            } catch (Exception ignored) {
            }
        }
        if (words.isEmpty()) {
            return List.of("赌博", "色情", "诈骗", "传销");
        }
        return words.stream().filter(StringUtils::hasText).distinct().toList();
    }

    public List<Map<String, Object>> listTenantWords() {
        assertStaff();
        requireTenant();
        return jdbcTemplate.queryForList(
                "select id,word,create_time as createTime from ai_sensitive_word where tenant_id=? order by id desc",
                TenantContext.getTenantId());
    }

    public Map<String, Object> addTenantWord(String word) {
        assertStaff();
        requireTenant();
        if (!StringUtils.hasText(word)) {
            throw new BizException(400, "词不能为空");
        }
        word = word.trim();
        try {
            jdbcTemplate.update(
                    "insert into ai_sensitive_word(tenant_id,word,create_time) values(?,?,now())",
                    TenantContext.getTenantId(), word);
        } catch (Exception e) {
            throw new BizException(400, "词已存在或写入失败");
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("word", word);
        return m;
    }

    public void deleteTenantWord(Long id) {
        assertStaff();
        requireTenant();
        jdbcTemplate.update("delete from ai_sensitive_word where id=? and tenant_id=?", id, TenantContext.getTenantId());
    }

    /* ===================== 公告定向推送 ===================== */

    public Map<String, Object> targetPushNotice(Long noticeId, String targetRuleJson) {
        assertStaff();
        requireTenant();
        Long tid = TenantContext.getTenantId();
        Map<String, Object> notice;
        try {
            notice = jdbcTemplate.queryForMap("select * from notice_info where id=? and tenant_id=?", noticeId, tid);
        } catch (Exception e) {
            throw new BizException(404, "公告不存在");
        }
        String title = str(notice.get("title"), "社区公告");
        String summary = str(notice.get("summary"), title);
        String rule = StringUtils.hasText(targetRuleJson) ? targetRuleJson : str(notice.get("target_rule"), "");
        List<Map<String, Object>> profiles = jdbcTemplate.queryForList(
                "select * from ai_user_profile where tenant_id=?", tid);
        // 无画像时推给本社区居民角色
        List<Long> userIds = new ArrayList<>();
        if (profiles.isEmpty()) {
            List<Map<String, Object>> users = jdbcTemplate.queryForList(
                    "select id from sys_user where tenant_id=? and role='resident' and status=1 limit 200", tid);
            for (Map<String, Object> u : users) {
                userIds.add(toLong(u.get("id")));
            }
        } else {
            for (Map<String, Object> p : profiles) {
                if (matchRule(p, rule)) {
                    userIds.add(toLong(p.get("user_id")));
                }
            }
        }
        int pushed = 0;
        for (Long uid : userIds) {
            if (uid == null) continue;
            try {
                jdbcTemplate.update(
                        "insert into sys_message(tenant_id,user_id,title,content,msg_type,read_flag,create_time) values(?,?,?,?,?,?,now())",
                        tid, uid, "定向公告：" + title, summary, "notice_target", 0);
                jdbcTemplate.update(
                        "insert into ai_notice_push_log(tenant_id,notice_id,user_id,reason,create_time) values(?,?,?,?,now())",
                        tid, noticeId, uid, StringUtils.hasText(rule) ? rule : "all_residents");
                pushed++;
            } catch (Exception ignored) {
            }
        }
        try {
            jdbcTemplate.update("update notice_info set push_count=ifnull(push_count,0)+?, target_rule=ifnull(?,target_rule) where id=? and tenant_id=?",
                    pushed, StringUtils.hasText(targetRuleJson) ? targetRuleJson : null, noticeId, tid);
        } catch (Exception ignored) {
        }
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("noticeId", noticeId);
        resp.put("pushed", pushed);
        return resp;
    }

    public List<Map<String, Object>> listPushLogs(Long noticeId, int limit) {
        assertStaff();
        requireTenant();
        if (noticeId != null) {
            return jdbcTemplate.queryForList(
                    "select id,notice_id as noticeId,user_id as userId,reason,create_time as createTime from ai_notice_push_log where tenant_id=? and notice_id=? order by id desc limit ?",
                    TenantContext.getTenantId(), noticeId, Math.min(Math.max(limit, 1), 200));
        }
        return jdbcTemplate.queryForList(
                "select id,notice_id as noticeId,user_id as userId,reason,create_time as createTime from ai_notice_push_log where tenant_id=? order by id desc limit ?",
                TenantContext.getTenantId(), Math.min(Math.max(limit, 1), 200));
    }

    private boolean matchRule(Map<String, Object> profile, String rule) {
        if (!StringUtils.hasText(rule) || "{}".equals(rule.trim())) {
            return true;
        }
        String age = str(profile.get("age_band"), "");
        int hasChild = toInt(profile.get("has_child"), 0);
        String interests = str(profile.get("interests"), "");
        String building = str(profile.get("building"), "");
        if (rule.contains("senior") || rule.contains("老年")) {
            return "senior".equals(age) || "elderly".equals(age);
        }
        if (rule.contains("child") || rule.contains("亲子") || rule.contains("hasChild")) {
            return hasChild == 1;
        }
        if (rule.contains("building")) {
            // 简单包含楼栋号
            int idx = rule.indexOf("building");
            return building.length() > 0 && rule.contains(building);
        }
        // 兴趣关键词
        for (String part : interests.split("[,，]")) {
            if (StringUtils.hasText(part) && rule.contains(part.trim())) {
                return true;
            }
        }
        return rule.contains("all");
    }

    /* ===================== helpers ===================== */

    private static String str(Object v, String def) {
        if (v == null) return def;
        String s = String.valueOf(v).trim();
        return s.isEmpty() ? def : s;
    }

    private static int toInt(Object v, int def) {
        if (v == null) return def;
        if (v instanceof Number n) return n.intValue();
        if (v instanceof Boolean b) return b ? 1 : 0;
        try {
            return Integer.parseInt(String.valueOf(v));
        } catch (Exception e) {
            return def;
        }
    }

    private static long toLong(Object v) {
        if (v == null) return 0L;
        if (v instanceof Number n) return n.longValue();
        try {
            return Long.parseLong(String.valueOf(v));
        } catch (Exception e) {
            return 0L;
        }
    }

    private static double toDouble(Object v) {
        if (v instanceof Number n) return n.doubleValue();
        return 0;
    }

    private static LocalDateTime toTime(Object v, LocalDateTime def) {
        if (v == null) return def;
        if (v instanceof LocalDateTime ldt) return ldt;
        if (v instanceof java.sql.Timestamp ts) return ts.toLocalDateTime();
        if (v instanceof java.sql.Date d) return d.toLocalDate().atStartOfDay();
        String s = String.valueOf(v).trim().replace(' ', 'T');
        try {
            if (s.length() >= 19) {
                return LocalDateTime.parse(s.substring(0, 19), DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            }
            if (s.length() == 10) {
                return LocalDateTime.parse(s + "T09:00:00");
            }
        } catch (Exception ignored) {
        }
        return def;
    }
}
