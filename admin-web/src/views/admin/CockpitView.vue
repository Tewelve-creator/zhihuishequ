<template>
  <div class="viz-root">
    <div class="viz-scale" :style="scaleStyle">
      <!-- 顶栏 -->
      <header class="viz-header">
        <div class="hdr-side left">
          <button class="hdr-btn" type="button" title="返回后台" @click="goBack">⌂</button>
          <button class="hdr-btn" type="button">⚙</button>
          <div class="hdr-deco" />
        </div>
        <div class="hdr-title-wrap">
          <div class="hdr-wing left-wing" />
          <h1 class="hdr-title">智慧智能社区可视化中心</h1>
          <div class="hdr-wing right-wing" />
        </div>
        <div class="hdr-side right">
          <div class="hdr-deco" />
          <div class="hdr-clock">{{ nowText }}</div>
          <button class="hdr-btn" type="button" title="全屏" @click="toggleFs">⛶</button>
        </div>
      </header>

      <!-- 顶部 KPI -->
      <section class="kpi-row">
        <div v-for="k in kpis" :key="k.label" class="kpi-card tech-box">
          <div class="kpi-icon">{{ k.icon }}</div>
          <div class="kpi-body">
            <div class="kpi-label">{{ k.label }}</div>
            <div class="kpi-value digi">{{ k.value }}</div>
          </div>
          <div class="kpi-glow" />
        </div>
      </section>

      <!-- 主体三栏 -->
      <section class="viz-body">
        <!-- 左栏 -->
        <aside class="col left-col">
          <div class="panel tech-box">
            <div class="panel-title">智能设备数量</div>
            <div class="digi-board">
              <span v-for="(d, i) in deviceDigits" :key="i" class="digi-cell">{{ d }}</span>
            </div>
            <div class="prog-list">
              <div v-for="p in deviceBars" :key="p.name" class="prog-item">
                <div class="prog-meta">
                  <span>{{ p.name }}</span>
                  <span>{{ p.value }}%</span>
                </div>
                <div class="prog-track">
                  <div class="prog-fill" :style="{ width: p.value + '%', background: p.color }" />
                </div>
              </div>
            </div>
          </div>

          <div class="panel tech-box grow">
            <div class="panel-title">告警事件概览</div>
            <v-chart class="chart" :option="alarmOpt" autoresize />
          </div>
        </aside>

        <!-- 中栏 -->
        <main class="col center-col">
          <div class="panel tech-box center-top">
            <div class="panel-title row">
              <span>核心业务中枢</span>
              <select v-model="year" class="year-select">
                <option v-for="y in years" :key="y" :value="y">{{ y }}</option>
              </select>
            </div>
            <div class="hub-row">
              <div v-for="m in modules" :key="m.title" class="hub-card">
                <div class="hub-iso" :class="m.tone">
                  <div class="iso-top" />
                  <div class="iso-face" />
                  <div class="iso-side" />
                  <span class="iso-emoji">{{ m.icon }}</span>
                </div>
                <div class="hub-title">{{ m.title }}</div>
                <div class="hub-sub">{{ m.sub }}</div>
              </div>
            </div>
            <div class="center-metrics">
              <div class="metric-block">
                <v-chart class="gauge-sm" :option="gaugeSafeOpt" autoresize />
                <div class="metric-label">安全指数</div>
              </div>
              <div class="metric-nums">
                <div class="num-card">
                  <div class="n-label">设备完好率</div>
                  <div class="n-val cyan">98%</div>
                </div>
                <div class="num-card">
                  <div class="n-label">工单闭环率</div>
                  <div class="n-val orange">86%</div>
                </div>
                <div class="num-card">
                  <div class="n-label">在线设备</div>
                  <div class="n-val lime">9,612</div>
                </div>
              </div>
              <div class="metric-block">
                <v-chart class="gauge-sm" :option="gaugeOnlineOpt" autoresize />
                <div class="metric-label">实时在线</div>
              </div>
            </div>
          </div>

          <div class="panel tech-box grow">
            <div class="panel-title">近 7 日业务流量趋势</div>
            <v-chart class="chart" :option="weekBarOpt" autoresize />
          </div>
        </main>

        <!-- 右栏 -->
        <aside class="col right-col">
          <div class="panel tech-box">
            <div class="panel-title">资源利用率</div>
            <div class="ring-row">
              <v-chart class="ring" :option="ringOpt(75, '#22d3ee', '算力')" autoresize />
              <v-chart class="ring" :option="ringOpt(52, '#a78bfa', '存储')" autoresize />
              <v-chart class="ring" :option="ringOpt(88, '#34d399', '带宽')" autoresize />
            </div>
          </div>

          <div class="panel tech-box">
            <div class="panel-title">实时通行 TOP10</div>
            <div class="rank-list">
              <div v-for="(r, idx) in ranks" :key="r.name" class="rank-item">
                <span class="rank-no" :class="{ top: idx < 3 }">{{ idx + 1 }}</span>
                <span class="rank-name">{{ r.name }}</span>
                <div class="rank-bar">
                  <i :style="{ width: r.pct + '%', background: r.color }" />
                </div>
                <span class="rank-val">{{ r.val }}</span>
              </div>
            </div>
          </div>

          <div class="panel tech-box grow">
            <div class="panel-title">业务构成分布</div>
            <v-chart class="chart" :option="pieOpt" autoresize />
          </div>
        </aside>
      </section>

      <!-- 底栏：今日态势 + 折线 -->
      <section class="viz-footer">
        <div class="panel tech-box today">
          <div class="panel-title">今日态势</div>
          <div class="today-cards">
            <div v-for="t in todayCards" :key="t.label" class="today-card">
              <div class="t-icon">{{ t.icon }}</div>
              <div>
                <div class="t-label">{{ t.label }}</div>
                <div class="t-val digi">{{ t.value }}</div>
              </div>
            </div>
          </div>
        </div>
        <div class="panel tech-box grow">
          <div class="panel-title">实时波动监测</div>
          <v-chart class="chart flat" :option="waveOpt" autoresize />
        </div>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { LineChart, PieChart, BarChart, GaugeChart } from 'echarts/charts'
import {
  GridComponent,
  TooltipComponent,
  LegendComponent,
  TitleComponent,
} from 'echarts/components'
import VChart from 'vue-echarts'
import dayjs from 'dayjs'

use([
  CanvasRenderer,
  LineChart,
  PieChart,
  BarChart,
  GaugeChart,
  GridComponent,
  TooltipComponent,
  LegendComponent,
  TitleComponent,
])

const router = useRouter()
const nowText = ref(dayjs().format('YYYY-MM-DD HH:mm:ss'))
const year = ref(2026)
const years = [2024, 2025, 2026]
const scale = ref(1)

let timer: number | undefined

const kpis = [
  { label: '统计人口', value: '88,743', icon: '人' },
  { label: '在管社区', value: '12', icon: '社' },
  { label: '住户数量', value: '3,455', icon: '户' },
  { label: '楼栋数量', value: '34', icon: '栋' },
]

const deviceDigits = computed(() => '09854'.split(''))

const deviceBars = [
  { name: '门禁系统', value: 92, color: 'linear-gradient(90deg,#06b6d4,#3b82f6)' },
  { name: '视频监控', value: 86, color: 'linear-gradient(90deg,#8b5cf6,#ec4899)' },
  { name: '智慧停车', value: 74, color: 'linear-gradient(90deg,#f59e0b,#ef4444)' },
]

const modules = [
  { title: '大数据中心', sub: '租户 · 订单 · 日志汇聚', icon: '◈', tone: 'cyan' },
  { title: '数字楼宇', sub: '楼栋 · 房态 · 设备联动', icon: '▣', tone: 'blue' },
  { title: '平台设备管理', sub: '物联感知 · 告警闭环', icon: '⬡', tone: 'violet' },
]

const ranks = [
  { name: '1号南门', val: 1286, pct: 100, color: '#22d3ee' },
  { name: '地下车库A', val: 1102, pct: 86, color: '#38bdf8' },
  { name: '2号东门', val: 968, pct: 75, color: '#818cf8' },
  { name: '访客通道', val: 842, pct: 66, color: '#a78bfa' },
  { name: '3号西门', val: 720, pct: 56, color: '#c084fc' },
  { name: '快递驿站', val: 654, pct: 51, color: '#e879f9' },
  { name: '健身中心', val: 532, pct: 41, color: '#f472b6' },
  { name: '泳池闸机', val: 410, pct: 32, color: '#fb7185' },
  { name: '幼儿园口', val: 356, pct: 28, color: '#fbbf24' },
  { name: '物业大厅', val: 298, pct: 23, color: '#34d399' },
]

const todayCards = [
  { label: '今日工单', value: '186', icon: '工' },
  { label: '今日访客', value: '342', icon: '访' },
  { label: '今日告警', value: '17', icon: '警' },
]

const axisLabel = { color: '#7dd3fc', fontSize: 11 }
const tip = { backgroundColor: 'rgba(2,12,30,.92)', borderColor: '#0ea5e9', textStyle: { color: '#e0f2fe' } }

const alarmOpt = computed(() => ({
  backgroundColor: 'transparent',
  tooltip: tip,
  legend: {
    bottom: 0,
    textStyle: { color: '#94a3b8', fontSize: 11 },
    data: ['紧急', '重要', '一般', '提示'],
  },
  series: [
    {
      type: 'pie',
      radius: ['42%', '68%'],
      center: ['50%', '46%'],
      roseType: false,
      label: { color: '#e0f2fe', formatter: '{b}\n{d}%', fontSize: 11 },
      itemStyle: {
        borderColor: '#020b1a',
        borderWidth: 2,
        shadowBlur: 12,
        shadowColor: 'rgba(56,189,248,.35)',
      },
      data: [
        { name: '紧急', value: 42, itemStyle: { color: '#f43f5e' } },
        { name: '重要', value: 86, itemStyle: { color: '#f59e0b' } },
        { name: '一般', value: 168, itemStyle: { color: '#22d3ee' } },
        { name: '提示', value: 214, itemStyle: { color: '#6366f1' } },
      ],
    },
    {
      type: 'pie',
      radius: ['0%', '28%'],
      center: ['50%', '46%'],
      silent: true,
      label: {
        show: true,
        position: 'center',
        formatter: '{a|34467}\n{b|累计告警}',
        rich: {
          a: { color: '#fff', fontSize: 18, fontWeight: 700, fontFamily: 'Orbitron, Consolas, monospace' },
          b: { color: '#64748b', fontSize: 11, lineHeight: 18 },
        },
      },
      data: [{ value: 1, itemStyle: { color: 'rgba(14,165,233,.12)' } }],
    },
  ],
}))

function gaugeBase(value: number, color: string, name: string) {
  return {
    backgroundColor: 'transparent',
    series: [
      {
        type: 'gauge',
        startAngle: 210,
        endAngle: -30,
        min: 0,
        max: 100,
        radius: '90%',
        center: ['50%', '58%'],
        axisLine: {
          lineStyle: {
            width: 10,
            color: [
              [value / 100, color],
              [1, 'rgba(148,163,184,.18)'],
            ],
          },
        },
        pointer: { show: true, length: '55%', width: 3, itemStyle: { color } },
        axisTick: { show: false },
        splitLine: { show: false },
        axisLabel: { show: false },
        detail: {
          valueAnimation: true,
          formatter: '{value}',
          color: '#e0f2fe',
          fontSize: 20,
          fontFamily: 'Orbitron, Consolas, monospace',
          offsetCenter: [0, '20%'],
        },
        title: { show: false },
        data: [{ value, name }],
      },
    ],
  }
}

const gaugeSafeOpt = computed(() => gaugeBase(92, '#22d3ee', '安全'))
const gaugeOnlineOpt = computed(() => gaugeBase(97, '#34d399', '在线'))

function ringOpt(value: number, color: string, name: string) {
  return {
    backgroundColor: 'transparent',
    series: [
      {
        type: 'pie',
        radius: ['62%', '82%'],
        silent: true,
        label: {
          show: true,
          position: 'center',
          formatter: `{a|${value}%}\n{b|${name}}`,
          rich: {
            a: { color: '#fff', fontSize: 15, fontWeight: 700, fontFamily: 'Orbitron, Consolas, monospace' },
            b: { color: '#64748b', fontSize: 10, lineHeight: 16 },
          },
        },
        data: [
          { value, itemStyle: { color, shadowBlur: 10, shadowColor: color } },
          { value: 100 - value, itemStyle: { color: 'rgba(148,163,184,.15)' } },
        ],
      },
    ],
  }
}

const weekBarOpt = computed(() => ({
  backgroundColor: 'transparent',
  tooltip: { ...tip, trigger: 'axis' },
  legend: { top: 0, right: 8, textStyle: { color: '#94a3b8', fontSize: 11 }, data: ['报修', '缴费', '访客'] },
  grid: { left: 36, right: 16, top: 32, bottom: 28 },
  xAxis: {
    type: 'category',
    data: ['周一', '周二', '周三', '周四', '周五', '周六', '周日'],
    axisLabel,
    axisLine: { lineStyle: { color: '#1e3a5f' } },
  },
  yAxis: {
    type: 'value',
    axisLabel,
    splitLine: { lineStyle: { color: 'rgba(56,189,248,.08)' } },
  },
  series: [
    {
      name: '报修',
      type: 'bar',
      barWidth: 10,
      itemStyle: { color: '#22d3ee', borderRadius: [2, 2, 0, 0], shadowBlur: 8, shadowColor: '#22d3ee66' },
      data: [42, 38, 55, 48, 61, 35, 28],
    },
    {
      name: '缴费',
      type: 'bar',
      barWidth: 10,
      itemStyle: { color: '#a78bfa', borderRadius: [2, 2, 0, 0], shadowBlur: 8, shadowColor: '#a78bfa66' },
      data: [68, 72, 64, 80, 76, 58, 45],
    },
    {
      name: '访客',
      type: 'bar',
      barWidth: 10,
      itemStyle: { color: '#f59e0b', borderRadius: [2, 2, 0, 0], shadowBlur: 8, shadowColor: '#f59e0b66' },
      data: [30, 44, 36, 52, 48, 66, 70],
    },
  ],
}))

const pieOpt = computed(() => ({
  backgroundColor: 'transparent',
  tooltip: tip,
  legend: {
    orient: 'vertical',
    right: 4,
    top: 'middle',
    textStyle: { color: '#94a3b8', fontSize: 11 },
  },
  series: [
    {
      type: 'pie',
      radius: ['40%', '68%'],
      center: ['38%', '50%'],
      label: { show: false },
      itemStyle: { borderColor: '#020b1a', borderWidth: 2, shadowBlur: 10, shadowColor: 'rgba(56,189,248,.3)' },
      data: [
        { name: '物业费', value: 42, itemStyle: { color: '#22d3ee' } },
        { name: '停车费', value: 18, itemStyle: { color: '#818cf8' } },
        { name: '报修服务', value: 16, itemStyle: { color: '#34d399' } },
        { name: '套餐续费', value: 14, itemStyle: { color: '#f59e0b' } },
        { name: '其他', value: 10, itemStyle: { color: '#f472b6' } },
      ],
    },
  ],
}))

const waveOpt = computed(() => ({
  backgroundColor: 'transparent',
  tooltip: { ...tip, trigger: 'axis' },
  grid: { left: 36, right: 16, top: 20, bottom: 24 },
  xAxis: {
    type: 'category',
    boundaryGap: false,
    data: Array.from({ length: 24 }, (_, i) => `${i}:00`),
    axisLabel: { ...axisLabel, interval: 3 },
    axisLine: { lineStyle: { color: '#1e3a5f' } },
  },
  yAxis: {
    type: 'value',
    axisLabel,
    splitLine: { lineStyle: { color: 'rgba(56,189,248,.08)' } },
  },
  series: [
    {
      name: '在线人数',
      type: 'line',
      smooth: true,
      symbol: 'none',
      lineStyle: { width: 2, color: '#22d3ee', shadowBlur: 12, shadowColor: '#22d3ee' },
      areaStyle: {
        color: {
          type: 'linear',
          x: 0, y: 0, x2: 0, y2: 1,
          colorStops: [
            { offset: 0, color: 'rgba(34,211,238,.35)' },
            { offset: 1, color: 'rgba(34,211,238,0)' },
          ],
        },
      },
      data: [820, 932, 901, 934, 1290, 1330, 1520, 1680, 1720, 1650, 1580, 1490, 1420, 1380, 1450, 1600, 1750, 1820, 1680, 1520, 1380, 1200, 1050, 920],
    },
  ],
}))

const scaleStyle = computed(() => ({
  transform: `scale(${scale.value})`,
  transformOrigin: 'left top',
  width: '1920px',
  height: '1080px',
}))

function updateScale() {
  const w = window.innerWidth / 1920
  const h = window.innerHeight / 1080
  scale.value = Math.min(w, h)
}

function goBack() {
  router.push('/admin/reports')
}

function toggleFs() {
  if (!document.fullscreenElement) {
    document.documentElement.requestFullscreen?.()
  } else {
    document.exitFullscreen?.()
  }
}

onMounted(() => {
  updateScale()
  window.addEventListener('resize', updateScale)
  timer = window.setInterval(() => {
    nowText.value = dayjs().format('YYYY-MM-DD HH:mm:ss')
  }, 1000)
})

onUnmounted(() => {
  window.removeEventListener('resize', updateScale)
  if (timer) clearInterval(timer)
})
</script>

<style scoped>
@import url('https://fonts.googleapis.com/css2?family=Orbitron:wght@500;700&family=Noto+Sans+SC:wght@400;600;700&display=swap');

.viz-root {
  width: 100vw;
  height: 100vh;
  overflow: hidden;
  background:
    radial-gradient(ellipse 80% 50% at 50% -10%, rgba(14, 165, 233, 0.18), transparent 55%),
    radial-gradient(ellipse 60% 40% at 80% 100%, rgba(99, 102, 241, 0.12), transparent 50%),
    linear-gradient(180deg, #031225 0%, #020b1a 45%, #010814 100%);
  color: #e0f2fe;
  font-family: 'Noto Sans SC', 'PingFang SC', sans-serif;
}

.viz-scale {
  position: relative;
  box-sizing: border-box;
  padding: 8px 18px 14px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

/* —— 顶栏 —— */
.viz-header {
  height: 72px;
  display: grid;
  grid-template-columns: 1fr auto 1fr;
  align-items: center;
  position: relative;
}
.hdr-side {
  display: flex;
  align-items: center;
  gap: 10px;
}
.hdr-side.right { justify-content: flex-end; }
.hdr-btn {
  width: 34px;
  height: 34px;
  border-radius: 50%;
  border: 1px solid rgba(34, 211, 238, 0.45);
  background: radial-gradient(circle at 30% 30%, rgba(56, 189, 248, 0.25), rgba(2, 12, 30, 0.9));
  color: #7dd3fc;
  cursor: pointer;
  box-shadow: 0 0 12px rgba(34, 211, 238, 0.25);
}
.hdr-deco {
  width: 120px;
  height: 2px;
  background: linear-gradient(90deg, transparent, #22d3ee, transparent);
  box-shadow: 0 0 8px #22d3ee;
}
.hdr-title-wrap {
  display: flex;
  align-items: center;
  gap: 12px;
}
.hdr-title {
  margin: 0;
  font-size: 34px;
  font-weight: 700;
  letter-spacing: 0.12em;
  background: linear-gradient(180deg, #ffffff 10%, #7dd3fc 55%, #38bdf8 100%);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
  text-shadow: 0 0 24px rgba(56, 189, 248, 0.35);
  white-space: nowrap;
}
.hdr-wing {
  width: 140px;
  height: 28px;
  border: 1px solid rgba(34, 211, 238, 0.5);
  border-radius: 2px;
  position: relative;
  background: linear-gradient(90deg, transparent, rgba(14, 165, 233, 0.15), transparent);
}
.left-wing { clip-path: polygon(12% 0, 100% 0, 100% 100%, 0 100%); }
.right-wing { clip-path: polygon(0 0, 100% 0, 88% 100%, 0 100%); }
.hdr-clock {
  font-family: Orbitron, Consolas, monospace;
  font-size: 14px;
  color: #7dd3fc;
  letter-spacing: 0.04em;
}

/* —— KPI —— */
.kpi-row {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 14px;
}
.kpi-card {
  height: 78px;
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 0 18px;
  position: relative;
  overflow: hidden;
}
.kpi-icon {
  width: 44px;
  height: 44px;
  display: grid;
  place-items: center;
  border-radius: 10px;
  background: rgba(14, 165, 233, 0.12);
  border: 1px solid rgba(34, 211, 238, 0.35);
  font-size: 15px;
  font-weight: 700;
  color: #7dd3fc;
  letter-spacing: 0.04em;
}
.kpi-label { font-size: 13px; color: #94a3b8; }
.kpi-value {
  margin-top: 4px;
  font-size: 28px;
  font-weight: 700;
  color: #f8fafc;
  letter-spacing: 0.04em;
}
.kpi-glow {
  position: absolute;
  right: -20px;
  bottom: -30px;
  width: 100px;
  height: 100px;
  background: radial-gradient(circle, rgba(34, 211, 238, 0.2), transparent 70%);
  pointer-events: none;
}

/* —— 三栏主体 —— */
.viz-body {
  flex: 1;
  min-height: 0;
  display: grid;
  grid-template-columns: 420px 1fr 420px;
  gap: 12px;
}
.col {
  display: flex;
  flex-direction: column;
  gap: 10px;
  min-height: 0;
}
.panel {
  padding: 10px 12px 8px;
  display: flex;
  flex-direction: column;
  min-height: 0;
}
.panel.grow { flex: 1; }
.panel-title {
  font-size: 14px;
  font-weight: 600;
  color: #bae6fd;
  margin-bottom: 8px;
  padding-left: 10px;
  border-left: 3px solid #22d3ee;
  letter-spacing: 0.06em;
}
.panel-title.row {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.year-select {
  background: rgba(2, 12, 30, 0.8);
  border: 1px solid rgba(34, 211, 238, 0.4);
  color: #7dd3fc;
  border-radius: 4px;
  padding: 2px 8px;
  outline: none;
}
.chart { flex: 1; min-height: 160px; width: 100%; }
.chart.flat { min-height: 120px; }

/* 数字翻牌 */
.digi-board {
  display: flex;
  gap: 6px;
  justify-content: center;
  margin: 6px 0 12px;
}
.digi-cell {
  width: 42px;
  height: 54px;
  display: grid;
  place-items: center;
  font-family: Orbitron, Consolas, monospace;
  font-size: 28px;
  font-weight: 700;
  color: #e0f2fe;
  background: linear-gradient(180deg, #0c4a6e 0%, #082f49 48%, #0ea5e9 49%, #082f49 50%, #020617 100%);
  border: 1px solid rgba(34, 211, 238, 0.55);
  border-radius: 4px;
  box-shadow: inset 0 0 12px rgba(14, 165, 233, 0.35), 0 0 10px rgba(14, 165, 233, 0.2);
}
.digi { font-family: Orbitron, Consolas, monospace; }

.prog-list { display: flex; flex-direction: column; gap: 8px; }
.prog-meta {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  color: #94a3b8;
  margin-bottom: 4px;
}
.prog-track {
  height: 8px;
  border-radius: 999px;
  background: rgba(148, 163, 184, 0.15);
  overflow: hidden;
}
.prog-fill {
  height: 100%;
  border-radius: 999px;
  box-shadow: 0 0 8px rgba(34, 211, 238, 0.45);
}

/* 中枢等距块 */
.hub-row {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
  padding: 8px 8px 4px;
}
.hub-card { text-align: center; }
.hub-iso {
  width: 92px;
  height: 78px;
  margin: 0 auto 8px;
  position: relative;
  transform-style: preserve-3d;
}
.hub-iso .iso-top,
.hub-iso .iso-face,
.hub-iso .iso-side {
  position: absolute;
  inset: 0;
  border-radius: 8px;
}
.hub-iso .iso-top {
  transform: translateY(-8px) rotateX(55deg);
  background: linear-gradient(135deg, rgba(125, 211, 252, 0.55), rgba(14, 165, 233, 0.2));
  border: 1px solid rgba(125, 211, 252, 0.5);
}
.hub-iso .iso-face {
  background: linear-gradient(180deg, rgba(14, 165, 233, 0.35), rgba(2, 12, 30, 0.85));
  border: 1px solid rgba(34, 211, 238, 0.4);
  box-shadow: 0 12px 24px rgba(2, 8, 20, 0.55);
}
.hub-iso .iso-side {
  width: 18px;
  left: auto;
  right: -10px;
  background: rgba(8, 47, 73, 0.9);
  transform: skewY(-25deg);
  opacity: 0.7;
}
.hub-iso.blue .iso-face { background: linear-gradient(180deg, rgba(59, 130, 246, 0.4), rgba(2, 12, 30, 0.85)); }
.hub-iso.violet .iso-face { background: linear-gradient(180deg, rgba(167, 139, 250, 0.4), rgba(2, 12, 30, 0.85)); }
.iso-emoji {
  position: relative;
  z-index: 2;
  top: 22px;
  font-size: 26px;
  color: #e0f2fe;
  text-shadow: 0 0 12px #22d3ee;
}
.hub-title { font-size: 14px; font-weight: 600; color: #e0f2fe; }
.hub-sub { margin-top: 4px; font-size: 11px; color: #64748b; }

.center-metrics {
  display: grid;
  grid-template-columns: 140px 1fr 140px;
  gap: 8px;
  align-items: center;
  margin-top: 4px;
}
.gauge-sm { height: 110px; width: 100%; }
.metric-label {
  text-align: center;
  font-size: 12px;
  color: #94a3b8;
  margin-top: -8px;
}
.metric-nums {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 8px;
}
.num-card {
  text-align: center;
  padding: 10px 6px;
  border: 1px solid rgba(34, 211, 238, 0.25);
  background: rgba(8, 47, 73, 0.35);
  border-radius: 6px;
}
.n-label { font-size: 11px; color: #64748b; }
.n-val {
  margin-top: 4px;
  font-family: Orbitron, Consolas, monospace;
  font-size: 20px;
  font-weight: 700;
}
.n-val.cyan { color: #22d3ee; }
.n-val.orange { color: #fbbf24; }
.n-val.lime { color: #34d399; }

.ring-row {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  height: 110px;
}
.ring { width: 100%; height: 100%; }

.rank-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
  max-height: 240px;
  overflow: hidden;
}
.rank-item {
  display: grid;
  grid-template-columns: 22px 78px 1fr 42px;
  align-items: center;
  gap: 6px;
  font-size: 12px;
}
.rank-no {
  width: 18px;
  height: 18px;
  border-radius: 50%;
  display: grid;
  place-items: center;
  background: rgba(148, 163, 184, 0.2);
  color: #94a3b8;
  font-size: 10px;
}
.rank-no.top {
  background: linear-gradient(135deg, #f59e0b, #ef4444);
  color: #fff;
  box-shadow: 0 0 8px rgba(245, 158, 11, 0.45);
}
.rank-name { color: #cbd5e1; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.rank-bar {
  height: 6px;
  border-radius: 999px;
  background: rgba(148, 163, 184, 0.12);
  overflow: hidden;
}
.rank-bar i {
  display: block;
  height: 100%;
  border-radius: 999px;
  box-shadow: 0 0 6px currentColor;
}
.rank-val {
  text-align: right;
  font-family: Orbitron, Consolas, monospace;
  color: #7dd3fc;
  font-size: 11px;
}

/* 底栏 */
.viz-footer {
  display: grid;
  grid-template-columns: 420px 1fr;
  gap: 12px;
  height: 168px;
}
.today-cards {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 10px;
  flex: 1;
  align-content: center;
}
.today-card {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px;
  border-radius: 8px;
  border: 1px solid rgba(34, 211, 238, 0.28);
  background: linear-gradient(135deg, rgba(14, 165, 233, 0.12), rgba(2, 12, 30, 0.4));
}
.t-icon {
  width: 36px;
  height: 36px;
  display: grid;
  place-items: center;
  border-radius: 8px;
  font-size: 14px;
  font-weight: 700;
  color: #7dd3fc;
  background: rgba(14, 165, 233, 0.15);
  border: 1px solid rgba(34, 211, 238, 0.35);
}
.t-label { font-size: 12px; color: #94a3b8; }
.t-val { margin-top: 4px; font-size: 22px; font-weight: 700; color: #f8fafc; }

/* 科技边框盒 */
.tech-box {
  position: relative;
  background:
    linear-gradient(180deg, rgba(8, 47, 73, 0.45) 0%, rgba(2, 12, 30, 0.72) 100%);
  border: 1px solid rgba(34, 211, 238, 0.28);
  box-shadow:
    inset 0 0 24px rgba(14, 165, 233, 0.06),
    0 0 16px rgba(2, 8, 20, 0.55);
  clip-path: polygon(
    0 10px, 10px 0, calc(100% - 10px) 0, 100% 10px,
    100% calc(100% - 10px), calc(100% - 10px) 100%, 10px 100%, 0 calc(100% - 10px)
  );
}
.tech-box::before,
.tech-box::after {
  content: '';
  position: absolute;
  width: 12px;
  height: 12px;
  border: 2px solid #22d3ee;
  pointer-events: none;
}
.tech-box::before {
  top: 4px;
  left: 4px;
  border-right: none;
  border-bottom: none;
  box-shadow: -2px -2px 8px rgba(34, 211, 238, 0.35);
}
.tech-box::after {
  right: 4px;
  bottom: 4px;
  border-left: none;
  border-top: none;
  box-shadow: 2px 2px 8px rgba(34, 211, 238, 0.35);
}
</style>
