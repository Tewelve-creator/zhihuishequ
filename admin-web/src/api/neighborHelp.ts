import request from '@/utils/request'

/** 邻里互助管理（独立于邻里倾诉 /appeal） */

export interface NeighborHelpPost {
  id: number
  postNo?: string
  ownerName?: string
  houseLabel?: string
  helpType?: string
  title?: string
  content?: string
  images?: string
  imageList?: string[]
  status?: string
  statusLabel?: string
  statusTagType?: string
  offReason?: string
  offByName?: string
  offTime?: string
  createTime?: string
}

export function listNeighborHelpAdmin(params?: {
  helpType?: string
  status?: string
  keyword?: string
}) {
  return request.get('/neighbor-help/admin/list', { params }) as Promise<NeighborHelpPost[]>
}

export function getNeighborHelpAdmin(id: number) {
  return request.get(`/neighbor-help/admin/${id}`) as Promise<NeighborHelpPost>
}

export function takeDownNeighborHelp(id: number, reason: string) {
  return request.put(`/neighbor-help/admin/${id}/off`, { reason }) as Promise<NeighborHelpPost>
}

export function restoreNeighborHelp(id: number) {
  return request.put(`/neighbor-help/admin/${id}/restore`) as Promise<NeighborHelpPost>
}
