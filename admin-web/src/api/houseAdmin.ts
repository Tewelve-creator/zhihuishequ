import request from '@/utils/request'

export function listAdminHouses(params?: {
  building?: string
  unit?: string
  keyword?: string
  occupyStatus?: string
}) {
  return request.get('/auth/house/admin/list', { params })
}
export function listAdminBuildings() {
  return request.get('/auth/house/admin/buildings')
}
export function listBuildingSummary() {
  return request.get('/auth/house/admin/building-summary') as Promise<
    Array<{
      building: string
      buildingName?: string
      unitCount: number
      houseCount: number
      activeCount: number
      vacantCount: number
      rentCount: number
      selfCount?: number
      occupiedCount?: number
      occupancyRate?: number
      elevatorCount?: number
      floors?: number
      remark?: string
      units?: string[]
      unitStats?: Array<{ unit: string; houseCount: number; vacantCount: number }>
    }>
  >
}
export function listAdminUnits(building: string) {
  return request.get('/auth/house/admin/units', { params: { building } })
}
export function ensureBuilding(data: {
  buildingName: string
  floors?: number
  elevators?: number
  elevatorCount?: number
  remark?: string
}) {
  return request.post('/auth/house/admin/building', data)
}
export function ensureUnit(data: { buildingName: string; unitName: string }) {
  return request.post('/auth/house/admin/unit', data)
}
export function saveHouse(data: Record<string, unknown>) {
  return data.id
    ? request.put(`/auth/house/admin/${data.id}`, data)
    : request.post('/auth/house/admin', data)
}
export function batchHouses(data: Record<string, unknown>) {
  return request.post('/auth/house/admin/batch', data)
}
export function deleteHouse(id: number) {
  return request.delete(`/auth/house/admin/${id}`)
}
