import request from '@/utils/request'

export function getAiProfile() {
  return request.get('/ai/profile') as Promise<Record<string, any>>
}

export function saveAiProfile(body: Record<string, any>) {
  return request.put('/ai/profile', body) as Promise<Record<string, any>>
}

export function generateSchedule() {
  return request.post('/ai/schedule/generate') as Promise<{ created: number; conflicts: number; items: any[] }>
}

export function listMySchedule() {
  return request.get('/ai/schedule') as Promise<any[]>
}

export function conflictCheck() {
  return request.post('/ai/schedule/conflict-check') as Promise<{ conflictCount: number; items: any[] }>
}

/** 物业：AI 日程台账 */
export function listAdminSchedule(params?: {
  status?: string
  keyword?: string
  userId?: number
  scheduleType?: string
  conflict?: number
  startTime?: string
  endTime?: string
  limit?: number
}) {
  return request.get('/ai/schedule/admin', { params }) as Promise<any[]>
}

export function getAdminScheduleStats() {
  return request.get('/ai/schedule/admin-stats') as Promise<{
    total: number
    pending: number
    sent: number
    conflict: number
    typeDist: Array<{ type: string; name: string; value: number }>
  }>
}

export function getAdminScheduleDetail(id: number) {
  return request.get(`/ai/schedule/admin/${id}`) as Promise<any>
}

export function deleteAdminSchedule(id: number) {
  return request.delete(`/ai/schedule/${id}`)
}

/** 各类日程提前提醒小时数 */
export function getScheduleSettings() {
  return request.get('/ai/schedule/settings') as Promise<Record<string, number>>
}

export function saveScheduleSettings(body: Record<string, number>) {
  return request.put('/ai/schedule/settings', body) as Promise<Record<string, number>>
}

export function listRecommendServices() {
  return request.get('/ai/recommend/services') as Promise<any[]>
}

/** 物业：AI 识图日志 */
export function listImageLogs(params?: {
  facilityType?: string
  damageLevel?: string
  needManual?: number
  confMin?: number
  confMax?: number
  startTime?: string
  endTime?: string
  limit?: number
} | number) {
  // 兼容旧调用 listImageLogs(80)
  if (typeof params === 'number') {
    return request.get('/ai/image/logs', { params: { limit: params } }) as Promise<any[]>
  }
  return request.get('/ai/image/logs', { params: params || {} }) as Promise<any[]>
}

export function getImageLogStats() {
  return request.get('/ai/image/admin-stats') as Promise<{
    todayTotal: number
    autoPass: number
    needReview: number
    highConfidence: number
    modeDist: Array<{ name: string; value: number }>
    facilityOptions?: string[]
    damageOptions?: string[]
  }>
}

export function getImageLogDetail(id: number) {
  return request.get(`/ai/image/logs/${id}`) as Promise<any>
}

export function deleteImageLog(id: number) {
  return request.delete(`/ai/image/logs/${id}`)
}

export interface EmotionCaseStats {
  total?: number
  pending?: number
  handling?: number
  closed?: number
  highRisk?: number
}

export function listEmotionCases(params?: {
  status?: string
  userId?: number | string
  keyword?: string
  emotionTag?: string
  startDate?: string
  endDate?: string
  limit?: number
}) {
  return request.get('/ai/emotion/cases', { params }) as Promise<any[]>
}

export function getEmotionCaseStats() {
  return request.get('/ai/emotion/cases/stats') as Promise<EmotionCaseStats>
}

export function updateEmotionCase(
  id: number,
  body: {
    status?: string
    handleRemark?: string
    mediateRecord?: string
    handleResult?: string
  },
) {
  return request.put(`/ai/emotion/cases/${id}`, body) as Promise<any>
}

export function deleteEmotionCase(id: number) {
  return request.delete(`/ai/emotion/cases/${id}`)
}

export function listSensitiveWords() {
  return request.get('/ai/sensitive-words') as Promise<Array<{ id: number; word: string; createTime?: string }>>
}

export function addSensitiveWord(word: string) {
  return request.post('/ai/sensitive-words', { word }) as Promise<any>
}

export function deleteSensitiveWord(id: number) {
  return request.delete(`/ai/sensitive-words/${id}`)
}

export function auditNoticeContent(content: string) {
  return request.post('/ai/notice/summary', { content, bizType: 'notice' }) as Promise<{
    pass: boolean
    summary?: string
    hitWords?: string[]
  }>
}

export function targetPushNotice(noticeId: number, targetRule?: string) {
  return request.post('/ai/notice/target-push', { noticeId, targetRule }) as Promise<{ pushed: number }>
}

export function listNoticePushLogs(noticeId?: number, limit = 50) {
  return request.get('/ai/notice/push-logs', { params: { noticeId, limit } }) as Promise<any[]>
}
