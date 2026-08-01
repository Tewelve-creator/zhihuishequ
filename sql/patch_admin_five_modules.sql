-- 物业后台五模块配套表结构（推荐用 python sql/apply_admin_five_modules.py 执行）
-- 见 apply_admin_five_modules.py
USE smart_community;
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS ai_kb_document (
  id            BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id     BIGINT NOT NULL,
  title         VARCHAR(128) NOT NULL,
  file_name     VARCHAR(255) NOT NULL,
  content       MEDIUMTEXT NOT NULL,
  char_count    INT DEFAULT 0,
  index_status  VARCHAR(32) DEFAULT 'NONE' COMMENT 'NONE/BUILDING/READY/FAILED',
  chunk_count   INT DEFAULT 0,
  uploader_id   BIGINT NULL,
  create_time   DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time   DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS ai_kb_chunk (
  id          BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id   BIGINT NOT NULL,
  doc_id      BIGINT NOT NULL,
  chunk_no    INT NOT NULL,
  content     TEXT NOT NULL,
  token_est   INT DEFAULT 0,
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  KEY idx_doc (doc_id),
  KEY idx_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS ai_kb_index_log (
  id          BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id   BIGINT NOT NULL,
  doc_id      BIGINT NOT NULL,
  step        VARCHAR(64) NOT NULL,
  message     VARCHAR(500) NULL,
  success     TINYINT DEFAULT 1,
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  KEY idx_doc (doc_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
