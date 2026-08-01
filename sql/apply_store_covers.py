# -*- coding: utf-8 -*-
"""把已生成的 store-*.png 上传到本地/MinIO，并写入商品 cover_url"""
import shutil
from pathlib import Path

import pymysql
from minio import Minio
from minio.error import S3Error

ASSETS = Path(r"C:\Users\Lenovo\.cursor\projects\d-xiangmu\assets")
LOCAL_DIR = Path(r"D:\java文件夹\zhineng\uploads\1")
BUCKET = "smart-community"
TENANT = "1"

# 文件名 -> 商品名
MAP = {
    "store-water.png": "农夫山泉 550ml",
    "store-coke.png": "可口可乐 330ml",
    "store-sprite.png": "雪碧 330ml",
    "store-tea.png": "康师傅冰红茶 500ml",
    "store-beer.png": "青岛啤酒 500ml",
    "store-milk.png": "纯牛奶 250ml*12",
    "store-chips.png": "乐事薯片原味 70g",
    "store-oreo.png": "奥利奥夹心饼干",
    "store-spicy.png": "卫龙亲嘴烧",
    "store-noodle.png": "统一老坛酸菜面",
    "store-rice.png": "自热米饭（咖喱鸡）",
    "store-senbei.png": "旺旺雪饼 150g",
    "store-tissue.png": "清风抽纸 3层*6包",
    "store-detergent.png": "蓝月亮洗衣液 1kg",
    "store-soap.png": "舒肤佳香皂 125g",
    "store-bags.png": "垃圾袋 中号 40只",
    "store-battery.png": "南孚电池 5号 4粒",
    "store-pens.png": "得力中性笔 0.5mm*12",
}


def ensure_bucket(client: Minio):
    if not client.bucket_exists(BUCKET):
        client.make_bucket(BUCKET)


def main():
    LOCAL_DIR.mkdir(parents=True, exist_ok=True)
    client = Minio(
        "127.0.0.1:9000",
        access_key="minioadmin",
        secret_key="minioadmin",
        secure=False,
    )
    ensure_bucket(client)

    conn = pymysql.connect(
        host="127.0.0.1",
        user="root",
        password="root",
        database="smart_community",
        charset="utf8mb4",
    )
    cur = conn.cursor()
    ok, miss = [], []

    for fname, pname in MAP.items():
        src = ASSETS / fname
        if not src.is_file():
            miss.append(f"missing file: {fname}")
            continue
        # 稳定文件名，便于重复执行覆盖
        dest_name = fname
        dest = LOCAL_DIR / dest_name
        shutil.copy2(src, dest)

        # MinIO: 1/store-xxx.png
        key = f"{TENANT}/{dest_name}"
        try:
            client.fput_object(BUCKET, key, str(dest), content_type="image/png")
        except S3Error as e:
            miss.append(f"minio fail {fname}: {e}")
            continue

        url = f"/file/static/{TENANT}/{dest_name}"
        cur.execute(
            "UPDATE store_product SET cover_url=%s WHERE tenant_id=1 AND name=%s AND status=1",
            (url, pname),
        )
        if cur.rowcount == 0:
            miss.append(f"no product row: {pname}")
        else:
            ok.append(f"{pname} -> {url}")

    conn.commit()
    conn.close()

    report = Path(r"D:\java文件夹\zhineng\sql\_cover_report.txt")
    report.write_text(
        "OK:\n" + "\n".join(ok) + "\n\nMISS:\n" + "\n".join(miss),
        encoding="utf-8",
    )
    print(f"ok={len(ok)} miss={len(miss)} report={report}")


if __name__ == "__main__":
    main()
