# -*- coding: utf-8 -*-
"""
阳光花园(tenant=1) 全模块演示数据种子。
用法: python sql/seed_demo_all.py
幂等：按唯一键/标题检测，已存在则跳过或刷新计数。
"""
from datetime import datetime, timedelta
import pymysql

TENANT = 1
ADMIN_ID = 2
GRID_ID = 3
RESIDENT_IDS = []  # filled at runtime

conn = pymysql.connect(
    host="127.0.0.1",
    user="root",
    password="root",
    database="smart_community",
    charset="utf8mb4",
    autocommit=False,
)
cur = conn.cursor()


def q(sql, args=None):
    cur.execute(sql, args or ())
    return cur.fetchall()


def one(sql, args=None):
    cur.execute(sql, args or ())
    return cur.fetchone()


def exe(sql, args=None):
    cur.execute(sql, args or ())


def col_exists(table, col):
    r = one(
        "SELECT COUNT(1) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=%s AND COLUMN_NAME=%s",
        (table, col),
    )
    return r and r[0] > 0


def add_col(table, col, ddl):
    if not col_exists(table, col):
        exe(f"ALTER TABLE {table} ADD COLUMN {ddl}")
        print("added", table, col)


def ensure_schema():
    # activity
    exe(
        """
        CREATE TABLE IF NOT EXISTS activity_category (
          id bigint primary key auto_increment,
          tenant_id bigint not null,
          name varchar(64) not null,
          tags varchar(255) null,
          sort_no int not null default 0,
          status tinyint not null default 1,
          remark varchar(255) null,
          create_time datetime null,
          unique key uk_act_cat (tenant_id, name)
        )
        """
    )
    for col, ddl in [
        ("summary", "summary varchar(200) null"),
        ("tags", "tags varchar(255) null"),
        ("target_rule", "target_rule varchar(255) null"),
        ("signup_end", "signup_end datetime null"),
        ("ai_audit_pass", "ai_audit_pass tinyint null"),
        ("push_count", "push_count int not null default 0"),
        ("cover_url", "cover_url varchar(512) null"),
        ("category", "category varchar(64) null"),
        ("allow_cancel", "allow_cancel tinyint default 1"),
    ]:
        add_col("community_activity", col, ddl)

    # lost
    exe(
        """
        CREATE TABLE IF NOT EXISTS lost_category (
          id bigint primary key auto_increment,
          tenant_id bigint not null,
          name varchar(64) not null,
          sort_no int not null default 0,
          status tinyint not null default 1,
          remark varchar(255) null,
          create_time datetime null,
          unique key uk_lost_cat (tenant_id, name)
        )
        """
    )
    exe(
        """
        CREATE TABLE IF NOT EXISTS lost_claim (
          id bigint primary key auto_increment,
          tenant_id bigint not null,
          lost_id bigint not null,
          user_id bigint not null,
          user_name varchar(64) null,
          phone varchar(32) null,
          remark varchar(512) null,
          status varchar(32) not null default 'pending',
          audit_remark varchar(255) null,
          auditor_id bigint null,
          audit_time datetime null,
          create_time datetime null,
          key idx_claim_tenant (tenant_id, status, create_time),
          key idx_claim_lost (lost_id, status)
        )
        """
    )
    for col, ddl in [
        ("item_type", "item_type varchar(16) default 'found'"),
        ("house_id", "house_id bigint null"),
        ("house_label", "house_label varchar(128) null"),
        ("contact_name", "contact_name varchar(64) null"),
        ("category", "category varchar(64) null"),
        ("ai_audit_pass", "ai_audit_pass tinyint null"),
        ("claim_count", "claim_count int not null default 0"),
    ]:
        add_col("lost_found", col, ddl)

    # move
    exe(
        """
        CREATE TABLE IF NOT EXISTS house_move_apply (
          id bigint primary key auto_increment,
          apply_no varchar(64) not null,
          tenant_id bigint not null,
          user_id bigint not null,
          house_id bigint not null,
          apply_type varchar(32) not null,
          identity_type varchar(32) null,
          applicant_name varchar(64) null,
          applicant_phone varchar(32) null,
          house_label varchar(128) null,
          status varchar(32) not null default 'PENDING',
          remark varchar(255) null,
          audit_user_id bigint null,
          audit_name varchar(64) null,
          audit_remark varchar(255) null,
          audit_time datetime null,
          create_time datetime null,
          unique key uk_apply_no (apply_no),
          key idx_tenant_status (tenant_id, status)
        )
        """
    )


def seed_users_houses():
    global RESIDENT_IDS
    # ensure a few more demo residents
    extra = [
        ("13800000022", "居民小红", "1栋102"),
        ("13800000023", "居民小刚", "1栋201"),
        ("13800000024", "居民小美", "2栋101"),
    ]
    pwd = "e10adc3949ba59abbe56e057f20f883e"
    for phone, nick, room in extra:
        if not one("select id from sys_user where phone=%s", (phone,)):
            exe(
                "insert into sys_user(tenant_id,phone,password,nickname,role,room_no,status,create_time) values(%s,%s,%s,%s,'resident',%s,1,now())",
                (TENANT, phone, pwd, nick, room),
            )
            print("user+", phone, nick)

    rows = q(
        "select id from sys_user where tenant_id=%s and role='resident' and status=1 order by id",
        (TENANT,),
    )
    RESIDENT_IDS = [r[0] for r in rows]
    print("residents", RESIDENT_IDS)

    houses = q("select id, building, unit, room_no from community_house where tenant_id=%s order by id", (TENANT,))
    if not houses:
        demo_houses = [
            ("1栋", "1单元", "101"),
            ("1栋", "1单元", "102"),
            ("1栋", "2单元", "201"),
            ("2栋", "1单元", "101"),
            ("2栋", "1单元", "202"),
            ("3栋", "1单元", "301"),
        ]
        for b, u, r in demo_houses:
            exe(
                "insert into community_house(tenant_id,community_name,building,unit,room_no,create_time) values(%s,'阳光花园',%s,%s,%s,now())",
                (TENANT, b, u, r),
            )
        houses = q(
            "select id, building, unit, room_no from community_house where tenant_id=%s order by id",
            (TENANT,),
        )
    print("houses", len(houses))
    return houses


def house_label(h):
    return f"阳光花园 · {h[1]}{h[2]}{h[3]}"


def ensure_bind(uid, hid):
    if one("select id from user_house_bind where user_id=%s and house_id=%s", (uid, hid)):
        return
    cnt = one(
        "select count(1) from user_house_bind where user_id=%s and tenant_id=%s",
        (uid, TENANT),
    )[0]
    exe(
        "insert into user_house_bind(tenant_id,user_id,house_id,is_default,create_time) values(%s,%s,%s,%s,now())",
        (TENANT, uid, hid, 1 if cnt == 0 else 0),
    )


def seed_household(houses):
    if not RESIDENT_IDS:
        return
    for i, uid in enumerate(RESIDENT_IDS[:6]):
        h = houses[i % len(houses)]
        u = one("select nickname, phone from sys_user where id=%s", (uid,))
        ensure_bind(uid, h[0])
        exists = one(
            "select id from household_resident where tenant_id=%s and house_id=%s and phone=%s",
            (TENANT, h[0], u[1]),
        )
        if not exists:
            exe(
                """insert into household_resident(tenant_id,user_id,house_id,name,phone,identity_type,status,source,house_label,create_time,update_time)
                   values(%s,%s,%s,%s,%s,%s,'ACTIVE','SEED',%s,now(),now())""",
                (
                    TENANT,
                    uid,
                    h[0],
                    u[0],
                    u[1],
                    "OWNER" if i % 2 == 0 else "TENANT",
                    house_label(h),
                ),
            )
            print("household+", u[0])


def seed_activity_categories():
    cats = [
        ("邻里文体", "文体,邻里", 1),
        ("公益志愿", "公益,志愿", 2),
        ("物业通知", "物业,公告", 3),
        ("亲子活动", "亲子,儿童", 4),
        ("老年活动", "老年,养生", 5),
    ]
    for name, tags, sort in cats:
        if not one("select id from activity_category where tenant_id=%s and name=%s", (TENANT, name)):
            exe(
                "insert into activity_category(tenant_id,name,tags,sort_no,status,create_time) values(%s,%s,%s,%s,1,now())",
                (TENANT, name, tags, sort),
            )


def seed_activities():
    now = datetime.now()
    acts = [
        (
            "夏日邻里篮球赛",
            "邻里文体",
            "文体,邻里",
            "小区篮球场",
            "周末篮球友谊赛，欢迎各栋报名组队，奖品丰厚。",
            "周末篮球友谊赛，欢迎组队报名。",
            now + timedelta(days=3),
            now + timedelta(days=3, hours=3),
            40,
            1,
        ),
        (
            "亲子手工DIY",
            "亲子活动",
            "亲子,儿童",
            "社区活动中心",
            "亲子陶艺与绘画体验课，需家长陪同。",
            "亲子陶艺绘画体验，家长陪同。",
            now + timedelta(days=5),
            now + timedelta(days=5, hours=2),
            30,
            1,
        ),
        (
            "垃圾分类志愿日",
            "公益志愿",
            "公益,志愿",
            "小区东门广场",
            "宣传垃圾分类知识并协助投放点整理。",
            "垃圾分类志愿宣传与协助。",
            now + timedelta(days=7),
            now + timedelta(days=7, hours=2),
            50,
            1,
        ),
        (
            "老年太极晨练",
            "老年活动",
            "老年,养生",
            "中心花园",
            "每日晨练打卡，教练现场指导。",
            "太极晨练，适合中老年住户。",
            now + timedelta(days=1),
            now + timedelta(days=1, hours=1),
            60,
            1,
        ),
        (
            "停水检修说明会（已结束）",
            "物业通知",
            "物业,公告",
            "物业会议室",
            "管网检修说明与供水恢复安排。",
            "管网检修说明会。",
            now - timedelta(days=10),
            now - timedelta(days=10) + timedelta(hours=2),
            80,
            0,
        ),
    ]
    for title, cat, tags, loc, content, summary, st, et, maxp, status in acts:
        row = one("select id from community_activity where tenant_id=%s and title=%s", (TENANT, title))
        if row:
            aid = row[0]
        else:
            exe(
                """insert into community_activity(
                    tenant_id,title,category,cover_url,content,summary,tags,target_rule,location,
                    start_time,end_time,signup_end,max_people,allow_cancel,signed_count,publisher_id,status,ai_audit_pass,push_count,create_time)
                   values(%s,%s,%s,null,%s,%s,%s,%s,%s,%s,%s,%s,%s,1,0,%s,%s,1,0,now())""",
                (
                    TENANT,
                    title,
                    cat,
                    content,
                    summary,
                    tags,
                    "all" if "老年" not in tags and "亲子" not in tags else ("senior" if "老年" in tags else "亲子"),
                    loc,
                    st,
                    et,
                    st - timedelta(hours=12),
                    maxp,
                    ADMIN_ID,
                    status,
                ),
            )
            aid = cur.lastrowid
            print("activity+", title)

        # signups
        if not RESIDENT_IDS:
            continue
        signed = 0
        for j, uid in enumerate(RESIDENT_IDS[:4]):
            if one(
                "select id from activity_signup where activity_id=%s and user_id=%s",
                (aid, uid),
            ):
                signed += 1
                continue
            u = one("select nickname, phone from sys_user where id=%s", (uid,))
            stt = "checked" if j == 0 else ("cancelled" if j == 3 else "signed")
            exe(
                """insert into activity_signup(tenant_id,activity_id,user_id,user_name,phone,status,create_time)
                   values(%s,%s,%s,%s,%s,%s,now())""",
                (TENANT, aid, uid, u[0], u[1], stt),
            )
            if stt != "cancelled":
                signed += 1
        exe("update community_activity set signed_count=%s where id=%s", (signed, aid))


def seed_lost(houses):
    cats = ["钥匙卡证", "数码电子", "衣物饰品", "生活用品", "证件票据", "其他"]
    for i, name in enumerate(cats, 1):
        if not one("select id from lost_category where tenant_id=%s and name=%s", (TENANT, name)):
            exe(
                "insert into lost_category(tenant_id,name,sort_no,status,create_time) values(%s,%s,%s,1,now())",
                (TENANT, name, i),
            )

    items = [
        ("丢失门禁卡", "lost", "钥匙卡证", "蓝色门禁卡，背面有1栋字样", "open"),
        ("捡到儿童手表", "found", "数码电子", "粉色儿童电话手表，在中心花园捡到", "open"),
        ("丢失黑色雨伞", "lost", "生活用品", "折叠伞，伞柄有贴纸", "open"),
        ("捡到身份证", "found", "证件票据", "在东门快递柜旁捡到，已交物业", "open"),
        ("丢失银色项链", "lost", "衣物饰品", "细链，挂件为心形", "found"),
        ("捡到保温杯", "found", "生活用品", "白色保温杯，有姓名贴", "open"),
    ]
    for i, (title, itype, cat, desc, status) in enumerate(items):
        row = one("select id from lost_found where tenant_id=%s and title=%s", (TENANT, title))
        uid = RESIDENT_IDS[i % len(RESIDENT_IDS)] if RESIDENT_IDS else 4
        h = houses[i % len(houses)]
        u = one("select nickname, phone from sys_user where id=%s", (uid,))
        if row:
            lid = row[0]
            exe(
                "update lost_found set item_type=%s,category=%s,status=%s,ai_audit_pass=1,house_id=%s,house_label=%s,contact_name=%s where id=%s",
                (itype, cat, status, h[0], house_label(h), u[0], lid),
            )
        else:
            exe(
                """insert into lost_found(tenant_id,user_id,house_id,house_label,title,item_type,category,description,contact,contact_name,image_url,status,ai_audit_pass,claim_count,create_time)
                   values(%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,null,%s,1,0,now())""",
                (
                    TENANT,
                    uid,
                    h[0],
                    house_label(h),
                    title,
                    itype,
                    cat,
                    desc,
                    u[1],
                    u[0],
                    status,
                ),
            )
            lid = cur.lastrowid
            print("lost+", title)

        # claim for open found items
        if status == "open" and itype == "found" and len(RESIDENT_IDS) > 1:
            claimer = RESIDENT_IDS[(i + 1) % len(RESIDENT_IDS)]
            if claimer == uid and len(RESIDENT_IDS) > 2:
                claimer = RESIDENT_IDS[(i + 2) % len(RESIDENT_IDS)]
            if claimer != uid and not one(
                "select id from lost_claim where lost_id=%s and user_id=%s", (lid, claimer)
            ):
                cu = one("select nickname, phone from sys_user where id=%s", (claimer,))
                st = "pending" if i % 2 == 0 else "approved"
                exe(
                    """insert into lost_claim(tenant_id,lost_id,user_id,user_name,phone,remark,status,audit_remark,auditor_id,audit_time,create_time)
                       values(%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,now())""",
                    (
                        TENANT,
                        lid,
                        claimer,
                        cu[0],
                        cu[1],
                        "特征吻合，请求核验",
                        st,
                        None if st == "pending" else "核验通过",
                        None if st == "pending" else ADMIN_ID,
                        None if st == "pending" else datetime.now(),
                    ),
                )
                exe(
                    "update lost_found set claim_count=ifnull(claim_count,0)+1, status=if(%s='approved','found',status) where id=%s",
                    (st, lid),
                )
                print("claim+", title, st)


def seed_moves(houses):
    if not RESIDENT_IDS:
        return
    samples = [
        ("MOVE_IN", "PENDING", None, None, "新入住申请绑定"),
        ("MOVE_OUT", "PENDING", None, None, "租约到期申请解绑"),
        ("MOVE_IN", "APPROVED", "材料齐全，予以通过", ADMIN_ID, "迁入已办结"),
        ("MOVE_OUT", "APPROVED", "确认已退租", ADMIN_ID, "迁出已办结"),
        ("MOVE_IN", "REJECTED", "房屋信息与产权不符", ADMIN_ID, "资料不全"),
        ("MOVE_OUT", "REJECTED", "仍有欠费未结清", ADMIN_ID, "欠费未清"),
    ]
    for i, (atype, status, arem, auditor, remark) in enumerate(samples):
        uid = RESIDENT_IDS[i % len(RESIDENT_IDS)]
        h = houses[(i + 1) % len(houses)]
        u = one("select nickname, phone from sys_user where id=%s", (uid,))
        # skip if similar pending already for same user+house+type
        if one(
            "select id from house_move_apply where tenant_id=%s and user_id=%s and house_id=%s and apply_type=%s and status=%s",
            (TENANT, uid, h[0], atype, status),
        ):
            continue
        prefix = "MI" if atype == "MOVE_IN" else "MO"
        apply_no = f"{prefix}SEED{datetime.now().strftime('%m%d%H%M')}{i}{uid}"
        exe(
            """insert into house_move_apply(
                apply_no,tenant_id,user_id,house_id,apply_type,identity_type,applicant_name,applicant_phone,
                house_label,status,remark,audit_user_id,audit_name,audit_remark,audit_time,create_time)
               values(%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,date_sub(now(), interval %s day))""",
            (
                apply_no,
                TENANT,
                uid,
                h[0],
                atype,
                "OWNER" if i % 2 == 0 else "TENANT",
                u[0],
                u[1],
                house_label(h),
                status,
                remark,
                auditor,
                "阳光物业管理员" if auditor else None,
                arem,
                datetime.now() - timedelta(days=1) if status != "PENDING" else None,
                i + 1,
            ),
        )
        print("move+", apply_no, status)


def seed_notices_repairs_visits():
    notices = [
        ("电梯维保通知", "本周四对各栋电梯进行例行维保，请错峰出行。", "周四电梯维保"),
        ("中秋游园活动预告", "中秋夜中心花园灯会，欢迎居民参与。", "中秋灯会预告"),
        ("地库清洗安排", "下周一清洗地库，请提前挪车。", "地库清洗挪车"),
    ]
    for title, content, summary in notices:
        if not one("select id from notice_info where tenant_id=%s and title=%s", (TENANT, title)):
            exe(
                """insert into notice_info(tenant_id,title,content,summary,publisher_id,status,publish_time,create_time)
                   values(%s,%s,%s,%s,%s,1,now(),now())""",
                (TENANT, title, content, summary, ADMIN_ID),
            )
            print("notice+", title)

    repairs = [
        ("地下车库积水", "地库B区有积水，请尽快处理", "排水", "中度", "pending"),
        ("单元门对讲失灵", "1栋2单元对讲无法呼叫", "门禁", "轻度", "processing"),
        ("花园路灯不亮", "中心花园东侧路灯损坏", "照明", "轻度", "done"),
    ]
    for title, desc, ftype, dmg, status in repairs:
        if not one("select id from repair_order where tenant_id=%s and title=%s", (TENANT, title)):
            uid = RESIDENT_IDS[0] if RESIDENT_IDS else 4
            exe(
                """insert into repair_order(tenant_id,user_id,title,description,facility_type,damage_level,status,create_time,update_time)
                   values(%s,%s,%s,%s,%s,%s,%s,now(),now())""",
                (TENANT, uid, title, desc, ftype, dmg, status),
            )
            print("repair+", title)

    # visitor
    if RESIDENT_IDS and col_exists("visitor_appointment", "visitor_name"):
        for i, name in enumerate(["王访客", "李快递", "赵亲友"]):
            if one(
                "select id from visitor_appointment where tenant_id=%s and visitor_name=%s",
                (TENANT, name),
            ):
                continue
            uid = RESIDENT_IDS[i % len(RESIDENT_IDS)]
            h = one("select house_id from user_house_bind where user_id=%s limit 1", (uid,))
            exe(
                """insert into visitor_appointment(tenant_id,user_id,house_id,visitor_name,visitor_phone,drive_flag,visit_reason,visit_time,expire_time,visitor_count,status,create_time)
                   values(%s,%s,%s,%s,%s,0,%s,date_add(now(), interval %s hour),date_add(now(), interval 1 day),1,'approved',now())""",
                (
                    TENANT,
                    uid,
                    h[0] if h else None,
                    name,
                    f"1390000{100+i}",
                    "探亲访友" if i != 1 else "快递派送",
                    i + 2,
                ),
            )
            print("visit+", name)


def main():
    ensure_schema()
    houses = seed_users_houses()
    seed_household(houses)
    seed_activity_categories()
    seed_activities()
    seed_lost(houses)
    seed_moves(houses)
    seed_notices_repairs_visits()
    conn.commit()

    print("\n=== counts tenant=1 ===")
    for t in [
        "community_activity",
        "activity_signup",
        "activity_category",
        "lost_found",
        "lost_claim",
        "lost_category",
        "house_move_apply",
        "household_resident",
        "notice_info",
        "repair_order",
        "visitor_appointment",
        "user_house_bind",
    ]:
        try:
            print(t, one(f"select count(1) from {t} where tenant_id=%s", (TENANT,))[0])
        except Exception as e:
            print(t, "err", e)


if __name__ == "__main__":
    try:
        main()
    except Exception:
        conn.rollback()
        raise
    finally:
        conn.close()
