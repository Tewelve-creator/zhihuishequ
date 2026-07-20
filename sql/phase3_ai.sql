-- ============================================================
-- 第三阶段：community-ai AI 特色功能建表 / 增强
-- ============================================================
CREATE DATABASE IF NOT EXISTS smart_community DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE smart_community;

-- 租户知识库（行级 tenant_id 隔离）
CREATE TABLE IF NOT EXISTS ai_knowledge (
  id          BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id   BIGINT NOT NULL COMMENT '租户隔离',
  category    VARCHAR(32) DEFAULT 'general' COMMENT '分类',
  question    VARCHAR(255) NOT NULL,
  answer      TEXT NOT NULL,
  hit_count   INT DEFAULT 0,
  status      TINYINT DEFAULT 1 COMMENT '1启用 0停用',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_tenant (tenant_id),
  KEY idx_q (tenant_id, question)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 兼容旧表：补列（已存在则忽略报错）
ALTER TABLE ai_knowledge ADD COLUMN category VARCHAR(32) DEFAULT 'general' AFTER tenant_id;
ALTER TABLE ai_knowledge ADD COLUMN status TINYINT DEFAULT 1 AFTER hit_count;
ALTER TABLE ai_knowledge ADD COLUMN update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP AFTER create_time;

-- AI 对话日志
CREATE TABLE IF NOT EXISTS ai_chat_log (
  id          BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id   BIGINT NOT NULL,
  user_id     BIGINT NULL,
  question    VARCHAR(1000) NOT NULL,
  answer      TEXT,
  source      VARCHAR(32) COMMENT 'tenant_kb/mock_llm/llm_http',
  transfer_human TINYINT DEFAULT 0,
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  KEY idx_tenant_time (tenant_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- AI 调用计数日志（Sentinel/额度审计）
CREATE TABLE IF NOT EXISTS ai_call_log (
  id          BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id   BIGINT NOT NULL,
  user_id     BIGINT NULL,
  scene       VARCHAR(32) NOT NULL COMMENT 'chat/image/emotion/audit/lost/summary',
  success     TINYINT DEFAULT 1,
  cost_ms     INT DEFAULT 0,
  remark      VARCHAR(255),
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  KEY idx_tenant_scene (tenant_id, scene, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 公告摘要 / 敏感词审核日志
CREATE TABLE IF NOT EXISTS ai_audit_log (
  id          BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id   BIGINT NULL,
  biz_type    VARCHAR(32) DEFAULT 'notice',
  content     TEXT,
  summary     VARCHAR(512),
  pass_flag   TINYINT DEFAULT 1,
  hit_words   VARCHAR(512),
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  KEY idx_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 便民日程提醒
CREATE TABLE IF NOT EXISTS ai_schedule_remind (
  id           BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id    BIGINT NOT NULL,
  user_id      BIGINT NOT NULL,
  title        VARCHAR(128) NOT NULL,
  content      VARCHAR(500),
  remind_time  DATETIME NOT NULL,
  status       VARCHAR(16) DEFAULT 'pending' COMMENT 'pending/sent/cancelled',
  create_time  DATETIME DEFAULT CURRENT_TIMESTAMP,
  sent_time    DATETIME NULL,
  KEY idx_remind (status, remind_time),
  KEY idx_tenant_user (tenant_id, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 失物（若不存在）
CREATE TABLE IF NOT EXISTS lost_found (
  id          BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id   BIGINT NOT NULL,
  user_id     BIGINT,
  title       VARCHAR(128),
  description VARCHAR(1000),
  contact     VARCHAR(64),
  image_url   VARCHAR(512),
  status      VARCHAR(16) DEFAULT 'open',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  KEY idx_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 情绪诉求（若不存在）
CREATE TABLE IF NOT EXISTS emotion_appeal (
  id          BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id   BIGINT NOT NULL,
  user_id     BIGINT NOT NULL,
  content     VARCHAR(1000),
  sentiment   VARCHAR(32),
  score       DECIMAL(6,3),
  status      VARCHAR(16) DEFAULT 'pending',
  grid_user_id BIGINT NULL COMMENT '指派网格员',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  KEY idx_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS sys_sensitive_word (
  id   BIGINT PRIMARY KEY AUTO_INCREMENT,
  word VARCHAR(64) NOT NULL,
  UNIQUE KEY uk_word (word)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS sys_message (
  id          BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id   BIGINT,
  user_id     BIGINT NOT NULL,
  title       VARCHAR(128),
  content     VARCHAR(1000),
  msg_type    VARCHAR(32) DEFAULT 'system',
  read_flag   TINYINT DEFAULT 0,
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  KEY idx_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 种子：敏感词
INSERT IGNORE INTO sys_sensitive_word(word) VALUES
('赌博'),('色情'),('诈骗'),('传销'),('反动'),('暴力');

-- 种子：租户知识库（隔离演示）
INSERT INTO ai_knowledge (tenant_id, category, question, answer, hit_count, status, create_time)
SELECT 1, 'fee', '物业费', '阳光花园物业费可在 App-缴费 中缴纳，每月5日前完成。', 0, 1, NOW()
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ai_knowledge WHERE tenant_id=1 AND question='物业费');

INSERT INTO ai_knowledge (tenant_id, category, question, answer, hit_count, status, create_time)
SELECT 1, 'repair', '报修电话', '紧急报修请拨打 400-800-1001，或使用 App 拍照报修。', 0, 1, NOW()
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ai_knowledge WHERE tenant_id=1 AND question='报修电话');

INSERT INTO ai_knowledge (tenant_id, category, question, answer, hit_count, status, create_time)
SELECT 1, 'visit', '访客预约', '访客需提前在 App 提交预约，物业审核后生成通行码。', 0, 1, NOW()
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ai_knowledge WHERE tenant_id=1 AND question='访客预约');

INSERT INTO ai_knowledge (tenant_id, category, question, answer, hit_count, status, create_time)
SELECT 2, 'fee', '物业费', '星河湾物业费标准请咨询物业前台或查看缴费页。', 0, 1, NOW()
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ai_knowledge WHERE tenant_id=2 AND question='物业费');

-- 失物演示数据
INSERT INTO lost_found (tenant_id, user_id, title, description, contact, image_url, status, create_time)
SELECT 1, 4, '黑色钥匙串', '在1栋电梯口捡到黑色钥匙串，挂有小区门禁卡', '13800000021', '/upload/lost/key1.jpg', 'open', NOW()
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM lost_found WHERE tenant_id=1 AND title='黑色钥匙串');

INSERT INTO lost_found (tenant_id, user_id, title, description, contact, image_url, status, create_time)
SELECT 1, 4, '蓝色水杯', '儿童蓝色保温杯，杯身有贴纸', '13800000021', '/upload/lost/cup1.jpg', 'open', NOW()
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM lost_found WHERE tenant_id=1 AND title='蓝色水杯');
