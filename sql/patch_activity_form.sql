-- 活动表单扩展字段（若列已存在会报错，可忽略）
ALTER TABLE community_activity
  ADD COLUMN category VARCHAR(64) NULL COMMENT '邻里文体/公益志愿/物业通知/亲子活动' AFTER title;

ALTER TABLE community_activity
  ADD COLUMN allow_cancel TINYINT DEFAULT 1 COMMENT '1允许取消报名 0不允许' AFTER max_people;
