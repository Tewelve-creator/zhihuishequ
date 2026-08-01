import request from '@/utils/request'

export interface EquipDevice {
  id: number
  name: string
  location?: string
  deviceType?: string
  /** 设备数量 */
  quantity?: number
  cycleType?: string
  cycleLabel?: string
  vendorName?: string
  contactName?: string
  contactPhone?: string
  lastMaintDate?: string
  nextMaintDate?: string
  status?: string
  statusLabel?: string
  remark?: string
}

export interface EquipWorkOrder {
  id: number
  title: string
  deviceId?: number
  deviceName?: string
  location?: string
  source?: string
  sourceLabel?: string
  sourceCheckinId?: number
  status?: string
  statusLabel?: string
  assigneeId?: number
  assigneeName?: string
  faultDesc?: string
  repairContent?: string
  partsReplaced?: string
  repairPhotos?: string
  acceptPhotos?: string
  createByName?: string
  assignTime?: string
  finishTime?: string
  remark?: string
  createTime?: string
}

export function listEquipDevices(params?: { status?: string; keyword?: string }) {
  return request.get('/equip/devices', { params }) as Promise<EquipDevice[]>
}

export function getEquipDevice(id: number) {
  return request.get(`/equip/devices/${id}`) as Promise<EquipDevice>
}

export function saveEquipDevice(
  data: Partial<EquipDevice> & { name: string; deviceType: string; quantity?: number },
) {
  if (data.id) return request.put(`/equip/devices/${data.id}`, data) as Promise<EquipDevice>
  return request.post('/equip/devices', data) as Promise<EquipDevice>
}

export function deleteEquipDevice(id: number) {
  return request.delete(`/equip/devices/${id}`)
}

export function listEquipWorkOrders(params?: { status?: string; keyword?: string; source?: string }) {
  return request.get('/equip/work-orders', { params }) as Promise<EquipWorkOrder[]>
}

export function getEquipWorkOrder(id: number) {
  return request.get(`/equip/work-orders/${id}`) as Promise<EquipWorkOrder>
}

export function saveEquipWorkOrder(data: Partial<EquipWorkOrder> & { title: string }) {
  if (data.id) return request.put(`/equip/work-orders/${data.id}`, data) as Promise<EquipWorkOrder>
  return request.post('/equip/work-orders', data) as Promise<EquipWorkOrder>
}

export function deleteEquipWorkOrder(id: number) {
  return request.delete(`/equip/work-orders/${id}`)
}

export function assignEquipWorkOrder(id: number, data: { assigneeId?: number; assigneeName: string }) {
  return request.post(`/equip/work-orders/${id}/assign`, data) as Promise<EquipWorkOrder>
}

export function completeEquipWorkOrder(
  id: number,
  data: {
    repairContent: string
    partsReplaced?: string
    repairPhotos?: string
    acceptPhotos?: string
    remark?: string
  },
) {
  return request.post(`/equip/work-orders/${id}/complete`, data) as Promise<EquipWorkOrder>
}

export function createWorkOrderFromCheckin(
  checkinId: number,
  data?: { title?: string; deviceName?: string; location?: string },
) {
  return request.post(`/equip/work-orders/from-checkin/${checkinId}`, data || {}) as Promise<EquipWorkOrder>
}

export function getEquipTodoSummary() {
  return request.get('/equip/todo-summary') as Promise<{
    todoCount: number
    pendingCount: number
    doingCount: number
  }>
}

/** 即将/已逾期维保设备（工作台风险预警） */
export function listEquipDue() {
  return request.get('/equip/due-list') as Promise<EquipDevice[]>
}
