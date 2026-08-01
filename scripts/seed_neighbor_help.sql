SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS neighbor_help_post (
  id bigint primary key auto_increment,
  tenant_id bigint not null,
  post_no varchar(32) not null,
  user_id bigint null,
  owner_name varchar(64) null,
  house_id bigint null,
  house_label varchar(128) null,
  help_type varchar(32) not null default '其他',
  title varchar(128) not null,
  content varchar(2000) null,
  images varchar(2000) null,
  status varchar(16) not null default 'NORMAL',
  off_reason varchar(500) null,
  off_by bigint null,
  off_by_name varchar(64) null,
  off_time datetime null,
  create_time datetime null,
  update_time datetime null,
  unique key uk_nh_no (tenant_id, post_no),
  key idx_nh_tenant_status (tenant_id, status)
) engine=InnoDB default charset=utf8mb4;

INSERT INTO neighbor_help_post (
  tenant_id, post_no, user_id, owner_name, house_id, house_label, help_type,
  title, content, images, status, create_time, update_time
)
SELECT * FROM (
  SELECT 1 AS tenant_id, 'NH202607310001' AS post_no, 4 AS user_id, '居民小明' AS owner_name, 2 AS house_id, '1栋1单元102' AS house_label,
         '借物求助' AS help_type, '临时借用梯子' AS title,
         '家里要换灯泡，需要借个两米折叠梯，用完当天归还，谢谢邻居！' AS content,
         NULL AS images, 'NORMAL' AS status, DATE_SUB(NOW(), INTERVAL 2 DAY) AS create_time, DATE_SUB(NOW(), INTERVAL 2 DAY) AS update_time
  UNION ALL SELECT 1,'NH202607310002',9,'小白',3,'1栋2单元201','拼车出行','明早拼车去高铁站','明早 7:30 从小区门口出发去高铁站，还有两个空位，AA油费。',NULL,'NORMAL',DATE_SUB(NOW(), INTERVAL 1 DAY),DATE_SUB(NOW(), INTERVAL 1 DAY)
  UNION ALL SELECT 1,'NH202607310003',12,'居民小刚',91,'1栋1单元103','技能互助','免费帮忙修电脑','本人从事 IT，周末可上门帮忙排查电脑蓝屏/重装系统，邻里免费。',NULL,'NORMAL',DATE_SUB(NOW(), INTERVAL 20 HOUR),DATE_SUB(NOW(), INTERVAL 20 HOUR)
  UNION ALL SELECT 1,'NH202607310004',13,'居民小美',92,'1栋1单元104','照看宠物','出差三天求照看猫咪','本周五至周日出差，求靠谱邻居帮忙喂粮换水，可互惠答谢。',NULL,'NORMAL',DATE_SUB(NOW(), INTERVAL 10 HOUR),DATE_SUB(NOW(), INTERVAL 10 HOUR)
  UNION ALL SELECT 1,'NH202607310005',11,'151',94,'1栋1单元202','其他','转让闲置儿童自行车','孩子长大了，12寸儿童自行车低价转让给小区邻居，有意私聊。',NULL,'OFF',DATE_SUB(NOW(), INTERVAL 3 DAY),DATE_SUB(NOW(), INTERVAL 5 HOUR)
) t
WHERE NOT EXISTS (SELECT 1 FROM neighbor_help_post WHERE tenant_id=1 LIMIT 1);

UPDATE neighbor_help_post
SET off_reason='疑似广告引流，已下架处理', off_by=2, off_by_name='阳光物业管理处', off_time=DATE_SUB(NOW(), INTERVAL 5 HOUR)
WHERE tenant_id=1 AND post_no='NH202607310005' AND status='OFF' AND off_reason IS NULL;

SELECT post_no, owner_name, help_type, status, title FROM neighbor_help_post WHERE tenant_id=1 ORDER BY id;
