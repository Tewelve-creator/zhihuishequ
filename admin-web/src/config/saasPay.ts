/** SaaS 套餐订单支付方式（演示环境均可即时到账） */

export type SaasPayMethod = 'wechat' | 'alipay' | 'bank' | 'mock'

export const SAAS_PAY_METHODS: Array<{
  value: SaasPayMethod
  label: string
  desc: string
}> = [
  { value: 'wechat', label: '微信支付', desc: '演示环境即时到账' },
  { value: 'alipay', label: '支付宝支付', desc: '演示环境即时到账' },
  { value: 'bank', label: '银行卡支付', desc: '演示环境即时到账' },
  { value: 'mock', label: '模拟支付', desc: '一键成功，计入对账管理' },
]

export function saasPayMethodLabel(code?: string | null) {
  if (!code) return '—'
  const hit = SAAS_PAY_METHODS.find((m) => m.value === code)
  if (hit) return hit.label
  if (code === 'unknown') return '未标注'
  return code
}
