import { request } from '../utils/request'

export const login = (data) => request({ url: '/auth/login', method: 'POST', data, auth: false })
export const register = (data) => request({ url: '/auth/register', method: 'POST', data, auth: false })
export const getSms = (phone) => request({ url: `/auth/sms?phone=${phone}`, auth: false })
/** 登录/注册可选社区列表（开通租户后自动同步） */
export const listPublicTenants = () =>
  request({ url: '/auth/tenants/public', method: 'GET', auth: false, showError: false })
export const getProfile = () => request({ url: '/auth/profile' })
/** 修改昵称 / 头像：{ nickname?, avatar? } */
export const updateProfile = (data) =>
  request({ url: '/auth/profile/update', method: 'POST', data })
export const changePassword = (data) =>
  request({ url: '/auth/password', method: 'PUT', data })
export const getMessages = () => request({ url: '/auth/messages', showError: false })
export const readMessage = (id) => request({ url: `/auth/messages/${id}/read`, method: 'PUT', showError: false })
