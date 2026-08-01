-- 商户营销资金账户（独立于 SaaS 租户套餐订购账务）
USE smart_community;
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS store_merchant (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id BIGINT NOT NULL,
  name VARCHAR(128) NOT NULL,
  contact_name VARCHAR(64) NULL,
  contact_phone VARCHAR(32) NULL,
  status TINYINT NOT NULL DEFAULT 1,
  warn_threshold DECIMAL(12,2) NOT NULL DEFAULT 100.00,
  remark VARCHAR(255) NULL,
  create_time DATETIME NULL,
  update_time DATETIME NULL,
  KEY idx_sm_tenant (tenant_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS merchant_wallet (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id BIGINT NOT NULL,
  merchant_id BIGINT NOT NULL,
  balance DECIMAL(14,2) NOT NULL DEFAULT 0,
  total_recharge DECIMAL(14,2) NOT NULL DEFAULT 0,
  total_consume DECIMAL(14,2) NOT NULL DEFAULT 0,
  total_withdraw DECIMAL(14,2) NOT NULL DEFAULT 0,
  pending_withdraw DECIMAL(14,2) NOT NULL DEFAULT 0,
  status TINYINT NOT NULL DEFAULT 1 COMMENT '1正常 0冻结',
  update_time DATETIME NULL,
  create_time DATETIME NULL,
  UNIQUE KEY uk_mw_merchant (merchant_id),
  KEY idx_mw_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS merchant_wallet_ledger (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id BIGINT NOT NULL,
  merchant_id BIGINT NOT NULL,
  biz_type VARCHAR(32) NOT NULL,
  amount DECIMAL(14,2) NOT NULL,
  balance_after DECIMAL(14,2) NOT NULL,
  remark VARCHAR(255) NULL,
  operator_id BIGINT NULL,
  operator_name VARCHAR(64) NULL,
  ref_id BIGINT NULL,
  create_time DATETIME NULL,
  KEY idx_mwl_merchant (merchant_id, create_time),
  KEY idx_mwl_tenant (tenant_id, biz_type, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS merchant_withdraw (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id BIGINT NOT NULL,
  merchant_id BIGINT NOT NULL,
  amount DECIMAL(14,2) NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
  apply_remark VARCHAR(255) NULL,
  applicant_id BIGINT NULL,
  applicant_name VARCHAR(64) NULL,
  audit_remark VARCHAR(255) NULL,
  auditor_id BIGINT NULL,
  auditor_name VARCHAR(64) NULL,
  audit_time DATETIME NULL,
  create_time DATETIME NULL,
  KEY idx_mwd_status (status, create_time),
  KEY idx_mwd_tenant (tenant_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
