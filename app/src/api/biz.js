import { request } from '../utils/request'

/** status 字符串，或 { status, mine } */
export const listRepair = (statusOrOpts) => {
  const opts =
    statusOrOpts && typeof statusOrOpts === 'object'
      ? statusOrOpts
      : { status: statusOrOpts }
  const q = []
  if (opts.status) q.push(`status=${encodeURIComponent(opts.status)}`)
  if (opts.mine) q.push('mine=1')
  const qs = q.length ? `?${q.join('&')}` : ''
  return request({ url: `/repair/orders${qs}` })
}
export const getRepair = (id) => request({ url: `/repair/orders/${id}` })
export const createRepair = (data) => request({ url: '/repair/orders', method: 'POST', data })
export const handleRepair = (id, data) =>
  request({ url: `/repair/orders/${id}/handle`, method: 'PUT', data })
export const acceptRepair = (id) =>
  request({ url: `/repair/orders/${id}/accept`, method: 'PUT' })
export const assignRepair = (id, handlerId) =>
  request({ url: `/repair/orders/${id}/assign`, method: 'PUT', data: { handlerId } })
export const finishRepair = (id, data) =>
  request({ url: `/repair/orders/${id}/finish`, method: 'PUT', data })

export const listNotice = (manage) =>
  request({
    url: manage ? '/notice?manage=true' : '/notice',
  })
export const getNotice = (id) => request({ url: `/notice/${id}` })
export const createNotice = (data) => request({ url: '/notice', method: 'POST', data })
export const updateNotice = (id, data) =>
  request({ url: `/notice/${id}`, method: 'PUT', data })
export const deleteNotice = (id) =>
  request({ url: `/notice/${id}`, method: 'DELETE' })

export const listVisit = (status) =>
  request({ url: '/visit', data: status ? { status } : undefined })
export const createVisit = (data) => request({ url: '/visit', method: 'POST', data })
export const checkinVisit = (id) => request({ url: `/visit/${id}/checkin`, method: 'PUT' })
export const cancelVisit = (id) => request({ url: `/visit/${id}/cancel`, method: 'PUT' })
export const getVisitTodoSummary = () => request({ url: '/visit/todo-summary' })
/** 通行页（匿名）：打开记日志；仅校验通过才返回短时 qrPayload */
export const getVisitPassInfo = (token, phone) =>
  request({
    url: '/visit/pass/info',
    data: phone ? { token, phone } : { token },
    auth: false
  })
/** 短时二维码刷新 */
export const refreshVisitPassQr = (token, phone) =>
  request({
    url: '/visit/pass/qr',
    data: phone ? { token, phone } : { token },
    auth: false
  })
/** 扫码核验：优先 qrPayload */
export const verifyVisitPass = (data) =>
  request({
    url: '/visit/pass/verify',
    method: 'POST',
    data: typeof data === 'string' ? { token: data } : data || {},
    auth: false
  })

export const listActivity = () => request({ url: '/activity' })
export const createActivity = (data) => request({ url: '/activity', method: 'POST', data })
export const signupActivity = (id, data) =>
  request({ url: `/activity/${id}/signup`, method: 'POST', data: data || {} })
export const listSignups = (id) => request({ url: `/activity/${id}/signups` })
export const checkSignup = (signupId) =>
  request({ url: `/activity/signup/${signupId}/check`, method: 'PUT' })

export const listAppeal = (status) =>
  request({ url: '/appeal/tickets', data: status ? { status } : undefined })
export const createAppeal = (data) => request({ url: '/appeal', method: 'POST', data })
export const handleAppeal = (id, data) =>
  request({ url: `/appeal/${id}/handle`, method: 'PUT', data })

/** 物业费账单（网格员后端会 403） */
export const listFeeBills = () => request({ url: '/fee/bills' })
