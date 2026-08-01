package com.community.ai.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * AI 特色模块表结构 ensure（多小区 tenant_id）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiSchemaService {

    private final JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void ensure() {
        try {
            ensureTables();
            ensureColumns();
        } catch (Exception e) {
            log.warn("ai schema ensure skip: {}", e.getMessage());
        }
    }

    private void ensureTables() {
        jdbcTemplate.execute("""
                create table if not exists ai_user_profile (
                  id bigint primary key auto_increment,
                  tenant_id bigint not null,
                  user_id bigint not null,
                  age_band varchar(32) null,
                  interests varchar(512) null,
                  has_child tinyint not null default 0,
                  living_needs varchar(512) null,
                  building varchar(64) null,
                  update_time datetime null,
                  create_time datetime null,
                  unique key uk_tenant_user (tenant_id, user_id)
                )
                """);
        jdbcTemplate.execute("""
                create table if not exists ai_behavior_event (
                  id bigint primary key auto_increment,
                  tenant_id bigint not null,
                  user_id bigint not null,
                  event_type varchar(64) not null,
                  biz_id varchar(64) null,
                  create_time datetime null,
                  key idx_beh_user (tenant_id, user_id, create_time)
                )
                """);
        jdbcTemplate.execute("""
                create table if not exists ai_image_log (
                  id bigint primary key auto_increment,
                  tenant_id bigint not null,
                  user_id bigint null,
                  image_url varchar(512) null,
                  facility_type varchar(64) null,
                  location_hint varchar(128) null,
                  damage_level varchar(32) null,
                  confidence double null,
                  provider varchar(32) null,
                  need_manual tinyint not null default 0,
                  order_id bigint null,
                  create_time datetime null,
                  key idx_img_tenant (tenant_id, create_time)
                )
                """);
        jdbcTemplate.execute("""
                create table if not exists ai_emotion_case (
                  id bigint primary key auto_increment,
                  tenant_id bigint not null,
                  user_id bigint not null,
                  content text not null,
                  sentiment varchar(32) null,
                  emotion_tags varchar(255) null,
                  score double null,
                  advice varchar(1000) null,
                  status varchar(32) not null default 'pending',
                  grid_id bigint null,
                  handle_remark varchar(500) null,
                  mediate_record text null comment '调解过程记录',
                  handle_result varchar(1000) null comment '结案处置结果',
                  create_time datetime null,
                  update_time datetime null,
                  key idx_emo_tenant (tenant_id, status, create_time)
                )
                """);
        jdbcTemplate.execute("""
                create table if not exists ai_sensitive_word (
                  id bigint primary key auto_increment,
                  tenant_id bigint not null,
                  word varchar(64) not null,
                  create_time datetime null,
                  unique key uk_tenant_word (tenant_id, word)
                )
                """);
        jdbcTemplate.execute("""
                create table if not exists ai_notice_push_log (
                  id bigint primary key auto_increment,
                  tenant_id bigint not null,
                  notice_id bigint not null,
                  user_id bigint not null,
                  reason varchar(255) null,
                  create_time datetime null,
                  key idx_push_notice (tenant_id, notice_id)
                )
                """);
        jdbcTemplate.execute("""
                create table if not exists ai_schedule_remind (
                  id bigint primary key auto_increment,
                  tenant_id bigint not null,
                  user_id bigint not null,
                  title varchar(128) not null,
                  content varchar(1000) null,
                  remind_time datetime null,
                  status varchar(32) null,
                  create_time datetime null,
                  sent_time datetime null
                )
                """);
        // 各类日程提前提醒小时数（物业可配置）
        jdbcTemplate.execute("""
                create table if not exists ai_schedule_settings (
                  tenant_id bigint primary key,
                  ahead_fee_hours int not null default 24,
                  ahead_activity_hours int not null default 6,
                  ahead_maint_hours int not null default 24,
                  ahead_convenience_hours int not null default 12,
                  ahead_notice_hours int not null default 2,
                  update_time datetime null
                )
                """);
    }

    private void ensureColumns() {
        addCol("ai_schedule_remind", "source_type", "varchar(32) null");
        addCol("ai_schedule_remind", "source_id", "bigint null");
        addCol("ai_schedule_remind", "start_time", "datetime null");
        addCol("ai_schedule_remind", "end_time", "datetime null");
        addCol("ai_schedule_remind", "jump_path", "varchar(255) null");
        addCol("ai_schedule_remind", "conflict_flag", "tinyint not null default 0");
        addCol("notice_info", "target_rule", "varchar(512) null");
        addCol("notice_info", "ai_audit_pass", "tinyint null");
        addCol("notice_info", "push_count", "int not null default 0");
        addCol("repair_order", "order_type", "varchar(16) null default 'repair'");
        addCol("repair_order", "urgency", "varchar(16) null");
        addCol("repair_order", "category_id", "bigint null");
        addCol("repair_order", "category_name", "varchar(64) null");
        addCol("repair_order", "emotion_case_id", "bigint null");
        addCol("repair_order", "emotion_tags", "varchar(255) null");
        addCol("repair_order", "sentiment", "varchar(32) null");
        addCol("ai_emotion_case", "mediate_record", "text null");
        addCol("ai_emotion_case", "handle_result", "varchar(1000) null");
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
            log.debug("add col {}.{} skip: {}", table, col, e.getMessage());
        }
    }
}
