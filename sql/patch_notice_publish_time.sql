-- 公告定时发布
ALTER TABLE notice_info ADD COLUMN IF NOT EXISTS publish_time DATETIME NULL COMMENT '定时发布时间' AFTER status;
UPDATE notice_info SET publish_time = IFNULL(create_time, NOW()) WHERE publish_time IS NULL;
