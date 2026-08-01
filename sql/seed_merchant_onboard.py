# -*- coding: utf-8 -*-
"""商圈入驻申请 + 交易佣金应收演示（≠ SaaS 套餐订购账务）"""
from datetime import datetime, timedelta
from decimal import Decimal

import pymysql

conn = pymysql.connect(
    host="127.0.0.1",
    user="root",
    password="root",
    database="smart_community",
    charset="utf8mb4",
)
cur = conn.cursor()

for ddl in [
    """
    CREATE TABLE IF NOT EXISTS store_merchant (
      id BIGINT PRIMARY KEY AUTO_INCREMENT,
      tenant_id BIGINT NOT NULL,
      name VARCHAR(128) NOT NULL,
      shop_type VARCHAR(64) NULL,
      contact_name VARCHAR(64) NULL,
      contact_phone VARCHAR(32) NULL,
      status TINYINT NOT NULL DEFAULT 1,
      onboard_status VARCHAR(32) NULL DEFAULT 'APPROVED',
      warn_threshold DECIMAL(12,2) NOT NULL DEFAULT 100.00,
      commission_rate DECIMAL(5,2) NULL DEFAULT 5.00,
      remark VARCHAR(255) NULL,
      create_time DATETIME NULL,
      update_time DATETIME NULL,
      KEY idx_sm_tenant (tenant_id, status)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
    """,
    """
    CREATE TABLE IF NOT EXISTS store_merchant_apply (
      id BIGINT PRIMARY KEY AUTO_INCREMENT,
      tenant_id BIGINT NOT NULL,
      merchant_name VARCHAR(128) NOT NULL,
      shop_type VARCHAR(64) NULL,
      contact_name VARCHAR(64) NULL,
      contact_phone VARCHAR(32) NULL,
      address VARCHAR(255) NULL,
      business_license VARCHAR(128) NULL,
      license_url VARCHAR(512) NULL,
      intro VARCHAR(500) NULL,
      commission_rate DECIMAL(5,2) NOT NULL DEFAULT 5.00,
      status VARCHAR(32) NOT NULL DEFAULT 'PENDING_PROPERTY',
      reject_reason VARCHAR(255) NULL,
      merchant_id BIGINT NULL,
      applicant_id BIGINT NULL,
      applicant_name VARCHAR(64) NULL,
      property_auditor_id BIGINT NULL,
      property_auditor_name VARCHAR(64) NULL,
      property_audit_time DATETIME NULL,
      property_audit_remark VARCHAR(255) NULL,
      platform_auditor_id BIGINT NULL,
      platform_auditor_name VARCHAR(64) NULL,
      platform_audit_time DATETIME NULL,
      platform_audit_remark VARCHAR(255) NULL,
      create_time DATETIME NULL,
      update_time DATETIME NULL,
      KEY idx_sma_tenant_status (tenant_id, status),
      KEY idx_sma_status (status, create_time)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
    """,
    """
    CREATE TABLE IF NOT EXISTS store_merchant_audit_log (
      id BIGINT PRIMARY KEY AUTO_INCREMENT,
      tenant_id BIGINT NOT NULL,
      apply_id BIGINT NOT NULL,
      stage VARCHAR(32) NOT NULL,
      action VARCHAR(32) NOT NULL,
      remark VARCHAR(255) NULL,
      operator_id BIGINT NULL,
      operator_name VARCHAR(64) NULL,
      create_time DATETIME NULL,
      KEY idx_smal_apply (apply_id, create_time)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
    """,
    """
    CREATE TABLE IF NOT EXISTS merchant_commission_bill (
      id BIGINT PRIMARY KEY AUTO_INCREMENT,
      tenant_id BIGINT NOT NULL,
      merchant_id BIGINT NOT NULL,
      order_id BIGINT NULL,
      order_no VARCHAR(64) NULL,
      gmv DECIMAL(14,2) NOT NULL DEFAULT 0,
      commission_rate DECIMAL(5,2) NOT NULL DEFAULT 5.00,
      commission_amount DECIMAL(14,2) NOT NULL DEFAULT 0,
      settle_status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
      settle_time DATETIME NULL,
      settle_batch_no VARCHAR(64) NULL,
      remark VARCHAR(255) NULL,
      create_time DATETIME NULL,
      UNIQUE KEY uk_mcb_order (order_id),
      KEY idx_mcb_merchant (merchant_id, settle_status),
      KEY idx_mcb_tenant (tenant_id, settle_status, create_time)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
    """,
]:
    cur.execute(ddl)

# 扩展列
for col, ddl in [
    ("shop_type", "alter table store_merchant add column shop_type varchar(64) null after name"),
    ("onboard_status", "alter table store_merchant add column onboard_status varchar(32) null default 'APPROVED' after status"),
    ("commission_rate", "alter table store_merchant add column commission_rate decimal(5,2) null default 5.00"),
]:
    cur.execute(
        "select count(1) from information_schema.columns where table_schema=database() "
        "and table_name='store_merchant' and column_name=%s",
        (col,),
    )
    if cur.fetchone()[0] == 0:
        try:
            cur.execute(ddl)
        except Exception:
            pass

applies = [
    # tenant, name, type, contact, phone, status, need_property_pass
    (1, "阳光果切工坊", "餐饮", "陈师傅", "13700001111", "PENDING_PROPERTY", False),
    (1, "邻里洗衣店", "家政服务", "赵姐", "13700002222", "PENDING_PLATFORM", True),
    (1, "社区烘焙屋", "餐饮", "周老板", "13700003333", "APPROVED", True),
    (2, "和谐五金店", "其他", "吴师傅", "13600004444", "PENDING_PLATFORM", True),
    (2, "便民打印社", "其他", "郑小姐", "13600005555", "REJECTED_PROPERTY", False),
]

for tid, name, stype, contact, phone, status, prop_pass in applies:
    cur.execute(
        "select id from store_merchant_apply where tenant_id=%s and merchant_name=%s limit 1",
        (tid, name),
    )
    if cur.fetchone():
        continue
    rate = Decimal("5.00")
    mid = None
    if status == "APPROVED":
        cur.execute(
            "select id from store_merchant where tenant_id=%s and name=%s limit 1",
            (tid, name),
        )
        row = cur.fetchone()
        if row:
            mid = row[0]
        else:
            cur.execute(
                "insert into store_merchant(tenant_id,name,shop_type,contact_name,contact_phone,status,"
                "onboard_status,warn_threshold,commission_rate,remark,create_time,update_time) "
                "values(%s,%s,%s,%s,%s,1,'APPROVED',100,%s,'演示入驻',now(),now())",
                (tid, name, stype, contact, phone, rate),
            )
            mid = cur.lastrowid
            cur.execute(
                "insert ignore into merchant_wallet(tenant_id,merchant_id,balance,total_recharge,total_consume,"
                "total_withdraw,pending_withdraw,status,create_time,update_time) "
                "values(%s,%s,0,0,0,0,0,1,now(),now())",
                (tid, mid),
            )

    cur.execute(
        "insert into store_merchant_apply(tenant_id,merchant_name,shop_type,contact_name,contact_phone,"
        "address,business_license,intro,commission_rate,status,merchant_id,applicant_name,"
        "property_auditor_name,property_audit_time,property_audit_remark,"
        "platform_auditor_name,platform_audit_time,platform_audit_remark,"
        "reject_reason,create_time,update_time) values(%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,now(),now())",
        (
            tid,
            name,
            stype,
            contact,
            phone,
            f"{tid}号小区商铺",
            f"91{tid}{phone[-4:]}",
            f"{name}入驻商圈申请",
            rate,
            status,
            mid,
            "商户申请人",
            "物业管理员" if prop_pass or status.startswith("REJECTED") else None,
            datetime.now() - timedelta(days=2) if prop_pass or status.startswith("REJECTED") else None,
            "初审通过，转平台" if prop_pass and status != "REJECTED_PROPERTY" else ("资料不全" if status == "REJECTED_PROPERTY" else None),
            "平台运营" if status == "APPROVED" else None,
            datetime.now() - timedelta(days=1) if status == "APPROVED" else None,
            "终审通过" if status == "APPROVED" else None,
            "证照信息不清晰" if status == "REJECTED_PROPERTY" else None,
        ),
    )
    apply_id = cur.lastrowid
    # 审核日志
    cur.execute(
        "insert into store_merchant_audit_log(tenant_id,apply_id,stage,action,remark,operator_name,create_time) "
        "values(%s,%s,'PROPERTY','SUBMIT','提交入驻申请','商户申请人',%s)",
        (tid, apply_id, datetime.now() - timedelta(days=3)),
    )
    if prop_pass or status.startswith("REJECTED"):
        act = "REJECT" if status == "REJECTED_PROPERTY" else "APPROVE"
        remark = "证照信息不清晰" if act == "REJECT" else "物业初审通过"
        cur.execute(
            "insert into store_merchant_audit_log(tenant_id,apply_id,stage,action,remark,operator_name,create_time) "
            "values(%s,%s,'PROPERTY',%s,%s,'物业管理员',%s)",
            (tid, apply_id, act, remark, datetime.now() - timedelta(days=2)),
        )
    if status == "APPROVED":
        cur.execute(
            "insert into store_merchant_audit_log(tenant_id,apply_id,stage,action,remark,operator_name,create_time) "
            "values(%s,%s,'PLATFORM','APPROVE','平台终审通过','平台运营',%s)",
            (tid, apply_id, datetime.now() - timedelta(days=1)),
        )

# 确保各租户有可挂佣金的商户
cur.execute("select id, tenant_id from store_merchant")
merchants = cur.fetchall()
merchant_by_tenant = {}
for mid, tid in merchants:
    merchant_by_tenant.setdefault(tid, mid)

# 从已完成订单同步佣金应收
cur.execute(
    "select id, tenant_id, order_no, pay_amount, complete_time, pay_time from store_order "
    "where status='COMPLETED' order by id desc limit 80"
)
orders = cur.fetchall()
for oid, tid, ono, pay, ctime, ptime in orders:
    mid = merchant_by_tenant.get(tid)
    if not mid:
        continue
    cur.execute("select id from merchant_commission_bill where order_id=%s", (oid,))
    if cur.fetchone():
        continue
    gmv = Decimal(str(pay or 0))
    rate = Decimal("5.00")
    commission = (gmv * rate / Decimal("100")).quantize(Decimal("0.01"))
    # 约一半标记已结算
    settled = oid % 2 == 0
    cur.execute(
        "insert into merchant_commission_bill(tenant_id,merchant_id,order_id,order_no,gmv,commission_rate,"
        "commission_amount,settle_status,settle_time,settle_batch_no,remark,create_time) "
        "values(%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s)",
        (
            tid,
            mid,
            oid,
            ono,
            gmv,
            rate,
            commission,
            "SETTLED" if settled else "PENDING",
            datetime.now() - timedelta(days=1) if settled else None,
            f"DEMO{tid}" if settled else None,
            "订单完成自动计提佣金",
            ctime or ptime or datetime.now(),
        ),
    )

# 若无订单，造几笔演示应收
cur.execute("select count(1) from merchant_commission_bill")
if cur.fetchone()[0] == 0 and merchants:
    for i, (mid, tid) in enumerate(merchants[:3]):
        for j in range(3):
            gmv = Decimal("120.00") + Decimal(j * 30)
            commission = (gmv * Decimal("5") / Decimal("100")).quantize(Decimal("0.01"))
            cur.execute(
                "insert into merchant_commission_bill(tenant_id,merchant_id,order_id,order_no,gmv,commission_rate,"
                "commission_amount,settle_status,remark,create_time) "
                "values(%s,%s,%s,%s,%s,5,%s,%s,'演示佣金应收',now())",
                (
                    tid,
                    mid,
                    -(i * 10 + j + 1),
                    f"DEMO{tid}{i}{j}",
                    gmv,
                    commission,
                    "PENDING" if j < 2 else "SETTLED",
                ),
            )

conn.commit()
cur.execute("select count(1) from store_merchant_apply")
ac = cur.fetchone()[0]
cur.execute("select count(1) from store_merchant_audit_log")
lc = cur.fetchone()[0]
cur.execute("select count(1) from merchant_commission_bill")
bc = cur.fetchone()[0]
cur.execute("select count(1) from store_merchant_apply where status='PENDING_PROPERTY'")
pp = cur.fetchone()[0]
cur.execute("select count(1) from store_merchant_apply where status='PENDING_PLATFORM'")
pf = cur.fetchone()[0]
print(f"OK applies={ac} logs={lc} bills={bc} pending_property={pp} pending_platform={pf}")
cur.close()
conn.close()
