<template>
  <div class="page-card" v-loading="loading">
    <div class="page-header">
      <div>
        <h2>迁移统计</h2>
        <p class="sub">当前小区真实住户流动：申请审批、迁入迁出与住户档案状态</p>
      </div>
      <div class="actions">
        <el-button :loading="loading" @click="load">刷新</el-button>
        <el-button type="primary" plain @click="onExport">导出 Excel</el-button>
      </div>
    </div>

    <div class="section-label">申请概况</div>
    <div class="kpi-row cols-5">
      <div
        v-for="k in applyKpis"
        :key="k.key"
        class="kpi-card"
        :class="{ clickable: !!k.to }"
        @click="k.to && go(k.to)"
      >
        <div class="kpi-label">{{ k.label }}</div>
        <div class="kpi-value" :class="k.tone">{{ k.value }}</div>
        <div v-if="k.hint" class="kpi-hint">{{ k.hint }}</div>
      </div>
    </div>

    <div class="section-label">住户档案</div>
    <div class="kpi-row cols-5">
      <div
        v-for="k in householdKpis"
        :key="k.key"
        class="kpi-card"
        :class="{ clickable: !!k.to }"
        @click="k.to && go(k.to)"
      >
        <div class="kpi-label">{{ k.label }}</div>
        <div class="kpi-value" :class="k.tone">{{ k.value }}</div>
        <div v-if="k.hint" class="kpi-hint">{{ k.hint }}</div>
      </div>
    </div>

    <div class="charts">
      <div class="panel">
        <div class="panel-hd">
          <h3>通过申请 · 身份占比</h3>
          <span class="muted">已通过申请</span>
        </div>
        <div class="chart-box">
          <v-chart :option="identityPieOption" autoresize />
        </div>
        <el-table :data="identityRows" size="small" stripe empty-text="暂无通过申请">
          <el-table-column prop="label" label="身份" />
          <el-table-column prop="value" label="数量" width="90" align="right" />
        </el-table>
      </div>

      <div class="panel trend-panel">
        <div class="panel-hd">
          <h3>近 30 日申请趋势</h3>
          <span class="muted">按申请创建日统计</span>
        </div>
        <div class="chart-box trend">
          <v-chart :option="trendOption" autoresize />
        </div>
        <el-table :data="trendTable" size="small" stripe max-height="220" empty-text="近 30 日暂无申请">
          <el-table-column prop="day" label="日期" min-width="110" />
          <el-table-column prop="moveIn" label="迁入" width="72" align="right" />
          <el-table-column prop="moveOut" label="迁出" width="72" align="right" />
          <el-table-column prop="total" label="合计" width="72" align="right" />
        </el-table>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { BarChart, LineChart, PieChart } from 'echarts/charts'
import {
  GridComponent,
  LegendComponent,
  TooltipComponent,
} from 'echarts/components'
import VChart from 'vue-echarts'
import { ElMessage } from 'element-plus'
import { exportExcel } from '@/utils/exportExcel'
import { getMoveStats, type MoveStats } from '@/api/household'

use([CanvasRenderer, PieChart, BarChart, LineChart, TooltipComponent, LegendComponent, GridComponent])

const router = useRouter()
const loading = ref(false)
const data = ref<MoveStats>({})

const PIE_COLORS = ['#1677ff', '#52c41a', '#faad14', '#ff4d4f']

type KpiItem = {
  key: string
  label: string
  value: number
  tone?: string
  hint?: string
  to?: string
}

const applyKpis = computed<KpiItem[]>(() => {
  const d = data.value
  return [
    { key: 'pending', label: '待审批', value: num(d.pendingTotal), tone: 'warn', hint: '点击去审批', to: '/property/move/approve' },
    { key: 'approved', label: '已通过', value: num(d.approvedTotal), tone: 'ok' },
    { key: 'rejected', label: '已驳回', value: num(d.rejectedTotal), tone: 'muted' },
    { key: 'inPending', label: '迁入待审', value: num(d.moveInPending), tone: 'warn', to: '/property/move/approve' },
    { key: 'outPending', label: '迁出待审', value: num(d.moveOutPending), tone: 'warn', to: '/property/move/approve' },
  ]
})

const householdKpis = computed<KpiItem[]>(() => {
  const d = data.value
  return [
    { key: 'inOk', label: '迁入已通过', value: num(d.moveInApproved), tone: 'ok' },
    { key: 'outOk', label: '迁出已通过', value: num(d.moveOutApproved), tone: 'ok' },
    { key: 'active', label: '在住户数', value: num(d.activeHousehold), tone: 'primary', to: '/property/residents' },
    { key: 'hhPending', label: '住户待审核', value: num(d.pendingHousehold), tone: 'warn', to: '/property/residents' },
    {
      key: 'moved',
      label: '迁出中/已迁出',
      value: num(d.movingOutHousehold) + num(d.movedOutHousehold),
      tone: 'muted',
      hint: `迁出中 ${num(d.movingOutHousehold)} · 已迁出 ${num(d.movedOutHousehold)}`,
      to: '/property/residents',
    },
  ]
})

const identityRows = computed(() =>
  (data.value.byIdentity || []).map((r) => ({
    label: r.label || (r.name === 'TENANT' ? '租客' : '业主'),
    value: num(r.value),
  })),
)

const trendRows = computed(() =>
  (data.value.recentTrend || []).map((r) => ({
    day: String(r.day || '').slice(0, 10),
    moveIn: num(r.moveIn),
    moveOut: num(r.moveOut),
    total: num(r.total),
  })),
)

/** 表格只展示有申请的日期，避免 30 行全零 */
const trendTable = computed(() => trendRows.value.filter((r) => r.total > 0).slice().reverse())

const identityPieOption = computed(() => {
  const rows = identityRows.value.filter((x) => x.value > 0)
  return {
    color: PIE_COLORS,
    tooltip: { trigger: 'item', formatter: '{b}：{c}（{d}%）' },
    legend: { bottom: 0, textStyle: { fontSize: 11 } },
    series: [
      {
        type: 'pie',
        radius: ['40%', '68%'],
        center: ['50%', '46%'],
        itemStyle: { borderRadius: 4, borderColor: '#fff', borderWidth: 2 },
        label: { formatter: '{b}\n{c}', fontSize: 11 },
        data: rows.length
          ? rows.map((x) => ({ name: x.label, value: x.value }))
          : [{ name: '暂无数据', value: 1, itemStyle: { color: '#d9d9d9' } }],
      },
    ],
  }
})

const trendOption = computed(() => {
  const rows = trendRows.value
  return {
    color: ['#1677ff', '#52c41a'],
    tooltip: { trigger: 'axis' },
    legend: { data: ['迁入', '迁出'], top: 0, textStyle: { fontSize: 11 } },
    grid: { left: 36, right: 16, top: 32, bottom: 28 },
    xAxis: {
      type: 'category',
      data: rows.map((r) => r.day.slice(5)),
      axisLabel: { fontSize: 10, color: '#8f959e' },
      axisLine: { lineStyle: { color: '#e8eaed' } },
    },
    yAxis: {
      type: 'value',
      minInterval: 1,
      splitLine: { lineStyle: { color: '#f0f2f5' } },
      axisLabel: { fontSize: 10, color: '#8f959e' },
    },
    series: [
      { name: '迁入', type: 'bar', barMaxWidth: 14, data: rows.map((r) => r.moveIn) },
      { name: '迁出', type: 'bar', barMaxWidth: 14, data: rows.map((r) => r.moveOut) },
    ],
  }
})

function num(v: unknown) {
  const n = Number(v)
  return Number.isFinite(n) ? n : 0
}

function go(path: string) {
  router.push(path)
}

async function load() {
  loading.value = true
  try {
    data.value = (await getMoveStats()) || {}
  } catch {
    data.value = {}
    ElMessage.error('迁移统计加载失败，请确认已登录物业账号且 auth 服务可用')
  } finally {
    loading.value = false
  }
}

function onExport() {
  const d = data.value
  const rows = [
    { 指标: '待审批', 数值: num(d.pendingTotal) },
    { 指标: '已通过', 数值: num(d.approvedTotal) },
    { 指标: '已驳回', 数值: num(d.rejectedTotal) },
    { 指标: '迁入待审', 数值: num(d.moveInPending) },
    { 指标: '迁出待审', 数值: num(d.moveOutPending) },
    { 指标: '迁入已通过', 数值: num(d.moveInApproved) },
    { 指标: '迁出已通过', 数值: num(d.moveOutApproved) },
    { 指标: '在住户数', 数值: num(d.activeHousehold) },
    { 指标: '住户待审核', 数值: num(d.pendingHousehold) },
    { 指标: '迁出中住户', 数值: num(d.movingOutHousehold) },
    { 指标: '已迁出住户', 数值: num(d.movedOutHousehold) },
    ...identityRows.value.map((r) => ({ 指标: `身份-${r.label}`, 数值: r.value })),
    ...trendTable.value.map((r) => ({
      指标: `趋势-${r.day}`,
      数值: `迁入${r.moveIn}/迁出${r.moveOut}/合计${r.total}`,
    })),
  ]
  exportExcel(rows, '迁移统计.xlsx')
}

onMounted(load)
</script>

<style scoped>
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
  margin-bottom: 16px;
}
.page-header h2 {
  margin: 0;
  font-size: 18px;
  color: #1f2329;
}
.sub {
  margin: 6px 0 0;
  color: #8f959e;
  font-size: 13px;
}
.actions {
  display: flex;
  gap: 8px;
  flex-shrink: 0;
}

.section-label {
  margin: 4px 0 8px;
  font-size: 13px;
  font-weight: 600;
  color: #646a73;
}

.kpi-row {
  display: grid;
  gap: 10px;
  margin-bottom: 14px;
}
.kpi-row.cols-5 {
  grid-template-columns: repeat(5, minmax(0, 1fr));
}
.kpi-card {
  background: #fff;
  border: 1px solid #e8eaed;
  border-radius: 8px;
  padding: 14px 16px;
  min-height: 92px;
  box-sizing: border-box;
  box-shadow: 0 1px 4px rgba(31, 35, 41, 0.04);
}
.kpi-card.clickable {
  cursor: pointer;
  transition: border-color 0.15s, box-shadow 0.15s;
}
.kpi-card.clickable:hover {
  border-color: #91caff;
  box-shadow: 0 2px 8px rgba(22, 119, 255, 0.1);
}
.kpi-label {
  font-size: 13px;
  color: #646a73;
  line-height: 1.2;
}
.kpi-value {
  margin-top: 10px;
  font-size: 28px;
  font-weight: 700;
  line-height: 1;
  color: #1f2329;
}
.kpi-value.warn { color: #d48806; }
.kpi-value.ok { color: #389e0d; }
.kpi-value.primary { color: #1677ff; }
.kpi-value.muted { color: #8f959e; }
.kpi-hint {
  margin-top: 8px;
  font-size: 12px;
  color: #8f959e;
  line-height: 1.3;
}

.charts {
  display: grid;
  grid-template-columns: minmax(260px, 34%) 1fr;
  gap: 12px;
  align-items: stretch;
}
.panel {
  background: #fff;
  border: 1px solid #e8eaed;
  border-radius: 8px;
  padding: 14px;
  box-shadow: 0 1px 4px rgba(31, 35, 41, 0.04);
  min-height: 420px;
  display: flex;
  flex-direction: column;
}
.panel-hd {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}
.panel-hd h3 {
  margin: 0;
  font-size: 14px;
  font-weight: 600;
  color: #1f2329;
}
.muted {
  color: #8f959e;
  font-size: 12px;
}
.chart-box {
  height: 220px;
  margin-bottom: 8px;
}
.chart-box.trend {
  height: 240px;
}

@media (max-width: 1200px) {
  .kpi-row.cols-5 {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
  .charts {
    grid-template-columns: 1fr;
  }
}
</style>
