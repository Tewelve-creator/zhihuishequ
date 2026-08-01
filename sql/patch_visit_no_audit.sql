-- 临时访客免审核：补齐字段（幂等）
USE smart_community;
SET NAMES utf8mb4;

-- plate_no
SET @c := (SELECT COUNT(1) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='visitor_appointment' AND COLUMN_NAME='plate_no');
SET @s := IF(@c=0, 'ALTER TABLE visitor_appointment ADD COLUMN plate_no VARCHAR(20) NULL AFTER visitor_phone', 'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

-- drive_flag
SET @c := (SELECT COUNT(1) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='visitor_appointment' AND COLUMN_NAME='drive_flag');
SET @s := IF(@c=0, 'ALTER TABLE visitor_appointment ADD COLUMN drive_flag TINYINT NOT NULL DEFAULT 0 AFTER plate_no', 'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

-- house_id
SET @c := (SELECT COUNT(1) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='visitor_appointment' AND COLUMN_NAME='house_id');
SET @s := IF(@c=0, 'ALTER TABLE visitor_appointment ADD COLUMN house_id BIGINT NULL AFTER user_id', 'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

-- expire_time
SET @c := (SELECT COUNT(1) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='visitor_appointment' AND COLUMN_NAME='expire_time');
SET @s := IF(@c=0, 'ALTER TABLE visitor_appointment ADD COLUMN expire_time DATETIME NULL AFTER visit_time', 'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

-- checkin_time
SET @c := (SELECT COUNT(1) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='visitor_appointment' AND COLUMN_NAME='checkin_time');
SET @s := IF(@c=0, 'ALTER TABLE visitor_appointment ADD COLUMN checkin_time DATETIME NULL AFTER status', 'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

-- checkin_by
SET @c := (SELECT COUNT(1) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='visitor_appointment' AND COLUMN_NAME='checkin_by');
SET @s := IF(@c=0, 'ALTER TABLE visitor_appointment ADD COLUMN checkin_by BIGINT NULL AFTER checkin_time', 'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

UPDATE visitor_appointment SET status='approved'
WHERE status IN ('pending','rejected')
  AND (expire_time IS NULL OR expire_time > NOW());

UPDATE visitor_appointment SET status='expired'
WHERE status='approved' AND expire_time IS NOT NULL AND expire_time < NOW();

UPDATE visitor_appointment
SET expire_time = IF(visit_time IS NOT NULL, DATE_ADD(visit_time, INTERVAL 12 HOUR), DATE_ADD(create_time, INTERVAL 24 HOUR))
WHERE expire_time IS NULL;
