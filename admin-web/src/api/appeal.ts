import request from '@/utils/request'

/**
 * 社区动态 · 物业审核管理 API
 *
 * GET    /appeal/admin/list?filter=all|normal|deleted|muted|hidden|pinned
 * GET    /appeal/admin/{id}
 * DELETE /appeal/admin/{id}            body?: { reason }
 * PUT    /appeal/admin/{id}/pin        { pinned }
 * PUT    /appeal/admin/{id}/hide       { hidden, reason? }
 * GET    /appeal/admin/comments
 * DELETE /appeal/admin/comments/{id}   body?: { reason }
 * GET    /appeal/admin/reports
 * PUT    /appeal/admin/reports/{id}/handle
 * POST   /appeal/admin/mute            { userId, duration: forever|3d|7d, reason }
 * POST   /appeal/admin/unmute          { userId }
 * GET    /appeal/admin/op-logs
 */

export function listAppealAdmin(filter?: string) {
  return request.get('/appeal/admin/list', { params: filter ? { filter } : undefined })
}

export function getAppealAdminDetail(id: number) {
  return request.get(`/appeal/admin/${id}`)
}

export function deleteAppealAdmin(id: number, reason?: string) {
  return request.delete(`/appeal/admin/${id}`, { data: reason ? { reason } : {} })
}

export function pinAppealAdmin(id: number, pinned: boolean) {
  return request.put(`/appeal/admin/${id}/pin`, { pinned })
}

export function hideAppealAdmin(id: number, hidden: boolean, reason?: string) {
  return request.put(`/appeal/admin/${id}/hide`, { hidden, reason })
}

export function listAppealCommentsAdmin(params?: { keyword?: string; deletedOnly?: boolean }) {
  return request.get('/appeal/admin/comments', { params })
}

export function deleteAppealCommentAdmin(id: number, reason?: string) {
  return request.delete(`/appeal/admin/comments/${id}`, { data: reason ? { reason } : {} })
}

export function listAppealReports(status?: string) {
  return request.get('/appeal/admin/reports', { params: status ? { status } : undefined })
}

export function handleAppealReport(
  id: number,
  data: { action: 'done' | 'reject' | 'delete_target'; handleResult?: string },
) {
  return request.put(`/appeal/admin/reports/${id}/handle`, data)
}

export function muteAppealUser(data: { userId: number; duration: string; reason?: string }) {
  return request.post('/appeal/admin/mute', data)
}

export function unmuteAppealUser(userId: number) {
  return request.post('/appeal/admin/unmute', { userId })
}

export function listAppealOpLogs(limit = 80) {
  return request.get('/appeal/admin/op-logs', { params: { limit } })
}

/** 兼容旧工单接口 */
export function listAppeals(status?: string) {
  return request.get('/appeal/tickets', { params: status ? { status } : undefined })
}
export function handleAppeal(id: number, data: { status?: string; handleRemark?: string }) {
  return request.put(`/appeal/${id}/handle`, data)
}
