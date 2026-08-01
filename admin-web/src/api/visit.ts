import request from '@/utils/request'

export interface VisitItem {
  id: number
  visitorName?: string
  visitorPhone?: string
  plateNo?: string
  driveFlag?: number
  visitReason?: string
  visitTime?: string
  expireTime?: string
  visitorCount?: number
  status?: string
  statusLabel?: string
  statusTone?: string
  checkinTime?: string
  remark?: string
  createTime?: string
  hostHouse?: string
  communityName?: string
  hostNickname?: string
  passToken?: string
  passUrl?: string
  remainSeconds?: number
  accessLogs?: AccessLog[]
}

export interface AccessLog {
  id: number
  result?: string
  resultLabel?: string
  failReason?: string
  channel?: string
  channelLabel?: string
  clientIp?: string
  remark?: string
  createTime?: string
}

/** 兼容旧调用 */
export function listVisits(params?: { status?: string }) {
  return request.get('/visit', { params }) as Promise<VisitItem[]>
}

export function listAdminVisits(params?: {
  status?: string
  phone?: string
  keyword?: string
  startTime?: string
  endTime?: string
  page?: number
  size?: number
}) {
  return request.get('/visit/admin', { params }) as Promise<{
    records: VisitItem[]
    total: number
    page: number
    size: number
  }>
}

export function getAdminVisitDetail(id: number) {
  return request.get(`/visit/admin/${id}`) as Promise<VisitItem>
}

export function checkinVisit(id: number) {
  return request.put(`/visit/${id}/checkin`)
}

export function deleteAdminVisit(id: number) {
  return request.delete(`/visit/admin/${id}`)
}

export function getVisitTodoSummary() {
  return request.get('/visit/todo-summary') as Promise<{ todoCount: number }>
}
