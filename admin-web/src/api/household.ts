import request from '@/utils/request'

export interface MoveApply {
  id: number
  applyNo?: string
  no?: string
  name?: string
  phone?: string
  room?: string
  houseLabel?: string
  type?: string
  applyType?: string
  identityLabel?: string
  status?: string
  statusLabel?: string
  remark?: string
  createTime?: string
  auditName?: string
  auditRemark?: string
  auditTime?: string
}

export interface Household {
  id: number
  name: string
  phone: string
  room?: string
  houseLabel?: string
  houseId?: number
  userId?: number
  type?: string
  identityType?: string
  status?: string
  statusLabel?: string
  source?: string
  leaseStart?: string
  leaseEnd?: string
  createTime?: string
}

export interface HouseCatalogItem {
  id: number
  label?: string
  communityName?: string
  building?: string
  unit?: string
  roomNo?: string
}

export function listMoveApplies(params?: {
  status?: string
  applyType?: string
  keyword?: string
  page?: number
  size?: number
}) {
  return request.get('/auth/house/move/list', { params }) as Promise<{
    records: MoveApply[]
    total: number
  }>
}

export interface MoveStats {
  pendingTotal?: number
  approvedTotal?: number
  rejectedTotal?: number
  moveInPending?: number
  moveOutPending?: number
  moveInApproved?: number
  moveOutApproved?: number
  activeHousehold?: number
  pendingHousehold?: number
  movingOutHousehold?: number
  movedOutHousehold?: number
  byIdentity?: Array<{ name?: string; label?: string; value?: number }>
  recentTrend?: Array<{ day?: string; moveIn?: number; moveOut?: number; total?: number }>
}

/** 当前租户迁移/住户流动统计（真实接口） */
export function getMoveStats() {
  return request.get('/auth/house/move/stats') as Promise<MoveStats>
}

export function approveMove(id: number, remark?: string) {
  return request.post(`/auth/house/move/${id}/approve`, { remark })
}

export function rejectMove(id: number, remark?: string) {
  return request.post(`/auth/house/move/${id}/reject`, { remark })
}

export function listHouseholds(params?: {
  status?: string
  identityType?: string
  keyword?: string
  page?: number
  size?: number
}) {
  return request.get('/auth/house/household/list', { params }) as Promise<{
    records: Household[]
    total: number
  }>
}

export function saveHousehold(
  data: Partial<Household> & {
    houseId: number
    name: string
    phone: string
    leaseStart?: string
    leaseEnd?: string
  },
) {
  if (data.id) {
    return request.put(`/auth/house/household/${data.id}`, data)
  }
  return request.post('/auth/house/household', data)
}

export function removeHousehold(id: number) {
  return request.delete(`/auth/house/household/${id}`)
}

export function listHouseCatalog() {
  return request.get('/auth/house/catalog') as Promise<HouseCatalogItem[]>
}
