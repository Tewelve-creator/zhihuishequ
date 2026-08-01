import request from '@/utils/request'

/** 平台全域能力 API（community-repair /platform/hub） */

export function getRiskSummary() {
  return request.get('/platform/hub/risk/summary') as Promise<any>
}
export function listRiskRepairs(params?: { level?: string; tenantId?: number; keyword?: string }) {
  return request.get('/platform/hub/risk/repairs', { params }) as Promise<any[]>
}
export function getOpsReport(days = 30) {
  return request.get('/platform/hub/reports/ops', { params: { days } }) as Promise<any>
}
export function getStoreReport(days = 30) {
  return request.get('/platform/hub/reports/store', { params: { days } }) as Promise<any>
}
/** 商户营收：社区×商户总营收与社区提成（平台不抽成） */
export function getMerchantRevenueReport(params?: {
  days?: number
  tenantId?: number
  shopId?: number
}) {
  return request.get('/platform/hub/reports/merchant-revenue', { params }) as Promise<any>
}
export function getAiReport(days = 30) {
  return request.get('/platform/hub/reports/ai', { params: { days } }) as Promise<any>
}
