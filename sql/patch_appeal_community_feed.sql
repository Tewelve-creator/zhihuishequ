-- 社区动态：置顶/屏蔽 + 举报工单
USE smart_community;
SET NAMES utf8mb4;

-- emotion_appeal 扩展（兼容无 IF NOT EXISTS 时由服务启动迁移）
-- ALTER TABLE emotion_appeal ADD COLUMN pinned TINYINT NOT NULL DEFAULT 0;
-- ALTER TABLE emotion_appeal ADD COLUMN hidden TINYINT NOT NULL DEFAULT 0;

CREATE TABLE IF NOT EXISTS appeal_report (
  id               BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id        BIGINT NOT NULL,
  reporter_id      BIGINT NOT NULL,
  reporter_name    VARCHAR(64) NULL,
  target_type      VARCHAR(16) NOT NULL COMMENT 'POST/COMMENT',
  target_id        BIGINT NOT NULL,
  appeal_id        BIGINT NULL COMMENT '所属动态',
  reason           VARCHAR(255) NULL,
  content_snapshot VARCHAR(1000) NULL,
  status           VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/DONE/REJECTED',
  handle_result    VARCHAR(500) NULL,
  handler_id       BIGINT NULL,
  handler_name     VARCHAR(64) NULL,
  handle_time      DATETIME NULL,
  create_time      DATETIME DEFAULT CURRENT_TIMESTAMP,
  KEY idx_tenant_status (tenant_id, status),
  KEY idx_target (target_type, target_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='社区动态举报工单';
