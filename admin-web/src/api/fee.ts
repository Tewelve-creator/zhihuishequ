import request from '@/utils/request'

export interface FeeBill {
  id: number
  houseId?: number
  houseLabel?: string
  room?: string
  userId?: number
  payerName?: string
  owner?: string
  payerPhone?: string
  feeType?: string
  feeTypeName?: string
  item?: string
  amount: number
  period?: string
  dueDate?: string
  status?: string
  rawStatus?: string
  statusLabel?: string
  paidAt?: string
  remark?: string
  createTime?: string
  plateNo?: string
}

export interface FeeHouseOption {
  id: number
  communityName?: string
  building?: string
  unit?: string
  roomNo?: string
  label?: string
}

export interface FeePayer {
  id: number
  userId?: number
  payerName?: string
  payerPhone?: string
  identityType?: string
  houseLabel?: string
}

export interface FeeBillSaveBody {
  id?: number
  houseId: number
  payerName?: string
  payerPhone?: string
  userId?: number
  feeType: string
  amount: number
  period: string
  dueDate?: string
  remark?: string
  plateNo?: string
}

export function listFeeBills(params?: { status?: string; keyword?: string; period?: string }) {
  return request.get('/fee/bills', { params }) as Promise<FeeBill[]>
}

export function createFeeBill(data: FeeBillSaveBody) {
  return request.post('/fee/bills', data) as Promise<FeeBill>
}

export function updateFeeBill(id: number, data: FeeBillSaveBody) {
  return request.put(`/fee/bills/${id}`, data) as Promise<FeeBill>
}

export function deleteFeeBill(id: number) {
  return request.delete(`/fee/bills/${id}`)
}

export function batchCreateFeeBills(data: {
  feeType: string
  amount: number
  period: string
  dueDate?: string
  remark?: string
  houseIds?: number[]
}) {
  return request.post('/fee/bills/batch', data) as Promise<{ count: number }>
}

export function listFeeHouses() {
  return request.get('/fee/houses') as Promise<FeeHouseOption[]>
}

export function listFeePayers(houseId: number) {
  return request.get(`/fee/houses/${houseId}/payers`) as Promise<FeePayer[]>
}

export interface FeePayment {
  id: number
  orderNo?: string
  houseId?: number
  houseLabel?: string
  room?: string
  payerName?: string
  payerPhone?: string
  amount: number
  payChannel?: string
  payChannelLabel?: string
  status?: string
  statusLabel?: string
  payTradeNo?: string
  billIds?: string
  paidAt?: string
  createTime?: string
}

export interface FeeArrearsHouse {
  houseId?: number
  building?: string
  room?: string
  owner?: string
  phone?: string
  userId?: number
  unpaidCount: number
  unpaidAmount: number
  overdueCount: number
  overdueAmount: number
  periods?: string[]
}

export interface FeeArrearsBuilding {
  building: string
  houseCount: number
  unpaidCount: number
  unpaidAmount: number
  overdueCount: number
  overdueAmount: number
}

export interface FeeArrearsSummary {
  buildings: FeeArrearsBuilding[]
  houses: FeeArrearsHouse[]
  buildingOptions: string[]
  totalHouses: number
  totalUnpaidAmount: number
  totalOverdueHouses: number
}

export interface FeeReceiptNotice {
  id: number
  title: string
  content?: string
  scope?: string
  amountHint?: string
  pushCount?: number
  status?: string
  sendAt?: string
  createTime?: string
  targetJson?: string
}

export function listFeePayments(params?: {
  startDate?: string
  endDate?: string
  keyword?: string
  payChannel?: string
}) {
  return request.get('/fee/payments', { params }) as Promise<FeePayment[]>
}

export function getFeeArrears(params?: {
  building?: string
  overdueOnly?: boolean
  keyword?: string
}) {
  return request.get('/fee/arrears', { params }) as Promise<FeeArrearsSummary>
}

export function listFeeReceiptNotices() {
  return request.get('/fee/receipt-notices') as Promise<FeeReceiptNotice[]>
}

export function createFeeReceiptNotice(data: {
  title: string
  content?: string
  scope?: string
  amountHint?: string
  targets?: Array<{
    houseId?: number
    userId?: number
    owner?: string
    room?: string
    phone?: string
    unpaidAmount?: number
  }>
}) {
  return request.post('/fee/receipt-notices', data) as Promise<FeeReceiptNotice>
}
