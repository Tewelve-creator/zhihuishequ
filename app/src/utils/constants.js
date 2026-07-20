/** 套餐功能码（与后端 FeatureCodes 对齐） */
export const FEATURES = {
  BASIC_REPAIR: 'basic_repair',
  BASIC_NOTICE: 'basic_notice',
  AI_CHAT: 'ai_chat',
  AI_IMAGE_REPAIR: 'ai_image_repair',
  AI_EMOTION: 'ai_emotion',
  AI_LOST_MATCH: 'ai_lost_match',
  DATA_DASHBOARD: 'data_dashboard',
  DATA_EXPORT: 'data_export',
  VENDOR_MANAGE: 'vendor_manage',
  MULTI_COMMUNITY: 'multi_community'
}

export const FEATURE_LABELS = {
  basic_repair: '基础报修',
  basic_notice: '社区公告',
  ai_chat: 'AI 智能问答',
  ai_image_repair: 'AI 识图报修',
  ai_emotion: '情绪调解',
  ai_lost_match: '失物 AI 匹配',
  data_dashboard: '数据大屏',
  data_export: '数据导出',
  vendor_manage: '服务商管理',
  multi_community: '多社区管理'
}

/** 演示社区列表（注册/登录选择，与 schema 一致） */
export const DEMO_TENANTS = [
  { id: 1, name: '阳光花园社区', address: '示例市示例区阳光路1号' },
  { id: 2, name: '星河湾社区', address: '示例市示例区星河路8号' }
]

export const ROLE_LABELS = {
  resident: '居民',
  staff: '社区工作人员',
  property_admin: '物业管理员',
  grid_worker: '网格员',
  platform_admin: '平台运营'
}

export function parseFeatures(features) {
  if (!features) return []
  if (Array.isArray(features)) return features
  return String(features)
    .split(',')
    .map((s) => s.trim())
    .filter(Boolean)
}

export function hasFeature(features, code) {
  return parseFeatures(features).includes(code)
}

export function isStaffRole(role) {
  return ['staff', 'property_admin', 'grid_worker'].includes(role)
}

export function statusText(status) {
  const map = {
    pending: '待处理',
    processing: '处理中',
    done: '已完成',
    closed: '已关闭',
    approved: '已通过',
    rejected: '已拒绝',
    checked: '已核销',
    paid: '已支付',
    unpaid: '待支付'
  }
  return map[status] || status || '-'
}

export function tenantStatusText(status) {
  const map = {
    active: '正常',
    degraded: '已降级',
    frozen: '已冻结',
    disabled: '已停用'
  }
  return map[status] || status || '未知'
}
