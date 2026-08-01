/** 经营分析中心：图表色板、用量阈值、时间预设（统一口径，避免各页硬编码） */

export const ANALYTICS_COLORS = {
  primary: '#2563eb',
  success: '#067647',
  warning: '#b54708',
  danger: '#b42318',
  muted: '#98a2b3',
  series: ['#2563eb', '#0e9f6e', '#b54708', '#7c3aed', '#0891b2'] as const,
}

/** AI 使用率分档阈值 */
export const AI_USAGE_THRESHOLDS = {
  warn: 70,
  alert: 90,
  over: 100,
} as const

export type AiUsageBand = 'ok' | 'warn' | 'alert' | 'over'

export function aiUsageBand(pct: number): AiUsageBand {
  if (pct > AI_USAGE_THRESHOLDS.over) return 'over'
  if (pct > AI_USAGE_THRESHOLDS.alert) return 'alert'
  if (pct >= AI_USAGE_THRESHOLDS.warn) return 'warn'
  return 'ok'
}

export function aiUsageBandLabel(band: AiUsageBand) {
  return { ok: '正常', warn: '提醒', alert: '预警', over: '超额' }[band]
}

export function aiUsageColor(band: AiUsageBand) {
  return {
    ok: ANALYTICS_COLORS.primary,
    warn: ANALYTICS_COLORS.warning,
    alert: ANALYTICS_COLORS.danger,
    over: ANALYTICS_COLORS.danger,
  }[band]
}

export const DAYS_OPTIONS = [
  { label: '近 7 天', value: 7 },
  { label: '近 30 天', value: 30 },
  { label: '近 90 天', value: 90 },
] as const

export function formatMoney(v: unknown, digits = 2) {
  const n = Number(v)
  if (!Number.isFinite(n)) return '¥0.00'
  return `¥${n.toLocaleString('zh-CN', {
    minimumFractionDigits: digits,
    maximumFractionDigits: digits,
  })}`
}

export function formatNum(v: unknown) {
  const n = Number(v)
  if (!Number.isFinite(n)) return '0'
  return n.toLocaleString('zh-CN')
}

export function formatPct(v: unknown, digits = 1) {
  const n = Number(v)
  if (!Number.isFinite(n)) return '0%'
  return `${n.toFixed(digits)}%`
}

export function formatTime(v: unknown) {
  if (!v) return '--'
  return String(v).replace('T', ' ').slice(0, 19)
}

/** 从月度趋势计算环比（%） */
export function momChange(monthly: { paidAmount?: unknown }[]) {
  if (!monthly || monthly.length < 2) return null
  const a = Number(monthly[monthly.length - 2]?.paidAmount) || 0
  const b = Number(monthly[monthly.length - 1]?.paidAmount) || 0
  if (a === 0) return b === 0 ? 0 : null
  return Math.round(((b - a) / a) * 1000) / 10
}
