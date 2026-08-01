-- 设备维保台账（区别于居民报事报修）
USE smart_community;
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS equip_device (
  id              BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id       BIGINT NOT NULL,
  name            VARCHAR(120) NOT NULL COMMENT '设备名称',
  location        VARCHAR(255) NULL COMMENT '设备位置',
  device_type     VARCHAR(64) NOT NULL COMMENT '电梯/消防水泵/监控等',
  cycle_type      VARCHAR(16) NOT NULL DEFAULT 'QUARTER' COMMENT 'MONTH/QUARTER/HALF_YEAR/YEAR',
  vendor_name     VARCHAR(120) NULL COMMENT '维保服务商',
  contact_name    VARCHAR(64) NULL,
  contact_phone   VARCHAR(20) NULL,
  last_maint_date DATE NULL COMMENT '上次维保日期',
  next_maint_date DATE NULL COMMENT '下次维保日期',
  status          VARCHAR(16) NOT NULL DEFAULT 'NORMAL' COMMENT 'NORMAL/DUE/OVERDUE',
  remark          VARCHAR(500) NULL,
  create_by       BIGINT NULL,
  create_time     DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time     DATETIME NULL,
  KEY idx_tenant_status (tenant_id, status),
  KEY idx_next (tenant_id, next_maint_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='设备基础信息（计划性维保）';

CREATE TABLE IF NOT EXISTS equip_maint_record (
  id              BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id       BIGINT NOT NULL,
  device_id       BIGINT NOT NULL,
  device_name     VARCHAR(120) NULL,
  maint_date      DATE NOT NULL,
  content         VARCHAR(1000) NOT NULL COMMENT '维保内容',
  photo_urls      VARCHAR(2000) NULL,
  attachment_urls VARCHAR(2000) NULL COMMENT '报告附件',
  worker_name     VARCHAR(64) NULL,
  create_by       BIGINT NULL,
  create_by_name  VARCHAR(64) NULL,
  remark          VARCHAR(500) NULL,
  advance_next    TINYINT NOT NULL DEFAULT 1 COMMENT '是否已顺延下次维保',
  create_time     DATETIME DEFAULT CURRENT_TIMESTAMP,
  KEY idx_device (tenant_id, device_id),
  KEY idx_date (tenant_id, maint_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='设备维保历史记录';

INSERT INTO equip_device (tenant_id, name, location, device_type, cycle_type, vendor_name, contact_name, contact_phone,
  last_maint_date, next_maint_date, status, remark, create_time)
SELECT 1, '1号电梯', '1栋电梯机房', '电梯', 'QUARTER', '通力电梯', '王工', '13900001111',
  DATE_SUB(CURDATE(), INTERVAL 95 DAY), DATE_SUB(CURDATE(), INTERVAL 5 DAY),
  'OVERDUE', '演示：已逾期', NOW()
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM equip_device WHERE tenant_id=1 AND name='1号电梯');

INSERT INTO equip_device (tenant_id, name, location, device_type, cycle_type, vendor_name, contact_name, contact_phone,
  last_maint_date, next_maint_date, status, remark, create_time)
SELECT 1, '消防水泵', '地下泵房', '消防水泵', 'HALF_YEAR', '消防维保公司', '李工', '13900002222',
  DATE_SUB(CURDATE(), INTERVAL 175 DAY), DATE_ADD(CURDATE(), INTERVAL 3 DAY),
  'DUE', '演示：临近维保', NOW()
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM equip_device WHERE tenant_id=1 AND name='消防水泵');

INSERT INTO equip_device (tenant_id, name, location, device_type, cycle_type, vendor_name, contact_name, contact_phone,
  last_maint_date, next_maint_date, status, remark, create_time)
SELECT 1, '监控系统', '监控中心', '监控', 'YEAR', '海康服务商', '赵工', '13900003333',
  DATE_SUB(CURDATE(), INTERVAL 200 DAY), DATE_ADD(CURDATE(), INTERVAL 165 DAY),
  'NORMAL', '演示：正常', NOW()
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM equip_device WHERE tenant_id=1 AND name='监控系统');

-- 刷新状态：逾期 / 待维保（7天内）/ 正常
UPDATE equip_device SET status='OVERDUE', update_time=NOW()
WHERE tenant_id=1 AND next_maint_date IS NOT NULL AND next_maint_date < CURDATE();
UPDATE equip_device SET status='DUE', update_time=NOW()
WHERE tenant_id=1 AND next_maint_date IS NOT NULL
  AND next_maint_date >= CURDATE() AND next_maint_date <= DATE_ADD(CURDATE(), INTERVAL 7 DAY);
UPDATE equip_device SET status='NORMAL', update_time=NOW()
WHERE tenant_id=1 AND (next_maint_date IS NULL OR next_maint_date > DATE_ADD(CURDATE(), INTERVAL 7 DAY));
