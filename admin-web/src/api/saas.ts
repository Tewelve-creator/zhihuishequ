import request from '@/utils/request'

/** SaaS 运营管理 API（对接 community-auth / community-stats / community-ai） */

export interface PageResult<T> {
  total: number
  page?: number
  size?: number
  records: T[]
}

/* ---------- 租户 ---------- */
export function pageTenants(params: { page?: number; size?: number; keyword?: string }) {
  return request.get('/auth/tenants', { params }) as Promise<PageResult<any>>
}
export function getTenantStats() {
  return request.get('/auth/tenants/stats') as Promise<any>
}
export function listExpiringTenants(withinDays = 30) {
  return request.get('/auth/tenants/expiring', { params: { withinDays } }) as Promise<any[]>
}
export function getTenant(id: number) {
  return request.get(`/auth/tenants/${id}`) as Promise<any>
}
export function createTenant(data: any) {
  return request.post('/auth/tenants', data) as Promise<any>
}
export function updateTenant(data: any) {
  return request.put('/auth/tenants', data) as Promise<any>
}
export function updateTenantStatus(id: number, status: string) {
  return request.put(`/auth/tenants/${id}/status`, null, { params: { status } }) as Promise<void>
}
export function deleteTenant(id: number) {
  return request.delete(`/auth/tenants/${id}`) as Promise<void>
}
export function runExpireScan() {
  return request.post('/auth/tenants/expire-scan') as Promise<any>
}
export function updateTenantAiQuota(id: number, aiQuota: number) {
  return request.put(`/auth/tenants/${id}/ai-quota`, { aiQuota }) as Promise<any>
}
export function getTenantAdminAccount(id: number) {
  return request.get(`/auth/tenants/${id}/admin-account`) as Promise<any>
}
export function upsertTenantAdminAccount(
  id: number,
  data: { adminPhone: string; adminPassword?: string; adminNickname?: string },
) {
  return request.put(`/auth/tenants/${id}/admin-account`, data) as Promise<any>
}

/* ---------- 套餐 ---------- */
export function listPackages() {
  return request.get('/auth/packages') as Promise<any[]>
}
export function getFeatureDict() {
  return request.get('/auth/packages/features') as Promise<Record<string, string>>
}
export function createPackage(data: any) {
  return request.post('/auth/packages', data) as Promise<any>
}
export function updatePackage(data: any) {
  return request.put('/auth/packages', data) as Promise<any>
}
export function deletePackage(id: number) {
  return request.delete(`/auth/packages/${id}`) as Promise<void>
}

/* ---------- 订单 ---------- */
export function pageOrders(params: {
  page?: number
  size?: number
  tenantId?: number
  status?: string
  orderType?: string
}) {
  return request.get('/auth/orders', { params }) as Promise<PageResult<any>>
}
export function createOrder(data: any) {
  return request.post('/auth/orders', data) as Promise<any>
}
/** payMethod: wechat | alipay | bank | mock */
export function payOrder(id: number, payMethod: string = 'mock') {
  return request.post(`/auth/orders/${id}/pay`, { payMethod }) as Promise<any>
}
export function cancelOrder(id: number) {
  return request.post(`/auth/orders/${id}/cancel`) as Promise<void>
}
export function refundOrder(id: number, reason?: string) {
  return request.post(`/auth/orders/${id}/refund`, { reason }) as Promise<any>
}

/* ---------- 账号 / 角色 / 菜单 / 日志 ---------- */
export function pageUsers(params: {
  page?: number
  size?: number
  role?: string
  staffOnly?: boolean
}) {
  return request.get('/auth/users', { params }) as Promise<PageResult<any>>
}
export function createUser(data: any) {
  return request.post('/auth/users', data) as Promise<any>
}
export function updateUserStatus(id: number, status: number) {
  return request.put(`/auth/users/${id}/status`, null, { params: { status } }) as Promise<void>
}
export function resetUserPassword(id: number) {
  return request.put(`/auth/users/${id}/password/reset`) as Promise<void>
}
export function listRoles() {
  return request.get('/auth/roles') as Promise<any[]>
}
export function listRoleMenus(roleId: number) {
  return request.get(`/auth/roles/${roleId}/menus`) as Promise<number[]>
}
export function saveRoleMenus(roleId: number, menuIds: number[]) {
  return request.put(`/auth/roles/${roleId}/menus`, { menuIds }) as Promise<void>
}
export function listMenuTree(clientType = 'ADMIN') {
  return request.get('/auth/menus/tree', { params: { clientType } }) as Promise<any[]>
}
export function listAllMenus(clientType = 'ADMIN') {
  return request.get('/auth/menus/all', { params: { clientType } }) as Promise<any[]>
}
export function listPermissions() {
  return request.get('/auth/permissions') as Promise<string[]>
}
export function pageLoginLogs(params: { page?: number; size?: number }) {
  return request.get('/auth/login-logs', { params }) as Promise<PageResult<any>>
}
export function listSessions() {
  return request.get('/auth/sessions') as Promise<any[]>
}
export function kickSession(token: string) {
  return request.delete(`/auth/sessions/${encodeURIComponent(token)}`) as Promise<void>
}

/* ---------- 运营驾驶舱 / 监控 / 配置 / 操作日志 / AI额度 ---------- */
export function saasDashboard() {
  return request.get('/stats/saas/dashboard') as Promise<any>
}
export function listAiQuotaOverview() {
  return request.get('/auth/ops/ai-quota') as Promise<any[]>
}
export function listOperLogs(params: { page?: number; size?: number }) {
  return request.get('/auth/ops/logs', { params }) as Promise<PageResult<any>>
}
export function listSysConfig() {
  return request.get('/auth/ops/config') as Promise<any[]>
}
export function saveSysConfig(data: { configKey: string; configValue: string; remark?: string }) {
  return request.post('/auth/ops/config', data) as Promise<any>
}
export function getServiceHealth() {
  return request.get('/auth/ops/health') as Promise<any[]>
}
export function listRefundOrders(params: { page?: number; size?: number }) {
  return request.get('/auth/orders', {
    params: { ...params, status: 'refunded' },
  }) as Promise<PageResult<any>>
}
export function financeSummary() {
  return request.get('/auth/ops/finance') as Promise<any>
}

/* ---------- 平台敏感词 / AI 参数 / AI 调用监控 / 租户功能授权 ---------- */
export function listPlatformSensitiveWords() {
  return request.get('/auth/ops/sensitive-words') as Promise<any[]>
}
export function addPlatformSensitiveWord(word: string) {
  return request.post('/auth/ops/sensitive-words', { word }) as Promise<void>
}
export function deletePlatformSensitiveWord(id: number) {
  return request.delete(`/auth/ops/sensitive-words/${id}`) as Promise<void>
}
export function listAiPlatformConfig() {
  return request.get('/auth/ops/ai-config') as Promise<any[]>
}
export function saveAiPlatformConfig(data: {
  configKey: string
  configValue: string
  remark?: string
}) {
  return request.post('/auth/ops/ai-config', data) as Promise<void>
}
export function getAiCallStats(days = 7) {
  return request.get('/auth/ops/ai-calls/stats', { params: { days } }) as Promise<any>
}
export function pageAiCalls(params: {
  tenantId?: number
  scene?: string
  page?: number
  size?: number
}) {
  return request.get('/auth/ops/ai-calls', { params }) as Promise<PageResult<any>>
}
export function updateTenantFeatures(id: number, featureCodes: string[]) {
  return request.put(`/auth/tenants/${id}/features`, { featureCodes }) as Promise<any>
}
