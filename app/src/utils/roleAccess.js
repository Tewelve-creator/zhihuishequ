/**
 * App 双端角色权限（手机端）
 * - resident：居民
 * - property_admin / staff：物业管理员
 * - grid_worker：网格员
 */
import { isStaffRole, ROLE_LABELS } from './constants'

export const ROLES = {
  RESIDENT: 'resident',
  STAFF: 'staff',
  PROPERTY_ADMIN: 'property_admin',
  GRID_WORKER: 'grid_worker'
}

export const PERMS = {
  AI_CHAT: 'ai_chat',
  REPAIR_SUBMIT: 'repair_submit',
  PAYMENT: 'payment',
  VISITOR_BOOK: 'visitor_book',
  ACTIVITY_SIGNUP: 'activity_signup',

  REPAIR_MANAGE: 'repair_manage',
  VISITOR_VERIFY: 'visitor_verify',
  APPEAL_MEDIATE: 'appeal_mediate',
  KB_MANAGE: 'kb_manage',
  REPAIR_AI_LOG: 'repair_ai_log',
  NOTICE_AI_TOOL: 'notice_ai_tool',
  CHAT_STATS: 'chat_stats',

  NOTICE_PUBLISH: 'notice_publish',
  NOTICE_MANAGE: 'notice_manage',
  ACTIVITY_MANAGE: 'activity_manage',
  LOST_AUDIT: 'lost_audit',
  DATA_DASHBOARD: 'data_dashboard',
  PERFORMANCE: 'performance',
  PACKAGE_VIEW: 'package_view',
  WORKBENCH_FULL: 'workbench_full'
}

/** 物业管理员：首页 8 入口全开 */
const ADMIN_PERMS = [
  PERMS.REPAIR_MANAGE,
  PERMS.VISITOR_VERIFY,
  PERMS.APPEAL_MEDIATE,
  PERMS.KB_MANAGE,
  PERMS.REPAIR_AI_LOG,
  PERMS.NOTICE_AI_TOOL,
  PERMS.CHAT_STATS,
  PERMS.NOTICE_PUBLISH,
  PERMS.NOTICE_MANAGE,
  PERMS.ACTIVITY_MANAGE,
  PERMS.PAYMENT,
  PERMS.LOST_AUDIT,
  PERMS.DATA_DASHBOARD,
  PERMS.PERFORMANCE,
  PERMS.PACKAGE_VIEW,
  PERMS.WORKBENCH_FULL
]

/** 网格员：知识库、报修、访客、邻里倾诉（+管家运维工具） */
const GRID_PERMS = [
  PERMS.KB_MANAGE,
  PERMS.REPAIR_MANAGE,
  PERMS.VISITOR_VERIFY,
  PERMS.APPEAL_MEDIATE,
  PERMS.REPAIR_AI_LOG,
  PERMS.NOTICE_AI_TOOL,
  PERMS.CHAT_STATS
]

const RESIDENT_PERMS = [
  PERMS.AI_CHAT,
  PERMS.REPAIR_SUBMIT,
  PERMS.PAYMENT,
  PERMS.VISITOR_BOOK,
  PERMS.ACTIVITY_SIGNUP
]

export function normalizeRole(role) {
  if (!role) return ''
  if (role === ROLES.STAFF) return ROLES.PROPERTY_ADMIN
  return role
}

export function isPropertyAdminRole(role) {
  return normalizeRole(role) === ROLES.PROPERTY_ADMIN
}

export function isGridWorkerRole(role) {
  return normalizeRole(role) === ROLES.GRID_WORKER
}

export function roleLabel(role) {
  return ROLE_LABELS[role] || ROLE_LABELS[normalizeRole(role)] || role || '未登录'
}

export function permsOf(role) {
  const r = normalizeRole(role)
  if (r === ROLES.PROPERTY_ADMIN) return ADMIN_PERMS
  if (r === ROLES.GRID_WORKER) return GRID_PERMS
  if (r === ROLES.RESIDENT) return RESIDENT_PERMS
  if (isStaffRole(role)) return ADMIN_PERMS
  return RESIDENT_PERMS
}

export function canAccess(role, perm) {
  return permsOf(role).includes(perm)
}

export function homePathAfterLogin() {
  return '/pages/home/index'
}

/** 物业端智能管家子模块（无聊天） */
export function staffAiTabs(role) {
  const tabs = []
  if (canAccess(role, PERMS.KB_MANAGE)) tabs.push({ key: 'kb', name: '知识库' })
  if (canAccess(role, PERMS.REPAIR_AI_LOG)) tabs.push({ key: 'image_log', name: '识图记录' })
  if (canAccess(role, PERMS.NOTICE_AI_TOOL)) tabs.push({ key: 'notice_ai', name: '公告AI' })
  if (canAccess(role, PERMS.CHAT_STATS)) tabs.push({ key: 'stats', name: '咨询统计' })
  return tabs
}

/** 首页 8 快捷入口定义（按角色过滤） */
export function staffHomeShortcuts(role) {
  const all = [
    { name: 'AI知识库', letter: '库', perm: PERMS.KB_MANAGE, path: '/pages/ai/chat', tab: true, bg: 'linear-gradient(145deg,#6ed3a8,#47b686)' },
    { name: '报修工单', letter: '修', perm: PERMS.REPAIR_MANAGE, path: '/pages/repair/list', bg: 'linear-gradient(145deg,#7eb6f7,#4f8fe8)' },
    { name: '访客审核', letter: '访', perm: PERMS.VISITOR_VERIFY, path: '/pages/visitor/list', bg: 'linear-gradient(145deg,#6ec8f0,#3aaedc)' },
    { name: '活动管理', letter: '活', perm: PERMS.ACTIVITY_MANAGE, path: '/pages/activity/list', bg: 'linear-gradient(145deg,#ffc85a,#f5a623)' },
    { name: '社区公告', letter: '告', perm: PERMS.NOTICE_MANAGE, path: '/pages/notice/list', bg: 'linear-gradient(145deg,#a78bfa,#7c5cbf)' },
    { name: '物业费账单', letter: '费', perm: PERMS.PAYMENT, path: '/pages/payment/index', bg: 'linear-gradient(145deg,#f5a0a0,#e86b6b)' },
    { name: '失物招领', letter: '失', perm: PERMS.LOST_AUDIT, path: '/pages/lost/index', bg: 'linear-gradient(145deg,#7ed9b0,#3aa075)' },
    { name: '邻里倾诉', letter: '诉', perm: PERMS.APPEAL_MEDIATE, path: '/pages/appeal/index', bg: 'linear-gradient(145deg,#f0b27a,#d68910)' }
  ]
  return all.filter((i) => canAccess(role, i.perm))
}
