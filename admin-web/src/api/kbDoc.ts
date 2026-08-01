import request from '@/utils/request'

/** 文档 */
export function listKbDocs(params?: { category?: string; keyword?: string; docStatus?: string }) {
  return request.get('/ai/kb/docs', { params })
}
export function getKbDoc(id: number) {
  return request.get(`/ai/kb/docs/${id}`)
}
export function uploadKbDoc(data: {
  title?: string
  fileName?: string
  content: string
  category?: string
}) {
  return request.post('/ai/kb/docs', data)
}
export function uploadKbDocFile(file: File, category?: string, title?: string) {
  const fd = new FormData()
  fd.append('file', file)
  if (category) fd.append('category', category)
  if (title) fd.append('title', title)
  return request.post('/ai/kb/docs/upload', fd, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}
export function updateKbDoc(
  id: number,
  data: { title?: string; content?: string; category?: string; docStatus?: string },
) {
  return request.put(`/ai/kb/docs/${id}`, data)
}
export function updateKbDocStatus(id: number, docStatus: string) {
  return request.put(`/ai/kb/docs/${id}/status`, { docStatus })
}
export function deleteKbDoc(id: number) {
  return request.delete(`/ai/kb/docs/${id}`)
}
export function batchDeleteKbDocs(ids: number[]) {
  return request.post('/ai/kb/docs/batch-delete', { ids })
}
export function buildKbIndex(
  id: number,
  data?: { chunkSize?: number; overlap?: number; rebuild?: boolean },
) {
  return request.post(`/ai/kb/docs/${id}/index`, data || {})
}
export function batchBuildKbIndex(data: {
  ids: number[]
  chunkSize?: number
  overlap?: number
  rebuild?: boolean
}) {
  return request.post('/ai/kb/docs/batch-index', data)
}
export function listKbIndexLogs(id: number) {
  return request.get(`/ai/kb/docs/${id}/logs`)
}
export function listKbDocChunks(id: number) {
  return request.get(`/ai/kb/docs/${id}/chunks`)
}

/** 分类 */
export function listKbCategories(enabledOnly?: boolean) {
  return request.get('/ai/kb/categories', {
    params: enabledOnly != null ? { enabledOnly } : undefined,
  })
}
export function saveKbCategory(data: Record<string, unknown>) {
  return request.post('/ai/kb/categories', data)
}
export function deleteKbCategory(id: number) {
  return request.delete(`/ai/kb/categories/${id}`)
}

/** 设置 */
export function getKbSettings() {
  return request.get('/ai/kb/settings')
}
export function saveKbSettings(data: Record<string, unknown>) {
  return request.put('/ai/kb/settings', data)
}

/** 问答日志 */
export function listKbChatLogs(params?: {
  keyword?: string
  rating?: number
  /** kb | llm | biz | chitchat */
  source?: string
  limit?: number
}) {
  return request.get('/ai/chat/admin-logs', { params })
}
/** 问答统计：卡片（当日）+ 高频问题 TOP10（topRange: today|7d|30d） */
export function getKbChatStats(topRange?: string) {
  return request.get('/ai/chat/admin-stats', {
    params: topRange ? { topRange } : undefined,
  })
}
export function rateKbChatLog(id: number, data: { rating: number; remark?: string }) {
  return request.put(`/ai/chat/logs/${id}/rate`, data)
}
