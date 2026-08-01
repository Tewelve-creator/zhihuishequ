-- 巡检点位 / 任务 / 打卡 / 隐患
USE smart_community;
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS insp_point (
  id            BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id     BIGINT NOT NULL,
  name          VARCHAR(100) NOT NULL,
  location_desc VARCHAR(255) NULL COMMENT '位置描述',
  address       VARCHAR(255) NULL,
  latitude      DECIMAL(10,7) NULL,
  longitude     DECIMAL(10,7) NULL,
  status        TINYINT NOT NULL DEFAULT 1 COMMENT '1启用 0停用',
  remark        VARCHAR(255) NULL,
  create_time   DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time   DATETIME NULL,
  KEY idx_tenant (tenant_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='巡检点位';

CREATE TABLE IF NOT EXISTS insp_task (
  id              BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id       BIGINT NOT NULL,
  title           VARCHAR(120) NOT NULL,
  assignee_id     BIGINT NOT NULL,
  assignee_name   VARCHAR(64) NULL,
  cycle_type      VARCHAR(16) NOT NULL DEFAULT 'ONCE' COMMENT 'ONCE/DAILY/WEEKLY',
  start_time      DATETIME NULL,
  end_time        DATETIME NULL,
  requirement     VARCHAR(500) NULL,
  remark          VARCHAR(500) NULL,
  point_total     INT NOT NULL DEFAULT 0,
  point_done      INT NOT NULL DEFAULT 0,
  hazard_count    INT NOT NULL DEFAULT 0,
  status          VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/DOING/DONE',
  parent_task_id  BIGINT NULL COMMENT '循环任务来源',
  create_by       BIGINT NULL,
  create_time     DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time     DATETIME NULL,
  KEY idx_tenant_status (tenant_id, status),
  KEY idx_assignee (tenant_id, assignee_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='巡检任务';

CREATE TABLE IF NOT EXISTS insp_task_point (
  id            BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id     BIGINT NOT NULL,
  task_id       BIGINT NOT NULL,
  point_id      BIGINT NOT NULL,
  point_name    VARCHAR(100) NULL,
  location_desc VARCHAR(255) NULL,
  latitude      DECIMAL(10,7) NULL,
  longitude     DECIMAL(10,7) NULL,
  sort_no       INT NOT NULL DEFAULT 0,
  checked       TINYINT NOT NULL DEFAULT 0,
  checkin_id    BIGINT NULL,
  KEY idx_task (task_id),
  KEY idx_tenant_task (tenant_id, task_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='任务-点位关联';

CREATE TABLE IF NOT EXISTS insp_checkin (
  id              BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id       BIGINT NOT NULL,
  task_id         BIGINT NOT NULL,
  task_point_id   BIGINT NOT NULL,
  point_id        BIGINT NOT NULL,
  point_name      VARCHAR(100) NULL,
  user_id         BIGINT NOT NULL,
  user_name       VARCHAR(64) NULL,
  check_time      DATETIME NOT NULL,
  latitude        DECIMAL(10,7) NULL,
  longitude       DECIMAL(10,7) NULL,
  distance_m      DECIMAL(10,2) NULL,
  gps_ok          TINYINT NOT NULL DEFAULT 1,
  photo_urls      VARCHAR(1000) NULL,
  has_hazard      TINYINT NOT NULL DEFAULT 0,
  hazard_desc     VARCHAR(500) NULL,
  hazard_status   VARCHAR(16) NULL COMMENT 'OPEN/FIXED',
  remark          VARCHAR(255) NULL,
  create_time     DATETIME DEFAULT CURRENT_TIMESTAMP,
  KEY idx_task (tenant_id, task_id),
  KEY idx_hazard (tenant_id, has_hazard, hazard_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='巡检打卡记录（永久台账）';

-- 演示点位
INSERT INTO insp_point (tenant_id, name, location_desc, address, latitude, longitude, status, remark)
SELECT 1, '1栋消防通道', '1栋一层消防通道', '阳光花园1栋', 38.8141300, 115.4828900, 1, '演示点位'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM insp_point WHERE tenant_id=1 AND name='1栋消防通道');

INSERT INTO insp_point (tenant_id, name, location_desc, address, latitude, longitude, status, remark)
SELECT 1, '2栋电梯机房', '2栋屋顶电梯机房', '阳光花园2栋', 38.8141600, 115.4829100, 1, '演示点位'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM insp_point WHERE tenant_id=1 AND name='2栋电梯机房');

INSERT INTO insp_point (tenant_id, name, location_desc, address, latitude, longitude, status, remark)
SELECT 1, '地下车库出入口', '地库A出口', '阳光花园地库', 38.8141900, 115.4829300, 1, '演示点位'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM insp_point WHERE tenant_id=1 AND name='地下车库出入口');

INSERT INTO insp_point (tenant_id, name, location_desc, address, latitude, longitude, status, remark)
SELECT 1, '中心花园消防栓', '中心花园东侧', '阳光花园中心花园', 38.8142200, 115.4829500, 1, '演示点位'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM insp_point WHERE tenant_id=1 AND name='中心花园消防栓');

-- 若点位已存在，强制刷新为演示坐标（保定联调）
UPDATE insp_point SET latitude=38.8141300, longitude=115.4828900, update_time=NOW() WHERE tenant_id=1 AND name='1栋消防通道';
UPDATE insp_point SET latitude=38.8141600, longitude=115.4829100, update_time=NOW() WHERE tenant_id=1 AND name='2栋电梯机房';
UPDATE insp_point SET latitude=38.8141900, longitude=115.4829300, update_time=NOW() WHERE tenant_id=1 AND name='地下车库出入口';
UPDATE insp_point SET latitude=38.8142200, longitude=115.4829500, update_time=NOW() WHERE tenant_id=1 AND name='中心花园消防栓';
