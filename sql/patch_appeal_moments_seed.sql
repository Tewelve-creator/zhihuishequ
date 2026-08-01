-- 邻里圈：按社区（租户）全员可见；为四社区补演示动态（可重复执行）
USE smart_community;

-- 确保各社区有居民账号（密码 123456 = md5）
INSERT INTO sys_user (tenant_id, phone, password, nickname, role, status, create_time)
SELECT 2, '13800000032', 'e10adc3949ba59abbe56e057f20f883e', '星河居民阿华', 'resident', 1, NOW()
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_user WHERE phone='13800000032' AND tenant_id=2);

INSERT INTO sys_user (tenant_id, phone, password, nickname, role, status, create_time)
SELECT 2, '13800000033', 'e10adc3949ba59abbe56e057f20f883e', '星河居民小雨', 'resident', 1, NOW()
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_user WHERE phone='13800000033' AND tenant_id=2);

INSERT INTO sys_user (tenant_id, phone, password, nickname, role, status, create_time)
SELECT 3, '13800000034', 'e10adc3949ba59abbe56e057f20f883e', '鹏程居民阿杰', 'resident', 1, NOW()
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_user WHERE phone='13800000034' AND tenant_id=3);

INSERT INTO sys_user (tenant_id, phone, password, nickname, role, status, create_time)
SELECT 3, '13800000035', 'e10adc3949ba59abbe56e057f20f883e', '鹏程居民小琳', 'resident', 1, NOW()
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_user WHERE phone='13800000035' AND tenant_id=3);

INSERT INTO sys_user (tenant_id, phone, password, nickname, role, status, create_time)
SELECT 4, '13800000036', 'e10adc3949ba59abbe56e057f20f883e', '翠湖居民老周', 'resident', 1, NOW()
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_user WHERE phone='13800000036' AND tenant_id=4);

INSERT INTO sys_user (tenant_id, phone, password, nickname, role, status, create_time)
SELECT 4, '13800000037', 'e10adc3949ba59abbe56e057f20f883e', '翠湖居民小荷', 'resident', 1, NOW()
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_user WHERE phone='13800000037' AND tenant_id=4);

-- 清理本脚本标记的旧演示动态后重插（不影响用户真实发帖：用固定 content 前缀 DEMO_MOMENT）
DELETE FROM appeal_comment WHERE appeal_id IN (
  SELECT id FROM (SELECT id FROM emotion_appeal WHERE content LIKE '【邻里圈】%') x
);
DELETE FROM appeal_like WHERE appeal_id IN (
  SELECT id FROM (SELECT id FROM emotion_appeal WHERE content LIKE '【邻里圈】%') x
);
DELETE FROM emotion_appeal WHERE content LIKE '【邻里圈】%';

-- 阳光花园 tenant=1
INSERT INTO emotion_appeal(tenant_id,user_id,house_label,nickname,content,like_count,comment_count,deleted,pinned,hidden,status,create_time)
SELECT 1, u.id, '1栋2单元801', u.nickname, '【邻里圈】周末跳蚤市场有人一起摆摊吗？闲置玩具图书都可交换～', 3, 0, 0, 1, 0, 'normal', DATE_SUB(NOW(), INTERVAL 2 HOUR)
FROM sys_user u WHERE u.phone='13800000021' AND u.tenant_id=1 LIMIT 1;

INSERT INTO emotion_appeal(tenant_id,user_id,house_label,nickname,content,like_count,comment_count,deleted,pinned,hidden,status,create_time)
SELECT 1, u.id, '2栋1单元502', u.nickname, '【邻里圈】小区东门快递柜修好了，取件方便多了！', 5, 0, 0, 0, 0, 'normal', DATE_SUB(NOW(), INTERVAL 5 HOUR)
FROM sys_user u WHERE u.phone='13800000023' AND u.tenant_id=1 LIMIT 1;

INSERT INTO emotion_appeal(tenant_id,user_id,house_label,nickname,content,like_count,comment_count,deleted,pinned,hidden,status,create_time)
SELECT 1, u.id, '3栋3单元1201', u.nickname, '【邻里圈】今晚天台看日落，欢迎邻居一起来晒太阳。', 2, 0, 0, 0, 0, 'normal', DATE_SUB(NOW(), INTERVAL 1 DAY)
FROM sys_user u WHERE u.phone='13800000024' AND u.tenant_id=1 LIMIT 1;

-- 星河湾 tenant=2
INSERT INTO emotion_appeal(tenant_id,user_id,house_label,nickname,content,like_count,comment_count,deleted,pinned,hidden,status,create_time)
SELECT 2, u.id, 'A栋1单元301', u.nickname, '【邻里圈】星河湾篮球场周五晚约球，差两人！', 4, 0, 0, 1, 0, 'normal', DATE_SUB(NOW(), INTERVAL 3 HOUR)
FROM sys_user u WHERE u.phone='13800000032' AND u.tenant_id=2 LIMIT 1;

INSERT INTO emotion_appeal(tenant_id,user_id,house_label,nickname,content,like_count,comment_count,deleted,pinned,hidden,status,create_time)
SELECT 2, u.id, 'B栋2单元605', u.nickname, '【邻里圈】有没有拼车去早市的邻居？明早七点东门集合。', 1, 0, 0, 0, 0, 'normal', DATE_SUB(NOW(), INTERVAL 8 HOUR)
FROM sys_user u WHERE u.phone='13800000033' AND u.tenant_id=2 LIMIT 1;

INSERT INTO emotion_appeal(tenant_id,user_id,house_label,nickname,content,like_count,comment_count,deleted,pinned,hidden,status,create_time)
SELECT 2, u.id, 'C栋1单元102', u.nickname, '【邻里圈】小区里捡到一把钥匙，失主联系我哦。', 0, 0, 0, 0, 0, 'normal', DATE_SUB(NOW(), INTERVAL 12 HOUR)
FROM sys_user u WHERE u.phone='13800000022' AND u.tenant_id=2 LIMIT 1;

-- 鹏程 tenant=3
INSERT INTO emotion_appeal(tenant_id,user_id,house_label,nickname,content,like_count,comment_count,deleted,pinned,hidden,status,create_time)
SELECT 3, u.id, '1号楼501', u.nickname, '【邻里圈】鹏程新开的社区食堂味道不错，推荐红烧排骨套餐。', 6, 0, 0, 1, 0, 'normal', DATE_SUB(NOW(), INTERVAL 4 HOUR)
FROM sys_user u WHERE u.phone='13800000034' AND u.tenant_id=3 LIMIT 1;

INSERT INTO emotion_appeal(tenant_id,user_id,house_label,nickname,content,like_count,comment_count,deleted,pinned,hidden,status,create_time)
SELECT 3, u.id, '2号楼302', u.nickname, '【邻里圈】有人出售二手婴儿车吗？同小区自提优先。', 2, 0, 0, 0, 0, 'normal', DATE_SUB(NOW(), INTERVAL 1 DAY)
FROM sys_user u WHERE u.phone='13800000035' AND u.tenant_id=3 LIMIT 1;

-- 翠湖湾 tenant=4
INSERT INTO emotion_appeal(tenant_id,user_id,house_label,nickname,content,like_count,comment_count,deleted,pinned,hidden,status,create_time)
SELECT 4, u.id, '翠湖苑3-201', u.nickname, '【邻里圈】湖边散步道修好了，傍晚凉快，欢迎散步。', 3, 0, 0, 1, 0, 'normal', DATE_SUB(NOW(), INTERVAL 6 HOUR)
FROM sys_user u WHERE u.phone='13800000036' AND u.tenant_id=4 LIMIT 1;

INSERT INTO emotion_appeal(tenant_id,user_id,house_label,nickname,content,like_count,comment_count,deleted,pinned,hidden,status,create_time)
SELECT 4, u.id, '翠湖苑5-1102', u.nickname, '【邻里圈】谁家橘猫走丢了？在西门绿化带看到一只很亲人。', 1, 0, 0, 0, 0, 'normal', DATE_SUB(NOW(), INTERVAL 9 HOUR)
FROM sys_user u WHERE u.phone='13800000037' AND u.tenant_id=4 LIMIT 1;

-- 校验：各社区动态数（同社区任意登录用户 feed 可见全部）
SELECT t.name, COUNT(a.id) AS moment_count
FROM sys_tenant t
LEFT JOIN emotion_appeal a ON a.tenant_id=t.id AND a.status='normal' AND IFNULL(a.deleted,0)=0 AND IFNULL(a.hidden,0)=0
WHERE t.name IN ('阳光花园社区','星河湾社区','鹏程小区','翠湖湾社区') AND IFNULL(t.deleted,0)=0
GROUP BY t.id, t.name
ORDER BY t.id;
