/** 生态运营模块统一格式化 */

export function money(v: unknown) {
  const n = Number(v)
  if (!Number.isFinite(n)) return '--'
  return `¥${n.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`
}

export function moneyPlain(v: unknown) {
  const n = Number(v)
  if (!Number.isFinite(n)) return '--'
  return n.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

export function fmtTime(v: unknown) {
  if (!v) return '--'
  return String(v).replace('T', ' ').slice(0, 19)
}

export function maskAccount(v?: string | null) {
  if (!v) return '--'
  const s = String(v).replace(/\s/g, '')
  if (s.length < 8) return '****'
  return `${s.slice(0, 4)}****${s.slice(-4)}`
}

export function maskPhone(v?: string | null) {
  if (!v) return '--'
  const s = String(v)
  if (s.length < 7) return s
  return `${s.slice(0, 3)}****${s.slice(-4)}`
}
