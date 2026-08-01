-- 房屋主数据 + 用户房屋绑定（居民端房屋绑定功能）
USE smart_community;

CREATE TABLE IF NOT EXISTS community_house (
  id              BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id       BIGINT NOT NULL COMMENT '租户/社区ID',
  community_name  VARCHAR(128) NOT NULL COMMENT '社区名称',
  building        VARCHAR(64) NOT NULL COMMENT '楼栋',
  unit            VARCHAR(64) NOT NULL COMMENT '单元',
  room_no         VARCHAR(64) NOT NULL COMMENT '房号',
  status          TINYINT DEFAULT 1 COMMENT '1可用 0停用',
  create_time     DATETIME DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_tenant_addr (tenant_id, building, unit, room_no),
  KEY idx_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='社区房屋档案';

CREATE TABLE IF NOT EXISTS user_house_bind (
  id          BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id   BIGINT NOT NULL,
  user_id     BIGINT NOT NULL,
  house_id    BIGINT NOT NULL,
  is_default  TINYINT DEFAULT 0 COMMENT '1默认房屋',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_user_house (user_id, house_id),
  KEY idx_user (user_id),
  KEY idx_tenant_user (tenant_id, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='居民房屋绑定';

-- 阳光花园（tenant_id=1）演示房源
INSERT INTO community_house (tenant_id, community_name, building, unit, room_no, status, create_time)
SELECT 1, '阳光花园社区', '1栋', '1单元', '101', 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM community_house WHERE tenant_id=1 AND building='1栋' AND unit='1单元' AND room_no='101');
INSERT INTO community_house (tenant_id, community_name, building, unit, room_no, status, create_time)
SELECT 1, '阳光花园社区', '1栋', '1单元', '102', 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM community_house WHERE tenant_id=1 AND building='1栋' AND unit='1单元' AND room_no='102');
INSERT INTO community_house (tenant_id, community_name, building, unit, room_no, status, create_time)
SELECT 1, '阳光花园社区', '1栋', '2单元', '201', 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM community_house WHERE tenant_id=1 AND building='1栋' AND unit='2单元' AND room_no='201');
INSERT INTO community_house (tenant_id, community_name, building, unit, room_no, status, create_time)
SELECT 1, '阳光花园社区', '2栋', '1单元', '101', 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM community_house WHERE tenant_id=1 AND building='2栋' AND unit='1单元' AND room_no='101');
INSERT INTO community_house (tenant_id, community_name, building, unit, room_no, status, create_time)
SELECT 1, '阳光花园社区', '2栋', '1单元', '202', 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM community_house WHERE tenant_id=1 AND building='2栋' AND unit='1单元' AND room_no='202');
INSERT INTO community_house (tenant_id, community_name, building, unit, room_no, status, create_time)
SELECT 1, '阳光花园社区', '3栋', '1单元', '301', 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM community_house WHERE tenant_id=1 AND building='3栋' AND unit='1单元' AND room_no='301');

-- 星河湾（tenant_id=2）
INSERT INTO community_house (tenant_id, community_name, building, unit, room_no, status, create_time)
SELECT 2, '星河湾社区', 'A栋', '1单元', '101', 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM community_house WHERE tenant_id=2 AND building='A栋' AND unit='1单元' AND room_no='101');
INSERT INTO community_house (tenant_id, community_name, building, unit, room_no, status, create_time)
SELECT 2, '星河湾社区', 'A栋', '2单元', '201', 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM community_house WHERE tenant_id=2 AND building='A栋' AND unit='2单元' AND room_no='201');
INSERT INTO community_house (tenant_id, community_name, building, unit, room_no, status, create_time)
SELECT 2, '星河湾社区', 'B栋', '1单元', '501', 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM community_house WHERE tenant_id=2 AND building='B栋' AND unit='1单元' AND room_no='501');

-- 演示居民 13800000021（若存在）默认绑定 1栋1单元101
INSERT INTO user_house_bind (tenant_id, user_id, house_id, is_default, create_time)
SELECT u.tenant_id, u.id, h.id, 1, NOW()
FROM sys_user u
JOIN community_house h ON h.tenant_id = u.tenant_id AND h.building='1栋' AND h.unit='1单元' AND h.room_no='101'
WHERE u.phone='13800000021' AND u.role='resident'
  AND NOT EXISTS (SELECT 1 FROM user_house_bind b WHERE b.user_id=u.id AND b.house_id=h.id);
