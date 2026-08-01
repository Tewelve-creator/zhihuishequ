import { request, resolveLoginTenantId } from '../utils/request'

function withTenant(data = {}) {
  const tenantId = resolveLoginTenantId()
  const userId = uni.getStorageSync('userId')
  return {
    ...data,
    tenantId: tenantId != null && tenantId !== '' ? Number(tenantId) || tenantId : undefined,
    userId: userId != null && userId !== '' ? Number(userId) || userId : undefined
  }
}

/** 居民智能管家问答（强制带登录租户） */
export const chat = (question, tenantId) => {
  const tid = tenantId != null ? tenantId : resolveLoginTenantId()
  return request({
    url: '/ai/chat',
    method: 'POST',
    data: withTenant({ question, tenantId: tid })
  })
}

/** 确认业务办理（未确认不写库） */
export const confirmBiz = (actionToken) =>
  request({
    url: '/ai/chat/confirm',
    method: 'POST',
    data: withTenant({ actionToken })
  })

/** 取消当前业务办理 */
export const cancelBiz = (actionToken) =>
  request({
    url: '/ai/chat/cancel',
    method: 'POST',
    data: withTenant({ actionToken })
  })

/** 物业：AI 业务操作日志 */
export const listBizOpLogs = (limit = 50) =>
  request({ url: '/ai/biz-op-logs', data: { limit } })

/** 居民本人对话记录（本社区） */
export const listChatLogs = () =>
  request({ url: '/ai/chat/logs', data: withTenant() })

/** 评价对话 1好评 2差评 */
export const rateChatLog = (id, rating, remark) =>
  request({
    url: `/ai/chat/logs/${id}/rate`,
    method: 'PUT',
    data: withTenant({ rating, remark })
  })

/** 报修识图 */
export const imageRepair = (imageUrl) =>
  request({ url: '/ai/image', method: 'POST', data: withTenant({ imageUrl }) })

/** 情绪分析 / 轻调解 */
export const emotion = (text) =>
  request({ url: '/ai/emotion', method: 'POST', data: withTenant({ text }) })

export const myEmotionCases = () => request({ url: '/ai/emotion/my' })

/** 公告审核摘要（物业工具） */
export const auditNotice = (content) =>
  request({
    url: '/ai/notice/summary',
    method: 'POST',
    data: withTenant({ content, bizType: 'notice' })
  })

export const getProfile = () => request({ url: '/ai/profile' })
export const saveProfile = (data) =>
  request({ url: '/ai/profile', method: 'PUT', data: withTenant(data) })

export const generateSchedule = () =>
  request({ url: '/ai/schedule/generate', method: 'POST', data: withTenant() })
export const listSchedule = () => request({ url: '/ai/schedule' })
export const conflictCheck = () =>
  request({ url: '/ai/schedule/conflict-check', method: 'POST', data: withTenant() })
export const recommendServices = () => request({ url: '/ai/recommend/services' })

export const lostMatch = (data) =>
  request({ url: '/ai/lost/match', method: 'POST', data: withTenant(data) })
export const createLost = (data) =>
  request({ url: '/ai/lost', method: 'POST', data: withTenant(data) })
export const listLost = (params = {}) =>
  request({ url: '/ai/lost', data: params })
export const listLostCategories = (enabledOnly = true) =>
  request({ url: '/ai/lost/categories', data: { enabledOnly } })
export const submitLostClaim = (id, data = {}) =>
  request({ url: `/ai/lost/${id}/claim`, method: 'POST', data: withTenant(data) })
export const listKb = () => request({ url: '/ai/kb?all=true' })
export const saveKb = (data) =>
  request({ url: '/ai/kb', method: 'POST', data: withTenant(data) })
export const deleteKb = (id) => request({ url: `/ai/kb/${id}`, method: 'DELETE' })

/** 物业：AI 调用日志（识图等） */
export const listCallLogs = (scene = 'image') =>
  request({ url: '/ai/call-logs', data: { scene, limit: 30 } })

/** 物业：业主咨询统计 */
export const chatStats = () => request({ url: '/ai/chat/stats' })
