package com.community.ai.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 失物招领：分类表、认领表、扩展字段（按小区隔离）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LostSchemaService {

    private final JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void ensure() {
        try {
            jdbcTemplate.execute("""
                    create table if not exists lost_category (
                      id bigint primary key auto_increment,
                      tenant_id bigint not null,
                      name varchar(64) not null,
                      sort_no int not null default 0,
                      status tinyint not null default 1,
                      remark varchar(255) null,
                      create_time datetime null,
                      unique key uk_lost_cat (tenant_id, name)
                    )
                    """);
            jdbcTemplate.execute("""
                    create table if not exists lost_claim (
                      id bigint primary key auto_increment,
                      tenant_id bigint not null,
                      lost_id bigint not null,
                      user_id bigint not null,
                      user_name varchar(64) null,
                      phone varchar(32) null,
                      remark varchar(512) null,
                      status varchar(32) not null default 'pending',
                      audit_remark varchar(255) null,
                      auditor_id bigint null,
                      audit_time datetime null,
                      create_time datetime null,
                      key idx_claim_tenant (tenant_id, status, create_time),
                      key idx_claim_lost (lost_id, status)
                    )
                    """);
            addCol("lost_found", "category", "varchar(64) null");
            addCol("lost_found", "ai_audit_pass", "tinyint null");
            addCol("lost_found", "claim_count", "int not null default 0");
            seedCategories();
        } catch (Exception e) {
            log.warn("lost schema ensure skip: {}", e.getMessage());
        }
    }

    private void addCol(String table, String col, String def) {
        try {
            Integer n = jdbcTemplate.queryForObject(
                    "select count(1) from information_schema.columns where table_schema=database() and table_name=? and column_name=?",
                    Integer.class, table, col);
            if (n == null || n == 0) {
                jdbcTemplate.execute("alter table " + table + " add column " + col + " " + def);
            }
        } catch (Exception e) {
            log.debug("add {}.{} skip: {}", table, col, e.getMessage());
        }
    }

    private void seedCategories() {
        try {
            Integer n = jdbcTemplate.queryForObject("select count(1) from lost_category", Integer.class);
            if (n != null && n > 0) return;
            String[] names = {"钥匙卡证", "数码电子", "衣物饰品", "生活用品", "证件票据", "其他"};
            int i = 1;
            for (String name : names) {
                jdbcTemplate.update(
                        "insert into lost_category(tenant_id,name,sort_no,status,create_time) values(1,?,?,1,now())",
                        name, i++);
            }
        } catch (Exception e) {
            log.debug("seed lost category skip: {}", e.getMessage());
        }
    }
}
