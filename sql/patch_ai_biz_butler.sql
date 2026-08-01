-- AI 智能管家业务操作日志（可溯源）
USE smart_community;
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS ai_biz_op_log (
  id           BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id    BIGINT NOT NULL,
  user_id      BIGINT NOT NULL,
  house_id     BIGINT NULL,
  intent       VARCHAR(64) NOT NULL COMMENT 'VISIT_CREATE/REPAIR_CREATE/FEE_UNPAID/CAR_LIST/VISIT_LIST',
  action_token VARCHAR(64) NULL,
  slots_json   TEXT COMMENT '确认时的业务参数快照',
  result_json  TEXT COMMENT '执行结果摘要',
  status       VARCHAR(32) NOT NULL DEFAULT 'CONFIRMED' COMMENT 'CONFIRMED/CANCELLED/FAILED',
  source       VARCHAR(32) DEFAULT 'ai_butler',
  create_time  DATETIME DEFAULT CURRENT_TIMESTAMP,
  KEY idx_tenant_user (tenant_id, user_id),
  KEY idx_tenant_time (tenant_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
