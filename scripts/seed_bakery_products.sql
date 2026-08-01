SET NAMES utf8mb4;

-- 社区烘焙屋 shop_id=4, tenant_id=1

-- 分类（按名称去重）
INSERT INTO store_category (tenant_id, shop_id, name, sort_no, status, create_time)
SELECT 1, 4, '面包类', 1, 1, NOW() FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM store_category WHERE shop_id=4 AND name='面包类');

INSERT INTO store_category (tenant_id, shop_id, name, sort_no, status, create_time)
SELECT 1, 4, '蛋糕甜品', 2, 1, NOW() FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM store_category WHERE shop_id=4 AND name='蛋糕甜品');

INSERT INTO store_category (tenant_id, shop_id, name, sort_no, status, create_time)
SELECT 1, 4, '点心饼干', 3, 1, NOW() FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM store_category WHERE shop_id=4 AND name='点心饼干');

INSERT INTO store_category (tenant_id, shop_id, name, sort_no, status, create_time)
SELECT 1, 4, '饮品搭配', 4, 1, NOW() FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM store_category WHERE shop_id=4 AND name='饮品搭配');

INSERT INTO store_category (tenant_id, shop_id, name, sort_no, status, create_time)
SELECT 1, 4, '邻里团购大件', 5, 1, NOW() FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM store_category WHERE shop_id=4 AND name='邻里团购大件');

-- 商品插入辅助：按店+名称去重
-- 一、面包类
INSERT INTO store_product (tenant_id, shop_id, category_id, name, price, original_price, stock, stock_warn, limit_buy, sales, status, sort_no, remark, create_time, update_time)
SELECT 1, 4, c.id, '奶香吐司', 8.80, 9.80, 80, 10, 0, 0, 1, 1, '400g/条', NOW(), NOW()
FROM store_category c WHERE c.shop_id=4 AND c.name='面包类'
AND NOT EXISTS (SELECT 1 FROM store_product p WHERE p.shop_id=4 AND p.name='奶香吐司');

INSERT INTO store_product (tenant_id, shop_id, category_id, name, price, original_price, stock, stock_warn, limit_buy, sales, status, sort_no, remark, create_time, update_time)
SELECT 1, 4, c.id, '红豆面包', 4.50, 5.00, 120, 15, 0, 0, 1, 2, '个', NOW(), NOW()
FROM store_category c WHERE c.shop_id=4 AND c.name='面包类'
AND NOT EXISTS (SELECT 1 FROM store_product p WHERE p.shop_id=4 AND p.name='红豆面包');

INSERT INTO store_product (tenant_id, shop_id, category_id, name, price, original_price, stock, stock_warn, limit_buy, sales, status, sort_no, remark, create_time, update_time)
SELECT 1, 4, c.id, '肉松卷', 6.00, 6.80, 100, 15, 0, 0, 1, 3, '个', NOW(), NOW()
FROM store_category c WHERE c.shop_id=4 AND c.name='面包类'
AND NOT EXISTS (SELECT 1 FROM store_product p WHERE p.shop_id=4 AND p.name='肉松卷');

INSERT INTO store_product (tenant_id, shop_id, category_id, name, price, original_price, stock, stock_warn, limit_buy, sales, status, sort_no, remark, create_time, update_time)
SELECT 1, 4, c.id, '牛角包', 5.50, 6.00, 90, 15, 0, 0, 1, 4, '个', NOW(), NOW()
FROM store_category c WHERE c.shop_id=4 AND c.name='面包类'
AND NOT EXISTS (SELECT 1 FROM store_product p WHERE p.shop_id=4 AND p.name='牛角包');

INSERT INTO store_product (tenant_id, shop_id, category_id, name, price, original_price, stock, stock_warn, limit_buy, sales, status, sort_no, remark, create_time, update_time)
SELECT 1, 4, c.id, '全麦欧包', 7.90, 8.90, 70, 10, 0, 0, 1, 5, '个', NOW(), NOW()
FROM store_category c WHERE c.shop_id=4 AND c.name='面包类'
AND NOT EXISTS (SELECT 1 FROM store_product p WHERE p.shop_id=4 AND p.name='全麦欧包');

INSERT INTO store_product (tenant_id, shop_id, category_id, name, price, original_price, stock, stock_warn, limit_buy, sales, status, sort_no, remark, create_time, update_time)
SELECT 1, 4, c.id, '椰蓉面包', 4.80, 5.50, 100, 15, 0, 0, 1, 6, '个', NOW(), NOW()
FROM store_category c WHERE c.shop_id=4 AND c.name='面包类'
AND NOT EXISTS (SELECT 1 FROM store_product p WHERE p.shop_id=4 AND p.name='椰蓉面包');

INSERT INTO store_product (tenant_id, shop_id, category_id, name, price, original_price, stock, stock_warn, limit_buy, sales, status, sort_no, remark, create_time, update_time)
SELECT 1, 4, c.id, '香葱肉松面包', 5.20, 5.80, 100, 15, 0, 0, 1, 7, '个', NOW(), NOW()
FROM store_category c WHERE c.shop_id=4 AND c.name='面包类'
AND NOT EXISTS (SELECT 1 FROM store_product p WHERE p.shop_id=4 AND p.name='香葱肉松面包');

-- 二、蛋糕甜品
INSERT INTO store_product (tenant_id, shop_id, category_id, name, price, original_price, stock, stock_warn, limit_buy, sales, status, sort_no, remark, create_time, update_time)
SELECT 1, 4, c.id, '原味戚风蛋糕小块', 9.90, 11.00, 60, 10, 0, 0, 1, 1, '块', NOW(), NOW()
FROM store_category c WHERE c.shop_id=4 AND c.name='蛋糕甜品'
AND NOT EXISTS (SELECT 1 FROM store_product p WHERE p.shop_id=4 AND p.name='原味戚风蛋糕小块');

INSERT INTO store_product (tenant_id, shop_id, category_id, name, price, original_price, stock, stock_warn, limit_buy, sales, status, sort_no, remark, create_time, update_time)
SELECT 1, 4, c.id, '芒果慕斯杯', 12.80, 14.80, 50, 8, 0, 0, 1, 2, '杯', NOW(), NOW()
FROM store_category c WHERE c.shop_id=4 AND c.name='蛋糕甜品'
AND NOT EXISTS (SELECT 1 FROM store_product p WHERE p.shop_id=4 AND p.name='芒果慕斯杯');

INSERT INTO store_product (tenant_id, shop_id, category_id, name, price, original_price, stock, stock_warn, limit_buy, sales, status, sort_no, remark, create_time, update_time)
SELECT 1, 4, c.id, '提拉米苏小盒', 13.50, 15.00, 45, 8, 0, 0, 1, 3, '盒', NOW(), NOW()
FROM store_category c WHERE c.shop_id=4 AND c.name='蛋糕甜品'
AND NOT EXISTS (SELECT 1 FROM store_product p WHERE p.shop_id=4 AND p.name='提拉米苏小盒');

INSERT INTO store_product (tenant_id, shop_id, category_id, name, price, original_price, stock, stock_warn, limit_buy, sales, status, sort_no, remark, create_time, update_time)
SELECT 1, 4, c.id, '奶油泡芙（4个）', 8.50, 9.90, 55, 10, 0, 0, 1, 4, '4个装', NOW(), NOW()
FROM store_category c WHERE c.shop_id=4 AND c.name='蛋糕甜品'
AND NOT EXISTS (SELECT 1 FROM store_product p WHERE p.shop_id=4 AND p.name='奶油泡芙（4个）');

INSERT INTO store_product (tenant_id, shop_id, category_id, name, price, original_price, stock, stock_warn, limit_buy, sales, status, sort_no, remark, create_time, update_time)
SELECT 1, 4, c.id, '草莓纸杯蛋糕', 7.80, 8.80, 70, 10, 0, 0, 1, 5, '个', NOW(), NOW()
FROM store_category c WHERE c.shop_id=4 AND c.name='蛋糕甜品'
AND NOT EXISTS (SELECT 1 FROM store_product p WHERE p.shop_id=4 AND p.name='草莓纸杯蛋糕');

INSERT INTO store_product (tenant_id, shop_id, category_id, name, price, original_price, stock, stock_warn, limit_buy, sales, status, sort_no, remark, create_time, update_time)
SELECT 1, 4, c.id, '巧克力熔岩蛋糕', 11.00, 12.80, 40, 8, 0, 0, 1, 6, '个', NOW(), NOW()
FROM store_category c WHERE c.shop_id=4 AND c.name='蛋糕甜品'
AND NOT EXISTS (SELECT 1 FROM store_product p WHERE p.shop_id=4 AND p.name='巧克力熔岩蛋糕');

-- 三、点心饼干
INSERT INTO store_product (tenant_id, shop_id, category_id, name, price, original_price, stock, stock_warn, limit_buy, sales, status, sort_no, remark, create_time, update_time)
SELECT 1, 4, c.id, '奶香曲奇饼干（袋装）', 10.90, 12.00, 80, 10, 0, 0, 1, 1, '袋装', NOW(), NOW()
FROM store_category c WHERE c.shop_id=4 AND c.name='点心饼干'
AND NOT EXISTS (SELECT 1 FROM store_product p WHERE p.shop_id=4 AND p.name='奶香曲奇饼干（袋装）');

INSERT INTO store_product (tenant_id, shop_id, category_id, name, price, original_price, stock, stock_warn, limit_buy, sales, status, sort_no, remark, create_time, update_time)
SELECT 1, 4, c.id, '蛋黄酥', 5.50, 6.00, 100, 15, 0, 0, 1, 2, '枚', NOW(), NOW()
FROM store_category c WHERE c.shop_id=4 AND c.name='点心饼干'
AND NOT EXISTS (SELECT 1 FROM store_product p WHERE p.shop_id=4 AND p.name='蛋黄酥');

INSERT INTO store_product (tenant_id, shop_id, category_id, name, price, original_price, stock, stock_warn, limit_buy, sales, status, sort_no, remark, create_time, update_time)
SELECT 1, 4, c.id, '麻薯团子（3个）', 9.00, 10.00, 60, 10, 0, 0, 1, 3, '3个装', NOW(), NOW()
FROM store_category c WHERE c.shop_id=4 AND c.name='点心饼干'
AND NOT EXISTS (SELECT 1 FROM store_product p WHERE p.shop_id=4 AND p.name='麻薯团子（3个）');

INSERT INTO store_product (tenant_id, shop_id, category_id, name, price, original_price, stock, stock_warn, limit_buy, sales, status, sort_no, remark, create_time, update_time)
SELECT 1, 4, c.id, '雪花酥', 12.50, 14.00, 50, 8, 0, 0, 1, 4, '盒', NOW(), NOW()
FROM store_category c WHERE c.shop_id=4 AND c.name='点心饼干'
AND NOT EXISTS (SELECT 1 FROM store_product p WHERE p.shop_id=4 AND p.name='雪花酥');

INSERT INTO store_product (tenant_id, shop_id, category_id, name, price, original_price, stock, stock_warn, limit_buy, sales, status, sort_no, remark, create_time, update_time)
SELECT 1, 4, c.id, '桃酥', 7.50, 8.50, 70, 10, 0, 0, 1, 5, '半斤', NOW(), NOW()
FROM store_category c WHERE c.shop_id=4 AND c.name='点心饼干'
AND NOT EXISTS (SELECT 1 FROM store_product p WHERE p.shop_id=4 AND p.name='桃酥');

-- 四、饮品搭配
INSERT INTO store_product (tenant_id, shop_id, category_id, name, price, original_price, stock, stock_warn, limit_buy, sales, status, sort_no, remark, create_time, update_time)
SELECT 1, 4, c.id, '常温纯牛奶', 4.00, 4.50, 150, 20, 0, 0, 1, 1, '瓶', NOW(), NOW()
FROM store_category c WHERE c.shop_id=4 AND c.name='饮品搭配'
AND NOT EXISTS (SELECT 1 FROM store_product p WHERE p.shop_id=4 AND p.name='常温纯牛奶');

INSERT INTO store_product (tenant_id, shop_id, category_id, name, price, original_price, stock, stock_warn, limit_buy, sales, status, sort_no, remark, create_time, update_time)
SELECT 1, 4, c.id, '现磨豆浆', 3.50, 4.00, 120, 20, 0, 0, 1, 2, '杯', NOW(), NOW()
FROM store_category c WHERE c.shop_id=4 AND c.name='饮品搭配'
AND NOT EXISTS (SELECT 1 FROM store_product p WHERE p.shop_id=4 AND p.name='现磨豆浆');

INSERT INTO store_product (tenant_id, shop_id, category_id, name, price, original_price, stock, stock_warn, limit_buy, sales, status, sort_no, remark, create_time, update_time)
SELECT 1, 4, c.id, '原味酸奶', 5.80, 6.50, 100, 15, 0, 0, 1, 3, '杯', NOW(), NOW()
FROM store_category c WHERE c.shop_id=4 AND c.name='饮品搭配'
AND NOT EXISTS (SELECT 1 FROM store_product p WHERE p.shop_id=4 AND p.name='原味酸奶');

INSERT INTO store_product (tenant_id, shop_id, category_id, name, price, original_price, stock, stock_warn, limit_buy, sales, status, sort_no, remark, create_time, update_time)
SELECT 1, 4, c.id, '柠檬红茶', 6.50, 7.50, 80, 10, 0, 0, 1, 4, '杯', NOW(), NOW()
FROM store_category c WHERE c.shop_id=4 AND c.name='饮品搭配'
AND NOT EXISTS (SELECT 1 FROM store_product p WHERE p.shop_id=4 AND p.name='柠檬红茶');

-- 五、邻里团购大件
INSERT INTO store_product (tenant_id, shop_id, category_id, name, price, original_price, stock, stock_warn, limit_buy, sales, status, sort_no, remark, create_time, update_time)
SELECT 1, 4, c.id, '6寸水果生日蛋糕', 88.00, 98.00, 20, 3, 2, 0, 1, 1, '需提前预订', NOW(), NOW()
FROM store_category c WHERE c.shop_id=4 AND c.name='邻里团购大件'
AND NOT EXISTS (SELECT 1 FROM store_product p WHERE p.shop_id=4 AND p.name='6寸水果生日蛋糕');

INSERT INTO store_product (tenant_id, shop_id, category_id, name, price, original_price, stock, stock_warn, limit_buy, sales, status, sort_no, remark, create_time, update_time)
SELECT 1, 4, c.id, '8寸奶油蛋糕', 128.00, 148.00, 15, 3, 2, 0, 1, 2, '需提前预订', NOW(), NOW()
FROM store_category c WHERE c.shop_id=4 AND c.name='邻里团购大件'
AND NOT EXISTS (SELECT 1 FROM store_product p WHERE p.shop_id=4 AND p.name='8寸奶油蛋糕');

-- 店铺品类同步
UPDATE store_shop SET category_name='社区烘焙', update_time=NOW() WHERE id=4;

SELECT c.name AS category, COUNT(p.id) AS product_count
FROM store_category c
LEFT JOIN store_product p ON p.category_id=c.id AND p.shop_id=4
WHERE c.shop_id=4
GROUP BY c.id, c.name
ORDER BY c.sort_no;

SELECT p.name, c.name AS category, p.price, p.remark, p.stock, p.status
FROM store_product p
LEFT JOIN store_category c ON c.id=p.category_id
WHERE p.shop_id=4
ORDER BY c.sort_no, p.sort_no, p.id;
