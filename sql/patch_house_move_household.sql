-- 迁入迁出审批 + 住户档案（与房屋绑定打通）
USE smart_community;

CREATE TABLE IF NOT EXISTS house_move_apply (
  id               BIGINT PRIMARY KEY AUTO_INCREMENT,
  apply_no         VARCHAR(32) NOT NULL COMMENT '申请单号',
  tenant_id        BIGINT NOT NULL,
  user_id          BIGINT NOT NULL COMMENT '申请人用户ID',
  house_id         BIGINT NOT NULL,
  apply_type       VARCHAR(16) NOT NULL COMMENT 'MOVE_IN/MOVE_OUT',
  identity_type    VARCHAR(16) DEFAULT 'OWNER' COMMENT 'OWNER业主/TENANT租客',
  applicant_name   VARCHAR(64),
  applicant_phone  VARCHAR(20),
  house_label      VARCHAR(255) COMMENT '房屋展示文案',
  status           VARCHAR(16) DEFAULT 'PENDING' COMMENT 'PENDING/APPROVED/REJECTED',
  remark           VARCHAR(500) COMMENT '申请备注',
  audit_user_id    BIGINT NULL,
  audit_name       VARCHAR(64) NULL,
  audit_remark     VARCHAR(500) NULL,
  audit_time       DATETIME NULL,
  create_time      DATETIME DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_apply_no (apply_no),
  KEY idx_tenant_status (tenant_id, status),
  KEY idx_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='迁入迁出审批';

CREATE TABLE IF NOT EXISTS household_resident (
  id            BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id     BIGINT NOT NULL,
  user_id       BIGINT NULL COMMENT '关联App用户，可空（物业手录）',
  house_id      BIGINT NOT NULL,
  name          VARCHAR(64) NOT NULL,
  phone         VARCHAR(20) NOT NULL,
  identity_type VARCHAR(16) DEFAULT 'OWNER' COMMENT 'OWNER/TENANT',
  status        VARCHAR(16) DEFAULT 'ACTIVE' COMMENT 'ACTIVE正常/MOVING_OUT迁出中/MOVED_OUT已迁出',
  source        VARCHAR(16) DEFAULT 'APPLY' COMMENT 'APPLY审批同步/MANUAL手录',
  house_label   VARCHAR(255),
  create_time   DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time   DATETIME NULL,
  UNIQUE KEY uk_tenant_house_phone (tenant_id, house_id, phone),
  KEY idx_tenant_status (tenant_id, status),
  KEY idx_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='住户管理档案';

-- 将已有绑定同步为住户（演示居民）
INSERT INTO household_resident (tenant_id, user_id, house_id, name, phone, identity_type, status, source, house_label, create_time, update_time)
SELECT b.tenant_id, b.user_id, b.house_id,
       IFNULL(u.nickname, '居民'), u.phone COLLATE utf8mb4_unicode_ci, 'OWNER', 'ACTIVE', 'APPLY',
       CONCAT(h.community_name, ' · ', h.building, h.unit, h.room_no),
       NOW(), NOW()
FROM user_house_bind b
JOIN sys_user u ON u.id = b.user_id
JOIN community_house h ON h.id = b.house_id
WHERE NOT EXISTS (
  SELECT 1 FROM household_resident r
  WHERE r.user_id = b.user_id AND r.house_id = b.house_id
);
