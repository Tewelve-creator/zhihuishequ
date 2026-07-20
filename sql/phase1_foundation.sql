-- ============================================================
-- 第一阶段地基：认证 / 租户 / RBAC（不含业务、AI、租赁订单）
-- 库名：smart_community
-- ============================================================
CREATE DATABASE IF NOT EXISTS smart_community DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE smart_community;

-- ---------- 租户（社区） ----------
DROP TABLE IF EXISTS sys_user_role;
DROP TABLE IF EXISTS sys_role_menu;
DROP TABLE IF EXISTS sys_menu;
DROP TABLE IF EXISTS sys_role;
DROP TABLE IF EXISTS sys_user;
DROP TABLE IF EXISTS sys_tenant;

CREATE TABLE sys_tenant (
  id            BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '租户ID',
  name          VARCHAR(128) NOT NULL COMMENT '社区名称',
  address       VARCHAR(255) DEFAULT NULL COMMENT '地址',
  contact_name  VARCHAR(64)  DEFAULT NULL COMMENT '联系人',
  contact_phone VARCHAR(32)  DEFAULT NULL COMMENT '联系电话',
  status        VARCHAR(32)  NOT NULL DEFAULT 'active' COMMENT 'active/disabled',
  remark        VARCHAR(255) DEFAULT NULL,
  create_time   DATETIME     DEFAULT CURRENT_TIMESTAMP,
  update_time   DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted       TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0正常 1删除'
) ENGINE=InnoDB COMMENT='租户社区';

-- ---------- 用户 ----------
CREATE TABLE sys_user (
  id            BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id     BIGINT       DEFAULT NULL COMMENT '所属租户，运营账号可为空',
  phone         VARCHAR(20)  NOT NULL,
  password      VARCHAR(64)  NOT NULL COMMENT 'MD5',
  nickname      VARCHAR(64)  DEFAULT NULL,
  avatar        VARCHAR(255) DEFAULT NULL,
  role          VARCHAR(32)  NOT NULL COMMENT '主角色码：resident/property_admin/grid_worker/platform_admin',
  room_no       VARCHAR(64)  DEFAULT NULL COMMENT '房号（居民）',
  status        TINYINT      NOT NULL DEFAULT 1 COMMENT '1启用 0禁用',
  create_time   DATETIME     DEFAULT CURRENT_TIMESTAMP,
  update_time   DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted       TINYINT      NOT NULL DEFAULT 0,
  UNIQUE KEY uk_phone_tenant (phone, tenant_id),
  KEY idx_tenant (tenant_id),
  KEY idx_role (role)
) ENGINE=InnoDB COMMENT='系统用户';

-- ---------- 角色 ----------
CREATE TABLE sys_role (
  id          BIGINT PRIMARY KEY AUTO_INCREMENT,
  role_code   VARCHAR(32)  NOT NULL COMMENT '角色编码',
  role_name   VARCHAR(64)  NOT NULL COMMENT '角色名称',
  remark      VARCHAR(255) DEFAULT NULL,
  status      TINYINT      NOT NULL DEFAULT 1,
  create_time DATETIME     DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_role_code (role_code)
) ENGINE=InnoDB COMMENT='角色（四类固定角色）';

-- ---------- 菜单 / 权限点 ----------
CREATE TABLE sys_menu (
  id          BIGINT PRIMARY KEY AUTO_INCREMENT,
  parent_id   BIGINT       NOT NULL DEFAULT 0,
  menu_name   VARCHAR(64)  NOT NULL,
  menu_type   CHAR(1)      NOT NULL COMMENT 'M目录 C菜单 F按钮/接口',
  path        VARCHAR(128) DEFAULT NULL COMMENT '路由 path',
  component   VARCHAR(128) DEFAULT NULL,
  perms       VARCHAR(128) DEFAULT NULL COMMENT '权限标识，如 auth:user:list',
  icon        VARCHAR(64)  DEFAULT NULL,
  client_type VARCHAR(16)  NOT NULL DEFAULT 'ADMIN' COMMENT 'APP/ADMIN',
  sort_no     INT          NOT NULL DEFAULT 0,
  visible     TINYINT      NOT NULL DEFAULT 1,
  status      TINYINT      NOT NULL DEFAULT 1,
  create_time DATETIME     DEFAULT CURRENT_TIMESTAMP,
  KEY idx_parent (parent_id),
  KEY idx_perms (perms)
) ENGINE=InnoDB COMMENT='菜单与接口权限';

CREATE TABLE sys_role_menu (
  id      BIGINT PRIMARY KEY AUTO_INCREMENT,
  role_id BIGINT NOT NULL,
  menu_id BIGINT NOT NULL,
  UNIQUE KEY uk_role_menu (role_id, menu_id)
) ENGINE=InnoDB COMMENT='角色-菜单';

CREATE TABLE sys_user_role (
  id      BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  role_id BIGINT NOT NULL,
  UNIQUE KEY uk_user_role (user_id, role_id)
) ENGINE=InnoDB COMMENT='用户-角色';

-- ==================== 种子数据 ====================
INSERT INTO sys_role (id, role_code, role_name, remark) VALUES
(1, 'platform_admin', 'SaaS运营管理员', '可跨租户查看全平台数据'),
(2, 'property_admin', '物业管理员', '本租户管理'),
(3, 'grid_worker',    '社区网格员', '本租户协助管理'),
(4, 'resident',       '居民', '本租户居民');

INSERT INTO sys_tenant (id, name, address, contact_name, contact_phone, status, remark) VALUES
(1, '阳光花园社区', '示范市阳光路1号', '张管家', '13800000001', 'active', '租户1'),
(2, '星河湾社区',   '示范市星河路8号', '李主任', '13800000002', 'active', '租户2');

-- 密码均为 123456 → MD5 e10adc3949ba59abbe56e057f20f883e
INSERT INTO sys_user (id, tenant_id, phone, password, nickname, role, room_no, status) VALUES
(1, NULL, '13900000000', 'e10adc3949ba59abbe56e057f20f883e', '平台运营', 'platform_admin', NULL, 1),
(2, 1,    '13800000001', 'e10adc3949ba59abbe56e057f20f883e', '阳光物业管理员', 'property_admin', NULL, 1),
(3, 1,    '13800000011', 'e10adc3949ba59abbe56e057f20f883e', '网格员小王', 'grid_worker', NULL, 1),
(4, 1,    '13800000021', 'e10adc3949ba59abbe56e057f20f883e', '居民小明', 'resident', '1栋101', 1),
(5, 2,    '13800000002', 'e10adc3949ba59abbe56e057f20f883e', '星河物业管理员', 'property_admin', NULL, 1),
(6, 2,    '13800000022', 'e10adc3949ba59abbe56e057f20f883e', '星河居民', 'resident', '2栋202', 1);

INSERT INTO sys_user_role (user_id, role_id) VALUES
(1, 1), (2, 2), (3, 3), (4, 4), (5, 2), (6, 4);

-- 菜单：管理后台
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, perms, icon, client_type, sort_no) VALUES
(1,  0, '系统管理', 'M', '/system', NULL, NULL, 'setting', 'ADMIN', 1),
(2,  1, '用户管理', 'C', 'user', 'system/user', 'auth:user:list', 'user', 'ADMIN', 1),
(3,  2, '用户查询', 'F', NULL, NULL, 'auth:user:query', NULL, 'ADMIN', 1),
(4,  2, '用户新增', 'F', NULL, NULL, 'auth:user:add', NULL, 'ADMIN', 2),
(5,  2, '用户启停', 'F', NULL, NULL, 'auth:user:status', NULL, 'ADMIN', 3),
(10, 0, '租户管理', 'M', '/tenant', NULL, NULL, 'office', 'ADMIN', 2),
(11, 10,'租户列表', 'C', 'list', 'tenant/list', 'auth:tenant:list', 'list', 'ADMIN', 1),
(12, 11,'租户查询', 'F', NULL, NULL, 'auth:tenant:query', NULL, 'ADMIN', 1),
(13, 11,'租户新增', 'F', NULL, NULL, 'auth:tenant:add', NULL, 'ADMIN', 2),
(14, 11,'租户修改', 'F', NULL, NULL, 'auth:tenant:edit', NULL, 'ADMIN', 3),
(15, 11,'租户删除', 'F', NULL, NULL, 'auth:tenant:remove', NULL, 'ADMIN', 4),
(20, 0, '权限管理', 'M', '/rbac', NULL, NULL, 'lock', 'ADMIN', 3),
(21, 20,'角色管理', 'C', 'role', 'rbac/role', 'auth:role:list', 'peoples', 'ADMIN', 1),
(22, 20,'菜单管理', 'C', 'menu', 'rbac/menu', 'auth:menu:list', 'tree', 'ADMIN', 2),
(23, 20,'在线会话', 'C', 'session', 'rbac/session', 'auth:session:list', 'online', 'ADMIN', 3),
(30, 0, '工作台',   'C', '/dashboard', 'dashboard', 'auth:dashboard:view', 'dashboard', 'ADMIN', 0);

-- 菜单：App
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, perms, icon, client_type, sort_no) VALUES
(100, 0, '首页', 'C', '/pages/home/index', NULL, 'app:home:view', 'home', 'APP', 1),
(101, 0, '我的', 'C', '/pages/mine/index', NULL, 'app:mine:view', 'user', 'APP', 2);

-- 运营：全部后台菜单
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, id FROM sys_menu WHERE client_type = 'ADMIN';

-- 物业：工作台 + 用户管理（本租户）+ App
INSERT INTO sys_role_menu (role_id, menu_id) VALUES
(2, 30), (2, 1), (2, 2), (2, 3), (2, 4), (2, 5), (2, 100), (2, 101);

-- 网格员：工作台 + 用户查询 + App
INSERT INTO sys_role_menu (role_id, menu_id) VALUES
(3, 30), (3, 1), (3, 2), (3, 3), (3, 100), (3, 101);

-- 居民：仅 App 菜单
INSERT INTO sys_role_menu (role_id, menu_id) VALUES
(4, 100), (4, 101);
