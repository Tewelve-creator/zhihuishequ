import request from '@/utils/request'

/** 物业端社区运营大屏（本租户真实统计） */
export function getTenantDashboard() {
  return request.get('/stats/tenant/dashboard') as Promise<{
    tenantId?: number
    tenantName?: string
    workSummary?: {
      todayTotal?: number
      pending?: number
      doneToday?: number
      finishRate?: number
    }
    community?: {
      residentTotal?: number
      staffTotal?: number
      houseTotal?: number
      visitToday?: number
      visitPending?: number
      emotionPending?: number
      inspOpen?: number
    }
    todo?: {
      total?: number
      urgentRepair?: number
      pendingAudit?: number
      emotionPending?: number
      inspOpen?: number
    }
    repairTrendSeries?: Array<{ day: string; created: number; finished: number }>
    activeResidents?: Array<{ day: string; cnt: number }>
    workTypeDist?: Array<{ name: string; cnt: number }>
    staffRank?: Array<{ name: string; total: number; done: number }>
    buildings?: Array<{ name: string; houses: number; occupied: number }>
    mapMarkers?: Array<{
      id: number
      kind: string
      title: string
      status?: string
      place?: string
      building?: string
      buildingIndex?: number
      pinned?: boolean
      urgency?: string
    }>
    alerts?: Array<{
      id?: number
      type?: string
      level?: string
      content?: string
      createTime?: string
    }>
    recentEvents?: Array<{
      id: number
      type: string
      title: string
      status?: string
      createTime?: string
      biz?: string
    }>
  }>
}
