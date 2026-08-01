-- AI知识库四模块：分类 / 设置 / 问答评价
USE smart_community;
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS knowledge_category (
  id          BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id   BIGINT NOT NULL,
  code        VARCHAR(32) NOT NULL COMMENT '分类编码，文档 category 字段',
  name        VARCHAR(64) NOT NULL,
  description VARCHAR(255) NULL,
  sort_no     INT NOT NULL DEFAULT 0,
  enabled     TINYINT NOT NULL DEFAULT 1,
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_tenant_code (tenant_id, code),
  KEY idx_tenant_sort (tenant_id, sort_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识库分类';

CREATE TABLE IF NOT EXISTS knowledge_settings (
  id                    BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id             BIGINT NOT NULL,
  min_score             DOUBLE NOT NULL DEFAULT 0.32 COMMENT 'RAG 最低相似度',
  top_k                 INT NOT NULL DEFAULT 3 COMMENT '检索条数',
  system_prompt         TEXT NULL COMMENT '系统提示词',
  enable_unofficial_tip TINYINT NOT NULL DEFAULT 1 COMMENT '无知识库命中时追加温馨提示',
  prefer_document       TINYINT NOT NULL DEFAULT 1 COMMENT '同分时优先文档命中',
  reply_strategy        VARCHAR(32) NOT NULL DEFAULT 'rag_first' COMMENT 'rag_first|llm_first|kb_only',
  update_time           DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识库应答设置';

-- 问答评价字段（已存在则跳过，由服务 ensure 兜底）
-- ALTER TABLE ai_chat_log ADD COLUMN rating TINYINT NULL COMMENT '1好评 2差评';
-- ALTER TABLE ai_chat_log ADD COLUMN rating_remark VARCHAR(255) NULL;
-- ALTER TABLE ai_chat_log ADD COLUMN user_nickname VARCHAR(64) NULL;
