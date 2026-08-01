# -*- coding: utf-8 -*-
"""生成两张任务分配表图片：昨天已完成 / 今天未完成"""
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

OUT = Path(r"D:\java文件夹\zhineng\docs")
OUT.mkdir(parents=True, exist_ok=True)

# 表头
HEADERS = ["二级模块", "三级功能点", "负责人", "优先级", "任务状态", "开始时间", "结束时间"]
# 列宽
COLS = [160, 380, 90, 70, 90, 110, 110]

# 昨天已完成（2026/7/27）— 仅：马兴桦、和姜泽、赵泽阳、张子煜、程佳心
YESTERDAY = [
    ("房屋基础管理", "楼栋/单元/房号新增编辑与唯一校验", "马兴桦", "高", "已完成", "2026/7/27", "2026/7/27"),
    ("房屋基础管理", "单元下批量导入房号（区间解析）", "赵泽阳", "高", "已完成", "2026/7/27", "2026/7/27"),
    ("房屋基础管理", "已绑定住户房屋禁止删除拦截", "程佳心", "中", "已完成", "2026/7/27", "2026/7/27"),
    ("社区超市", "商品分类、售价、库存与图片上传", "张子煜", "高", "已完成", "2026/7/27", "2026/7/27"),
    ("社区超市", "送货上门/到店自提结算与起送校验", "和姜泽", "高", "已完成", "2026/7/27", "2026/7/27"),
    ("社区超市", "接单备货配送履约与退款审核", "马兴桦", "高", "已完成", "2026/7/27", "2026/7/27"),
    ("AI知识库", "Markdown制度文档上传与全文查看", "张子煜", "高", "已完成", "2026/7/27", "2026/7/27"),
    ("AI知识库", "滑动窗口切分与向量索引构建", "赵泽阳", "高", "已完成", "2026/7/27", "2026/7/27"),
    ("社区活动", "活动发布（标题封面时间地点人数）", "和姜泽", "中", "已完成", "2026/7/27", "2026/7/27"),
    ("社区活动", "报名名单查询与Excel导出", "程佳心", "中", "已完成", "2026/7/27", "2026/7/27"),
    ("失物招领", "寻物/招领列表筛选与详情图片预览", "张子煜", "中", "已完成", "2026/7/27", "2026/7/27"),
    ("邻里倾诉", "工单列表与待处理/处理中/已完结流转", "和姜泽", "高", "已完成", "2026/7/27", "2026/7/27"),
]

# 今天未完成（2026/7/28）— 仅上述五人；结束时间统一为明天
TODAY = [
    ("房屋基础管理", "网格员只读权限与按钮显隐联调", "马兴桦", "高", "进行中", "2026/7/28", "2026/7/29"),
    ("房屋基础管理", "物业App工作台房屋查看入口完善", "程佳心", "中", "未完成", "2026/7/28", "2026/7/29"),
    ("社区超市", "未支付超时关单与库存回滚压测", "赵泽阳", "高", "进行中", "2026/7/28", "2026/7/29"),
    ("社区超市", "支付/退款流水财务对账导出", "和姜泽", "高", "未完成", "2026/7/28", "2026/7/29"),
    ("AI知识库", "索引片段接入智能管家RAG检索联调", "张子煜", "高", "进行中", "2026/7/28", "2026/7/29"),
    ("AI知识库", "索引构建失败重试与日志告警", "马兴桦", "中", "未完成", "2026/7/28", "2026/7/29"),
    ("社区活动", "活动封面图上传组件与预览", "程佳心", "中", "未完成", "2026/7/28", "2026/7/29"),
    ("社区活动", "报名人数达上限前端拦截提示", "和姜泽", "中", "未完成", "2026/7/28", "2026/7/29"),
    ("失物招领", "标记已找到后居民端状态同步", "赵泽阳", "高", "进行中", "2026/7/28", "2026/7/29"),
    ("邻里倾诉", "处理备注台账导出与统计看板", "张子煜", "中", "未完成", "2026/7/28", "2026/7/29"),
    ("临时访客", "核销记录查询与过期状态自动刷新", "马兴桦", "中", "未完成", "2026/7/28", "2026/7/29"),
    ("AI智能管家", "业务办理确认写库全链路回归", "张子煜", "高", "进行中", "2026/7/28", "2026/7/29"),
    ("租户隔离", "五模块 tenantId 越权访问安全校验", "赵泽阳", "高", "未完成", "2026/7/28", "2026/7/29"),
]


def font(size, bold=False):
    candidates = [
        r"C:\Windows\Fonts\msyhbd.ttc" if bold else r"C:\Windows\Fonts\msyh.ttc",
        r"C:\Windows\Fonts\simhei.ttf",
        r"C:\Windows\Fonts\simsun.ttc",
    ]
    for p in candidates:
        if Path(p).exists():
            try:
                return ImageFont.truetype(p, size)
            except Exception:
                continue
    return ImageFont.load_default()


def draw_table(rows, title, out_path):
    pad_x, pad_y = 24, 20
    title_h = 48
    row_h = 40
    head_h = 42
    w = pad_x * 2 + sum(COLS)
    h = pad_y * 2 + title_h + head_h + row_h * len(rows) + 16

    img = Image.new("RGB", (w, h), (245, 248, 252))
    draw = ImageDraw.Draw(img)

    f_title = font(22, True)
    f_head = font(14, True)
    f_cell = font(13)
    f_cell_b = font(13, True)

    # title
    draw.text((pad_x, pad_y + 8), title, fill=(30, 64, 120), font=f_title)

    y0 = pad_y + title_h
    x0 = pad_x
    # header
    draw.rectangle([x0, y0, x0 + sum(COLS), y0 + head_h], fill=(37, 99, 168))
    x = x0
    for i, hname in enumerate(HEADERS):
        tw = draw.textlength(hname, font=f_head)
        draw.text((x + (COLS[i] - tw) / 2, y0 + 11), hname, fill=(255, 255, 255), font=f_head)
        x += COLS[i]

    # group spans for col0
    groups = []
    i = 0
    while i < len(rows):
        mod = rows[i][0]
        j = i
        while j < len(rows) and rows[j][0] == mod:
            j += 1
        groups.append((i, j - 1, mod))
        i = j

    # rows bg
    for ri, row in enumerate(rows):
        y = y0 + head_h + ri * row_h
        bg = (255, 255, 255) if ri % 2 == 0 else (230, 240, 250)
        draw.rectangle([x0, y, x0 + sum(COLS), y + row_h], fill=bg)
        # grid
        draw.line([x0, y + row_h, x0 + sum(COLS), y + row_h], fill=(210, 222, 235), width=1)

    # vertical lines
    x = x0
    for c in COLS:
        draw.line([x, y0, x, y0 + head_h + row_h * len(rows)], fill=(210, 222, 235), width=1)
        x += c
    draw.line(
        [x0 + sum(COLS), y0, x0 + sum(COLS), y0 + head_h + row_h * len(rows)],
        fill=(210, 222, 235),
        width=1,
    )
    draw.rectangle(
        [x0, y0, x0 + sum(COLS), y0 + head_h + row_h * len(rows)],
        outline=(37, 99, 168),
        width=2,
    )

    # module merged cells text
    for start, end, mod in groups:
        y1 = y0 + head_h + start * row_h
        y2 = y0 + head_h + (end + 1) * row_h
        # fill module column uniformly light
        draw.rectangle([x0 + 1, y1 + 1, x0 + COLS[0] - 1, y2 - 1], fill=(219, 234, 254))
        tw = draw.textlength(mod, font=f_cell_b)
        th = 16
        draw.text(
            (x0 + (COLS[0] - tw) / 2, (y1 + y2) / 2 - th / 2),
            mod,
            fill=(30, 64, 120),
            font=f_cell_b,
        )
        if end > start:
            draw.line([x0, y2, x0 + COLS[0], y2], fill=(186, 210, 235), width=1)

    # other cells
    for ri, row in enumerate(rows):
        y = y0 + head_h + ri * row_h
        x = x0 + COLS[0]
        for ci, val in enumerate(row[1:], start=1):
            text = val or "—"
            color = (30, 41, 59)
            if ci == 3 and text == "高":  # 优先级
                color = (220, 38, 38)
                ff = f_cell_b
            elif ci == 4 and text == "已完成":
                color = (22, 163, 74)
                ff = f_cell_b
            elif ci == 4 and text in ("未完成", "进行中"):
                color = (217, 119, 6)
                ff = f_cell_b
            else:
                ff = f_cell
            tw = draw.textlength(text, font=ff)
            # left align for long feature col
            if ci == 1:
                draw.text((x + 10, y + 12), text, fill=color, font=ff)
            else:
                draw.text((x + (COLS[ci] - tw) / 2, y + 12), text, fill=color, font=ff)
            x += COLS[ci]

    img.save(out_path, "PNG")
    print("saved", out_path)


draw_table(
    YESTERDAY,
    "智慧社区 · 任务分配表（昨天已完成 · 2026/7/27）",
    OUT / "tasks_yesterday_done.png",
)
draw_table(
    TODAY,
    "智慧社区 · 任务分配表（今天未完成 · 2026/7/28）",
    OUT / "tasks_today_todo.png",
)
