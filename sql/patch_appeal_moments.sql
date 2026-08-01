-- 邻里倾诉 · 朋友圈改造：扩展 emotion_appeal + 评论/点赞/禁言/操作日志
-- 可重复执行（ADD COLUMN IF 用存储过程风格用忽略错误 / 或手动执行）

-- 1) 扩展 emotion_appeal（朋友圈动态字段）
ALTER TABLE emotion_appeal
  ADD COLUMN IF NOT EXISTS house_id BIGINT NULL COMMENT '发布房屋' AFTER user_id,
  ADD COLUMN IF NOT EXISTS house_label VARCHAR(128) NULL COMMENT '楼栋房号展示' AFTER house_id,
  ADD COLUMN IF NOT EXISTS nickname VARCHAR(64) NULL COMMENT '发布时昵称' AFTER house_label,
  ADD COLUMN IF NOT EXISTS image_urls VARCHAR(2000) NULL COMMENT '图片JSON数组1~9张' AFTER content,
  ADD COLUMN IF NOT EXISTS like_count INT NOT NULL DEFAULT 0 AFTER image_urls,
  ADD COLUMN IF NOT EXISTS comment_count INT NOT NULL DEFAULT 0 AFTER like_count,
  ADD COLUMN IF NOT EXISTS deleted TINYINT NOT NULL DEFAULT 0 COMMENT '0正常1已删除' AFTER comment_count,
  ADD COLUMN IF NOT EXISTS delete_reason VARCHAR(255) NULL AFTER deleted,
  ADD COLUMN IF NOT EXISTS deleted_by BIGINT NULL AFTER delete_reason,
  ADD COLUMN IF NOT EXISTS deleted_time DATETIME NULL AFTER deleted_by;

-- MySQL 5.7 无 IF NOT EXISTS 时用下面兼容脚本（python 补丁也可）
-- 若上面报错，请改用 sql/apply_appeal_moments.py

CREATE TABLE IF NOT EXISTS appeal_comment (
  id            BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id     BIGINT NOT NULL,
  appeal_id     BIGINT NOT NULL,
  user_id       BIGINT NOT NULL,
  nickname      VARCHAR(64) NULL,
  house_label   VARCHAR(128) NULL,
  content       VARCHAR(500) NOT NULL,
  deleted       TINYINT NOT NULL DEFAULT 0,
  deleted_by    BIGINT NULL,
  deleted_time  DATETIME NULL,
  create_time   DATETIME,
  KEY idx_appeal (appeal_id),
  KEY idx_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS appeal_like (
  id            BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id     BIGINT NOT NULL,
  appeal_id     BIGINT NOT NULL,
  user_id       BIGINT NOT NULL,
  create_time   DATETIME,
  UNIQUE KEY uk_appeal_user (appeal_id, user_id),
  KEY idx_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS appeal_user_mute (
  id            BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id     BIGINT NOT NULL,
  user_id       BIGINT NOT NULL,
  mute_until    DATETIME NULL COMMENT 'NULL=永久禁言',
  reason        VARCHAR(255) NULL,
  operator_id   BIGINT NULL,
  operator_name VARCHAR(64) NULL,
  status        VARCHAR(16) NOT NULL DEFAULT 'active' COMMENT 'active/released',
  create_time   DATETIME,
  update_time   DATETIME,
  KEY idx_tenant_user (tenant_id, user_id),
  KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS appeal_op_log (
  id               BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id        BIGINT NOT NULL,
  operator_id      BIGINT NULL,
  operator_name    VARCHAR(64) NULL,
  action           VARCHAR(32) NOT NULL COMMENT 'DELETE_POST/DELETE_COMMENT/MUTE/UNMUTE',
  target_user_id   BIGINT NULL,
  target_appeal_id BIGINT NULL,
  target_comment_id BIGINT NULL,
  detail           VARCHAR(500) NULL,
  create_time      DATETIME,
  KEY idx_tenant (tenant_id),
  KEY idx_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
