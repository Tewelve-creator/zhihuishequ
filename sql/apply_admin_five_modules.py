# -*- coding: utf-8 -*-
import pymysql

conn = pymysql.connect(host="127.0.0.1", user="root", password="root", database="smart_community", charset="utf8mb4")
cur = conn.cursor()

def col_exists(table, col):
    cur.execute(
        "SELECT COUNT(1) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=%s AND TABLE_NAME=%s AND COLUMN_NAME=%s",
        ("smart_community", table, col),
    )
    return cur.fetchone()[0] > 0

def add_col(table, col, ddl):
    if not col_exists(table, col):
        cur.execute(f"ALTER TABLE {table} ADD COLUMN {ddl}")
        print("added", table, col)

add_col("community_activity", "cover_url", "cover_url VARCHAR(512) NULL COMMENT '封面图' AFTER title")
add_col("community_activity", "category", "category VARCHAR(64) NULL COMMENT '邻里文体/公益志愿/物业通知/亲子活动' AFTER title")
add_col("community_activity", "allow_cancel", "allow_cancel TINYINT DEFAULT 1 COMMENT '1允许取消报名 0不允许' AFTER max_people")
add_col("lost_found", "item_type", "item_type VARCHAR(16) DEFAULT 'found' COMMENT 'lost/found' AFTER title")
add_col("lost_found", "house_id", "house_id BIGINT NULL AFTER user_id")
add_col("lost_found", "house_label", "house_label VARCHAR(128) NULL AFTER house_id")
add_col("lost_found", "contact_name", "contact_name VARCHAR(64) NULL AFTER contact")

cur.execute("""
CREATE TABLE IF NOT EXISTS ai_kb_document (
  id            BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id     BIGINT NOT NULL,
  title         VARCHAR(128) NOT NULL,
  file_name     VARCHAR(255) NOT NULL,
  content       MEDIUMTEXT NOT NULL,
  char_count    INT DEFAULT 0,
  index_status  VARCHAR(32) DEFAULT 'NONE',
  chunk_count   INT DEFAULT 0,
  uploader_id   BIGINT NULL,
  create_time   DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time   DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
""")
cur.execute("""
CREATE TABLE IF NOT EXISTS ai_kb_chunk (
  id          BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id   BIGINT NOT NULL,
  doc_id      BIGINT NOT NULL,
  chunk_no    INT NOT NULL,
  content     TEXT NOT NULL,
  token_est   INT DEFAULT 0,
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  KEY idx_doc (doc_id),
  KEY idx_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
""")
cur.execute("""
CREATE TABLE IF NOT EXISTS ai_kb_index_log (
  id          BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id   BIGINT NOT NULL,
  doc_id      BIGINT NOT NULL,
  step        VARCHAR(64) NOT NULL,
  message     VARCHAR(500) NULL,
  success     TINYINT DEFAULT 1,
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  KEY idx_doc (doc_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
""")
conn.commit()
print("ok")
conn.close()
