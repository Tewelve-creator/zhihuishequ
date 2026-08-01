# -*- coding: utf-8 -*-
"""SaaS 运营后台：sys_config / sys_oper_log 及 saas_order 退款列（兼容 MySQL 5.7）。"""
import os
import pymysql

HOST = os.getenv("DB_HOST", "127.0.0.1")
PORT = int(os.getenv("DB_PORT", "3306"))
USER = os.getenv("DB_USER", "root")
PASSWORD = os.getenv("DB_PASSWORD", "root")
DB = os.getenv("DB_NAME", "smart_community")


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
    conn = pymysql.connect(
        host=HOST, port=PORT, user=USER, password=PASSWORD, database=DB, charset="utf8mb4"
    )
    cur = conn.cursor()

    cur.execute(
        """
        CREATE TABLE IF NOT EXISTS sys_config (
          id BIGINT PRIMARY KEY AUTO_INCREMENT,
          config_key VARCHAR(128) NOT NULL,
          config_value VARCHAR(2000) NULL,
          remark VARCHAR(255) NULL,
          update_time DATETIME NULL,
          UNIQUE KEY uk_config_key (config_key)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
        """
    )
    print("ensure sys_config")

    cur.execute(
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
          KEY idx_time (create_time),
          KEY idx_module (module)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
        """
    )
    print("ensure sys_oper_log")

    add_col(cur, "saas_order", "refund_reason", "refund_reason VARCHAR(255) NULL COMMENT '退款原因' AFTER end_time")
    add_col(cur, "saas_order", "refund_time", "refund_time DATETIME NULL COMMENT '退款时间' AFTER refund_reason")

    conn.commit()
    cur.close()
    conn.close()
    print("saas ops patch done")


if __name__ == "__main__":
    main()
