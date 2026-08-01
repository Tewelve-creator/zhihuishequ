-- 四社区固定套餐绑定（可重复执行）
-- 翠湖湾→free  鹏程→basic  星河湾→pro  阳光花园→flagship
-- 功能重分：取消 multi_community；vendor_manage（社区商超）仅旗舰版
USE smart_community;

-- 确保四级套餐存在（含 features 列，兼容严格模式）
INSERT INTO sys_package (id, name, code, features, price_month, price_quarter, price_year, ai_quota, status, sort_no, remark)
VALUES
(1, '免费版', 'free', 'basic_repair,basic_notice', 0, 0, 0, 20, 1, 1, '到期降级保底：仅公告+报修'),
(2, '基础版', 'basic', 'basic_repair,basic_notice,ai_chat,ai_image_repair', 99, 269, 999, 100, 1, 2, '基础便民+简易AI'),
(3, '专业版', 'pro', 'basic_repair,basic_notice,ai_chat,ai_image_repair,ai_emotion,ai_lost_match,data_dashboard,data_export', 299, 799, 2999, 500, 1, 3, '全部AI+数据大屏+导出'),
(4, '旗舰版', 'flagship', 'basic_repair,basic_notice,ai_chat,ai_image_repair,ai_emotion,ai_lost_match,data_dashboard,data_export,vendor_manage', 599, 1599, 5999, 2000, 1, 4, '社区商超+旗舰配额')
ON DUPLICATE KEY UPDATE
  name=VALUES(name), code=VALUES(code), features=VALUES(features),
  ai_quota=VALUES(ai_quota), status=1, sort_no=VALUES(sort_no), remark=VALUES(remark);

-- 重建套餐功能
DELETE FROM sys_package_feature WHERE package_id IN (1,2,3,4);
INSERT INTO sys_package_feature (package_id, feature_code, feature_name) VALUES
(1,'basic_repair','基础报修'),
(1,'basic_notice','基础公告'),
(2,'basic_repair','基础报修'),
(2,'basic_notice','基础公告'),
(2,'ai_chat','AI问答'),
(2,'ai_image_repair','识图报修'),
(3,'basic_repair','基础报修'),
(3,'basic_notice','基础公告'),
(3,'ai_chat','AI问答'),
(3,'ai_image_repair','识图报修'),
(3,'ai_emotion','情绪倾诉'),
(3,'ai_lost_match','失物匹配'),
(3,'data_dashboard','数据大屏'),
(3,'data_export','数据导出'),
(4,'basic_repair','基础报修'),
(4,'basic_notice','基础公告'),
(4,'ai_chat','AI问答'),
(4,'ai_image_repair','识图报修'),
(4,'ai_emotion','情绪倾诉'),
(4,'ai_lost_match','失物匹配'),
(4,'data_dashboard','数据大屏'),
(4,'data_export','数据导出'),
(4,'vendor_manage','社区商超');

-- 阳光花园社区 → 旗舰版（含商超，无 multi_community）
UPDATE sys_tenant SET
  package_id=4,
  features='basic_repair,basic_notice,ai_chat,ai_image_repair,ai_emotion,ai_lost_match,data_dashboard,data_export,vendor_manage',
  expire_time=DATE_ADD(NOW(), INTERVAL 365 DAY),
  ai_quota=2000,
  status='active'
WHERE name='阳光花园社区' AND IFNULL(deleted,0)=0;

-- 星河湾社区 → 专业版（全 AI + 大屏 + 导出，不含商超）
UPDATE sys_tenant SET
  package_id=3,
  features='basic_repair,basic_notice,ai_chat,ai_image_repair,ai_emotion,ai_lost_match,data_dashboard,data_export',
  expire_time=DATE_ADD(NOW(), INTERVAL 180 DAY),
  ai_quota=500,
  status='active'
WHERE name='星河湾社区' AND IFNULL(deleted,0)=0;

-- 翠湖湾社区 → 免费版（不存在则创建）
INSERT INTO sys_tenant(name, address, contact_name, contact_phone, package_id, features, status,
                       expire_time, storage_limit_mb, ai_quota, create_time)
SELECT '翠湖湾社区', '示例市翠湖路1号', '翠湖物业', '13800000003', 1,
       'basic_repair,basic_notice', 'active',
       DATE_ADD(NOW(), INTERVAL 90 DAY), 512, 20, NOW()
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_tenant WHERE name='翠湖湾社区' AND IFNULL(deleted,0)=0);

UPDATE sys_tenant SET
  package_id=1,
  features='basic_repair,basic_notice',
  expire_time=DATE_ADD(NOW(), INTERVAL 90 DAY),
  ai_quota=20,
  status='active'
WHERE name='翠湖湾社区' AND IFNULL(deleted,0)=0;

-- 鹏程小区 → 基础版
INSERT INTO sys_tenant(name, address, contact_name, contact_phone, package_id, features, status,
                       expire_time, storage_limit_mb, ai_quota, create_time)
SELECT '鹏程小区', '示例市鹏程大道88号', '鹏程物业', '13800000004', 2,
       'basic_repair,basic_notice,ai_chat,ai_image_repair', 'active',
       DATE_ADD(NOW(), INTERVAL 120 DAY), 1024, 100, NOW()
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_tenant WHERE name='鹏程小区' AND IFNULL(deleted,0)=0);

UPDATE sys_tenant SET
  package_id=2,
  features='basic_repair,basic_notice,ai_chat,ai_image_repair',
  expire_time=DATE_ADD(NOW(), INTERVAL 120 DAY),
  ai_quota=100,
  status='active'
WHERE name='鹏程小区' AND IFNULL(deleted,0)=0;

-- 演示物业账号 13800000001 可切换四社区（同手机号多租户账号，密码 123456）
INSERT INTO sys_user (tenant_id, phone, password, nickname, role, status, create_time)
SELECT t.id, '13800000001', 'e10adc3949ba59abbe56e057f20f883e', CONCAT(t.name, '管理员'), 'property_admin', 1, NOW()
FROM sys_tenant t
WHERE t.name IN ('翠湖湾社区', '鹏程小区', '星河湾社区', '阳光花园社区')
  AND IFNULL(t.deleted,0)=0
  AND NOT EXISTS (
    SELECT 1 FROM sys_user u WHERE u.phone='13800000001' AND u.tenant_id=t.id
  );

SELECT id, name, code, features, ai_quota, remark FROM sys_package WHERE id IN (1,2,3,4);
SELECT id, name, package_id, features, ai_quota, status FROM sys_tenant
WHERE name IN ('翠湖湾社区','鹏程小区','星河湾社区','阳光花园社区') AND IFNULL(deleted,0)=0;
