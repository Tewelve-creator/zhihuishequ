import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import {
  DEMO_COMMUNITY_PACKAGES,
  FEATURE_LABELS,
  hasFeature,
  minPackageNameForFeature,
  parseFeatures,
  type FeatureState,
} from '@/config/features'

/** admin=运营管理端 property=物业端 merchant=商超商户端 */
export type PortalType = 'admin' | 'property' | 'merchant'

export const useUserStore = defineStore('user', () => {
  const token = ref(localStorage.getItem('admin_token') || '')
  const username = ref(localStorage.getItem('admin_username') || '')
  const phone = ref(localStorage.getItem('admin_phone') || '')
  const portal = ref<PortalType>(
    (localStorage.getItem('admin_portal') as PortalType) || 'admin',
  )
  const communityName = ref(localStorage.getItem('admin_community') || '阳光花园社区')
  const features = ref(localStorage.getItem('admin_features') || '')
  const tenantStatus = ref(localStorage.getItem('admin_tenant_status') || 'active')
  const role = ref(localStorage.getItem('admin_role') || '')
  const tenantId = ref(localStorage.getItem('admin_tenant_id') || '')
  const userId = ref(localStorage.getItem('admin_user_id') || '')
  const shopId = ref(localStorage.getItem('admin_shop_id') || '')
  const packageCode = ref(localStorage.getItem('admin_package_code') || '')
  const packageName = ref(localStorage.getItem('admin_package_name') || '')
  /** 权限/社区切换加载中，避免短暂误判 */
  const featureLoading = ref(false)
  const featureLimitVisible = ref(false)
  const featureLimitCode = ref('')

  const isLogin = computed(() => !!token.value)
  const isPlatform = computed(() => role.value === 'platform_admin' || portal.value === 'admin')
  const featureList = computed(() => parseFeatures(features.value))
  const hasRealToken = computed(() => !!token.value && !token.value.startsWith('token-'))

  function persist() {
    localStorage.setItem('admin_token', token.value)
    localStorage.setItem('admin_username', username.value)
    localStorage.setItem('admin_phone', phone.value)
    localStorage.setItem('admin_portal', portal.value)
    localStorage.setItem('admin_features', features.value)
    localStorage.setItem('admin_tenant_status', tenantStatus.value)
    localStorage.setItem('admin_role', role.value)
    localStorage.setItem('admin_tenant_id', tenantId.value)
    localStorage.setItem('admin_user_id', userId.value)
    localStorage.setItem('admin_shop_id', shopId.value)
    localStorage.setItem('admin_community', communityName.value)
    localStorage.setItem('admin_package_code', packageCode.value)
    localStorage.setItem('admin_package_name', packageName.value)
  }

  /** 演示登录：按社区名强制绑定套餐（覆盖旧社区权限，防泄漏） */
  function applyPackageFromCommunity(name?: string, force = false) {
    if (!name) return
    const demo = DEMO_COMMUNITY_PACKAGES[name]
    if (!demo) return
    if (force || !packageCode.value) packageCode.value = demo.packageCode
    if (force || !packageName.value) packageName.value = demo.packageName
    if (force || !features.value) features.value = demo.features
  }

  function login(payload: {
    token: string
    username: string
    portal: PortalType
    phone?: string
    features?: string | string[]
    tenantStatus?: string
    role?: string
    tenantId?: string | number | null
    userId?: string | number | null
    shopId?: string | number | null
    communityName?: string
    packageCode?: string
    packageName?: string
    effectiveFeatureCodes?: string[]
  }) {
    token.value = payload.token
    username.value = payload.username
    phone.value = payload.phone || ''
    portal.value = payload.portal
    const eff = payload.effectiveFeatureCodes?.length
      ? payload.effectiveFeatureCodes.join(',')
      : Array.isArray(payload.features)
        ? payload.features.join(',')
        : payload.features || ''
    features.value = eff
    tenantStatus.value = payload.tenantStatus || 'active'
    role.value =
      payload.role ||
      (payload.portal === 'admin'
        ? 'platform_admin'
        : payload.portal === 'merchant'
          ? 'store_merchant'
          : 'property_admin')
    tenantId.value =
      payload.tenantId !== undefined && payload.tenantId !== null ? String(payload.tenantId) : ''
    userId.value =
      payload.userId !== undefined && payload.userId !== null ? String(payload.userId) : ''
    shopId.value =
      payload.shopId !== undefined && payload.shopId !== null ? String(payload.shopId) : ''
    if (payload.communityName) communityName.value = payload.communityName
    packageCode.value = payload.packageCode || ''
    packageName.value = payload.packageName || ''
    if (payload.portal === 'property') {
      applyPackageFromCommunity(communityName.value, !packageCode.value || !features.value)
    }
    featureLoading.value = false
    persist()
  }

  /** 切换社区后用接口回包刷新租户上下文（按 tenantId 隔离，覆盖全部权限字段） */
  function applyAuthContext(payload: {
    token?: string
    features?: string | string[]
    effectiveFeatureCodes?: string[]
    tenantStatus?: string
    tenantId?: string | number | null
    userId?: string | number | null
    shopId?: string | number | null
    communityName?: string
    tenantName?: string
    packageCode?: string
    packageName?: string
    role?: string
    phone?: string
  }) {
    if (payload.token) token.value = payload.token
    const eff = payload.effectiveFeatureCodes?.length
      ? payload.effectiveFeatureCodes.join(',')
      : Array.isArray(payload.features)
        ? payload.features.join(',')
        : payload.features != null
          ? String(payload.features)
          : null
    if (eff != null) features.value = eff
    if (payload.tenantStatus) tenantStatus.value = payload.tenantStatus
    if (payload.tenantId !== undefined && payload.tenantId !== null) {
      tenantId.value = String(payload.tenantId)
    }
    if (payload.userId !== undefined && payload.userId !== null) {
      userId.value = String(payload.userId)
    }
    if (payload.shopId !== undefined && payload.shopId !== null) {
      shopId.value = String(payload.shopId)
    }
    if (payload.communityName || payload.tenantName) {
      communityName.value = payload.communityName || payload.tenantName || communityName.value
    }
    if (payload.packageCode != null) packageCode.value = payload.packageCode
    if (payload.packageName != null) packageName.value = payload.packageName
    if (payload.role) role.value = payload.role
    if (payload.phone != null) phone.value = payload.phone
    persist()
  }

  function setPortal(next: PortalType) {
    portal.value = next
    localStorage.setItem('admin_portal', next)
  }

  function updateLease(nextFeatures?: string, nextStatus?: string) {
    if (nextFeatures != null) {
      features.value = nextFeatures
      localStorage.setItem('admin_features', nextFeatures)
    }
    if (nextStatus != null) {
      tenantStatus.value = nextStatus
      localStorage.setItem('admin_tenant_status', nextStatus)
    }
  }

  function setFeatureLoading(v: boolean) {
    featureLoading.value = v
  }

  function getFeatureState(code: string): FeatureState {
    if (!code) return 'available'
    if (featureLoading.value) return 'loading'
    if (isPlatform.value && portal.value === 'admin') return 'available'
    if (tenantStatus.value === 'frozen') return 'disabled'
    if (tenantStatus.value === 'degraded') {
      return code === 'basic_repair' || code === 'basic_notice' ? 'available' : 'expired'
    }
    return hasFeature(features.value, code) ? 'available' : 'unavailable'
  }

  function can(code: string) {
    return getFeatureState(code) === 'available'
  }

  function requireFeature(code: string): boolean {
    return can(code)
  }

  function openFeatureLimit(code: string) {
    featureLimitCode.value = code || ''
    featureLimitVisible.value = true
  }

  function closeFeatureLimit() {
    featureLimitVisible.value = false
    featureLimitCode.value = ''
  }

  function featureHint(code: string): string {
    const state = getFeatureState(code)
    const label = FEATURE_LABELS[code] || code
    const pkg = packageName.value || '当前套餐'
    if (state === 'loading') return '权限加载中…'
    if (state === 'expired') return `套餐已到期降级，暂不可用「${label}」`
    if (state === 'disabled') return '租户已冻结，请联系平台管理员'
    if (state === 'unavailable') {
      const need = minPackageNameForFeature(code)
      return need
        ? `当前社区使用${pkg}，该功能暂未开通（通常需要${need}及以上）`
        : `当前社区使用${pkg}，该功能暂未开通。`
    }
    return ''
  }

  function logout() {
    token.value = ''
    username.value = ''
    phone.value = ''
    features.value = ''
    tenantStatus.value = 'active'
    role.value = ''
    tenantId.value = ''
    userId.value = ''
    shopId.value = ''
    packageCode.value = ''
    packageName.value = ''
    featureLoading.value = false
    communityName.value = ''
    ;[
      'admin_token',
      'admin_username',
      'admin_phone',
      'admin_portal',
      'admin_features',
      'admin_tenant_status',
      'admin_role',
      'admin_tenant_id',
      'admin_user_id',
      'admin_shop_id',
      'admin_package_code',
      'admin_package_name',
      'admin_community',
    ].forEach((k) => localStorage.removeItem(k))
  }

  return {
    token,
    username,
    phone,
    portal,
    communityName,
    features,
    tenantStatus,
    role,
    tenantId,
    userId,
    shopId,
    packageCode,
    packageName,
    featureLoading,
    featureLimitVisible,
    featureLimitCode,
    featureList,
    isLogin,
    isPlatform,
    hasRealToken,
    login,
    applyAuthContext,
    applyPackageFromCommunity,
    setPortal,
    updateLease,
    setFeatureLoading,
    getFeatureState,
    can,
    requireFeature,
    openFeatureLimit,
    closeFeatureLimit,
    featureHint,
    logout,
  }
})
