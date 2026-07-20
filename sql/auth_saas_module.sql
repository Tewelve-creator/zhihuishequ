-- ============================================================
-- community-auth：套餐 / 订单 / 登录日志 / 消息 + 租户增强
-- ============================================================
CREATE DATABASE IF NOT EXISTS smart_community DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE smart_community;

DROP TABLE IF EXISTS sys_package_feature;
DROP TABLE IF EXISTS saas_order;
DROP TABLE IF EXISTS sys_login_log;
DROP TABLE IF EXISTS sys_message;
DROP TABLE IF EXISTS sys_package;

CREATE TABLE sys_package (
  id            BIGINT PRIMARY KEY AUTO_INCREMENT,
  name          VARCHAR(64)  NOT NULL,
  code          VARCHAR(32)  NOT NULL,
  price_month   DECIMAL(10,2) DEFAULT 0,
  price_quarter DECIMAL(10,2) DEFAULT 0,
  price_year    DECIMAL(10,2) DEFAULT 0,
  ai_quota      INT DEFAULT 50,
  status        TINYINT DEFAULT 1,
  sort_no       INT DEFAULT 0,
  remark        VARCHAR(255),
  create_time   DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time   DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted       TINYINT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_code (code)
) ENGINE=InnoDB;

CREATE TABLE sys_package_feature (
  id           BIGINT PRIMARY KEY AUTO_INCREMENT,
  package_id   BIGINT NOT NULL,
  feature_code VARCHAR(64) NOT NULL,
  feature_name VARCHAR(64) DEFAULT NULL,
  UNIQUE KEY uk_pkg_feat (package_id, feature_code)
) ENGINE=InnoDB;

CREATE TABLE saas_order (
  id            BIGINT PRIMARY KEY AUTO_INCREMENT,
  order_no      VARCHAR(64) NOT NULL,
  tenant_id     BIGINT NOT NULL,
  package_id    BIGINT NOT NULL,
  package_name  VARCHAR(64),
  cycle         VARCHAR(16) NOT NULL,
  amount        DECIMAL(10,2) NOT NULL DEFAULT 0,
  order_type    VARCHAR(16) NOT NULL DEFAULT 'new',
  status        VARCHAR(16) NOT NULL DEFAULT 'pending',
  pay_time      DATETIME NULL,
  start_time    DATETIME NULL,
  end_time      DATETIME NULL,
  create_by     BIGINT NULL,
  create_time   DATETIME DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_order_no (order_no),
  KEY idx_tenant (tenant_id)
) ENGINE=InnoDB;

CREATE TABLE sys_login_log (
  id          BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id   BIGINT NULL,
  user_id     BIGINT NULL,
  phone       VARCHAR(20),
  client_type VARCHAR(16),
  login_type  VARCHAR(16),
  ip          VARCHAR(64),
  success     TINYINT NOT NULL DEFAULT 1,
  msg         VARCHAR(255),
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  KEY idx_user (user_id),
  KEY idx_time (create_time)
) ENGINE=InnoDB;

CREATE TABLE sys_message (
  id          BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id   BIGINT NULL,
  user_id     BIGINT NOT NULL,
  title       VARCHAR(128) NOT NULL,
  content     VARCHAR(1000),
  msg_type    VARCHAR(32) DEFAULT 'system',
  read_flag   TINYINT DEFAULT 0,
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  KEY idx_user (user_id)
) ENGINE=InnoDB;

-- 租户增强列（已存在会报错，可忽略）
-- 建议：先 DESCRIBE sys_tenant，缺哪列补哪列
ALTER TABLE sys_tenant ADD COLUMN package_id BIGINT NULL COMMENT '当前套餐' AFTER contact_phone;
ALTER TABLE sys_tenant ADD COLUMN features VARCHAR(512) NULL COMMENT '功能码CSV' AFTER package_id;
ALTER TABLE sys_tenant ADD COLUMN expire_time DATETIME NULL COMMENT '到期时间' AFTER features;
ALTER TABLE sys_tenant ADD COLUMN ai_quota INT DEFAULT 50 AFTER expire_time;
ALTER TABLE sys_tenant ADD COLUMN storage_limit_mb INT DEFAULT 1024 AFTER ai_quota;

INSERT INTO sys_package (id, name, code, price_month, price_quarter, price_year, ai_quota, status, sort_no, remark) VALUES
(1, '免费试用版', 'free',     0,    0,     0,    20,  1, 1, '7天试用'),
(2, '基础版',     'basic',  99,  269,   999,   100,  1, 2, '年费基础'),
(3, '专业版',     'pro',   299,  799,  2999,   500,  1, 3, '全部AI+大屏'),
(4, '旗舰版',     'flagship',599,1599, 5999,  2000,  1, 4, '多社区');

INSERT INTO sys_package_feature (package_id, feature_code, feature_name) VALUES
-- free：到期降级保底，仅公告+报修
(1,'basic_repair','基础报修'),(1,'basic_notice','基础公告'),
(2,'basic_repair','基础报修'),(2,'basic_notice','基础公告'),(2,'ai_chat','AI问答'),(2,'ai_image_repair','识图报修'),
(3,'basic_repair','基础报修'),(3,'basic_notice','基础公告'),(3,'ai_chat','AI问答'),
(3,'ai_image_repair','识图报修'),(3,'ai_emotion','情绪倾诉'),(3,'ai_lost_match','失物匹配'),
(3,'data_dashboard','数据大屏'),(3,'data_export','数据导出'),(3,'vendor_manage','服务商管理'),
(4,'basic_repair','基础报修'),(4,'basic_notice','基础公告'),(4,'ai_chat','AI问答'),
(4,'ai_image_repair','识图报修'),(4,'ai_emotion','情绪倾诉'),(4,'ai_lost_match','失物匹配'),
(4,'data_dashboard','数据大屏'),(4,'data_export','数据导出'),(4,'vendor_manage','服务商管理'),
(4,'multi_community','多社区管理');

UPDATE sys_tenant SET package_id=3,
  features='basic_repair,basic_notice,ai_chat,ai_image_repair,ai_emotion,ai_lost_match,data_dashboard,data_export,vendor_manage',
  expire_time=DATE_ADD(NOW(), INTERVAL 90 DAY), ai_quota=500, status='active'
WHERE id=1;

UPDATE sys_tenant SET package_id=2,
  features='basic_repair,basic_notice,ai_chat,ai_image_repair',
  expire_time=DATE_ADD(NOW(), INTERVAL 30 DAY), ai_quota=100, status='active'
WHERE id=2;

-- 套餐/订单菜单权限
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, perms, icon, client_type, sort_no) VALUES
(40, 0,  '套餐租赁', 'M', '/lease', NULL, NULL, 'money', 'ADMIN', 4),
(41, 40, '套餐管理', 'C', 'package', 'lease/package', 'auth:package:list', 'goods', 'ADMIN', 1),
(42, 41, '套餐编辑', 'F', NULL, NULL, 'auth:package:edit', NULL, 'ADMIN', 1),
(43, 40, '租赁订单', 'C', 'order', 'lease/order', 'auth:order:list', 'list', 'ADMIN', 2),
(44, 43, '创建订单', 'F', NULL, NULL, 'auth:order:create', NULL, 'ADMIN', 1),
(45, 43, '订单支付', 'F', NULL, NULL, 'auth:order:pay', NULL, 'ADMIN', 2),
(46, 40, '我的套餐', 'C', 'mine', 'lease/mine', 'auth:lease:view', 'time', 'ADMIN', 3)
ON DUPLICATE KEY UPDATE menu_name=VALUES(menu_name), perms=VALUES(perms);

-- 运营：套餐租赁全部
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES
(1, 40), (1, 41), (1, 42), (1, 43), (1, 44), (1, 45), (1, 46);

-- 物业：订单+我的套餐（续费升级）
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES
(2, 40), (2, 43), (2, 44), (2, 45), (2, 46);

