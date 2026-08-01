-- 增量：访客/活动/情绪诉求/失物
USE smart_community;

CREATE TABLE IF NOT EXISTS visitor_appointment (
  id            BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id     BIGINT NOT NULL,
  user_id       BIGINT,
  house_id      BIGINT,
  visitor_name  VARCHAR(64),
  visitor_phone VARCHAR(32),
  plate_no      VARCHAR(20),
  drive_flag    TINYINT NOT NULL DEFAULT 0 COMMENT '1驾车来访',
  id_card       VARCHAR(32) COMMENT '已废弃，不再采集',
  visit_reason  VARCHAR(255),
  visit_time    DATETIME,
  expire_time   DATETIME COMMENT '通行有效截止',
  visitor_count INT DEFAULT 1,
  status        VARCHAR(32) DEFAULT 'approved' COMMENT 'approved/checked_in/expired/cancelled',
  checkin_time  DATETIME,
  checkin_by    BIGINT,
  remark        VARCHAR(255),
  create_time   DATETIME,
  KEY idx_tenant (tenant_id),
  KEY idx_tenant_status (tenant_id, status)
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
