import request from '@/utils/request'

export interface RepairOrder {
  id: number
  tenantId?: number
  userId?: number
  handlerId?: number
  handlerName?: string
  title?: string
  description?: string
  facilityType?: string
  damageLevel?: string
  aiTag?: string
  imageUrl?: string
  finishImageUrl?: string
  status?: string
  handleRemark?: string
  acceptTime?: string
  finishTime?: string
  createTime?: string
  updateTime?: string
  orderType?: string
  orderTypeLabel?: string
  urgency?: string
  categoryId?: number
  categoryName?: string
  emotionCaseId?: number
  emotionTags?: string
  sentiment?: string
  emotionAlert?: boolean
  aiImageLogCount?: number
  houseId?: number
  building?: string
  unit?: string
  roomNo?: string
  /** 展示用位置，如 2栋1单元101 */
  locationLabel?: string
}

export interface RepairCategory {
  id?: number
  name: string
  orderType?: string
  urgencyDefault?: string
  sortNo?: number
  status?: number
  remark?: string
}

export function listRepairOrders(params?: {
  status?: string
  orderType?: string
  urgency?: string
  categoryId?: number
  unassigned?: boolean
  emotionAlert?: boolean
  mine?: boolean
}) {
  return request.get('/repair/orders', { params }) as Promise<RepairOrder[]>
}

export function acceptRepairOrder(id: number) {
  return request.put(`/repair/orders/${id}/accept`) as Promise<RepairOrder>
}

export function assignRepairOrder(id: number, handlerId: number) {
  return request.put(`/repair/orders/${id}/assign`, { handlerId }) as Promise<RepairOrder>
}

export function finishRepairOrder(id: number, body?: Partial<RepairOrder>) {
  return request.put(`/repair/orders/${id}/finish`, body || {}) as Promise<RepairOrder>
}

export function handleRepairOrder(id: number, body: Partial<RepairOrder>) {
  return request.put(`/repair/orders/${id}/handle`, body) as Promise<RepairOrder>
}

export function deleteRepairOrder(id: number) {
  return request.delete(`/repair/orders/${id}`) as Promise<void>
}

export function listRepairCategories(params?: { orderType?: string; enabledOnly?: boolean }) {
  return request.get('/repair/categories', { params }) as Promise<RepairCategory[]>
}

export function saveRepairCategory(body: Partial<RepairCategory>) {
  return request.post('/repair/categories', body) as Promise<RepairCategory>
}

export function deleteRepairCategory(id: number) {
  return request.delete(`/repair/categories/${id}`)
}

export function getRepairStats(params?: { startDate?: string; endDate?: string }) {
  return request.get('/repair/stats', { params }) as Promise<{
    total: number
    done: number
    pending: number
    processing: number
    repairCount: number
    reportCount: number
    finishRate: number
    avgHandleHours: number
    startDate?: string
    endDate?: string
    byCategory: Array<{ name: string; value: number }>
    byUrgency: Array<{ name: string; value: number }>
    byType: Array<{ name: string; value: number }>
  }>
}
