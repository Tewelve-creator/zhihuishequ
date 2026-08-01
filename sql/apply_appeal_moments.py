# -*- coding: utf-8 -*-
"""邻里倾诉朋友圈改造：为 emotion_appeal 补列（兼容 MySQL 5.7）并建附属表。"""
import os
import pymysql

HOST = os.getenv("DB_HOST", "127.0.0.1")
PORT = int(os.getenv("DB_PORT", "3306"))
USER = os.getenv("DB_USER", "root")
PASSWORD = os.getenv("DB_PASSWORD", "root")
DB = os.getenv("DB_NAME", "community")


def col_exists(cur, table, col):
    cur.execute(
        "SELECT COUNT(*) FROM information_schema.COLUMNS "
        "WHERE TABLE_SCHEMA=%s AND TABLE_NAME=%s AND COLUMN_NAME=%s",
        (DB, table, col),
    )
    return cur.fetchone()[0] > 0


def add_col(cur, table, col, ddl):
    if col_exists(cur, table, col):
        print(f"skip {table}.{col}")
        return
    cur.execute(f"ALTER TABLE {table} ADD COLUMN {ddl}")
    print(f"add {table}.{col}")


def main():
    conn = pymysql.connect(host=HOST, port=PORT, user=USER, password=PASSWORD, database=DB, charset="utf8mb4")
    cur = conn.cursor()
    add_col(cur, "emotion_appeal", "house_id", "house_id BIGINT NULL COMMENT '发布房屋' AFTER user_id")
    add_col(cur, "emotion_appeal", "house_label", "house_label VARCHAR(128) NULL COMMENT '楼栋房号' AFTER house_id")
    add_col(cur, "emotion_appeal", "nickname", "nickname VARCHAR(64) NULL COMMENT '昵称' AFTER house_label")
    add_col(cur, "emotion_appeal", "image_urls", "image_urls VARCHAR(2000) NULL COMMENT '图片JSON' AFTER content")
    add_col(cur, "emotion_appeal", "like_count", "like_count INT NOT NULL DEFAULT 0 AFTER image_urls")
    add_col(cur, "emotion_appeal", "comment_count", "comment_count INT NOT NULL DEFAULT 0 AFTER like_count")
    add_col(cur, "emotion_appeal", "deleted", "deleted TINYINT NOT NULL DEFAULT 0 COMMENT '0正常1删除' AFTER comment_count")
    add_col(cur, "emotion_appeal", "delete_reason", "delete_reason VARCHAR(255) NULL AFTER deleted")
    add_col(cur, "emotion_appeal", "deleted_by", "deleted_by BIGINT NULL AFTER delete_reason")
    add_col(cur, "emotion_appeal", "deleted_time", "deleted_time DATETIME NULL AFTER deleted_by")

    cur.execute(
        """
        CREATE TABLE IF NOT EXISTS appeal_comment (
          id BIGINT PRIMARY KEY AUTO_INCREMENT,
          tenant_id BIGINT NOT NULL,
          appeal_id BIGINT NOT NULL,
          user_id BIGINT NOT NULL,
          nickname VARCHAR(64) NULL,
          house_label VARCHAR(128) NULL,
          content VARCHAR(500) NOT NULL,
          deleted TINYINT NOT NULL DEFAULT 0,
          deleted_by BIGINT NULL,
          deleted_time DATETIME NULL,
          create_time DATETIME,
          KEY idx_appeal (appeal_id),
          KEY idx_tenant (tenant_id)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
        """
    )
    cur.execute(
        """
        CREATE TABLE IF NOT EXISTS appeal_like (
          id BIGINT PRIMARY KEY AUTO_INCREMENT,
          tenant_id BIGINT NOT NULL,
          appeal_id BIGINT NOT NULL,
          user_id BIGINT NOT NULL,
          create_time DATETIME,
          UNIQUE KEY uk_appeal_user (appeal_id, user_id),
          KEY idx_tenant (tenant_id)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
        """
    )
    cur.execute(
        """
        CREATE TABLE IF NOT EXISTS appeal_user_mute (
          id BIGINT PRIMARY KEY AUTO_INCREMENT,
          tenant_id BIGINT NOT NULL,
          user_id BIGINT NOT NULL,
          mute_until DATETIME NULL,
          reason VARCHAR(255) NULL,
          operator_id BIGINT NULL,
          operator_name VARCHAR(64) NULL,
          status VARCHAR(16) NOT NULL DEFAULT 'active',
          create_time DATETIME,
          update_time DATETIME,
          KEY idx_tenant_user (tenant_id, user_id)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
        """
    )
    cur.execute(
        """
        CREATE TABLE IF NOT EXISTS appeal_op_log (
          id BIGINT PRIMARY KEY AUTO_INCREMENT,
          tenant_id BIGINT NOT NULL,
          operator_id BIGINT NULL,
          operator_name VARCHAR(64) NULL,
          action VARCHAR(32) NOT NULL,
          target_user_id BIGINT NULL,
          target_appeal_id BIGINT NULL,
          target_comment_id BIGINT NULL,
          detail VARCHAR(500) NULL,
          create_time DATETIME,
          KEY idx_tenant (tenant_id)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
        """
    )
    conn.commit()
    cur.close()
    conn.close()
    print("appeal moments patch done")


if __name__ == "__main__":
    main()
