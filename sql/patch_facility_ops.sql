-- 设施运维增强：点位类型/二维码、巡检逾期、维保工单
USE smart_community;
SET NAMES utf8mb4;

-- 点位扩展（服务端 ensure 兜底）
-- ALTER TABLE insp_point ADD COLUMN point_type VARCHAR(32) NULL COMMENT 'FIRE/ELEVATOR/...';
-- ALTER TABLE insp_point ADD COLUMN qr_code VARCHAR(64) NULL;
-- ALTER TABLE insp_point ADD COLUMN qr_enabled TINYINT NOT NULL DEFAULT 1;

CREATE TABLE IF NOT EXISTS equip_work_order (
  id                BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id         BIGINT NOT NULL,
  title             VARCHAR(160) NOT NULL,
  device_id         BIGINT NULL,
  device_name       VARCHAR(120) NULL,
  location          VARCHAR(255) NULL,
  source            VARCHAR(16) NOT NULL DEFAULT 'MANUAL' COMMENT 'MANUAL/INSPECTION',
  source_checkin_id BIGINT NULL,
  status            VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/DOING/DONE',
  assignee_id       BIGINT NULL,
  assignee_name     VARCHAR(64) NULL,
  fault_desc        VARCHAR(1000) NULL,
  repair_content    VARCHAR(2000) NULL,
  parts_replaced    VARCHAR(1000) NULL,
  repair_photos     VARCHAR(2000) NULL,
  accept_photos     VARCHAR(2000) NULL,
  create_by         BIGINT NULL,
  create_by_name    VARCHAR(64) NULL,
  assign_time       DATETIME NULL,
  finish_time       DATETIME NULL,
  remark            VARCHAR(500) NULL,
  create_time       DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time       DATETIME NULL,
  KEY idx_tenant_status (tenant_id, status),
  KEY idx_source_checkin (tenant_id, source_checkin_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='设备维保工单';
