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

/** 居民本人对话记录（本社区） */
export const listChatLogs = () =>
  request({ url: '/ai/chat/logs', data: withTenant() })

/** 报修识图 */
export const imageRepair = (imageUrl) =>
  request({ url: '/ai/image', method: 'POST', data: withTenant({ imageUrl }) })

/** 情绪分析（物业工具） */
export const emotion = (text) =>
  request({ url: '/ai/emotion', method: 'POST', data: withTenant({ text }) })

/** 公告审核摘要（物业工具） */
export const auditNotice = (content) =>
  request({
    url: '/ai/notice/summary',
    method: 'POST',
    data: withTenant({ content, bizType: 'notice' })
  })

export const lostMatch = (data) =>
  request({ url: '/ai/lost/match', method: 'POST', data: withTenant(data) })
export const createLost = (data) =>
  request({ url: '/ai/lost', method: 'POST', data: withTenant(data) })
export const listKb = () => request({ url: '/ai/kb?all=true' })
export const saveKb = (data) =>
  request({ url: '/ai/kb', method: 'POST', data: withTenant(data) })
export const deleteKb = (id) => request({ url: `/ai/kb/${id}`, method: 'DELETE' })

/** 物业：AI 调用日志（识图等） */
export const listCallLogs = (scene = 'image') =>
  request({ url: '/ai/call-logs', data: { scene, limit: 30 } })

/** 物业：业主咨询统计 */
export const chatStats = () => request({ url: '/ai/chat/stats' })
