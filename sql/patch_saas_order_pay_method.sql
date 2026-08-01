-- SaaS 订单支付方式（微信/支付宝/银行卡/模拟）
SET @db = DATABASE();

SET @sql = IF(
  (SELECT COUNT(1) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA=@db AND TABLE_NAME='saas_order' AND COLUMN_NAME='pay_method') = 0,
  'ALTER TABLE saas_order ADD COLUMN pay_method VARCHAR(32) NULL COMMENT ''wechat/alipay/bank/mock'' AFTER status',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
