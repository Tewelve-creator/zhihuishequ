-- 修复 community_house / household 中文乱码（须 utf8mb4 执行）
USE smart_community;

SET NAMES utf8mb4;

-- 清空后按租户正确重写房源
DELETE FROM community_house;

INSERT INTO community_house (id, tenant_id, community_name, building, unit, room_no, status, create_time) VALUES
(1, 1, '阳光花园社区', '1栋', '1单元', '101', 1, NOW()),
(2, 1, '阳光花园社区', '1栋', '1单元', '102', 1, NOW()),
(3, 1, '阳光花园社区', '1栋', '2单元', '201', 1, NOW()),
(4, 1, '阳光花园社区', '2栋', '1单元', '101', 1, NOW()),
(5, 1, '阳光花园社区', '2栋', '1单元', '202', 1, NOW()),
(6, 1, '阳光花园社区', '3栋', '1单元', '301', 1, NOW()),
(7, 2, '星河湾社区', 'A栋', '1单元', '101', 1, NOW()),
(8, 2, '星河湾社区', 'A栋', '2单元', '201', 1, NOW()),
(9, 2, '星河湾社区', 'B栋', '1单元', '501', 1, NOW());

-- 同步住户展示文案
UPDATE household_resident r
INNER JOIN community_house h ON h.id = r.house_id
SET r.house_label = CONCAT(h.community_name, ' · ', h.building, h.unit, h.room_no),
    r.update_time = NOW();

-- 同步未审结申请单上的房屋文案
UPDATE house_move_apply a
INNER JOIN community_house h ON h.id = a.house_id
SET a.house_label = CONCAT(h.community_name, ' · ', h.building, h.unit, h.room_no);
