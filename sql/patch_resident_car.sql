-- 常驻住户车辆 + 访客预约车牌 + 停车账单关联车牌
USE smart_community;
SET NAMES utf8mb4;

-- 1. 常驻车辆表（一套房屋可绑多车；同社区车牌唯一；迁出停用）
CREATE TABLE IF NOT EXISTS resident_car (
  id              BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id       BIGINT       NOT NULL COMMENT '租户/社区ID',
  house_id        BIGINT       NOT NULL COMMENT '绑定房屋ID',
  house_label     VARCHAR(255) NULL COMMENT '房屋位置冗余',
  resident_id     BIGINT       NULL COMMENT '住户档案 household_resident.id',
  user_id         BIGINT       NULL COMMENT 'App用户ID',
  owner_name      VARCHAR(64)  NULL COMMENT '车主姓名',
  owner_phone     VARCHAR(20)  NULL COMMENT '车主手机',
  plate_no        VARCHAR(20)  NOT NULL COMMENT '车牌号（大写归一化）',
  brand_model     VARCHAR(64)  NULL COMMENT '品牌型号',
  color           VARCHAR(32)  NULL COMMENT '颜色',
  status          VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE正常/DISABLED停用(迁出)',
  remark          VARCHAR(255) NULL,
  create_by       BIGINT       NULL,
  create_time     DATETIME     DEFAULT CURRENT_TIMESTAMP,
  update_time     DATETIME     NULL ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_tenant_plate (tenant_id, plate_no),
  KEY idx_house (tenant_id, house_id, status),
  KEY idx_user (tenant_id, user_id),
  KEY idx_resident (tenant_id, resident_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='常驻住户车辆';

-- 2. 访客预约增加临时车牌
SET @col := (
  SELECT COUNT(1) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'visitor_appointment' AND COLUMN_NAME = 'plate_no'
);
SET @sql := IF(@col = 0,
  'ALTER TABLE visitor_appointment ADD COLUMN plate_no VARCHAR(20) NULL COMMENT ''临时访客车牌'' AFTER visitor_phone',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 3. 物业费账单可关联车牌（停车费）
SET @col2 := (
  SELECT COUNT(1) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'fee_bill' AND COLUMN_NAME = 'plate_no'
);
SET @sql2 := IF(@col2 = 0,
  'ALTER TABLE fee_bill ADD COLUMN plate_no VARCHAR(20) NULL COMMENT ''关联车辆车牌(停车费)'' AFTER fee_type_name',
  'SELECT 1');
PREPARE stmt2 FROM @sql2; EXECUTE stmt2; DEALLOCATE PREPARE stmt2;

-- 演示：为阳光花园演示住户挂一辆车（若房屋/住户存在）
INSERT INTO resident_car (tenant_id, house_id, house_label, user_id, owner_name, owner_phone, plate_no, brand_model, color, status, create_time)
SELECT 1, uh.house_id, CONCAT(IFNULL(h.building,''), IFNULL(h.unit,''), IFNULL(h.room_no,'')),
       uh.user_id, u.nickname, u.phone, '京A88888', '演示轿车', '白', 'ACTIVE', NOW()
FROM user_house_bind uh
JOIN community_house h ON h.id = uh.house_id
JOIN sys_user u ON u.id = uh.user_id
WHERE uh.tenant_id = 1 AND u.phone = '13800000021'
  AND NOT EXISTS (SELECT 1 FROM resident_car WHERE tenant_id=1 AND plate_no='京A88888')
LIMIT 1;
