import request from '@/utils/request'

export interface LoginBody {
  phone: string
  password: string
  tenantId?: number
  clientType?: string
  loginType?: string
}

export function loginApi(body: LoginBody) {
  return request.post('/auth/login', {
    loginType: 'password',
    clientType: 'ADMIN',
    ...body,
  }) as Promise<{
    token: string
    userId: number
    tenantId: number
    shopId?: number
    role: string
    nickname?: string
    phone?: string
    features?: string | string[]
    effectiveFeatureCodes?: string[]
    tenantStatus?: string
    packageCode?: string
    packageName?: string
    tenantName?: string
  }>
}

/** 同账号切换社区，刷新 Token / 套餐 / 有效功能码 */
export function switchCommunityApi(tenantId: number) {
  return request.post('/auth/switch-community', { tenantId }) as Promise<{
    token: string
    userId: number
    tenantId: number
    shopId?: number
    role: string
    nickname?: string
    phone?: string
    features?: string | string[]
    effectiveFeatureCodes?: string[]
    tenantStatus?: string
    packageCode?: string
    packageName?: string
    tenantName?: string
  }>
}

/** 当前登录账号可进入的社区列表（含套餐信息） */
export function listMyCommunitiesApi() {
  return request.get('/auth/tenants/mine/communities', {
    params: { portal: 'property' },
  }) as Promise<
    Array<{
      id: number
      name: string
      address?: string
      status?: string
      role?: string
      packageCode?: string
      packageName?: string
    }>
  >
}

/** 登录页社区列表（免登录，开通租户后自动出现） */
export function listPublicTenants() {
  return request.get('/auth/tenants/public') as Promise<
    Array<{ id: number; name: string; address?: string; status?: string }>
  >
}

/** 登录页按账号自动识别所属社区（免登录） */
export function resolveCommunitiesByPhone(phone: string, portal: 'property' | 'merchant' = 'property') {
  return request.get('/auth/tenants/resolve-by-phone', {
    params: { phone, portal },
  }) as Promise<
    Array<{
      id: number
      name: string
      address?: string
      status?: string
      role?: string
      packageCode?: string
      packageName?: string
    }>
  >
}

/** 物业：当前社区套餐与租约（只读，对接运营端租户/套餐数据） */
export function getMyLease() {
  return request.get('/auth/tenants/lease/mine') as Promise<{
    tenantId: number
    tenantName?: string
    status?: string
    packageId?: number
    packageName?: string
    packageCode?: string
    packageSortNo?: number
    packageFeatures?: string[]
    features?: string[]
    expireTime?: string
    daysLeft?: number
    expireWarning?: boolean
    aiQuota?: number
    todayAiCalls?: number
  }>
}

/** 公开套餐目录（与运营端套餐管理同源） */
export function listPublicPackages() {
  return request.get('/auth/packages/public') as Promise<
    Array<{
      id: number
      name: string
      code?: string
      priceMonth?: number
      priceQuarter?: number
      priceYear?: number
      aiQuota?: number
      remark?: string
      featureCodes?: string[]
      sortNo?: number
      status?: number
    }>
  >
}

/** 修改当前登录用户密码（商户端账号安全） */
export function changePasswordApi(oldPassword: string, newPassword: string) {
  return request.put('/auth/password', { oldPassword, newPassword })
}

/** 站内消息 */
export function listMessagesApi() {
  return request.get('/auth/messages') as Promise<any[]>
}
export function getUnreadMessageCountApi() {
  return request.get('/auth/messages/unread-count') as Promise<{ count: number }>
}
export function readMessageApi(id: number) {
  return request.put(`/auth/messages/${id}/read`)
}
export function readAllMessagesApi() {
  return request.put('/auth/messages/read-all') as Promise<{ count: number }>
}
