-- 社区超市 / 社区商城
USE smart_community;
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS store_info (
  id              BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id       BIGINT NOT NULL,
  name            VARCHAR(128) NOT NULL COMMENT '店铺名称',
  logo_url        VARCHAR(512) NULL,
  address         VARCHAR(255) NULL COMMENT '到店自提地址',
  phone           VARCHAR(32) NULL,
  open_time       VARCHAR(16) DEFAULT '08:00',
  close_time      VARCHAR(16) DEFAULT '22:00',
  min_order_amount DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '最低起送金额',
  delivery_fee    DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '配送费',
  status          TINYINT NOT NULL DEFAULT 1 COMMENT '1营业 0停业',
  remark          VARCHAR(255) NULL,
  create_time     DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time     DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS store_category (
  id          BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id   BIGINT NOT NULL,
  name        VARCHAR(64) NOT NULL,
  sort_no     INT DEFAULT 0,
  status      TINYINT DEFAULT 1,
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  KEY idx_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS store_product (
  id          BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id   BIGINT NOT NULL,
  category_id BIGINT NULL,
  name        VARCHAR(128) NOT NULL,
  cover_url   VARCHAR(512) NULL,
  price       DECIMAL(10,2) NOT NULL,
  stock       INT NOT NULL DEFAULT 0,
  sales       INT NOT NULL DEFAULT 0,
  status      TINYINT NOT NULL DEFAULT 1 COMMENT '1上架 0下架',
  sort_no     INT DEFAULT 0,
  remark      VARCHAR(255) NULL,
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_tenant_cat (tenant_id, category_id),
  KEY idx_tenant_status (tenant_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS store_cart (
  id          BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id   BIGINT NOT NULL,
  user_id     BIGINT NOT NULL,
  product_id  BIGINT NOT NULL,
  quantity    INT NOT NULL DEFAULT 1,
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_user_product (tenant_id, user_id, product_id),
  KEY idx_user (tenant_id, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS store_order (
  id              BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id       BIGINT NOT NULL,
  order_no        VARCHAR(64) NOT NULL,
  user_id         BIGINT NOT NULL,
  house_id        BIGINT NULL,
  store_id        BIGINT NULL,
  delivery_type   VARCHAR(16) NOT NULL COMMENT 'DELIVERY/PICKUP',
  status          VARCHAR(32) NOT NULL COMMENT 'UNPAID/PENDING_ACCEPT/PREPARING/DELIVERING/COMPLETED/CANCELLED/REFUNDED',
  goods_amount    DECIMAL(10,2) NOT NULL DEFAULT 0,
  delivery_fee    DECIMAL(10,2) NOT NULL DEFAULT 0,
  pay_amount      DECIMAL(10,2) NOT NULL DEFAULT 0,
  receiver_name   VARCHAR(64) NULL,
  receiver_phone  VARCHAR(32) NULL,
  receiver_addr   VARCHAR(255) NULL,
  remark          VARCHAR(255) NULL,
  pay_time        DATETIME NULL,
  accept_time     DATETIME NULL,
  prepare_time    DATETIME NULL,
  deliver_time    DATETIME NULL,
  complete_time   DATETIME NULL,
  cancel_time     DATETIME NULL,
  cancel_reason   VARCHAR(255) NULL,
  refund_status   VARCHAR(32) NULL COMMENT 'NONE/APPLYING/APPROVED/REJECTED',
  refund_amount   DECIMAL(10,2) NULL,
  refund_reason   VARCHAR(255) NULL,
  expire_time     DATETIME NULL COMMENT '未支付超时关闭',
  handler_id      BIGINT NULL,
  create_time     DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time     DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_order_no (order_no),
  KEY idx_tenant_user (tenant_id, user_id),
  KEY idx_tenant_status (tenant_id, status),
  KEY idx_expire (status, expire_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS store_order_item (
  id           BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id    BIGINT NOT NULL,
  order_id     BIGINT NOT NULL,
  product_id   BIGINT NOT NULL,
  product_name VARCHAR(128) NOT NULL,
  cover_url    VARCHAR(512) NULL,
  price        DECIMAL(10,2) NOT NULL,
  quantity     INT NOT NULL,
  amount       DECIMAL(10,2) NOT NULL,
  KEY idx_order (order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS store_payment_log (
  id           BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id    BIGINT NOT NULL,
  order_id     BIGINT NOT NULL,
  order_no     VARCHAR(64) NOT NULL,
  pay_no       VARCHAR(64) NOT NULL,
  channel      VARCHAR(32) DEFAULT 'MOCK',
  amount       DECIMAL(10,2) NOT NULL,
  status       VARCHAR(32) NOT NULL COMMENT 'SUCCESS/FAIL/PENDING',
  raw_resp     VARCHAR(1000) NULL,
  create_time  DATETIME DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_pay_no (pay_no),
  KEY idx_order (order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS store_refund_log (
  id            BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id     BIGINT NOT NULL,
  order_id      BIGINT NOT NULL,
  order_no      VARCHAR(64) NOT NULL,
  refund_no     VARCHAR(64) NOT NULL,
  amount        DECIMAL(10,2) NOT NULL,
  reason        VARCHAR(255) NULL,
  status        VARCHAR(32) NOT NULL COMMENT 'APPLYING/APPROVED/REJECTED',
  audit_by      BIGINT NULL,
  audit_remark  VARCHAR(255) NULL,
  audit_time    DATETIME NULL,
  create_time   DATETIME DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_refund_no (refund_no),
  KEY idx_order (order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 演示种子（租户1）
INSERT INTO store_info (tenant_id, name, address, phone, open_time, close_time, min_order_amount, delivery_fee, status)
SELECT 1, '阳光花园社区超市', '小区东门便利店', '13800000001', '08:00', '22:00', 15.00, 2.00, 1
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM store_info WHERE tenant_id=1);

-- 首期三大分类（无生鲜）；完整测试商品见 patch_store_catalog_v2.sql
INSERT INTO store_category (tenant_id, name, sort_no, status)
SELECT 1, '饮品酒水', 1, 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM store_category WHERE tenant_id=1 AND name='饮品酒水');
INSERT INTO store_category (tenant_id, name, sort_no, status)
SELECT 1, '零食速食', 2, 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM store_category WHERE tenant_id=1 AND name='零食速食');
INSERT INTO store_category (tenant_id, name, sort_no, status)
SELECT 1, '日用百货', 3, 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM store_category WHERE tenant_id=1 AND name='日用百货');
