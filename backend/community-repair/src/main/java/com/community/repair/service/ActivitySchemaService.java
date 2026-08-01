package com.community.repair.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ActivitySchemaService {

    private final JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void ensure() {
        try {
            jdbcTemplate.execute("""
                    create table if not exists activity_category (
                      id bigint primary key auto_increment,
                      tenant_id bigint not null,
                      name varchar(64) not null,
                      tags varchar(255) null,
                      sort_no int not null default 0,
                      status tinyint not null default 1,
                      remark varchar(255) null,
                      create_time datetime null,
                      unique key uk_act_cat (tenant_id, name)
                    )
                    """);
            addCol("community_activity", "summary", "varchar(200) null");
            addCol("community_activity", "tags", "varchar(255) null");
            addCol("community_activity", "target_rule", "varchar(255) null");
            addCol("community_activity", "signup_end", "datetime null");
            addCol("community_activity", "ai_audit_pass", "tinyint null");
            addCol("community_activity", "push_count", "int not null default 0");
            seedCategories();
        } catch (Exception e) {
            log.warn("activity schema ensure skip: {}", e.getMessage());
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
            Integer n = jdbcTemplate.queryForObject("select count(1) from activity_category", Integer.class);
            if (n != null && n > 0) return;
            String[][] rows = {
                    {"邻里文体", "文体,邻里"},
                    {"公益志愿", "公益,志愿"},
                    {"物业通知", "物业,公告"},
                    {"亲子活动", "亲子,儿童"},
                    {"老年活动", "老年,养生"},
            };
            int i = 1;
            for (String[] r : rows) {
                jdbcTemplate.update(
                        "insert into activity_category(tenant_id,name,tags,sort_no,status,create_time) values(1,?,?,?,1,now())",
                        r[0], r[1], i++);
            }
        } catch (Exception e) {
            log.debug("seed activity category skip: {}", e.getMessage());
        }
    }
}
