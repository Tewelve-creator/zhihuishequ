-- 车辆停车管理（业主固定车 / 访客临停 / 车位 / 通行 / 收费）
USE smart_community;
SET NAMES utf8mb4;

-- ========== 1. 车辆表 ==========
CREATE TABLE IF NOT EXISTS park_vehicle (
  id              BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id       BIGINT       NOT NULL COMMENT '租户/社区ID',
  plate_no        VARCHAR(20)  NOT NULL COMMENT '车牌号（归一化大写）',
  vehicle_type    VARCHAR(16)  NOT NULL DEFAULT 'OWNER' COMMENT 'OWNER业主固定/VISITOR访客/GREEN特种绿通',
  list_type       VARCHAR(16)  NOT NULL DEFAULT 'WHITE' COMMENT 'WHITE白名单/BLACK黑名单/GREEN绿通',
  house_id        BIGINT       NULL COMMENT '绑定房屋ID',
  owner_user_id   BIGINT       NULL COMMENT '车主用户ID',
  owner_name      VARCHAR(64)  NULL COMMENT '车主姓名',
  owner_phone     VARCHAR(20)  NULL COMMENT '车主手机（挪车脱敏用）',
  space_id        BIGINT       NULL COMMENT '绑定固定车位ID',
  brand_model     VARCHAR(64)  NULL COMMENT '品牌型号',
  color           VARCHAR(32)  NULL COMMENT '颜色',
  bind_status     TINYINT      NOT NULL DEFAULT 1 COMMENT '1已绑定 0已解绑',
  remark          VARCHAR(255) NULL,
  create_by       BIGINT       NULL,
  create_time     DATETIME     DEFAULT CURRENT_TIMESTAMP,
  update_time     DATETIME     NULL ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_tenant_plate (tenant_id, plate_no),
  KEY idx_owner (tenant_id, owner_user_id),
  KEY idx_house (tenant_id, house_id),
  KEY idx_list (tenant_id, list_type, bind_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='车辆台账（白/黑/绿名单）';

-- ========== 2. 车位表 ==========
CREATE TABLE IF NOT EXISTS park_space (
  id              BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id       BIGINT       NOT NULL,
  space_no        VARCHAR(32)  NOT NULL COMMENT '车位编号，如 A-101',
  area_code       VARCHAR(32)  NULL COMMENT '区域编码，用于分布图',
  space_type      VARCHAR(16)  NOT NULL DEFAULT 'PUBLIC' COMMENT 'OWNED产权/RENT租赁/PUBLIC公共临停',
  status          VARCHAR(16)  NOT NULL DEFAULT 'FREE' COMMENT 'FREE空闲/OCCUPIED占用/RESERVED预留/FAULT故障',
  owner_user_id   BIGINT       NULL COMMENT '车位业主用户ID',
  house_id        BIGINT       NULL COMMENT '关联房屋',
  bind_plate_no   VARCHAR(20)  NULL COMMENT '固定绑定车牌（产权/租赁）',
  detector_type   VARCHAR(16)  NULL COMMENT 'GEOMAG地磁/AI_VIDEO视频/NONE',
  detector_id     VARCHAR(64)  NULL COMMENT '检测器设备号',
  map_x           DECIMAL(10,4) NULL COMMENT '分布图X坐标(0-100)',
  map_y           DECIMAL(10,4) NULL COMMENT '分布图Y坐标(0-100)',
  occupying_plate VARCHAR(20)  NULL COMMENT '当前占用车牌',
  occupy_time     DATETIME     NULL COMMENT '开始占用时间',
  anti_occupy     TINYINT      NOT NULL DEFAULT 0 COMMENT '1开启防占用告警',
  remark          VARCHAR(255) NULL,
  create_time     DATETIME     DEFAULT CURRENT_TIMESTAMP,
  update_time     DATETIME     NULL ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_tenant_space (tenant_id, space_no),
  KEY idx_status (tenant_id, status),
  KEY idx_area (tenant_id, area_code),
  KEY idx_owner (tenant_id, owner_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='车位台账与占用状态';

-- ========== 3. 通行记录表 ==========
CREATE TABLE IF NOT EXISTS park_access_log (
  id              BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id       BIGINT       NOT NULL,
  plate_no        VARCHAR(20)  NOT NULL COMMENT '车牌',
  direction       VARCHAR(8)   NOT NULL COMMENT 'IN进场/OUT离场',
  gate_code       VARCHAR(32)  NULL COMMENT '道闸编码',
  gate_name       VARCHAR(64)  NULL COMMENT '道闸名称',
  pass_result     VARCHAR(16)  NOT NULL COMMENT 'ALLOW放行/DENY拒绝',
  pass_reason     VARCHAR(128) NULL COMMENT '放行/拒绝原因',
  vehicle_id      BIGINT       NULL,
  space_id        BIGINT       NULL,
  auth_id         BIGINT       NULL COMMENT '访客授权ID',
  order_id        BIGINT       NULL COMMENT '关联停车订单',
  snap_url        VARCHAR(512) NULL COMMENT '抓拍图片URL',
  pass_time       DATETIME     NOT NULL COMMENT '通行时间',
  create_time     DATETIME     DEFAULT CURRENT_TIMESTAMP,
  KEY idx_plate_time (tenant_id, plate_no, pass_time),
  KEY idx_time (tenant_id, pass_time),
  KEY idx_result (tenant_id, pass_result)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='车辆进出通行日志（可溯源）';

-- ========== 4. 停车订单表 ==========
CREATE TABLE IF NOT EXISTS park_order (
  id              BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id       BIGINT       NOT NULL,
  order_no        VARCHAR(32)  NOT NULL COMMENT '订单号',
  plate_no        VARCHAR(20)  NOT NULL,
  vehicle_id      BIGINT       NULL,
  space_id        BIGINT       NULL COMMENT '停放车位',
  enter_time      DATETIME     NOT NULL COMMENT '进场时间',
  exit_time       DATETIME     NULL COMMENT '离场时间',
  free_minutes    INT          NOT NULL DEFAULT 0 COMMENT '免费时长(分钟)',
  duration_minutes INT         NULL COMMENT '计费时长(分钟)',
  amount          DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '应付金额',
  paid_amount     DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '已付金额',
  status          VARCHAR(16)  NOT NULL DEFAULT 'PARKING' COMMENT 'PARKING在场/UNPAID待缴/PAID已付/CLOSED关闭',
  pay_channel     VARCHAR(16)  NULL COMMENT 'MINI小程序/EXIT出口扫码/CASH现金',
  pay_time        DATETIME     NULL,
  fee_detail      VARCHAR(512) NULL COMMENT '计费明细JSON',
  arrears_flag    TINYINT      NOT NULL DEFAULT 0 COMMENT '1欠费拦截标记',
  create_time     DATETIME     DEFAULT CURRENT_TIMESTAMP,
  update_time     DATETIME     NULL ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_order_no (order_no),
  KEY idx_plate_status (tenant_id, plate_no, status),
  KEY idx_enter (tenant_id, enter_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='停车计费订单';

-- ========== 5. 访客授权表 ==========
CREATE TABLE IF NOT EXISTS park_visitor_auth (
  id              BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id       BIGINT       NOT NULL,
  plate_no        VARCHAR(20)  NULL COMMENT '授权车牌（可空，扫码后补录）',
  auth_code       VARCHAR(32)  NOT NULL COMMENT '授权码/临时通行码',
  qr_token        VARCHAR(64)  NOT NULL COMMENT '二维码令牌',
  owner_user_id   BIGINT       NOT NULL COMMENT '发起业主用户ID',
  house_id        BIGINT       NULL,
  start_time      DATETIME     NOT NULL COMMENT '生效开始',
  end_time        DATETIME     NOT NULL COMMENT '失效时间（超时计费起点）',
  status          VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE有效/USED已用/EXPIRED过期/REVOKED撤销',
  max_entries     INT          NOT NULL DEFAULT 2 COMMENT '最大进出次数',
  used_entries    INT          NOT NULL DEFAULT 0 COMMENT '已使用次数',
  source          VARCHAR(16)  NOT NULL DEFAULT 'OWNER' COMMENT 'OWNER业主生成/GATE门岗登记/SELF访客自助',
  remark          VARCHAR(255) NULL,
  create_time     DATETIME     DEFAULT CURRENT_TIMESTAMP,
  update_time     DATETIME     NULL ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_qr (qr_token),
  UNIQUE KEY uk_auth_code (tenant_id, auth_code),
  KEY idx_plate (tenant_id, plate_no, status),
  KEY idx_owner (tenant_id, owner_user_id),
  KEY idx_end (tenant_id, end_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='访客临时通行授权';

-- ========== 6. 收费规则（配套） ==========
CREATE TABLE IF NOT EXISTS park_fee_rule (
  id              BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id       BIGINT       NOT NULL,
  rule_name       VARCHAR(64)  NOT NULL DEFAULT '默认规则',
  free_minutes    INT          NOT NULL DEFAULT 15 COMMENT '免费时长(分钟)',
  first_hour_fee  DECIMAL(10,2) NOT NULL DEFAULT 5.00 COMMENT '首小时费用',
  step_minutes    INT          NOT NULL DEFAULT 60 COMMENT '阶梯步长(分钟)',
  step_fee        DECIMAL(10,2) NOT NULL DEFAULT 3.00 COMMENT '每阶梯费用',
  day_cap         DECIMAL(10,2) NOT NULL DEFAULT 40.00 COMMENT '单日封顶',
  night_start     VARCHAR(8)   NULL DEFAULT '22:00' COMMENT '夜间优惠开始',
  night_end       VARCHAR(8)   NULL DEFAULT '07:00' COMMENT '夜间优惠结束',
  night_discount  DECIMAL(4,2) NOT NULL DEFAULT 0.50 COMMENT '夜间折扣 0.5=五折',
  monthly_fee     DECIMAL(10,2) NULL COMMENT '月租费用',
  yearly_fee      DECIMAL(10,2) NULL COMMENT '包年费用',
  enabled         TINYINT      NOT NULL DEFAULT 1,
  create_time     DATETIME     DEFAULT CURRENT_TIMESTAMP,
  update_time     DATETIME     NULL ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_tenant (tenant_id, enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='停车收费规则';

-- 演示数据（阳光花园 tenant_id=1）
INSERT INTO park_fee_rule (tenant_id, rule_name, free_minutes, first_hour_fee, step_minutes, step_fee, day_cap)
SELECT 1, '默认临停规则', 15, 5.00, 60, 3.00, 40.00
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM park_fee_rule WHERE tenant_id=1 AND enabled=1);

INSERT INTO park_space (tenant_id, space_no, area_code, space_type, status, anti_occupy, map_x, map_y, detector_type)
SELECT 1, 'A-101', 'A', 'OWNED', 'FREE', 1, 12.5, 20.0, 'GEOMAG' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM park_space WHERE tenant_id=1 AND space_no='A-101');
INSERT INTO park_space (tenant_id, space_no, area_code, space_type, status, map_x, map_y, detector_type)
SELECT 1, 'A-102', 'A', 'RENT', 'FREE', 25.0, 20.0, 'AI_VIDEO' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM park_space WHERE tenant_id=1 AND space_no='A-102');
INSERT INTO park_space (tenant_id, space_no, area_code, space_type, status, map_x, map_y)
SELECT 1, 'B-201', 'B', 'PUBLIC', 'FREE', 40.0, 55.0 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM park_space WHERE tenant_id=1 AND space_no='B-201');

INSERT INTO park_vehicle (tenant_id, plate_no, vehicle_type, list_type, owner_name, owner_phone, bind_status, remark)
SELECT 1, '京A12345', 'OWNER', 'WHITE', '张先生', '13800000021', 1, '演示业主车' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM park_vehicle WHERE tenant_id=1 AND plate_no='京A12345');
INSERT INTO park_vehicle (tenant_id, plate_no, vehicle_type, list_type, owner_name, bind_status, remark)
SELECT 1, '京XF0001', 'GREEN', 'GREEN', '消防通道', 1, '消防车绿通' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM park_vehicle WHERE tenant_id=1 AND plate_no='京XF0001');
