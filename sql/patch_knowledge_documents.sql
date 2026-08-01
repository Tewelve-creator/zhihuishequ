-- 知识库文档表：policy 制度 / community 小区规则
USE smart_community;
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS knowledge_documents (
  id            BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id     BIGINT NOT NULL,
  category      VARCHAR(32) NOT NULL DEFAULT 'policy' COMMENT 'policy制度/community小区规则',
  title         VARCHAR(128) NOT NULL,
  file_name     VARCHAR(255) NOT NULL,
  content       MEDIUMTEXT NOT NULL,
  char_count    INT DEFAULT 0,
  index_status  VARCHAR(32) DEFAULT 'NONE' COMMENT 'NONE/BUILDING/READY/FAILED',
  chunk_count   INT DEFAULT 0,
  uploader_id   BIGINT NULL,
  create_time   DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time   DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_tenant_cat (tenant_id, category),
  KEY idx_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS knowledge_chunks (
  id              BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id       BIGINT NOT NULL,
  doc_id          BIGINT NOT NULL,
  chunk_no        INT NOT NULL,
  content         TEXT NOT NULL,
  token_est       INT DEFAULT 0,
  embedding_blob  LONGBLOB NULL COMMENT '向量BLOB，余弦检索',
  embedding_dim   INT DEFAULT 0,
  create_time     DATETIME DEFAULT CURRENT_TIMESTAMP,
  KEY idx_doc (doc_id),
  KEY idx_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS knowledge_index_log (
  id          BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id   BIGINT NOT NULL,
  doc_id      BIGINT NOT NULL,
  step        VARCHAR(64) NOT NULL,
  message     VARCHAR(500) NULL,
  success     TINYINT DEFAULT 1,
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  KEY idx_doc (doc_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
