import request from '@/utils/request'

export function listActivities(all = true, params?: { category?: string; keyword?: string }) {
  return request.get('/activity', { params: { all, ...params } }) as Promise<any[]>
}

export function saveActivity(data: Record<string, unknown>) {
  return data.id ? request.put(`/activity/${data.id}`, data) : request.post('/activity', data)
}

export function setActivityStatus(id: number, status: number) {
  return request.put(`/activity/${id}/status`, { status })
}

export function deleteActivity(id: number) {
  return request.delete(`/activity/${id}`)
}

export function listActivitySignups(id: number) {
  return request.get(`/activity/${id}/signups`)
}

export function listAllActivitySignups(params?: { activityId?: number; status?: string; keyword?: string }) {
  return request.get('/activity/signups', { params }) as Promise<any[]>
}

export function checkActivitySignup(signupId: number) {
  return request.put(`/activity/signup/${signupId}/check`)
}

export function cancelActivitySignup(signupId: number, remark?: string) {
  return request.put(`/activity/signup/${signupId}/cancel`, { remark })
}

export function listActivityCategories(enabledOnly?: boolean) {
  return request.get('/activity/categories', { params: { enabledOnly } }) as Promise<any[]>
}

export function saveActivityCategory(body: Record<string, unknown>) {
  return request.post('/activity/categories', body)
}

export function deleteActivityCategory(id: number) {
  return request.delete(`/activity/categories/${id}`)
}

export function getActivityStats() {
  return request.get('/activity/stats') as Promise<any>
}

export function listPlatformActivities(params?: {
  tenantId?: number
  keyword?: string
  category?: string
}) {
  return request.get('/activity/platform/list', { params }) as Promise<any[]>
}
export function platformSetActivityStatus(id: number, status: number) {
  return request.put(`/activity/platform/${id}/status`, { status })
}

export function targetPushActivity(id: number, targetRule?: string) {
  return request.post(`/activity/${id}/target-push`, { targetRule }) as Promise<{ pushed: number }>
}

export function auditActivityContent(content: string) {
  return request.post('/ai/notice/summary', { content, bizType: 'activity' }) as Promise<{
    pass: boolean
    summary?: string
    hitWords?: string[]
  }>
}
