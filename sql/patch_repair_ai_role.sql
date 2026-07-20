-- 报修工单：AI标签 / 完工图 / 接单人（可重复执行，已存在列忽略报错）
USE smart_community;

ALTER TABLE repair_order ADD COLUMN ai_tag VARCHAR(128) NULL COMMENT 'AI识别故障标签' AFTER damage_level;
ALTER TABLE repair_order ADD COLUMN finish_image_url VARCHAR(512) NULL COMMENT '维修完工照片' AFTER image_url;
ALTER TABLE repair_order ADD COLUMN handler_id BIGINT NULL COMMENT '接单物业用户' AFTER user_id;
ALTER TABLE repair_order ADD COLUMN accept_time DATETIME NULL AFTER handle_remark;
ALTER TABLE repair_order ADD COLUMN finish_time DATETIME NULL AFTER accept_time;
