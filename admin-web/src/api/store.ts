import request from '@/utils/request'

export function getStoreInfo() {
  return request.get('/store/info')
}
export function saveStoreInfo(data: Record<string, unknown>) {
  return request.put('/store/info', data)
}
export function listStoreCategories(all = true) {
  return request.get('/store/categories', { params: { all } })
}
export function saveStoreCategory(data: Record<string, unknown>) {
  return request.post('/store/categories', data)
}
export function deleteStoreCategory(id: number) {
  return request.delete(`/store/categories/${id}`)
}
export function listStoreProducts(params?: {
  categoryId?: number
  keyword?: string
  onSaleOnly?: boolean
}) {
  return request.get('/store/products', { params })
}
export function saveStoreProduct(data: Record<string, unknown>) {
  return request.post('/store/products', data)
}
export function deleteStoreProduct(id: number) {
  return request.delete(`/store/products/${id}`)
}
export function listStoreOrders(params?: { status?: string; keyword?: string }) {
  return request.get('/store/orders', { params })
}
export function getStoreOrder(id: number) {
  return request.get(`/store/orders/${id}`)
}
export function auditStoreRefund(id: number, approve: boolean, remark?: string) {
  return request.put(`/store/orders/${id}/refund/audit`, { approve, remark })
}
export function directStoreRefund(id: number, reason?: string) {
  return request.put(`/store/orders/${id}/refund/direct`, { reason })
}
export function staffStoreAction(id: number, action: string) {
  return request.put(`/store/orders/${id}/action`, { action })
}
export function getStoreSettlement(params?: { startDate?: string; endDate?: string }) {
  return request.get('/store/settlement', { params }) as Promise<{
    storeName?: string
    commissionRate?: number
    orderCount?: number
    gmv?: number
    commissionAmount?: number
    refundCount?: number
    refundAmount?: number
    settleAmount?: number
    netAmount?: number
    ledger?: any[]
  }>
}
export function listStoreRefunds() {
  return request.get('/store/refunds') as Promise<any[]>
}

/** 商户营销资金账户（≠ SaaS 租户套餐订购账务） */
export function listMerchantWallets(params?: {
  tenantId?: number
  keyword?: string
  warnOnly?: boolean
}) {
  return request.get('/store/wallet/accounts', { params }) as Promise<any[]>
}
export function getMerchantWallet(merchantId: number) {
  return request.get(`/store/wallet/accounts/${merchantId}`)
}
export function getMerchantWalletWarnSummary() {
  return request.get('/store/wallet/warn-summary')
}
export function createWalletMerchant(data: Record<string, unknown>) {
  return request.post('/store/wallet/merchants', data)
}
export function updateWalletWarnThreshold(merchantId: number, warnThreshold: number) {
  return request.put(`/store/wallet/accounts/${merchantId}/warn-threshold`, { warnThreshold })
}
export function rechargeMerchantWallet(merchantId: number, amount: number, remark?: string) {
  return request.post(`/store/wallet/accounts/${merchantId}/recharge`, { amount, remark })
}
export function consumeMerchantWallet(merchantId: number, amount: number, remark?: string) {
  return request.post(`/store/wallet/accounts/${merchantId}/consume`, { amount, remark })
}
export function applyMerchantWithdraw(merchantId: number, amount: number, remark?: string) {
  return request.post(`/store/wallet/accounts/${merchantId}/withdraw`, { amount, remark })
}
export function pageMerchantWalletLedger(params?: {
  tenantId?: number
  merchantId?: number
  bizType?: string
  keyword?: string
  page?: number
  size?: number
}) {
  return request.get('/store/wallet/ledger', { params }) as Promise<{
    total: number
    page: number
    size: number
    records: any[]
  }>
}
export function listMerchantWithdraws(params?: {
  tenantId?: number
  merchantId?: number
  status?: string
}) {
  return request.get('/store/wallet/withdraws', { params }) as Promise<any[]>
}
export function auditMerchantWithdraw(id: number, status: 'APPROVED' | 'REJECTED', auditRemark?: string) {
  return request.put(`/store/wallet/withdraws/${id}/audit`, { status, auditRemark })
}

/** 商圈入驻 + 交易佣金应收（≠ SaaS 套餐订购账务） */
export function submitMerchantApply(data: Record<string, unknown>) {
  return request.post('/store/onboard/applies', data)
}
export function listMerchantApplies(params?: {
  tenantId?: number
  status?: string
  keyword?: string
}) {
  return request.get('/store/onboard/applies', { params }) as Promise<any[]>
}
export function getMerchantApply(id: number) {
  return request.get(`/store/onboard/applies/${id}`)
}
export function listMerchantApplyLogs(id: number) {
  return request.get(`/store/onboard/applies/${id}/logs`) as Promise<any[]>
}
export function listPropertyOnboardPending(keyword?: string) {
  return request.get('/store/onboard/property-pending', { params: { keyword } }) as Promise<any[]>
}
export function listPlatformOnboardPending(keyword?: string) {
  return request.get('/store/onboard/platform-pending', { params: { keyword } }) as Promise<any[]>
}
export function propertyAuditMerchantApply(id: number, approve: boolean, remark?: string) {
  return request.put(`/store/onboard/applies/${id}/property-audit`, { approve, remark })
}
export function platformAuditMerchantApply(id: number, approve: boolean, remark?: string) {
  return request.put(`/store/onboard/applies/${id}/platform-audit`, { approve, remark })
}
export function listMerchantOnboardOverview(params?: {
  tenantId?: number
  keyword?: string
  onboardStatus?: string
  settleStatus?: string
}) {
  return request.get('/store/onboard/overview', { params }) as Promise<any[]>
}
export function pageMerchantCommissionBills(params?: {
  tenantId?: number
  merchantId?: number
  settleStatus?: string
  keyword?: string
  page?: number
  size?: number
}) {
  return request.get('/store/onboard/bills', { params }) as Promise<{
    total: number
    page: number
    size: number
    records: any[]
  }>
}
export function settleMerchantCommissionBills(billIds: number[], batchNo?: string) {
  return request.post('/store/onboard/bills/settle', { billIds, batchNo }) as Promise<{ settledCount: number }>
}
export function listPlatformProfitRules() {
  return request.get('/store/onboard/profit-rules') as Promise<any[]>
}
export function savePlatformProfitRule(data: Record<string, unknown>) {
  return request.post('/store/onboard/profit-rules', data)
}
export function deletePlatformProfitRule(id: number) {
  return request.delete(`/store/onboard/profit-rules/${id}`)
}
