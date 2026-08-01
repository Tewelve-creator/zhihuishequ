# -*- coding: utf-8 -*-
import pymysql

c = pymysql.connect(
    host="127.0.0.1",
    user="root",
    password="root",
    database="smart_community",
    charset="utf8mb4",
)
cur = c.cursor()
cur.execute(
    """
    CREATE TABLE IF NOT EXISTS platform_profit_rule (
      id BIGINT PRIMARY KEY AUTO_INCREMENT,
      shop_type VARCHAR(64) NOT NULL,
      commission_rate DECIMAL(5,2) NOT NULL DEFAULT 5.00,
      platform_share_rate DECIMAL(5,2) NOT NULL DEFAULT 70.00,
      property_share_rate DECIMAL(5,2) NOT NULL DEFAULT 30.00,
      enabled TINYINT NOT NULL DEFAULT 1,
      remark VARCHAR(255) NULL,
      create_time DATETIME NULL,
      update_time DATETIME NULL,
      UNIQUE KEY uk_ppr_type (shop_type)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
    """
)
try:
    cur.execute(
        "update store_merchant_apply set shop_type=%s "
        "where shop_type is null or shop_type in (%s,%s,%s,%s,%s,%s)",
        ("社区超市", "便利店", "超市", "餐饮", "生鲜", "家政服务", "其他"),
    )
except Exception as e:
    print("apply skip", e)
try:
    cur.execute(
        "update store_merchant set shop_type=%s "
        "where shop_type is null or shop_type in (%s,%s) or name like %s or name like %s",
        ("社区超市", "便利店", "超市", "%超市%", "%便利%"),
    )
except Exception as e:
    print("merchant skip", e)
cur.execute(
    "update platform_profit_rule set enabled=0 where shop_type not in (%s,%s)",
    ("*", "社区超市"),
)
for t, rate, ps, prs, rm in [
    ("*", 5, 70, 30, "社区超市默认分润"),
    ("社区超市", 5, 70, 30, "社区超市分润规则"),
]:
    cur.execute("select id from platform_profit_rule where shop_type=%s", (t,))
    if cur.fetchone():
        cur.execute(
            "update platform_profit_rule set commission_rate=%s,platform_share_rate=%s,"
            "property_share_rate=%s,enabled=1,remark=%s,update_time=now() where shop_type=%s",
            (rate, ps, prs, rm, t),
        )
    else:
        cur.execute(
            "insert into platform_profit_rule(shop_type,commission_rate,platform_share_rate,"
            "property_share_rate,enabled,remark,create_time,update_time) "
            "values(%s,%s,%s,%s,1,%s,now(),now())",
            (t, rate, ps, prs, rm),
        )
c.commit()
print("ok supermarket scope")
c.close()
