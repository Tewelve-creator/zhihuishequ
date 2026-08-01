package com.community.auth.service;

import com.community.common.constant.RoleConstants;
import com.community.common.exception.BizException;
import com.community.common.exception.ErrorCode;
import com.community.common.result.PageResult;
import com.community.common.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class OpsService {

    private static final int HEALTH_TIMEOUT_MS = 2000;
    private static final DateTimeFormatter MONTH_FMT = DateTimeFormatter.ofPattern("yyyy-MM");

    private static final List<Map.Entry<String, Integer>> SERVICE_PORTS = List.of(
            Map.entry("auth", 8081),
            Map.entry("saas", 8082),
            Map.entry("repair", 8083),
            Map.entry("ai", 8084),
            Map.entry("order", 8085),
            Map.entry("stats", 8086),
            Map.entry("gateway", 8080)
    );

    private final JdbcTemplate jdbcTemplate;

    public List<Map<String, Object>> aiQuotaOverview() {
        assertPlatform();
        TenantContext.setSkipTenant(true);
        try {
            List<Map<String, Object>> tenants = jdbcTemplate.queryForList(
                    "select id, name, status, ai_quota, expire_time from sys_tenant where deleted=0 order by id");
            List<Map<String, Object>> rows = new ArrayList<>();
            for (Map<String, Object> t : tenants) {
                Map<String, Object> row = new HashMap<>();
                row.put("id", t.get("id"));
                row.put("name", t.get("name"));
                row.put("status", t.get("status"));
                row.put("aiQuota", t.get("ai_quota"));
                row.put("expireTime", t.get("expire_time"));
                row.put("todayAiCalls", countTodayAiCalls(toLong(t.get("id"))));
                rows.add(row);
            }
            return rows;
        } finally {
            TenantContext.setSkipTenant(true);
        }
    }

    public Map<String, Object> financeOverview() {
        assertPlatform();
        TenantContext.setSkipTenant(true);
        try {
            Map<String, Object> data = new HashMap<>();
            data.put("paidAmount", sumAmount("paid"));
            data.put("paidCount", countByStatus("paid"));
            data.put("channelAmount", sumAmount("paid")); // 演示：渠道实收=系统已付（含模拟支付）
            data.put("refundAmount", sumAmount("refunded"));
            data.put("refundCount", countByStatus("refunded"));
            data.put("pendingAmount", sumAmount("pending"));
            data.put("pendingCount", countByStatus("pending"));
            data.put("byType", jdbcTemplate.queryForList(
                    "select order_type type, count(1) cnt, ifnull(sum(amount),0) amount "
                            + "from saas_order where status='paid' group by order_type"));
            try {
                data.put("byPayMethod", jdbcTemplate.queryForList(
                        "select ifnull(nullif(pay_method,''),'unknown') payMethod, count(1) cnt, "
                                + "ifnull(sum(amount),0) amount from saas_order where status='paid' "
                                + "group by ifnull(nullif(pay_method,''),'unknown')"));
            } catch (Exception e) {
                data.put("byPayMethod", List.of());
            }
            try {
                data.put("byTenant", jdbcTemplate.queryForList(
                        "select o.tenant_id as tenantId, ifnull(t.name, concat('社区',o.tenant_id)) as tenantName, "
                                + "count(1) as cnt, ifnull(sum(o.amount),0) as amount, "
                                + "sum(case when o.order_type='new' then 1 else 0 end) as newCnt, "
                                + "sum(case when o.order_type='renew' then 1 else 0 end) as renewCnt, "
                                + "sum(case when o.order_type='upgrade' then 1 else 0 end) as upgradeCnt, "
                                + "group_concat(distinct ifnull(nullif(o.pay_method,''),'unknown') "
                                + "order by o.pay_method separator ',') as payMethods "
                                + "from saas_order o left join sys_tenant t on t.id=o.tenant_id "
                                + "where o.status='paid' group by o.tenant_id, t.name order by amount desc"));
            } catch (Exception e) {
                data.put("byTenant", List.of());
            }
            data.put("monthlyTrend", monthlyTrend());
            return data;
        } finally {
            TenantContext.setSkipTenant(true);
        }
    }

    public List<Map<String, Object>> healthCheck() {
        assertPlatform();
        List<Map<String, Object>> list = new ArrayList<>();
        for (Map.Entry<String, Integer> e : SERVICE_PORTS) {
            list.add(probeService(e.getKey(), e.getValue()));
        }
        return list;
    }

    public List<Map<String, Object>> listConfig() {
        assertPlatform();
        ensureOpsTables();
        try {
            return jdbcTemplate.queryForList(
                    "select id, config_key configKey, config_value configValue, remark, update_time updateTime "
                            + "from sys_config order by config_key");
        } catch (Exception e) {
            log.debug("list config failed: {}", e.getMessage());
            return List.of();
        }
    }

    public void upsertConfig(String configKey, String configValue, String remark) {
        assertPlatform();
        ensureOpsTables();
        LocalDateTime now = LocalDateTime.now();
        int updated = jdbcTemplate.update(
                "update sys_config set config_value=?, remark=?, update_time=? where config_key=?",
                configValue, remark, now, configKey);
        if (updated == 0) {
            jdbcTemplate.update(
                    "insert into sys_config(config_key, config_value, remark, update_time) values(?,?,?,?)",
                    configKey, configValue, remark, now);
        }
    }

    public PageResult<Map<String, Object>> pageOperLogs(long page, long size) {
        assertPlatform();
        ensureOpsTables();
        try {
            Long total = jdbcTemplate.queryForObject("select count(1) from sys_oper_log", Long.class);
            long t = total == null ? 0 : total;
            if (t == 0) {
                return PageResult.empty(page, size);
            }
            long offset = (page - 1) * size;
            List<Map<String, Object>> records = jdbcTemplate.queryForList(
                    "select id, operator_id operatorId, operator_name operatorName, module, action, detail, ip, "
                            + "create_time createTime from sys_oper_log order by id desc limit ? offset ?",
                    size, offset);
            return PageResult.of(page, size, t, records);
        } catch (Exception e) {
            log.debug("page oper logs failed: {}", e.getMessage());
            return PageResult.empty(page, size);
        }
    }

    /* ========== 平台敏感词 ========== */

    public List<Map<String, Object>> listSensitiveWords() {
        assertPlatform();
        ensureSensitiveTable();
        try {
            return jdbcTemplate.queryForList(
                    "select id, word, create_time as createTime from sys_sensitive_word order by id desc");
        } catch (Exception e) {
            return List.of();
        }
    }

    public void addSensitiveWord(String word) {
        assertPlatform();
        ensureSensitiveTable();
        if (!StringUtils.hasText(word)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "敏感词不能为空");
        }
        String w = word.trim();
        Integer n = jdbcTemplate.queryForObject(
                "select count(1) from sys_sensitive_word where word=?", Integer.class, w);
        if (n != null && n > 0) {
            throw new BizException(ErrorCode.BAD_REQUEST, "敏感词已存在");
        }
        try {
            jdbcTemplate.update("insert into sys_sensitive_word(word, create_time) values(?, now())", w);
        } catch (Exception e) {
            jdbcTemplate.update("insert into sys_sensitive_word(word) values(?)", w);
        }
    }

    public void deleteSensitiveWord(Long id) {
        assertPlatform();
        if (id == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "id 不能为空");
        }
        jdbcTemplate.update("delete from sys_sensitive_word where id=?", id);
    }

    /* ========== 全局 AI 参数 ========== */

    private static final List<String[]> AI_CONFIG_PRESETS = List.of(
            new String[]{"ai.provider", "http", "AI 提供商：mock / http（DeepSeek 兼容）"},
            new String[]{"ai.api-url", "https://api.deepseek.com/v1", "大模型 API 地址"},
            new String[]{"ai.api-key", "", "大模型 API Key（仅运营可见，勿泄露）"},
            new String[]{"ai.model", "deepseek-chat", "默认模型名"},
            new String[]{"ai.daily-quota", "1000", "单租户日默认调用上限（业务参考）"},
            new String[]{"ai.rag.top-k", "5", "RAG 召回条数"},
            new String[]{"ai.sentinel.qps", "20", "AI 接口 QPS 限流参考"}
    );

    public List<Map<String, Object>> listAiConfig() {
        assertPlatform();
        ensureOpsTables();
        seedAiConfigIfEmpty();
        List<Map<String, Object>> all = listConfig();
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> row : all) {
            String key = String.valueOf(row.get("configKey"));
            if (key.startsWith("ai.")) {
                out.add(row);
            }
        }
        return out;
    }

    public void saveAiConfig(Map<String, Object> body) {
        assertPlatform();
        if (body == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "参数不能为空");
        }
        String key = String.valueOf(body.getOrDefault("configKey", "")).trim();
        if (!key.startsWith("ai.")) {
            throw new BizException(ErrorCode.BAD_REQUEST, "仅允许保存 ai. 前缀配置");
        }
        String value = body.get("configValue") == null ? "" : String.valueOf(body.get("configValue"));
        String remark = body.get("remark") == null ? null : String.valueOf(body.get("remark"));
        upsertConfig(key, value, remark);
    }

    private void seedAiConfigIfEmpty() {
        for (String[] p : AI_CONFIG_PRESETS) {
            try {
                Integer n = jdbcTemplate.queryForObject(
                        "select count(1) from sys_config where config_key=?", Integer.class, p[0]);
                if (n == null || n == 0) {
                    upsertConfig(p[0], p[1], p[2]);
                }
            } catch (Exception ignored) {
            }
        }
    }

    /* ========== AI 调用监控 ========== */

    public Map<String, Object> aiCallStats(int days) {
        assertPlatform();
        TenantContext.setSkipTenant(true);
        try {
            int d = Math.max(1, Math.min(days, 90));
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("days", d);
            m.put("totalCalls", cntAi("select count(1) from ai_call_log where create_time>=date_sub(now(), interval ? day)", d));
            m.put("successCalls", cntAi(
                    "select count(1) from ai_call_log where success=1 and create_time>=date_sub(now(), interval ? day)", d));
            m.put("failCalls", cntAi(
                    "select count(1) from ai_call_log where success=0 and create_time>=date_sub(now(), interval ? day)", d));
            m.put("todayCalls", cntAi("select count(1) from ai_call_log where date(create_time)=curdate()"));

            List<Map<String, Object>> byTenant = new ArrayList<>();
            try {
                byTenant = jdbcTemplate.queryForList(
                        "select l.tenant_id as tenantId, ifnull(t.name, concat('租户',l.tenant_id)) as tenantName, "
                                + "count(1) as total, sum(case when l.success=1 then 1 else 0 end) as successCnt, "
                                + "round(avg(l.cost_ms),0) as avgCostMs "
                                + "from ai_call_log l left join sys_tenant t on t.id=l.tenant_id "
                                + "where l.create_time>=date_sub(now(), interval ? day) "
                                + "group by l.tenant_id, t.name order by total desc limit 50",
                        d);
            } catch (Exception ignored) {
            }
            m.put("byTenant", byTenant);

            List<Map<String, Object>> byScene = new ArrayList<>();
            try {
                byScene = jdbcTemplate.queryForList(
                        "select ifnull(nullif(scene,''),'unknown') as scene, count(1) as total, "
                                + "sum(case when success=1 then 1 else 0 end) as successCnt "
                                + "from ai_call_log where create_time>=date_sub(now(), interval ? day) "
                                + "group by scene order by total desc",
                        d);
            } catch (Exception ignored) {
            }
            m.put("byScene", byScene);

            List<Map<String, Object>> recentTrend = new ArrayList<>();
            try {
                recentTrend = jdbcTemplate.queryForList(
                        "select date(create_time) as day, count(1) as total, "
                                + "sum(case when success=1 then 1 else 0 end) as successCnt "
                                + "from ai_call_log where create_time>=date_sub(now(), interval ? day) "
                                + "group by date(create_time) order by day",
                        d);
            } catch (Exception ignored) {
            }
            m.put("recentTrend", recentTrend);
            return m;
        } finally {
            TenantContext.setSkipTenant(true);
        }
    }

    public PageResult<Map<String, Object>> pageAiCalls(Long tenantId, String scene, long page, long size) {
        assertPlatform();
        TenantContext.setSkipTenant(true);
        try {
            StringBuilder where = new StringBuilder(" where 1=1 ");
            List<Object> args = new ArrayList<>();
            if (tenantId != null) {
                where.append(" and l.tenant_id=? ");
                args.add(tenantId);
            }
            if (StringUtils.hasText(scene)) {
                where.append(" and l.scene=? ");
                args.add(scene.trim());
            }
            Long total;
            try {
                List<Object> cargs = new ArrayList<>(args);
                total = jdbcTemplate.queryForObject(
                        "select count(1) from ai_call_log l" + where, Long.class, cargs.toArray());
            } catch (Exception e) {
                return PageResult.empty(page, size);
            }
            long t = total == null ? 0 : total;
            if (t == 0) {
                return PageResult.empty(page, size);
            }
            long offset = (page - 1) * size;
            List<Object> qargs = new ArrayList<>(args);
            qargs.add(size);
            qargs.add(offset);
            List<Map<String, Object>> records = jdbcTemplate.queryForList(
                    "select l.id, l.tenant_id as tenantId, ifnull(t.name, concat('租户',l.tenant_id)) as tenantName, "
                            + "l.scene, l.success, l.cost_ms as costMs, l.remark, l.create_time as createTime "
                            + "from ai_call_log l left join sys_tenant t on t.id=l.tenant_id "
                            + where + " order by l.id desc limit ? offset ?",
                    qargs.toArray());
            for (Map<String, Object> r : records) {
                Object ct = r.get("createTime");
                if (ct instanceof java.sql.Timestamp ts) {
                    r.put("createTime", ts.toLocalDateTime().toString().replace('T', ' '));
                }
                r.put("successLabel", toInt(r.get("success")) == 1 ? "成功" : "失败");
                r.put("providerLabel", "DeepSeek/兼容接口");
            }
            return PageResult.of(page, size, t, records);
        } finally {
            TenantContext.setSkipTenant(true);
        }
    }

    private long cntAi(String sql, Object... args) {
        try {
            Long v = jdbcTemplate.queryForObject(sql, Long.class, args);
            return v == null ? 0 : v;
        } catch (Exception e) {
            return 0;
        }
    }

    private int toInt(Object o) {
        if (o == null) return 0;
        if (o instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(String.valueOf(o));
        } catch (Exception e) {
            return 0;
        }
    }

    private void ensureSensitiveTable() {
        try {
            jdbcTemplate.execute(
                    """
                    CREATE TABLE IF NOT EXISTS sys_sensitive_word (
                      id BIGINT PRIMARY KEY AUTO_INCREMENT,
                      word VARCHAR(64) NOT NULL,
                      create_time DATETIME NULL,
                      UNIQUE KEY uk_word (word)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """);
        } catch (Exception e) {
            log.debug("ensure sensitive table skip: {}", e.getMessage());
        }
    }

    private void ensureOpsTables() {
        try {
            jdbcTemplate.execute(
                    """
                    CREATE TABLE IF NOT EXISTS sys_config (
                      id BIGINT PRIMARY KEY AUTO_INCREMENT,
                      config_key VARCHAR(128) NOT NULL,
                      config_value VARCHAR(2000) NULL,
                      remark VARCHAR(255) NULL,
                      update_time DATETIME NULL,
                      UNIQUE KEY uk_config_key (config_key)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """);
            jdbcTemplate.execute(
                    """
                    CREATE TABLE IF NOT EXISTS sys_oper_log (
                      id BIGINT PRIMARY KEY AUTO_INCREMENT,
                      operator_id BIGINT NULL,
                      operator_name VARCHAR(64) NULL,
                      module VARCHAR(64) NOT NULL,
                      action VARCHAR(64) NOT NULL,
                      detail VARCHAR(1000) NULL,
                      ip VARCHAR(64) NULL,
                      create_time DATETIME NULL,
                      KEY idx_time (create_time)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """);
        } catch (Exception e) {
            log.warn("ensure ops tables failed: {}", e.getMessage());
        }
    }

    private long countTodayAiCalls(Long tenantId) {
        if (tenantId == null) {
            return 0;
        }
        try {
            Long v = jdbcTemplate.queryForObject(
                    "select count(1) from ai_call_log where tenant_id=? and date(create_time)=curdate()",
                    Long.class, tenantId);
            return v == null ? 0 : v;
        } catch (Exception e) {
            return 0;
        }
    }

    private BigDecimal sumAmount(String status) {
        Object v = jdbcTemplate.queryForObject(
                "select ifnull(sum(amount),0) from saas_order where status=?", Object.class, status);
        if (v instanceof BigDecimal bd) {
            return bd;
        }
        if (v instanceof Number n) {
            return BigDecimal.valueOf(n.doubleValue());
        }
        return BigDecimal.ZERO;
    }

    private long countByStatus(String status) {
        Long v = jdbcTemplate.queryForObject(
                "select count(1) from saas_order where status=?", Long.class, status);
        return v == null ? 0 : v;
    }

    private List<Map<String, Object>> monthlyTrend() {
        YearMonth end = YearMonth.from(LocalDate.now());
        List<Map<String, Object>> trend = new ArrayList<>();
        for (int i = 5; i >= 0; i--) {
            YearMonth ym = end.minusMonths(i);
            String month = ym.format(MONTH_FMT);
            LocalDateTime start = ym.atDay(1).atStartOfDay();
            LocalDateTime next = ym.plusMonths(1).atDay(1).atStartOfDay();
            Long paidCnt = jdbcTemplate.queryForObject(
                    "select count(1) from saas_order where status='paid' and pay_time>=? and pay_time<?",
                    Long.class, start, next);
            Object paidAmt = jdbcTemplate.queryForObject(
                    "select ifnull(sum(amount),0) from saas_order where status='paid' and pay_time>=? and pay_time<?",
                    Object.class, start, next);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("month", month);
            row.put("paidCount", paidCnt == null ? 0 : paidCnt);
            row.put("paidAmount", paidAmt);
            trend.add(row);
        }
        return trend;
    }

    private Map<String, Object> probeService(String name, int port) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("name", name);
        row.put("url", "http://127.0.0.1:" + port);
        long start = System.currentTimeMillis();
        boolean up = false;
        for (String path : List.of("/actuator/health", "/")) {
            try {
                URL url = new URL("http://127.0.0.1:" + port + path);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(HEALTH_TIMEOUT_MS);
                conn.setReadTimeout(HEALTH_TIMEOUT_MS);
                int code = conn.getResponseCode();
                conn.disconnect();
                if (code >= 200 && code < 500) {
                    up = true;
                    break;
                }
            } catch (Exception ignored) {
                // try next path
            }
        }
        row.put("status", up ? "up" : "down");
        row.put("latencyMs", System.currentTimeMillis() - start);
        return row;
    }

    /* ---------- 租户权限模板 ---------- */

    public List<Map<String, Object>> listFeatureTemplates() {
        assertPlatform();
        ensureFeatureTemplateTable();
        seedFeatureTemplates();
        return jdbcTemplate.queryForList(
                "select id, name, feature_codes as featureCodes, remark, create_time as createTime, update_time as updateTime "
                        + "from tenant_feature_template order by id");
    }

    public Map<String, Object> saveFeatureTemplate(Map<String, Object> body) {
        assertPlatform();
        ensureFeatureTemplateTable();
        String name = body.get("name") == null ? null : String.valueOf(body.get("name")).trim();
        if (!StringUtils.hasText(name)) {
            throw new BizException(400, "模板名称不能为空");
        }
        String codes = normalizeFeatureCodes(body.get("featureCodes"));
        String remark = body.get("remark") == null ? null : String.valueOf(body.get("remark"));
        Long id = toLong(body.get("id"));
        if (id != null && id > 0) {
            jdbcTemplate.update(
                    "update tenant_feature_template set name=?, feature_codes=?, remark=?, update_time=now() where id=?",
                    name, codes, remark, id);
        } else {
            jdbcTemplate.update(
                    "insert into tenant_feature_template(name,feature_codes,remark,create_time,update_time) values(?,?,?,now(),now())",
                    name, codes, remark);
            id = jdbcTemplate.queryForObject("select last_insert_id()", Long.class);
        }
        Long finalId = id;
        return listFeatureTemplates().stream()
                .filter(r -> finalId != null && finalId.equals(toLong(r.get("id"))))
                .findFirst()
                .orElseThrow(() -> new BizException(500, "保存失败"));
    }

    public void deleteFeatureTemplate(Long id) {
        assertPlatform();
        jdbcTemplate.update("delete from tenant_feature_template where id=?", id);
    }

    public Map<String, Object> applyFeatureTemplate(Long templateId, Map<String, Object> body) {
        assertPlatform();
        Map<String, Object> tpl;
        try {
            tpl = jdbcTemplate.queryForMap(
                    "select * from tenant_feature_template where id=?", templateId);
        } catch (Exception e) {
            throw new BizException(404, "模板不存在");
        }
        String codes = String.valueOf(tpl.get("feature_codes"));
        List<Long> tenantIds = new ArrayList<>();
        Object raw = body == null ? null : body.get("tenantIds");
        if (raw instanceof List<?> list) {
            for (Object o : list) {
                if (o != null) tenantIds.add(Long.valueOf(String.valueOf(o)));
            }
        }
        if (tenantIds.isEmpty() && body != null && body.get("tenantId") != null) {
            tenantIds.add(Long.valueOf(String.valueOf(body.get("tenantId"))));
        }
        if (tenantIds.isEmpty()) {
            throw new BizException(400, "请指定要应用的租户 tenantIds");
        }
        int n = 0;
        for (Long tid : tenantIds) {
            n += jdbcTemplate.update(
                    "update sys_tenant set features=? where id=? and deleted=0", codes, tid);
        }
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("applied", n);
        resp.put("featureCodes", codes);
        resp.put("templateName", tpl.get("name"));
        return resp;
    }

    private void ensureFeatureTemplateTable() {
        try {
            jdbcTemplate.execute("""
                    create table if not exists tenant_feature_template (
                      id bigint primary key auto_increment,
                      name varchar(64) not null,
                      feature_codes varchar(1000) not null,
                      remark varchar(255) null,
                      create_time datetime null,
                      update_time datetime null,
                      unique key uk_tft_name (name)
                    ) engine=InnoDB default charset=utf8mb4
                    """);
        } catch (Exception e) {
            log.debug("ensure feature template skip: {}", e.getMessage());
        }
    }

    private void seedFeatureTemplates() {
        try {
            Integer n = jdbcTemplate.queryForObject("select count(1) from tenant_feature_template", Integer.class);
            if (n != null && n > 0) return;
            jdbcTemplate.update(
                    "insert into tenant_feature_template(name,feature_codes,remark,create_time,update_time) values(?,?,?,now(),now())",
                    "基础物业包",
                    "basic_repair,basic_notice",
                    "仅报修+公告");
            jdbcTemplate.update(
                    "insert into tenant_feature_template(name,feature_codes,remark,create_time,update_time) values(?,?,?,now(),now())",
                    "标准智慧社区",
                    "basic_repair,basic_notice,ai_chat,ai_image_repair,data_dashboard,data_export",
                    "含 AI 管家与识图");
            jdbcTemplate.update(
                    "insert into tenant_feature_template(name,feature_codes,remark,create_time,update_time) values(?,?,?,now(),now())",
                    "全量旗舰包",
                    "basic_repair,basic_notice,ai_chat,ai_image_repair,ai_emotion,ai_lost_match,data_dashboard,data_export,vendor_manage",
                    "全部增值模块（含社区商超）");
        } catch (Exception ignored) {
        }
    }

    private static String normalizeFeatureCodes(Object raw) {
        if (raw instanceof List<?> list) {
            StringBuilder sb = new StringBuilder();
            for (Object o : list) {
                if (o == null) continue;
                if (sb.length() > 0) sb.append(',');
                sb.append(String.valueOf(o).trim());
            }
            return sb.toString();
        }
        if (raw == null) return "";
        return String.valueOf(raw).trim();
    }

    private void assertPlatform() {
        if (!RoleConstants.isPlatformAdmin(TenantContext.getRole())) {
            throw new BizException(ErrorCode.FORBIDDEN, "仅平台运营可访问");
        }
    }

    private static Long toLong(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof Number n) {
            return n.longValue();
        }
        if (v instanceof String s && StringUtils.hasText(s)) {
            return Long.parseLong(s);
        }
        return null;
    }
}
