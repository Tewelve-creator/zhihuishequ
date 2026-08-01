-- ============================================================
-- 修复运营端空数据：对齐 saas_order / sys_package 表结构 + 演示订单
-- 可重复执行（缺列才加）
-- ============================================================
USE smart_community;

-- ---------- sys_package 补列 ----------
SET @db := DATABASE();

SET @sql := (
  SELECT IF(COUNT(*)=0,
    'ALTER TABLE sys_package ADD COLUMN sort_no INT DEFAULT 0 AFTER status',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=@db AND TABLE_NAME='sys_package' AND COLUMN_NAME='sort_no'
);
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @sql := (
  SELECT IF(COUNT(*)=0,
    'ALTER TABLE sys_package ADD COLUMN deleted TINYINT NOT NULL DEFAULT 0 AFTER remark',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=@db AND TABLE_NAME='sys_package' AND COLUMN_NAME='deleted'
);
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @sql := (
  SELECT IF(COUNT(*)=0,
    'ALTER TABLE sys_package ADD COLUMN create_time DATETIME NULL AFTER deleted',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=@db AND TABLE_NAME='sys_package' AND COLUMN_NAME='create_time'
);
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @sql := (
  SELECT IF(COUNT(*)=0,
    'ALTER TABLE sys_package ADD COLUMN update_time DATETIME NULL AFTER create_time',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=@db AND TABLE_NAME='sys_package' AND COLUMN_NAME='update_time'
);
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

UPDATE sys_package SET sort_no = id WHERE sort_no IS NULL OR sort_no = 0;
UPDATE sys_package SET deleted = 0 WHERE deleted IS NULL;
UPDATE sys_package SET create_time = NOW() WHERE create_time IS NULL;

CREATE TABLE IF NOT EXISTS sys_package_feature (
  id           BIGINT PRIMARY KEY AUTO_INCREMENT,
  package_id   BIGINT NOT NULL,
  feature_code VARCHAR(64) NOT NULL,
  feature_name VARCHAR(64) DEFAULT NULL,
  UNIQUE KEY uk_pkg_feat (package_id, feature_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 从 sys_package.features CSV 回填 feature 表（仅空时）
INSERT IGNORE INTO sys_package_feature(package_id, feature_code, feature_name)
SELECT p.id, TRIM(SUBSTRING_INDEX(SUBSTRING_INDEX(CONCAT(p.features, ','), ',', n.n), ',', -1)) AS code, NULL
FROM sys_package p
JOIN (
  SELECT 1 n UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5
  UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9 UNION ALL SELECT 10
) n ON n.n <= 1 + LENGTH(p.features) - LENGTH(REPLACE(p.features, ',', ''))
WHERE p.features IS NOT NULL AND TRIM(p.features) <> ''
  AND TRIM(SUBSTRING_INDEX(SUBSTRING_INDEX(CONCAT(p.features, ','), ',', n.n), ',', -1)) <> ''
  AND NOT EXISTS (SELECT 1 FROM sys_package_feature f WHERE f.package_id=p.id LIMIT 1);

-- ---------- saas_order 补列（旧表是 pay_status） ----------
SET @sql := (
  SELECT IF(COUNT(*)=0,
    'ALTER TABLE saas_order ADD COLUMN order_no VARCHAR(64) NULL AFTER id',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=@db AND TABLE_NAME='saas_order' AND COLUMN_NAME='order_no'
);
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @sql := (
  SELECT IF(COUNT(*)=0,
    'ALTER TABLE saas_order ADD COLUMN order_type VARCHAR(16) NOT NULL DEFAULT ''new'' AFTER amount',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=@db AND TABLE_NAME='saas_order' AND COLUMN_NAME='order_type'
);
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @sql := (
  SELECT IF(COUNT(*)=0,
    'ALTER TABLE saas_order ADD COLUMN status VARCHAR(16) NULL AFTER order_type',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=@db AND TABLE_NAME='saas_order' AND COLUMN_NAME='status'
);
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @sql := (
  SELECT IF(COUNT(*)=0,
    'ALTER TABLE saas_order ADD COLUMN create_by BIGINT NULL AFTER end_time',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=@db AND TABLE_NAME='saas_order' AND COLUMN_NAME='create_by'
);
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @sql := (
  SELECT IF(COUNT(*)=0,
    'ALTER TABLE saas_order ADD COLUMN refund_reason VARCHAR(255) NULL',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=@db AND TABLE_NAME='saas_order' AND COLUMN_NAME='refund_reason'
);
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @sql := (
  SELECT IF(COUNT(*)=0,
    'ALTER TABLE saas_order ADD COLUMN refund_time DATETIME NULL',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=@db AND TABLE_NAME='saas_order' AND COLUMN_NAME='refund_time'
);
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- 旧 pay_status → status
UPDATE saas_order
SET status = CASE
  WHEN pay_status IN ('paid','success','1') THEN 'paid'
  WHEN pay_status IN ('refunded','refund') THEN 'refunded'
  WHEN pay_status IN ('cancelled','canceled') THEN 'cancelled'
  WHEN pay_status IS NULL OR pay_status = '' THEN COALESCE(status, 'pending')
  ELSE pay_status
END
WHERE status IS NULL OR status = '';

UPDATE saas_order SET order_no = CONCAT('SO', id) WHERE order_no IS NULL OR order_no = '';
UPDATE saas_order SET order_type = 'new' WHERE order_type IS NULL OR order_type = '';
UPDATE saas_order SET status = 'pending' WHERE status IS NULL OR status = '';

-- ---------- 演示订单（无已付单时灌入，驾驶舱才有图） ----------
INSERT INTO saas_order(order_no, tenant_id, package_id, package_name, cycle, amount, order_type, status, pay_time, start_time, end_time, create_time)
SELECT * FROM (
  SELECT 'SODEMO1001', 1, 3, '专业版', 'year', 2999.00, 'new', 'paid', DATE_SUB(NOW(), INTERVAL 5 MONTH), DATE_SUB(NOW(), INTERVAL 5 MONTH), DATE_ADD(NOW(), INTERVAL 7 MONTH), DATE_SUB(NOW(), INTERVAL 5 MONTH)
  UNION ALL SELECT 'SODEMO1002', 1, 3, '专业版', 'year', 2999.00, 'renew', 'paid', DATE_SUB(NOW(), INTERVAL 4 MONTH), DATE_SUB(NOW(), INTERVAL 4 MONTH), DATE_ADD(NOW(), INTERVAL 8 MONTH), DATE_SUB(NOW(), INTERVAL 4 MONTH)
  UNION ALL SELECT 'SODEMO1003', 2, 2, '基础版', 'year', 999.00, 'new', 'paid', DATE_SUB(NOW(), INTERVAL 3 MONTH), DATE_SUB(NOW(), INTERVAL 3 MONTH), DATE_ADD(NOW(), INTERVAL 9 MONTH), DATE_SUB(NOW(), INTERVAL 3 MONTH)
  UNION ALL SELECT 'SODEMO1004', 2, 2, '基础版', 'quarter', 279.00, 'renew', 'paid', DATE_SUB(NOW(), INTERVAL 2 MONTH), DATE_SUB(NOW(), INTERVAL 2 MONTH), DATE_ADD(NOW(), INTERVAL 1 MONTH), DATE_SUB(NOW(), INTERVAL 2 MONTH)
  UNION ALL SELECT 'SODEMO1005', 1, 3, '专业版', 'year', 2999.00, 'upgrade', 'paid', DATE_SUB(NOW(), INTERVAL 1 MONTH), DATE_SUB(NOW(), INTERVAL 1 MONTH), DATE_ADD(NOW(), INTERVAL 11 MONTH), DATE_SUB(NOW(), INTERVAL 1 MONTH)
  UNION ALL SELECT 'SODEMO1006', 2, 2, '基础版', 'month', 99.00, 'renew', 'paid', DATE_SUB(NOW(), INTERVAL 10 DAY), DATE_SUB(NOW(), INTERVAL 10 DAY), DATE_ADD(NOW(), INTERVAL 20 DAY), DATE_SUB(NOW(), INTERVAL 10 DAY)
  UNION ALL SELECT 'SODEMO1007', 1, 3, '专业版', 'year', 2999.00, 'renew', 'refunded', DATE_SUB(NOW(), INTERVAL 20 DAY), NULL, NULL, DATE_SUB(NOW(), INTERVAL 25 DAY)
  UNION ALL SELECT 'SODEMO1008', 2, 2, '基础版', 'year', 999.00, 'new', 'pending', NULL, NULL, NULL, NOW()
) AS t(order_no, tenant_id, package_id, package_name, cycle, amount, order_type, status, pay_time, start_time, end_time, create_time)
WHERE NOT EXISTS (SELECT 1 FROM saas_order WHERE status='paid' LIMIT 1);

-- 退款演示单补退款信息
UPDATE saas_order SET refund_reason='演示退款', refund_time=DATE_SUB(NOW(), INTERVAL 18 DAY)
WHERE order_no='SODEMO1007' AND status='refunded' AND refund_time IS NULL;

-- 确保租户有到期时间，便于预警区有数据
UPDATE sys_tenant SET expire_time = DATE_ADD(NOW(), INTERVAL 12 DAY)
WHERE id=1 AND (expire_time IS NULL OR expire_time < NOW());
UPDATE sys_tenant SET expire_time = DATE_ADD(NOW(), INTERVAL 25 DAY)
WHERE id=2 AND (expire_time IS NULL OR expire_time < NOW());

SELECT 'sys_package' AS t, COUNT(*) AS cnt FROM sys_package
UNION ALL SELECT 'saas_order', COUNT(*) FROM saas_order
UNION ALL SELECT 'saas_order_paid', COUNT(*) FROM saas_order WHERE status='paid'
UNION ALL SELECT 'sys_tenant', COUNT(*) FROM sys_tenant;
