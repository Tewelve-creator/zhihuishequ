-- 住户管理扩展：房屋户型/面积/房态 + 租客租赁周期
USE smart_community;

SET NAMES utf8mb4;

-- community_house 扩展
SET @sql = (
  SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'community_house' AND COLUMN_NAME = 'layout_type'),
    'SELECT 1',
    'ALTER TABLE community_house ADD COLUMN layout_type VARCHAR(32) NULL COMMENT ''户型 如 两室一厅'' AFTER room_no'
  )
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (
  SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'community_house' AND COLUMN_NAME = 'area_sqm'),
    'SELECT 1',
    'ALTER TABLE community_house ADD COLUMN area_sqm DECIMAL(10,2) NULL COMMENT ''建筑面积㎡'' AFTER layout_type'
  )
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (
  SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'community_house' AND COLUMN_NAME = 'occupy_status'),
    'SELECT 1',
    'ALTER TABLE community_house ADD COLUMN occupy_status VARCHAR(16) NULL DEFAULT ''VACANT'' COMMENT ''VACANT/SELF/RENT/OTHER'' AFTER area_sqm'
  )
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- household_resident 租赁周期
SET @sql = (
  SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'household_resident' AND COLUMN_NAME = 'lease_start'),
    'SELECT 1',
    'ALTER TABLE household_resident ADD COLUMN lease_start DATE NULL COMMENT ''租赁开始'' AFTER identity_type'
  )
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (
  SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'household_resident' AND COLUMN_NAME = 'lease_end'),
    'SELECT 1',
    'ALTER TABLE household_resident ADD COLUMN lease_end DATE NULL COMMENT ''租赁结束'' AFTER lease_start'
  )
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
