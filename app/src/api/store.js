import { request } from '../utils/request'

export const getStoreInfo = () => request({ url: '/store/info' })
export const listOpenShops = () => request({ url: '/store/shops' })
export const getOpenShop = (id) => request({ url: `/store/shops/${id}` })
export const listShopCategories = (shopId) =>
  request({ url: `/store/shops/${shopId}/categories` })
export const listShopProducts = (shopId, data) =>
  request({ url: `/store/shops/${shopId}/products`, data })
/** 店铺可领优惠券 */
export const listShopCoupons = (shopId) =>
  request({ url: `/store/shops/${shopId}/coupons` })
/** 我的可用优惠券（结算） */
export const listMyShopCoupons = (shopId, goodsAmount) =>
  request({
    url: `/store/shops/${shopId}/coupons/mine`,
    data: goodsAmount != null ? { goodsAmount } : undefined,
  })
/** 领取优惠券 */
export const claimShopCoupon = (shopId, couponId) =>
  request({ url: `/store/shops/${shopId}/coupons/${couponId}/claim`, method: 'POST', data: {} })
export const listStoreCategories = () => request({ url: '/store/categories' })
export const listStoreProducts = (data) => request({ url: '/store/products', data })
export const listCart = () => request({ url: '/store/cart' })
export const addCart = (productId, quantity = 1) =>
  request({ url: '/store/cart', method: 'POST', data: { productId, quantity } })
export const updateCart = (id, quantity) =>
  request({ url: `/store/cart/${id}`, method: 'PUT', data: { quantity } })
export const createStoreOrder = (data) =>
  request({ url: '/store/orders', method: 'POST', data })
export const payStoreOrder = (orderNo, action = 'success', channel = 'WECHAT') =>
  request({
    url: `/store/orders/${orderNo}/pay`,
    method: 'POST',
    data: { action, channel, payChannel: channel },
  })

/** 调起支付宝沙盒收银台 */
export function createStoreAlipayPay(orderNo, product = 'page') {
  const p = encodeURIComponent(product || 'page')
  return request({
    url: `/store/orders/${orderNo}/alipay?product=${p}`,
    method: 'POST',
    data: {},
  })
}

/** 向支付宝查单并同步落账 */
export function syncStoreAlipayPay(orderNo) {
  return request({
    url: `/store/orders/${orderNo}/alipay/sync`,
    method: 'POST',
    data: {},
  })
}

/** 在浏览器中提交支付宝返回的 HTML form */
export function openAlipayPayForm(payFormHtml) {
  if (!payFormHtml) throw { code: 400, msg: '缺少支付表单' }
  // #ifdef H5
  const box = document.createElement('div')
  box.style.display = 'none'
  box.innerHTML = payFormHtml
  document.body.appendChild(box)
  const form = box.querySelector('form')
  if (!form) {
    document.body.removeChild(box)
    throw { code: 500, msg: '支付宝表单解析失败' }
  }
  form.submit()
  // #endif
  // #ifndef H5
  throw { code: 400, msg: '当前端仅支持 H5 调起支付宝沙盒，请用浏览器打开' }
  // #endif
}

/** 演示环境：弹出模拟支付结果选项 */
export function pickMockPayAction() {
  return new Promise((resolve) => {
    uni.showActionSheet({
      itemList: ['模拟支付成功', '模拟支付处理中', '模拟支付失败', '取消支付'],
      success: (res) => {
        const map = ['success', 'pending', 'fail', 'cancel']
        resolve(map[res.tapIndex] || 'success')
      },
      fail: () => resolve('cancel'),
    })
  })
}
export const listStoreOrders = (status) =>
  request({ url: '/store/orders', data: status ? { status } : undefined })
export const getStoreOrder = (id) => request({ url: `/store/orders/${id}` })
export const cancelStoreOrder = (id, reason) =>
  request({ url: `/store/orders/${id}/cancel`, method: 'PUT', data: { reason } })
export const refundStoreOrder = (id, reason) =>
  request({ url: `/store/orders/${id}/refund`, method: 'POST', data: { reason } })
export const staffStoreAction = (id, action) =>
  request({ url: `/store/orders/${id}/action`, method: 'PUT', data: { action } })
export const getStoreTodo = () => request({ url: '/store/todo-summary' })
