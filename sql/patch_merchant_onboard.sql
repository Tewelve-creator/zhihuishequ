-- 商圈入驻 + 交易佣金应收（独立于 SaaS 套餐订购账务）
USE smart_community;
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS store_merchant_apply (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id BIGINT NOT NULL,
  merchant_name VARCHAR(128) NOT NULL,
  shop_type VARCHAR(64) NULL,
  contact_name VARCHAR(64) NULL,
  contact_phone VARCHAR(32) NULL,
  address VARCHAR(255) NULL,
  business_license VARCHAR(128) NULL,
  license_url VARCHAR(512) NULL,
  intro VARCHAR(500) NULL,
  commission_rate DECIMAL(5,2) NOT NULL DEFAULT 5.00,
  status VARCHAR(32) NOT NULL DEFAULT 'PENDING_PROPERTY',
  reject_reason VARCHAR(255) NULL,
  merchant_id BIGINT NULL,
  applicant_id BIGINT NULL,
  applicant_name VARCHAR(64) NULL,
  property_auditor_id BIGINT NULL,
  property_auditor_name VARCHAR(64) NULL,
  property_audit_time DATETIME NULL,
  property_audit_remark VARCHAR(255) NULL,
  platform_auditor_id BIGINT NULL,
  platform_auditor_name VARCHAR(64) NULL,
  platform_audit_time DATETIME NULL,
  platform_audit_remark VARCHAR(255) NULL,
  create_time DATETIME NULL,
  update_time DATETIME NULL,
  KEY idx_sma_tenant_status (tenant_id, status),
  KEY idx_sma_status (status, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS store_merchant_audit_log (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id BIGINT NOT NULL,
  apply_id BIGINT NOT NULL,
  stage VARCHAR(32) NOT NULL COMMENT 'PROPERTY/PLATFORM',
  action VARCHAR(32) NOT NULL COMMENT 'APPROVE/REJECT/SUBMIT',
  remark VARCHAR(255) NULL,
  operator_id BIGINT NULL,
  operator_name VARCHAR(64) NULL,
  create_time DATETIME NULL,
  KEY idx_smal_apply (apply_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS merchant_commission_bill (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id BIGINT NOT NULL,
  merchant_id BIGINT NOT NULL,
  order_id BIGINT NULL,
  order_no VARCHAR(64) NULL,
  gmv DECIMAL(14,2) NOT NULL DEFAULT 0,
  commission_rate DECIMAL(5,2) NOT NULL DEFAULT 5.00,
  commission_amount DECIMAL(14,2) NOT NULL DEFAULT 0,
  settle_status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
  settle_time DATETIME NULL,
  settle_batch_no VARCHAR(64) NULL,
  remark VARCHAR(255) NULL,
  create_time DATETIME NULL,
  UNIQUE KEY uk_mcb_order (order_id),
  KEY idx_mcb_merchant (merchant_id, settle_status),
  KEY idx_mcb_tenant (tenant_id, settle_status, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
