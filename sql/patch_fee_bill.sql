-- 物业费账单 + 支付流水（打通 PC 缴费管理与居民 App）
USE smart_community;

SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS fee_bill (
  id            BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id     BIGINT NOT NULL,
  house_id      BIGINT NOT NULL,
  house_label   VARCHAR(255) NULL,
  user_id       BIGINT NULL COMMENT '关联App用户',
  payer_name    VARCHAR(64) NOT NULL,
  payer_phone   VARCHAR(20) NOT NULL,
  fee_type      VARCHAR(32) NOT NULL COMMENT 'PROPERTY/PARKING/SHARE/OTHER',
  fee_type_name VARCHAR(64) NOT NULL,
  amount        DECIMAL(12,2) NOT NULL,
  period        VARCHAR(32) NOT NULL COMMENT '如 2026-07',
  due_date      DATE NULL,
  status        VARCHAR(16) NOT NULL DEFAULT 'UNPAID' COMMENT 'UNPAID/PAYING/PAID',
  paid_at       DATETIME NULL,
  remark        VARCHAR(500) NULL,
  create_by     BIGINT NULL,
  create_time   DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time   DATETIME NULL,
  KEY idx_tenant_status (tenant_id, status),
  KEY idx_house (tenant_id, house_id),
  KEY idx_period (tenant_id, period)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='物业费账单';

CREATE TABLE IF NOT EXISTS fee_payment (
  id            BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id     BIGINT NOT NULL,
  order_no      VARCHAR(64) NOT NULL,
  user_id       BIGINT NOT NULL,
  house_id      BIGINT NOT NULL,
  house_label   VARCHAR(255) NULL,
  amount        DECIMAL(12,2) NOT NULL,
  pay_channel   VARCHAR(16) NOT NULL COMMENT 'WECHAT/ALIPAY',
  status        VARCHAR(16) NOT NULL DEFAULT 'PAYING' COMMENT 'PAYING/PAID/FAILED/CLOSED',
  pay_trade_no  VARCHAR(64) NULL,
  bill_ids      VARCHAR(512) NOT NULL COMMENT '逗号分隔账单ID',
  bill_snapshot TEXT NULL COMMENT '账单快照JSON',
  paid_at       DATETIME NULL,
  create_time   DATETIME DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_order_no (order_no),
  KEY idx_tenant_user (tenant_id, user_id),
  KEY idx_house (tenant_id, house_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='缴费支付流水（不可删）';

-- 演示：为已绑定住户生成未缴账单
INSERT INTO fee_bill (tenant_id, house_id, house_label, user_id, payer_name, payer_phone,
  fee_type, fee_type_name, amount, period, due_date, status, remark, create_time)
SELECT r.tenant_id, r.house_id, r.house_label, r.user_id, r.name, r.phone,
  'PROPERTY', '物业费', 274.40, '2026-07', '2026-07-25', 'UNPAID', '系统演示账单', NOW()
FROM household_resident r
WHERE r.status = 'ACTIVE' AND r.tenant_id = 1
  AND NOT EXISTS (
    SELECT 1 FROM fee_bill b
    WHERE b.tenant_id = r.tenant_id AND b.house_id = r.house_id
      AND b.period = '2026-07' AND b.fee_type = 'PROPERTY' AND b.payer_phone = r.phone
  );

INSERT INTO fee_bill (tenant_id, house_id, house_label, user_id, payer_name, payer_phone,
  fee_type, fee_type_name, amount, period, due_date, status, remark, create_time)
SELECT r.tenant_id, r.house_id, r.house_label, r.user_id, r.name, r.phone,
  'PARKING', '停车费', 180.00, '2026-07', '2026-07-20', 'UNPAID', '车位月租演示', NOW()
FROM household_resident r
WHERE r.status = 'ACTIVE' AND r.tenant_id = 1
  AND NOT EXISTS (
    SELECT 1 FROM fee_bill b
    WHERE b.tenant_id = r.tenant_id AND b.house_id = r.house_id
      AND b.period = '2026-07' AND b.fee_type = 'PARKING' AND b.payer_phone = r.phone
  );
