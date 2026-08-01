# -*- coding: utf-8 -*-
import pymysql

conn = pymysql.connect(
    host="127.0.0.1",
    user="root",
    password="root",
    database="smart_community",
    charset="utf8mb4",
)
cur = conn.cursor()

cur.execute("DELETE FROM store_product WHERE tenant_id=1 AND id>=5")
cur.execute("DELETE FROM store_category WHERE tenant_id=1 AND (name LIKE '%?%' OR id=4)")
cur.execute("UPDATE store_category SET name=%s, sort_no=1, status=1 WHERE id=1", ("饮品酒水",))
cur.execute("UPDATE store_category SET name=%s, sort_no=2, status=1 WHERE id=2", ("零食速食",))
cur.execute("UPDATE store_category SET name=%s, sort_no=3, status=1 WHERE id=3", ("日用百货",))
cur.execute(
    """UPDATE store_info SET name=%s, address=%s, min_order_amount=15, delivery_fee=2,
       open_time='08:00', close_time='22:00', status=1, remark=%s WHERE tenant_id=1""",
    ("阳光花园社区超市", "小区东门便利店（到店自提）", "首期不售生鲜，支持送货上门/到店自提"),
)


def cover(bg, fg, ch):
    return (
        'data:image/svg+xml;utf8,<svg xmlns="http://www.w3.org/2000/svg" width="200" height="200">'
        f'<rect fill="{bg}" width="200" height="200"/>'
        f'<text x="100" y="108" text-anchor="middle" font-size="42" fill="{fg}">{ch}</text></svg>'
    )


products = [
    (1, "农夫山泉 550ml", cover("%23d4f0e6", "%232f9e6f", "水"), 2.00, 200, 10, "常温饮品"),
    (1, "可口可乐 330ml", cover("%23ffe0dc", "%23d64545", "乐"), 3.50, 150, 20, None),
    (1, "雪碧 330ml", cover("%23e0f7e9", "%232f9e6f", "雪"), 3.50, 140, 30, None),
    (1, "康师傅冰红茶 500ml", cover("%23fff0d6", "%23d4890a", "茶"), 4.00, 120, 40, None),
    (1, "青岛啤酒 500ml", cover("%23fff6d9", "%23b8860b", "啤"), 6.00, 80, 50, "酒水"),
    (1, "纯牛奶 250ml*12", cover("%23f3f7ff", "%234a7fd4", "奶"), 42.00, 40, 60, "常温奶"),
    (2, "乐事薯片原味 70g", cover("%23fff3d6", "%23e39b00", "片"), 8.50, 90, 10, None),
    (2, "奥利奥夹心饼干", cover("%23ebe4d8", "%235c4634", "饼"), 9.90, 70, 20, None),
    (2, "卫龙亲嘴烧", cover("%23ffe8e0", "%23d35400", "辣"), 3.50, 100, 30, None),
    (2, "统一老坛酸菜面", cover("%23fff0e0", "%23c27803", "面"), 4.50, 110, 40, "速食"),
    (2, "自热米饭（咖喱鸡）", cover("%23ffe9d6", "%23b35c00", "饭"), 16.80, 50, 50, "速食"),
    (2, "旺旺雪饼 150g", cover("%23fff8e8", "%23c9a227", "饼"), 7.50, 60, 60, None),
    (3, "清风抽纸 3层*6包", cover("%23e8f5f2", "%2347b686", "纸"), 18.90, 60, 10, None),
    (3, "蓝月亮洗衣液 1kg", cover("%23e3f0ff", "%233b7ddd", "洗"), 29.90, 45, 20, None),
    (3, "舒肤佳香皂 125g", cover("%23e8f8ff", "%232a9fd6", "皂"), 6.50, 80, 30, None),
    (3, "垃圾袋 中号 40只", cover("%23f0f3f2", "%235a6562", "袋"), 9.90, 70, 40, None),
    (3, "南孚电池 5号 4粒", cover("%23fff0e8", "%23d35400", "电"), 14.80, 55, 50, None),
    (3, "得力中性笔 0.5mm*12", cover("%23eef6ff", "%233d6fb8", "笔"), 12.00, 65, 60, None),
]

# 下架旧短名演示商品，避免与新测试数据重复
cur.execute(
    "UPDATE store_product SET status=0 WHERE tenant_id=1 AND name IN (%s,%s,%s,%s)",
    ("矿泉水 550ml", "可乐 330ml", "薯片原味", "抽纸 3层"),
)

for cid, name, cv, price, stock, sort, remark in products:
    cur.execute("SELECT id FROM store_product WHERE tenant_id=1 AND name=%s", (name,))
    if cur.fetchone():
        cur.execute(
            "UPDATE store_product SET category_id=%s, cover_url=%s, price=%s, stock=%s, status=1, sort_no=%s, remark=%s WHERE tenant_id=1 AND name=%s",
            (cid, cv, price, stock, sort, remark, name),
        )
    else:
        cur.execute(
            "INSERT INTO store_product(tenant_id,category_id,name,cover_url,price,stock,status,sort_no,remark) VALUES(1,%s,%s,%s,%s,%s,1,%s,%s)",
            (cid, name, cv, price, stock, sort, remark),
        )

conn.commit()
cur.execute("SELECT name FROM store_category WHERE tenant_id=1 AND status=1 ORDER BY sort_no")
print("cats:", cur.fetchall())
cur.execute(
    "SELECT c.name, COUNT(*) FROM store_product p JOIN store_category c ON c.id=p.category_id "
    "WHERE p.tenant_id=1 AND p.status=1 GROUP BY c.name"
)
print("cnt:", cur.fetchall())
conn.close()
print("done")
