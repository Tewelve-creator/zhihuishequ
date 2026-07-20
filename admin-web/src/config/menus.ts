import { FEATURES } from './features'

export interface MenuItem {
  path?: string
  title: string
  icon?: string
  /** 所需套餐功能码；空表示不校验 */
  feature?: string
  children?: MenuItem[]
}

/** 管理端菜单 — 对齐 Axure 2270660 PC运营管理端 */
export const adminMenus: MenuItem[] = [
  { path: '/admin/cockpit', title: '数据监控驾驶舱', icon: 'DataBoard', feature: FEATURES.DATA_DASHBOARD },
  {
    title: '基础信息管理',
    icon: 'OfficeBuilding',
    children: [
      { path: '/admin/basic/park', title: '园区项目管理' },
      { path: '/admin/basic/building', title: '楼栋房屋' },
      { path: '/admin/basic/resident', title: '住户管理' },
      { path: '/admin/basic/venue', title: '场地管理' },
    ],
  },
  {
    title: '物业管理',
    icon: 'House',
    children: [
      { path: '/admin/property/workorder', title: '工单管理', feature: FEATURES.BASIC_REPAIR },
      { path: '/admin/property/inspection', title: '巡检管理' },
      { path: '/admin/property/device', title: '设备管理' },
      { path: '/admin/property/asset', title: '设备台账' },
    ],
  },
  {
    title: '缴费管理',
    icon: 'Wallet',
    children: [
      { path: '/admin/payment/cashier', title: '收银台' },
      { path: '/admin/payment/items', title: '收费项目' },
      { path: '/admin/payment/bills', title: '账单管理' },
      { path: '/admin/payment/account', title: '账务管理' },
      { path: '/admin/payment/meter', title: '仪表管理' },
    ],
  },
  {
    title: '招商管理',
    icon: 'Suitcase',
    children: [
      { path: '/admin/invest/room-status', title: '房态管理' },
      { path: '/admin/invest/lease', title: '租赁管理' },
      { path: '/admin/invest/contract', title: '合同管理' },
      { path: '/admin/invest/customer', title: '客户管理' },
      { path: '/admin/invest/tenant', title: '租客管理' },
    ],
  },
  {
    title: '智慧物联',
    icon: 'Cpu',
    children: [
      { path: '/admin/iot/access', title: '智能门禁' },
      { path: '/admin/iot/parking', title: '智慧停车' },
      { path: '/admin/iot/charging', title: '充电桩管理' },
    ],
  },
  { path: '/admin/reports', title: '统计报表', icon: 'TrendCharts', feature: FEATURES.DATA_EXPORT },
  {
    title: '系统管理',
    icon: 'Setting',
    children: [
      { path: '/admin/system/org', title: '组织账号' },
      { path: '/admin/system/roles', title: '角色权限' },
    ],
  },
]

/** 物业端菜单 */
export const propertyMenus: MenuItem[] = [
  { path: '/property/dashboard', title: '工作台', icon: 'Odometer' },
  { path: '/property/staff', title: '账号管理', icon: 'UserFilled' },
  { path: '/property/residents', title: '住户管理', icon: 'User' },
  { path: '/property/move', title: '迁入迁出审批', icon: 'Switch' },
  { path: '/property/repair', title: '报事报修', icon: 'Tools', feature: FEATURES.BASIC_REPAIR },
  { path: '/property/fee', title: '缴费管理', icon: 'CreditCard' },
  { path: '/property/visitor', title: '临时访客', icon: 'Avatar' },
  { path: '/property/inspection', title: '巡检任务', icon: 'MapLocation' },
  { path: '/property/maintenance', title: '设备维保', icon: 'SetUp' },
  { path: '/property/receipt', title: '收款通知', icon: 'Bell' },
  { path: '/property/notice', title: '公告通知', icon: 'ChatDotRound', feature: FEATURES.BASIC_NOTICE },
  { path: '/property/screen', title: '数据大屏', icon: 'DataBoard', feature: FEATURES.DATA_DASHBOARD },
  { path: '/property/export', title: '数据导出', icon: 'Download', feature: FEATURES.DATA_EXPORT },
  { path: '/property/renew', title: '套餐续费', icon: 'Ticket' },
]

/** 按套餐功能码过滤菜单 */
export function filterMenusByFeature(
  menus: MenuItem[],
  can: (code: string) => boolean,
): MenuItem[] {
  return menus
    .map((m) => {
      if (m.children?.length) {
        const children = filterMenusByFeature(m.children, can)
        if (!children.length) return null
        return { ...m, children }
      }
      if (m.feature && !can(m.feature)) return null
      return m
    })
    .filter(Boolean) as MenuItem[]
}
