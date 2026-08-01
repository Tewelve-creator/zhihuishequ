import request from '@/utils/request'

export interface FeedbackItem {
  id: number
  feedbackNo?: string
  ownerName?: string
  houseLabel?: string
  phone?: string
  feedbackType?: string
  title?: string
  content?: string
  images?: string
  imageList?: string[]
  status?: string
  statusLabel?: string
  statusTagType?: string
  replyContent?: string
  replyByName?: string
  replyTime?: string
  workOrderId?: number
  createTime?: string
  logs?: FeedbackLog[]
}

export interface FeedbackLog {
  id: number
  action?: string
  actionLabel?: string
  fromStatus?: string
  fromStatusLabel?: string
  toStatus?: string
  toStatusLabel?: string
  content?: string
  operatorName?: string
  createTime?: string
}

export function listFeedback(params?: { status?: string; keyword?: string }) {
  return request.get('/feedback/list', { params }) as Promise<FeedbackItem[]>
}

export function getFeedback(id: number) {
  return request.get(`/feedback/${id}`) as Promise<FeedbackItem>
}

export function handleFeedback(
  id: number,
  data: { replyContent?: string; status?: string },
) {
  return request.put(`/feedback/${id}/handle`, data) as Promise<FeedbackItem>
}

/** 通过反馈并转入报事工单 */
export function approveFeedback(id: number, data?: { replyContent?: string }) {
  return request.put(`/feedback/${id}/approve`, data || {}) as Promise<FeedbackItem>
}
