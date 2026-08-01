-- SaaS main loop demo: >=3 tenants + packages + paid/pending orders
-- mysql -uroot -proot --default-character-set=utf8mb4 smart_community < sql/seed_saas_main_loop.sql
-- Or: python sql/seed_saas_ops_demo.py

UPDATE sys_tenant SET expire_time = DATE_ADD(NOW(), INTERVAL 5 DAY), status='active'
WHERE id=1 AND deleted=0;
UPDATE sys_tenant SET expire_time = DATE_ADD(NOW(), INTERVAL 17 DAY), status='active'
WHERE id=2 AND deleted=0;

INSERT INTO sys_tenant(name, address, contact_name, contact_phone, package_id, features, status,
  expire_time, storage_limit_mb, ai_quota, deleted, create_time)
SELECT '翠湖湾社区', 'demo', 'ops', '13800009991', 3,
  'basic_repair,basic_notice,ai_chat,ai_image_repair,data_dashboard', 'active',
  DATE_ADD(NOW(), INTERVAL 8 DAY), 2048, 500, 0, NOW()
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_tenant WHERE name='翠湖湾社区' AND deleted=0);

INSERT INTO sys_tenant(name, address, contact_name, contact_phone, package_id, features, status,
  expire_time, storage_limit_mb, ai_quota, deleted, create_time)
SELECT '锦绣城社区', 'demo', 'ops', '13800009992', 2,
  'basic_repair,basic_notice,ai_chat', 'active',
  DATE_ADD(NOW(), INTERVAL 45 DAY), 1024, 200, 0, NOW()
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_tenant WHERE name='锦绣城社区' AND deleted=0);

INSERT INTO sys_tenant(name, address, contact_name, contact_phone, package_id, features, status,
  expire_time, storage_limit_mb, ai_quota, deleted, create_time)
SELECT '已到期样例社区', 'demo', 'ops', '13800009993', 1,
  'basic_repair,basic_notice', 'degraded',
  DATE_SUB(NOW(), INTERVAL 3 DAY), 512, 50, 0, NOW()
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_tenant WHERE name='已到期样例社区' AND deleted=0);

INSERT INTO saas_order(order_no, tenant_id, package_id, package_name, cycle, amount, order_type, status, pay_time, start_time, end_time, create_time)
SELECT 'SOMAIN1001', 1, 3, '专业版', 'year', 2999.00, 'new', 'paid',
  DATE_SUB(NOW(), INTERVAL 5 MONTH), DATE_SUB(NOW(), INTERVAL 5 MONTH), DATE_ADD(NOW(), INTERVAL 7 MONTH), DATE_SUB(NOW(), INTERVAL 5 MONTH)
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM saas_order WHERE order_no='SOMAIN1001');

INSERT INTO saas_order(order_no, tenant_id, package_id, package_name, cycle, amount, order_type, status, pay_time, start_time, end_time, create_time)
SELECT 'SOMAIN1002', 1, 3, '专业版', 'year', 2999.00, 'renew', 'pending',
  NULL, NULL, NULL, NOW()
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM saas_order WHERE order_no='SOMAIN1002');

INSERT INTO saas_order(order_no, tenant_id, package_id, package_name, cycle, amount, order_type, status, pay_time, start_time, end_time, create_time)
SELECT 'SOMAIN1003', 2, 2, '基础版', 'year', 999.00, 'new', 'paid',
  DATE_SUB(NOW(), INTERVAL 3 MONTH), DATE_SUB(NOW(), INTERVAL 3 MONTH), DATE_ADD(NOW(), INTERVAL 9 MONTH), DATE_SUB(NOW(), INTERVAL 3 MONTH)
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM saas_order WHERE order_no='SOMAIN1003');

INSERT INTO saas_order(order_no, tenant_id, package_id, package_name, cycle, amount, order_type, status, pay_time, start_time, end_time, create_time)
SELECT 'SOMAIN1004', 2, 2, '基础版', 'month', 99.00, 'renew', 'pending',
  NULL, NULL, NULL, DATE_SUB(NOW(), INTERVAL 1 DAY)
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM saas_order WHERE order_no='SOMAIN1004');

SELECT 'tenants' AS k, COUNT(*) AS v FROM sys_tenant WHERE deleted=0
UNION ALL SELECT 'orders_pending', COUNT(*) FROM saas_order WHERE status='pending'
UNION ALL SELECT 'orders_paid', COUNT(*) FROM saas_order WHERE status='paid';
