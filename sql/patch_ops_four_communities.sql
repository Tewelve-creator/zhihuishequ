-- 运营端演示数据：仅保留四社区，订单金额与套餐价目一致（可重复执行）
-- 翠湖湾 free / 鹏程 basic / 星河湾 pro / 阳光花园 flagship
USE smart_community;

-- 1) 套餐价目与说明（与 patch_community_packages 一致）
UPDATE sys_package SET
  price_month=0, price_quarter=0, price_year=0, ai_quota=20,
  features='basic_repair,basic_notice',
  remark='到期降级保底：仅公告+报修', status=1, sort_no=1
WHERE id=1 AND code='free';

UPDATE sys_package SET
  price_month=99, price_quarter=269, price_year=999, ai_quota=100,
  features='basic_repair,basic_notice,ai_chat,ai_image_repair',
  remark='基础便民+简易AI', status=1, sort_no=2
WHERE id=2 AND code='basic';

UPDATE sys_package SET
  price_month=299, price_quarter=799, price_year=2999, ai_quota=500,
  features='basic_repair,basic_notice,ai_chat,ai_image_repair,ai_emotion,ai_lost_match,data_dashboard,data_export',
  remark='全部AI+数据大屏+导出', status=1, sort_no=3
WHERE id=3 AND code='pro';

UPDATE sys_package SET
  price_month=599, price_quarter=1599, price_year=5999, ai_quota=2000,
  features='basic_repair,basic_notice,ai_chat,ai_image_repair,ai_emotion,ai_lost_match,data_dashboard,data_export,vendor_manage',
  remark='社区商超+旗舰配额', status=1, sort_no=4
WHERE id=4 AND code='flagship';

-- 2) 四社区套餐绑定与联系人（不改业务工单等）
UPDATE sys_tenant SET
  package_id=4,
  features='basic_repair,basic_notice,ai_chat,ai_image_repair,ai_emotion,ai_lost_match,data_dashboard,data_export,vendor_manage',
  ai_quota=2000, status='active',
  contact_name='张管家', contact_phone='13800000001',
  expire_time=DATE_ADD(NOW(), INTERVAL 365 DAY),
  deleted=0
WHERE name='阳光花园社区';

UPDATE sys_tenant SET
  package_id=3,
  features='basic_repair,basic_notice,ai_chat,ai_image_repair,ai_emotion,ai_lost_match,data_dashboard,data_export',
  ai_quota=500, status='active',
  contact_name='李主任', contact_phone='13800000002',
  expire_time=DATE_ADD(NOW(), INTERVAL 300 DAY),
  deleted=0
WHERE name='星河湾社区';

UPDATE sys_tenant SET
  package_id=2,
  features='basic_repair,basic_notice,ai_chat,ai_image_repair',
  ai_quota=100, status='active',
  contact_name='张不白', contact_phone='17702109622',
  expire_time=DATE_ADD(NOW(), INTERVAL 180 DAY),
  deleted=0
WHERE name='鹏程小区';

UPDATE sys_tenant SET
  package_id=1,
  features='basic_repair,basic_notice',
  ai_quota=20, status='active',
  contact_name='翠湖物业', contact_phone='13800000003',
  expire_time=DATE_ADD(NOW(), INTERVAL 90 DAY),
  deleted=0
WHERE name='翠湖湾社区';

-- 3) 删除其余社区（软删 + 停用其物业账号）
UPDATE sys_tenant SET deleted=1, status='disabled'
WHERE IFNULL(deleted,0)=0
  AND name NOT IN ('阳光花园社区','星河湾社区','鹏程小区','翠湖湾社区');

UPDATE sys_user u
JOIN sys_tenant t ON t.id=u.tenant_id
SET u.status=0
WHERE t.deleted=1 AND u.role <> 'platform_admin';

-- 4) 不再灌入 SaaS 演示订单；仅清理历史种子单，保留开户/升级真实订单
DELETE FROM saas_order
WHERE order_no LIKE 'SO4C-%'
   OR order_no LIKE 'SOMAIN%'
   OR order_no LIKE 'SOFIX%'
   OR order_no LIKE 'SODEMO%';

-- 5) 校验
SELECT id, name, package_id, status, deleted, ai_quota, expire_time, contact_phone
FROM sys_tenant ORDER BY id;

SELECT o.order_no, t.name AS tenant, o.package_name, o.cycle, o.amount, o.order_type, o.status, o.pay_method
FROM saas_order o
JOIN sys_tenant t ON t.id=o.tenant_id
ORDER BY o.id;

SELECT
  IFNULL(SUM(CASE WHEN status='paid' THEN amount ELSE 0 END),0) AS paid_amount,
  IFNULL(SUM(CASE WHEN status='pending' THEN amount ELSE 0 END),0) AS pending_amount,
  COUNT(*) AS order_count
FROM saas_order;
