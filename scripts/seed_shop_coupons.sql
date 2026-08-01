SET NAMES utf8mb4;

-- 店铺1：阳光花园社区超市
INSERT INTO store_coupon (
  tenant_id, shop_id, name, coupon_type, threshold_amount, discount_amount, discount_rate,
  total_count, claimed_count, limit_per_user, start_time, end_time, remark, status, create_time, update_time
)
SELECT * FROM (
  SELECT 1 AS tenant_id, 1 AS shop_id, '新客满30减5' AS name, 'FULL_REDUCE' AS coupon_type,
         30.00 AS threshold_amount, 5.00 AS discount_amount, NULL AS discount_rate,
         200 AS total_count, 12 AS claimed_count, 1 AS limit_per_user,
         DATE_SUB(NOW(), INTERVAL 3 DAY) AS start_time, DATE_ADD(NOW(), INTERVAL 27 DAY) AS end_time,
         '仅限本店商品使用' AS remark, 1 AS status, NOW() AS create_time, NOW() AS update_time
  UNION ALL SELECT 1,1,'无门槛立减3元','NO_THRESHOLD',0,3.00,NULL,300,28,2,
         DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_ADD(NOW(), INTERVAL 14 DAY),'仅限本店商品使用',1,NOW(),NOW()
  UNION ALL SELECT 1,1,'生鲜满88减15','FULL_REDUCE',88.00,15.00,NULL,100,5,1,
         DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_ADD(NOW(), INTERVAL 20 DAY),'仅限本店商品使用',1,NOW(),NOW()
  UNION ALL SELECT 1,1,'全场9折券','DISCOUNT',0,0,9.0,150,9,1,
         NOW(), DATE_ADD(NOW(), INTERVAL 10 DAY),'仅限本店商品使用',1,NOW(),NOW()
  UNION ALL SELECT 1,1,'周末满50减8','FULL_REDUCE',50.00,8.00,NULL,80,0,1,
         DATE_ADD(NOW(), INTERVAL 2 DAY), DATE_ADD(NOW(), INTERVAL 30 DAY),'周末专享·仅限本店',1,NOW(),NOW()
) t
WHERE NOT EXISTS (
  SELECT 1 FROM store_coupon c WHERE c.shop_id=1 AND c.name=t.name
);

-- 店铺4：社区烘焙屋
INSERT INTO store_coupon (
  tenant_id, shop_id, name, coupon_type, threshold_amount, discount_amount, discount_rate,
  total_count, claimed_count, limit_per_user, start_time, end_time, remark, status, create_time, update_time
)
SELECT * FROM (
  SELECT 1 AS tenant_id, 4 AS shop_id, '烘焙满20减3' AS name, 'FULL_REDUCE' AS coupon_type,
         20.00 AS threshold_amount, 3.00 AS discount_amount, NULL AS discount_rate,
         200 AS total_count, 18 AS claimed_count, 2 AS limit_per_user,
         DATE_SUB(NOW(), INTERVAL 5 DAY) AS start_time, DATE_ADD(NOW(), INTERVAL 25 DAY) AS end_time,
         '仅限本店面包甜品使用' AS remark, 1 AS status, NOW() AS create_time, NOW() AS update_time
  UNION ALL SELECT 1,4,'新客立减2元','NO_THRESHOLD',0,2.00,NULL,500,66,1,
         DATE_SUB(NOW(), INTERVAL 7 DAY), DATE_ADD(NOW(), INTERVAL 23 DAY),'仅限本店商品使用',1,NOW(),NOW()
  UNION ALL SELECT 1,4,'蛋糕满88减12','FULL_REDUCE',88.00,12.00,NULL,60,3,1,
         DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_ADD(NOW(), INTERVAL 29 DAY),'生日蛋糕/大件可用',1,NOW(),NOW()
  UNION ALL SELECT 1,4,'甜品8.5折','DISCOUNT',30.00,0,8.5,120,7,1,
         DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_ADD(NOW(), INTERVAL 12 DAY),'满30元可用·仅限本店',1,NOW(),NOW()
  UNION ALL SELECT 1,4,'早餐搭配券满15减2','FULL_REDUCE',15.00,2.00,NULL,300,41,3,
         NOW(), DATE_ADD(NOW(), INTERVAL 15 DAY),'面包+饮品更划算',1,NOW(),NOW()
) t
WHERE NOT EXISTS (
  SELECT 1 FROM store_coupon c WHERE c.shop_id=4 AND c.name=t.name
);

SELECT s.name AS shop, c.name AS coupon, c.coupon_type, c.threshold_amount, c.discount_amount, c.discount_rate,
       c.total_count, c.claimed_count, c.status,
       DATE_FORMAT(c.start_time,'%Y-%m-%d') AS start_d, DATE_FORMAT(c.end_time,'%Y-%m-%d') AS end_d
FROM store_coupon c
JOIN store_shop s ON s.id=c.shop_id
WHERE c.shop_id IN (1,4)
ORDER BY c.shop_id, c.id;
