-- ============================================================
-- SaaS 运营后台：系统配置 / 操作日志 / 订单退款字段
-- 可重复执行（CREATE IF NOT EXISTS + 幂等列补丁见 apply_saas_ops.py）
-- ============================================================
USE smart_community;

CREATE TABLE IF NOT EXISTS sys_config (
  id           BIGINT PRIMARY KEY AUTO_INCREMENT,
  config_key   VARCHAR(128) NOT NULL,
  config_value VARCHAR(2000) NULL,
  remark       VARCHAR(255) NULL,
  update_time  DATETIME NULL,
  UNIQUE KEY uk_config_key (config_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='平台运营配置';

CREATE TABLE IF NOT EXISTS sys_oper_log (
  id            BIGINT PRIMARY KEY AUTO_INCREMENT,
  operator_id   BIGINT NULL,
  operator_name VARCHAR(64) NULL,
  module        VARCHAR(64) NOT NULL,
  action        VARCHAR(64) NOT NULL,
  detail        VARCHAR(1000) NULL,
  ip            VARCHAR(64) NULL,
  create_time   DATETIME NULL,
  KEY idx_time (create_time),
  KEY idx_module (module)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='平台运营操作审计';

-- MySQL 5.7 无 ADD COLUMN IF NOT EXISTS，列补丁请执行 sql/apply_saas_ops.py
-- ALTER TABLE saas_order ADD COLUMN refund_reason VARCHAR(255) NULL COMMENT '退款原因' AFTER end_time;
-- ALTER TABLE saas_order ADD COLUMN refund_time DATETIME NULL COMMENT '退款时间' AFTER refund_reason;
