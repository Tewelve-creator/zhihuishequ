/**
 * 简易用户会话（兼容 HBuilderX / CLI）
 * 登录后按 role 区分居民端 / 物业端（管理员 · 网格员）
 */
import { reactive } from 'vue'
import { isStaffRole } from '../utils/constants'
import {
  canAccess,
  normalizeRole,
  isPropertyAdminRole,
  isGridWorkerRole,
  roleLabel,
  homePathAfterLogin,
  PERMS
} from '../utils/roleAccess'

export const userStore = reactive({
  token: '',
  userId: null,
  tenantId: null,
  role: '',
  nickname: '',
  features: '',
  tenantStatus: 'active',
  largeFont: false,

  get isLogin() {
    return !!this.token
  },
  get isStaff() {
    return isStaffRole(this.role)
  },
  get isResident() {
    return !this.isStaff && (this.role === 'resident' || !this.role)
  },
  get isPropertyAdmin() {
    return isPropertyAdminRole(this.role)
  },
  get isGridWorker() {
    return isGridWorkerRole(this.role)
  },
  get roleName() {
    return roleLabel(this.role)
  },
  get normalizedRole() {
    return normalizeRole(this.role)
  },
  get isDegraded() {
    return this.tenantStatus === 'degraded' || this.tenantStatus === 'expired'
  },
  get isFrozen() {
    return this.tenantStatus === 'frozen'
  },

  /** 角色权限（PERMS.*） */
  canPerm(perm) {
    return canAccess(this.role, perm)
  },

  /**
   * 套餐功能码（收费暂未启用：一律 true）
   * 角色权限请用 canPerm(PERMS.xxx)
   */
  can(_code) {
    return true
  },

  hydrate() {
    this.token = uni.getStorageSync('token') || ''
    this.userId = uni.getStorageSync('userId') || null
    const loginTid = uni.getStorageSync('loginTenantId')
    const tid = loginTid !== '' && loginTid != null ? loginTid : uni.getStorageSync('tenantId')
    this.tenantId = tid
    if (tid != null && tid !== '') {
      uni.setStorageSync('tenantId', tid)
      uni.setStorageSync('loginTenantId', tid)
    }
    this.role = uni.getStorageSync('role') || ''
    this.nickname = uni.getStorageSync('nickname') || ''
    this.features = uni.getStorageSync('features') || ''
    this.tenantStatus = uni.getStorageSync('tenantStatus') || 'active'
    this.largeFont = !!uni.getStorageSync('largeFont')
  },

  setSession(data) {
    this.token = data.token || ''
    this.userId = data.userId
    this.tenantId = data.tenantId
    this.role = data.role || ''
    this.nickname = data.nickname || ''
    this.features = data.features || ''
    this.tenantStatus = data.tenantStatus || 'active'
    uni.setStorageSync('token', this.token)
    uni.setStorageSync('userId', this.userId)
    uni.setStorageSync('tenantId', this.tenantId)
    // 登录租户快照：请求头以此为准，避免首页切换社区导致跨社区报错
    uni.setStorageSync('loginTenantId', this.tenantId)
    uni.setStorageSync('role', this.role)
    uni.setStorageSync('nickname', this.nickname)
    uni.setStorageSync('features', this.features)
    uni.setStorageSync('tenantStatus', this.tenantStatus)
  },

  /** 登录成功后的落地页（两端同一首页，内容按角色渲染） */
  enterHome() {
    uni.switchTab({ url: homePathAfterLogin(this.role) })
  },

  updateFeatures(features, tenantStatus) {
    if (features != null) {
      this.features = features
      uni.setStorageSync('features', features)
    }
    if (tenantStatus != null) {
      this.tenantStatus = tenantStatus
      uni.setStorageSync('tenantStatus', tenantStatus)
    }
  },

  toggleLargeFont() {
    this.largeFont = !this.largeFont
    uni.setStorageSync('largeFont', this.largeFont)
  },

  logout() {
    this.token = ''
    this.userId = null
    this.tenantId = null
    this.role = ''
    this.nickname = ''
    this.features = ''
    this.tenantStatus = 'active'
    ;[
      'token',
      'userId',
      'tenantId',
      'loginTenantId',
      'role',
      'nickname',
      'features',
      'tenantStatus'
    ].forEach((k) => {
      uni.removeStorageSync(k)
    })
    uni.reLaunch({ url: '/pages/login/login' })
  },

  guardFeature(_code, _tip) {
    return true
  },

  /** 无角色权限时 toast，返回是否放行 */
  guardPerm(perm, tip) {
    if (this.canPerm(perm)) return true
    uni.showToast({
      title: tip || '当前岗位无此权限',
      icon: 'none'
    })
    return false
  }
})

export function useUserStore() {
  return userStore
}

export { PERMS }
