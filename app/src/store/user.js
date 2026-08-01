/**
 * 简易用户会话（兼容 HBuilderX / CLI）
 * 登录后按 role 区分居民端 / 物业端（管理员 · 网格员）
 */
import { reactive } from 'vue'
import { hasFeature, isStaffRole } from '../utils/constants'
import {
  canAccess,
  normalizeRole,
  isPropertyAdminRole,
  isGridWorkerRole,
  roleLabel,
  homePathAfterLogin,
  PERMS
} from '../utils/roleAccess'
import { houseStore } from './house'

export const userStore = reactive({
  token: '',
  userId: null,
  tenantId: null,
  role: '',
  nickname: '',
  avatar: '',
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
   * 套餐功能码（与登录 features / 租户套餐对齐）
   * 角色权限请用 canPerm(PERMS.xxx)
   */
  can(code) {
    if (!code) return true
    if (this.isFrozen) return false
    if (this.isDegraded) {
      return code === 'basic_repair' || code === 'basic_notice'
    }
    // 未写入 features 时仅保底基础能力，避免误开高级功能
    if (!this.features) {
      return code === 'basic_repair' || code === 'basic_notice'
    }
    return hasFeature(this.features, code)
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
    this.avatar = uni.getStorageSync('avatar') || ''
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
    this.avatar = data.avatar || ''
    this.features = data.features || ''
    this.tenantStatus = data.tenantStatus || 'active'
    uni.setStorageSync('token', this.token)
    uni.setStorageSync('userId', this.userId)
    uni.setStorageSync('tenantId', this.tenantId)
    // 登录租户快照：请求头以此为准，避免首页切换社区导致跨社区报错
    uni.setStorageSync('loginTenantId', this.tenantId)
    uni.setStorageSync('role', this.role)
    uni.setStorageSync('nickname', this.nickname)
    uni.setStorageSync('avatar', this.avatar)
    uni.setStorageSync('features', this.features)
    uni.setStorageSync('tenantStatus', this.tenantStatus)
  },

  /** 更新本地昵称/头像（改资料后调用） */
  updateProfileLocal({ nickname, avatar } = {}) {
    if (nickname != null) {
      this.nickname = nickname
      uni.setStorageSync('nickname', nickname)
    }
    if (avatar !== undefined) {
      this.avatar = avatar || ''
      uni.setStorageSync('avatar', this.avatar)
    }
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
    this.avatar = ''
    this.features = ''
    this.tenantStatus = 'active'
    houseStore.clear()
    ;[
      'token',
      'userId',
      'tenantId',
      'loginTenantId',
      'role',
      'nickname',
      'avatar',
      'features',
      'tenantStatus',
      'currentHouseId',
      'currentHouse',
      'boundHouses'
    ].forEach((k) => {
      uni.removeStorageSync(k)
    })
    uni.reLaunch({ url: '/pages/login/login' })
  },

  /** 无套餐能力时 toast「暂未开放」，返回是否放行 */
  guardFeature(code, tip) {
    if (this.can(code)) return true
    uni.showToast({
      title: tip || '暂未开放',
      icon: 'none'
    })
    return false
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
