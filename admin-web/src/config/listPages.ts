import type { Component } from 'vue'

export interface ColumnDef {
  prop: string
  label: string
  width?: number | string
  tag?: boolean
}

export interface ListPageConfig {
  title: string
  columns: ColumnDef[]
  rows: Record<string, unknown>[]
  showDateRange?: boolean
  filters?: { key: string; label: string; options: { label: string; value: string }[] }[]
}

/** 管理端 / 物业端列表页数据（对齐 Axure 模块） */
export const listPages: Record<string, ListPageConfig> = {
  '/admin/basic/park': {
    title: '园区项目管理',
    columns: [
      { prop: 'name', label: '项目名称' },
      { prop: 'address', label: '地址' },
      { prop: 'manager', label: '负责人', width: 100 },
      { prop: 'buildings', label: '楼栋数', width: 90 },
      { prop: 'status', label: '状态', width: 90, tag: true },
    ],
    rows: [
      { name: '阳光花园', address: '高新区科技一路88号', manager: '陈伟', buildings: 12, status: '运营中' },
      { name: '湖畔名邸', address: '滨湖路16号', manager: '刘芳', buildings: 8, status: '运营中' },
      { name: '星河湾', address: '星河大道1号', manager: '周强', buildings: 20, status: '筹建中' },
    ],
  },
  '/admin/basic/building': {
    title: '楼栋房屋',
    columns: [
      { prop: 'building', label: '楼栋', width: 100 },
      { prop: 'unit', label: '单元', width: 80 },
      { prop: 'room', label: '房号', width: 100 },
      { prop: 'area', label: '面积㎡', width: 90 },
      { prop: 'type', label: '类型', width: 100 },
      { prop: 'status', label: '状态', width: 100, tag: true },
    ],
    rows: [
      { building: '1栋', unit: '2单元', room: '502', area: 98, type: '住宅', status: '已入住' },
      { building: '1栋', unit: '1单元', room: '301', area: 86, type: '住宅', status: '空置' },
      { building: '商业A', unit: '-', room: '101', area: 120, type: '商铺', status: '已出租' },
    ],
  },
  '/admin/basic/resident': {
    title: '住户管理',
    showDateRange: true,
    columns: [
      { prop: 'name', label: '姓名', width: 100 },
      { prop: 'phone', label: '手机号', width: 130 },
      { prop: 'room', label: '房屋' },
      { prop: 'type', label: '身份', width: 90 },
      { prop: 'status', label: '状态', width: 90, tag: true },
    ],
    rows: [
      { name: '王芳', phone: '13800001111', room: '1栋2单元502', type: '业主', status: '正常' },
      { name: '李强', phone: '13900002222', room: '2栋1单元301', type: '租客', status: '正常' },
      { name: '赵敏', phone: '13700003333', room: '3栋3单元1201', type: '业主', status: '迁出中' },
    ],
  },
  '/admin/basic/venue': {
    title: '场地管理',
    columns: [
      { prop: 'name', label: '场地名称' },
      { prop: 'capacity', label: '容纳人数', width: 100 },
      { prop: 'fee', label: '预约费用', width: 100 },
      { prop: 'status', label: '状态', width: 100, tag: true },
    ],
    rows: [
      { name: '社区活动室', capacity: 40, fee: 0, status: '可预约' },
      { name: '羽毛球场', capacity: 8, fee: 30, status: '可预约' },
      { name: '党建会议室', capacity: 20, fee: 0, status: '维护中' },
    ],
  },
  '/admin/property/workorder': {
    title: '工单管理',
    showDateRange: true,
    columns: [
      { prop: 'no', label: '工单号', width: 150 },
      { prop: 'title', label: '标题' },
      { prop: 'type', label: '类型', width: 100 },
      { prop: 'reporter', label: '报事人', width: 90 },
      { prop: 'assignee', label: '处理人', width: 90 },
      { prop: 'status', label: '状态', width: 100, tag: true },
    ],
    rows: [
      { no: 'WO20260717001', title: '1栋电梯异响', type: '报修', reporter: '王芳', assignee: '张师傅', status: '处理中' },
      { no: 'WO20260717002', title: '下水道堵塞', type: '报修', reporter: '李强', assignee: '未分配', status: '待接单' },
      { no: 'WO20260716008', title: '楼道灯不亮', type: '报事', reporter: '赵敏', assignee: '王师傅', status: '已完成' },
    ],
  },
  '/admin/property/inspection': {
    title: '巡检管理',
    columns: [
      { prop: 'name', label: '巡检计划' },
      { prop: 'route', label: '路线' },
      { prop: 'cycle', label: '周期', width: 100 },
      { prop: 'owner', label: '负责人', width: 100 },
      { prop: 'status', label: '状态', width: 100, tag: true },
    ],
    rows: [
      { name: '消防日常巡检', route: '1-12栋消防通道', cycle: '每日', owner: '安保组', status: '进行中' },
      { name: '电梯机房巡检', route: '各栋电梯机房', cycle: '每周', owner: '工程组', status: '进行中' },
      { name: '绿化巡检', route: '中心花园', cycle: '每周', owner: '保洁组', status: '已暂停' },
    ],
  },
  '/admin/property/device': {
    title: '设备管理',
    columns: [
      { prop: 'name', label: '设备名称' },
      { prop: 'code', label: '设备编号', width: 140 },
      { prop: 'location', label: '位置' },
      { prop: 'online', label: '在线状态', width: 100, tag: true },
    ],
    rows: [
      { name: '1号电梯', code: 'EL-1-01', location: '1栋', online: '在线' },
      { name: '南门门禁', code: 'AC-S-01', location: '南大门', online: '在线' },
      { name: '地下泵房水位仪', code: 'WT-B1-02', location: 'B1泵房', online: '离线' },
    ],
  },
  '/admin/property/asset': {
    title: '设备台账',
    columns: [
      { prop: 'name', label: '资产名称' },
      { prop: 'brand', label: '品牌', width: 100 },
      { prop: 'buyDate', label: '购置日期', width: 120 },
      { prop: 'value', label: '原值(元)', width: 110 },
      { prop: 'status', label: '状态', width: 90, tag: true },
    ],
    rows: [
      { name: '变频水泵', brand: '格兰富', buyDate: '2022-05-12', value: 28600, status: '在用' },
      { name: '监控硬盘录像机', brand: '海康', buyDate: '2023-01-08', value: 5200, status: '在用' },
      { name: '旧门禁主机', brand: '大华', buyDate: '2019-08-20', value: 1800, status: '报废' },
    ],
  },
  '/admin/payment/items': {
    title: '收费项目',
    columns: [
      { prop: 'name', label: '项目名称' },
      { prop: 'unit', label: '计费单位', width: 100 },
      { prop: 'price', label: '单价', width: 100 },
      { prop: 'cycle', label: '周期', width: 90 },
      { prop: 'status', label: '状态', width: 90, tag: true },
    ],
    rows: [
      { name: '物业费', unit: '元/㎡·月', price: 2.8, cycle: '月', status: '启用' },
      { name: '停车费', unit: '元/月', price: 200, cycle: '月', status: '启用' },
      { name: '垃圾处理费', unit: '元/户·月', price: 10, cycle: '月', status: '启用' },
    ],
  },
  '/admin/payment/bills': {
    title: '账单管理',
    showDateRange: true,
    columns: [
      { prop: 'billNo', label: '账单号', width: 150 },
      { prop: 'room', label: '房屋' },
      { prop: 'item', label: '收费项', width: 100 },
      { prop: 'amount', label: '金额', width: 100 },
      { prop: 'period', label: '账期', width: 100 },
      { prop: 'status', label: '状态', width: 90, tag: true },
    ],
    rows: [
      { billNo: 'B2026070001', room: '1栋502', item: '物业费', amount: 274.4, period: '2026-07', status: '未缴' },
      { billNo: 'B2026070002', room: '2栋301', item: '停车费', amount: 200, period: '2026-07', status: '已缴' },
      { billNo: 'B2026060018', room: '3栋1201', item: '物业费', amount: 310, period: '2026-06', status: '逾期' },
    ],
  },
  '/admin/payment/account': {
    title: '账务管理',
    columns: [
      { prop: 'date', label: '日期', width: 120 },
      { prop: 'type', label: '类型', width: 90 },
      { prop: 'amount', label: '金额', width: 110 },
      { prop: 'method', label: '支付方式', width: 110 },
      { prop: 'remark', label: '摘要' },
    ],
    rows: [
      { date: '2026-07-16', type: '收款', amount: 200, method: '微信', remark: '2栋301停车费' },
      { date: '2026-07-15', type: '收款', amount: 274.4, method: '支付宝', remark: '1栋502物业费' },
      { date: '2026-07-14', type: '退款', amount: 50, method: '现金', remark: '场地预约取消退款' },
    ],
  },
  '/admin/payment/meter': {
    title: '仪表管理',
    columns: [
      { prop: 'room', label: '房屋' },
      { prop: 'type', label: '表类型', width: 90 },
      { prop: 'last', label: '上次读数', width: 100 },
      { prop: 'current', label: '本次读数', width: 100 },
      { prop: 'usage', label: '用量', width: 90 },
      { prop: 'status', label: '状态', width: 90, tag: true },
    ],
    rows: [
      { room: '1栋502', type: '水表', last: 1203, current: 1218, usage: 15, status: '已抄' },
      { room: '1栋502', type: '电表', last: 8801, current: 8920, usage: 119, status: '已抄' },
      { room: '2栋301', type: '水表', last: 560, current: 560, usage: 0, status: '待抄' },
    ],
  },
  '/admin/invest/room-status': {
    title: '房态管理',
    columns: [
      { prop: 'room', label: '房源' },
      { prop: 'area', label: '面积', width: 90 },
      { prop: 'rent', label: '租金(元/月)', width: 120 },
      { prop: 'mode', label: '展示模式', width: 110 },
      { prop: 'status', label: '房态', width: 100, tag: true },
    ],
    rows: [
      { room: '商业A-101', area: 120, rent: 8500, mode: '平面房态', status: '可租' },
      { room: '商业A-102', area: 98, rent: 7200, mode: '平面房态', status: '已租' },
      { room: '公寓B-801', area: 45, rent: 2800, mode: '线性模式', status: '预订' },
    ],
  },
  '/admin/invest/lease': {
    title: '租赁管理',
    columns: [
      { prop: 'room', label: '房源' },
      { prop: 'tenant', label: '租客', width: 100 },
      { prop: 'start', label: '起租日', width: 120 },
      { prop: 'end', label: '到期日', width: 120 },
      { prop: 'status', label: '状态', width: 90, tag: true },
    ],
    rows: [
      { room: '商业A-102', tenant: '星巴克', start: '2025-01-01', end: '2027-12-31', status: '履约中' },
      { room: '公寓B-602', tenant: '陈先生', start: '2026-03-01', end: '2027-02-28', status: '履约中' },
      { room: '商业A-201', tenant: '便利店', start: '2024-06-01', end: '2026-05-31', status: '即将到期' },
    ],
  },
  '/admin/invest/contract': {
    title: '合同管理',
    columns: [
      { prop: 'no', label: '合同编号', width: 140 },
      { prop: 'title', label: '合同名称' },
      { prop: 'party', label: '签约方', width: 120 },
      { prop: 'amount', label: '合同金额', width: 110 },
      { prop: 'status', label: '状态', width: 90, tag: true },
    ],
    rows: [
      { no: 'HT202501001', title: '商业A-102租赁合同', party: '星巴克', amount: 204000, status: '生效' },
      { no: 'HT202603008', title: '公寓B-602租赁合同', party: '陈先生', amount: 33600, status: '生效' },
      { no: 'HT202405012', title: '商业A-201租赁合同', party: '便利店', amount: 96000, status: '审批中' },
    ],
  },
  '/admin/invest/customer': {
    title: '客户管理',
    columns: [
      { prop: 'name', label: '客户名称' },
      { prop: 'contact', label: '联系人', width: 100 },
      { prop: 'phone', label: '电话', width: 130 },
      { prop: 'intent', label: '意向房源' },
      { prop: 'level', label: '意向等级', width: 100, tag: true },
    ],
    rows: [
      { name: '某连锁餐饮', contact: '王经理', phone: '13611112222', intent: '商业A-101', level: '高' },
      { name: '个人客户-刘', contact: '刘女士', phone: '13522223333', intent: '公寓B-801', level: '中' },
      { name: '教育机构', contact: '赵总', phone: '13433334444', intent: '商业B整层', level: '低' },
    ],
  },
  '/admin/invest/tenant': {
    title: '租客管理',
    columns: [
      { prop: 'name', label: '租客' },
      { prop: 'room', label: '在租房源' },
      { prop: 'phone', label: '电话', width: 130 },
      { prop: 'deposit', label: '押金', width: 100 },
      { prop: 'status', label: '状态', width: 90, tag: true },
    ],
    rows: [
      { name: '星巴克', room: '商业A-102', phone: '4008001234', deposit: 17000, status: '在租' },
      { name: '陈先生', room: '公寓B-602', phone: '13855556666', deposit: 5600, status: '在租' },
      { name: '便利店', room: '商业A-201', phone: '13977778888', deposit: 8000, status: '退租中' },
    ],
  },
  '/admin/iot/access': {
    title: '智能门禁',
    columns: [
      { prop: 'name', label: '门禁点' },
      { prop: 'device', label: '设备SN', width: 140 },
      { prop: 'openCount', label: '今日开门', width: 100 },
      { prop: 'alarm', label: '告警', width: 90 },
      { prop: 'status', label: '状态', width: 90, tag: true },
    ],
    rows: [
      { name: '南大门', device: 'AC900012', openCount: 326, alarm: 0, status: '正常' },
      { name: '地下车库入口', device: 'AC900045', openCount: 188, alarm: 1, status: '告警' },
      { name: '1栋单元门', device: 'AC900078', openCount: 96, alarm: 0, status: '正常' },
    ],
  },
  '/admin/iot/parking': {
    title: '智慧停车',
    columns: [
      { prop: 'plate', label: '车牌', width: 110 },
      { prop: 'owner', label: '车主', width: 100 },
      { prop: 'space', label: '车位' },
      { prop: 'type', label: '类型', width: 90 },
      { prop: 'status', label: '状态', width: 90, tag: true },
    ],
    rows: [
      { plate: '粤B12345', owner: '王芳', space: 'B1-068', type: '月卡', status: '在场' },
      { plate: '粤B67890', owner: '访客', space: '临停区', type: '临停', status: '已离场' },
      { plate: '粤B88888', owner: '李强', space: 'B2-012', type: '月卡', status: '在场' },
    ],
  },
  '/admin/iot/charging': {
    title: '充电桩管理',
    columns: [
      { prop: 'name', label: '桩位' },
      { prop: 'power', label: '功率', width: 90 },
      { prop: 'usage', label: '今日用量(度)', width: 120 },
      { prop: 'status', label: '状态', width: 100, tag: true },
    ],
    rows: [
      { name: 'A区-01', power: '7kW', usage: 18.6, status: '空闲' },
      { name: 'A区-02', power: '7kW', usage: 32.1, status: '充电中' },
      { name: 'B区-01', power: '21kW', usage: 0, status: '故障' },
    ],
  },
  '/admin/system/org': {
    title: '组织账号',
    columns: [
      { prop: 'name', label: '姓名', width: 100 },
      { prop: 'account', label: '账号', width: 120 },
      { prop: 'dept', label: '部门' },
      { prop: 'role', label: '角色', width: 120 },
      { prop: 'status', label: '状态', width: 90, tag: true },
    ],
    rows: [
      { name: '系统管理员', account: 'admin', dept: '运营中心', role: '超级管理员', status: '启用' },
      { name: '陈伟', account: 'chenwei', dept: '物业部', role: '物业经理', status: '启用' },
      { name: '张师傅', account: 'zhangsf', dept: '工程部', role: '维修员', status: '启用' },
    ],
  },
  '/property/residents': {
    title: '住户管理',
    columns: [
      { prop: 'name', label: '姓名', width: 100 },
      { prop: 'phone', label: '手机号', width: 130 },
      { prop: 'room', label: '房屋' },
      { prop: 'type', label: '身份', width: 90 },
      { prop: 'status', label: '状态', width: 90, tag: true },
    ],
    rows: [
      { name: '王芳', phone: '13800001111', room: '1栋502', type: '业主', status: '正常' },
      { name: '李强', phone: '13900002222', room: '2栋301', type: '租客', status: '正常' },
    ],
  },
  '/property/move': {
    title: '迁入迁出审批',
    showDateRange: true,
    columns: [
      { prop: 'no', label: '申请单号', width: 140 },
      { prop: 'name', label: '申请人', width: 100 },
      { prop: 'room', label: '房屋' },
      { prop: 'type', label: '类型', width: 90 },
      { prop: 'status', label: '状态', width: 100, tag: true },
    ],
    rows: [
      { no: 'MV2026071601', name: '周杰', room: '5栋901', type: '迁入', status: '待审批' },
      { no: 'MV2026071503', name: '赵敏', room: '3栋1201', type: '迁出', status: '审批中' },
      { no: 'MV2026071002', name: '钱进', room: '4栋202', type: '迁入', status: '已通过' },
    ],
  },
  '/property/repair': {
    title: '报事报修',
    showDateRange: true,
    columns: [
      { prop: 'no', label: '工单号', width: 150 },
      { prop: 'title', label: '问题描述' },
      { prop: 'room', label: '房屋', width: 120 },
      { prop: 'priority', label: '优先级', width: 90 },
      { prop: 'status', label: '状态', width: 100, tag: true },
    ],
    rows: [
      { no: 'BX2026071701', title: '卫生间漏水', room: '1栋502', priority: '高', status: '待派单' },
      { no: 'BX2026071605', title: '空调外机异响', room: '2栋301', priority: '中', status: '处理中' },
      { no: 'BX2026071402', title: '门禁刷卡失败', room: '公区', priority: '中', status: '已完成' },
    ],
  },
  '/property/fee': {
    title: '缴费管理',
    columns: [
      { prop: 'room', label: '房屋' },
      { prop: 'owner', label: '缴费人', width: 100 },
      { prop: 'item', label: '费用项', width: 100 },
      { prop: 'amount', label: '金额', width: 100 },
      { prop: 'status', label: '状态', width: 90, tag: true },
    ],
    rows: [
      { room: '1栋502', owner: '王芳', item: '物业费', amount: 274.4, status: '未缴' },
      { room: '2栋301', owner: '李强', item: '停车费', amount: 200, status: '已缴' },
    ],
  },
  '/property/visitor': {
    title: '临时访客',
    showDateRange: true,
    columns: [
      { prop: 'name', label: '访客', width: 100 },
      { prop: 'phone', label: '手机号', width: 130 },
      { prop: 'host', label: '被访住户' },
      { prop: 'visitAt', label: '来访时间', width: 160 },
      { prop: 'status', label: '状态', width: 90, tag: true },
    ],
    rows: [
      { name: '快递小哥', phone: '13011112222', host: '1栋502王芳', visitAt: '2026-07-17 09:20', status: '已放行' },
      { name: '张女士', phone: '13122223333', host: '2栋301李强', visitAt: '2026-07-17 10:05', status: '待审核' },
    ],
  },
  '/property/inspection': {
    title: '巡检任务',
    columns: [
      { prop: 'task', label: '任务' },
      { prop: 'points', label: '点位数', width: 90 },
      { prop: 'done', label: '已巡', width: 80 },
      { prop: 'owner', label: '执行人', width: 100 },
      { prop: 'status', label: '状态', width: 100, tag: true },
    ],
    rows: [
      { task: '今日消防巡检', points: 24, done: 18, owner: '安保-小刘', status: '进行中' },
      { task: '电梯机房巡检', points: 12, done: 12, owner: '工程-老张', status: '已完成' },
      { task: '夜间巡逻', points: 16, done: 0, owner: '安保-小王', status: '未开始' },
    ],
  },
  '/property/maintenance': {
    title: '设备维保',
    columns: [
      { prop: 'device', label: '设备' },
      { prop: 'plan', label: '维保计划' },
      { prop: 'nextDate', label: '下次维保', width: 120 },
      { prop: 'vendor', label: '维保单位', width: 140 },
      { prop: 'status', label: '状态', width: 100, tag: true },
    ],
    rows: [
      { device: '1号电梯', plan: '季度维保', nextDate: '2026-08-01', vendor: '通力电梯', status: '待维保' },
      { device: '消防水泵', plan: '半年维保', nextDate: '2026-09-15', vendor: '消防维保公司', status: '正常' },
      { device: '监控系统', plan: '年度维保', nextDate: '2026-12-01', vendor: '海康服务商', status: '正常' },
    ],
  },
  '/property/receipt': {
    title: '收款通知',
    columns: [
      { prop: 'title', label: '通知标题' },
      { prop: 'scope', label: '推送范围' },
      { prop: 'amountHint', label: '费用说明', width: 140 },
      { prop: 'sendAt', label: '发送时间', width: 160 },
      { prop: 'status', label: '状态', width: 90, tag: true },
    ],
    rows: [
      { title: '2026年7月物业费缴纳提醒', scope: '全小区业主', amountHint: '按房屋面积计费', sendAt: '2026-07-01 09:00', status: '已发送' },
      { title: '停车费欠费催缴', scope: '欠费车位业主', amountHint: '逾期费用', sendAt: '2026-07-10 10:00', status: '已发送' },
    ],
  },
  '/property/notice': {
    title: '公告通知',
    columns: [
      { prop: 'title', label: '标题' },
      { prop: 'type', label: '类型', width: 90 },
      { prop: 'publishAt', label: '发布时间', width: 120 },
      { prop: 'status', label: '状态', width: 90, tag: true },
    ],
    rows: [
      { title: '停水通知', type: '公告', publishAt: '2026-07-16', status: '已发布' },
      { title: '夏季纳凉活动报名', type: '活动', publishAt: '2026-07-14', status: '已发布' },
    ],
  },
}

export function getListPage(path: string): ListPageConfig | undefined {
  return listPages[path]
}
