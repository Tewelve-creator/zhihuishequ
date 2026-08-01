# -*- coding: utf-8 -*-
"""按专高六《实训项目开发任务表》模板生成 AI智慧社区 一页计划表"""
from openpyxl import Workbook
from openpyxl.styles import Font, Alignment, Border, Side, PatternFill
from openpyxl.utils import get_column_letter

dst = r"c:\Users\Lenovo\Desktop\AI智慧社区-实训项目开发任务表.xlsx"

wb = Workbook()
ws = wb.active
ws.title = "AI智慧社区"

thin = Border(
    left=Side(style="thin", color="B0B0B0"),
    right=Side(style="thin", color="B0B0B0"),
    top=Side(style="thin", color="B0B0B0"),
    bottom=Side(style="thin", color="B0B0B0"),
)
header_fill = PatternFill("solid", fgColor="1F4E79")
header_font = Font(name="微软雅黑", bold=True, color="FFFFFF", size=11)
title_font = Font(name="微软雅黑", bold=True, size=16, color="1F4E79")
section_font = Font(name="微软雅黑", bold=True, size=12, color="1F4E79")
label_font = Font(name="微软雅黑", bold=True, size=10)
normal = Font(name="微软雅黑", size=10)
center = Alignment(horizontal="center", vertical="center", wrap_text=True)
left = Alignment(horizontal="left", vertical="center", wrap_text=True)

ws.merge_cells("A1:J1")
ws["A1"] = "实训项目开发任务表"
ws["A1"].font = title_font
ws["A1"].alignment = Alignment(horizontal="center", vertical="center")
ws.row_dimensions[1].height = 28

ws.merge_cells("A2:J2")
ws["A2"] = "一、项目基本情况"
ws["A2"].font = section_font

ws["A3"] = "项目名称："
ws.merge_cells("B3:F3")
ws["B3"] = "AI智慧社区（多租户物业SaaS）"
ws["G3"] = "核心模块："
ws.merge_cells("H3:J3")
ws["H3"] = "业主App(UniApp)、物业后台(Vue3)、网关与微服务、AI特色能力"

ws["A4"] = "核心技术栈："
ws.merge_cells("B4:F4")
ws["B4"] = (
    "Spring Boot / Spring Cloud Gateway / MySQL 8 / Redis / "
    "Vue3 + Element Plus / UniApp / ECharts / RAG向量检索 / MinIO"
)
ws["G4"] = "核心业务："
ws.merge_cells("H4:J4")
ws["H4"] = "多租户隔离、报修报事、缴费商城、AI管家问答、知识库RAG、识图报修、智能日程与提醒"

ws["A5"] = "项目经理："
ws.merge_cells("B5:C5")
ws["B5"] = "待填写"
ws["G5"] = "填写日期："
ws.merge_cells("H5:J5")
ws["H5"] = "2026-07-31"

ws.merge_cells("A6:J6")
ws["A6"] = "二、项目组成员"
ws["A6"].font = section_font

ws["A7"] = "组长："
ws["B7"] = "待填写"
ws["C7"] = "后端开发："
ws.merge_cells("D7:F7")
ws["D7"] = "待填写"
ws["G7"] = "App/前端："
ws.merge_cells("H7:J7")
ws["H7"] = "待填写"

ws["A8"] = "AI/算法："
ws["B8"] = "待填写"
ws["C8"] = "物业后台："
ws.merge_cells("D8:F8")
ws["D8"] = "待填写"
ws["G8"] = "测试/运维："
ws.merge_cells("H8:J8")
ws["H8"] = "待填写"

ws.merge_cells("A9:J9")
ws["A9"] = "三、工作分解结构"
ws["A9"].font = section_font

headers = [
    "项目名称",
    "模块",
    "核心功能",
    "工时",
    "总工时",
    "开始日期",
    "结束日期",
    "每日检查时间",
    "开发人员",
    "功能描述/交付物",
]
for i, h in enumerate(headers, 1):
    cell = ws.cell(10, i, h)
    cell.font = header_font
    cell.fill = header_fill
    cell.alignment = center
    cell.border = thin

# (模块, 功能, 工时, 总工时或None, 开始, 结束, 人员, 交付物)
tasks = [
    (
        "项目管理",
        "需求梳理与范围确认",
        "8h",
        "24h",
        "2026-06-16",
        "2026-06-16",
        "待填写",
        "P0：明确多租户物业边界、角色权限、App/后台菜单与验收口径",
    ),
    (
        "项目管理",
        "里程碑与风险台账",
        "8h",
        None,
        "2026-06-16",
        "2026-06-17",
        "待填写",
        "P0：输出计划表、里程碑、风险清单、联调节奏",
    ),
    (
        "项目管理",
        "答辩材料与演示脚本",
        "8h",
        None,
        "2026-07-28",
        "2026-07-31",
        "待填写",
        "P0：完成演示路径、截图说明、功能亮点与问题复盘",
    ),
    (
        "架构与基础设施",
        "微服务与网关骨架",
        "12h",
        "48h",
        "2026-06-18",
        "2026-06-19",
        "待填写",
        "P0：community-gateway/auth/repair/ai 服务拆分，统一鉴权与租户头透传",
    ),
    (
        "架构与基础设施",
        "多租户与数据隔离",
        "12h",
        None,
        "2026-06-19",
        "2026-06-20",
        "待填写",
        "P0：TenantContext + 租户拦截器 + MyBatis 租户行插件落地",
    ),
    (
        "架构与基础设施",
        "数据库与核心表设计",
        "12h",
        None,
        "2026-06-20",
        "2026-06-21",
        "待填写",
        "P0：用户房屋、工单、缴费、活动、ai_chat_log、ai_schedule_remind、ai_image_log 等",
    ),
    (
        "架构与基础设施",
        "文件存储与网关路由",
        "12h",
        None,
        "2026-06-21",
        "2026-06-22",
        "待填写",
        "P0：MinIO/本地文件上传、静态访问、网关路由与套餐降级",
    ),
    (
        "物业后台",
        "登录与角色门户",
        "8h",
        "56h",
        "2026-06-22",
        "2026-06-23",
        "待填写",
        "P0：物业/网格员/平台管理员登录，菜单按角色与套餐展示",
    ),
    (
        "物业后台",
        "报修/报事工单台账",
        "12h",
        None,
        "2026-06-23",
        "2026-06-25",
        "待填写",
        "P0：工单列表、分派、接单完工、详情溯源、按 orderType 拆分菜单",
    ),
    (
        "物业后台",
        "缴费/活动/访客等运营",
        "12h",
        None,
        "2026-06-25",
        "2026-06-27",
        "待填写",
        "P0：账单、活动报名、访客预约、公告通知等管理页",
    ),
    (
        "物业后台",
        "工作台KPI与图表",
        "8h",
        None,
        "2026-06-27",
        "2026-06-28",
        "待填写",
        "P1：待办汇总、风险预警、ECharts 可视化",
    ),
    (
        "物业后台",
        "巡检维保与设备台账",
        "16h",
        None,
        "2026-06-28",
        "2026-07-01",
        "待填写",
        "P0：巡检点位/任务、设备清单、维保工单派发与完工",
    ),
    (
        "业主App",
        "登录注册与房屋绑定",
        "12h",
        "52h",
        "2026-06-22",
        "2026-06-24",
        "待填写",
        "P0：手机号登录、多房屋切换、搬入搬出申请",
    ),
    (
        "业主App",
        "报修报事与进度跟踪",
        "12h",
        None,
        "2026-06-24",
        "2026-06-26",
        "待填写",
        "P0：提单、图片上传、状态流转、详情与消息提醒",
    ),
    (
        "业主App",
        "缴费与商城下单",
        "12h",
        None,
        "2026-06-26",
        "2026-06-28",
        "待填写",
        "P0：账单支付确认、商城购物车/优惠券/订单",
    ),
    (
        "业主App",
        "邻里圈与互助",
        "8h",
        None,
        "2026-06-28",
        "2026-06-30",
        "待填写",
        "P1：动态、评论、举报、邻里互助接单联系",
    ),
    (
        "业主App",
        "活动报名与访客预约",
        "8h",
        None,
        "2026-06-30",
        "2026-07-01",
        "待填写",
        "P0：活动列表报名、访客预约与通行信息",
    ),
    (
        "AI知识库",
        "问答对/文档入库与分类",
        "12h",
        "48h",
        "2026-07-02",
        "2026-07-04",
        "待填写",
        "P0：知识库分类、文档上传、分块参数、状态 ACTIVE/DRAFT/REVOKED",
    ),
    (
        "AI知识库",
        "RAG检索与应答策略",
        "16h",
        None,
        "2026-07-04",
        "2026-07-06",
        "待填写",
        "P0：本地向量检索、相似度阈值、知识库优先/仅知识库/大模型优先策略",
    ),
    (
        "AI知识库",
        "问答日志与统计优化",
        "12h",
        None,
        "2026-07-07",
        "2026-07-08",
        "待填写",
        "P0：来源中文映射、筛选、统计卡片、高频问题TOP10横向柱图、RAG召回详情",
    ),
    (
        "AI知识库",
        "知识库设置页",
        "8h",
        None,
        "2026-07-08",
        "2026-07-08",
        "待填写",
        "P1：TopK、最低相似度、System Prompt、非官方提示开关",
    ),
    (
        "AI特色-识图",
        "报修识图识别链路",
        "12h",
        "36h",
        "2026-07-09",
        "2026-07-10",
        "待填写",
        "P0：App拍照识图→设施/位置/损坏/置信度→AI或人工识别→关联工单",
    ),
    (
        "AI特色-识图",
        "识图日志后台台账",
        "12h",
        None,
        "2026-07-10",
        "2026-07-11",
        "待填写",
        "P0：统计卡片、环形图、多维筛选、图片预览、详情溯源、删除",
    ),
    (
        "AI特色-识图",
        "识别模式与置信度展示优化",
        "4h",
        None,
        "2026-07-11",
        "2026-07-11",
        "待填写",
        "P1：AI识别/人工识别文案、置信度色标、工单一键跳转",
    ),
    (
        "AI特色-日程",
        "日程生成与业务同步",
        "12h",
        "40h",
        "2026-07-14",
        "2026-07-15",
        "待填写",
        "P0：同步公告/活动/缴费/维保回访/访客预约，结合画像生成个性化日程",
    ),
    (
        "AI特色-日程",
        "冲突检测与到期推送",
        "8h",
        None,
        "2026-07-15",
        "2026-07-16",
        "待填写",
        "P0：时段冲突标记、定时任务写站内消息、跳转路径",
    ),
    (
        "AI特色-日程",
        "智能日程后台台账",
        "12h",
        None,
        "2026-07-16",
        "2026-07-17",
        "待填写",
        "P0：类型标签、冲突高亮、类型分布饼图、多维筛选、详情与提前提醒配置",
    ),
    (
        "AI特色-日程",
        "便民推荐与画像",
        "8h",
        None,
        "2026-07-17",
        "2026-07-18",
        "待填写",
        "P1：业主画像标签、便民服务推荐一键跳转预约",
    ),
    (
        "联调与验收",
        "端到端联调与缺陷修复",
        "16h",
        "36h",
        "2026-07-21",
        "2026-07-24",
        "全员",
        "P0：登录→业务→AI问答/识图/日程全链路通过，修复端口占用与编码类问题",
    ),
    (
        "联调与验收",
        "演示环境与数据准备",
        "12h",
        None,
        "2026-07-25",
        "2026-07-28",
        "全员",
        "P0：演示账号、样例知识库、识图样例、日程样例数据就绪",
    ),
    (
        "联调与验收",
        "验收与归档",
        "8h",
        None,
        "2026-07-29",
        "2026-07-31",
        "全员",
        "P0：验收用例、缺陷关闭清单、源码与文档归档、答辩演示",
    ),
]

proj = "AI智慧社区"
module_rows = {}
row = 11
for mod, func, hours, total, start, end, who, desc in tasks:
    ws.cell(row, 1, proj)
    ws.cell(row, 2, mod)
    ws.cell(row, 3, func)
    ws.cell(row, 4, hours)
    ws.cell(row, 5, total or "")
    ws.cell(row, 6, start)
    ws.cell(row, 7, end)
    ws.cell(row, 8, "每天16:00-17:00")
    ws.cell(row, 9, who)
    ws.cell(row, 10, desc)
    for c in range(1, 11):
        cell = ws.cell(row, c)
        cell.font = normal
        cell.border = thin
        cell.alignment = left if c in (3, 10) else center
    module_rows.setdefault(mod, []).append(row)
    row += 1

last_row = row - 1
ws.merge_cells(start_row=11, start_column=1, end_row=last_row, end_column=1)
ws.cell(11, 1).alignment = center
ws.cell(11, 1).font = Font(name="微软雅黑", bold=True, size=10)

for mod, rows in module_rows.items():
    if len(rows) > 1:
        ws.merge_cells(start_row=rows[0], start_column=2, end_row=rows[-1], end_column=2)
    ws.cell(rows[0], 2).alignment = center
    ws.cell(rows[0], 2).font = Font(name="微软雅黑", bold=True, size=10)

for r in range(2, 10):
    for c in range(1, 11):
        cell = ws.cell(r, c)
        cell.border = thin
        if r in (2, 6, 9):
            cell.font = section_font
        elif cell.value and str(cell.value).endswith("："):
            cell.font = label_font
        else:
            cell.font = normal
        cell.alignment = left

widths = {
    "A": 14,
    "B": 16,
    "C": 24,
    "D": 8,
    "E": 9,
    "F": 12,
    "G": 12,
    "H": 14,
    "I": 12,
    "J": 54,
}
for k, v in widths.items():
    ws.column_dimensions[k].width = v

ws.freeze_panes = "A11"
wb.save(dst)
print("saved:", dst)
print("task_rows:", last_row - 10)
