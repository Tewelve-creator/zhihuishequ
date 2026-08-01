<template>
  <div class="viz-root">
    <div class="viz-scale" :style="scaleStyle">
      <!-- 顶栏 -->
      <header class="viz-header">
        <div class="hdr-side left">
          <button class="hdr-btn" type="button" title="返回后台" @click="exitScreen">⌂</button>
          <button class="hdr-btn" type="button" :disabled="loading" title="刷新" @click="load">↻</button>
          <div class="cta-bar">
            <button class="cta-btn primary" type="button" @click="goOnboard">开户向导</button>
            <button class="cta-btn warn" type="button" @click="goExpiring">
              到期风险 <em>{{ expiringCount }}</em>
            </button>
            <button class="cta-btn" type="button" @click="goPendingOrders">
              待支付 <em>{{ pendingCount }}</em>
            </button>
          </div>
          <div class="hdr-deco" />
        </div>
        <div class="hdr-title-wrap">
          <div class="hdr-wing left-wing" />
          <h1 class="hdr-title">智慧社区 SaaS 运营可视化指挥大屏</h1>
          <div class="hdr-wing right-wing" />
        </div>
        <div class="hdr-side right">
          <div class="hdr-deco" />
          <div class="hdr-clock">
            <div>{{ nowText }}</div>
            <div class="hdr-sub">实时数据</div>
          </div>
          <button class="hdr-btn" type="button" title="全屏" @click="toggleFs">⛶</button>
        </div>
      </header>

      <!-- KPI：与业务字段一一对应 -->
      <section class="kpi-row">
        <div
          v-for="k in kpis"
          :key="k.label"
          class="kpi-card tech-box"
          :class="k.tone"
        >
          <div class="kpi-label">{{ k.label }}</div>
          <div class="kpi-value digi">{{ k.value }}</div>
          <div class="kpi-glow" />
        </div>
      </section>

      <!-- 中部：社区分布 + 营收趋势 + 活跃 TOP -->
      <section class="mid-row">
        <div class="panel tech-box map-panel">
          <div class="panel-title">社区租户分布</div>
          <div class="map-stage">
            <v-chart
              v-if="chinaReady"
              class="china-map"
              :option="chinaMapOpt"
              autoresize
            />
            <div v-else class="map-empty">中国地图加载中…</div>
            <div class="map-hint">滚轮缩放 · 拖拽平移 · 缩小可看全国</div>
          </div>
          <div class="map-legend">
            <span><i class="lg ok" />正常运营</span>
            <span><i class="lg alert" />到期/降级预警</span>
            <span class="map-count">在管社区 {{ tenantMap.length }}</span>
          </div>
        </div>

        <div class="mid-right">
          <div class="panel tech-box grow">
            <div class="panel-title">近月营收趋势</div>
            <v-chart class="chart" :option="trendOpt" autoresize />
          </div>
          <div class="panel tech-box grow">
            <div class="panel-title">租户活跃 TOP10（报修量）</div>
            <v-chart class="chart" :option="activityOpt" autoresize />
          </div>
        </div>
      </section>

      <!-- 底部四栏 -->
      <section class="bot-row">
        <div class="panel tech-box">
          <div class="panel-title">套餐分布</div>
          <v-chart class="chart-sm" :option="packageOpt" autoresize />
        </div>
        <div class="panel tech-box">
          <div class="panel-title">订单类型结构</div>
          <v-chart class="chart-sm" :option="typeOpt" autoresize />
        </div>
        <div class="panel tech-box">
          <div class="panel-title">待处理工单汇总</div>
          <div class="wo-list">
            <div v-for="w in workOrders" :key="w.name" class="wo-row">
              <span class="wo-name">{{ w.name }}</span>
              <div class="wo-bar-wrap">
                <div class="wo-bar" :style="{ width: woWidth(w.cnt) }" />
              </div>
              <span class="wo-cnt digi">{{ w.cnt }}</span>
            </div>
            <div v-if="!workOrders.length" class="empty-tip">暂无工单数据</div>
          </div>
        </div>
        <div class="panel tech-box">
          <div class="panel-title">
            即将到期租户预警
            <button class="panel-link" type="button" @click="goExpiring">全部 →</button>
          </div>
          <div class="exp-list">
            <div
              v-for="e in expiringList"
              :key="e.id"
              class="exp-row clickable"
              :class="{ urgent: e.daysLeft != null && e.daysLeft <= 7 }"
              title="点击去续费"
              @click="goRenew(e)"
            >
              <div class="exp-main">
                <span class="exp-name">{{ e.name }}</span>
                <span class="exp-sub">{{ expText(e) }} · 点此续费</span>
              </div>
              <span class="exp-timer digi">{{ countdown(e) }}</span>
            </div>
            <div v-if="!expiringList.length" class="empty-tip">30 日内暂无到期租户</div>
          </div>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import * as echarts from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { LineChart, PieChart, BarChart, EffectScatterChart, ScatterChart } from 'echarts/charts'
import {
  GridComponent,
  TooltipComponent,
  LegendComponent,
  GeoComponent,
} from 'echarts/components'
import VChart from 'vue-echarts'
import dayjs from 'dayjs'
import {
  saasDashboard,
  getTenantStats,
  financeSummary,
  listExpiringTenants,
  pageTenants,
  pageOrders,
} from '@/api/saas'

echarts.use([
  CanvasRenderer,
  LineChart,
  PieChart,
  BarChart,
  EffectScatterChart,
  ScatterChart,
  GridComponent,
  TooltipComponent,
  LegendComponent,
  GeoComponent,
])

const TYPE_LABEL: Record<string, string> = { new: '新购', renew: '续费', upgrade: '升级' }
const CYAN = '#22d3ee'
const CYAN2 = '#06b6d4'
const GOLD = '#fbbf24'
const RED = '#f43f5e'

const router = useRouter()
const loading = ref(false)
const nowText = ref(dayjs().format('YYYY-MM-DD HH:mm:ss'))
const scale = ref(1)
const dash = ref<Record<string, any>>({})
const stats = ref<Record<string, any>>({})
const finance = ref<Record<string, any>>({})
const expiringFallback = ref<any[]>([])
const tenantsFallback = ref<any[]>([])
const pendingCount = ref(0)
let clockTimer: number | undefined
let tickTimer: number | undefined

const packageDist = computed(() => dash.value.packageDist || [])
const tenantActivity = computed(() => dash.value.tenantActivity || [])
const orderByType = computed(() => dash.value.orderByType || finance.value.byType || [])
const monthlyTrend = computed(() => dash.value.monthlyTrend || finance.value.monthlyTrend || [])
const workOrders = computed(() =>
  (dash.value.workOrderSummary || []).map((r: any) => ({
    name: r.name,
    cnt: Number(r.cnt) || 0,
  })),
)
const expiringList = computed(() => {
  const fromDash = dash.value.expiringList || []
  if (fromDash.length) return fromDash
  return expiringFallback.value
})
const expiringCount = computed(() => expiringList.value.length)
const tenantMap = computed(() => {
  const fromDash = dash.value.tenantMap || []
  if (fromDash.length) return fromDash
  // 兜底：租户列表 / 活跃列表
  if (tenantsFallback.value.length) {
    return tenantsFallback.value.map((t: any) => ({
      id: t.id,
      name: t.name,
      status: t.status,
      expireTime: t.expireTime,
      daysLeft: t.expireTime ? dayjs(t.expireTime).diff(dayjs(), 'day') : null,
    }))
  }
  return (tenantActivity.value || []).map((t: any, i: number) => ({
    id: i + 1,
    name: t.name,
    status: 'active',
    daysLeft: null,
  }))
})

const scaleStyle = computed(() => ({
  transform: `scale(${scale.value})`,
  transformOrigin: 'left top',
}))

/** KPI 与接口字段对照 */
const kpis = computed(() => {
  const d = dash.value
  const s = stats.value
  const f = finance.value
  const paid = Number(f.paidAmount ?? d.orderPaidAmount) || 0
  const refund = Number(f.refundAmount ?? d.orderRefundAmount) || 0
  const degraded = Number(s.degradedTenants ?? 0)
  const frozen = Number(s.frozenTenants ?? 0)
  return [
    { label: '租户总数', value: s.totalTenants ?? d.tenantTotal ?? 0, tone: '' },
    { label: '活跃租户', value: s.activeTenants ?? d.tenantActive ?? 0, tone: 'ok' },
    { label: '到期/降级', value: (degraded + frozen) || (d.tenantExpired ?? 0), tone: 'warn' },
    { label: '30日内到期', value: s.expiringIn30Days ?? 0, tone: 'warn' },
    { label: '已付金额', value: fmtMoney(paid), tone: 'ok' },
    { label: '已付笔数', value: f.paidCount ?? d.orderCount ?? 0, tone: '' },
    { label: '退款金额', value: fmtMoney(refund), tone: 'danger' },
    { label: '退款笔数', value: f.refundCount ?? d.orderRefundCount ?? 0, tone: 'danger' },
    { label: '净营收', value: fmtMoney(paid - refund), tone: 'ok' },
  ]
})

const chinaReady = ref(false)

/**
 * 社区演示坐标（经度, 纬度）
 * 阳光花园 / 星河湾落在京津冀，默认缩放到该区域即可同时看到两点
 */
const TENANT_COORDS: Record<string, [number, number]> = {
  阳光花园社区: [115.4829, 38.8141],
  阳光花园: [115.4829, 38.8141],
  星河湾社区: [116.35, 39.12],
  星河湾: [116.35, 39.12],
}
const ID_COORDS: Record<number, [number, number]> = {
  1: [115.4829, 38.8141],
  2: [116.35, 39.12],
}
/** 新增租户兜底城市（轮换） */
const FALLBACK_COORDS: [number, number][] = [
  [116.4074, 39.9042], // 北京
  [121.4737, 31.2304], // 上海
  [113.2644, 23.1291], // 广州
  [104.0665, 30.5723], // 成都
  [114.0579, 22.5431], // 深圳
  [118.7969, 32.0603], // 南京
  [120.1551, 30.2741], // 杭州
  [108.9398, 34.3416], // 西安
]

function resolveCoord(t: any, index: number): [number, number] {
  const id = Number(t.id)
  if (Number.isFinite(id) && ID_COORDS[id]) return ID_COORDS[id]
  const name = String(t.name || '')
  if (TENANT_COORDS[name]) return TENANT_COORDS[name]
  for (const [k, v] of Object.entries(TENANT_COORDS)) {
    if (name.includes(k) || k.includes(name.replace(/社区$/, ''))) return v
  }
  return FALLBACK_COORDS[index % FALLBACK_COORDS.length]
}

/** 真实租户点位（不再灌演示假数据） */
const mapPoints = computed(() => {
  return (tenantMap.value as any[]).map((t, i) => {
    const [lng, lat] = resolveCoord(t, i)
    const days = t.daysLeft == null ? null : Number(t.daysLeft)
    const alert =
      t.status === 'degraded' ||
      t.status === 'frozen' ||
      (days != null && days <= 20)
    const name = String(t.name || '社区')
    return {
      id: t.id ?? i,
      name,
      lng,
      lat,
      alert,
      daysLeft: days,
      status: t.status,
      value: [lng, lat, 1] as [number, number, number],
    }
  })
})

/** 默认视野：聚焦社区所在区域；缩小可看全国（锁定后不打断用户缩放） */
const geoCenter = ref<[number, number]>([104.5, 36.0])
const geoZoom = ref(1.15)
const viewLocked = ref(false)
/** true 时把 center/zoom 写入 option；首帧后关掉，避免刷新点位时重置漫游 */
const applyGeoView = ref(true)

function calcMapView(pts: { lng: number; lat: number }[]) {
  if (!pts.length) {
    return { center: [104.5, 36.0] as [number, number], zoom: 1.15 }
  }
  const lngs = pts.map((p) => p.lng)
  const lats = pts.map((p) => p.lat)
  const minLng = Math.min(...lngs)
  const maxLng = Math.max(...lngs)
  const minLat = Math.min(...lats)
  const maxLat = Math.max(...lats)
  const center: [number, number] = [(minLng + maxLng) / 2, (minLat + maxLat) / 2]
  const span = Math.max(maxLng - minLng, maxLat - minLat, 0.3)
  let zoom = 1.15
  // 阳光花园≈保定、星河湾≈廊坊，跨度约 1° → zoom≈5.8，能同时看到两点
  if (span < 0.5) zoom = 7.2
  else if (span < 1.5) zoom = 5.8
  else if (span < 4) zoom = 4.2
  else if (span < 10) zoom = 2.6
  else zoom = 1.4
  return { center, zoom }
}

watch(
  mapPoints,
  (pts) => {
    if (viewLocked.value || !pts.length) return
    const v = calcMapView(pts)
    geoCenter.value = v.center
    geoZoom.value = v.zoom
    viewLocked.value = true
  },
  { immediate: true },
)

/** 地图组件挂载后再写入一次视野，再关掉，避免被后续 setOption 重置 */
watch([chinaReady, viewLocked], ([ready, locked]) => {
  if (!ready || !locked) return
  applyGeoView.value = true
  window.setTimeout(() => {
    applyGeoView.value = false
  }, 600)
})

const chinaMapOpt = computed(() => {
  const pts = mapPoints.value
  const okData = pts
    .filter((p) => !p.alert)
    .map((p) => ({
      name: p.name,
      value: p.value,
      daysLeft: p.daysLeft,
      status: p.status,
    }))
  const alertData = pts
    .filter((p) => p.alert)
    .map((p) => ({
      name: p.name,
      value: p.value,
      daysLeft: p.daysLeft,
      status: p.status,
    }))

  const geo: Record<string, any> = {
    map: 'china',
    roam: true,
    scaleLimit: { min: 1, max: 12 },
    label: { show: false },
    itemStyle: {
      areaColor: '#0a2740',
      borderColor: '#22d3ee',
      borderWidth: 1,
      shadowColor: 'rgba(34,211,238,0.35)',
      shadowBlur: 12,
    },
    emphasis: {
      label: { show: true, color: '#e0f2fe', fontSize: 11 },
      itemStyle: {
        areaColor: '#0e4a5c',
        borderColor: '#67e8f9',
        borderWidth: 1.4,
      },
    },
  }
  if (applyGeoView.value) {
    geo.center = geoCenter.value
    geo.zoom = geoZoom.value
  }

  return {
    backgroundColor: 'transparent',
    tooltip: {
      backgroundColor: 'rgba(2,12,30,.92)',
      borderColor: CYAN,
      textStyle: { color: '#e0f2fe' },
      trigger: 'item',
      formatter: (p: any) => {
        if (p.seriesType !== 'effectScatter' && p.seriesType !== 'scatter') return p.name || ''
        const d = p.data || {}
        const days =
          d.daysLeft == null ? '' : `<br/>剩余 ${d.daysLeft} 天`
        const st = d.status ? `<br/>状态：${statusLabel(d.status)}` : ''
        return `<b>${d.name || p.name}</b>${st}${days}`
      },
    },
    geo,
    series: [
      {
        name: '正常社区',
        type: 'effectScatter',
        coordinateSystem: 'geo',
        data: okData,
        symbolSize: 14,
        showEffectOn: 'render',
        rippleEffect: { brushType: 'stroke', scale: 3.2, period: 3.5 },
        label: {
          show: true,
          formatter: '{b}',
          position: 'right',
          color: '#f8fafc',
          fontSize: 12,
          fontWeight: 600,
          textBorderColor: 'rgba(2,12,24,0.85)',
          textBorderWidth: 2,
        },
        itemStyle: {
          color: '#67e8f9',
          shadowBlur: 14,
          shadowColor: '#22d3ee',
        },
        zlevel: 2,
      },
      {
        name: '预警社区',
        type: 'effectScatter',
        coordinateSystem: 'geo',
        data: alertData,
        symbolSize: 18,
        showEffectOn: 'render',
        rippleEffect: { brushType: 'stroke', scale: 3.8, period: 2.4 },
        label: {
          show: true,
          formatter: '{b}',
          position: 'right',
          color: '#fff',
          fontSize: 12,
          fontWeight: 600,
          backgroundColor: 'rgba(190,24,60,0.88)',
          padding: [3, 8],
          borderRadius: 4,
          borderColor: 'rgba(251,113,133,0.85)',
          borderWidth: 1,
        },
        itemStyle: {
          color: '#fb7185',
          shadowBlur: 18,
          shadowColor: '#f43f5e',
        },
        zlevel: 3,
      },
    ],
  }
})

async function ensureChinaMap() {
  if (chinaReady.value) return
  try {
    const res = await fetch('/geo/china.json')
    const geoJson = await res.json()
    echarts.registerMap('china', geoJson as any)
    chinaReady.value = true
  } catch (e) {
    console.error('加载中国地图失败', e)
  }
}

const chartTip = {
  backgroundColor: 'rgba(2,12,30,.92)',
  borderColor: CYAN,
  textStyle: { color: '#e0f2fe' },
}

const packageOpt = computed(() => ({
  tooltip: { ...chartTip, trigger: 'item' },
  legend: {
    bottom: 0,
    textStyle: { color: '#7dd3fc', fontSize: 11 },
    type: 'scroll',
  },
  series: [
    {
      type: 'pie',
      radius: ['40%', '68%'],
      center: ['50%', '44%'],
      data: packageDist.value.map((r: any) => ({
        name: r.name || '未知',
        value: Number(r.value) || 0,
      })),
      label: { color: '#bae6fd', fontSize: 11 },
      itemStyle: {
        borderColor: '#020b1a',
        borderWidth: 2,
        shadowBlur: 12,
        shadowColor: 'rgba(34,211,238,.35)',
      },
      color: [CYAN, CYAN2, '#38bdf8', '#0ea5e9', GOLD, '#67e8f9'],
    },
  ],
}))

const typeOpt = computed(() => ({
  tooltip: { ...chartTip, trigger: 'item' },
  legend: { bottom: 0, textStyle: { color: '#7dd3fc', fontSize: 11 } },
  series: [
    {
      type: 'pie',
      radius: ['28%', '62%'],
      center: ['50%', '44%'],
      roseType: 'radius',
      data: orderByType.value.map((r: any) => ({
        name: TYPE_LABEL[r.type] || r.type || '其他',
        value: Number(r.amount) || Number(r.cnt) || 0,
      })),
      label: { color: '#bae6fd', fontSize: 11 },
      color: [CYAN, GOLD, '#38bdf8'],
      itemStyle: { borderColor: '#020b1a', borderWidth: 2 },
    },
  ],
}))

const trendOpt = computed(() => {
  const months = monthlyTrend.value.map((r: any) => r.month)
  const amounts = monthlyTrend.value.map((r: any) => Number(r.paidAmount) || 0)
  const counts = monthlyTrend.value.map((r: any) => Number(r.paidCount) || 0)
  return {
    tooltip: { ...chartTip, trigger: 'axis' },
    legend: { data: ['金额', '笔数'], textStyle: { color: '#7dd3fc' }, top: 0 },
    grid: { left: 48, right: 48, top: 36, bottom: 28 },
    xAxis: {
      type: 'category',
      data: months,
      axisLabel: { color: '#7dd3fc', fontSize: 11 },
      axisLine: { lineStyle: { color: '#0e7490' } },
    },
    yAxis: [
      {
        type: 'value',
        axisLabel: { color: '#7dd3fc' },
        splitLine: { lineStyle: { color: 'rgba(14,116,144,.25)' } },
      },
      {
        type: 'value',
        splitLine: { show: false },
        axisLabel: { color: '#67e8f9' },
      },
    ],
    series: [
      {
        name: '金额',
        type: 'line',
        smooth: true,
        data: amounts,
        symbol: 'circle',
        symbolSize: 7,
        lineStyle: { width: 3, color: CYAN },
        areaStyle: {
          color: {
            type: 'linear',
            x: 0, y: 0, x2: 0, y2: 1,
            colorStops: [
              { offset: 0, color: 'rgba(34,211,238,.45)' },
              { offset: 1, color: 'rgba(34,211,238,.02)' },
            ],
          },
        },
        itemStyle: { color: CYAN },
      },
      {
        name: '笔数',
        type: 'bar',
        yAxisIndex: 1,
        data: counts,
        barMaxWidth: 16,
        itemStyle: {
          color: {
            type: 'linear',
            x: 0, y: 0, x2: 0, y2: 1,
            colorStops: [
              { offset: 0, color: '#67e8f9' },
              { offset: 1, color: 'rgba(6,182,212,.2)' },
            ],
          },
        },
      },
    ],
  }
})

const activityOpt = computed(() => {
  const rows = [...tenantActivity.value].reverse()
  return {
    tooltip: { ...chartTip, trigger: 'axis' },
    grid: { left: 88, right: 24, top: 12, bottom: 20 },
    xAxis: {
      type: 'value',
      axisLabel: { color: '#7dd3fc' },
      splitLine: { lineStyle: { color: 'rgba(14,116,144,.25)' } },
    },
    yAxis: {
      type: 'category',
      data: rows.map((r: any) => r.name),
      axisLabel: { color: '#bae6fd', fontSize: 11, width: 72, overflow: 'truncate' },
    },
    series: [
      {
        type: 'bar',
        data: rows.map((r: any) => Number(r.repair_cnt) || 0),
        barMaxWidth: 14,
        itemStyle: {
          borderRadius: [0, 6, 6, 0],
          color: {
            type: 'linear',
            x: 0, y: 0, x2: 1, y2: 0,
            colorStops: [
              { offset: 0, color: '#0e7490' },
              { offset: 1, color: CYAN },
            ],
          },
        },
      },
    ],
  }
})

const maxWo = computed(() => Math.max(...workOrders.value.map((w) => w.cnt), 1))

function woWidth(cnt: number) {
  return `${Math.round((cnt / maxWo.value) * 100)}%`
}

function fmtMoney(v: unknown) {
  const n = Number(v)
  if (Number.isNaN(n)) return '0'
  if (Math.abs(n) >= 10000) {
    return `${(n / 10000).toLocaleString('zh-CN', { maximumFractionDigits: 1 })}万`
  }
  return n.toLocaleString('zh-CN', { maximumFractionDigits: 2 })
}

function statusLabel(s: string) {
  return ({ active: '正常', degraded: '降级', frozen: '冻结', disabled: '停用' } as Record<string, string>)[s] || s || '-'
}

function expText(e: any) {
  const d = e.daysLeft
  if (d == null) return e.expireTime || ''
  if (d < 0) return `已过期 ${Math.abs(d)} 天`
  return `${d} 天后到期`
}

function countdown(e: any) {
  if (!e.expireTime) return '--:--:--'
  const end = dayjs(e.expireTime)
  const sec = Math.max(0, end.diff(dayjs(), 'second'))
  const h = String(Math.floor(sec / 3600)).padStart(2, '0')
  const m = String(Math.floor((sec % 3600) / 60)).padStart(2, '0')
  const s = String(sec % 60).padStart(2, '0')
  return `${h}:${m}:${s}`
}

function fitScale() {
  const w = window.innerWidth
  const h = window.innerHeight
  scale.value = Math.min(w / 1920, h / 1080)
}

function toggleFs() {
  if (!document.fullscreenElement) {
    document.documentElement.requestFullscreen?.()
  } else {
    document.exitFullscreen?.()
  }
}

function exitScreen() {
  router.push('/admin/saas/onboard')
}

function goOnboard() {
  router.push('/admin/saas/onboard')
}
function goExpiring() {
  router.push('/admin/saas/monitor/expiring')
}
function goPendingOrders() {
  router.push({ path: '/admin/saas/orders', query: { status: 'pending' } })
}
function goRenew(e: any) {
  if (!e?.id) {
    goExpiring()
    return
  }
  router.push({
    path: '/admin/saas/orders',
    query: { tenantId: String(e.id), orderType: 'renew', action: 'renew' },
  })
}

async function load() {
  loading.value = true
  try {
    const [d, s, f, exp, tenants, pending] = await Promise.allSettled([
      saasDashboard(),
      getTenantStats(),
      financeSummary(),
      listExpiringTenants(30),
      pageTenants({ page: 1, size: 100 }),
      pageOrders({ page: 1, size: 1, status: 'pending' }),
    ])
    dash.value = d.status === 'fulfilled' ? d.value || {} : {}
    stats.value = s.status === 'fulfilled' ? s.value || {} : {}
    finance.value = f.status === 'fulfilled' ? f.value || {} : {}
    expiringFallback.value = exp.status === 'fulfilled' ? exp.value || [] : []
    tenantsFallback.value =
      tenants.status === 'fulfilled' ? tenants.value?.records || [] : []
    pendingCount.value =
      pending.status === 'fulfilled' ? Number(pending.value?.total || 0) : 0
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  fitScale()
  window.addEventListener('resize', fitScale)
  clockTimer = window.setInterval(() => {
    nowText.value = dayjs().format('YYYY-MM-DD HH:mm:ss')
  }, 1000)
  tickTimer = window.setInterval(() => {
    /* 触发倒计时重渲染 */
    nowText.value = dayjs().format('YYYY-MM-DD HH:mm:ss')
  }, 1000)
  ensureChinaMap()
  load()
})

onUnmounted(() => {
  window.removeEventListener('resize', fitScale)
  if (clockTimer) clearInterval(clockTimer)
  if (tickTimer) clearInterval(tickTimer)
})
</script>

<style scoped>
.viz-root {
  width: 100vw;
  height: 100vh;
  overflow: hidden;
  background:
    radial-gradient(ellipse at 50% 0%, rgba(8, 47, 73, 0.55), transparent 55%),
    linear-gradient(180deg, #020617 0%, #020b1a 40%, #01060f 100%);
  color: #e0f2fe;
  font-family: 'DIN Alternate', 'Segoe UI', 'PingFang SC', sans-serif;
}
.viz-scale {
  width: 1920px;
  height: 1080px;
  padding: 12px 20px 16px;
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.viz-header {
  display: grid;
  grid-template-columns: 1fr auto 1fr;
  align-items: center;
  height: 72px;
  position: relative;
}
.hdr-side {
  display: flex;
  align-items: center;
  gap: 10px;
}
.hdr-side.right { justify-content: flex-end; }
.hdr-btn {
  width: 36px;
  height: 36px;
  border: 1px solid rgba(34, 211, 238, 0.45);
  background: rgba(8, 47, 73, 0.6);
  color: #67e8f9;
  border-radius: 6px;
  cursor: pointer;
  font-size: 16px;
}
.hdr-btn:disabled { opacity: 0.5; cursor: wait; }
.cta-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-left: 4px;
}
.cta-btn {
  height: 32px;
  padding: 0 12px;
  border: 1px solid rgba(34, 211, 238, 0.4);
  background: rgba(8, 47, 73, 0.75);
  color: #a5f3fc;
  border-radius: 6px;
  cursor: pointer;
  font-size: 12px;
  white-space: nowrap;
}
.cta-btn em {
  font-style: normal;
  margin-left: 4px;
  color: #fbbf24;
  font-weight: 700;
}
.cta-btn.primary {
  border-color: rgba(34, 211, 238, 0.75);
  background: linear-gradient(180deg, rgba(8, 145, 178, 0.55), rgba(8, 47, 73, 0.85));
  color: #ecfeff;
}
.cta-btn.warn {
  border-color: rgba(251, 191, 36, 0.55);
}
.cta-btn:hover {
  filter: brightness(1.15);
}
.hdr-deco {
  width: 120px;
  height: 2px;
  background: linear-gradient(90deg, transparent, #22d3ee);
}
.hdr-side.right .hdr-deco {
  background: linear-gradient(90deg, #22d3ee, transparent);
}
.hdr-title-wrap {
  display: flex;
  align-items: center;
  gap: 16px;
}
.hdr-title {
  margin: 0;
  font-size: 34px;
  letter-spacing: 0.12em;
  font-weight: 700;
  background: linear-gradient(180deg, #fff 20%, #67e8f9 100%);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
  text-shadow: 0 0 24px rgba(34, 211, 238, 0.35);
}
.hdr-wing {
  width: 88px;
  height: 18px;
  background: linear-gradient(90deg, transparent, #22d3ee, transparent);
  clip-path: polygon(0 50%, 18% 0, 82% 0, 100% 50%, 82% 100%, 18% 100%);
  opacity: 0.85;
}
.hdr-clock {
  text-align: right;
  font-size: 16px;
  color: #67e8f9;
  font-variant-numeric: tabular-nums;
}
.hdr-sub { font-size: 11px; color: #64748b; margin-top: 2px; }

.tech-box {
  position: relative;
  background: linear-gradient(180deg, rgba(8, 47, 73, 0.45), rgba(2, 11, 26, 0.72));
  border: 1px solid rgba(34, 211, 238, 0.28);
  box-shadow:
    inset 0 0 24px rgba(34, 211, 238, 0.06),
    0 0 18px rgba(6, 182, 212, 0.08);
}
.tech-box::before,
.tech-box::after {
  content: '';
  position: absolute;
  width: 12px;
  height: 12px;
  border-color: #22d3ee;
  border-style: solid;
  pointer-events: none;
}
.tech-box::before {
  left: -1px; top: -1px;
  border-width: 2px 0 0 2px;
}
.tech-box::after {
  right: -1px; bottom: -1px;
  border-width: 0 2px 2px 0;
}

.kpi-row {
  display: grid;
  grid-template-columns: repeat(9, 1fr);
  gap: 10px;
}
.kpi-card {
  padding: 12px 14px 14px;
  min-height: 78px;
  overflow: hidden;
}
.kpi-card.ok { border-color: rgba(52, 211, 153, 0.45); }
.kpi-card.warn { border-color: rgba(251, 191, 36, 0.5); }
.kpi-card.danger {
  border-color: rgba(244, 63, 94, 0.65);
  box-shadow: 0 0 18px rgba(244, 63, 94, 0.25), inset 0 0 20px rgba(244, 63, 94, 0.08);
}
.kpi-label {
  font-size: 13px;
  color: #7dd3fc;
  letter-spacing: 0.04em;
}
.kpi-value {
  margin-top: 8px;
  font-size: 26px;
  font-weight: 700;
  color: #ecfeff;
  text-shadow: 0 0 12px rgba(34, 211, 238, 0.45);
}
.kpi-card.danger .kpi-value {
  color: #fda4af;
  text-shadow: 0 0 12px rgba(244, 63, 94, 0.5);
}
.kpi-glow {
  position: absolute;
  right: -20px;
  bottom: -24px;
  width: 70px;
  height: 70px;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(34, 211, 238, 0.25), transparent 70%);
  pointer-events: none;
}
.digi { font-variant-numeric: tabular-nums; letter-spacing: 0.02em; }

.mid-row {
  flex: 1.35;
  display: grid;
  grid-template-columns: 1.15fr 1fr;
  gap: 12px;
  min-height: 0;
}
.mid-right {
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-height: 0;
}
.grow { flex: 1; min-height: 0; display: flex; flex-direction: column; }
.panel {
  padding: 10px 12px 8px;
  display: flex;
  flex-direction: column;
  min-height: 0;
}
.panel-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 15px;
  font-weight: 600;
  color: #a5f3fc;
  letter-spacing: 0.08em;
  padding: 2px 0 8px 8px;
  border-left: 3px solid #22d3ee;
  margin-bottom: 4px;
}
.panel-link {
  border: none;
  background: transparent;
  color: #67e8f9;
  font-size: 12px;
  cursor: pointer;
  letter-spacing: 0;
  font-weight: 500;
}
.panel-link:hover { color: #fff; }
.chart, .chart-sm { flex: 1; width: 100%; min-height: 160px; }

.map-panel { min-height: 0; }
.map-stage {
  position: relative;
  flex: 1;
  margin: 2px 6px 4px;
  border-radius: 8px;
  overflow: hidden;
  background: radial-gradient(ellipse 70% 55% at 50% 45%, #041a28 0%, #020b14 55%, #01060d 100%);
}
.china-map {
  width: 100%;
  height: 100%;
  min-height: 280px;
}
.map-hint {
  position: absolute;
  left: 10px;
  bottom: 8px;
  z-index: 2;
  font-size: 11px;
  color: rgba(125, 211, 252, 0.65);
  pointer-events: none;
  letter-spacing: 0.04em;
}
.map-empty {
  position: absolute;
  inset: 0;
  display: grid;
  place-items: center;
  color: #64748b;
  font-size: 14px;
  z-index: 3;
}
.map-legend {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 4px 10px 2px;
  font-size: 12px;
  color: #7dd3fc;
}
.map-legend i.lg {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  margin-right: 6px;
}
.map-legend i.ok { background: #22d3ee; box-shadow: 0 0 6px #22d3ee; }
.map-legend i.alert { background: #f43f5e; box-shadow: 0 0 6px #f43f5e; }
.map-count {
  margin-left: auto;
  color: #67e8f9;
  font-variant-numeric: tabular-nums;
}

.bot-row {
  height: 260px;
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
}

.wo-list, .exp-list {
  flex: 1;
  overflow: auto;
  padding: 4px 6px 8px;
}
.wo-row {
  display: grid;
  grid-template-columns: 96px 1fr 42px;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
}
.wo-name { font-size: 12px; color: #bae6fd; }
.wo-bar-wrap {
  height: 8px;
  background: rgba(14, 116, 144, 0.25);
  border-radius: 99px;
  overflow: hidden;
}
.wo-bar {
  height: 100%;
  background: linear-gradient(90deg, #0e7490, #22d3ee);
  border-radius: 99px;
  min-width: 2px;
}
.wo-cnt {
  text-align: right;
  font-size: 15px;
  color: #67e8f9;
  font-weight: 700;
}

.exp-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 10px 10px;
  margin-bottom: 8px;
  background: rgba(8, 47, 73, 0.35);
  border: 1px solid rgba(34, 211, 238, 0.15);
  border-radius: 6px;
}
.exp-row.clickable {
  cursor: pointer;
  transition: border-color 0.15s, background 0.15s;
}
.exp-row.clickable:hover {
  border-color: rgba(34, 211, 238, 0.55);
  background: rgba(8, 47, 73, 0.65);
}
.exp-row.urgent {
  border-color: rgba(244, 63, 94, 0.55);
  background: rgba(76, 5, 25, 0.35);
}
.exp-name { display: block; font-size: 13px; color: #ecfeff; font-weight: 600; }
.exp-sub { display: block; margin-top: 2px; font-size: 11px; color: #7dd3fc; }
.exp-timer {
  font-size: 14px;
  color: #fda4af;
  text-shadow: 0 0 8px rgba(244, 63, 94, 0.45);
  white-space: nowrap;
}
.empty-tip {
  padding: 24px 8px;
  text-align: center;
  color: #64748b;
  font-size: 13px;
}
</style>
