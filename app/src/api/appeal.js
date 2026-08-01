/**
 * 邻里倾诉 · 社区朋友圈 API
 *
 * 住户端：
 *   GET    /appeal/feed
 *   POST   /appeal
 *   DELETE /appeal/{id}
 *   GET    /appeal/{id}/comments
 *   POST   /appeal/{id}/comments
 *   DELETE /appeal/comments/{id}
 *   POST   /appeal/{id}/like
 *   GET    /appeal/mute/me
 *
 * 物业后台：
 *   GET    /appeal/admin/list?filter=all|normal|deleted|muted
 *   GET    /appeal/admin/{id}
 *   DELETE /appeal/admin/{id}
 *   DELETE /appeal/admin/comments/{id}
 *   POST   /appeal/admin/mute
 *   POST   /appeal/admin/unmute
 *   GET    /appeal/admin/op-logs
 */
import { request } from '../utils/request'

export const listAppealFeed = (limit = 50) =>
  request({ url: '/appeal/feed', data: { limit } })

export const publishAppeal = (data) =>
  request({ url: '/appeal', method: 'POST', data })

export const deleteAppeal = (id) =>
  request({ url: `/appeal/${id}`, method: 'DELETE' })

export const listAppealComments = (id) =>
  request({ url: `/appeal/${id}/comments` })

export const addAppealComment = (id, content) =>
  request({ url: `/appeal/${id}/comments`, method: 'POST', data: { content } })

export const deleteAppealComment = (id) =>
  request({ url: `/appeal/comments/${id}`, method: 'DELETE' })

export const toggleAppealLike = (id) =>
  request({ url: `/appeal/${id}/like`, method: 'POST' })

export const getMyAppealMute = () =>
  request({ url: '/appeal/mute/me' })

/** 举报动态或评论 targetType: POST | COMMENT */
export const submitAppealReport = (data) =>
  request({ url: '/appeal/reports', method: 'POST', data })

/** 历史 AI 工单（物业工作台统计用） */
export const listAppeal = (status) =>
  request({ url: '/appeal/tickets', data: status ? { status } : undefined })
export const createAppeal = (data) => publishAppeal(data)
export const handleAppeal = (id, data) =>
  request({ url: `/appeal/${id}/handle`, method: 'PUT', data })
