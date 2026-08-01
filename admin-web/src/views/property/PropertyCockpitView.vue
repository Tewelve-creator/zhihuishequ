<template>
  <div class="viz-root">
    <div class="viz-scale" :style="scaleStyle">
      <header class="viz-header">
        <div class="hdr-side left">
          <button class="hdr-btn" type="button" title="返回工作台" @click="exitScreen">⌂</button>
          <button class="hdr-btn" type="button" :disabled="loading" title="刷新" @click="load">↻</button>
          <span class="live-dot" :class="{ on: !loading }" />
          <span class="live-txt">{{ loading ? '同步中' : '数据已同步' }}</span>
        </div>
        <div class="hdr-title-wrap">
          <div class="hdr-wing" />
          <h1 class="hdr-title">社区运营驾驶舱</h1>
          <div class="hdr-wing" />
        </div>
        <div class="hdr-side right">
          <div class="hdr-meta">
            <div class="community">{{ dash.tenantName || user.communityName || '当前社区' }}</div>
            <div class="clock digi">{{ nowText }}</div>
          </div>
          <button class="hdr-btn" type="button" title="全屏" @click="toggleFs">⛶</button>
        </div>
      </header>

      <main class="viz-body">
        <!-- 左栏 -->
        <section class="col left">
          <div class="panel">
            <div class="panel-title">工单汇总</div>
            <div class="kpi-grid">
              <div class="kpi">
                <div class="kpi-label">今日工单</div>
                <div class="kpi-val digi">{{ n(ws.todayTotal) }}</div>
              </div>
              <div class="kpi">
                <div class="kpi-label">待处理</div>
                <div class="kpi-val digi warn">{{ n(ws.pending) }}</div>
              </div>
              <div class="kpi">
                <div class="kpi-label">今日办结</div>
                <div class="kpi-val digi ok">{{ n(ws.doneToday) }}</div>
              </div>
              <div class="kpi">
                <div class="kpi-label">完成率</div>
                <div class="kpi-val digi">{{ n(ws.finishRate) }}%</div>
              </div>
            </div>
          </div>

          <div class="panel grow">
            <div class="panel-title">工单趋势（近7日）</div>
            <v-chart class="chart" :option="trendOpt" autoresize />
          </div>

          <div class="panel grow">
            <div class="panel-title">活跃住户（近7日有报修）</div>
            <v-chart class="chart" :option="activeOpt" autoresize />
          </div>
        </section>

        <!-- 中栏：楼栋态势 -->
        <section class="col mid">
          <BuildingSituationPanel
            class="sit-embed"
            :buildings="buildings"
            :map-markers="mapMarkers"
            :recent-events="recentEvents"
            :todo="todo"
            :community="community"
            :loading="loading"
            :error="loadError"
            :updated-at="updatedAt"
            @detail="goBiz"
          />
        </section>

        <!-- 右栏 -->
        <section class="col right">
          <div class="panel">
            <div class="panel-title">待处理事项</div>
            <div class="gauge-wrap">
              <v-chart class="gauge" :option="gaugeOpt" autoresize />
              <div class="todo-break">
                <div><em class="warn">{{ n(todo.urgentRepair) }}</em><span>紧急报修</span></div>
                <div><em>{{ n(todo.pendingAudit) }}</em><span>待审核</span></div>
                <div><em>{{ n(todo.emotionPending) }}</em><span>情绪工单</span></div>
                <div><em>{{ n(todo.inspOpen) }}</em><span>巡检待办</span></div>
              </div>
            </div>
          </div>

          <div class="panel grow">
            <div class="panel-title">工单类型分布（近30日）</div>
            <v-chart class="chart" :option="typeOpt" autoresize />
          </div>

          <div class="panel grow">
            <div class="panel-title">服务质量排行（近30日办结）</div>
            <div class="rank-list">
              <div v-for="(s, i) in staffRankView" :key="s.name" class="rank-row">
                <span class="rank-idx" :class="{ top: i < 3 }">{{ i + 1 }}</span>
                <span class="rank-name">{{ s.name }}</span>
                <div class="rank-bar">
                  <i :style="{ width: `${s.rate}%` }" />
                </div>
                <span class="rank-rate digi">{{ s.rate }}%</span>
              </div>
              <div v-if="!staffRankView.length" class="empty">暂无处理人办结数据</div>
            </div>
            <div class="community-foot">
              住户 {{ n(community.residentTotal) }} · 房屋 {{ n(community.houseTotal) }} · 今日访客
              {{ n(community.visitToday) }}
            </div>
          </div>
        </section>

        <!-- 底栏：真实预警 + 最新事件 -->
        <section class="viz-foot">
          <div class="panel foot-panel">
            <div class="panel-title">
              社区动态
              <span class="sub">预警 {{ alerts.length }}</span>
            </div>
            <div class="feed-list">
              <button
                v-for="a in alerts.slice(0, 8)"
                :key="`a-${a.type}-${a.id}`"
                type="button"
                class="feed-row"
                @click="goBiz(alertBiz(a.type))"
              >
                <span class="lvl" :class="a.level || 'low'">{{ a.type || '动态' }}</span>
                <span class="txt">{{ a.content || '—' }}</span>
                <span class="tm digi">{{ fmtTime(a.createTime) }}</span>
              </button>
              <div v-if="!alerts.length" class="empty">暂无预警动态</div>
            </div>
          </div>
          <div class="panel foot-panel">
            <div class="panel-title">
              最新事件
              <span class="sub">近 {{ recentEvents.length }} 条</span>
            </div>
            <div class="feed-list">
              <button
                v-for="ev in recentEvents.slice(0, 8)"
                :key="`e-${ev.biz}-${ev.id}`"
                type="button"
                class="feed-row"
                @click="goBiz(ev.biz)"
              >
                <span class="lvl">{{ ev.type || '事件' }}</span>
                <span class="txt">{{ ev.title || '—' }}</span>
                <span class="st">{{ statusLabel(ev.status) }}</span>
                <span class="tm digi">{{ fmtTime(ev.createTime) }}</span>
              </button>
              <div v-if="!recentEvents.length" class="empty">暂无最新事件</div>
            </div>
          </div>
        </section>
      </main>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { LineChart, BarChart, PieChart, GaugeChart } from 'echarts/charts'
import { GridComponent, TooltipComponent, LegendComponent } from 'echarts/components'
import VChart from 'vue-echarts'
import dayjs from 'dayjs'
import { useUserStore } from '@/stores/user'
import { getTenantDashboard } from '@/api/stats'
import BuildingSituationPanel from '@/views/property/BuildingSituationPanel.vue'

use([CanvasRenderer, LineChart, BarChart, PieChart, GaugeChart, GridComponent, TooltipComponent, LegendComponent])

const router = useRouter()
const user = useUserStore()
const loading = ref(false)
const loadError = ref(false)
const updatedAt = ref<number>(Date.now())
const scale = ref(1)
const nowText = ref(dayjs().format('HH:mm:ss'))
const dash = ref<Awaited<ReturnType<typeof getTenantDashboard>>>({})
let timer: ReturnType<typeof setInterval> | null = null
let clockTimer: ReturnType<typeof setInterval> | null = null

const scaleStyle = computed(() => ({
  transform: `scale(${scale.value})`,
  transformOrigin: 'left top',
}))

const ws = computed(() => dash.value.workSummary || {})
const community = computed(() => dash.value.community || {})
const todo = computed(() => dash.value.todo || {})
const buildings = computed(() => dash.value.buildings || [])
const mapMarkers = computed(() => dash.value.mapMarkers || [])
const recentEvents = computed(() => dash.value.recentEvents || [])
const alerts = computed(() => dash.value.alerts || [])

const staffRankView = computed(() => {
  return (dash.value.staffRank || []).map((s) => {
    const total = Number(s.total) || 0
    const done = Number(s.done) || 0
    const rate = total > 0 ? Math.round((done / total) * 100) : 0
    return { name: s.name || '—', rate, done, total }
  })
})

const chartAxis = {
  axisLine: { lineStyle: { color: 'rgba(94,234,212,.25)' } },
  axisLabel: { color: '#7dd3c7', fontSize: 11 },
  splitLine: { lineStyle: { color: 'rgba(94,234,212,.08)' } },
}

const trendOpt = computed(() => {
  const rows = dash.value.repairTrendSeries || []
  return {
    color: ['#22d3ee', '#34d399'],
    tooltip: { trigger: 'axis' },
    legend: { data: ['新增', '办结'], textStyle: { color: '#99f6e4' }, top: 0 },
    grid: { left: 36, right: 12, top: 28, bottom: 24 },
    xAxis: { type: 'category', data: rows.map((r) => r.day), ...chartAxis },
    yAxis: { type: 'value', minInterval: 1, ...chartAxis },
    series: [
      { name: '新增', type: 'line', smooth: true, data: rows.map((r) => Number(r.created) || 0), areaStyle: { opacity: 0.12 } },
      { name: '办结', type: 'line', smooth: true, data: rows.map((r) => Number(r.finished) || 0), areaStyle: { opacity: 0.08 } },
    ],
  }
})

const activeOpt = computed(() => {
  const rows = dash.value.activeResidents || []
  return {
    color: ['#2dd4bf'],
    tooltip: { trigger: 'axis' },
    grid: { left: 36, right: 12, top: 16, bottom: 24 },
    xAxis: { type: 'category', data: rows.map((r) => r.day), ...chartAxis },
    yAxis: { type: 'value', minInterval: 1, ...chartAxis },
    series: [
      {
        type: 'bar',
        barWidth: 14,
        data: rows.map((r) => Number(r.cnt) || 0),
        itemStyle: {
          borderRadius: [4, 4, 0, 0],
          color: {
            type: 'linear',
            x: 0,
            y: 0,
            x2: 0,
            y2: 1,
            colorStops: [
              { offset: 0, color: '#5eead4' },
              { offset: 1, color: '#0f766e' },
            ],
          },
        },
      },
    ],
  }
})

const typeOpt = computed(() => {
  const rows = (dash.value.workTypeDist || []).filter((r) => Number(r.cnt) > 0)
  return {
    color: ['#22d3ee', '#a78bfa', '#fbbf24', '#fb7185'],
    tooltip: { trigger: 'item' },
    legend: { bottom: 0, textStyle: { color: '#99f6e4', fontSize: 11 } },
    series: [
      {
        type: 'pie',
        radius: ['42%', '68%'],
        center: ['50%', '44%'],
        label: { color: '#ccfbf1', formatter: '{b}\n{d}%', fontSize: 11 },
        data: rows.length
          ? rows.map((r) => ({ name: r.name, value: Number(r.cnt) || 0 }))
          : [{ name: '暂无', value: 1 }],
      },
    ],
  }
})

const gaugeOpt = computed(() => {
  const total = Number(todo.value.total) || 0
  return {
    series: [
      {
        type: 'gauge',
        startAngle: 210,
        endAngle: -30,
        min: 0,
        max: Math.max(20, total * 1.4),
        pointer: { show: false },
        progress: { show: true, width: 12, itemStyle: { color: '#22d3ee' } },
        axisLine: { lineStyle: { width: 12, color: [[1, 'rgba(34,211,238,.15)']] } },
        axisTick: { show: false },
        splitLine: { show: false },
        axisLabel: { show: false },
        detail: {
          valueAnimation: true,
          formatter: '{value}',
          color: '#ecfeff',
          fontSize: 36,
          fontWeight: 700,
          offsetCenter: [0, '8%'],
        },
        title: { offsetCenter: [0, '42%'], color: '#5eead4', fontSize: 13 },
        data: [{ value: total, name: '待处理合计' }],
      },
    ],
  }
})

function n(v: unknown) {
  const num = Number(v)
  return Number.isFinite(num) ? num : 0
}

function fmtTime(v?: string) {
  if (!v) return '--:--'
  return dayjs(v).isValid() ? dayjs(v).format('HH:mm') : '--:--'
}

function statusLabel(s?: string) {
  const v = String(s || '').toLowerCase()
  if (!v) return '—'
  if (v === 'pending' || v === '待处理') return '待处理'
  if (v === 'processing' || v === 'handling' || v === '处理中') return '处理中'
  if (v === 'done' || v === 'closed' || v === '已完成') return '已完成'
  if (v === 'cancelled' || v === '已取消') return '已取消'
  return String(s)
}

function alertBiz(type?: string) {
  if (type === '情绪') return 'emotion'
  if (type === '巡检') return 'insp'
  return 'repair'
}

function goBiz(biz?: string) {
  if (biz === 'visit') router.push('/property/residents/visits')
  else if (biz === 'emotion') router.push('/property/ai/emotion')
  else if (biz === 'insp') router.push('/property/inspection')
  else router.push('/property/repair')
}

function exitScreen() {
  router.push('/property/dashboard')
}

function toggleFs() {
  if (!document.fullscreenElement) document.documentElement.requestFullscreen?.()
  else document.exitFullscreen?.()
}

function resize() {
  const w = window.innerWidth
  const h = window.innerHeight
  scale.value = Math.min(w / 1920, h / 1080)
}

async function load() {
  loading.value = true
  try {
    dash.value = (await getTenantDashboard()) || {}
    loadError.value = false
    updatedAt.value = Date.now()
  } catch {
    dash.value = {}
    loadError.value = true
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  resize()
  window.addEventListener('resize', resize)
  load()
  timer = setInterval(load, 60000)
  clockTimer = setInterval(() => {
    nowText.value = dayjs().format('HH:mm:ss')
  }, 1000)
})

onUnmounted(() => {
  window.removeEventListener('resize', resize)
  if (timer) clearInterval(timer)
  if (clockTimer) clearInterval(clockTimer)
})
</script>

<style scoped>
.viz-root {
  width: 100%;
  height: 100vh;
  overflow: hidden;
  background: radial-gradient(ellipse at 50% 0%, #0b3a4a 0%, #020b1a 55%, #01060f 100%);
  color: #ecfeff;
}
.viz-scale {
  width: 1920px;
  height: 1080px;
}
.digi {
  font-variant-numeric: tabular-nums;
  font-family: 'DIN Alternate', 'Segoe UI', system-ui, sans-serif;
}

.viz-header {
  height: 88px;
  display: grid;
  grid-template-columns: 1fr auto 1fr;
  align-items: center;
  padding: 0 28px;
}
.hdr-side {
  display: flex;
  align-items: center;
  gap: 12px;
}
.hdr-side.right {
  justify-content: flex-end;
}
.hdr-btn {
  width: 36px;
  height: 36px;
  border: 1px solid rgba(94, 234, 212, 0.35);
  background: rgba(15, 118, 110, 0.2);
  color: #99f6e4;
  border-radius: 8px;
  cursor: pointer;
}
.hdr-btn:disabled {
  opacity: 0.5;
  cursor: default;
}
.live-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #64748b;
}
.live-dot.on {
  background: #34d399;
  box-shadow: 0 0 8px #34d399;
}
.live-txt {
  font-size: 12px;
  color: #5eead4;
}
.hdr-title-wrap {
  display: flex;
  align-items: center;
  gap: 16px;
}
.hdr-wing {
  width: 80px;
  height: 2px;
  background: linear-gradient(90deg, transparent, #22d3ee, transparent);
}
.hdr-title {
  margin: 0;
  padding: 10px 36px;
  font-size: 28px;
  font-weight: 700;
  letter-spacing: 4px;
  color: #ecfeff;
  border: 1px solid rgba(34, 211, 238, 0.45);
  background: linear-gradient(180deg, rgba(8, 51, 68, 0.9), rgba(2, 20, 35, 0.95));
  box-shadow: 0 0 24px rgba(34, 211, 238, 0.18);
  clip-path: polygon(12px 0, calc(100% - 12px) 0, 100% 50%, calc(100% - 12px) 100%, 12px 100%, 0 50%);
}
.hdr-meta {
  text-align: right;
}
.community {
  font-size: 14px;
  color: #99f6e4;
}
.clock {
  font-size: 22px;
  font-weight: 700;
  color: #ecfeff;
}

.viz-body {
  height: calc(1080px - 88px);
  display: grid;
  grid-template-columns: 420px 1fr 420px;
  grid-template-rows: minmax(0, 1fr) 188px;
  gap: 14px;
  padding: 0 18px 18px;
  box-sizing: border-box;
}
.col {
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-height: 0;
}
.viz-foot {
  grid-column: 1 / -1;
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
  min-height: 0;
}
.foot-panel {
  display: flex;
  flex-direction: column;
  min-height: 0;
  height: 100%;
}
.feed-list {
  flex: 1;
  min-height: 0;
  overflow: auto;
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.feed-row {
  display: grid;
  grid-template-columns: 52px 1fr auto auto;
  gap: 8px;
  align-items: center;
  width: 100%;
  padding: 6px 8px;
  border: 1px solid rgba(45, 212, 191, 0.14);
  border-radius: 4px;
  background: rgba(2, 20, 35, 0.4);
  color: #ecfeff;
  font-size: 12px;
  text-align: left;
  cursor: pointer;
}
.feed-row:hover {
  border-color: rgba(34, 211, 238, 0.45);
}
.feed-row .lvl {
  font-size: 11px;
  color: #67e8f9;
  padding: 1px 4px;
  border-radius: 3px;
  background: rgba(34, 211, 238, 0.12);
  text-align: center;
}
.feed-row .lvl.high {
  color: #fbbf24;
  background: rgba(251, 191, 36, 0.15);
}
.feed-row .lvl.mid {
  color: #fb7185;
  background: rgba(251, 113, 133, 0.14);
}
.feed-row .txt {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.feed-row .st {
  color: #5eead4;
  font-size: 11px;
}
.feed-row .tm {
  color: #7dd3c7;
  font-size: 11px;
}
.empty {
  padding: 18px 8px;
  text-align: center;
  color: #5eead4;
  font-size: 12px;
  opacity: 0.75;
}
.panel {
  border: 1px solid rgba(45, 212, 191, 0.28);
  background: linear-gradient(180deg, rgba(8, 47, 73, 0.72), rgba(2, 16, 32, 0.88));
  border-radius: 8px;
  padding: 12px 14px;
  box-shadow: inset 0 0 0 1px rgba(255, 255, 255, 0.02);
  min-height: 0;
}
.panel.grow {
  flex: 1;
  display: flex;
  flex-direction: column;
}
.panel-title {
  display: flex;
  align-items: baseline;
  gap: 10px;
  margin-bottom: 10px;
  font-size: 15px;
  font-weight: 650;
  color: #ccfbf1;
}
.panel-title .sub {
  font-size: 12px;
  font-weight: 400;
  color: #5eead4;
  opacity: 0.75;
}

.kpi-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
}
.kpi {
  padding: 10px 12px;
  border: 1px solid rgba(45, 212, 191, 0.18);
  border-radius: 6px;
  background: rgba(2, 20, 35, 0.45);
}
.kpi-label {
  font-size: 12px;
  color: #5eead4;
}
.kpi-val {
  margin-top: 6px;
  font-size: 26px;
  font-weight: 700;
}
.kpi-val.warn {
  color: #fbbf24;
}
.kpi-val.ok {
  color: #34d399;
}

.chart {
  flex: 1;
  min-height: 160px;
  width: 100%;
}

.sit-embed {
  flex: 1;
  min-height: 0;
}

.gauge-wrap {
  display: grid;
  grid-template-columns: 1.1fr 1fr;
  gap: 8px;
  align-items: center;
}
.gauge {
  height: 180px;
}
.todo-break {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
}
.todo-break div {
  padding: 10px;
  border: 1px solid rgba(45, 212, 191, 0.16);
  border-radius: 6px;
  text-align: center;
}
.todo-break em {
  display: block;
  font-style: normal;
  font-size: 22px;
  font-weight: 700;
  color: #ecfeff;
}
.todo-break em.warn {
  color: #fbbf24;
}
.todo-break span {
  font-size: 11px;
  color: #5eead4;
}

.rank-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  flex: 1;
  overflow: auto;
}
.rank-row {
  display: grid;
  grid-template-columns: 28px 72px 1fr 48px;
  gap: 8px;
  align-items: center;
}
.rank-idx {
  width: 22px;
  height: 22px;
  border-radius: 50%;
  display: grid;
  place-items: center;
  font-size: 12px;
  background: rgba(45, 212, 191, 0.15);
  color: #99f6e4;
}
.rank-idx.top {
  background: #0f766e;
  color: #ecfeff;
}
.rank-name {
  font-size: 12px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.rank-bar {
  height: 8px;
  border-radius: 99px;
  background: rgba(45, 212, 191, 0.12);
  overflow: hidden;
}
.rank-bar i {
  display: block;
  height: 100%;
  background: linear-gradient(90deg, #0f766e, #22d3ee);
}
.rank-rate {
  text-align: right;
  font-size: 12px;
  color: #5eead4;
}
.community-foot {
  margin-top: 10px;
  padding-top: 8px;
  border-top: 1px solid rgba(45, 212, 191, 0.15);
  font-size: 12px;
  color: #5eead4;
}
</style>
