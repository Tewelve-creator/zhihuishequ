package com.community.repair.store;

import com.community.common.constant.RoleConstants;
import com.community.common.exception.BizException;
import com.community.common.result.PageResult;
import com.community.common.tenant.TenantContext;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.*;

/**
 * 商户营销资金账户（与 SaaS 租户套餐订购账务独立）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MerchantWalletService {

    public static final String LEDGER_RECHARGE = "RECHARGE";
    public static final String LEDGER_CONSUME = "CONSUME";
    public static final String LEDGER_WITHDRAW = "WITHDRAW";
    public static final String LEDGER_WITHDRAW_REJECT = "WITHDRAW_REJECT_REFUND";
    public static final String WD_PENDING = "PENDING";
    public static final String WD_APPROVED = "APPROVED";
    public static final String WD_REJECTED = "REJECTED";

    private final JdbcTemplate jdbc;

    @PostConstruct
    public void ensure() {
        try {
            jdbc.execute("""
                    create table if not exists store_merchant (
                      id bigint primary key auto_increment,
                      tenant_id bigint not null,
                      name varchar(128) not null,
                      contact_name varchar(64) null,
                      contact_phone varchar(32) null,
                      status tinyint not null default 1,
                      warn_threshold decimal(12,2) not null default 100.00,
                      remark varchar(255) null,
                      create_time datetime null,
                      update_time datetime null,
                      key idx_sm_tenant (tenant_id, status)
                    ) engine=InnoDB default charset=utf8mb4
                    """);
            jdbc.execute("""
                    create table if not exists merchant_wallet (
                      id bigint primary key auto_increment,
                      tenant_id bigint not null,
                      merchant_id bigint not null,
                      balance decimal(14,2) not null default 0,
                      total_recharge decimal(14,2) not null default 0,
                      total_consume decimal(14,2) not null default 0,
                      total_withdraw decimal(14,2) not null default 0,
                      pending_withdraw decimal(14,2) not null default 0,
                      status tinyint not null default 1 comment '1正常 0冻结',
                      update_time datetime null,
                      create_time datetime null,
                      unique key uk_mw_merchant (merchant_id),
                      key idx_mw_tenant (tenant_id)
                    ) engine=InnoDB default charset=utf8mb4
                    """);
            jdbc.execute("""
                    create table if not exists merchant_wallet_ledger (
                      id bigint primary key auto_increment,
                      tenant_id bigint not null,
                      merchant_id bigint not null,
                      biz_type varchar(32) not null,
                      amount decimal(14,2) not null,
                      balance_after decimal(14,2) not null,
                      remark varchar(255) null,
                      operator_id bigint null,
                      operator_name varchar(64) null,
                      ref_id bigint null,
                      create_time datetime null,
                      key idx_mwl_merchant (merchant_id, create_time),
                      key idx_mwl_tenant (tenant_id, biz_type, create_time)
                    ) engine=InnoDB default charset=utf8mb4
                    """);
            jdbc.execute("""
                    create table if not exists merchant_withdraw (
                      id bigint primary key auto_increment,
                      tenant_id bigint not null,
                      merchant_id bigint not null,
                      amount decimal(14,2) not null,
                      status varchar(32) not null default 'PENDING',
                      apply_remark varchar(255) null,
                      applicant_id bigint null,
                      applicant_name varchar(64) null,
                      audit_remark varchar(255) null,
                      auditor_id bigint null,
                      auditor_name varchar(64) null,
                      audit_time datetime null,
                      create_time datetime null,
                      key idx_mwd_status (status, create_time),
                      key idx_mwd_tenant (tenant_id, status)
                    ) engine=InnoDB default charset=utf8mb4
                    """);
            seedFromStoreInfo();
        } catch (Exception e) {
            log.warn("merchant wallet schema skip: {}", e.getMessage());
        }
    }

    private void seedFromStoreInfo() {
        try {
            List<Map<String, Object>> stores = jdbc.queryForList(
                    "select id, tenant_id, name, phone from store_info");
            for (Map<String, Object> s : stores) {
                Long tid = toLong(s.get("tenant_id"));
                String name = str(s.get("name"), "社区商户");
                Integer n = jdbc.queryForObject(
                        "select count(1) from store_merchant where tenant_id=? and name=?",
                        Integer.class, tid, name);
                if (n != null && n > 0) continue;
                jdbc.update(
                        "insert into store_merchant(tenant_id,name,contact_phone,status,warn_threshold,create_time,update_time) "
                                + "values(?,?,?,1,100,now(),now())",
                        tid, name, s.get("phone"));
                Long mid = jdbc.queryForObject("select last_insert_id()", Long.class);
                ensureWallet(tid, mid);
            }
        } catch (Exception e) {
            log.debug("seed merchant from store_info skip: {}", e.getMessage());
        }
    }

    private void ensureWallet(Long tid, Long merchantId) {
        Integer n = jdbc.queryForObject(
                "select count(1) from merchant_wallet where merchant_id=?", Integer.class, merchantId);
        if (n != null && n > 0) return;
        jdbc.update(
                "insert into merchant_wallet(tenant_id,merchant_id,balance,total_recharge,total_consume,total_withdraw,"
                        + "pending_withdraw,status,create_time,update_time) values(?,?,0,0,0,0,0,1,now(),now())",
                tid, merchantId);
    }

    public List<Map<String, Object>> listAccounts(Long tenantIdFilter, String keyword, Boolean warnOnly) {
        assertStaffOrPlatform();
        boolean platform = isPlatform();
        Long tid = TenantContext.getTenantId();
        if (!platform) {
            if (tid == null) throw new BizException(400, "缺少社区租户");
            tenantIdFilter = tid;
        }
        StringBuilder sql = new StringBuilder(
                "select m.id as merchantId, m.tenant_id as tenantId, m.name as merchantName, "
                        + "m.contact_name as contactName, m.contact_phone as contactPhone, "
                        + "m.status as merchantStatus, m.warn_threshold as warnThreshold, "
                        + "ifnull(t.name, concat('小区',m.tenant_id)) as tenantName, "
                        + "w.balance, w.total_recharge as totalRecharge, w.total_consume as totalConsume, "
                        + "w.total_withdraw as totalWithdraw, w.pending_withdraw as pendingWithdraw, "
                        + "w.status as accountStatus, w.update_time as updateTime "
                        + "from store_merchant m "
                        + "left join merchant_wallet w on w.merchant_id=m.id "
                        + "left join sys_tenant t on t.id=m.tenant_id where 1=1 ");
        List<Object> args = new ArrayList<>();
        if (tenantIdFilter != null) {
            sql.append("and m.tenant_id=? ");
            args.add(tenantIdFilter);
        }
        if (StringUtils.hasText(keyword)) {
            sql.append("and (m.name like ? or ifnull(t.name,'') like ?) ");
            String like = "%" + keyword.trim() + "%";
            args.add(like);
            args.add(like);
        }
        sql.append("order by m.tenant_id, m.id desc limit 500");
        List<Map<String, Object>> rows = jdbc.queryForList(sql.toString(), args.toArray());
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            Long mid = toLong(r.get("merchantId"));
            Long mt = toLong(r.get("tenantId"));
            ensureWallet(mt, mid);
            Map<String, Object> m = new LinkedHashMap<>(r);
            if (m.get("balance") == null) {
                try {
                    Map<String, Object> w = jdbc.queryForMap(
                            "select balance, total_recharge as totalRecharge, total_consume as totalConsume, "
                                    + "total_withdraw as totalWithdraw, pending_withdraw as pendingWithdraw, status as accountStatus "
                                    + "from merchant_wallet where merchant_id=?",
                            mid);
                    m.putAll(w);
                } catch (Exception ignored) {
                    m.put("balance", BigDecimal.ZERO);
                    m.put("totalRecharge", BigDecimal.ZERO);
                    m.put("totalConsume", BigDecimal.ZERO);
                    m.put("totalWithdraw", BigDecimal.ZERO);
                    m.put("pendingWithdraw", BigDecimal.ZERO);
                    m.put("accountStatus", 1);
                }
            }
            BigDecimal bal = toDec(m.get("balance"));
            BigDecimal th = toDec(m.get("warnThreshold"));
            boolean warn = bal.compareTo(th) < 0;
            m.put("balanceWarn", warn);
            m.put("balanceWarnLabel", warn ? "余额预警" : "正常");
            m.put("accountStatusLabel", toInt(m.get("accountStatus"), 1) == 1 ? "正常" : "冻结");
            m.put("merchantStatusLabel", toInt(m.get("merchantStatus"), 1) == 1 ? "营业" : "停用");
            if (Boolean.TRUE.equals(warnOnly) && !warn) continue;
            out.add(m);
        }
        return out;
    }

    public Map<String, Object> warnSummary() {
        assertStaffOrPlatform();
        List<Map<String, Object>> all = listAccounts(null, null, true);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("warnCount", all.size());
        m.put("items", all.size() > 10 ? all.subList(0, 10) : all);
        return m;
    }

    @Transactional
    public Map<String, Object> recharge(Long merchantId, BigDecimal amount, String remark) {
        assertPropertyOrPlatform();
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException(400, "充值金额须大于0");
        }
        Map<String, Object> merchant = requireMerchant(merchantId);
        Long tid = toLong(merchant.get("tenant_id"));
        assertTenantAccess(tid);
        ensureWallet(tid, merchantId);
        jdbc.update(
                "update merchant_wallet set balance=balance+?, total_recharge=total_recharge+?, update_time=now() where merchant_id=?",
                amount, amount, merchantId);
        BigDecimal after = currentBalance(merchantId);
        writeLedger(tid, merchantId, LEDGER_RECHARGE, amount, after, remark, null);
        return accountDetail(merchantId);
    }

    @Transactional
    public Map<String, Object> consume(Long merchantId, BigDecimal amount, String remark) {
        assertPropertyOrPlatform();
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException(400, "扣费金额须大于0");
        }
        Map<String, Object> merchant = requireMerchant(merchantId);
        Long tid = toLong(merchant.get("tenant_id"));
        assertTenantAccess(tid);
        ensureWallet(tid, merchantId);
        BigDecimal bal = currentBalance(merchantId);
        if (bal.compareTo(amount) < 0) {
            throw new BizException(400, "余额不足");
        }
        jdbc.update(
                "update merchant_wallet set balance=balance-?, total_consume=total_consume+?, update_time=now() where merchant_id=?",
                amount, amount, merchantId);
        BigDecimal after = currentBalance(merchantId);
        String rm = StringUtils.hasText(remark) ? remark : "营销服务扣费（广告/推送）";
        writeLedger(tid, merchantId, LEDGER_CONSUME, amount.negate(), after, rm, null);
        return accountDetail(merchantId);
    }

    @Transactional
    public Map<String, Object> applyWithdraw(Long merchantId, BigDecimal amount, String remark) {
        assertPropertyOrPlatform();
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException(400, "提现金额须大于0");
        }
        Map<String, Object> merchant = requireMerchant(merchantId);
        Long tid = toLong(merchant.get("tenant_id"));
        assertTenantAccess(tid);
        ensureWallet(tid, merchantId);
        BigDecimal bal = currentBalance(merchantId);
        if (bal.compareTo(amount) < 0) {
            throw new BizException(400, "余额不足，无法提现");
        }
        // 冻结：先扣余额，记 pending；驳回时退回
        jdbc.update(
                "update merchant_wallet set balance=balance-?, pending_withdraw=pending_withdraw+?, update_time=now() where merchant_id=?",
                amount, amount, merchantId);
        BigDecimal after = currentBalance(merchantId);
        writeLedger(tid, merchantId, LEDGER_WITHDRAW, amount.negate(), after,
                StringUtils.hasText(remark) ? "提现申请：" + remark : "提现申请冻结", null);
        jdbc.update(
                "insert into merchant_withdraw(tenant_id,merchant_id,amount,status,apply_remark,applicant_id,applicant_name,create_time) "
                        + "values(?,?,?,'PENDING',?,?,?,now())",
                tid, merchantId, amount, remark, TenantContext.getUserId(), operatorName());
        Long wid = jdbc.queryForObject("select last_insert_id()", Long.class);
        Map<String, Object> resp = accountDetail(merchantId);
        resp.put("withdrawId", wid);
        return resp;
    }

    @Transactional
    public Map<String, Object> auditWithdraw(Long withdrawId, String status, String auditRemark) {
        assertPlatform();
        if (!WD_APPROVED.equals(status) && !WD_REJECTED.equals(status)) {
            throw new BizException(400, "status 须为 APPROVED 或 REJECTED");
        }
        Map<String, Object> wd;
        try {
            wd = jdbc.queryForMap("select * from merchant_withdraw where id=?", withdrawId);
        } catch (Exception e) {
            throw new BizException(404, "提现申请不存在");
        }
        if (!WD_PENDING.equals(String.valueOf(wd.get("status")))) {
            throw new BizException(400, "该申请已处理");
        }
        Long merchantId = toLong(wd.get("merchant_id"));
        Long tid = toLong(wd.get("tenant_id"));
        BigDecimal amount = toDec(wd.get("amount"));
        jdbc.update(
                "update merchant_withdraw set status=?, audit_remark=?, auditor_id=?, auditor_name=?, audit_time=now() where id=?",
                status, auditRemark, TenantContext.getUserId(), operatorName(), withdrawId);
        if (WD_APPROVED.equals(status)) {
            jdbc.update(
                    "update merchant_wallet set pending_withdraw=greatest(pending_withdraw-?,0), "
                            + "total_withdraw=total_withdraw+?, update_time=now() where merchant_id=?",
                    amount, amount, merchantId);
        } else {
            jdbc.update(
                    "update merchant_wallet set balance=balance+?, pending_withdraw=greatest(pending_withdraw-?,0), update_time=now() where merchant_id=?",
                    amount, amount, merchantId);
            BigDecimal after = currentBalance(merchantId);
            writeLedger(tid, merchantId, LEDGER_WITHDRAW_REJECT, amount, after,
                    StringUtils.hasText(auditRemark) ? "提现驳回退回：" + auditRemark : "提现驳回退回余额",
                    withdrawId);
        }
        return jdbc.queryForMap(
                "select id, tenant_id as tenantId, merchant_id as merchantId, amount, status, apply_remark as applyRemark, "
                        + "audit_remark as auditRemark, auditor_name as auditorName, audit_time as auditTime, create_time as createTime "
                        + "from merchant_withdraw where id=?",
                withdrawId);
    }

    public List<Map<String, Object>> listWithdraws(Long tenantIdFilter, Long merchantId, String status) {
        assertStaffOrPlatform();
        boolean platform = isPlatform();
        if (!platform) {
            // 物业可看本小区提现记录，但不可审核
            Long tid = requireTenant();
            tenantIdFilter = tid;
        }
        StringBuilder sql = new StringBuilder(
                "select w.id, w.tenant_id as tenantId, w.merchant_id as merchantId, w.amount, w.status, "
                        + "w.apply_remark as applyRemark, w.applicant_name as applicantName, "
                        + "w.audit_remark as auditRemark, w.auditor_name as auditorName, "
                        + "w.audit_time as auditTime, w.create_time as createTime, "
                        + "m.name as merchantName, ifnull(t.name, concat('小区',w.tenant_id)) as tenantName "
                        + "from merchant_withdraw w "
                        + "left join store_merchant m on m.id=w.merchant_id "
                        + "left join sys_tenant t on t.id=w.tenant_id where 1=1 ");
        List<Object> args = new ArrayList<>();
        if (tenantIdFilter != null) {
            sql.append("and w.tenant_id=? ");
            args.add(tenantIdFilter);
        }
        if (merchantId != null) {
            sql.append("and w.merchant_id=? ");
            args.add(merchantId);
        }
        if (StringUtils.hasText(status)) {
            sql.append("and w.status=? ");
            args.add(status.trim());
        }
        sql.append("order by case when w.status='PENDING' then 0 else 1 end, w.id desc limit 500");
        List<Map<String, Object>> rows = jdbc.queryForList(sql.toString(), args.toArray());
        for (Map<String, Object> r : rows) {
            normalizeTime(r, "createTime");
            normalizeTime(r, "auditTime");
            String st = String.valueOf(r.get("status"));
            r.put("statusLabel", WD_APPROVED.equals(st) ? "已通过" : WD_REJECTED.equals(st) ? "已驳回" : "待审核");
        }
        return rows;
    }

    public PageResult<Map<String, Object>> pageLedger(
            Long tenantIdFilter, Long merchantId, String bizType, String keyword, long page, long size) {
        assertStaffOrPlatform();
        boolean platform = isPlatform();
        if (!platform) {
            tenantIdFilter = requireTenant();
        }
        StringBuilder where = new StringBuilder(" where 1=1 ");
        List<Object> args = new ArrayList<>();
        if (tenantIdFilter != null) {
            where.append(" and l.tenant_id=? ");
            args.add(tenantIdFilter);
        }
        if (merchantId != null) {
            where.append(" and l.merchant_id=? ");
            args.add(merchantId);
        }
        if (StringUtils.hasText(bizType)) {
            where.append(" and l.biz_type=? ");
            args.add(bizType.trim());
        }
        if (StringUtils.hasText(keyword)) {
            where.append(" and (l.remark like ? or l.operator_name like ? or m.name like ?) ");
            String like = "%" + keyword.trim() + "%";
            args.add(like);
            args.add(like);
            args.add(like);
        }
        Long total = jdbc.queryForObject(
                "select count(1) from merchant_wallet_ledger l left join store_merchant m on m.id=l.merchant_id " + where,
                Long.class, args.toArray());
        long t = total == null ? 0 : total;
        if (t == 0) return PageResult.empty(page, size);
        long offset = (page - 1) * size;
        List<Object> q = new ArrayList<>(args);
        q.add(size);
        q.add(offset);
        List<Map<String, Object>> records = jdbc.queryForList(
                "select l.id, l.tenant_id as tenantId, l.merchant_id as merchantId, l.biz_type as bizType, "
                        + "l.amount, l.balance_after as balanceAfter, l.remark, l.operator_id as operatorId, "
                        + "l.operator_name as operatorName, l.create_time as createTime, "
                        + "m.name as merchantName, ifnull(tn.name, concat('小区',l.tenant_id)) as tenantName "
                        + "from merchant_wallet_ledger l "
                        + "left join store_merchant m on m.id=l.merchant_id "
                        + "left join sys_tenant tn on tn.id=l.tenant_id "
                        + where + " order by l.id desc limit ? offset ?",
                q.toArray());
        for (Map<String, Object> r : records) {
            normalizeTime(r, "createTime");
            r.put("bizTypeLabel", bizTypeLabel(String.valueOf(r.get("bizType"))));
        }
        return PageResult.of(page, size, t, records);
    }

    public Map<String, Object> accountDetail(Long merchantId) {
        assertStaffOrPlatform();
        Map<String, Object> merchant = requireMerchant(merchantId);
        assertTenantAccess(toLong(merchant.get("tenant_id")));
        ensureWallet(toLong(merchant.get("tenant_id")), merchantId);
        List<Map<String, Object>> list = listAccounts(toLong(merchant.get("tenant_id")), null, null);
        return list.stream()
                .filter(m -> Objects.equals(toLong(m.get("merchantId")), merchantId))
                .findFirst()
                .orElseThrow(() -> new BizException(404, "账户不存在"));
    }

    @Transactional
    public Map<String, Object> createMerchant(Map<String, Object> body) {
        assertPropertyOrPlatform();
        Long tid;
        if (isPlatform()) {
            tid = toLong(body.get("tenantId"));
            if (tid == null) throw new BizException(400, "请指定归属小区 tenantId");
        } else {
            tid = requireTenant();
        }
        String name = str(body.get("name"), null);
        if (!StringUtils.hasText(name)) throw new BizException(400, "商户名称不能为空");
        BigDecimal th = body.get("warnThreshold") == null ? new BigDecimal("100") : toDec(body.get("warnThreshold"));
        jdbc.update(
                "insert into store_merchant(tenant_id,name,contact_name,contact_phone,status,warn_threshold,remark,create_time,update_time) "
                        + "values(?,?,?,?,1,?,?,now(),now())",
                tid, name, str(body.get("contactName"), null), str(body.get("contactPhone"), null),
                th, str(body.get("remark"), null));
        Long mid = jdbc.queryForObject("select last_insert_id()", Long.class);
        ensureWallet(tid, mid);
        return accountDetail(mid);
    }

    @Transactional
    public Map<String, Object> updateWarnThreshold(Long merchantId, BigDecimal threshold) {
        assertPropertyOrPlatform();
        Map<String, Object> merchant = requireMerchant(merchantId);
        assertTenantAccess(toLong(merchant.get("tenant_id")));
        if (threshold == null || threshold.compareTo(BigDecimal.ZERO) < 0) {
            throw new BizException(400, "预警阈值须为非负数");
        }
        jdbc.update("update store_merchant set warn_threshold=?, update_time=now() where id=?", threshold, merchantId);
        return accountDetail(merchantId);
    }

    private void writeLedger(Long tid, Long merchantId, String bizType, BigDecimal amount,
                             BigDecimal after, String remark, Long refId) {
        jdbc.update(
                "insert into merchant_wallet_ledger(tenant_id,merchant_id,biz_type,amount,balance_after,remark,"
                        + "operator_id,operator_name,ref_id,create_time) values(?,?,?,?,?,?,?,?,?,now())",
                tid, merchantId, bizType, amount, after, remark,
                TenantContext.getUserId(), operatorName(), refId);
    }

    private BigDecimal currentBalance(Long merchantId) {
        BigDecimal b = jdbc.queryForObject(
                "select balance from merchant_wallet where merchant_id=?", BigDecimal.class, merchantId);
        return b == null ? BigDecimal.ZERO : b;
    }

    private Map<String, Object> requireMerchant(Long merchantId) {
        try {
            return jdbc.queryForMap("select * from store_merchant where id=?", merchantId);
        } catch (Exception e) {
            throw new BizException(404, "商户不存在");
        }
    }

    private void assertTenantAccess(Long merchantTenantId) {
        if (isPlatform()) return;
        Long tid = TenantContext.getTenantId();
        if (tid == null || !Objects.equals(tid, merchantTenantId)) {
            throw new BizException(403, "无权访问其他小区商户");
        }
    }

    private Long requireTenant() {
        Long tid = TenantContext.getTenantId();
        if (tid == null) throw new BizException(400, "缺少社区租户");
        return tid;
    }

    private boolean isPlatform() {
        return RoleConstants.isPlatformAdmin(TenantContext.getRole());
    }

    private void assertPlatform() {
        if (!isPlatform()) throw new BizException(403, "仅平台运营可审核提现");
    }

    private void assertStaffOrPlatform() {
        String role = TenantContext.getRole();
        if (!RoleConstants.isTenantStaff(role) && !RoleConstants.isPlatformAdmin(role)) {
            throw new BizException(403, "仅物业/平台人员可查看");
        }
    }

    private void assertPropertyOrPlatform() {
        String role = TenantContext.getRole();
        if (!RoleConstants.isPropertyAdmin(role) && !RoleConstants.isPlatformAdmin(role)) {
            throw new BizException(403, "仅物业管理员或平台可操作资金");
        }
    }

    private String operatorName() {
        Long uid = TenantContext.getUserId();
        if (uid == null) return "system";
        try {
            String n = jdbc.queryForObject("select nickname from sys_user where id=?", String.class, uid);
            return StringUtils.hasText(n) ? n : String.valueOf(uid);
        } catch (Exception e) {
            return String.valueOf(uid);
        }
    }

    private static String bizTypeLabel(String t) {
        if (LEDGER_RECHARGE.equals(t)) return "充值";
        if (LEDGER_CONSUME.equals(t)) return "消费扣费";
        if (LEDGER_WITHDRAW.equals(t)) return "提现冻结";
        if (LEDGER_WITHDRAW_REJECT.equals(t)) return "提现驳回退回";
        return t;
    }

    private static void normalizeTime(Map<String, Object> m, String key) {
        Object v = m.get(key);
        if (v instanceof java.sql.Timestamp ts) {
            m.put(key, ts.toLocalDateTime().toString().replace('T', ' '));
        }
    }

    private static String str(Object v, String def) {
        if (v == null) return def;
        String s = String.valueOf(v).trim();
        return s.isEmpty() || "null".equalsIgnoreCase(s) ? def : s;
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

    private static int toInt(Object v, int def) {
        if (v == null) return def;
        if (v instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(String.valueOf(v));
        } catch (Exception e) {
            return def;
        }
    }

    private static BigDecimal toDec(Object v) {
        if (v == null) return BigDecimal.ZERO;
        if (v instanceof BigDecimal b) return b;
        try {
            return new BigDecimal(String.valueOf(v));
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }
}
