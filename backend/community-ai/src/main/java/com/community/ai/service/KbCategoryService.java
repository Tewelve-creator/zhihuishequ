package com.community.ai.service;

import com.community.common.constant.RoleConstants;
import com.community.common.exception.BizException;
import com.community.common.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 知识库分类目录维护
 */
@Service
@RequiredArgsConstructor
public class KbCategoryService {

    private final JdbcTemplate jdbc;

    public List<Map<String, Object>> list(Boolean enabledOnly) {
        assertStaff();
        ensureTable();
        seedDefaults();
        Long tid = requireTenant();
        String sql = "select id,tenant_id,code,name,description,sort_no,enabled,create_time,update_time "
                + "from knowledge_category where tenant_id=? ";
        if (Boolean.TRUE.equals(enabledOnly)) {
            sql += "and enabled=1 ";
        }
        sql += "order by sort_no asc, id asc";
        List<Map<String, Object>> rows = jdbc.queryForList(sql, tid);
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            out.add(camel(r));
        }
        return out;
    }

    @Transactional
    public Map<String, Object> save(Map<String, Object> body) {
        assertAdmin();
        ensureTable();
        Long tid = requireTenant();
        Long id = parseLong(body.get("id"));
        String code = str(body.get("code"));
        String name = str(body.get("name"));
        if (!StringUtils.hasText(code) || !StringUtils.hasText(name)) {
            throw new BizException("请填写分类编码与名称");
        }
        code = code.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_\\-]", "");
        if (code.isEmpty()) throw new BizException("分类编码无效");
        String desc = str(body.get("description"));
        int sortNo = body.get("sortNo") == null ? 0 : Integer.parseInt(String.valueOf(body.get("sortNo")));
        int enabled = body.get("enabled") == null || Boolean.TRUE.equals(body.get("enabled"))
                || "1".equals(String.valueOf(body.get("enabled"))) ? 1 : 0;

        if (id == null) {
            Integer exists = jdbc.queryForObject(
                    "select count(1) from knowledge_category where tenant_id=? and code=?",
                    Integer.class, tid, code);
            if (exists != null && exists > 0) throw new BizException("分类编码已存在");
            jdbc.update(
                    "insert into knowledge_category(tenant_id,code,name,description,sort_no,enabled) values(?,?,?,?,?,?)",
                    tid, code, name.trim(), desc, sortNo, enabled);
            id = jdbc.queryForObject("select last_insert_id()", Long.class);
        } else {
            int n = jdbc.update(
                    "update knowledge_category set code=?, name=?, description=?, sort_no=?, enabled=? where id=? and tenant_id=?",
                    code, name.trim(), desc, sortNo, enabled, id, tid);
            if (n == 0) throw new BizException("分类不存在");
        }
        return get(id);
    }

    public Map<String, Object> get(Long id) {
        assertStaff();
        List<Map<String, Object>> rows = jdbc.queryForList(
                "select * from knowledge_category where id=? and tenant_id=?", id, requireTenant());
        if (rows.isEmpty()) throw new BizException("分类不存在");
        return camel(rows.get(0));
    }

    @Transactional
    public void delete(Long id) {
        assertAdmin();
        Long tid = requireTenant();
        List<Map<String, Object>> rows = jdbc.queryForList(
                "select code from knowledge_category where id=? and tenant_id=?", id, tid);
        if (rows.isEmpty()) throw new BizException("分类不存在");
        String code = String.valueOf(rows.get(0).get("code"));
        Integer used = 0;
        try {
            used = jdbc.queryForObject(
                    "select count(1) from knowledge_documents where tenant_id=? and category=?",
                    Integer.class, tid, code);
        } catch (Exception ignored) {
        }
        if (used != null && used > 0) {
            throw new BizException("该分类下仍有 " + used + " 篇文档，请先迁移或删除文档");
        }
        jdbc.update("delete from knowledge_category where id=? and tenant_id=?", id, tid);
    }

    public boolean isValidCode(String code) {
        if (!StringUtils.hasText(code)) return false;
        ensureTable();
        seedDefaults();
        Integer n = jdbc.queryForObject(
                "select count(1) from knowledge_category where tenant_id=? and code=? and enabled=1",
                Integer.class, requireTenant(), code.trim().toLowerCase(Locale.ROOT));
        return n != null && n > 0;
    }

    public void ensureTable() {
        try {
            jdbc.execute(
                    "create table if not exists knowledge_category ("
                            + "id bigint primary key auto_increment,"
                            + "tenant_id bigint not null,"
                            + "code varchar(32) not null,"
                            + "name varchar(64) not null,"
                            + "description varchar(255) null,"
                            + "sort_no int not null default 0,"
                            + "enabled tinyint not null default 1,"
                            + "create_time datetime default current_timestamp,"
                            + "update_time datetime default current_timestamp on update current_timestamp,"
                            + "unique key uk_tenant_code (tenant_id, code)"
                            + ") engine=InnoDB default charset=utf8mb4");
        } catch (Exception ignored) {
        }
    }

    private void seedDefaults() {
        Long tid = TenantContext.getTenantId();
        if (tid == null) return;
        Integer cnt = jdbc.queryForObject(
                "select count(1) from knowledge_category where tenant_id=?", Integer.class, tid);
        if (cnt != null && cnt > 0) return;
        Object[][] seeds = {
                {"policy", "物业制度", "物业管理制度、收费标准等", 10},
                {"community", "小区规则", "小区公约、门禁停车等", 20},
                {"fee", "缴费相关", "物业费、水电燃气等", 30},
                {"repair", "报事报修", "报修流程与常见问题", 40},
                {"notice", "公告通知", "公告类说明与模板", 50},
        };
        for (Object[] s : seeds) {
            jdbc.update(
                    "insert into knowledge_category(tenant_id,code,name,description,sort_no,enabled) values(?,?,?,?,?,1)",
                    tid, s[0], s[1], s[2], s[3]);
        }
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
            throw new BizException(403, "仅物业管理员可维护分类");
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

    private Long parseLong(Object o) {
        if (o == null || !StringUtils.hasText(String.valueOf(o))) return null;
        try {
            return Long.valueOf(String.valueOf(o));
        } catch (Exception e) {
            return null;
        }
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
