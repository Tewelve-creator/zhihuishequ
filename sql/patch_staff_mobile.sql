USE smart_community;

CREATE TABLE IF NOT EXISTS property_fee_bill (
  id          BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id   BIGINT NOT NULL,
  user_id     BIGINT NULL,
  house_no    VARCHAR(64) NULL,
  title       VARCHAR(128) NOT NULL,
  amount      DECIMAL(10,2) NOT NULL DEFAULT 0,
  period      VARCHAR(32) NULL,
  status      VARCHAR(16) NOT NULL DEFAULT 'unpaid',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  KEY idx_tenant (tenant_id),
  KEY idx_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS ai_call_log (
  id          BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id   BIGINT NOT NULL,
  user_id     BIGINT NULL,
  scene       VARCHAR(32) NOT NULL,
  success     TINYINT DEFAULT 1,
  cost_ms     INT DEFAULT 0,
  remark      VARCHAR(512) NULL,
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  KEY idx_tenant_scene (tenant_id, scene)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO property_fee_bill (tenant_id, user_id, house_no, title, amount, period, status)
SELECT 1, NULL, 'ALL', '2026-07 property fee', 12800.00, '2026-07', 'unpaid'
FROM DUAL
WHERE NOT EXISTS (
  SELECT 1 FROM property_fee_bill WHERE tenant_id=1 AND period='2026-07' AND house_no='ALL'
);
