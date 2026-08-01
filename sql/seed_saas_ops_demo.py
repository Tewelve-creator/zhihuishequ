# -*- coding: utf-8 -*-
"""运营端演示：敏感词 + AI 调用日志"""
import random
from datetime import datetime, timedelta

import pymysql

conn = pymysql.connect(
    host="127.0.0.1",
    user="root",
    password="root",
    database="smart_community",
    charset="utf8mb4",
)
cur = conn.cursor()

cur.execute(
    """
    CREATE TABLE IF NOT EXISTS sys_sensitive_word (
      id BIGINT PRIMARY KEY AUTO_INCREMENT,
      word VARCHAR(64) NOT NULL,
      create_time DATETIME NULL,
      UNIQUE KEY uk_word (word)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
    """
)
cur.execute(
    """
    CREATE TABLE IF NOT EXISTS ai_call_log (
      id BIGINT PRIMARY KEY AUTO_INCREMENT,
      tenant_id BIGINT NOT NULL,
      scene VARCHAR(64) NULL,
      success TINYINT DEFAULT 1,
      cost_ms INT NULL,
      remark VARCHAR(255) NULL,
      create_time DATETIME NULL,
      KEY idx_tenant_time (tenant_id, create_time)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
    """
)

for w in ["赌博", "色情", "诈骗", "传销", "暴力", "违禁品", "刷单", "代孕"]:
    cur.execute("select id from sys_sensitive_word where word=%s", (w,))
    if not cur.fetchone():
        try:
            cur.execute("insert into sys_sensitive_word(word, create_time) values(%s, now())", (w,))
        except Exception:
            cur.execute("insert into sys_sensitive_word(word) values(%s)", (w,))

cur.execute("select count(1) from ai_call_log")
n = cur.fetchone()[0]
if n < 30:
    scenes = ["chat", "audit", "image", "emotion", "lost", "summary", "notice"]
    for i in range(40):
        tid = 1 if i % 3 else 2
        scene = scenes[i % len(scenes)]
        success = 0 if i % 9 == 0 else 1
        cost = random.randint(80, 1800)
        dt = datetime.now() - timedelta(days=random.randint(0, 10), hours=random.randint(0, 20))
        cur.execute(
            "insert into ai_call_log(tenant_id,scene,success,cost_ms,remark,create_time) values(%s,%s,%s,%s,%s,%s)",
            (tid, scene, success, cost, "deepseek-chat" if success else "timeout/mock", dt),
        )
    print("ai_call_log seeded")
else:
    print("ai_call_log exists", n)

# 调整到期：阳光花园 5 天内紧急，再造几个演示租户
cur.execute(
    "update sys_tenant set expire_time=date_add(now(), interval 5 day) where id=1 and deleted=0"
)
cur.execute(
    "update sys_tenant set expire_time=date_add(now(), interval 17 day) where id=2 and deleted=0"
)

extras = [
    ("翠湖湾社区", 3, 8, "active"),
    ("锦绣城社区", 2, 45, "active"),
    ("已到期样例社区", 1, -3, "degraded"),
]
for name, pkg, days, status in extras:
    cur.execute("select id from sys_tenant where name=%s", (name,))
    if cur.fetchone():
        continue
    feats = "basic_repair,basic_notice,ai_chat,ai_image_repair"
    if days < 0:
        feats = "basic_repair,basic_notice"
    cur.execute(
        "insert into sys_tenant(name,address,contact_name,contact_phone,package_id,features,status,"
        "expire_time,storage_limit_mb,ai_quota,deleted,create_time) "
        "values(%s,%s,%s,%s,%s,%s,%s,date_add(now(), interval %s day),1024,200,0,now())",
        (name, "示例市", "运营联系人", "13800009999", pkg, feats, status, days),
    )
    print("tenant+", name)

# ---------- 主业务闭环：订单演示（≥1 待支付 + 若干已付） ----------
def ensure_order(order_no, tenant_id, package_id, package_name, cycle, amount, order_type, status, days_ago=0):
    cur.execute("select id from saas_order where order_no=%s", (order_no,))
    if cur.fetchone():
        return
    pay = None
    start = None
    end = None
    if status == "paid":
        pay = datetime.now() - timedelta(days=max(days_ago, 1))
        start = pay
        end = datetime.now() + timedelta(days=300)
    cur.execute(
        "insert into saas_order(order_no,tenant_id,package_id,package_name,cycle,amount,order_type,status,"
        "pay_time,start_time,end_time,create_time) values(%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s)",
        (
            order_no,
            tenant_id,
            package_id,
            package_name,
            cycle,
            amount,
            order_type,
            status,
            pay,
            start,
            end,
            datetime.now() - timedelta(days=days_ago),
        ),
    )
    print("order+", order_no, status)


cur.execute("select id from sys_tenant where deleted=0 order by id limit 5")
tids = [r[0] for r in cur.fetchall()]
if len(tids) >= 1:
    ensure_order("SOMAIN1001", tids[0], 3, "专业版", "year", 2999, "new", "paid", 120)
    ensure_order("SOMAIN1002", tids[0], 3, "专业版", "year", 2999, "renew", "pending", 0)
if len(tids) >= 2:
    ensure_order("SOMAIN1003", tids[1], 2, "基础版", "year", 999, "new", "paid", 90)
    ensure_order("SOMAIN1004", tids[1], 2, "基础版", "month", 99, "renew", "pending", 1)
if len(tids) >= 3:
    ensure_order("SOMAIN1005", tids[2], 3, "专业版", "year", 2999, "new", "paid", 60)

conn.commit()
cur.execute("select count(1) from sys_sensitive_word")
print("sensitive", cur.fetchone()[0])
cur.execute("select count(1) from ai_call_log")
print("calls", cur.fetchone()[0])
cur.execute(
    "select id,name,status,datediff(expire_time,now()) from sys_tenant where deleted=0 order by id"
)
print("tenants", cur.fetchall())
cur.execute("select status, count(1) from saas_order group by status")
print("orders", cur.fetchall())
conn.close()
