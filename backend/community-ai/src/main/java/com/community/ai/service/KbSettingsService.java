package com.community.ai.service;

import com.community.common.constant.RoleConstants;
import com.community.common.exception.BizException;
import com.community.common.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 知识库检索参数与系统提示词（按租户持久化，内存缓存）
 */
@Service
@RequiredArgsConstructor
public class KbSettingsService {

    public static final String DEFAULT_SYSTEM_PROMPT = """
            你是小区AI智能管家。
            1. 优先使用提供的小区知识库资料解答用户问题；
            2. 若无相关知识库内容，可以凭借自身知识正常回答各类问题，无需拒绝；
            3. 本次回答没有引用小区知识库内容时，回答结尾必须带上：⚠温馨提示：该内容不属于本小区官方规定，仅供参考，小区相关事务建议联系物业核实。
            4. 语气友好通俗，简洁作答。
            """;

    private final JdbcTemplate jdbc;
    private final ConcurrentHashMap<Long, Map<String, Object>> cache = new ConcurrentHashMap<>();

    public Map<String, Object> get() {
        assertStaff();
        return load(requireTenant());
    }

    /** RAG / 对话内部调用，不校验角色 */
    public Map<String, Object> loadForTenant(Long tenantId) {
        if (tenantId == null) return defaults();
        return load(tenantId);
    }

    public double minScore(Long tenantId) {
        Object v = loadForTenant(tenantId).get("minScore");
        if (v instanceof Number n) return n.doubleValue();
        try {
            return Double.parseDouble(String.valueOf(v));
        } catch (Exception e) {
            return 0.32;
        }
    }

    public int topK(Long tenantId) {
        Object v = loadForTenant(tenantId).get("topK");
        if (v instanceof Number n) return Math.max(1, Math.min(10, n.intValue()));
        return 3;
    }

    public String systemPrompt(Long tenantId) {
        Object v = loadForTenant(tenantId).get("systemPrompt");
        String s = v == null ? null : String.valueOf(v);
        return StringUtils.hasText(s) ? s : DEFAULT_SYSTEM_PROMPT;
    }

    public boolean enableUnofficialTip(Long tenantId) {
        return flag(loadForTenant(tenantId).get("enableUnofficialTip"), true);
    }

    public boolean preferDocument(Long tenantId) {
        return flag(loadForTenant(tenantId).get("preferDocument"), true);
    }

    public String replyStrategy(Long tenantId) {
        Object v = loadForTenant(tenantId).get("replyStrategy");
        String s = v == null ? "rag_first" : String.valueOf(v);
        return switch (s) {
            case "llm_first", "kb_only" -> s;
            default -> "rag_first";
        };
    }

    @Transactional
    public Map<String, Object> save(Map<String, Object> body) {
        assertAdmin();
        ensureTable();
        Long tid = requireTenant();
        double minScore = body.get("minScore") == null ? 0.32
                : Double.parseDouble(String.valueOf(body.get("minScore")));
        if (minScore < 0.05 || minScore > 0.95) throw new BizException("相似度阈值建议在 0.05～0.95");
        int topK = body.get("topK") == null ? 3 : Integer.parseInt(String.valueOf(body.get("topK")));
        if (topK < 1 || topK > 10) throw new BizException("检索条数建议在 1～10");
        String prompt = body.get("systemPrompt") == null ? DEFAULT_SYSTEM_PROMPT : String.valueOf(body.get("systemPrompt"));
        int tip = flag(body.get("enableUnofficialTip"), true) ? 1 : 0;
        int prefer = flag(body.get("preferDocument"), true) ? 1 : 0;
        String strategy = body.get("replyStrategy") == null ? "rag_first" : String.valueOf(body.get("replyStrategy"));
        if (!List.of("rag_first", "llm_first", "kb_only").contains(strategy)) {
            strategy = "rag_first";
        }

        Integer exists = jdbc.queryForObject(
                "select count(1) from knowledge_settings where tenant_id=?", Integer.class, tid);
        if (exists != null && exists > 0) {
            jdbc.update(
                    "update knowledge_settings set min_score=?, top_k=?, system_prompt=?, enable_unofficial_tip=?, prefer_document=?, reply_strategy=? where tenant_id=?",
                    minScore, topK, prompt, tip, prefer, strategy, tid);
        } else {
            jdbc.update(
                    "insert into knowledge_settings(tenant_id,min_score,top_k,system_prompt,enable_unofficial_tip,prefer_document,reply_strategy) values(?,?,?,?,?,?,?)",
                    tid, minScore, topK, prompt, tip, prefer, strategy);
        }
        cache.remove(tid);
        return load(tid);
    }

    private Map<String, Object> load(Long tid) {
        ensureTable();
        Map<String, Object> cached = cache.get(tid);
        if (cached != null) return cached;
        List<Map<String, Object>> rows = jdbc.queryForList(
                "select * from knowledge_settings where tenant_id=?", tid);
        Map<String, Object> m;
        if (rows.isEmpty()) {
            m = defaults();
            m.put("tenantId", tid);
        } else {
            m = camel(rows.get(0));
            if (!StringUtils.hasText(str(m.get("systemPrompt")))) {
                m.put("systemPrompt", DEFAULT_SYSTEM_PROMPT);
            }
            if (m.get("replyStrategy") == null) m.put("replyStrategy", "rag_first");
        }
        cache.put(tid, m);
        return m;
    }

    private Map<String, Object> defaults() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("minScore", 0.32);
        m.put("topK", 3);
        m.put("systemPrompt", DEFAULT_SYSTEM_PROMPT);
        m.put("enableUnofficialTip", 1);
        m.put("preferDocument", 1);
        m.put("replyStrategy", "rag_first");
        return m;
    }

    public void ensureTable() {
        try {
            jdbc.execute(
                    "create table if not exists knowledge_settings ("
                            + "id bigint primary key auto_increment,"
                            + "tenant_id bigint not null,"
                            + "min_score double not null default 0.32,"
                            + "top_k int not null default 3,"
                            + "system_prompt text null,"
                            + "enable_unofficial_tip tinyint not null default 1,"
                            + "prefer_document tinyint not null default 1,"
                            + "reply_strategy varchar(32) not null default 'rag_first',"
                            + "update_time datetime default current_timestamp on update current_timestamp,"
                            + "unique key uk_tenant (tenant_id)"
                            + ") engine=InnoDB default charset=utf8mb4");
        } catch (Exception ignored) {
        }
    }

    private boolean flag(Object v, boolean def) {
        if (v == null) return def;
        if (v instanceof Boolean b) return b;
        if (v instanceof Number n) return n.intValue() != 0;
        String s = String.valueOf(v);
        if ("0".equals(s) || "false".equalsIgnoreCase(s)) return false;
        if ("1".equals(s) || "true".equalsIgnoreCase(s)) return true;
        return def;
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
            throw new BizException(403, "仅物业管理员可修改设置");
        }
    }

    private Long requireTenant() {
        Long tid = TenantContext.getTenantId();
        if (tid == null) throw new BizException(40001, "缺少租户");
        return tid;
    }

    private String str(Object o) {
        return o == null ? null : String.valueOf(o).trim();
    }

    private Map<String, Object> camel(Map<String, Object> row) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : row.entrySet()) {
            String k = e.getKey();
            if (k == null) continue;
            StringBuilder sb = new StringBuilder();
            boolean up = false;
            for (char c : k.toCharArray()) {
                if (c == '_') {
                    up = true;
                    continue;
                }
                sb.append(up ? Character.toUpperCase(c) : c);
                up = false;
            }
            Object v = e.getValue();
            if (v instanceof java.sql.Timestamp ts) {
                v = ts.toLocalDateTime().toString().replace('T', ' ');
            }
            m.put(sb.toString(), v);
        }
        return m;
    }
}
