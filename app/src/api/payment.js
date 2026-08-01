/**
 * 居民物业缴费 API（集中封装）
 * USE_MOCK=true：本地 Mock，便于联调 UI；后端就绪后改为 false 即可切真实接口。
 */
import { request } from '../utils/request'
import { resolveLoginTenantId } from '../utils/request'

export const BILL_STATUS = {
  UNPAID: 'UNPAID',
  OVERDUE: 'OVERDUE',
  PAYING: 'PAYING',
  PAID: 'PAID',
  FAILED: 'FAILED',
  CLOSED: 'CLOSED',
  REFUNDED: 'REFUNDED'
}

export const BILL_STATUS_LABEL = {
  UNPAID: '待缴',
  OVERDUE: '已逾期',
  PAYING: '支付中',
  PAID: '已缴',
  FAILED: '支付失败',
  CLOSED: '已关闭',
  REFUNDED: '已退款'
}

/** 已对接 /fee/resident/** 真实接口 */
const USE_MOCK = false

const MOCK_KEY_BILLS = 'mock_fee_bills_v1'
const MOCK_KEY_ORDERS = 'mock_fee_orders_v1'

function ctx() {
  return {
    userId: uni.getStorageSync('userId'),
    tenantId: resolveLoginTenantId(),
    houseId: uni.getStorageSync('currentHouseId'),
    communityId: resolveLoginTenantId()
  }
}

function withScope(extra = {}) {
  const c = ctx()
  return {
    userId: c.userId,
    tenantId: c.tenantId,
    communityId: c.communityId,
    houseId: c.houseId,
    ...extra
  }
}

function readJson(key, fallback) {
  try {
    const v = uni.getStorageSync(key)
    if (!v) return fallback
    return typeof v === 'string' ? JSON.parse(v) : v
  } catch (e) {
    return fallback
  }
}

function writeJson(key, val) {
  uni.setStorageSync(key, val)
}

function money(n) {
  return Math.round(Number(n || 0) * 100) / 100
}

function seedBillsIfNeed() {
  let all = readJson(MOCK_KEY_BILLS, null)
  if (all && Array.isArray(all) && all.length) return all
  const tid = resolveLoginTenantId() || 1
  const hid = uni.getStorageSync('currentHouseId') || 1
  const house = uni.getStorageSync('currentHouse') || {}
  const houseLabel =
    house.label ||
    `${house.communityName || '阳光花园社区'} · ${house.building || '1栋'}${house.unit || '1单元'}${house.roomNo || '101'}`
  all = [
    {
      id: 1001,
      billNo: 'BILL202607001',
      tenantId: tid,
      communityId: tid,
      houseId: hid,
      houseLabel,
      title: '2026年7月物业费',
      period: '2026-07',
      itemName: '物业服务费',
      chargeUnit: '阳光花园物业管理处',
      chargeBasis: '建筑面积 × 单价（98㎡ × 2.8元）',
      amount: 274.4,
      discount: 0,
      lateFee: 0,
      payable: 274.4,
      dueDate: '2026-07-25',
      status: BILL_STATUS.UNPAID,
      items: [{ name: '物业服务费', amount: 274.4 }]
    },
    {
      id: 1002,
      billNo: 'BILL202607002',
      tenantId: tid,
      communityId: tid,
      houseId: hid,
      houseLabel,
      title: '2026年7月停车费',
      period: '2026-07',
      itemName: '地下车位租金',
      chargeUnit: '阳光花园物业管理处',
      chargeBasis: '车位月租',
      amount: 200,
      discount: 20,
      lateFee: 0,
      payable: 180,
      dueDate: '2026-07-20',
      status: BILL_STATUS.OVERDUE,
      items: [
        { name: '车位租金', amount: 200 },
        { name: '早鸟优惠', amount: -20 }
      ]
    },
    {
      id: 1003,
      billNo: 'BILL202606001',
      tenantId: tid,
      communityId: tid,
      houseId: hid,
      houseLabel,
      title: '2026年6月物业费',
      period: '2026-06',
      itemName: '物业服务费',
      chargeUnit: '阳光花园物业管理处',
      chargeBasis: '建筑面积 × 单价',
      amount: 274.4,
      discount: 0,
      lateFee: 0,
      payable: 274.4,
      dueDate: '2026-06-25',
      status: BILL_STATUS.PAID,
      paidAt: '2026-06-18 10:22:00',
      items: [{ name: '物业服务费', amount: 274.4 }]
    },
    {
      id: 1004,
      billNo: 'BILL202607003',
      tenantId: tid,
      communityId: tid,
      houseId: hid,
      houseLabel,
      title: '公共维修分摊',
      period: '2026-Q2',
      itemName: '电梯维保分摊',
      chargeUnit: '阳光花园业委会/物业',
      chargeBasis: '楼栋分摊',
      amount: 56,
      discount: 0,
      lateFee: 0,
      payable: 56,
      dueDate: '2026-08-05',
      status: BILL_STATUS.UNPAID,
      items: [{ name: '电梯维保分摊', amount: 56 }]
    }
  ]
  writeJson(MOCK_KEY_BILLS, all)
  return all
}

function mockDelay(data, ms = 280) {
  return new Promise((resolve) => setTimeout(() => resolve(typeof data === 'function' ? data() : data), ms))
}

/** 汇总：待缴总额/笔数/最早截止 */
export function getFeeSummary(houseId) {
  const hid = houseId || ctx().houseId
  if (!USE_MOCK) {
    return request({ url: '/fee/resident/summary', data: withScope({ houseId: hid }) })
  }
  return mockDelay(() => {
    const bills = seedBillsIfNeed().filter(
      (b) => String(b.houseId) === String(hid) && (b.status === BILL_STATUS.UNPAID || b.status === BILL_STATUS.OVERDUE)
    )
    const total = money(bills.reduce((s, b) => s + Number(b.payable || 0), 0))
    const dues = bills.map((b) => b.dueDate).filter(Boolean).sort()
    return {
      unpaidAmount: total,
      unpaidCount: bills.length,
      earliestDueDate: dues[0] || null,
      overdueCount: bills.filter((b) => b.status === BILL_STATUS.OVERDUE).length
    }
  })
}

/** 账单列表 tab: unpaid|paid|all */
export function listResidentBills({ houseId, tab = 'unpaid' } = {}) {
  const hid = houseId || ctx().houseId
  if (!USE_MOCK) {
    return request({
      url: '/fee/resident/bills',
      data: withScope({ houseId: hid, tab })
    })
  }
  return mockDelay(() => {
    let list = seedBillsIfNeed().filter((b) => String(b.houseId) === String(hid))
    // 跟随当前房屋展示文案
    const house = uni.getStorageSync('currentHouse') || {}
    if (house.label || house.building) {
      const label =
        house.label ||
        `${house.communityName || ''} · ${house.building || ''}${house.unit || ''}${house.roomNo || ''}`
      list = list.map((b) => ({ ...b, houseId: Number(hid) || b.houseId, houseLabel: label }))
    }
    if (tab === 'unpaid') {
      list = list.filter((b) => b.status === BILL_STATUS.UNPAID || b.status === BILL_STATUS.OVERDUE)
    } else if (tab === 'paid') {
      list = list.filter((b) => b.status === BILL_STATUS.PAID)
    }
    return list.sort((a, b) => String(b.dueDate || '').localeCompare(String(a.dueDate || '')))
  })
}

export function getBillDetail(billId) {
  if (!USE_MOCK) {
    return request({ url: `/fee/resident/bills/${billId}`, data: withScope() })
  }
  return mockDelay(() => {
    const b = seedBillsIfNeed().find((x) => String(x.id) === String(billId))
    if (!b) throw { code: 404, msg: '账单不存在' }
    return { ...b }
  })
}

/** 取消支付中状态，账单恢复待缴 */
export function cancelPayingBill(billId) {
  return request({
    url: `/fee/resident/bills/${billId}/cancel-paying`,
    method: 'POST',
    data: withScope({})
  })
}

/**
 * 创建支付订单（金额以后端为准，前端勿传 amount）
 * @param {{ billIds: number[], payChannel: 'WECHAT'|'ALIPAY' }} payload
 */
export function createFeeOrder(payload) {
  const body = withScope({ billIds: payload.billIds, payChannel: payload.payChannel })
  if (!USE_MOCK) {
    return request({ url: '/fee/resident/orders', method: 'POST', data: body })
  }
  return mockDelay(() => {
    const all = seedBillsIfNeed()
    const bills = all.filter(
      (b) =>
        payload.billIds.map(String).includes(String(b.id)) &&
        (b.status === BILL_STATUS.UNPAID || b.status === BILL_STATUS.OVERDUE)
    )
    if (!bills.length) throw { code: 400, msg: '没有可支付的账单' }
    const amount = money(bills.reduce((s, b) => s + Number(b.payable || 0), 0))
    const orderId = `FO${Date.now()}`
    const order = {
      orderId,
      orderNo: orderId,
      tenantId: ctx().tenantId,
      communityId: ctx().communityId,
      userId: ctx().userId,
      houseId: ctx().houseId,
      houseLabel: bills[0].houseLabel,
      billIds: bills.map((b) => b.id),
      bills: bills.map((b) => ({
        id: b.id,
        billNo: b.billNo,
        title: b.title,
        payable: b.payable
      })),
      amount,
      payChannel: payload.payChannel || 'WECHAT',
      status: BILL_STATUS.PAYING,
      createTime: formatNow(),
      payTradeNo: null,
      paidAt: null
    }
    const orders = readJson(MOCK_KEY_ORDERS, [])
    orders.unshift(order)
    writeJson(MOCK_KEY_ORDERS, orders)
    // 账单置为支付中
    bills.forEach((b) => {
      const i = all.findIndex((x) => x.id === b.id)
      if (i >= 0) all[i] = { ...all[i], status: BILL_STATUS.PAYING }
    })
    writeJson(MOCK_KEY_BILLS, all)
    return order
  }, 400)
}

/** 调起支付宝沙盒收银台（返回 payForm） */
export function createAlipayPay(orderId, product = 'page') {
  const p = encodeURIComponent(product || 'page')
  return request({
    url: `/fee/resident/orders/${orderId}/alipay?product=${p}`,
    method: 'POST',
    data: withScope({})
  })
}

/** 向支付宝查单并同步落账 */
export function syncAlipayPay(orderId) {
  return request({
    url: `/fee/resident/orders/${orderId}/alipay/sync`,
    method: 'POST',
    data: withScope({})
  })
}

/** 查询支付宝配置是否就绪 */
export function getAlipayStatus() {
  return request({ url: '/fee/alipay/status' })
}

/** 在浏览器中提交支付宝返回的 HTML form，跳转沙盒收银台 */
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

/** 模拟调起支付（演示：随机成功/处理中） */
export function mockInvokePay(orderId, action = 'success') {
  if (!USE_MOCK) {
    return request({
      url: `/fee/resident/orders/${orderId}/pay`,
      method: 'POST',
      data: withScope({ action })
    })
  }
  return mockDelay(() => {
    const orders = readJson(MOCK_KEY_ORDERS, [])
    const idx = orders.findIndex((o) => o.orderId === orderId || o.orderNo === orderId)
    if (idx < 0) throw { code: 404, msg: '订单不存在' }
    const order = { ...orders[idx] }
    if (action === 'cancel') {
      order.status = BILL_STATUS.CLOSED
      revertBills(order.billIds, BILL_STATUS.UNPAID)
    } else if (action === 'fail') {
      order.status = BILL_STATUS.FAILED
      revertBills(order.billIds, BILL_STATUS.UNPAID)
    } else if (action === 'pending') {
      order.status = BILL_STATUS.PAYING
    } else {
      // success — 延迟到轮询里完成，这里先保持 PAYING 模拟异步
      order.status = BILL_STATUS.PAYING
      order._mockSucceedAt = Date.now() + 1200
    }
    orders[idx] = order
    writeJson(MOCK_KEY_ORDERS, orders)
    return { ...order }
  }, 350)
}

function revertBills(billIds, status) {
  const all = seedBillsIfNeed()
  billIds.forEach((id) => {
    const i = all.findIndex((x) => String(x.id) === String(id))
    if (i >= 0 && all[i].status === BILL_STATUS.PAYING) {
      // 逾期原状简单恢复：按 dueDate 判断
      const due = all[i].dueDate
      const overdue = due && due < formatDate(new Date())
      all[i] = {
        ...all[i],
        status: overdue ? BILL_STATUS.OVERDUE : status
      }
    }
  })
  writeJson(MOCK_KEY_BILLS, all)
}

/** 查询订单（支付中需轮询） */
export function getFeeOrder(orderId) {
  if (!USE_MOCK) {
    return request({ url: `/fee/resident/orders/${orderId}`, data: withScope() })
  }
  return mockDelay(() => {
    const orders = readJson(MOCK_KEY_ORDERS, [])
    const idx = orders.findIndex((o) => o.orderId === orderId || o.orderNo === orderId)
    if (idx < 0) throw { code: 404, msg: '订单不存在' }
    let order = { ...orders[idx] }
    if (order.status === BILL_STATUS.PAYING && order._mockSucceedAt && Date.now() >= order._mockSucceedAt) {
      order = markOrderPaid(order)
      orders[idx] = order
      writeJson(MOCK_KEY_ORDERS, orders)
    }
    return { ...order }
  }, 200)
}

function markOrderPaid(order) {
  const paid = {
    ...order,
    status: BILL_STATUS.PAID,
    payTradeNo: `TX${Date.now()}`,
    paidAt: formatNow(),
    _mockSucceedAt: undefined
  }
  const all = seedBillsIfNeed()
  ;(order.billIds || []).forEach((id) => {
    const i = all.findIndex((x) => String(x.id) === String(id))
    if (i >= 0) {
      all[i] = { ...all[i], status: BILL_STATUS.PAID, paidAt: paid.paidAt }
    }
  })
  writeJson(MOCK_KEY_BILLS, all)
  return paid
}

/** 缴费记录 */
export function listFeeOrders({ houseId, status, from, to } = {}) {
  const hid = houseId || ctx().houseId
  if (!USE_MOCK) {
    return request({
      url: '/fee/resident/orders',
      data: withScope({ houseId: hid, status, from, to })
    })
  }
  return mockDelay(() => {
    let list = readJson(MOCK_KEY_ORDERS, []).filter((o) => String(o.houseId) === String(hid))
    if (status) list = list.filter((o) => o.status === status)
    if (from) list = list.filter((o) => String(o.createTime || '') >= from)
    if (to) list = list.filter((o) => String(o.createTime || '') <= to + ' 23:59:59')
    return list
  })
}

/** 电子凭证 */
export function getFeeReceipt(orderId) {
  if (!USE_MOCK) {
    return request({ url: `/fee/resident/orders/${orderId}/receipt`, data: withScope() })
  }
  return getFeeOrder(orderId).then((order) => {
    if (order.status !== BILL_STATUS.PAID) throw { code: 400, msg: '订单未支付成功，暂无凭证' }
    return {
      receiptNo: `RC${order.orderNo}`,
      orderNo: order.orderNo,
      payTradeNo: order.payTradeNo,
      payChannel: order.payChannel,
      payChannelLabel: order.payChannel === 'ALIPAY' ? '支付宝' : '微信支付',
      amount: order.amount,
      paidAt: order.paidAt,
      houseLabel: order.houseLabel,
      bills: order.bills,
      payer: uni.getStorageSync('nickname') || '业主',
      communityName: (uni.getStorageSync('currentHouse') || {}).communityName || uni.getStorageSync('communityName')
    }
  })
}

/** 对账单有疑问 → 创建咨询工单（走报修/诉求，失败则本地提示） */
export async function createBillQuestion(billNo, content) {
  const data = withScope({
    title: `账单咨询 ${billNo}`,
    description: content || `对账单 ${billNo} 有疑问，请物业核实。`,
    facilityType: '账单咨询',
    billNo
  })
  try {
    return await request({
      url: '/repair/orders',
      method: 'POST',
      data: {
        title: data.title,
        description: data.description,
        facilityType: '账单咨询',
        damageLevel: '咨询',
        houseId: data.houseId,
        billNo
      },
      showError: false
    })
  } catch (e) {
    // Mock 兜底
    return mockDelay({ id: Date.now(), billNo, msg: '已提交咨询（演示）' })
  }
}

export function statusLabel(s) {
  return BILL_STATUS_LABEL[s] || s || '-'
}

export function formatMoney(n) {
  const v = Number(n || 0)
  return v.toFixed(2)
}

function formatNow() {
  const d = new Date()
  const p = (x) => String(x).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`
}

function formatDate(d) {
  const p = (x) => String(x).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`
}
