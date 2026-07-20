import { request } from '../utils/request'

export const createOrder = (data) => request({ url: '/order/create', method: 'POST', data })
export const payOrder = (id) => request({ url: `/order/${id}/pay`, method: 'POST' })
export const listOrders = () => request({ url: '/order/list' })
