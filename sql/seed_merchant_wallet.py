# -*- coding: utf-8 -*-
"""商户营销资金账户演示数据（≠ SaaS 租户套餐订购账务）"""
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
      contact_name VARCHAR(64) NULL,
      contact_phone VARCHAR(32) NULL,
      status TINYINT NOT NULL DEFAULT 1,
      warn_threshold DECIMAL(12,2) NOT NULL DEFAULT 100.00,
      remark VARCHAR(255) NULL,
      create_time DATETIME NULL,
      update_time DATETIME NULL,
      KEY idx_sm_tenant (tenant_id, status)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
    """,
    """
    CREATE TABLE IF NOT EXISTS merchant_wallet (
      id BIGINT PRIMARY KEY AUTO_INCREMENT,
      tenant_id BIGINT NOT NULL,
      merchant_id BIGINT NOT NULL,
      balance DECIMAL(14,2) NOT NULL DEFAULT 0,
      total_recharge DECIMAL(14,2) NOT NULL DEFAULT 0,
      total_consume DECIMAL(14,2) NOT NULL DEFAULT 0,
      total_withdraw DECIMAL(14,2) NOT NULL DEFAULT 0,
      pending_withdraw DECIMAL(14,2) NOT NULL DEFAULT 0,
      status TINYINT NOT NULL DEFAULT 1,
      update_time DATETIME NULL,
      create_time DATETIME NULL,
      UNIQUE KEY uk_mw_merchant (merchant_id),
      KEY idx_mw_tenant (tenant_id)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
    """,
    """
    CREATE TABLE IF NOT EXISTS merchant_wallet_ledger (
      id BIGINT PRIMARY KEY AUTO_INCREMENT,
      tenant_id BIGINT NOT NULL,
      merchant_id BIGINT NOT NULL,
      biz_type VARCHAR(32) NOT NULL,
      amount DECIMAL(14,2) NOT NULL,
      balance_after DECIMAL(14,2) NOT NULL,
      remark VARCHAR(255) NULL,
      operator_id BIGINT NULL,
      operator_name VARCHAR(64) NULL,
      ref_id BIGINT NULL,
      create_time DATETIME NULL,
      KEY idx_mwl_merchant (merchant_id, create_time),
      KEY idx_mwl_tenant (tenant_id, biz_type, create_time)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
    """,
    """
    CREATE TABLE IF NOT EXISTS merchant_withdraw (
      id BIGINT PRIMARY KEY AUTO_INCREMENT,
      tenant_id BIGINT NOT NULL,
      merchant_id BIGINT NOT NULL,
      amount DECIMAL(14,2) NOT NULL,
      status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
      apply_remark VARCHAR(255) NULL,
      applicant_id BIGINT NULL,
      applicant_name VARCHAR(64) NULL,
      audit_remark VARCHAR(255) NULL,
      auditor_id BIGINT NULL,
      auditor_name VARCHAR(64) NULL,
      audit_time DATETIME NULL,
      create_time DATETIME NULL,
      KEY idx_mwd_status (status, create_time),
      KEY idx_mwd_tenant (tenant_id, status)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
    """,
]:
    cur.execute(ddl)

# 从 store_info 同步主商户 + 额外演示商户
cur.execute("select id, tenant_id, name, phone from store_info")
stores = cur.fetchall()
for sid, tid, name, phone in stores:
    cur.execute("select id from store_merchant where tenant_id=%s and name=%s limit 1", (tid, name))
    if not cur.fetchone():
        cur.execute(
            "insert into store_merchant(tenant_id,name,contact_phone,status,warn_threshold,create_time,update_time) "
            "values(%s,%s,%s,1,100,now(),now())",
            (tid, name or "社区商户", phone),
        )

extra = [
    (1, "阳光便利店", "张店长", "13800001111", Decimal("200")),
    (1, "社区生鲜驿站", "李老板", "13800002222", Decimal("150")),
    (2, "和谐邻里小超", "王经理", "13900003333", Decimal("100")),
]
for tid, name, contact, phone, th in extra:
    cur.execute("select id from store_merchant where tenant_id=%s and name=%s limit 1", (tid, name))
    if not cur.fetchone():
        cur.execute(
            "insert into store_merchant(tenant_id,name,contact_name,contact_phone,status,warn_threshold,create_time,update_time) "
            "values(%s,%s,%s,%s,1,%s,now(),now())",
            (tid, name, contact, phone, th),
        )

cur.execute("select id, tenant_id, name, warn_threshold from store_merchant")
merchants = cur.fetchall()

for mid, tid, name, th in merchants:
    cur.execute("select id, balance from merchant_wallet where merchant_id=%s", (mid,))
    row = cur.fetchone()
    if not row:
        # 演示余额：部分低于阈值触发预警
        if "生鲜" in (name or ""):
            bal, recharge, consume = Decimal("48.00"), Decimal("500.00"), Decimal("452.00")
        elif "便利" in (name or ""):
            bal, recharge, consume = Decimal("1280.00"), Decimal("2000.00"), Decimal("520.00")
        elif tid == 2:
            bal, recharge, consume = Decimal("80.00"), Decimal("300.00"), Decimal("220.00")
        else:
            bal, recharge, consume = Decimal("860.50"), Decimal("1500.00"), Decimal("639.50")
        cur.execute(
            "insert into merchant_wallet(tenant_id,merchant_id,balance,total_recharge,total_consume,"
            "total_withdraw,pending_withdraw,status,create_time,update_time) "
            "values(%s,%s,%s,%s,%s,0,0,1,now(),now())",
            (tid, mid, bal, recharge, consume),
        )
        # 流水
        cur.execute("select count(1) from merchant_wallet_ledger where merchant_id=%s", (mid,))
        if cur.fetchone()[0] == 0:
            t0 = datetime.now() - timedelta(days=12)
            cur.execute(
                "insert into merchant_wallet_ledger(tenant_id,merchant_id,biz_type,amount,balance_after,remark,"
                "operator_name,create_time) values(%s,%s,'RECHARGE',%s,%s,'开户充值','物业管理员',%s)",
                (tid, mid, recharge, recharge, t0),
            )
            if consume > 0:
                t1 = datetime.now() - timedelta(days=5)
                after = recharge - consume
                cur.execute(
                    "insert into merchant_wallet_ledger(tenant_id,merchant_id,biz_type,amount,balance_after,remark,"
                    "operator_name,create_time) values(%s,%s,'CONSUME',%s,%s,'社区广告投放扣费','系统自动',%s)",
                    (tid, mid, -consume, after, t1),
                )

# 待审提现（取余额较高的商户）
cur.execute(
    "select m.id, m.tenant_id, w.balance from store_merchant m "
    "join merchant_wallet w on w.merchant_id=m.id where w.balance>=200 order by w.balance desc limit 2"
)
cands = cur.fetchall()
for mid, tid, bal in cands:
    cur.execute(
        "select id from merchant_withdraw where merchant_id=%s and status='PENDING' limit 1",
        (mid,),
    )
    if cur.fetchone():
        continue
    amt = Decimal("200.00")
    if bal < amt:
        continue
    cur.execute(
        "update merchant_wallet set balance=balance-%s, pending_withdraw=pending_withdraw+%s, update_time=now() "
        "where merchant_id=%s",
        (amt, amt, mid),
    )
    cur.execute("select balance from merchant_wallet where merchant_id=%s", (mid,))
    after = cur.fetchone()[0]
    cur.execute(
        "insert into merchant_wallet_ledger(tenant_id,merchant_id,biz_type,amount,balance_after,remark,"
        "operator_name,create_time) values(%s,%s,'WITHDRAW',%s,%s,'提现申请冻结','物业管理员',now())",
        (tid, mid, -amt, after),
    )
    cur.execute(
        "insert into merchant_withdraw(tenant_id,merchant_id,amount,status,apply_remark,applicant_name,create_time) "
        "values(%s,%s,%s,'PENDING','营销账户余额提现','物业管理员',now())",
        (tid, mid, amt),
    )

conn.commit()
cur.execute("select count(1) from store_merchant")
mc = cur.fetchone()[0]
cur.execute("select count(1) from merchant_wallet")
wc = cur.fetchone()[0]
cur.execute("select count(1) from merchant_wallet_ledger")
lc = cur.fetchone()[0]
cur.execute("select count(1) from merchant_withdraw where status='PENDING'")
pc = cur.fetchone()[0]
print(f"OK merchants={mc} wallets={wc} ledger={lc} pending_withdraw={pc}")
cur.close()
conn.close()
