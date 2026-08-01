-- 社区超市目录说明（三大分类，无生鲜）
-- 因 Windows 终端编码问题，推荐执行：
--   python sql/seed_store_catalog.py
-- 本文件仅作结构说明；实际种子数据以 seed_store_catalog.py 为准。
--
-- 分类：饮品酒水 / 零食速食 / 日用百货
-- 能力：商品图 cover_url、库存 stock、售价 price；居民结算可选送货上门 DELIVERY / 到店自提 PICKUP
USE smart_community;
SET NAMES utf8mb4;
