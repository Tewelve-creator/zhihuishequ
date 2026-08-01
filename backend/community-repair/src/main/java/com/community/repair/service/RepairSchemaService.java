package com.community.repair.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 报事报修扩展表：分类、分派规则、工单扩展字段
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RepairSchemaService {

    private final JdbcTemplate jdbcTemplate;
    private final RepairWorkOrderService repairWorkOrderService;

    @PostConstruct
    public void ensure() {
        try {
            jdbcTemplate.execute("""
                    create table if not exists repair_category (
                      id bigint primary key auto_increment,
                      tenant_id bigint not null,
                      name varchar(64) not null,
                      order_type varchar(16) not null default 'repair',
                      urgency_default varchar(16) null,
                      sort_no int not null default 0,
                      status tinyint not null default 1,
                      remark varchar(255) null,
                      create_time datetime null,
                      key idx_cat_tenant (tenant_id, order_type, status)
                    )
                    """);
            jdbcTemplate.execute("""
                    create table if not exists repair_assign_rule (
                      id bigint primary key auto_increment,
                      tenant_id bigint not null,
                      order_type varchar(16) null,
                      category_id bigint null,
                      category_name varchar(64) null,
                      urgency varchar(16) null,
                      handler_id bigint not null,
                      handler_name varchar(64) null,
                      priority int not null default 0,
                      enabled tinyint not null default 1,
                      remark varchar(255) null,
                      create_time datetime null,
                      key idx_rule_tenant (tenant_id, enabled, priority)
                    )
                    """);
            addCol("repair_order", "order_type", "varchar(16) null default 'repair'");
            addCol("repair_order", "urgency", "varchar(16) null");
            addCol("repair_order", "category_id", "bigint null");
            addCol("repair_order", "category_name", "varchar(64) null");
            addCol("repair_order", "emotion_case_id", "bigint null");
            addCol("repair_order", "emotion_tags", "varchar(255) null");
            addCol("repair_order", "sentiment", "varchar(32) null");
            addCol("repair_order", "feedback_id", "bigint null");
            addCol("repair_order", "house_id", "bigint null");
            addCol("repair_order", "building", "varchar(64) null");
            addCol("repair_order", "unit", "varchar(64) null");
            addCol("repair_order", "room_no", "varchar(64) null");
            addCol("repair_order", "location_label", "varchar(128) null");
            seedDefaultCategories();
            try {
                int n = repairWorkOrderService.backfillAllLocations();
                if (n > 0) {
                    log.info("repair_order location backfill updated {} rows", n);
                }
            } catch (Exception e) {
                log.warn("repair location backfill skip: {}", e.getMessage());
            }
        } catch (Exception e) {
            log.warn("repair schema ensure skip: {}", e.getMessage());
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

    private void seedDefaultCategories() {
        try {
            Integer n = jdbcTemplate.queryForObject("select count(1) from repair_category", Integer.class);
            if (n != null && n > 0) {
                return;
            }
            // 种子到租户 1（阳光花园），其他租户可自行新增
            String[][] rows = {
                    {"repair", "电梯", "高"},
                    {"repair", "路灯", "中"},
                    {"repair", "楼道门窗", "中"},
                    {"repair", "健身器材", "低"},
                    {"repair", "水管", "高"},
                    {"repair", "门禁/单元门", "中"},
                    {"report", "邻里纠纷", "高"},
                    {"report", "环境投诉", "中"},
                    {"report", "安全隐患", "高"},
                    {"report", "噪音扰民", "中"},
                    {"report", "其他民生诉求", "低"},
            };
            int sort = 1;
            for (String[] r : rows) {
                jdbcTemplate.update(
                        "insert into repair_category(tenant_id,name,order_type,urgency_default,sort_no,status,create_time) values(1,?,?,?,?,1,now())",
                        r[1], r[0], r[2], sort++);
            }
        } catch (Exception e) {
            log.debug("seed categories skip: {}", e.getMessage());
        }
    }
}
