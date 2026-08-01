package com.community.auth.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 房屋档案扩展字段：启动时自动补齐，避免未执行 patch SQL 导致 Unknown column
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HouseSchemaMigrator implements ApplicationRunner {

    private final JdbcTemplate jdbc;

    @Override
    public void run(ApplicationArguments args) {
        ensureColumn("community_house", "layout_type",
                "alter table community_house add column layout_type varchar(32) null comment '户型' after room_no");
        ensureColumn("community_house", "area_sqm",
                "alter table community_house add column area_sqm decimal(10,2) null comment '建筑面积㎡' after layout_type");
        ensureColumn("community_house", "occupy_status",
                "alter table community_house add column occupy_status varchar(16) null default 'VACANT' comment 'VACANT/SELF/RENT/OTHER' after area_sqm");
        ensureColumn("household_resident", "lease_start",
                "alter table household_resident add column lease_start date null comment '租赁开始' after identity_type");
        ensureColumn("household_resident", "lease_end",
                "alter table household_resident add column lease_end date null comment '租赁结束' after lease_start");
    }

    private void ensureColumn(String table, String column, String alterSql) {
        try {
            Integer cnt = jdbc.queryForObject(
                    "select count(1) from information_schema.columns "
                            + "where table_schema = database() and table_name = ? and column_name = ?",
                    Integer.class, table, column);
            if (cnt != null && cnt > 0) {
                return;
            }
            jdbc.execute(alterSql);
            log.info("schema migrate: {}.{} added", table, column);
        } catch (Exception e) {
            log.warn("schema migrate {}.{} skipped: {}", table, column, e.getMessage());
        }
    }
}
