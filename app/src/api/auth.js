import { request } from '../utils/request'

export const login = (data) => request({ url: '/auth/login', method: 'POST', data, auth: false })
export const register = (data) => request({ url: '/auth/register', method: 'POST', data, auth: false })
export const getSms = (phone) => request({ url: `/auth/sms?phone=${phone}`, auth: false })
export const getProfile = () => request({ url: '/auth/profile' })
export const changePassword = (data) =>
  request({ url: '/auth/password', method: 'PUT', data })
export const getMessages = () => request({ url: '/auth/messages', showError: false })
export const readMessage = (id) => request({ url: `/auth/messages/${id}/read`, method: 'PUT', showError: false })
