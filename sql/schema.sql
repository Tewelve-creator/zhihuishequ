CREATE DATABASE IF NOT EXISTS smart_community DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE smart_community;

-- 套餐
CREATE TABLE IF NOT EXISTS sys_package (
  id            BIGINT PRIMARY KEY AUTO_INCREMENT,
  name          VARCHAR(64) NOT NULL,
  code          VARCHAR(32) NOT NULL,
  features      VARCHAR(512) NOT NULL COMMENT '逗号分隔功能码',
  price_month   DECIMAL(10,2) DEFAULT 0,
  price_quarter DECIMAL(10,2) DEFAULT 0,
  price_year    DECIMAL(10,2) DEFAULT 0,
  ai_quota      INT DEFAULT 50,
  status        TINYINT DEFAULT 1,
  remark        VARCHAR(255)
) ENGINE=InnoDB;

-- 租户社区
CREATE TABLE IF NOT EXISTS sys_tenant (
  id               BIGINT PRIMARY KEY AUTO_INCREMENT,
  name             VARCHAR(128) NOT NULL,
  address          VARCHAR(255),
  contact_name     VARCHAR(64),
  contact_phone    VARCHAR(32),
  package_id       BIGINT,
  features         VARCHAR(512),
  status           VARCHAR(32) DEFAULT 'active' COMMENT 'active/degraded/frozen/disabled',
  expire_time      DATETIME,
  storage_limit_mb INT DEFAULT 1024,
  ai_quota         INT DEFAULT 50,
  create_time      DATETIME
) ENGINE=InnoDB;

-- 用户（含 tenant_id 隔离）
CREATE TABLE IF NOT EXISTS sys_user (
  id          BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id   BIGINT NULL,
  phone       VARCHAR(20) NOT NULL,
  password    VARCHAR(64) NOT NULL,
  nickname    VARCHAR(64),
  avatar      VARCHAR(255),
  role        VARCHAR(32) NOT NULL,
  room_no     VARCHAR(64),
  status      TINYINT DEFAULT 1,
  create_time DATETIME,
  KEY idx_tenant_phone (tenant_id, phone)
) ENGINE=InnoDB;

-- 报修
CREATE TABLE IF NOT EXISTS repair_order (
  id            BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id     BIGINT NOT NULL,
  user_id       BIGINT,
  handler_id    BIGINT NULL COMMENT '接单/被指派物业用户',
  title         VARCHAR(128),
  description   VARCHAR(1000),
  facility_type VARCHAR(64),
  damage_level  VARCHAR(32),
  ai_tag        VARCHAR(128) NULL COMMENT 'AI识别故障标签',
  image_url     VARCHAR(255),
  finish_image_url VARCHAR(512) NULL COMMENT '维修完工照片',
  status        VARCHAR(32) DEFAULT 'pending',
  handle_remark VARCHAR(500),
  accept_time   DATETIME NULL,
  finish_time   DATETIME NULL,
  create_time   DATETIME,
  update_time   DATETIME,
  KEY idx_tenant (tenant_id)
) ENGINE=InnoDB;

-- 公告
CREATE TABLE IF NOT EXISTS notice_info (
  id           BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id    BIGINT NOT NULL,
  title        VARCHAR(128),
  content      TEXT,
  summary      VARCHAR(255),
  target_tag   VARCHAR(64),
  publisher_id BIGINT,
  status       TINYINT DEFAULT 1,
  publish_time DATETIME NULL COMMENT '定时发布时间',
  create_time  DATETIME,
  KEY idx_tenant (tenant_id)
) ENGINE=InnoDB;

-- AI 知识库
CREATE TABLE IF NOT EXISTS ai_knowledge (
  id          BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id   BIGINT NOT NULL,
  question    VARCHAR(255),
  answer      TEXT,
  hit_count   INT DEFAULT 0,
  create_time DATETIME,
  KEY idx_tenant (tenant_id)
) ENGINE=InnoDB;

-- 租赁订单
CREATE TABLE IF NOT EXISTS saas_order (
  id           BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id    BIGINT NOT NULL,
  package_id   BIGINT,
  package_name VARCHAR(64),
  cycle        VARCHAR(16),
  amount       DECIMAL(10,2),
  pay_status   VARCHAR(16),
  pay_time     DATETIME,
  start_time   DATETIME,
  end_time     DATETIME,
  features     VARCHAR(512),
  create_time  DATETIME,
  KEY idx_tenant (tenant_id)
) ENGINE=InnoDB;

-- 全局敏感词 / 公共知识库占位
CREATE TABLE IF NOT EXISTS sys_sensitive_word (
  id   BIGINT PRIMARY KEY AUTO_INCREMENT,
  word VARCHAR(64) NOT NULL
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS global_ai_kb (
  id       BIGINT PRIMARY KEY AUTO_INCREMENT,
  question VARCHAR(255),
  answer   TEXT
) ENGINE=InnoDB;

-- 站内消息（续费提醒、公告推送等）
CREATE TABLE IF NOT EXISTS sys_message (
  id          BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id   BIGINT,
  user_id     BIGINT NOT NULL,
  title       VARCHAR(128),
  content     VARCHAR(1000),
  msg_type    VARCHAR(32) DEFAULT 'system',
  read_flag   TINYINT DEFAULT 0,
  create_time DATETIME,
  KEY idx_user (user_id),
  KEY idx_tenant (tenant_id)
) ENGINE=InnoDB;

-- ========== 演示数据 ==========
-- 密码均为 123456 的 MD5: e10adc3949ba59abbe56e057f20f883e
INSERT INTO sys_package (id, name, code, features, price_month, price_quarter, price_year, ai_quota, status, remark) VALUES
(1, '免费版', 'free', 'basic_repair,basic_notice,ai_chat', 0, 0, 0, 20, 1, '7天试用'),
(2, '基础版', 'basic', 'basic_repair,basic_notice,ai_chat,ai_image_repair', 99, 279, 999, 100, 1, '年费'),
(3, '专业版', 'pro', 'basic_repair,basic_notice,ai_chat,ai_image_repair,ai_emotion,ai_lost_match,data_dashboard,data_export,vendor_manage', 199, 549, 1999, 500, 1, '全功能'),
(4, '旗舰版', 'flagship', 'basic_repair,basic_notice,ai_chat,ai_image_repair,ai_emotion,ai_lost_match,data_dashboard,data_export,vendor_manage,multi_community', 399, 1099, 3999, 2000, 1, '连锁多社区');

INSERT INTO sys_tenant (id, name, address, contact_name, contact_phone, package_id, features, status, expire_time, storage_limit_mb, ai_quota, create_time) VALUES
(1, '阳光花园社区', '示例市示例区阳光路1号', '张物业', '13800000001', 3,
 'basic_repair,basic_notice,ai_chat,ai_image_repair,ai_emotion,ai_lost_match,data_dashboard,data_export,vendor_manage',
 'active', DATE_ADD(NOW(), INTERVAL 180 DAY), 2048, 500, NOW()),
(2, '星河湾社区', '示例市示例区星河路8号', '李物业', '13800000002', 2,
 'basic_repair,basic_notice,ai_chat,ai_image_repair',
 'active', DATE_ADD(NOW(), INTERVAL 90 DAY), 1024, 100, NOW());

INSERT INTO sys_user (id, tenant_id, phone, password, nickname, role, room_no, status, create_time) VALUES
(1, NULL, '13900000000', 'e10adc3949ba59abbe56e057f20f883e', '平台运营', 'platform_admin', NULL, 1, NOW()),
(2, 1, '13800000001', 'e10adc3949ba59abbe56e057f20f883e', '阳光物业管理员', 'property_admin', NULL, 1, NOW()),
(3, 1, '13800000011', 'e10adc3949ba59abbe56e057f20f883e', '网格员小王', 'grid_worker', NULL, 1, NOW()),
(4, 1, '13800000021', 'e10adc3949ba59abbe56e057f20f883e', '居民小明', 'resident', '1栋101', 1, NOW()),
(5, 1, '13800000031', 'e10adc3949ba59abbe56e057f20f883e', '工作人员小李', 'staff', NULL, 1, NOW()),
(6, 2, '13800000002', 'e10adc3949ba59abbe56e057f20f883e', '星河物业管理员', 'property_admin', NULL, 1, NOW()),
(7, 2, '13800000022', 'e10adc3949ba59abbe56e057f20f883e', '星河居民', 'resident', '2栋202', 1, NOW());

INSERT INTO ai_knowledge (tenant_id, question, answer, hit_count, create_time) VALUES
(1, '物业费', '阳光花园物业费可在App-个人服务中心缴纳，每月5日前完成。', 0, NOW()),
(1, '报修电话', '紧急报修请拨打物业热线 400-800-1001，或使用App拍照报修。', 0, NOW()),
(2, '物业费', '星河湾物业费标准请咨询物业前台。', 0, NOW());

INSERT INTO repair_order (tenant_id, user_id, title, description, facility_type, damage_level, status, create_time, update_time) VALUES
(1, 4, '楼道灯不亮', '1栋2单元楼道灯损坏', '照明', '轻度', 'pending', NOW(), NOW()),
(1, 4, '电梯异响', '1号电梯运行异响', '电梯', '中度', 'processing', NOW(), NOW());

INSERT INTO notice_info (tenant_id, title, content, summary, publisher_id, status, publish_time, create_time) VALUES
(1, '停水通知', '本周六上午9-12点进行管网检修，请提前储水。', '本周六停水检修', 2, 1, NOW(), NOW());

INSERT INTO sys_sensitive_word(word) VALUES ('赌博'), ('色情'), ('诈骗');
-- 增量：访客/活动/情绪诉求/失物
USE smart_community;

CREATE TABLE IF NOT EXISTS visitor_appointment (
  id            BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id     BIGINT NOT NULL,
  user_id       BIGINT,
  visitor_name  VARCHAR(64),
  visitor_phone VARCHAR(32),
  id_card       VARCHAR(32),
  visit_reason  VARCHAR(255),
  visit_time    DATETIME,
  visitor_count INT DEFAULT 1,
  status        VARCHAR(32) DEFAULT 'pending',
  remark        VARCHAR(255),
  create_time   DATETIME,
  KEY idx_tenant (tenant_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS community_activity (
  id           BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id    BIGINT NOT NULL,
  title        VARCHAR(128),
  content      TEXT,
  location     VARCHAR(128),
  start_time   DATETIME,
  end_time     DATETIME,
  max_people   INT DEFAULT 50,
  signed_count INT DEFAULT 0,
  publisher_id BIGINT,
  status       TINYINT DEFAULT 1,
  create_time  DATETIME,
  KEY idx_tenant (tenant_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS activity_signup (
  id          BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id   BIGINT NOT NULL,
  activity_id BIGINT NOT NULL,
  user_id     BIGINT,
  user_name   VARCHAR(64),
  phone       VARCHAR(32),
  status      VARCHAR(32) DEFAULT 'signed',
  create_time DATETIME,
  KEY idx_act (activity_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS emotion_appeal (
  id            BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id     BIGINT NOT NULL,
  user_id       BIGINT,
  content       VARCHAR(1000),
  sentiment     VARCHAR(32),
  score         DOUBLE,
  status        VARCHAR(32) DEFAULT 'pending',
  handler_id    BIGINT,
  handle_remark VARCHAR(500),
  create_time   DATETIME,
  KEY idx_tenant (tenant_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS lost_found (
  id          BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id   BIGINT NOT NULL,
  user_id     BIGINT,
  title       VARCHAR(128),
  description VARCHAR(500),
  contact     VARCHAR(64),
  image_url   VARCHAR(255),
  status      VARCHAR(32) DEFAULT 'open',
  create_time DATETIME,
  KEY idx_tenant (tenant_id)
) ENGINE=InnoDB;

INSERT INTO lost_found(tenant_id,user_id,title,description,contact,status,create_time)
SELECT 1,4,'黑色雨伞','在1号楼电梯口捡到黑色雨伞','13800000021','open',NOW()
WHERE NOT EXISTS (SELECT 1 FROM lost_found WHERE tenant_id=1 AND title='黑色雨伞');

