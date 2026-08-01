import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { listPages } from '@/config/listPages'

const Layout = () => import('@/layouts/AdminLayout.vue')
const GenericList = () => import('@/views/shared/GenericListView.vue')

const listChildren: RouteRecordRaw[] = Object.keys(listPages)
  .filter(
    (path) =>
      path !== '/property/repair' &&
      path !== '/property/report' &&
      path !== '/property/repair/assign' &&
      path !== '/property/report/assign' &&
      path !== '/property/repair/stats' &&
      path !== '/property/repair/categories' &&
      path !== '/property/activity' &&
      path !== '/property/activity/signups' &&
      path !== '/property/activity/categories' &&
      path !== '/property/activity/stats' &&
      path !== '/property/lost' &&
      path !== '/property/lost/claims' &&
      path !== '/property/lost/categories' &&
      path !== '/property/notice' &&
      path !== '/property/feedback' &&
      path !== '/property/neighbor-help' &&
      path !== '/property/residents' &&
      path !== '/property/residents/buildings' &&
      path !== '/property/residents/houses' &&
      path !== '/property/residents/owners' &&
      path !== '/property/residents/tenants' &&
      path !== '/property/residents/visits' &&
      path !== '/property/move' &&
      path !== '/property/move/approve' &&
      path !== '/property/move/history' &&
      path !== '/property/move/stats' &&
      path !== '/property/fee' &&
      path !== '/property/fee/bills' &&
      path !== '/property/fee/flow' &&
      path !== '/property/fee/arrears' &&
      path !== '/property/fee/receipt' &&
      path !== '/property/receipt' &&
      path !== '/property/inspection' &&
      path !== '/property/inspection-points' &&
      path !== '/property/inspection-records' &&
      path !== '/property/equip-devices' &&
      path !== '/property/maintenance' &&
      path !== '/property/visitor' &&
      path !== '/property/houses' &&
      path !== '/property/cars' &&
      path !== '/property/parking/spaces' &&
      path !== '/property/parking/access' &&
      path !== '/property/parking/fees' &&
      path !== '/property/store' &&
      path !== '/property/store/merchants' &&
      path !== '/property/store/products' &&
      path !== '/property/store/orders' &&
      path !== '/property/store/settlement' &&
      path !== '/property/store/onboard' &&
      path !== '/property/store/onboard-audit' &&
      path !== '/property/mall/shops' &&
      path !== '/property/mall/orders' &&
      path !== '/merchant/home' &&
      path !== '/merchant/shop' &&
      path !== '/merchant/products' &&
      path !== '/merchant/orders' &&
      path !== '/merchant/coupons' &&
      path !== '/merchant/revenue' &&
      path !== '/merchant/account',
  )
  .map((path) => ({
    path: path.replace(/^\//, ''),
    component: GenericList,
    meta: {
      title: listPages[path].title,
      portal: path.startsWith('/admin')
        ? 'admin'
        : path.startsWith('/merchant')
          ? 'merchant'
          : 'property',
    },
  }))

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/LoginView.vue'),
    meta: { public: true, title: '登录' },
  },
  {
    path: '/',
    component: Layout,
    redirect: '/admin/saas/cockpit',
    children: [
      {
        path: 'admin/cockpit',
        redirect: '/admin/saas/cockpit',
      },
      {
        path: 'admin/saas/cockpit',
        component: () => import('@/views/saas/SaasCockpitView.vue'),
        meta: { title: '运营数据驾驶舱', portal: 'admin', screen: true },
      },
      {
        path: 'admin/saas/onboard',
        component: () => import('@/views/saas/TenantOnboardWizardView.vue'),
        meta: { title: '开户向导', portal: 'admin' },
      },
      {
        path: 'admin/saas/tenants',
        component: () => import('@/views/saas/TenantManageView.vue'),
        meta: { title: 'SaaS租户管理', portal: 'admin' },
      },
      {
        path: 'admin/saas/expiring',
        redirect: '/admin/saas/monitor/expiring',
      },
      {
        path: 'admin/saas/monitor/expiring',
        component: () => import('@/views/saas/ExpiringTenantView.vue'),
        meta: { title: '到期预警', portal: 'admin' },
      },
      {
        path: 'admin/saas/monitor/risk-orders',
        redirect: '/admin/saas/analytics/overview',
      },
      {
        path: 'admin/saas/monitor/ai-calls',
        redirect: '/admin/saas/analytics/ai',
      },
      {
        path: 'admin/saas/ops/notices',
        redirect: '/admin/saas/cockpit',
      },
      {
        path: 'admin/saas/ops/activities',
        redirect: '/admin/saas/cockpit',
      },
      {
        path: 'admin/saas/ops/appeals',
        redirect: '/admin/saas/cockpit',
      },
      {
        path: 'admin/saas/content/notices',
        redirect: '/admin/saas/cockpit',
      },
      {
        path: 'admin/saas/content/activities',
        redirect: '/admin/saas/cockpit',
      },
      {
        path: 'admin/saas/content/moderation',
        redirect: '/admin/saas/sensitive-words',
      },
      {
        path: 'admin/saas/analytics/overview',
        component: () => import('@/views/saas/analytics/OverviewView.vue'),
        meta: { title: '运营总览', portal: 'admin' },
      },
      {
        path: 'admin/saas/analytics/workorders',
        redirect: '/admin/saas/analytics/overview',
      },
      {
        path: 'admin/saas/analytics/commerce',
        component: () => import('@/views/saas/analytics/CommerceView.vue'),
        meta: { title: '商业运营', portal: 'admin' },
      },
      {
        path: 'admin/saas/analytics/ai',
        component: () => import('@/views/saas/analytics/AiUsageView.vue'),
        meta: { title: 'AI 用量分析', portal: 'admin' },
      },
      {
        path: 'admin/saas/analytics/finance',
        component: () => import('@/views/saas/analytics/FinanceAnalysisView.vue'),
        meta: { title: '财务分析', portal: 'admin' },
      },
      {
        path: 'admin/saas/finance/refunds',
        component: () => import('@/views/saas/finance/RefundManageView.vue'),
        meta: { title: '退款管理', portal: 'admin' },
      },
      {
        path: 'admin/saas/finance/reconcile',
        component: () => import('@/views/saas/finance/ReconcileView.vue'),
        meta: { title: '对账管理', portal: 'admin' },
      },
      {
        path: 'admin/saas/reports/ops',
        redirect: '/admin/saas/analytics/overview',
      },
      {
        path: 'admin/saas/reports/store',
        redirect: '/admin/saas/analytics/commerce',
      },
      {
        path: 'admin/saas/reports/ai',
        redirect: '/admin/saas/analytics/ai',
      },
      {
        path: 'admin/saas/packages',
        component: () => import('@/views/saas/PackageManageView.vue'),
        meta: { title: '套餐管理', portal: 'admin' },
      },
      {
        path: 'admin/saas/package-features',
        redirect: '/admin/saas/packages',
      },
      {
        path: 'admin/saas/ai-quota',
        component: () => import('@/views/saas/AiQuotaView.vue'),
        meta: { title: '用量与额度', portal: 'admin' },
      },
      {
        path: 'admin/saas/tenant-features',
        component: () => import('@/views/saas/TenantFeatureView.vue'),
        meta: { title: '租户授权', portal: 'admin' },
      },
      {
        path: 'admin/saas/feature-templates',
        redirect: '/admin/saas/tenant-features',
      },
      {
        path: 'admin/saas/settings/platform',
        component: () => import('@/views/saas/settings/PlatformInfoView.vue'),
        meta: { title: '平台信息', portal: 'admin' },
      },
      {
        path: 'admin/saas/settings/accounts',
        component: () => import('@/views/saas/settings/AccountsPermissionView.vue'),
        meta: { title: '账号与权限', portal: 'admin' },
      },
      {
        path: 'admin/saas/settings/security',
        component: () => import('@/views/saas/settings/SecurityAuditView.vue'),
        meta: { title: '安全与审计', portal: 'admin' },
      },
      {
        path: 'admin/saas/settings/ai',
        component: () => import('@/views/saas/settings/AiServiceConfigView.vue'),
        meta: { title: 'AI 服务配置', portal: 'admin' },
      },
      {
        path: 'admin/saas/settings/system',
        component: () => import('@/views/saas/settings/SystemConfigView.vue'),
        meta: { title: '系统配置', portal: 'admin' },
      },
      {
        path: 'admin/saas/ops/services',
        component: () => import('@/views/saas/settings/ServiceStatusView.vue'),
        meta: { title: '服务状态', portal: 'admin' },
      },
      {
        path: 'admin/saas/sensitive-words',
        component: () => import('@/views/saas/PlatformSensitiveView.vue'),
        meta: { title: '敏感词库', portal: 'admin' },
      },

      { path: 'admin/saas/ai-config', redirect: '/admin/saas/settings/ai' },
      { path: 'admin/saas/accounts', redirect: '/admin/saas/settings/accounts' },
      { path: 'admin/saas/roles', redirect: { path: '/admin/saas/settings/accounts', query: { tab: 'roles' } } },
      { path: 'admin/saas/logs', redirect: '/admin/saas/settings/security' },
      { path: 'admin/saas/config', redirect: '/admin/saas/settings/system' },
      { path: 'admin/saas/monitor', redirect: '/admin/saas/ops/services' },
      {
        path: 'admin/saas/ai-calls',
        redirect: '/admin/saas/analytics/ai',
      },
      {
        path: 'admin/saas/orders',
        component: () => import('@/views/saas/OrderManageView.vue'),
        meta: { title: '订单管理', portal: 'admin' },
      },
      {
        path: 'admin/saas/finance',
        redirect: '/admin/saas/analytics/finance',
      },
      {
        path: 'admin/saas/refunds',
        redirect: '/admin/saas/finance/refunds',
      },
      {
        path: 'admin/saas/merchant-withdraw',
        redirect: '/admin/saas/settlement/withdraw',
      },
      {
        path: 'admin/saas/merchant-onboard',
        redirect: '/admin/saas/merchant/shops',
      },
      {
        path: 'admin/saas/merchant-overview',
        redirect: '/admin/saas/merchant/shops',
      },
      {
        path: 'admin/saas/merchant-final-audit',
        redirect: '/admin/saas/merchant/onboard',
      },
      {
        path: 'admin/saas/merchant-commission',
        redirect: '/admin/saas/settlement/commission',
      },
      {
        path: 'admin/saas/profit-rules',
        redirect: '/admin/saas/settlement/profit-rules',
      },
      {
        path: 'admin/saas/mall-activities',
        redirect: '/admin/saas/merchant/shops',
      },
      {
        path: 'admin/saas/merchant/shops',
        component: () => import('@/views/saas/MerchantOnboardOverviewView.vue'),
        meta: { title: '商户管理', portal: 'admin' },
      },
      {
        path: 'admin/saas/merchant/onboard',
        component: () => import('@/views/saas/MerchantOnboardAuditView.vue'),
        meta: { title: '入驻审核', portal: 'admin', onboardMode: 'platform' },
        props: { mode: 'platform' },
      },
      {
        path: 'admin/saas/merchant/campaigns',
        redirect: '/admin/saas/merchant/shops',
      },
      {
        path: 'admin/saas/merchant/revenue',
        component: () => import('@/views/saas/MerchantRevenueBoardView.vue'),
        meta: { title: '商户营收', portal: 'admin' },
      },
      {
        path: 'admin/saas/settlement/commission',
        component: () => import('@/views/saas/MerchantCommissionBillView.vue'),
        meta: { title: '提成明细', portal: 'admin' },
      },
      {
        path: 'admin/saas/settlement/withdraw',
        component: () => import('@/views/saas/MerchantWithdrawAuditView.vue'),
        meta: { title: '提现审核', portal: 'admin' },
      },
      {
        path: 'admin/saas/settlement/profit-rules',
        component: () => import('@/views/saas/PlatformProfitRuleView.vue'),
        meta: { title: '提成规则', portal: 'admin' },
      },
      {
        path: 'admin/payment/cashier',
        component: () => import('@/views/admin/CashierView.vue'),
        meta: { title: '收银台', portal: 'admin' },
      },
      {
        path: 'admin/reports',
        component: () => import('@/views/admin/ReportsView.vue'),
        meta: { title: '统计报表', portal: 'admin' },
      },
      {
        path: 'admin/system/roles',
        component: () => import('@/views/admin/RolesView.vue'),
        meta: { title: '角色权限', portal: 'admin' },
      },
      {
        path: 'property/dashboard',
        component: () => import('@/views/property/DashboardView.vue'),
        meta: { title: '工作台', portal: 'property' },
      },
      {
        path: 'property/feature-unavailable',
        component: () => import('@/views/property/FeatureUnavailableView.vue'),
        meta: { title: '功能未开通', portal: 'property' },
      },
      {
        path: 'property/cockpit',
        component: () => import('@/views/property/PropertyCockpitView.vue'),
        meta: { title: '数据大屏', portal: 'property', screen: true, feature: 'data_dashboard' },
      },
      {
        path: 'property/package/features',
        component: () => import('@/views/property/PackageManageView.vue'),
        meta: { title: '功能权限', portal: 'property', packageTab: 'features' },
      },
      {
        path: 'property/package/quota',
        component: () => import('@/views/property/PackageManageView.vue'),
        meta: { title: '用量与额度', portal: 'property', packageTab: 'quota' },
      },
      {
        path: 'property/package/upgrade',
        component: () => import('@/views/property/PackageManageView.vue'),
        meta: { title: '升级与办理', portal: 'property', packageTab: 'upgrade' },
      },
      {
        path: 'property/package/orders',
        component: () => import('@/views/property/PackageManageView.vue'),
        meta: { title: '订购记录', portal: 'property', packageTab: 'orders' },
      },
      {
        path: 'property/package',
        component: () => import('@/views/property/PackageManageView.vue'),
        meta: { title: '订阅概览', portal: 'property', packageTab: 'overview' },
      },
      {
        path: 'property/repair',
        component: () => import('@/views/property/RepairView.vue'),
        meta: { title: '报修工单', portal: 'property', orderType: 'repair', feature: 'basic_repair' },
      },
      {
        path: 'property/report',
        component: () => import('@/views/property/RepairView.vue'),
        meta: { title: '报事工单', portal: 'property', orderType: 'report', feature: 'basic_repair' },
      },
      {
        path: 'property/repair/assign',
        component: () => import('@/views/property/RepairAssignView.vue'),
        meta: { title: '报修分派', portal: 'property', orderType: 'repair', feature: 'basic_repair' },
      },
      {
        path: 'property/report/assign',
        component: () => import('@/views/property/RepairAssignView.vue'),
        meta: { title: '报事分派', portal: 'property', orderType: 'report', feature: 'basic_repair' },
      },
      {
        path: 'property/repair/stats',
        component: () => import('@/views/property/RepairStatsView.vue'),
        meta: { title: '工单统计', portal: 'property', feature: 'basic_repair' },
      },
      {
        path: 'property/repair/categories',
        component: () => import('@/views/property/RepairCategoryView.vue'),
        meta: { title: '报修分类管理', portal: 'property', feature: 'basic_repair' },
      },
      {
        path: 'property/staff',
        component: () => import('@/views/property/StaffAccountsView.vue'),
        meta: { title: '账号管理', portal: 'property' },
      },
      {
        path: 'property/notice',
        component: () => import('@/views/property/NoticeView.vue'),
        meta: { title: '公告通知', portal: 'property', feature: 'basic_notice' },
      },
      {
        path: 'property/feedback',
        component: () => import('@/views/property/FeedbackManageView.vue'),
        meta: { title: '意见反馈', portal: 'property', feature: 'basic_repair' },
      },
      {
        path: 'property/move/approve',
        component: () => import('@/views/property/MoveApproveView.vue'),
        meta: { title: '审批处理', portal: 'property' },
      },
      {
        path: 'property/move/history',
        component: () => import('@/views/property/MoveHistoryView.vue'),
        meta: { title: '历史记录', portal: 'property' },
      },
      {
        path: 'property/move/stats',
        component: () => import('@/views/property/MoveStatsView.vue'),
        meta: { title: '迁移统计', portal: 'property' },
      },
      {
        path: 'property/move',
        component: () => import('@/views/property/MoveApplyListView.vue'),
        meta: { title: '申请列表', portal: 'property' },
      },
      {
        path: 'property/residents',
        redirect: '/property/residents/owners',
      },
      {
        path: 'property/residents/buildings',
        component: () => import('@/views/property/BuildingManageView.vue'),
        meta: { title: '楼栋信息', portal: 'property' },
      },
      {
        path: 'property/residents/houses',
        component: () => import('@/views/property/HouseManageView.vue'),
        meta: { title: '房屋档案', portal: 'property' },
      },
      {
        path: 'property/residents/owners',
        component: () => import('@/views/property/OwnerResidentsView.vue'),
        meta: { title: '业主住户', portal: 'property' },
      },
      {
        path: 'property/residents/tenants',
        component: () => import('@/views/property/TenantManageView.vue'),
        meta: { title: '租客管理', portal: 'property' },
      },
      {
        path: 'property/residents/visits',
        component: () => import('@/views/property/VisitManageView.vue'),
        meta: { title: '访客记录', portal: 'property' },
      },
      {
        path: 'property/parking/spaces',
        component: () => import('@/views/property/ParkSpaceView.vue'),
        meta: { title: '车位档案', portal: 'property' },
      },
      {
        path: 'property/cars',
        component: () => import('@/views/property/ResidentCarView.vue'),
        meta: { title: '业主车辆', portal: 'property' },
      },
      {
        path: 'property/parking/access',
        component: () => import('@/views/property/ParkAccessView.vue'),
        meta: { title: '临时通行记录', portal: 'property' },
      },
      {
        path: 'property/parking/fees',
        component: () => import('@/views/property/ParkFeeLedgerView.vue'),
        meta: { title: '停车收费台账', portal: 'property' },
      },
      {
        path: 'property/fee',
        redirect: '/property/fee/bills',
      },
      {
        path: 'property/fee/bills',
        component: () => import('@/views/property/FeeManageView.vue'),
        meta: { title: '账单管理', portal: 'property' },
      },
      {
        path: 'property/fee/flow',
        component: () => import('@/views/property/FeeFlowView.vue'),
        meta: { title: '缴费流水', portal: 'property' },
      },
      {
        path: 'property/fee/arrears',
        component: () => import('@/views/property/FeeArrearsView.vue'),
        meta: { title: '欠费汇总', portal: 'property' },
      },
      {
        path: 'property/fee/receipt',
        component: () => import('@/views/property/FeeReceiptView.vue'),
        meta: { title: '收款通知', portal: 'property' },
      },
      {
        path: 'property/inspection',
        component: () => import('@/views/property/InspectionTaskView.vue'),
        meta: { title: '巡检任务', portal: 'property' },
      },
      {
        path: 'property/inspection-points',
        component: () => import('@/views/property/InspectionPointView.vue'),
        meta: { title: '巡检点位', portal: 'property' },
      },
      {
        path: 'property/inspection-records',
        component: () => import('@/views/property/InspectionRecordView.vue'),
        meta: { title: '巡检记录', portal: 'property' },
      },
      {
        path: 'property/equip-devices',
        component: () => import('@/views/property/EquipmentDeviceView.vue'),
        meta: { title: '设备清单', portal: 'property' },
      },
      {
        path: 'property/maintenance',
        component: () => import('@/views/property/EquipmentMaintView.vue'),
        meta: { title: '设备维保', portal: 'property' },
      },
      {
        path: 'property/visitor',
        redirect: '/property/residents/visits',
      },
      {
        path: 'property/store',
        redirect: '/property/store/merchants',
      },
      {
        path: 'property/store/merchants',
        component: () => import('@/views/property/StoreMerchantView.vue'),
        meta: { title: '商户档案', portal: 'property', feature: 'vendor_manage' },
      },
      {
        path: 'property/store/products',
        component: () => import('@/views/property/StoreProductView.vue'),
        meta: { title: '商品管理', portal: 'property', feature: 'vendor_manage' },
      },
      {
        path: 'property/store/orders',
        component: () => import('@/views/property/StoreOrderView.vue'),
        meta: { title: '订单管理', portal: 'property', feature: 'vendor_manage' },
      },
      {
        path: 'property/store/settlement',
        component: () => import('@/views/property/StoreSettlementView.vue'),
        meta: { title: '商圈佣金应收', portal: 'property', feature: 'vendor_manage' },
      },
      {
        path: 'property/store/onboard',
        component: () => import('@/views/saas/MerchantOnboardOverviewView.vue'),
        meta: { title: '商户入驻总览', portal: 'property', feature: 'vendor_manage' },
      },
      {
        path: 'property/store/onboard-audit',
        component: () => import('@/views/saas/MerchantOnboardAuditView.vue'),
        meta: { title: '商户入驻初审', portal: 'property', onboardMode: 'property', feature: 'vendor_manage' },
        props: { mode: 'property' },
      },
      {
        path: 'property/mall/shops',
        component: () => import('@/views/property/MallShopListView.vue'),
        meta: { title: '商户列表', portal: 'property', feature: 'vendor_manage' },
      },
      {
        path: 'property/mall/orders',
        component: () => import('@/views/property/MallOrderAllView.vue'),
        meta: { title: '商超总订单', portal: 'property', feature: 'vendor_manage' },
      },
      {
        path: 'merchant/home',
        component: () => import('@/views/merchant/MerchantHomeView.vue'),
        meta: { title: '首页', portal: 'merchant' },
      },
      {
        path: 'merchant/shop',
        component: () => import('@/views/merchant/MerchantShopView.vue'),
        meta: { title: '店铺信息', portal: 'merchant' },
      },
      {
        path: 'merchant/products',
        component: () => import('@/views/merchant/MerchantProductView.vue'),
        meta: { title: '商品管理', portal: 'merchant' },
      },
      {
        path: 'merchant/orders',
        component: () => import('@/views/merchant/MerchantOrderView.vue'),
        meta: { title: '订单管理', portal: 'merchant' },
      },
      {
        path: 'merchant/coupons',
        component: () => import('@/views/merchant/MerchantCouponView.vue'),
        meta: { title: '优惠券管理', portal: 'merchant' },
      },
      {
        path: 'merchant/revenue',
        component: () => import('@/views/merchant/MerchantRevenueView.vue'),
        meta: { title: '营销资金', portal: 'merchant' },
      },
      {
        path: 'merchant/account',
        component: () => import('@/views/merchant/MerchantAccountView.vue'),
        meta: { title: '系统设置', portal: 'merchant' },
      },
      {
        path: 'property/houses',
        redirect: '/property/residents/houses',
      },
      {
        path: 'property/kb/categories',
        component: () => import('@/views/property/KbCategoryView.vue'),
        meta: { title: '知识库分类', portal: 'property', feature: 'ai_chat' },
      },
      {
        path: 'property/kb/docs',
        component: () => import('@/views/property/KbDocManageView.vue'),
        meta: { title: '文档管理', portal: 'property', feature: 'ai_chat' },
      },
      {
        path: 'property/kb/chat-logs',
        component: () => import('@/views/property/KbChatLogView.vue'),
        meta: { title: '问答日志', portal: 'property', feature: 'ai_chat' },
      },
      {
        path: 'property/kb/settings',
        component: () => import('@/views/property/KbSettingsView.vue'),
        meta: { title: '知识库设置', portal: 'property', feature: 'ai_chat' },
      },
      {
        path: 'property/kb',
        redirect: '/property/kb/docs',
      },
      {
        path: 'property/ai/schedule',
        component: () => import('@/views/property/AiScheduleView.vue'),
        meta: { title: 'AI智能日程', portal: 'property', feature: 'ai_chat' },
      },
      {
        path: 'property/ai/image-logs',
        component: () => import('@/views/property/AiImageLogView.vue'),
        meta: { title: 'AI识图日志', portal: 'property', feature: 'ai_image_repair' },
      },
      {
        path: 'property/ai/emotion',
        component: () => import('@/views/property/AiEmotionCaseView.vue'),
        meta: { title: '情绪调解工单', portal: 'property', feature: 'ai_emotion' },
      },
      {
        path: 'property/ai/sensitive',
        component: () => import('@/views/property/AiSensitiveWordView.vue'),
        meta: { title: '敏感词库', portal: 'property', feature: 'basic_notice' },
      },
      {
        path: 'property/activity/signups',
        component: () => import('@/views/property/ActivitySignupView.vue'),
        meta: { title: '报名管理', portal: 'property' },
      },
      {
        path: 'property/activity/categories',
        component: () => import('@/views/property/ActivityCategoryView.vue'),
        meta: { title: '活动分类', portal: 'property' },
      },
      {
        path: 'property/activity/stats',
        component: () => import('@/views/property/ActivityStatsView.vue'),
        meta: { title: '活动统计', portal: 'property' },
      },
      {
        path: 'property/activity',
        component: () => import('@/views/property/ActivityManageView.vue'),
        meta: { title: '活动列表', portal: 'property' },
      },
      {
        path: 'property/lost/claims',
        component: () => import('@/views/property/LostClaimView.vue'),
        meta: { title: '认领核实', portal: 'property', feature: 'ai_lost_match' },
      },
      {
        path: 'property/lost/categories',
        component: () => import('@/views/property/LostCategoryView.vue'),
        meta: { title: '物品分类管理', portal: 'property', feature: 'ai_lost_match' },
      },
      {
        path: 'property/lost',
        component: () => import('@/views/property/LostManageView.vue'),
        meta: { title: '招领信息列表', portal: 'property', feature: 'ai_lost_match' },
      },
      {
        path: 'property/moment/posts',
        component: () => import('@/views/property/MomentPostView.vue'),
        meta: { title: '动态管理', portal: 'property' },
      },
      {
        path: 'property/moment/comments',
        component: () => import('@/views/property/MomentCommentView.vue'),
        meta: { title: '评论管理', portal: 'property' },
      },
      {
        path: 'property/moment/reports',
        component: () => import('@/views/property/MomentReportView.vue'),
        meta: { title: '举报管理', portal: 'property' },
      },
      {
        path: 'property/neighbor-help',
        component: () => import('@/views/property/NeighborHelpManageView.vue'),
        meta: { title: '邻里互助管理', portal: 'property' },
      },
      {
        path: 'property/appeal',
        redirect: '/property/moment/posts',
      },
      ...listChildren,
    ],
  },
  { path: '/:pathMatch(.*)*', redirect: '/login' },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

router.beforeEach((to) => {
  const user = useUserStore()
  if (to.meta.public) return true
  if (!user.isLogin) return { path: '/login', query: { redirect: to.fullPath } }

  const portal = to.meta.portal as string | undefined
  if (portal && portal !== user.portal) {
    if (user.portal === 'admin') return '/admin/saas/cockpit'
    if (user.portal === 'merchant') return '/merchant/home'
    return '/property/dashboard'
  }
  // 权限加载中不误判
  if (user.featureLoading) return true
  const feature = (to.meta.feature || to.meta.featureCode) as string | undefined
  if (
    feature &&
    portal === 'property' &&
    to.path !== '/property/feature-unavailable' &&
    !user.can(feature)
  ) {
    return {
      path: '/property/feature-unavailable',
      query: { feature, from: to.fullPath },
    }
  }
  return true
})

export default router
