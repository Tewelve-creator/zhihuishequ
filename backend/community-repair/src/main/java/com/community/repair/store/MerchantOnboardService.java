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
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.util.*;

/**
 * 商圈商户入驻审批 + 交易佣金应收（与 SaaS 套餐订购账务独立）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MerchantOnboardService {

    /** 本期仅支持社区超市，暂不开放餐饮/家政等类型 */
    public static final String SHOP_TYPE_SUPERMARKET = "社区超市";

    public static final String ST_PENDING_PROPERTY = "PENDING_PROPERTY";
    public static final String ST_PENDING_PLATFORM = "PENDING_PLATFORM";
    public static final String ST_APPROVED = "APPROVED";
    public static final String ST_REJECTED_PROPERTY = "REJECTED_PROPERTY";
    public static final String ST_REJECTED_PLATFORM = "REJECTED_PLATFORM";

    public static final String SETTLE_PENDING = "PENDING";
    public static final String SETTLE_SETTLED = "SETTLED";

    private final JdbcTemplate jdbc;

    @PostConstruct
    public void ensure() {
        try {
            jdbc.execute("""
                    create table if not exists store_merchant (
                      id bigint primary key auto_increment,
                      tenant_id bigint not null,
                      name varchar(128) not null,
                      shop_type varchar(64) null,
                      contact_name varchar(64) null,
                      contact_phone varchar(32) null,
                      status tinyint not null default 1,
                      onboard_status varchar(32) null default 'APPROVED',
                      warn_threshold decimal(12,2) not null default 100.00,
                      commission_rate decimal(5,2) null default 5.00,
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
                      status tinyint not null default 1,
                      update_time datetime null,
                      create_time datetime null,
                      unique key uk_mw_merchant (merchant_id),
                      key idx_mw_tenant (tenant_id)
                    ) engine=InnoDB default charset=utf8mb4
                    """);
            jdbc.execute("""
                    create table if not exists store_merchant_apply (
                      id bigint primary key auto_increment,
                      tenant_id bigint not null,
                      merchant_name varchar(128) not null,
                      shop_type varchar(64) null,
                      contact_name varchar(64) null,
                      contact_phone varchar(32) null,
                      address varchar(255) null,
                      business_license varchar(128) null,
                      license_url varchar(512) null,
                      intro varchar(500) null,
                      commission_rate decimal(5,2) not null default 5.00,
                      status varchar(32) not null default 'PENDING_PROPERTY',
                      reject_reason varchar(255) null,
                      merchant_id bigint null,
                      applicant_id bigint null,
                      applicant_name varchar(64) null,
                      property_auditor_id bigint null,
                      property_auditor_name varchar(64) null,
                      property_audit_time datetime null,
                      property_audit_remark varchar(255) null,
                      platform_auditor_id bigint null,
                      platform_auditor_name varchar(64) null,
                      platform_audit_time datetime null,
                      platform_audit_remark varchar(255) null,
                      create_time datetime null,
                      update_time datetime null,
                      key idx_sma_tenant_status (tenant_id, status),
                      key idx_sma_status (status, create_time)
                    ) engine=InnoDB default charset=utf8mb4
                    """);
            jdbc.execute("""
                    create table if not exists store_merchant_audit_log (
                      id bigint primary key auto_increment,
                      tenant_id bigint not null,
                      apply_id bigint not null,
                      stage varchar(32) not null comment 'PROPERTY/PLATFORM',
                      action varchar(32) not null comment 'APPROVE/REJECT/SUBMIT',
                      remark varchar(255) null,
                      operator_id bigint null,
                      operator_name varchar(64) null,
                      create_time datetime null,
                      key idx_smal_apply (apply_id, create_time),
                      key idx_smal_tenant (tenant_id, create_time)
                    ) engine=InnoDB default charset=utf8mb4
                    """);
            jdbc.execute("""
                    create table if not exists merchant_commission_bill (
                      id bigint primary key auto_increment,
                      tenant_id bigint not null,
                      merchant_id bigint not null,
                      order_id bigint null,
                      order_no varchar(64) null,
                      gmv decimal(14,2) not null default 0,
                      commission_rate decimal(5,2) not null default 5.00,
                      commission_amount decimal(14,2) not null default 0,
                      settle_status varchar(16) not null default 'PENDING',
                      settle_time datetime null,
                      settle_batch_no varchar(64) null,
                      remark varchar(255) null,
                      create_time datetime null,
                      unique key uk_mcb_order (order_id),
                      key idx_mcb_merchant (merchant_id, settle_status),
                      key idx_mcb_tenant (tenant_id, settle_status, create_time)
                    ) engine=InnoDB default charset=utf8mb4
                    """);
            jdbc.execute("""
                    create table if not exists platform_profit_rule (
                      id bigint primary key auto_increment,
                      shop_type varchar(64) not null comment '店铺类型，* 表示默认',
                      commission_rate decimal(5,2) not null default 5.00 comment '平台向商户收取佣金%',
                      platform_share_rate decimal(5,2) not null default 70.00 comment '平台分润占比%',
                      property_share_rate decimal(5,2) not null default 30.00 comment '物业分润占比%',
                      enabled tinyint not null default 1,
                      remark varchar(255) null,
                      create_time datetime null,
                      update_time datetime null,
                      unique key uk_ppr_type (shop_type)
                    ) engine=InnoDB default charset=utf8mb4
                    """);
            seedProfitRules();
            ensureMerchantTypeColumn();
        } catch (Exception e) {
            log.warn("merchant onboard schema skip: {}", e.getMessage());
        }
    }

    private void seedProfitRules() {
        // 平台不抽成：platform_share=0，社区提成归物业 property_share=100；commission_rate 为社区向商户计提比例
        Object[][] defaults = {
                {"*", new BigDecimal("5.00"), new BigDecimal("0.00"), new BigDecimal("100.00"), "社区提成默认（平台不抽成）"},
                {SHOP_TYPE_SUPERMARKET, new BigDecimal("5.00"), new BigDecimal("0.00"), new BigDecimal("100.00"), "社区超市提成规则（平台不抽成）"},
        };
        for (Object[] d : defaults) {
            Integer n = jdbc.queryForObject(
                    "select count(1) from platform_profit_rule where shop_type=?", Integer.class, d[0]);
            if (n != null && n > 0) continue;
            jdbc.update(
                    "insert into platform_profit_rule(shop_type,commission_rate,platform_share_rate,property_share_rate,"
                            + "enabled,remark,create_time,update_time) values(?,?,?,?,1,?,now(),now())",
                    d[0], d[1], d[2], d[3], d[4]);
        }
        // 统一历史规则为平台 0% / 物业 100%
        try {
            jdbc.update(
                    "update platform_profit_rule set platform_share_rate=0, property_share_rate=100, "
                            + "remark=case when ifnull(remark,'') like '%平台不抽成%' then remark "
                            + "else concat(ifnull(remark,''), '（平台不抽成）') end, update_time=now() "
                            + "where platform_share_rate<>0 or property_share_rate<>100");
        } catch (Exception ignored) {
        }
        // 停用非超市类历史规则（本期不开放）
        try {
            jdbc.update(
                    "update platform_profit_rule set enabled=0, remark=concat(ifnull(remark,''),'[本期仅超市]'), update_time=now() "
                            + "where shop_type not in ('*','社区超市') and enabled=1");
        } catch (Exception ignored) {
        }
    }

    private void ensureMerchantTypeColumn() {
        try {
            Integer n = jdbc.queryForObject(
                    "select count(1) from information_schema.columns where table_schema=database() "
                            + "and table_name='store_merchant' and column_name='shop_type'",
                    Integer.class);
            if (n == null || n == 0) {
                jdbc.execute("alter table store_merchant add column shop_type varchar(64) null after name");
            }
            Integer c = jdbc.queryForObject(
                    "select count(1) from information_schema.columns where table_schema=database() "
                            + "and table_name='store_merchant' and column_name='onboard_status'",
                    Integer.class);
            if (c == null || c == 0) {
                jdbc.execute("alter table store_merchant add column onboard_status varchar(32) null default 'APPROVED' after status");
            }
            Integer r = jdbc.queryForObject(
                    "select count(1) from information_schema.columns where table_schema=database() "
                            + "and table_name='store_merchant' and column_name='commission_rate'",
                    Integer.class);
            if (r == null || r == 0) {
                jdbc.execute("alter table store_merchant add column commission_rate decimal(5,2) null default 5.00 after warn_threshold");
            }
        } catch (Exception e) {
            log.debug("store_merchant ext columns skip: {}", e.getMessage());
        }
    }

    /* ========== 入驻申请 ========== */

    @Transactional
    public Map<String, Object> submitApply(Map<String, Object> body) {
        assertPropertyOrPlatform();
        Long tid = isPlatform() && body.get("tenantId") != null
                ? toLong(body.get("tenantId")) : requireTenant();
        if (!isPlatform()) assertTenantAccess(tid);
        String name = str(body.get("merchantName"), str(body.get("name"), null));
        if (!StringUtils.hasText(name)) throw new BizException(400, "超市名称不能为空");
        String shopType = str(body.get("shopType"), SHOP_TYPE_SUPERMARKET);
        if (!SHOP_TYPE_SUPERMARKET.equals(shopType) && !"便利店".equals(shopType) && !"超市".equals(shopType)) {
            throw new BizException(400, "本期仅支持社区超市入驻，暂不开放其他商户类型");
        }
        shopType = SHOP_TYPE_SUPERMARKET;
        BigDecimal rate = body.get("commissionRate") == null
                ? new BigDecimal("5") : toDec(body.get("commissionRate"));
        jdbc.update(
                "insert into store_merchant_apply(tenant_id,merchant_name,shop_type,contact_name,contact_phone,"
                        + "address,business_license,license_url,intro,commission_rate,status,"
                        + "applicant_id,applicant_name,create_time,update_time) "
                        + "values(?,?,?,?,?,?,?,?,?,?,'PENDING_PROPERTY',?,?,now(),now())",
                tid, name, shopType,
                str(body.get("contactName"), null), str(body.get("contactPhone"), null),
                str(body.get("address"), null), str(body.get("businessLicense"), null),
                str(body.get("licenseUrl"), null), str(body.get("intro"), null), rate,
                TenantContext.getUserId(), operatorName());
        Long id = jdbc.queryForObject("select last_insert_id()", Long.class);
        writeAudit(tid, id, "PROPERTY", "SUBMIT", "提交入驻申请");
        return getApply(id);
    }

    public Map<String, Object> getApply(Long id) {
        assertStaffOrPlatform();
        Map<String, Object> row;
        try {
            row = jdbc.queryForMap(
                    "select a.*, ifnull(t.name, concat('小区',a.tenant_id)) as tenantName "
                            + "from store_merchant_apply a left join sys_tenant t on t.id=a.tenant_id where a.id=?",
                    id);
        } catch (Exception e) {
            throw new BizException(404, "入驻申请不存在");
        }
        assertTenantAccess(toLong(row.get("tenant_id")));
        return mapApply(row);
    }

    public List<Map<String, Object>> listApplies(Long tenantIdFilter, String status, String keyword) {
        assertStaffOrPlatform();
        if (!isPlatform()) {
            tenantIdFilter = requireTenant();
        }
        StringBuilder sql = new StringBuilder(
                "select a.id, a.tenant_id as tenantId, a.merchant_name as merchantName, a.shop_type as shopType, "
                        + "a.contact_name as contactName, a.contact_phone as contactPhone, a.address, "
                        + "a.business_license as businessLicense, a.license_url as licenseUrl, a.intro, "
                        + "a.commission_rate as commissionRate, a.status, a.reject_reason as rejectReason, "
                        + "a.merchant_id as merchantId, a.applicant_name as applicantName, "
                        + "a.property_auditor_name as propertyAuditorName, a.property_audit_time as propertyAuditTime, "
                        + "a.property_audit_remark as propertyAuditRemark, "
                        + "a.platform_auditor_name as platformAuditorName, a.platform_audit_time as platformAuditTime, "
                        + "a.platform_audit_remark as platformAuditRemark, "
                        + "a.create_time as createTime, a.update_time as updateTime, "
                        + "ifnull(t.name, concat('小区',a.tenant_id)) as tenantName "
                        + "from store_merchant_apply a "
                        + "left join sys_tenant t on t.id=a.tenant_id where 1=1 ");
        List<Object> args = new ArrayList<>();
        if (tenantIdFilter != null) {
            sql.append("and a.tenant_id=? ");
            args.add(tenantIdFilter);
        }
        if (StringUtils.hasText(status)) {
            sql.append("and a.status=? ");
            args.add(status.trim());
        }
        if (StringUtils.hasText(keyword)) {
            sql.append("and (a.merchant_name like ? or ifnull(t.name,'') like ?) ");
            String like = "%" + keyword.trim() + "%";
            args.add(like);
            args.add(like);
        }
        // 本期仅社区超市
        sql.append("and (a.shop_type is null or a.shop_type in ('社区超市','便利店','超市')) ");
        sql.append("order by case a.status "
                + "when 'PENDING_PROPERTY' then 0 when 'PENDING_PLATFORM' then 1 else 2 end, a.id desc limit 500");
        List<Map<String, Object>> rows = jdbc.queryForList(sql.toString(), args.toArray());
        for (Map<String, Object> r : rows) {
            enrichApply(r);
        }
        return rows;
    }

    public List<Map<String, Object>> listPropertyPending(String keyword) {
        assertStaffOrPlatform();
        return listApplies(null, ST_PENDING_PROPERTY, keyword);
    }

    public List<Map<String, Object>> listPlatformPending(String keyword) {
        assertPlatform();
        return listApplies(null, ST_PENDING_PLATFORM, keyword);
    }

    @Transactional
    public Map<String, Object> propertyAudit(Long applyId, boolean approve, String remark) {
        assertPropertyAdmin();
        Map<String, Object> apply = requireApplyRow(applyId);
        Long tid = toLong(apply.get("tenant_id"));
        assertTenantAccess(tid);
        if (!ST_PENDING_PROPERTY.equals(String.valueOf(apply.get("status")))) {
            throw new BizException(400, "当前状态不可物业初审");
        }
        if (!approve && !StringUtils.hasText(remark)) {
            throw new BizException(400, "驳回须填写原因");
        }
        if (approve) {
            jdbc.update(
                    "update store_merchant_apply set status=?, property_auditor_id=?, property_auditor_name=?, "
                            + "property_audit_time=now(), property_audit_remark=?, reject_reason=null, update_time=now() where id=?",
                    ST_PENDING_PLATFORM, TenantContext.getUserId(), operatorName(), remark, applyId);
            writeAudit(tid, applyId, "PROPERTY", "APPROVE",
                    StringUtils.hasText(remark) ? remark : "物业初审通过，提交平台终审");
        } else {
            jdbc.update(
                    "update store_merchant_apply set status=?, reject_reason=?, property_auditor_id=?, "
                            + "property_auditor_name=?, property_audit_time=now(), property_audit_remark=?, update_time=now() where id=?",
                    ST_REJECTED_PROPERTY, remark, TenantContext.getUserId(), operatorName(), remark, applyId);
            writeAudit(tid, applyId, "PROPERTY", "REJECT", remark);
        }
        return getApply(applyId);
    }

    @Transactional
    public Map<String, Object> platformAudit(Long applyId, boolean approve, String remark) {
        assertPlatform();
        Map<String, Object> apply = requireApplyRow(applyId);
        Long tid = toLong(apply.get("tenant_id"));
        if (!ST_PENDING_PLATFORM.equals(String.valueOf(apply.get("status")))) {
            throw new BizException(400, "当前状态不可平台终审（须物业初审通过后）");
        }
        if (!approve && !StringUtils.hasText(remark)) {
            throw new BizException(400, "驳回须填写原因");
        }
        if (approve) {
            Long merchantId = createMerchantFromApply(apply);
            jdbc.update(
                    "update store_merchant_apply set status=?, merchant_id=?, platform_auditor_id=?, platform_auditor_name=?, "
                            + "platform_audit_time=now(), platform_audit_remark=?, reject_reason=null, update_time=now() where id=?",
                    ST_APPROVED, merchantId, TenantContext.getUserId(), operatorName(), remark, applyId);
            writeAudit(tid, applyId, "PLATFORM", "APPROVE",
                    StringUtils.hasText(remark) ? remark : "平台终审通过，商户入驻生效");
        } else {
            jdbc.update(
                    "update store_merchant_apply set status=?, reject_reason=?, platform_auditor_id=?, "
                            + "platform_auditor_name=?, platform_audit_time=now(), platform_audit_remark=?, update_time=now() where id=?",
                    ST_REJECTED_PLATFORM, remark, TenantContext.getUserId(), operatorName(), remark, applyId);
            writeAudit(tid, applyId, "PLATFORM", "REJECT", remark);
        }
        return getApply(applyId);
    }

    public List<Map<String, Object>> listAuditLogs(Long applyId) {
        assertStaffOrPlatform();
        Map<String, Object> apply = requireApplyRow(applyId);
        assertTenantAccess(toLong(apply.get("tenant_id")));
        List<Map<String, Object>> rows = jdbc.queryForList(
                "select id, apply_id as applyId, stage, action, remark, operator_id as operatorId, "
                        + "operator_name as operatorName, create_time as createTime "
                        + "from store_merchant_audit_log where apply_id=? order by id",
                applyId);
        for (Map<String, Object> r : rows) {
            normalizeTime(r, "createTime");
            r.put("stageLabel", "PLATFORM".equals(String.valueOf(r.get("stage"))) ? "平台终审" : "物业初审");
            String act = String.valueOf(r.get("action"));
            r.put("actionLabel", "APPROVE".equals(act) ? "通过" : "REJECT".equals(act) ? "驳回" : "提交");
        }
        return rows;
    }

    private Long createMerchantFromApply(Map<String, Object> apply) {
        Long tid = toLong(apply.get("tenant_id"));
        String name = String.valueOf(apply.get("merchant_name"));
        BigDecimal rate = toDec(apply.get("commission_rate"));
        jdbc.update(
                "insert into store_merchant(tenant_id,name,shop_type,contact_name,contact_phone,status,onboard_status,"
                        + "warn_threshold,commission_rate,remark,create_time,update_time) "
                        + "values(?,?,?,?,?,1,'APPROVED',100,?,?,now(),now())",
                tid, name, apply.get("shop_type"), apply.get("contact_name"), apply.get("contact_phone"),
                rate, "入驻终审通过");
        Long mid = jdbc.queryForObject("select last_insert_id()", Long.class);
        Integer n = jdbc.queryForObject(
                "select count(1) from merchant_wallet where merchant_id=?", Integer.class, mid);
        if (n == null || n == 0) {
            jdbc.update(
                    "insert into merchant_wallet(tenant_id,merchant_id,balance,total_recharge,total_consume,"
                            + "total_withdraw,pending_withdraw,status,create_time,update_time) "
                            + "values(?,?,0,0,0,0,0,1,now(),now())",
                    tid, mid);
        }
        // 同步到商超可运营店铺 store_shop（物业「商户列表」数据源）
        ensureStoreShopFromMerchant(tid, mid, name,
                apply.get("contact_name") == null ? null : String.valueOf(apply.get("contact_name")),
                apply.get("contact_phone") == null ? null : String.valueOf(apply.get("contact_phone")),
                apply.get("address") == null ? null : String.valueOf(apply.get("address")));
        return mid;
    }

    /** 入驻通过后写入 store_shop；已存在同名店铺则跳过 */
    private void ensureStoreShopFromMerchant(Long tid, Long merchantId, String name,
                                            String contactName, String contactPhone, String address) {
        if (tid == null || !StringUtils.hasText(name)) {
            return;
        }
        try {
            Integer exist = jdbc.queryForObject(
                    "select count(1) from store_shop where tenant_id=? and name=? and deleted=0",
                    Integer.class, tid, name.trim());
            if (exist != null && exist > 0) {
                return;
            }
            jdbc.update(
                    "insert into store_shop(tenant_id,name,contact_name,contact_phone,address,open_time,close_time,"
                            + "status,remark,deleted,create_time,update_time) "
                            + "values(?,?,?,?,?,'08:00','22:00',1,?,0,now(),now())",
                    tid, name.trim(), contactName, contactPhone, address,
                    "入驻同步#" + (merchantId == null ? "" : merchantId));
        } catch (Exception e) {
            log.warn("ensureStoreShopFromMerchant fail: {}", e.getMessage());
        }
    }

    /* ========== 入驻总览（含应收汇总） ========== */

    public List<Map<String, Object>> listOverview(
            Long tenantIdFilter, String keyword, String onboardStatus, String settleFilter) {
        assertStaffOrPlatform();
        if (!isPlatform()) {
            tenantIdFilter = requireTenant();
        }
        syncCommissionBills(tenantIdFilter);

        // 以入驻申请为主展示；已通过关联 merchant_id；未通过也展示
        StringBuilder sql = new StringBuilder(
                "select a.id as applyId, a.tenant_id as tenantId, a.merchant_name as merchantName, "
                        + "a.shop_type as shopType, a.status as onboardStatus, a.merchant_id as merchantId, "
                        + "a.commission_rate as commissionRate, a.contact_name as contactName, "
                        + "a.contact_phone as contactPhone, a.address, a.create_time as createTime, "
                        + "ifnull(t.name, concat('小区',a.tenant_id)) as tenantName "
                        + "from store_merchant_apply a "
                        + "left join sys_tenant t on t.id=a.tenant_id where 1=1 ");
        List<Object> args = new ArrayList<>();
        if (tenantIdFilter != null) {
            sql.append("and a.tenant_id=? ");
            args.add(tenantIdFilter);
        }
        if (StringUtils.hasText(onboardStatus)) {
            sql.append("and a.status=? ");
            args.add(onboardStatus.trim());
        }
        if (StringUtils.hasText(keyword)) {
            sql.append("and (a.merchant_name like ? or ifnull(t.name,'') like ?) ");
            String like = "%" + keyword.trim() + "%";
            args.add(like);
            args.add(like);
        }
        sql.append("and (a.shop_type is null or a.shop_type in ('社区超市','便利店','超市')) ");
        sql.append("order by a.id desc limit 500");
        List<Map<String, Object>> rows = jdbc.queryForList(sql.toString(), args.toArray());
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            enrichApply(r);
            // 统一展示为社区超市
            r.put("shopType", SHOP_TYPE_SUPERMARKET);
            Long mid = toLong(r.get("merchantId"));
            BigDecimal total = BigDecimal.ZERO;
            BigDecimal settled = BigDecimal.ZERO;
            BigDecimal pending = BigDecimal.ZERO;
            if (mid != null) {
                Map<String, Object> sum = sumCommission(mid);
                total = toDec(sum.get("total"));
                settled = toDec(sum.get("settled"));
                pending = toDec(sum.get("pending"));
            }
            r.put("totalCommission", total);
            r.put("settledAmount", settled);
            r.put("pendingAmount", pending);
            r.put("settleStatusLabel", pending.compareTo(BigDecimal.ZERO) > 0
                    ? (settled.compareTo(BigDecimal.ZERO) > 0 ? "部分待结算" : "待结算")
                    : (total.compareTo(BigDecimal.ZERO) > 0 ? "已结清" : "暂无应收"));
            if (StringUtils.hasText(settleFilter)) {
                String sf = settleFilter.trim();
                if ("PENDING".equals(sf) && pending.compareTo(BigDecimal.ZERO) <= 0) continue;
                if ("SETTLED".equals(sf) && (pending.compareTo(BigDecimal.ZERO) > 0 || total.compareTo(BigDecimal.ZERO) <= 0)) continue;
            }
            out.add(r);
        }
        // 补充：无申请记录但已有 store_merchant 的历史商户
        if (!StringUtils.hasText(onboardStatus) || ST_APPROVED.equals(onboardStatus)) {
            appendLegacyMerchants(out, tenantIdFilter, keyword, settleFilter);
        }
        return out;
    }

    private void appendLegacyMerchants(
            List<Map<String, Object>> out, Long tenantIdFilter, String keyword, String settleFilter) {
        Set<Long> exists = new HashSet<>();
        for (Map<String, Object> r : out) {
            Long mid = toLong(r.get("merchantId"));
            if (mid != null) exists.add(mid);
        }
        StringBuilder sql = new StringBuilder(
                "select m.id as merchantId, m.tenant_id as tenantId, m.name as merchantName, "
                        + "m.shop_type as shopType, ifnull(m.onboard_status,'APPROVED') as onboardStatus, "
                        + "m.commission_rate as commissionRate, m.contact_name as contactName, "
                        + "m.contact_phone as contactPhone, m.create_time as createTime, "
                        + "ifnull(t.name, concat('小区',m.tenant_id)) as tenantName "
                        + "from store_merchant m left join sys_tenant t on t.id=m.tenant_id where 1=1 ");
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
        sql.append("and (m.shop_type is null or m.shop_type in ('社区超市','便利店','超市') "
                + "or m.name like '%超市%' or m.name like '%便利%') ");
        sql.append("order by m.id desc limit 200");
        List<Map<String, Object>> rows = jdbc.queryForList(sql.toString(), args.toArray());
        for (Map<String, Object> r : rows) {
            Long mid = toLong(r.get("merchantId"));
            if (mid == null || exists.contains(mid)) continue;
            r.put("applyId", null);
            r.put("shopType", SHOP_TYPE_SUPERMARKET);
            r.put("onboardStatusLabel", "已入驻");
            Map<String, Object> sum = sumCommission(mid);
            BigDecimal total = toDec(sum.get("total"));
            BigDecimal settled = toDec(sum.get("settled"));
            BigDecimal pending = toDec(sum.get("pending"));
            r.put("totalCommission", total);
            r.put("settledAmount", settled);
            r.put("pendingAmount", pending);
            r.put("settleStatusLabel", pending.compareTo(BigDecimal.ZERO) > 0
                    ? (settled.compareTo(BigDecimal.ZERO) > 0 ? "部分待结算" : "待结算")
                    : (total.compareTo(BigDecimal.ZERO) > 0 ? "已结清" : "暂无应收"));
            if (StringUtils.hasText(settleFilter)) {
                String sf = settleFilter.trim();
                if ("PENDING".equals(sf) && pending.compareTo(BigDecimal.ZERO) <= 0) continue;
                if ("SETTLED".equals(sf) && (pending.compareTo(BigDecimal.ZERO) > 0 || total.compareTo(BigDecimal.ZERO) <= 0)) continue;
            }
            out.add(r);
        }
    }

    private Map<String, Object> sumCommission(Long merchantId) {
        Map<String, Object> m = new LinkedHashMap<>();
        try {
            BigDecimal total = jdbc.queryForObject(
                    "select ifnull(sum(commission_amount),0) from merchant_commission_bill where merchant_id=?",
                    BigDecimal.class, merchantId);
            BigDecimal settled = jdbc.queryForObject(
                    "select ifnull(sum(commission_amount),0) from merchant_commission_bill where merchant_id=? and settle_status=?",
                    BigDecimal.class, merchantId, SETTLE_SETTLED);
            BigDecimal pending = jdbc.queryForObject(
                    "select ifnull(sum(commission_amount),0) from merchant_commission_bill where merchant_id=? and settle_status=?",
                    BigDecimal.class, merchantId, SETTLE_PENDING);
            m.put("total", total);
            m.put("settled", settled);
            m.put("pending", pending);
        } catch (Exception e) {
            m.put("total", BigDecimal.ZERO);
            m.put("settled", BigDecimal.ZERO);
            m.put("pending", BigDecimal.ZERO);
        }
        return m;
    }

    /* ========== 应收账单 ========== */

    public void syncCommissionBills(Long tenantIdFilter) {
        try {
            // 将已完成订单写入应收；按租户找主商户（优先已入驻 store_merchant）
            StringBuilder sql = new StringBuilder(
                    "select o.id, o.tenant_id, o.order_no, o.pay_amount, o.complete_time, o.pay_time "
                            + "from store_order o where o.status='COMPLETED' "
                            + "and not exists (select 1 from merchant_commission_bill b where b.order_id=o.id) ");
            List<Object> args = new ArrayList<>();
            if (tenantIdFilter != null) {
                sql.append("and o.tenant_id=? ");
                args.add(tenantIdFilter);
            } else if (!isPlatform()) {
                sql.append("and o.tenant_id=? ");
                args.add(requireTenant());
            }
            sql.append("order by o.id desc limit 1000");
            List<Map<String, Object>> orders = jdbc.queryForList(sql.toString(), args.toArray());
            for (Map<String, Object> o : orders) {
                Long tid = toLong(o.get("tenant_id"));
                Long mid = resolveMerchantId(tid);
                if (mid == null) continue;
                BigDecimal rate = resolveCommissionRate(mid, tid);
                BigDecimal gmv = toDec(o.get("pay_amount"));
                BigDecimal commission = gmv.multiply(rate).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
                try {
                    jdbc.update(
                            "insert into merchant_commission_bill(tenant_id,merchant_id,order_id,order_no,gmv,"
                                    + "commission_rate,commission_amount,settle_status,remark,create_time) "
                                    + "values(?,?,?,?,?,?,?,'PENDING','订单完成自动计提佣金',?)",
                            tid, mid, o.get("id"), o.get("order_no"), gmv, rate, commission,
                            o.get("complete_time") != null ? o.get("complete_time")
                                    : (o.get("pay_time") != null ? o.get("pay_time") : new Timestamp(System.currentTimeMillis())));
                } catch (Exception ignored) {
                    // unique order_id
                }
            }
        } catch (Exception e) {
            log.debug("sync commission skip: {}", e.getMessage());
        }
    }

    private Long resolveMerchantId(Long tid) {
        try {
            return jdbc.queryForObject(
                    "select id from store_merchant where tenant_id=? and ifnull(onboard_status,'APPROVED')='APPROVED' "
                            + "order by id asc limit 1",
                    Long.class, tid);
        } catch (Exception e) {
            return null;
        }
    }

    private BigDecimal resolveCommissionRate(Long merchantId, Long tid) {
        try {
            BigDecimal r = jdbc.queryForObject(
                    "select commission_rate from store_merchant where id=?", BigDecimal.class, merchantId);
            if (r != null && r.compareTo(BigDecimal.ZERO) > 0) return r;
        } catch (Exception ignored) {
        }
        try {
            String shopType = jdbc.queryForObject(
                    "select shop_type from store_merchant where id=?", String.class, merchantId);
            BigDecimal fromRule = rateFromProfitRule(shopType);
            if (fromRule != null) return fromRule;
        } catch (Exception ignored) {
        }
        try {
            BigDecimal r = jdbc.queryForObject(
                    "select commission_rate from store_info where tenant_id=? limit 1", BigDecimal.class, tid);
            if (r != null && r.compareTo(BigDecimal.ZERO) > 0) return r;
        } catch (Exception ignored) {
        }
        BigDecimal def = rateFromProfitRule("*");
        return def != null ? def : new BigDecimal("5");
    }

    private BigDecimal rateFromProfitRule(String shopType) {
        try {
            if (StringUtils.hasText(shopType)) {
                List<Map<String, Object>> rows = jdbc.queryForList(
                        "select commission_rate from platform_profit_rule where enabled=1 and shop_type=? limit 1",
                        shopType.trim());
                if (!rows.isEmpty()) return toDec(rows.get(0).get("commission_rate"));
            }
            List<Map<String, Object>> def = jdbc.queryForList(
                    "select commission_rate from platform_profit_rule where enabled=1 and shop_type='*' limit 1");
            if (!def.isEmpty()) return toDec(def.get(0).get("commission_rate"));
        } catch (Exception ignored) {
        }
        return null;
    }

    /* ========== 平台分润规则 ========== */

    public List<Map<String, Object>> listProfitRules() {
        assertPlatform();
        List<Map<String, Object>> rows = jdbc.queryForList(
                "select id, shop_type as shopType, commission_rate as commissionRate, "
                        + "platform_share_rate as platformShareRate, property_share_rate as propertyShareRate, "
                        + "enabled, remark, update_time as updateTime "
                        + "from platform_profit_rule "
                        + "where shop_type in ('*','社区超市') or enabled=1 "
                        + "order by case when shop_type='*' then 0 when shop_type='社区超市' then 1 else 2 end, id");
        for (Map<String, Object> r : rows) {
            normalizeTime(r, "updateTime");
            r.put("shopTypeLabel", "*".equals(String.valueOf(r.get("shopType"))) ? "全局默认" : r.get("shopType"));
            r.put("enabledLabel", toInt(r.get("enabled"), 1) == 1 ? "启用" : "停用");
        }
        return rows;
    }

    @Transactional
    public Map<String, Object> saveProfitRule(Map<String, Object> body) {
        assertPlatform();
        String shopType = str(body.get("shopType"), null);
        if (!StringUtils.hasText(shopType)) throw new BizException(400, "店铺类型不能为空（全局默认用 *）");
        if (!"*".equals(shopType) && !SHOP_TYPE_SUPERMARKET.equals(shopType)) {
            throw new BizException(400, "本期仅支持社区超市分润规则");
        }
        BigDecimal commission = toDec(body.get("commissionRate"));
        BigDecimal platformShare = body.get("platformShareRate") == null
                ? new BigDecimal("70") : toDec(body.get("platformShareRate"));
        BigDecimal propertyShare = body.get("propertyShareRate") == null
                ? new BigDecimal("30") : toDec(body.get("propertyShareRate"));
        if (commission.compareTo(BigDecimal.ZERO) < 0 || commission.compareTo(new BigDecimal("100")) > 0) {
            throw new BizException(400, "佣金比例须在 0-100");
        }
        if (platformShare.add(propertyShare).compareTo(new BigDecimal("100")) != 0) {
            throw new BizException(400, "平台分润% + 物业分润% 须等于 100");
        }
        int enabled = body.get("enabled") == null ? 1 : toInt(body.get("enabled"), 1);
        String remark = str(body.get("remark"), null);
        Long id = toLong(body.get("id"));
        Long savedId = id;
        if (id != null && id > 0) {
            jdbc.update(
                    "update platform_profit_rule set shop_type=?, commission_rate=?, platform_share_rate=?, "
                            + "property_share_rate=?, enabled=?, remark=?, update_time=now() where id=?",
                    shopType, commission, platformShare, propertyShare, enabled, remark, id);
        } else {
            Integer exists = jdbc.queryForObject(
                    "select count(1) from platform_profit_rule where shop_type=?", Integer.class, shopType);
            if (exists != null && exists > 0) {
                jdbc.update(
                        "update platform_profit_rule set commission_rate=?, platform_share_rate=?, "
                                + "property_share_rate=?, enabled=?, remark=?, update_time=now() where shop_type=?",
                        commission, platformShare, propertyShare, enabled, remark, shopType);
            } else {
                jdbc.update(
                        "insert into platform_profit_rule(shop_type,commission_rate,platform_share_rate,"
                                + "property_share_rate,enabled,remark,create_time,update_time) values(?,?,?,?,?,?,now(),now())",
                        shopType, commission, platformShare, propertyShare, enabled, remark);
                savedId = jdbc.queryForObject("select last_insert_id()", Long.class);
            }
        }
        final Long matchId = savedId;
        final String matchType = shopType;
        return listProfitRules().stream()
                .filter(r -> matchType.equals(String.valueOf(r.get("shopType")))
                        || (matchId != null && Objects.equals(toLong(r.get("id")), matchId)))
                .findFirst()
                .orElseThrow(() -> new BizException(500, "保存失败"));
    }

    public void deleteProfitRule(Long id) {
        assertPlatform();
        Map<String, Object> row;
        try {
            row = jdbc.queryForMap("select shop_type from platform_profit_rule where id=?", id);
        } catch (Exception e) {
            throw new BizException(404, "规则不存在");
        }
        if ("*".equals(String.valueOf(row.get("shop_type")))) {
            throw new BizException(400, "全局默认规则不可删除，可修改比例");
        }
        jdbc.update("delete from platform_profit_rule where id=?", id);
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

    public PageResult<Map<String, Object>> pageBills(
            Long tenantIdFilter, Long merchantId, String settleStatus, String keyword, long page, long size) {
        assertStaffOrPlatform();
        if (!isPlatform()) {
            tenantIdFilter = requireTenant();
        }
        syncCommissionBills(tenantIdFilter);
        StringBuilder where = new StringBuilder(" where 1=1 ");
        List<Object> args = new ArrayList<>();
        if (tenantIdFilter != null) {
            where.append(" and b.tenant_id=? ");
            args.add(tenantIdFilter);
        }
        if (merchantId != null) {
            where.append(" and b.merchant_id=? ");
            args.add(merchantId);
        }
        if (StringUtils.hasText(settleStatus)) {
            where.append(" and b.settle_status=? ");
            args.add(settleStatus.trim());
        }
        if (StringUtils.hasText(keyword)) {
            where.append(" and (b.order_no like ? or m.name like ?) ");
            String like = "%" + keyword.trim() + "%";
            args.add(like);
            args.add(like);
        }
        Long total = jdbc.queryForObject(
                "select count(1) from merchant_commission_bill b "
                        + "left join store_merchant m on m.id=b.merchant_id " + where,
                Long.class, args.toArray());
        long t = total == null ? 0 : total;
        if (t == 0) return PageResult.empty(page, size);
        List<Object> q = new ArrayList<>(args);
        q.add(size);
        q.add((page - 1) * size);
        List<Map<String, Object>> records = jdbc.queryForList(
                "select b.id, b.tenant_id as tenantId, b.merchant_id as merchantId, b.order_id as orderId, "
                        + "b.order_no as orderNo, b.gmv, b.commission_rate as commissionRate, "
                        + "b.commission_amount as commissionAmount, b.settle_status as settleStatus, "
                        + "b.settle_time as settleTime, b.settle_batch_no as settleBatchNo, b.remark, "
                        + "b.create_time as createTime, m.name as merchantName, "
                        + "ifnull(tn.name, concat('小区',b.tenant_id)) as tenantName "
                        + "from merchant_commission_bill b "
                        + "left join store_merchant m on m.id=b.merchant_id "
                        + "left join sys_tenant tn on tn.id=b.tenant_id "
                        + where + " order by b.id desc limit ? offset ?",
                q.toArray());
        for (Map<String, Object> r : records) {
            normalizeTime(r, "createTime");
            normalizeTime(r, "settleTime");
            r.put("settleStatusLabel", SETTLE_SETTLED.equals(String.valueOf(r.get("settleStatus"))) ? "已结算" : "待结算");
        }
        return PageResult.of(page, size, t, records);
    }

    @Transactional
    public int settleBills(List<Long> billIds, String batchNo) {
        assertPlatformOrPropertyAdmin();
        if (billIds == null || billIds.isEmpty()) throw new BizException(400, "请选择账单");
        String batch = StringUtils.hasText(batchNo) ? batchNo : ("S" + System.currentTimeMillis());
        int n = 0;
        for (Long id : billIds) {
            Map<String, Object> bill;
            try {
                bill = jdbc.queryForMap("select * from merchant_commission_bill where id=?", id);
            } catch (Exception e) {
                continue;
            }
            assertTenantAccess(toLong(bill.get("tenant_id")));
            if (!SETTLE_PENDING.equals(String.valueOf(bill.get("settle_status")))) continue;
            n += jdbc.update(
                    "update merchant_commission_bill set settle_status=?, settle_time=now(), settle_batch_no=? where id=?",
                    SETTLE_SETTLED, batch, id);
        }
        return n;
    }

    /* ========== helpers ========== */

    private void writeAudit(Long tid, Long applyId, String stage, String action, String remark) {
        jdbc.update(
                "insert into store_merchant_audit_log(tenant_id,apply_id,stage,action,remark,operator_id,operator_name,create_time) "
                        + "values(?,?,?,?,?,?,?,now())",
                tid, applyId, stage, action, remark, TenantContext.getUserId(), operatorName());
    }

    private Map<String, Object> requireApplyRow(Long id) {
        try {
            return jdbc.queryForMap("select * from store_merchant_apply where id=?", id);
        } catch (Exception e) {
            throw new BizException(404, "入驻申请不存在");
        }
    }

    private Map<String, Object> mapApply(Map<String, Object> row) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", row.get("id"));
        m.put("tenantId", row.get("tenant_id"));
        m.put("tenantName", row.get("tenantName"));
        m.put("merchantName", row.get("merchant_name"));
        m.put("shopType", row.get("shop_type"));
        m.put("contactName", row.get("contact_name"));
        m.put("contactPhone", row.get("contact_phone"));
        m.put("address", row.get("address"));
        m.put("businessLicense", row.get("business_license"));
        m.put("licenseUrl", row.get("license_url"));
        m.put("intro", row.get("intro"));
        m.put("commissionRate", row.get("commission_rate"));
        m.put("status", row.get("status"));
        m.put("rejectReason", row.get("reject_reason"));
        m.put("merchantId", row.get("merchant_id"));
        m.put("applicantName", row.get("applicant_name"));
        m.put("propertyAuditorName", row.get("property_auditor_name"));
        m.put("propertyAuditTime", row.get("property_audit_time"));
        m.put("propertyAuditRemark", row.get("property_audit_remark"));
        m.put("platformAuditorName", row.get("platform_auditor_name"));
        m.put("platformAuditTime", row.get("platform_audit_time"));
        m.put("platformAuditRemark", row.get("platform_audit_remark"));
        m.put("createTime", row.get("create_time"));
        m.put("updateTime", row.get("update_time"));
        enrichApply(m);
        return m;
    }

    private void enrichApply(Map<String, Object> r) {
        normalizeTime(r, "createTime");
        normalizeTime(r, "updateTime");
        normalizeTime(r, "propertyAuditTime");
        normalizeTime(r, "platformAuditTime");
        String st = String.valueOf(r.get("status") != null ? r.get("status") : r.get("onboardStatus"));
        r.put("onboardStatus", st);
        r.put("statusLabel", statusLabel(st));
        r.put("onboardStatusLabel", statusLabel(st));
        if (r.get("shopType") == null || !StringUtils.hasText(String.valueOf(r.get("shopType")))) {
            r.put("shopType", SHOP_TYPE_SUPERMARKET);
        } else if ("便利店".equals(String.valueOf(r.get("shopType"))) || "超市".equals(String.valueOf(r.get("shopType")))) {
            r.put("shopType", SHOP_TYPE_SUPERMARKET);
        }
    }

    private static String statusLabel(String st) {
        if (ST_PENDING_PROPERTY.equals(st)) return "待物业初审";
        if (ST_PENDING_PLATFORM.equals(st)) return "待平台终审";
        if (ST_APPROVED.equals(st)) return "已入驻";
        if (ST_REJECTED_PROPERTY.equals(st)) return "物业已驳回";
        if (ST_REJECTED_PLATFORM.equals(st)) return "平台已驳回";
        return st;
    }

    private void assertTenantAccess(Long merchantTenantId) {
        if (isPlatform()) return;
        Long tid = TenantContext.getTenantId();
        if (tid == null || !Objects.equals(tid, merchantTenantId)) {
            throw new BizException(403, "无权访问其他小区数据");
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
        if (!isPlatform()) throw new BizException(403, "仅平台运营可操作");
    }

    private void assertStaffOrPlatform() {
        String role = TenantContext.getRole();
        if (!RoleConstants.isTenantStaff(role) && !RoleConstants.isPlatformAdmin(role)) {
            throw new BizException(403, "仅物业/平台人员可查看");
        }
    }

    private void assertPropertyOrPlatform() {
        String role = TenantContext.getRole();
        if (!RoleConstants.isPropertyAdmin(role) && !RoleConstants.isPlatformAdmin(role)
                && !RoleConstants.isTenantStaff(role)) {
            throw new BizException(403, "无权限提交入驻申请");
        }
    }

    private void assertPropertyAdmin() {
        String role = TenantContext.getRole();
        if (!RoleConstants.isPropertyAdmin(role) && !RoleConstants.isPlatformAdmin(role)) {
            // 网格员可看不可审；平台代审不走此接口
            if (RoleConstants.isGridWorker(role)) {
                throw new BizException(403, "网格员无初审权限");
            }
            if (!RoleConstants.isPropertyAdmin(role)) {
                throw new BizException(403, "仅物业管理员可初审");
            }
        }
        if (isPlatform()) {
            // 平台不在此做初审
            throw new BizException(403, "请使用平台终审接口");
        }
    }

    private void assertPlatformOrPropertyAdmin() {
        String role = TenantContext.getRole();
        if (!RoleConstants.isPlatformAdmin(role) && !RoleConstants.isPropertyAdmin(role)) {
            throw new BizException(403, "仅平台或物业管理员可结算");
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

    private static void normalizeTime(Map<String, Object> m, String key) {
        Object v = m.get(key);
        if (v instanceof Timestamp ts) {
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
