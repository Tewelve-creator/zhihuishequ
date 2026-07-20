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
}

export function listRepairOrders(status?: string) {
  return request.get('/repair/orders', {
    params: status ? { status } : undefined,
  }) as Promise<RepairOrder[]>
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
