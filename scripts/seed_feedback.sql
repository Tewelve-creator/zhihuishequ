SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS community_feedback (
  id bigint primary key auto_increment,
  tenant_id bigint not null,
  feedback_no varchar(32) not null,
  user_id bigint null,
  owner_name varchar(64) null,
  house_id bigint null,
  house_label varchar(128) null,
  phone varchar(32) null,
  feedback_type varchar(32) not null default '其他',
  title varchar(128) not null,
  content varchar(2000) null,
  images varchar(2000) null,
  status varchar(16) not null default 'PENDING',
  reply_content varchar(2000) null,
  reply_by bigint null,
  reply_by_name varchar(64) null,
  reply_time datetime null,
  create_time datetime null,
  update_time datetime null,
  unique key uk_fb_no (tenant_id, feedback_no),
  key idx_fb_tenant_status (tenant_id, status)
) engine=InnoDB default charset=utf8mb4;

CREATE TABLE IF NOT EXISTS community_feedback_log (
  id bigint primary key auto_increment,
  tenant_id bigint not null,
  feedback_id bigint not null,
  action varchar(32) not null,
  from_status varchar(16) null,
  to_status varchar(16) null,
  content varchar(2000) null,
  operator_id bigint null,
  operator_name varchar(64) null,
  create_time datetime null,
  key idx_fbl_fb (feedback_id)
) engine=InnoDB default charset=utf8mb4;

-- 演示数据（仅当租户1尚无反馈时插入）
INSERT INTO community_feedback (
  tenant_id, feedback_no, user_id, owner_name, house_id, house_label, phone,
  feedback_type, title, content, images, status, reply_content, reply_by, reply_by_name, reply_time,
  create_time, update_time
)
SELECT * FROM (
  SELECT 1 AS tenant_id, 'FB202607310001' AS feedback_no, 4 AS user_id, '居民小明' AS owner_name, 2 AS house_id, '1栋1单元102' AS house_label, '13800000021' AS phone,
         '环境卫生' AS feedback_type, '楼道堆物清理' AS title, '1单元楼道长期堆放纸箱，希望物业安排清理并加强巡查。' AS content,
         NULL AS images, 'PENDING' AS status, NULL AS reply_content, NULL AS reply_by, NULL AS reply_by_name, NULL AS reply_time,
         DATE_SUB(NOW(), INTERVAL 2 DAY) AS create_time, DATE_SUB(NOW(), INTERVAL 2 DAY) AS update_time
  UNION ALL SELECT 1,'FB202607310002',9,'小白',3,'1栋2单元201','18803109636','停车管理','访客车位指引不清','地下车库访客车位标识不明显，外来车辆经常占用人家产权车位。',NULL,'PROCESSING','已安排增设指示牌，预计本周完成。',2,'阳光物业管理处',DATE_SUB(NOW(), INTERVAL 1 DAY),DATE_SUB(NOW(), INTERVAL 3 DAY),DATE_SUB(NOW(), INTERVAL 1 DAY)
  UNION ALL SELECT 1,'FB202607310003',12,'居民小刚',91,'1栋1单元103','13800000023','设施建议','健身区增加遮阳棚','小区健身器材区域夏天暴晒，建议加装遮阳棚。',NULL,'DONE','已纳入下季度改造计划，感谢反馈。',2,'阳光物业管理处',DATE_SUB(NOW(), INTERVAL 12 HOUR),DATE_SUB(NOW(), INTERVAL 5 DAY),DATE_SUB(NOW(), INTERVAL 12 HOUR)
  UNION ALL SELECT 1,'FB202607310004',13,'居民小美',92,'1栋1单元104','13800000024','服务态度','快递柜客服响应慢','周末快递柜异常无人处理，等待近两小时。',NULL,'PENDING',NULL,NULL,NULL,NULL,DATE_SUB(NOW(), INTERVAL 8 HOUR),DATE_SUB(NOW(), INTERVAL 8 HOUR)
  UNION ALL SELECT 1,'FB202607310005',11,'151',94,'1栋1单元202','19903109633','其他','绿化浇水噪音','清晨浇水机器噪音较大，能否调整作业时间。',NULL,'PROCESSING','已与绿化单位沟通，改为上午9点后作业。',8,'张师傅',DATE_SUB(NOW(), INTERVAL 3 HOUR),DATE_SUB(NOW(), INTERVAL 1 DAY),DATE_SUB(NOW(), INTERVAL 3 HOUR)
) t
WHERE NOT EXISTS (SELECT 1 FROM community_feedback WHERE tenant_id=1 LIMIT 1);

INSERT INTO community_feedback_log (tenant_id, feedback_id, action, from_status, to_status, content, operator_id, operator_name, create_time)
SELECT f.tenant_id, f.id, 'CREATE', NULL, 'PENDING', '提交意见反馈', f.user_id, f.owner_name, f.create_time
FROM community_feedback f
WHERE f.tenant_id=1
  AND NOT EXISTS (SELECT 1 FROM community_feedback_log l WHERE l.feedback_id=f.id AND l.action='CREATE');

INSERT INTO community_feedback_log (tenant_id, feedback_id, action, from_status, to_status, content, operator_id, operator_name, create_time)
SELECT f.tenant_id, f.id, 'REPLY_STATUS', 'PENDING', f.status, f.reply_content, f.reply_by, f.reply_by_name, f.reply_time
FROM community_feedback f
WHERE f.tenant_id=1 AND f.reply_content IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM community_feedback_log l WHERE l.feedback_id=f.id AND l.action='REPLY_STATUS');

SELECT feedback_no, owner_name, house_label, feedback_type, status, title FROM community_feedback WHERE tenant_id=1 ORDER BY id;
