import { FEATURES } from './features'

export interface MenuItem {
  path?: string
  title: string
  icon?: string
  /** 所需套餐功能码；空表示不校验 */
  feature?: string
  /** 当前套餐不可用：仍显示，禁用并带锁 */
  locked?: boolean
  children?: MenuItem[]
}

/**
 * SaaS 运营管理端菜单（平台管理员）
 * 信息架构：主业务 → 套餐与计费 → 权限管理 → AI 管理 → 经营分析 → 财务管理 → 商户/结算 → 平台设置 → 系统运维
 */
export const adminMenus: MenuItem[] = [
  { path: '/admin/saas/cockpit', title: '运营数据驾驶舱', icon: 'DataBoard' },
  {
    title: '主业务',
    icon: 'OfficeBuilding',
    children: [
      { path: '/admin/saas/onboard', title: '开户向导' },
      { path: '/admin/saas/tenants', title: 'SaaS租户管理' },
    ],
  },
  {
    title: '套餐与计费',
    icon: 'Ticket',
    children: [
      { path: '/admin/saas/packages', title: '套餐管理' },
      { path: '/admin/saas/orders', title: '订单管理' },
      { path: '/admin/saas/monitor/expiring', title: '到期预警' },
    ],
  },
  {
    title: '权限管理',
    icon: 'Lock',
    children: [{ path: '/admin/saas/tenant-features', title: '租户授权' }],
  },
  {
    title: 'AI 管理',
    icon: 'MagicStick',
    children: [
      { path: '/admin/saas/ai-quota', title: '用量与额度' },
      { path: '/admin/saas/analytics/ai', title: 'AI 用量分析' },
      { path: '/admin/saas/sensitive-words', title: '敏感词库' },
    ],
  },
  {
    title: '经营分析',
    icon: 'DataAnalysis',
    children: [
      { path: '/admin/saas/analytics/overview', title: '运营总览' },
      { path: '/admin/saas/analytics/commerce', title: '商业运营' },
      { path: '/admin/saas/analytics/finance', title: '财务分析' },
    ],
  },
  {
    title: '财务管理',
    icon: 'Wallet',
    children: [
      { path: '/admin/saas/finance/refunds', title: '退款管理' },
      { path: '/admin/saas/finance/reconcile', title: '对账管理' },
    ],
  },
  {
    title: '商户与超市',
    icon: 'ShoppingBag',
    children: [
      { path: '/admin/saas/merchant/shops', title: '商户管理' },
      { path: '/admin/saas/merchant/onboard', title: '入驻审核' },
      { path: '/admin/saas/merchant/revenue', title: '商户营收' },
    ],
  },
  {
    title: '结算管理',
    icon: 'Coin',
    children: [
      { path: '/admin/saas/settlement/commission', title: '提成明细' },
      { path: '/admin/saas/settlement/withdraw', title: '提现审核' },
      { path: '/admin/saas/settlement/profit-rules', title: '提成规则' },
    ],
  },
  {
    title: '平台设置',
    icon: 'Setting',
    children: [
      { path: '/admin/saas/settings/platform', title: '平台信息' },
      { path: '/admin/saas/settings/accounts', title: '账号与权限' },
      { path: '/admin/saas/settings/security', title: '安全与审计' },
      { path: '/admin/saas/settings/ai', title: 'AI 服务配置' },
      { path: '/admin/saas/settings/system', title: '系统配置' },
    ],
  },
  {
    title: '系统运维',
    icon: 'Monitor',
    children: [{ path: '/admin/saas/ops/services', title: '服务状态' }],
  },
]

/** 物业端菜单（仅本小区本地能力） */
export const propertyMenus: MenuItem[] = [
  { path: '/property/dashboard', title: '工作台', icon: 'Odometer' },
  {
    path: '/property/cockpit',
    title: '数据大屏',
    icon: 'DataBoard',
    feature: FEATURES.DATA_DASHBOARD,
  },
  {
    title: '住户管理',
    icon: 'User',
    children: [
      { path: '/property/residents/buildings', title: '楼栋信息' },
      { path: '/property/residents/houses', title: '房屋档案' },
      { path: '/property/residents/owners', title: '业主住户' },
      { path: '/property/residents/tenants', title: '租客管理' },
      { path: '/property/residents/visits', title: '访客预约通行' },
    ],
  },
  {
    title: '车辆管理',
    icon: 'Van',
    children: [
      { path: '/property/parking/spaces', title: '车位档案' },
      { path: '/property/cars', title: '业主车辆' },
      { path: '/property/parking/access', title: '临时通行记录' },
      { path: '/property/parking/fees', title: '停车收费台账' },
    ],
  },
  {
    title: '迁入迁出审批',
    icon: 'Switch',
    children: [
      { path: '/property/move', title: '申请列表' },
      { path: '/property/move/approve', title: '审批处理' },
      { path: '/property/move/history', title: '历史记录' },
      { path: '/property/move/stats', title: '迁移统计' },
    ],
  },
  {
    title: '报事报修',
    icon: 'Tools',
    feature: FEATURES.BASIC_REPAIR,
    children: [
      { path: '/property/repair', title: '报修工单' },
      { path: '/property/report', title: '报事工单' },
      { path: '/property/repair/assign', title: '报修分派' },
      { path: '/property/report/assign', title: '报事分派' },
      { path: '/property/feedback', title: '意见反馈' },
      { path: '/property/repair/stats', title: '工单统计' },
      { path: '/property/repair/categories', title: '报修分类管理' },
    ],
  },
  {
    title: '缴费管理',
    icon: 'CreditCard',
    children: [
      { path: '/property/fee/bills', title: '账单管理' },
      { path: '/property/fee/flow', title: '缴费流水' },
      { path: '/property/fee/arrears', title: '欠费汇总' },
      { path: '/property/fee/receipt', title: '收款通知' },
    ],
  },
  {
    title: '社区商超',
    icon: 'ShoppingCart',
    feature: FEATURES.VENDOR_MANAGE,
    children: [
      { path: '/property/mall/shops', title: '商户列表' },
      { path: '/property/mall/orders', title: '商超总订单' },
      { path: '/property/store/onboard-audit', title: '超市入驻初审' },
      { path: '/property/store/onboard', title: '本小区超市总览' },
      { path: '/property/store/settlement', title: '超市佣金应收' },
    ],
  },
  {
    title: '社区活动',
    icon: 'Calendar',
    children: [
      { path: '/property/activity', title: '活动列表' },
      { path: '/property/activity/signups', title: '报名管理' },
      { path: '/property/activity/categories', title: '活动分类' },
      { path: '/property/activity/stats', title: '活动统计' },
    ],
  },
  {
    title: '失物招领',
    icon: 'Box',
    feature: FEATURES.AI_LOST_MATCH,
    children: [
      { path: '/property/lost', title: '招领信息列表' },
      { path: '/property/lost/claims', title: '认领核实' },
      { path: '/property/lost/categories', title: '物品分类管理' },
    ],
  },
  {
    title: '社区动态',
    icon: 'ChatLineSquare',
    children: [
      { path: '/property/moment/posts', title: '动态管理' },
      { path: '/property/moment/comments', title: '评论管理' },
      { path: '/property/moment/reports', title: '举报管理' },
      { path: '/property/neighbor-help', title: '邻里互助管理' },
    ],
  },
  {
    title: 'AI知识库',
    icon: 'Collection',
    feature: FEATURES.AI_CHAT,
    children: [
      { path: '/property/kb/categories', title: '知识库分类' },
      { path: '/property/kb/docs', title: '文档管理' },
      { path: '/property/kb/chat-logs', title: '问答日志' },
      { path: '/property/kb/settings', title: '知识库设置' },
    ],
  },
  {
    title: 'AI特色',
    icon: 'MagicStick',
    children: [
      { path: '/property/ai/schedule', title: 'AI智能日程', feature: FEATURES.AI_CHAT },
      { path: '/property/ai/image-logs', title: 'AI识图日志', feature: FEATURES.AI_IMAGE_REPAIR },
      { path: '/property/ai/emotion', title: '情绪调解工单', feature: FEATURES.AI_EMOTION },
      { path: '/property/ai/sensitive', title: '敏感词库', feature: FEATURES.BASIC_NOTICE },
    ],
  },
  {
    title: '设施运维',
    icon: 'MapLocation',
    children: [
      { path: '/property/inspection-points', title: '巡检点位' },
      { path: '/property/inspection', title: '巡检任务' },
      { path: '/property/inspection-records', title: '巡检记录' },
      { path: '/property/equip-devices', title: '设备清单' },
      { path: '/property/maintenance', title: '设备维保' },
    ],
  },
  {
    title: '套餐管理',
    icon: 'Ticket',
    children: [
      { path: '/property/package', title: '订阅概览' },
      { path: '/property/package/features', title: '功能权限' },
      { path: '/property/package/quota', title: '用量与额度' },
      { path: '/property/package/upgrade', title: '升级与办理' },
      { path: '/property/package/orders', title: '订购记录' },
    ],
  },
  { path: '/property/notice', title: '公告通知', icon: 'ChatDotRound', feature: FEATURES.BASIC_NOTICE },
  { path: '/property/staff', title: '账号管理', icon: 'UserFilled' },
]

/** 独立商户后台菜单（按 shop_id 隔离；不含提现申请） */
export const merchantMenus: MenuItem[] = [
  { path: '/merchant/home', title: '首页', icon: 'HomeFilled' },
  { path: '/merchant/shop', title: '店铺信息', icon: 'Shop' },
  { path: '/merchant/products', title: '商品管理', icon: 'Goods' },
  { path: '/merchant/orders', title: '订单管理', icon: 'List' },
  { path: '/merchant/coupons', title: '优惠券管理', icon: 'Ticket' },
  { path: '/merchant/revenue', title: '营销资金', icon: 'Wallet' },
  { path: '/merchant/account', title: '系统设置', icon: 'Setting' },
]

/**
 * 按套餐装饰菜单：无权限不隐藏，标记 locked。
 * @param can true=可用
 */
export function filterMenusByFeature(
  menus: MenuItem[],
  can: (code: string) => boolean,
): MenuItem[] {
  return decorateMenusByFeature(menus, can)
}

/** 保留全部菜单项，不可用项 locked=true */
export function decorateMenusByFeature(
  menus: MenuItem[],
  can: (code: string) => boolean,
): MenuItem[] {
  return menus.map((m) => {
    const children = m.children?.length ? decorateMenusByFeature(m.children, can) : undefined
    const selfLocked = !!(m.feature && !can(m.feature))
    return {
      ...m,
      locked: selfLocked,
      children: children?.map((c) => ({
        ...c,
        locked: !!(c.locked || selfLocked),
      })),
    }
  })
}
