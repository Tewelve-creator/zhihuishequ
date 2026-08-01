import request from '@/utils/request'

export interface NoticeInfo {
  id?: number
  tenantId?: number
  title?: string
  content?: string
  summary?: string
  targetTag?: string
  targetRule?: string
  aiAuditPass?: number
  pushCount?: number
  publisherId?: number
  status?: number
  publishTime?: string
  createTime?: string
  publishState?: string
}

export function listNotices(manage = false) {
  return request.get('/notice', {
    params: manage ? { manage: true } : undefined,
  }) as Promise<NoticeInfo[]>
}

export function getNotice(id: number) {
  return request.get(`/notice/${id}`) as Promise<NoticeInfo>
}

export function createNotice(body: Partial<NoticeInfo>) {
  return request.post('/notice', body) as Promise<NoticeInfo>
}

export function updateNotice(id: number, body: Partial<NoticeInfo>) {
  return request.put(`/notice/${id}`, body) as Promise<NoticeInfo>
}

export function deleteNotice(id: number) {
  return request.delete(`/notice/${id}`) as Promise<void>
}
