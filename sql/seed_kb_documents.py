# -*- coding: utf-8 -*-
"""生成并导入 AI 知识库 Markdown 文档（knowledge_documents + chunks 向量）"""
import math
import struct
from datetime import datetime

import pymysql

DIM = 256
WINDOW = 400
OVERLAP = 80
TENANT_ID = 1

DOCS = [
    {
        "category": "community",
        "title": "小区公告汇编",
        "file_name": "小区公告.md",
        "content": """# 阳光花园社区 · 小区公告汇编

## 一、物业服务中心信息
- 地址：阳光花园社区 1 号门物业服务中心
- 值班电话：400-800-2026（7×24 小时）
- 办事时间：周一至周日 8:30–18:00（法定节假日值班岗亭不间断）
- 业主微信群：以楼栋群为准，重要通知同步发布至 App「公告通知」与单元门厅公告栏

## 二、近期重点公告
### 1. 电梯维保安排
每周三 09:00–12:00 进行电梯例行维保，维保期间该梯暂停使用，请改乘相邻电梯或楼梯。紧急情况请拨打值班电话。

### 2. 快递与外卖
快递柜位于 1 号门东侧及地下车库 B1 西北角。外卖骑手请至门岗登记后进入，禁止在楼道长时间停留。大件快递可联系门岗代收。

### 3. 绿化与消杀
每月第二个周六上午进行公共区域绿化养护与病媒消杀，请业主收好阳台衣物，关闭外窗。

### 4. 停水停电预告渠道
计划性停水停电将提前 24 小时在 App 公告、单元门厅张贴，并短信通知登记手机号。突发故障以现场广播与值班电话答复为准。

## 三、温馨提示
请勿在消防通道、楼道堆放杂物；电动车请到指定车棚充电，禁止入户充电。发现安全隐患可随时向物业报事。
""",
    },
    {
        "category": "policy",
        "title": "物业规章制度",
        "file_name": "物业规章制度.md",
        "content": """# 阳光花园社区 · 物业规章制度

依据《阳光花园物业管理规约》及业主大会决议，全体业主、租户及访客应遵守下列规定。

## 一、公共秩序
1. 禁止占用消防通道、疏散楼梯、配电井及公共绿地。
2. 夜间 22:00 后请控制室内外噪音，禁止高分贝装修、聚众喧哗。
3. 宠物出门须牵绳，及时清理粪便；烈性犬按地方规定管理。
4. 广告、条幅悬挂须经物业书面同意。

## 二、装修管理
1. 装修须提前 3 个工作日到物业办理登记，提交方案与施工人员名单，并缴纳装修押金。
2. 施工时间：工作日 8:00–12:00、14:00–18:00；法定节假日及周日禁止噪音施工。
3. 严禁擅自改动承重结构、外立面及公共管线；涉及水电改线须符合安全规范。
4. 竣工后由物业验收，合格后退还装修押金。

## 三、停车与车辆
1. 固定车位业主凭备案车牌出入；访客临停请先办理访客预约并填写车牌。
2. 地下车库限速 5km/h，禁止鸣笛；车位内禁止堆放易燃物品。
3. 电动自行车禁止进入电梯轿厢与室内充电，须使用指定充电车棚。

## 四、消防安全
1. 楼道、地下室禁止存放电动车、纸箱等可燃物。
2. 禁止遮挡消防设施、损坏烟感喷淋。
3. 发现火情立即拨打 119，并通知物业值班室。

## 五、违约处理
对违反上述规定且拒不整改的，物业有权按规约进行劝阻、记录并报业主委员会或相关部门依法处理。
""",
    },
    {
        "category": "policy",
        "title": "收费标准说明",
        "file_name": "收费标准.md",
        "content": """# 阳光花园社区 · 收费标准说明

## 一、住宅物业费
- 收费标准：2.80 元/㎡·月
- 服务内容：公共区域保洁、绿化养护、秩序维护、共用设施设备日常维护、客户服务等
- 出账规则：按自然月出账，一般每月 1 日生成账单
- 缴费截止：当月 25 日（以账单「截止日期」为准）
- 缴纳方式：App「缴费管理」在线支付，或物业服务中心收银台

## 二、车位与临停
- 固定车位管理费：50 元/月·位（不含产权车位购置费用）
- 访客临停：前 2 小时免费，超时 5 元/小时（以岗亭最新公示为准）
- 月卡、错时卡业务请咨询门岗或物业前台

## 三、公摊水电与其他
- 公共区域水电按实分摊，随物业费账单列示
- 户内水电由供水供电公司直接抄表收费
- 装修押金、门禁卡工本费等一次性费用以办理时公示为准

## 四、欠费说明
- 逾期未缴将短信提醒
- 逾期超过 30 天，物业可再次书面催缴
- 长期欠费可能影响部分便民服务办理，具体按管理规约执行

## 五、发票与对账
业主可在 App 查看缴费记录；需要电子发票请到物业服务中心登记抬头与税号。对账单金额有异议的，请携带房屋证明到前台核对。
""",
    },
    {
        "category": "community",
        "title": "社区制度与便民指引",
        "file_name": "社区制度.md",
        "content": """# 阳光花园社区 · 社区制度与便民指引

## 一、访客与门禁
1. 临时访客可在 App「智能管家」或「访客预约」提交，填写姓名后生效（状态：已通过），物业负责线下核销。
2. 驾车来访须填写车牌；超时未到访的预约将自动过期。
3. 业主可在 App 查看本人有效访客记录。

## 二、报事报修
1. 公共设施、户内公共部位问题可通过 App「报事报修」或对智能管家说「我要报修」。
2. 请尽可能描述位置、现象并上传现场照片，便于派单。
3. 紧急险情（跑水、停电、被困电梯）请先拨打值班电话 400-800-2026。

## 三、迁入迁出
1. 迁入（房屋绑定）、迁出（解绑）请在 App 提交申请，由物业审批后同步住户档案。
2. 租户办理迁入需业主协助或提供授权材料（以物业当期要求为准）。

## 四、社区活动
1. 文体活动在 App「社区活动」查看与报名，名额满即止。
2. 活动现场请服从秩序，未成年人须监护人陪同。

## 五、失物招领与邻里事务
1. 失物信息可在 App「失物招领」发布；实物暂存物业失物柜，认领须登记。
2. 邻里纠纷、情绪倾诉可使用 App「邻里倾诉」，由网格员跟进（非紧急报警替代渠道）。

## 六、垃圾分类
可回收物→蓝桶；厨余→绿桶；有害垃圾→红桶；其他垃圾→灰桶。投放时间每天 7:00–21:00，请勿在楼道堆放。

## 七、智能管家可办事项
- 解答本小区公告、规章、收费与制度问题（优先依据知识库）
- 协助登记访客、发起报修、查询本人未缴账单与车辆
- 办理前需您确认关键信息，避免误操作
""",
    },
]


def java_hash(s: str) -> int:
    """与 Java String.hashCode() 一致（32 位有符号）"""
    h = 0
    for ch in s:
        h = (31 * h + ord(ch)) & 0xFFFFFFFF
    if h >= 0x80000000:
        h -= 0x100000000
    return h


def java_mul31(h: int) -> int:
    r = (h * 31) & 0xFFFFFFFF
    if r >= 0x80000000:
        r -= 0x100000000
    return r


def floor_mod(x: int, y: int) -> int:
    return x - math.floor(x / y) * y


def embed(text: str) -> list[float]:
    v = [0.0] * DIM
    if not text or not text.strip():
        return v
    s = "".join(text.lower().split())
    if len(s) < 2:
        v[floor_mod(java_hash(s), DIM)] += 1.0
        return l2norm(v)
    for i in range(len(s) - 1):
        bg = s[i : i + 2]
        h = floor_mod(java_hash(bg), DIM)
        v[h] += 1.0
        if i + 2 < len(s):
            tg = s[i : i + 3]
            h2 = floor_mod(java_mul31(java_hash(tg)), DIM)
            v[h2] += 0.5
    return l2norm(v)


def l2norm(v: list[float]) -> list[float]:
    s = sum(f * f for f in v)
    if s <= 1e-12:
        return v
    inv = 1.0 / math.sqrt(s)
    return [f * inv for f in v]


def to_blob(v: list[float]) -> bytes:
    return struct.pack("<" + "f" * len(v), *v)


def split_chunks(content: str) -> list[str]:
    text = content.replace("\r\n", "\n").strip()
    chunks = []
    if not text:
        return chunks
    i = 0
    while i < len(text):
        end = min(len(text), i + WINDOW)
        chunks.append(text[i:end])
        if end >= len(text):
            break
        i = max(i + 1, end - OVERLAP)
    return chunks


def main():
    conn = pymysql.connect(
        host="127.0.0.1",
        user="root",
        password="root",
        database="smart_community",
        charset="utf8mb4",
    )
    cur = conn.cursor()
    now = datetime.now().strftime("%Y-%m-%d %H:%M:%S")

    for doc in DOCS:
        cur.execute(
            "SELECT id FROM knowledge_documents WHERE tenant_id=%s AND title=%s",
            (TENANT_ID, doc["title"]),
        )
        row = cur.fetchone()
        if row:
            doc_id = row[0]
            cur.execute(
                "UPDATE knowledge_documents SET category=%s, file_name=%s, content=%s, char_count=%s, "
                "index_status='BUILDING', update_time=%s WHERE id=%s",
                (
                    doc["category"],
                    doc["file_name"],
                    doc["content"],
                    len(doc["content"]),
                    now,
                    doc_id,
                ),
            )
            cur.execute(
                "DELETE FROM knowledge_chunks WHERE doc_id=%s AND tenant_id=%s",
                (doc_id, TENANT_ID),
            )
            print("update doc", doc["title"], "id=", doc_id)
        else:
            cur.execute(
                "INSERT INTO knowledge_documents(tenant_id,category,title,file_name,content,char_count,"
                "index_status,chunk_count,uploader_id,create_time,update_time) "
                "VALUES(%s,%s,%s,%s,%s,%s,'BUILDING',0,NULL,%s,%s)",
                (
                    TENANT_ID,
                    doc["category"],
                    doc["title"],
                    doc["file_name"],
                    doc["content"],
                    len(doc["content"]),
                    now,
                    now,
                ),
            )
            doc_id = cur.lastrowid
            print("insert doc", doc["title"], "id=", doc_id)

        chunks = split_chunks(doc["content"])
        for i, c in enumerate(chunks):
            vec = embed(c)
            blob = to_blob(vec)
            cur.execute(
                "INSERT INTO knowledge_chunks(tenant_id,doc_id,chunk_no,content,token_est,"
                "embedding_blob,embedding_dim,create_time) VALUES(%s,%s,%s,%s,%s,%s,%s,%s)",
                (
                    TENANT_ID,
                    doc_id,
                    i,
                    c,
                    max(1, len(c) // 2),
                    blob,
                    DIM,
                    now,
                ),
            )
        cur.execute(
            "UPDATE knowledge_documents SET index_status='READY', chunk_count=%s, update_time=%s WHERE id=%s",
            (len(chunks), now, doc_id),
        )
        cur.execute(
            "INSERT INTO knowledge_index_log(tenant_id,doc_id,step,message,success,create_time) VALUES(%s,%s,%s,%s,1,%s)",
            (TENANT_ID, doc_id, "SEED", f"脚本导入并建索引，共 {len(chunks)} 片", now),
        )
        print("  indexed chunks=", len(chunks))

    conn.commit()
    cur.execute(
        "SELECT id, category, title, index_status, chunk_count, char_count FROM knowledge_documents "
        "WHERE tenant_id=%s ORDER BY id",
        (TENANT_ID,),
    )
    print("--- docs ---")
    for r in cur.fetchall():
        print(r)
    conn.close()


if __name__ == "__main__":
    main()
