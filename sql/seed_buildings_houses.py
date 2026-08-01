# -*- coding: utf-8 -*-
"""补齐阳光花园楼栋信息 + 房屋档案演示数据（utf8mb4）。"""
import pymysql

TENANT = 1
COMMUNITY = "阳光花园社区"

conn = pymysql.connect(
    host="127.0.0.1",
    user="root",
    password="root",
    database="smart_community",
    charset="utf8mb4",
)
cur = conn.cursor()


def ensure_cols():
    for col, ddl in [
        ("layout_type", "varchar(32) null"),
        ("area_sqm", "decimal(10,2) null"),
        ("occupy_status", "varchar(16) null default 'VACANT'"),
    ]:
        cur.execute(
            "select count(1) from information_schema.columns "
            "where table_schema=database() and table_name='community_house' and column_name=%s",
            (col,),
        )
        if cur.fetchone()[0] == 0:
            cur.execute(f"alter table community_house add column {col} {ddl}")
            print("added", col)


def upsert(building, unit, room, layout, area, occupy, status=1):
    cur.execute(
        "select id from community_house where tenant_id=%s and building=%s and unit=%s and room_no=%s",
        (TENANT, building, unit, room),
    )
    row = cur.fetchone()
    if row:
        cur.execute(
            "update community_house set community_name=%s, layout_type=%s, area_sqm=%s, "
            "occupy_status=%s, status=%s where id=%s",
            (COMMUNITY, layout, area, occupy, status, row[0]),
        )
        return row[0]
    cur.execute(
        "insert into community_house(tenant_id,community_name,building,unit,room_no,"
        "layout_type,area_sqm,occupy_status,status,create_time) "
        "values(%s,%s,%s,%s,%s,%s,%s,%s,%s,now())",
        (TENANT, COMMUNITY, building, unit, room, layout, area, occupy, status),
    )
    return cur.lastrowid


def main():
    ensure_cols()

    # 纠正已有乱码/半截中文：按 id 重写 1-6
    fixes = [
        (1, "1栋", "1单元", "101", "两室一厅", 89.5, "SELF"),
        (2, "1栋", "1单元", "102", "两室一厅", 92.0, "RENT"),
        (3, "1栋", "2单元", "201", "三室一厅", 110.0, "SELF"),
        (4, "2栋", "1单元", "101", "一室一厅", 56.0, "VACANT"),
        (5, "2栋", "1单元", "202", "两室一厅", 88.0, "SELF"),
        (6, "3栋", "1单元", "301", "三室两厅", 128.0, "RENT"),
    ]
    for hid, b, u, r, layout, area, occupy in fixes:
        cur.execute("select id from community_house where id=%s", (hid,))
        if cur.fetchone():
            cur.execute(
                "update community_house set tenant_id=%s, community_name=%s, building=%s, unit=%s, "
                "room_no=%s, layout_type=%s, area_sqm=%s, occupy_status=%s, status=1 where id=%s",
                (TENANT, COMMUNITY, b, u, r, layout, area, occupy, hid),
            )
        else:
            cur.execute(
                "insert into community_house(id,tenant_id,community_name,building,unit,room_no,"
                "layout_type,area_sqm,occupy_status,status,create_time) "
                "values(%s,%s,%s,%s,%s,%s,%s,%s,%s,1,now())",
                (hid, TENANT, COMMUNITY, b, u, r, layout, area, occupy),
            )

    # 扩充：1-5栋 × 2单元 × 每单元 101-104 / 201-204
    layouts = ["一室一厅", "两室一厅", "两室两厅", "三室一厅", "三室两厅"]
    occupies = ["VACANT", "SELF", "RENT", "SELF", "VACANT", "RENT", "OTHER", "SELF"]
    areas = [48.0, 78.5, 89.0, 96.5, 108.0, 118.0, 126.5, 135.0]
    n = 0
    for bi, building in enumerate(["1栋", "2栋", "3栋", "4栋", "5栋"], 1):
        for unit in ["1单元", "2单元"]:
            for floor in (1, 2):
                for seq in range(1, 5):
                    room = f"{floor}0{seq}"
                    layout = layouts[(bi + seq) % len(layouts)]
                    area = areas[(bi + floor + seq) % len(areas)]
                    occupy = occupies[n % len(occupies)]
                    upsert(building, unit, room, layout, area, occupy)
                    n += 1

    # 同步住户 / 申请单房屋文案
    try:
        cur.execute(
            """
            update household_resident r
            inner join community_house h on h.id=r.house_id
            set r.house_label = concat(h.community_name, ' · ', h.building, h.unit, h.room_no),
                r.update_time = now()
            where r.tenant_id=%s
            """,
            (TENANT,),
        )
        cur.execute(
            """
            update house_move_apply a
            inner join community_house h on h.id=a.house_id
            set a.house_label = concat(h.community_name, ' · ', h.building, h.unit, h.room_no)
            where a.tenant_id=%s
            """,
            (TENANT,),
        )
    except Exception as e:
        print("sync label skip", e)

    # 星河湾也补一点
    for b, u, r in [("A栋", "1单元", "101"), ("A栋", "1单元", "102"), ("A栋", "2单元", "201"),
                    ("B栋", "1单元", "301"), ("B栋", "1单元", "302")]:
        cur.execute(
            "select id from community_house where tenant_id=2 and building=%s and unit=%s and room_no=%s",
            (b, u, r),
        )
        if not cur.fetchone():
            cur.execute(
                "insert into community_house(tenant_id,community_name,building,unit,room_no,"
                "layout_type,area_sqm,occupy_status,status,create_time) "
                "values(2,'星河湾社区',%s,%s,%s,'两室一厅',90,'VACANT',1,now())",
                (b, u, r),
            )

    conn.commit()

    cur.execute(
        "select building, count(distinct unit), count(1) from community_house where tenant_id=1 group by building order by building"
    )
    print("tenant1 buildings:", cur.fetchall())
    cur.execute("select count(1) from community_house where tenant_id=1")
    print("tenant1 houses:", cur.fetchone()[0])
    cur.execute("select building, unit, room_no, layout_type, occupy_status from community_house where tenant_id=1 order by building,unit,room_no limit 12")
    for r in cur.fetchall():
        print(r)


if __name__ == "__main__":
    main()
    conn.close()
