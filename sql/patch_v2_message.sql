-- 增量补丁：已有库执行本文件即可（勿重复执行 schema 全量 INSERT）
USE smart_community;

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

INSERT INTO sys_sensitive_word(word)
SELECT * FROM (SELECT '赌博' AS w UNION SELECT '色情' UNION SELECT '诈骗') t
WHERE NOT EXISTS (SELECT 1 FROM sys_sensitive_word s WHERE s.word=t.w);
