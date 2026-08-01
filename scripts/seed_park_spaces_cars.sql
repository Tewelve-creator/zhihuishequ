-- 车位 + 业主车辆演示数据（tenant_id=1），并完成绑定
SET NAMES utf8mb4;

-- 1) 补齐车位（已存在则跳过）
INSERT INTO park_space (tenant_id, space_no, area_code, space_type, status, anti_occupy, remark, create_time, update_time)
SELECT * FROM (
  SELECT 1 AS tenant_id, 'A-103' AS space_no, 'A区' AS area_code, 'OWNED' AS space_type, 'FREE' AS status, 1 AS anti_occupy, '地下一层' AS remark, NOW() AS create_time, NOW() AS update_time
  UNION ALL SELECT 1,'A-104','A区','OWNED','FREE',1,'地下一层',NOW(),NOW()
  UNION ALL SELECT 1,'A-105','A区','RENT','FREE',1,'地下一层',NOW(),NOW()
  UNION ALL SELECT 1,'A-106','A区','RENT','FREE',0,'地下一层',NOW(),NOW()
  UNION ALL SELECT 1,'A-107','A区','PUBLIC','FREE',0,'访客临停',NOW(),NOW()
  UNION ALL SELECT 1,'A-108','A区','OWNED','FREE',1,'地下一层',NOW(),NOW()
  UNION ALL SELECT 1,'B-202','B区','OWNED','FREE',1,'地下二层',NOW(),NOW()
  UNION ALL SELECT 1,'B-203','B区','RENT','FREE',1,'地下二层',NOW(),NOW()
  UNION ALL SELECT 1,'B-204','B区','OWNED','FREE',1,'地下二层',NOW(),NOW()
  UNION ALL SELECT 1,'B-205','B区','RENT','FREE',0,'地下二层',NOW(),NOW()
  UNION ALL SELECT 1,'B-206','B区','PUBLIC','FREE',0,'访客临停',NOW(),NOW()
  UNION ALL SELECT 1,'C-301','C区','OWNED','FREE',1,'地面车位',NOW(),NOW()
  UNION ALL SELECT 1,'C-302','C区','OWNED','FREE',1,'地面车位',NOW(),NOW()
  UNION ALL SELECT 1,'C-303','C区','RENT','FREE',1,'地面车位',NOW(),NOW()
  UNION ALL SELECT 1,'C-304','C区','RENT','FREE',0,'地面车位',NOW(),NOW()
  UNION ALL SELECT 1,'C-305','C区','PUBLIC','FREE',0,'访客临停',NOW(),NOW()
) t
WHERE NOT EXISTS (
  SELECT 1 FROM park_space p WHERE p.tenant_id=1 AND p.space_no=t.space_no
);

-- 更新已有车位区域编码
UPDATE park_space SET area_code='A区', space_type='OWNED', anti_occupy=1, remark=IFNULL(remark,'地下一层') WHERE tenant_id=1 AND space_no IN ('A-101','A-102');
UPDATE park_space SET area_code='B区', space_type='RENT', anti_occupy=1, remark=IFNULL(remark,'地下二层') WHERE tenant_id=1 AND space_no='B-201';

-- 2) 补齐业主车辆
INSERT INTO resident_car (
  tenant_id, house_id, house_label, user_id, owner_name, owner_phone,
  plate_no, brand_model, color, status, access_restricted, create_time, update_time
)
SELECT * FROM (
  SELECT 1 AS tenant_id, 91 AS house_id, '1栋1单元103' AS house_label, 12 AS user_id, '居民小刚' AS owner_name, '13800000023' AS owner_phone,
         '京A12345' AS plate_no, '丰田凯美瑞' AS brand_model, '白色' AS color, 'ACTIVE' AS status, 0 AS access_restricted, NOW() AS create_time, NOW() AS update_time
  UNION ALL SELECT 1,92,'1栋1单元104',13,'居民小美','13800000024','京B67890','本田雅阁','黑色','ACTIVE',0,NOW(),NOW()
  UNION ALL SELECT 1,94,'1栋1单元202',11,'151','19903109633','京C11223','大众帕萨特','银色','ACTIVE',0,NOW(),NOW()
  UNION ALL SELECT 1,96,'1栋1单元204',4,'居民小明','13800000021','京A99999','比亚迪汉','蓝色','ACTIVE',0,NOW(),NOW()
  UNION ALL SELECT 1,98,'1栋2单元102',9,'小白','18803109636','冀E10086','特斯拉Model3','白色','ACTIVE',0,NOW(),NOW()
  UNION ALL SELECT 1,100,'1栋2单元104',12,'居民小刚','13800000023','京D55667','日产轩逸','灰色','ACTIVE',0,NOW(),NOW()
  UNION ALL SELECT 1,4,'2栋1单元101',13,'居民小美','13800000024','京E77889','宝马3系','蓝色','ACTIVE',0,NOW(),NOW()
  UNION ALL SELECT 1,5,'2栋1单元202',11,'151','19903109633','京F33445','奥迪A4L','黑色','ACTIVE',0,NOW(),NOW()
  UNION ALL SELECT 1,104,'2栋1单元102',4,'居民小明','13800000021','京G66778','理想L7','灰色','ACTIVE',0,NOW(),NOW()
  UNION ALL SELECT 1,110,'2栋2单元101',9,'小白','18803109636','冀A52013','小鹏P7','白色','ACTIVE',0,NOW(),NOW()
  UNION ALL SELECT 1,118,'3栋1单元101',12,'居民小刚','13800000023','京H99001','吉利星越','红色','ACTIVE',0,NOW(),NOW()
  UNION ALL SELECT 1,6,'3栋1单元301',13,'居民小美','13800000024','京J22334','长安CS75','白色','ACTIVE',0,NOW(),NOW()
) c
WHERE NOT EXISTS (
  SELECT 1 FROM resident_car r WHERE r.tenant_id=1 AND r.plate_no=c.plate_no
);

-- 3) 绑定：车位 <-> 车辆（成对绑定，状态置为占用）
-- 使用临时映射表
DROP TEMPORARY TABLE IF EXISTS tmp_space_car_bind;
CREATE TEMPORARY TABLE tmp_space_car_bind (
  space_no varchar(32),
  plate_no varchar(20)
);
INSERT INTO tmp_space_car_bind(space_no, plate_no) VALUES
('A-101','京A88888'),
('A-102','冀E846815'),
('A-103','京A12345'),
('A-104','京B67890'),
('A-105','京C11223'),
('A-106','京A99999'),
('A-108','冀E10086'),
('B-201','京D55667'),
('B-202','京E77889'),
('B-203','京F33445'),
('B-204','京G66778'),
('C-301','冀A52013'),
('C-302','京H99001'),
('C-303','京J22334');

UPDATE park_space s
JOIN tmp_space_car_bind b ON b.space_no = s.space_no
JOIN resident_car c ON c.tenant_id = s.tenant_id AND c.plate_no = b.plate_no
SET
  s.status = 'OCCUPIED',
  s.bind_plate_no = c.plate_no,
  s.occupying_plate = c.plate_no,
  s.occupy_time = NOW(),
  s.owner_user_id = c.user_id,
  s.house_id = c.house_id,
  s.update_time = NOW()
WHERE s.tenant_id = 1;

UPDATE resident_car c
JOIN tmp_space_car_bind b ON b.plate_no = c.plate_no
JOIN park_space s ON s.tenant_id = c.tenant_id AND s.space_no = b.space_no
SET
  c.space_id = s.id,
  c.space_no = s.space_no,
  c.update_time = NOW()
WHERE c.tenant_id = 1;

-- 留几辆未绑定、几个空闲车位便于演示筛选
-- A-107 / B-205 / B-206 / C-304 / C-305 保持 FREE

SELECT 'park_space' AS t, COUNT(*) AS cnt, SUM(status='FREE') AS free_cnt, SUM(status='OCCUPIED') AS occupied_cnt
FROM park_space WHERE tenant_id=1
UNION ALL
SELECT 'resident_car', COUNT(*), SUM(space_id IS NULL), SUM(space_id IS NOT NULL)
FROM resident_car WHERE tenant_id=1;

SELECT s.space_no, s.status, s.bind_plate_no, c.owner_name, c.house_label
FROM park_space s
LEFT JOIN resident_car c ON c.space_id = s.id
WHERE s.tenant_id=1
ORDER BY s.space_no;
