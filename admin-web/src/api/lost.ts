import request from '@/utils/request'

export function listLost(params?: {
  status?: string
  itemType?: string
  category?: string
  keyword?: string
}) {
  return request.get('/ai/lost', { params }) as Promise<any[]>
}

export function markLostFound(id: number) {
  return request.put(`/ai/lost/${id}/found`)
}

/** 使用 POST 删除，避免部分代理/环境拦截 DELETE */
export function deleteLost(id: number) {
  return request.post(`/ai/lost/${id}/delete`) as Promise<void>
}

export function listLostCategories(enabledOnly?: boolean) {
  return request.get('/ai/lost/categories', { params: { enabledOnly } }) as Promise<any[]>
}

export function saveLostCategory(body: Record<string, unknown>) {
  return request.post('/ai/lost/categories', body)
}

export function deleteLostCategory(id: number) {
  return request.delete(`/ai/lost/categories/${id}`)
}

export function listLostClaims(params?: { lostId?: number; status?: string; keyword?: string }) {
  return request.get('/ai/lost/claims', { params }) as Promise<any[]>
}

export function verifyLostClaim(id: number, body: { status: 'approved' | 'rejected'; auditRemark?: string }) {
  return request.put(`/ai/lost/claims/${id}/verify`, body)
}

export function submitLostClaim(lostId: number, body?: Record<string, unknown>) {
  return request.post(`/ai/lost/${lostId}/claim`, body || {})
}
