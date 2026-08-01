import request from '@/utils/request'

/** 多商户商超 API（shop_id 隔离） */

export function listMallShops(params?: { keyword?: string; status?: number }) {
  return request.get('/store/mall/shops', { params }) as Promise<any[]>
}
export function createMallShop(data: Record<string, unknown>) {
  return request.post('/store/mall/shops', data)
}
export function getMallShop(id: number) {
  return request.get(`/store/mall/shops/${id}`)
}
export function updateMallShopStatus(id: number, status: number) {
  return request.put(`/store/mall/shops/${id}/status`, { status })
}
export function resetMallShopPassword(id: number, password: string) {
  return request.put(`/store/mall/shops/${id}/password`, { password })
}
export function bindMallShopAccount(
  id: number,
  data: { loginPhone: string; password?: string },
) {
  return request.put(`/store/mall/shops/${id}/account`, data)
}
export function getMyMallShop() {
  return request.get('/store/mall/mine')
}
export function updateMyMallShop(data: Record<string, unknown>) {
  return request.put('/store/mall/mine', data)
}
export function updateMyMallNotifySettings(data: {
  notifyNewOrder?: boolean
  notifyOrderCancel?: boolean
  notifyPlatformNotice?: boolean
  notifyStockLow?: boolean
}) {
  return request.put('/store/mall/mine/notify-settings', data)
}
export function getMyMallRevenue(params?: { startDate?: string; endDate?: string }) {
  return request.get('/store/mall/mine/revenue', { params }) as Promise<any>
}
export function listMallProducts(params?: {
  shopId?: number
  keyword?: string
  onSaleOnly?: boolean
  categoryId?: number
  status?: number
}) {
  return request.get('/store/mall/products', { params }) as Promise<any[]>
}
export function saveMallProduct(data: Record<string, unknown>) {
  return request.post('/store/mall/products', data)
}
export function batchMallProductStatus(ids: number[], status: number) {
  return request.put('/store/mall/products/batch-status', { ids, status }) as Promise<number>
}
export function batchMallProductStock(ids: number[], stock: number) {
  return request.put('/store/mall/products/batch-stock', { ids, stock }) as Promise<number>
}
export function deleteMallProduct(id: number) {
  return request.delete(`/store/mall/products/${id}`)
}
export function listMallCategories(shopId?: number) {
  return request.get('/store/mall/categories', { params: { shopId } }) as Promise<any[]>
}
export function saveMallCategory(data: Record<string, unknown>) {
  return request.post('/store/mall/categories', data)
}
export function listMallOrders(params?: {
  shopId?: number
  status?: string
  keyword?: string
}) {
  return request.get('/store/mall/orders', { params }) as Promise<any[]>
}
export function updateMallOrderStatus(id: number, action: string) {
  return request.put(`/store/mall/orders/${id}/status`, { action })
}

/** 商户优惠券 */
export function listMallCoupons(params?: { keyword?: string; status?: string }) {
  return request.get('/store/mall/coupons', { params }) as Promise<any[]>
}
export function saveMallCoupon(data: Record<string, unknown>) {
  return request.post('/store/mall/coupons', data)
}
export function offMallCoupon(id: number) {
  return request.put(`/store/mall/coupons/${id}/off`)
}
export function listMallCouponClaims(id: number) {
  return request.get(`/store/mall/coupons/${id}/claims`) as Promise<any[]>
}
