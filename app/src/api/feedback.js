import { request } from '../utils/request'

export const listMyFeedback = () => request({ url: '/feedback/mine' })
export const createFeedback = (data) => request({ url: '/feedback', method: 'POST', data })

export const listNeighborHelp = (helpType) =>
  request({ url: '/neighbor-help', data: helpType ? { helpType } : undefined })
export const createNeighborHelp = (data) =>
  request({ url: '/neighbor-help', method: 'POST', data })
/** 我来帮忙 */
export const offerNeighborHelp = (id, data) =>
  request({ url: `/neighbor-help/${id}/offer`, method: 'POST', data })
