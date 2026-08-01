import request from '@/utils/request'

export interface ParkSpace {
  id: number
  spaceNo: string
  areaCode?: string
  spaceType?: string
  status?: string
  houseId?: number
  bindPlateNo?: string
  occupyingPlate?: string
  ownerName?: string
  bindCarId?: number
  antiOccupy?: number
  remark?: string
}

export interface ParkAccessLog {
  id: number
  plateNo: string
  direction?: string
  gateName?: string
  passResult?: string
  passReason?: string
  passTime?: string
  snapUrl?: string
}

export interface ParkFeeLedgerRow {
  id: number
  plateNo?: string
  houseLabel?: string
  amount?: number
  period?: string
  status?: string
  statusLabel?: string
  feeKind?: string
  feeKindLabel?: string
  orderNo?: string
  enterTime?: string
  exitTime?: string
  payTime?: string
  createTime?: string
}

export function listParkSpaces(params?: { areaCode?: string; status?: string; spaceType?: string }) {
  return request.get('/parking/spaces', { params }) as Promise<ParkSpace[]>
}

export function saveParkSpace(data: Partial<ParkSpace> & { spaceNo: string }) {
  if (data.id) return request.put(`/parking/spaces/${data.id}`, data) as Promise<ParkSpace>
  return request.post('/parking/spaces', data) as Promise<ParkSpace>
}

export function deleteParkSpace(id: number) {
  return request.delete(`/parking/spaces/${id}`)
}

export function getParkSpaceStats() {
  return request.get('/parking/spaces/map') as Promise<{
    total: number
    free: number
    occupied: number
  }>
}

export function bindParkSpaceCar(spaceId: number, carId: number) {
  return request.put(`/parking/spaces/${spaceId}/bind-car`, { carId }) as Promise<ParkSpace>
}

export function listParkAccessLogs(params?: {
  plateNo?: string
  from?: string
  to?: string
  direction?: string
  passResult?: string
}) {
  return request.get('/parking/access-logs', { params }) as Promise<ParkAccessLog[]>
}

export function listParkFeeLedger(params?: { feeKind?: string; keyword?: string; status?: string }) {
  return request.get('/parking/fee-ledger', { params }) as Promise<ParkFeeLedgerRow[]>
}

export function syncParkArrearsRestrict() {
  return request.post('/parking/arrears/sync-restrict') as Promise<{ marked: number; message: string }>
}

export function listParkOrders(params?: { plateNo?: string; status?: string }) {
  return request.get('/parking/orders', { params })
}
