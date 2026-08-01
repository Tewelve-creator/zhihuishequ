package com.community.repair.platform;

import com.community.common.constant.RoleConstants;
import com.community.common.exception.BizException;
import com.community.common.tenant.TenantContext;
import com.community.repair.store.StoreNotifyService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;

/**
 * SaaS 平台全域能力：监控汇总、公告/活动下发、内容巡查、报表（与物业本小区能力隔离）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlatformHubService {

    private final JdbcTemplate jdbc;
    private final StoreNotifyService storeNotifyService;

    @PostConstruct
    public void ensure() {
        try {
            jdbc.execute("""
                    create table if not exists platform_notice_dispatch (
                      id bigint primary key auto_increment,
                      title varchar(128) not null,
                      content text null,
                      publisher_id bigint null,
                      publisher_name varchar(64) null,
                      target_tenant_ids varchar(1000) null comment '逗号分隔，空=全部',
                      dispatched_count int default 0,
                      create_time datetime null,
                      key idx_pnd_time (create_time)
                    ) engine=InnoDB default charset=utf8mb4
                    """);
            jdbc.execute("""
                    create table if not exists platform_activity_dispatch (
                      id bigint primary key auto_increment,
                      title varchar(128) not null,
                      category varchar(64) null,
                      content text null,
                      summary varchar(255) null,
                      location varchar(128) null,
                      start_time datetime null,
                      end_time datetime null,
                      max_people int null,
                      publisher_id bigint null,
                      publisher_name varchar(64) null,
                      target_tenant_ids varchar(1000) null,
                      dispatched_count int default 0,
                      create_time datetime null
                    ) engine=InnoDB default charset=utf8mb4
                    """);
        } catch (Exception e) {
            log.warn("platform hub schema skip: {}", e.getMessage());
        }
    }

    private void assertPlatform() {
        if (!RoleConstants.isPlatformAdmin(TenantContext.getRole())) {
            throw new BizException(403, "仅平台运营可访问全域能力");
        }
    }

    /** 全小区风险工单汇总 */
    public List<Map<String, Object>> riskRepairOrders(String level, Long tenantId, String keyword) {
        assertPlatform();
        StringBuilder sql = new StringBuilder(
                "select o.id, o.tenant_id as tenantId, o.id as orderNo, o.title, o.status, "
                        + "o.urgency, o.create_time as createTime, o.update_time as updateTime, "
                        + "ifnull(t.name, concat('小区',o.tenant_id)) as tenantName "
                        + "from repair_order o left join sys_tenant t on t.id=o.tenant_id "
                        + "where o.status in ('PENDING','ASSIGNED','PROCESSING','PENDING_ACCEPT') ");
        List<Object> args = new ArrayList<>();
        if (tenantId != null) {
            sql.append("and o.tenant_id=? ");
            args.add(tenantId);
        }
        if (StringUtils.hasText(level)) {
            sql.append("and o.urgency=? ");
            args.add(level.trim());
        } else {
            sql.append("and (o.urgency in ('HIGH','URGENT','高','紧急') or o.status='PENDING' "
                    + "or o.create_time < date_sub(now(), interval 2 day)) ");
        }
        if (StringUtils.hasText(keyword)) {
            sql.append("and (o.title like ? or cast(o.id as char) like ? or ifnull(t.name,'') like ?) ");
            String like = "%" + keyword.trim() + "%";
            args.add(like);
            args.add(like);
            args.add(like);
        }
        sql.append("order by case when o.urgency in ('URGENT','紧急') then 0 when o.urgency in ('HIGH','高') then 1 else 2 end, o.id desc limit 300");
        List<Map<String, Object>> rows;
        try {
            rows = jdbc.queryForList(sql.toString(), args.toArray());
        } catch (Exception e) {
            rows = jdbc.queryForList(
                    "select o.id, o.tenant_id as tenantId, o.id as orderNo, o.title, o.status, "
                            + "o.create_time as createTime, "
                            + "ifnull(t.name, concat('小区',o.tenant_id)) as tenantName "
                            + "from repair_order o left join sys_tenant t on t.id=o.tenant_id "
                            + "where o.status in ('PENDING','ASSIGNED','PROCESSING') "
                            + "order by o.id desc limit 300");
        }
        for (Map<String, Object> r : rows) {
            normalizeTime(r, "createTime");
            normalizeTime(r, "updateTime");
            r.put("statusLabel", statusLabel(String.valueOf(r.get("status"))));
            r.put("riskLevel", riskLevel(r));
        }
        return rows;
    }

    public Map<String, Object> riskSummary() {
        assertPlatform();
        Map<String, Object> m = new LinkedHashMap<>();
        try {
            Integer pending = jdbc.queryForObject(
                    "select count(1) from repair_order where status in ('PENDING','ASSIGNED','PROCESSING')", Integer.class);
            Integer overdue = jdbc.queryForObject(
                    "select count(1) from repair_order where status in ('PENDING','ASSIGNED','PROCESSING') "
                            + "and create_time < date_sub(now(), interval 2 day)", Integer.class);
            m.put("openOrders", pending == null ? 0 : pending);
            m.put("overdueOrders", overdue == null ? 0 : overdue);
        } catch (Exception e) {
            m.put("openOrders", 0);
            m.put("overdueOrders", 0);
        }
        try {
            Integer expiring = jdbc.queryForObject(
                    "select count(1) from sys_tenant where deleted=0 and status='active' "
                            + "and expire_time is not null and expire_time <= date_add(now(), interval 30 day)",
                    Integer.class);
            m.put("expiringTenants", expiring == null ? 0 : expiring);
        } catch (Exception e) {
            m.put("expiringTenants", 0);
        }
        return m;
    }

    /** 发布平台级公告并下发到指定/全部小区 */
    @Transactional
    public Map<String, Object> publishGlobalNotice(Map<String, Object> body) {
        assertPlatform();
        String title = str(body.get("title"), null);
        String content = str(body.get("content"), null);
        if (!StringUtils.hasText(title) || !StringUtils.hasText(content)) {
            throw new BizException(400, "标题与内容不能为空");
        }
        List<Long> targets = parseTenantIds(body.get("tenantIds"));
        if (targets.isEmpty()) {
            targets = allTenantIds();
        }
        String targetCsv = joinIds(targets);
        jdbc.update(
                "insert into platform_notice_dispatch(title,content,publisher_id,publisher_name,target_tenant_ids,dispatched_count,create_time) "
                        + "values(?,?,?,?,?,0,now())",
                title, content, TenantContext.getUserId(), operatorName(), targetCsv);
        Long dispatchId = jdbc.queryForObject("select last_insert_id()", Long.class);
        int n = 0;
        for (Long tid : targets) {
            try {
                jdbc.update(
                        "insert into notice_info(tenant_id,title,content,publisher_id,status,publish_time,create_time) "
                                + "values(?,?,?,?,1,now(),now())",
                        tid, title, content, TenantContext.getUserId());
                storeNotifyService.notifyPlatformNoticeToMerchants(tid, "平台公告：" + title, content);
                n++;
            } catch (Exception e) {
                log.warn("dispatch notice to {} fail: {}", tid, e.getMessage());
            }
        }
        jdbc.update("update platform_notice_dispatch set dispatched_count=? where id=?", n, dispatchId);
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("dispatchId", dispatchId);
        resp.put("dispatchedCount", n);
        resp.put("targetCount", targets.size());
        return resp;
    }

    public List<Map<String, Object>> listNoticeDispatches() {
        assertPlatform();
        List<Map<String, Object>> rows = jdbc.queryForList(
                "select id, title, content, publisher_name as publisherName, target_tenant_ids as targetTenantIds, "
                        + "dispatched_count as dispatchedCount, create_time as createTime "
                        + "from platform_notice_dispatch order by id desc limit 100");
        for (Map<String, Object> r : rows) {
            normalizeTime(r, "createTime");
        }
        return rows;
    }

    /** 统一创建活动并下发到小区 */
    @Transactional
    public Map<String, Object> dispatchActivity(Map<String, Object> body) {
        assertPlatform();
        String title = str(body.get("title"), null);
        if (!StringUtils.hasText(title)) throw new BizException(400, "活动标题不能为空");
        String category = str(body.get("category"), "社区活动");
        String content = str(body.get("content"), title);
        String summary = str(body.get("summary"), title);
        String location = str(body.get("location"), "各小区广场");
        List<Long> targets = parseTenantIds(body.get("tenantIds"));
        if (targets.isEmpty()) targets = allTenantIds();
        jdbc.update(
                "insert into platform_activity_dispatch(title,category,content,summary,location,start_time,end_time,"
                        + "max_people,publisher_id,publisher_name,target_tenant_ids,dispatched_count,create_time) "
                        + "values(?,?,?,?,?,?,?,?,?,?,?,0,now())",
                title, category, content, summary, location,
                body.get("startTime"), body.get("endTime"),
                body.get("maxPeople") == null ? 100 : Integer.valueOf(String.valueOf(body.get("maxPeople"))),
                TenantContext.getUserId(), operatorName(), joinIds(targets));
        Long dispatchId = jdbc.queryForObject("select last_insert_id()", Long.class);
        int n = 0;
        for (Long tid : targets) {
            try {
                jdbc.update(
                        "insert into community_activity(tenant_id,title,category,content,summary,location,"
                                + "start_time,end_time,max_people,signed_count,status,ai_audit_pass,push_count,"
                                + "publisher_id,create_time,tags) "
                                + "values(?,?,?,?,?,?,?,?,?,0,1,1,0,?,now(),?)",
                        tid, title, category, content, summary, location,
                        body.get("startTime"), body.get("endTime"),
                        body.get("maxPeople") == null ? 100 : Integer.valueOf(String.valueOf(body.get("maxPeople"))),
                        TenantContext.getUserId(), "平台下发,超市");
                n++;
            } catch (Exception e) {
                log.warn("dispatch activity to {} fail: {}", tid, e.getMessage());
            }
        }
        jdbc.update("update platform_activity_dispatch set dispatched_count=? where id=?", n, dispatchId);
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("dispatchId", dispatchId);
        resp.put("dispatchedCount", n);
        return resp;
    }

    public List<Map<String, Object>> listActivityDispatches() {
        assertPlatform();
        List<Map<String, Object>> rows = jdbc.queryForList(
                "select id, title, category, location, publisher_name as publisherName, "
                        + "dispatched_count as dispatchedCount, create_time as createTime "
                        + "from platform_activity_dispatch order by id desc limit 100");
        for (Map<String, Object> r : rows) normalizeTime(r, "createTime");
        return rows;
    }

    /** 全域邻里倾诉/动态巡查 */
    public List<Map<String, Object>> inspectAppeals(Long tenantId, String filter, String keyword) {
        assertPlatform();
        StringBuilder sql = new StringBuilder(
                "select a.id, a.tenant_id as tenantId, a.user_id as userId, a.content, a.status, "
                        + "a.create_time as createTime, ifnull(a.hidden,0) as hidden, ifnull(a.pinned,0) as pinned, "
                        + "ifnull(t.name, concat('小区',a.tenant_id)) as tenantName, "
                        + "ifnull(u.nickname, concat('用户',a.user_id)) as userName "
                        + "from emotion_appeal a "
                        + "left join sys_tenant t on t.id=a.tenant_id "
                        + "left join sys_user u on u.id=a.user_id where 1=1 ");
        List<Object> args = new ArrayList<>();
        if (tenantId != null) {
            sql.append("and a.tenant_id=? ");
            args.add(tenantId);
        }
        if ("hidden".equals(filter)) {
            sql.append("and ifnull(a.hidden,0)=1 ");
        } else if ("reported".equals(filter)) {
            sql.append("and exists (select 1 from appeal_report r where r.appeal_id=a.id and r.status='PENDING') ");
        } else if ("ticket".equals(filter)) {
            sql.append("and a.status in ('pending','processing') ");
        } else {
            sql.append("and (a.status='normal' or a.status is null or a.status in ('pending','processing','done')) ");
        }
        if (StringUtils.hasText(keyword)) {
            sql.append("and (a.content like ? or ifnull(t.name,'') like ?) ");
            String like = "%" + keyword.trim() + "%";
            args.add(like);
            args.add(like);
        }
        sql.append("order by a.id desc limit 300");
        List<Map<String, Object>> rows;
        try {
            rows = jdbc.queryForList(sql.toString(), args.toArray());
        } catch (Exception e) {
            rows = jdbc.queryForList(
                    "select a.id, a.tenant_id as tenantId, a.user_id as userId, a.content, a.status, "
                            + "a.create_time as createTime, "
                            + "ifnull(t.name, concat('小区',a.tenant_id)) as tenantName "
                            + "from emotion_appeal a left join sys_tenant t on t.id=a.tenant_id "
                            + "order by a.id desc limit 300");
        }
        for (Map<String, Object> r : rows) {
            normalizeTime(r, "createTime");
            r.put("contentPreview", preview(String.valueOf(r.get("content")), 80));
            int hidden = r.get("hidden") instanceof Number n ? n.intValue() : 0;
            r.put("statusLabel", hidden == 1 ? "已隐藏" : String.valueOf(r.get("status")));
        }
        return rows;
    }

    @Transactional
    public Map<String, Object> handleAppealViolation(Long id, String action, String reason) {
        assertPlatform();
        if (!StringUtils.hasText(action)) throw new BizException(400, "action 必填");
        if ("hide".equals(action)) {
            try {
                jdbc.update("update emotion_appeal set hidden=1 where id=?", id);
            } catch (Exception e) {
                jdbc.update("update emotion_appeal set status='closed' where id=?", id);
            }
        } else if ("delete".equals(action)) {
            jdbc.update("update emotion_appeal set status='deleted', hidden=1 where id=?", id);
        } else if ("restore".equals(action)) {
            try {
                jdbc.update("update emotion_appeal set hidden=0, status='normal' where id=?", id);
            } catch (Exception e) {
                jdbc.update("update emotion_appeal set status='normal' where id=?", id);
            }
        } else {
            throw new BizException(400, "不支持的 action");
        }
        try {
            jdbc.update(
                    "insert into appeal_op_log(tenant_id,appeal_id,action,reason,operator_id,operator_name,create_time) "
                            + "select tenant_id,id,?,?,?,?,now() from emotion_appeal where id=?",
                    action, reason, TenantContext.getUserId(), operatorName(), id);
        } catch (Exception ignored) {
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("action", action);
        return m;
    }

    /** 报表：全域运营 */
    public Map<String, Object> reportOps(Integer days) {
        assertPlatform();
        int d = days == null || days < 1 ? 30 : days;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("days", d);
        m.put("tenantCount", scalarInt("select count(1) from sys_tenant where deleted=0"));
        m.put("activeTenantCount", scalarInt("select count(1) from sys_tenant where deleted=0 and status='active'"));
        m.put("repairOrderCount", scalarInt(
                "select count(1) from repair_order where create_time >= date_sub(now(), interval ? day)", d));
        m.put("noticeCount", scalarInt(
                "select count(1) from notice_info where create_time >= date_sub(now(), interval ? day)", d));
        m.put("activityCount", scalarInt(
                "select count(1) from community_activity where create_time >= date_sub(now(), interval ? day)", d));
        try {
            m.put("byTenantRepair", jdbc.queryForList(
                    "select o.tenant_id as tenantId, ifnull(t.name, concat('小区',o.tenant_id)) as tenantName, count(1) as cnt "
                            + "from repair_order o left join sys_tenant t on t.id=o.tenant_id "
                            + "where o.create_time >= date_sub(now(), interval ? day) "
                            + "group by o.tenant_id, t.name order by cnt desc limit 50", d));
        } catch (Exception e) {
            m.put("byTenantRepair", List.of());
        }
        return m;
    }

    /** 报表：超市营收 */
    public Map<String, Object> reportStore(Integer days) {
        assertPlatform();
        int d = days == null || days < 1 ? 30 : days;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("days", d);
        try {
            BigDecimal gmv = jdbc.queryForObject(
                    "select ifnull(sum(pay_amount),0) from store_order where status='COMPLETED' "
                            + "and ifnull(complete_time,create_time) >= date_sub(now(), interval ? day)",
                    BigDecimal.class, d);
            Integer orderCnt = jdbc.queryForObject(
                    "select count(1) from store_order where status='COMPLETED' "
                            + "and ifnull(complete_time,create_time) >= date_sub(now(), interval ? day)",
                    Integer.class, d);
            BigDecimal commission = jdbc.queryForObject(
                    "select ifnull(sum(commission_amount),0) from merchant_commission_bill "
                            + "where create_time >= date_sub(now(), interval ? day)",
                    BigDecimal.class, d);
            BigDecimal pending = jdbc.queryForObject(
                    "select ifnull(sum(commission_amount),0) from merchant_commission_bill where settle_status='PENDING'",
                    BigDecimal.class);
            m.put("gmv", gmv);
            m.put("orderCount", orderCnt == null ? 0 : orderCnt);
            m.put("commissionAmount", commission);
            m.put("pendingCommission", pending);
            m.put("byTenant", jdbc.queryForList(
                    "select o.tenant_id as tenantId, ifnull(t.name, concat('小区',o.tenant_id)) as tenantName, "
                            + "count(1) as orderCount, ifnull(sum(o.pay_amount),0) as gmv "
                            + "from store_order o left join sys_tenant t on t.id=o.tenant_id "
                            + "where o.status='COMPLETED' and ifnull(o.complete_time,o.create_time) >= date_sub(now(), interval ? day) "
                            + "group by o.tenant_id, t.name order by gmv desc limit 50", d));
        } catch (Exception e) {
            m.put("gmv", BigDecimal.ZERO);
            m.put("orderCount", 0);
            m.put("commissionAmount", BigDecimal.ZERO);
            m.put("pendingCommission", BigDecimal.ZERO);
            m.put("byTenant", List.of());
        }
        return m;
    }

    /**
     * 商户营收看板：平台不抽成（platformTakeRate=0），仅展示社区提成与商户实收。
     * 维度：社区汇总 → 商户（店铺）汇总 → 可选订单明细。
     */
    public Map<String, Object> reportMerchantRevenue(Integer days, Long tenantId, Long shopId) {
        assertPlatform();
        int d = days == null || days < 1 ? 30 : Math.min(days, 365);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("days", d);
        m.put("platformTakeRate", BigDecimal.ZERO);
        m.put("note", "平台不抽取提成，仅监管查看社区提成与商户营收");

        List<Map<String, Object>> byMerchant = new ArrayList<>();
        try {
            StringBuilder sql = new StringBuilder(
                    "select o.tenant_id as tenantId, ifnull(t.name, concat('小区',o.tenant_id)) as tenantName, "
                            + "o.shop_id as shopId, ifnull(s.name, '未绑定店铺') as shopName, "
                            + "m.id as merchantId, ifnull(m.commission_rate, ifnull(pr.commission_rate, 5)) as commissionRate, "
                            + "count(1) as orderCount, ifnull(sum(o.pay_amount),0) as gmv "
                            + "from store_order o "
                            + "left join sys_tenant t on t.id=o.tenant_id "
                            + "left join store_shop s on s.id=o.shop_id "
                            + "left join store_merchant m on m.tenant_id=o.tenant_id and m.name=s.name "
                            + "left join platform_profit_rule pr on pr.enabled=1 and pr.shop_type='*' "
                            + "where o.status='COMPLETED' "
                            + "and ifnull(o.complete_time,o.create_time) >= date_sub(now(), interval ? day) ");
            List<Object> args = new ArrayList<>();
            args.add(d);
            if (tenantId != null) {
                sql.append("and o.tenant_id=? ");
                args.add(tenantId);
            }
            if (shopId != null) {
                sql.append("and o.shop_id=? ");
                args.add(shopId);
            }
            sql.append("group by o.tenant_id, t.name, o.shop_id, s.name, m.id, m.commission_rate, pr.commission_rate "
                    + "order by gmv desc limit 200");
            byMerchant = jdbc.queryForList(sql.toString(), args.toArray());
        } catch (Exception e) {
            log.warn("merchant revenue byMerchant: {}", e.getMessage());
            byMerchant = new ArrayList<>();
        }

        Map<Long, BigDecimal> billCommissionByMerchant = new HashMap<>();
        Map<Long, BigDecimal> billGmvByMerchant = new HashMap<>();
        try {
            StringBuilder bsql = new StringBuilder(
                    "select merchant_id as merchantId, ifnull(sum(commission_amount),0) as commissionAmount, "
                            + "ifnull(sum(gmv),0) as billGmv "
                            + "from merchant_commission_bill "
                            + "where create_time >= date_sub(now(), interval ? day) ");
            List<Object> bargs = new ArrayList<>();
            bargs.add(d);
            if (tenantId != null) {
                bsql.append("and tenant_id=? ");
                bargs.add(tenantId);
            }
            bsql.append("group by merchant_id");
            for (Map<String, Object> row : jdbc.queryForList(bsql.toString(), bargs.toArray())) {
                Long mid = toLongObj(row.get("merchantId"));
                if (mid == null) continue;
                billCommissionByMerchant.put(mid, toBd(row.get("commissionAmount")));
                billGmvByMerchant.put(mid, toBd(row.get("billGmv")));
            }
        } catch (Exception e) {
            log.debug("merchant revenue bills: {}", e.getMessage());
        }

        BigDecimal totalGmv = BigDecimal.ZERO;
        BigDecimal totalCommission = BigDecimal.ZERO;
        int totalOrders = 0;
        Map<Long, Map<String, Object>> tenantAgg = new LinkedHashMap<>();

        for (Map<String, Object> row : byMerchant) {
            BigDecimal gmv = toBd(row.get("gmv"));
            BigDecimal rate = toBd(row.get("commissionRate"));
            Long mid = toLongObj(row.get("merchantId"));
            BigDecimal commission;
            if (mid != null && billCommissionByMerchant.containsKey(mid)
                    && billGmvByMerchant.getOrDefault(mid, BigDecimal.ZERO).compareTo(BigDecimal.ZERO) > 0
                    && gmv.compareTo(billGmvByMerchant.get(mid)) == 0) {
                commission = billCommissionByMerchant.get(mid);
            } else if (mid != null && billCommissionByMerchant.containsKey(mid)
                    && byMerchant.stream().filter(r -> Objects.equals(toLongObj(r.get("merchantId")), mid)).count() == 1) {
                commission = billCommissionByMerchant.get(mid);
            } else {
                commission = gmv.multiply(rate).divide(new BigDecimal("100"), 2, java.math.RoundingMode.HALF_UP);
            }
            BigDecimal net = gmv.subtract(commission);
            row.put("commissionAmount", commission);
            row.put("merchantNet", net);
            row.put("platformTake", BigDecimal.ZERO);

            totalGmv = totalGmv.add(gmv);
            totalCommission = totalCommission.add(commission);
            totalOrders += ((Number) row.getOrDefault("orderCount", 0)).intValue();

            Long tid = toLongObj(row.get("tenantId"));
            if (tid == null) continue;
            Map<String, Object> ta = tenantAgg.computeIfAbsent(tid, k -> {
                Map<String, Object> x = new LinkedHashMap<>();
                x.put("tenantId", tid);
                x.put("tenantName", row.get("tenantName"));
                x.put("merchantCount", 0);
                x.put("orderCount", 0);
                x.put("gmv", BigDecimal.ZERO);
                x.put("commissionAmount", BigDecimal.ZERO);
                x.put("merchantNet", BigDecimal.ZERO);
                return x;
            });
            ta.put("merchantCount", ((Number) ta.get("merchantCount")).intValue() + 1);
            ta.put("orderCount", ((Number) ta.get("orderCount")).intValue()
                    + ((Number) row.getOrDefault("orderCount", 0)).intValue());
            ta.put("gmv", toBd(ta.get("gmv")).add(gmv));
            ta.put("commissionAmount", toBd(ta.get("commissionAmount")).add(commission));
            ta.put("merchantNet", toBd(ta.get("merchantNet")).add(net));
        }

        List<Map<String, Object>> byTenant = new ArrayList<>(tenantAgg.values());
        byTenant.sort((a, b) -> toBd(b.get("gmv")).compareTo(toBd(a.get("gmv"))));

        m.put("gmv", totalGmv);
        m.put("orderCount", totalOrders);
        m.put("commissionAmount", totalCommission);
        m.put("merchantNet", totalGmv.subtract(totalCommission));
        m.put("merchantCount", byMerchant.size());
        m.put("byTenant", byTenant);
        m.put("byMerchant", byMerchant);

        if (shopId != null) {
            try {
                StringBuilder osql = new StringBuilder(
                        "select o.id as orderId, o.order_no as orderNo, o.pay_amount as gmv, "
                                + "ifnull(b.commission_rate, ifnull(m.commission_rate, 5)) as commissionRate, "
                                + "b.commission_amount as billCommission, b.settle_status as settleStatus, "
                                + "ifnull(o.complete_time, o.create_time) as completeTime "
                                + "from store_order o "
                                + "left join store_shop s on s.id=o.shop_id "
                                + "left join store_merchant m on m.tenant_id=o.tenant_id and m.name=s.name "
                                + "left join merchant_commission_bill b on b.order_id=o.id "
                                + "where o.status='COMPLETED' and o.shop_id=? "
                                + "and ifnull(o.complete_time,o.create_time) >= date_sub(now(), interval ? day) ");
                List<Object> oargs = new ArrayList<>();
                oargs.add(shopId);
                oargs.add(d);
                if (tenantId != null) {
                    osql.append("and o.tenant_id=? ");
                    oargs.add(tenantId);
                }
                osql.append("order by ifnull(o.complete_time,o.create_time) desc limit 200");
                List<Map<String, Object>> orders = jdbc.queryForList(osql.toString(), oargs.toArray());
                for (Map<String, Object> o : orders) {
                    BigDecimal gmv = toBd(o.get("gmv"));
                    BigDecimal rate = toBd(o.get("commissionRate"));
                    BigDecimal commission = o.get("billCommission") != null
                            ? toBd(o.get("billCommission"))
                            : gmv.multiply(rate).divide(new BigDecimal("100"), 2, java.math.RoundingMode.HALF_UP);
                    o.put("commissionAmount", commission);
                    o.put("merchantNet", gmv.subtract(commission));
                    o.put("platformTake", BigDecimal.ZERO);
                    Object st = o.get("settleStatus");
                    o.put("settleStatusLabel", st == null ? "未入账"
                            : ("SETTLED".equals(String.valueOf(st)) ? "已结算" : "待结算"));
                }
                m.put("orders", orders);
            } catch (Exception e) {
                m.put("orders", List.of());
            }
        } else {
            m.put("orders", List.of());
        }
        return m;
    }

    private static BigDecimal toBd(Object v) {
        if (v == null) return BigDecimal.ZERO;
        if (v instanceof BigDecimal bd) return bd;
        try {
            return new BigDecimal(String.valueOf(v));
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    private static Long toLongObj(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return n.longValue();
        try {
            return Long.parseLong(String.valueOf(v));
        } catch (Exception e) {
            return null;
        }
    }

    /** 报表：AI 消耗（读 auth 同库 ai_call_log） */
    public Map<String, Object> reportAi(Integer days) {
        assertPlatform();
        int d = days == null || days < 1 ? 30 : days;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("days", d);
        try {
            Integer total = jdbc.queryForObject(
                    "select count(1) from ai_call_log where create_time >= date_sub(now(), interval ? day)", Integer.class, d);
            Integer fail = jdbc.queryForObject(
                    "select count(1) from ai_call_log where success=0 and create_time >= date_sub(now(), interval ? day)",
                    Integer.class, d);
            m.put("totalCalls", total == null ? 0 : total);
            m.put("failCalls", fail == null ? 0 : fail);
            m.put("byScene", jdbc.queryForList(
                    "select ifnull(scene,'unknown') as scene, count(1) as cnt, "
                            + "sum(case when success=0 then 1 else 0 end) as failCnt "
                            + "from ai_call_log where create_time >= date_sub(now(), interval ? day) "
                            + "group by scene order by cnt desc", d));
            m.put("byTenant", jdbc.queryForList(
                    "select l.tenant_id as tenantId, ifnull(t.name, concat('小区',l.tenant_id)) as tenantName, "
                            + "count(1) as cnt from ai_call_log l left join sys_tenant t on t.id=l.tenant_id "
                            + "where l.create_time >= date_sub(now(), interval ? day) "
                            + "group by l.tenant_id, t.name order by cnt desc limit 50", d));
        } catch (Exception e) {
            m.put("totalCalls", 0);
            m.put("failCalls", 0);
            m.put("byScene", List.of());
            m.put("byTenant", List.of());
        }
        return m;
    }

    private int scalarInt(String sql, Object... args) {
        try {
            Integer n = jdbc.queryForObject(sql, Integer.class, args);
            return n == null ? 0 : n;
        } catch (Exception e) {
            return 0;
        }
    }

    private List<Long> allTenantIds() {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "select id from sys_tenant where deleted=0 and status in ('active','degraded')");
        List<Long> ids = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            Object id = r.get("id");
            if (id instanceof Number n) ids.add(n.longValue());
        }
        return ids;
    }

    @SuppressWarnings("unchecked")
    private List<Long> parseTenantIds(Object raw) {
        List<Long> ids = new ArrayList<>();
        if (raw instanceof List<?> list) {
            for (Object o : list) {
                if (o != null) ids.add(Long.valueOf(String.valueOf(o)));
            }
        } else if (raw != null && StringUtils.hasText(String.valueOf(raw))) {
            for (String p : String.valueOf(raw).split(",")) {
                if (StringUtils.hasText(p)) ids.add(Long.parseLong(p.trim()));
            }
        }
        return ids;
    }

    private String joinIds(List<Long> ids) {
        StringBuilder sb = new StringBuilder();
        for (Long id : ids) {
            if (sb.length() > 0) sb.append(',');
            sb.append(id);
        }
        return sb.toString();
    }

    private String operatorName() {
        Long uid = TenantContext.getUserId();
        if (uid == null) return "platform";
        try {
            String n = jdbc.queryForObject("select nickname from sys_user where id=?", String.class, uid);
            return StringUtils.hasText(n) ? n : String.valueOf(uid);
        } catch (Exception e) {
            return String.valueOf(uid);
        }
    }

    private static String str(Object v, String def) {
        if (v == null) return def;
        String s = String.valueOf(v).trim();
        return s.isEmpty() || "null".equalsIgnoreCase(s) ? def : s;
    }

    private static void normalizeTime(Map<String, Object> m, String key) {
        Object v = m.get(key);
        if (v instanceof Timestamp ts) {
            m.put(key, ts.toLocalDateTime().toString().replace('T', ' '));
        }
    }

    private static String preview(String s, int n) {
        if (s == null || "null".equals(s)) return "";
        return s.length() <= n ? s : s.substring(0, n) + "...";
    }

    private static String statusLabel(String st) {
        if (st == null) return "-";
        return switch (st) {
            case "PENDING" -> "待接单";
            case "ASSIGNED" -> "已分派";
            case "PROCESSING" -> "处理中";
            case "PENDING_ACCEPT" -> "待验收";
            case "DONE", "COMPLETED" -> "已完成";
            default -> st;
        };
    }

    private static String riskLevel(Map<String, Object> r) {
        String u = String.valueOf(r.get("urgency"));
        if ("URGENT".equals(u) || "紧急".equals(u)) return "紧急";
        if ("HIGH".equals(u) || "高".equals(u)) return "高";
        Object ct = r.get("createTime");
        if (ct != null) {
            try {
                LocalDateTime t = LocalDateTime.parse(String.valueOf(ct).replace(' ', 'T'));
                if (t.isBefore(LocalDateTime.now().minusDays(2))) return "超时";
            } catch (Exception ignored) {
            }
        }
        return "关注";
    }
}
