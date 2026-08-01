import copy
import os
from pathlib import Path

from openpyxl import load_workbook


source = Path(os.environ["TASK_XLSX"])
target = Path(os.environ["TASK_XLSX_OUT"])

features = [
    ("创新亮点", "运营态势", "中国地图租户态势", "在中国地图展示社区租户、套餐、状态和到期风险，支持缩放、拖拽与全屏", "地图态势页可用", "点位可交互；状态颜色正确；支持全屏", "无", "P1", "INNO-001"),
    ("", "", "租户经营健康评分", "融合活跃度、续费、工单、缴费和满意度生成社区健康分并给出风险原因", "健康评分卡可用", "评分可解释；可下钻查看指标", "是", "P1", "INNO-002"),
    ("", "AI服务闭环", "AI报修多模态一键成单", "结合文字和现场图片识别故障类型、位置与紧急程度，居民确认后一键生成工单", "AI报修成单可用", "字段自动预填；确认后生成真实工单", "是", "P0", "INNO-003"),
    ("", "", "工单SLA超时预测", "根据工单类型、历史耗时、人员负载预测超时风险并提前催办", "超时预警可用", "高风险工单可识别并通知责任人", "是", "P1", "INNO-004"),
    ("", "SaaS商业化", "套餐到期实时降级恢复", "租户到期自动降级基础功能，续费成功后实时刷新功能码和网关权限", "租约状态联动可用", "降级与恢复无需重新登录；越权接口被拦截", "无", "P0", "INNO-005"),
    ("", "", "多社区运营对标", "按工单效率、收费率、满意度和设备完好率对社区进行匿名对标分析", "运营对标看板可用", "支持同周期对比；隐私数据脱敏", "是", "P1", "INNO-006"),
    ("", "可信AI", "知识问答来源引用", "AI回答同步展示知识文档、版本和引用片段，支持一键反馈答案质量", "引用与反馈可用", "回答可追溯；低质量反馈可进入优化队列", "是", "P0", "INNO-007"),
    ("", "应急联动", "社区事件一键联动指挥", "将告警、地图点位、附近人员、设备视频与处置流程汇聚为应急事件卡", "应急事件卡可用", "可定位、派人、留痕并形成处置报告", "是", "P1", "INNO-008"),
]


def clone_row_style(ws, source_row, target_row):
    for col in range(1, ws.max_column + 1):
        src = ws.cell(source_row, col)
        dst = ws.cell(target_row, col)
        if src.has_style:
            dst._style = copy.copy(src._style)
        if src.number_format:
            dst.number_format = src.number_format
        if src.alignment:
            dst.alignment = copy.copy(src.alignment)
        if src.protection:
            dst.protection = copy.copy(src.protection)
    ws.row_dimensions[target_row].height = ws.row_dimensions[source_row].height


wb = load_workbook(source)
for sheet_index, ws in enumerate(wb.worksheets):
    original_last = ws.max_row
    for offset, feature in enumerate(features, start=1):
        row = original_last + offset
        clone_row_style(ws, original_last, row)
        level1, level2, level3, description, delivery, acceptance, ai_use, priority, code = feature

        if sheet_index == 0:
            values = [
                level1, level2, level3, description, delivery, acceptance,
                "张子煜", priority, "待开始", None, None,
                f'=IF(OR(J{row}="",K{row}=""),"",ROUND((K{row}-J{row})*24,1)&"小时")',
                code,
            ]
        else:
            values = [
                level1, level2, level3, description, delivery, acceptance,
                ai_use, priority, "张子煜", "待开始", None, None,
                f'=IF(OR(K{row}="",L{row}=""),"",ROUND((L{row}-K{row})*24,1)&"小时")',
                code,
            ]

        for col, value in enumerate(values, start=1):
            ws.cell(row, col, value)

    ws.auto_filter.ref = f"A1:{ws.cell(1, ws.max_column).column_letter}{ws.max_row}"

wb.save(target)
print(target)
