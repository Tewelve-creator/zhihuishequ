-- 缴费催缴 / 收款通知推送记录
USE smart_community;

SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS fee_receipt_notice (
  id            BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id     BIGINT NOT NULL,
  title         VARCHAR(128) NOT NULL,
  content       VARCHAR(2000) NULL,
  scope         VARCHAR(255) NULL COMMENT '推送范围描述',
  amount_hint   VARCHAR(128) NULL COMMENT '费用说明',
  target_json   TEXT NULL COMMENT '推送对象JSON',
  push_count    INT NOT NULL DEFAULT 0,
  status        VARCHAR(16) NOT NULL DEFAULT 'SENT' COMMENT 'SENT/DRAFT',
  send_at       DATETIME NULL,
  create_by     BIGINT NULL,
  create_time   DATETIME DEFAULT CURRENT_TIMESTAMP,
  KEY idx_tenant_time (tenant_id, send_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='缴费催缴收款通知';
