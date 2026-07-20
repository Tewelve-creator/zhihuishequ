import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { hasFeature, parseFeatures } from '@/config/features'

/** admin=运营管理端 property=物业端 */
export type PortalType = 'admin' | 'property'

export const useUserStore = defineStore('user', () => {
  const token = ref(localStorage.getItem('admin_token') || '')
  const username = ref(localStorage.getItem('admin_username') || '')
  const portal = ref<PortalType>(
    (localStorage.getItem('admin_portal') as PortalType) || 'admin',
  )
  const communityName = ref(localStorage.getItem('admin_community') || '阳光花园小区')
  const features = ref(localStorage.getItem('admin_features') || '')
  const tenantStatus = ref(localStorage.getItem('admin_tenant_status') || 'active')
  const role = ref(localStorage.getItem('admin_role') || '')
  const tenantId = ref(localStorage.getItem('admin_tenant_id') || '')
  const userId = ref(localStorage.getItem('admin_user_id') || '')

  const isLogin = computed(() => !!token.value)
  const isPlatform = computed(() => role.value === 'platform_admin' || portal.value === 'admin')
  const featureList = computed(() => parseFeatures(features.value))
  /** 真实网关 Token（演示 token- 开头不可调业务接口） */
  const hasRealToken = computed(() => !!token.value && !token.value.startsWith('token-'))

  function login(payload: {
    token: string
    username: string
    portal: PortalType
    features?: string
    tenantStatus?: string
    role?: string
    tenantId?: string | number | null
    userId?: string | number | null
    communityName?: string
  }) {
    token.value = payload.token
    username.value = payload.username
    portal.value = payload.portal
    features.value = payload.features || ''
    tenantStatus.value = payload.tenantStatus || 'active'
    role.value = payload.role || (payload.portal === 'admin' ? 'platform_admin' : 'property_admin')
    tenantId.value =
      payload.tenantId !== undefined && payload.tenantId !== null ? String(payload.tenantId) : ''
    userId.value =
      payload.userId !== undefined && payload.userId !== null ? String(payload.userId) : ''
    if (payload.communityName) {
      communityName.value = payload.communityName
    }
    localStorage.setItem('admin_token', token.value)
    localStorage.setItem('admin_username', username.value)
    localStorage.setItem('admin_portal', portal.value)
    localStorage.setItem('admin_features', features.value)
    localStorage.setItem('admin_tenant_status', tenantStatus.value)
    localStorage.setItem('admin_role', role.value)
    localStorage.setItem('admin_tenant_id', tenantId.value)
    localStorage.setItem('admin_user_id', userId.value)
    localStorage.setItem('admin_community', communityName.value)
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

  function can(code: string) {
    if (isPlatform.value && portal.value === 'admin') return true
    if (tenantStatus.value === 'frozen' || tenantStatus.value === 'degraded') {
      return code === 'basic_repair' || code === 'basic_notice'
    }
    return hasFeature(features.value, code)
  }

  function logout() {
    token.value = ''
    username.value = ''
    features.value = ''
    tenantStatus.value = 'active'
    role.value = ''
    tenantId.value = ''
    userId.value = ''
    ;[
      'admin_token',
      'admin_username',
      'admin_portal',
      'admin_features',
      'admin_tenant_status',
      'admin_role',
      'admin_tenant_id',
      'admin_user_id',
    ].forEach((k) => localStorage.removeItem(k))
  }

  return {
    token,
    username,
    portal,
    communityName,
    features,
    tenantStatus,
    role,
    tenantId,
    userId,
    featureList,
    isLogin,
    isPlatform,
    hasRealToken,
    login,
    setPortal,
    updateLease,
    can,
    logout,
  }
})
