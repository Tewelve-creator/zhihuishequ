<template>
  <div class="page-card">
    <div class="page-header">
      <div>
        <h2>工单统计</h2>
        <p class="sub">办结率、处理时效、分类/类型占比（当前小区 · 按创建时间筛选）</p>
      </div>
      <div class="actions">
        <el-button :loading="loading" @click="load">刷新</el-button>
        <el-button @click="onExport">导出 Excel</el-button>
      </div>
    </div>

    <div class="filter-bar">
      <span class="filter-label">时间范围</span>
      <el-radio-group v-model="quickRange" @change="onQuickChange">
        <el-radio-button value="today">今日</el-radio-button>
        <el-radio-button value="week">本周</el-radio-button>
        <el-radio-button value="month">本月</el-radio-button>
        <el-radio-button value="custom">自定义</el-radio-button>
      </el-radio-group>
      <el-date-picker
        v-model="dateRange"
        type="daterange"
        value-format="YYYY-MM-DD"
        start-placeholder="开始日期"
        end-placeholder="结束日期"
        :disabled="quickRange !== 'custom'"
        style="width: 260px"
        @change="onCustomChange"
      />
      <span v-if="rangeText" class="range-tip">当前：{{ rangeText }}</span>
    </div>

    <el-row :gutter="12" class="kpi">
      <el-col :span="4"><el-statistic title="工单总数" :value="data.total || 0" /></el-col>
      <el-col :span="4"><el-statistic title="待处理" :value="data.pending || 0" /></el-col>
      <el-col :span="4"><el-statistic title="处理中" :value="data.processing || 0" /></el-col>
      <el-col :span="4"><el-statistic title="已办结" :value="data.done || 0" /></el-col>
      <el-col :span="4"><el-statistic title="办结率%" :value="data.finishRate || 0" :precision="1" /></el-col>
      <el-col :span="4"><el-statistic title="平均时效(时)" :value="data.avgHandleHours || 0" :precision="1" /></el-col>
    </el-row>

    <el-row :gutter="12" style="margin-top: 16px">
      <el-col :span="8">
        <div class="panel">
          <h3>工单类型占比</h3>
          <div class="chart-box">
            <v-chart :option="typePieOption" autoresize />
          </div>
          <el-table :data="data.byType || []" size="small" stripe>
            <el-table-column prop="name" label="类型" />
            <el-table-column prop="value" label="数量" width="80" />
          </el-table>
        </div>
      </el-col>
      <el-col :span="8">
        <div class="panel">
          <h3>紧急等级占比</h3>
          <div class="chart-box">
            <v-chart :option="urgencyPieOption" autoresize />
          </div>
          <el-table :data="data.byUrgency || []" size="small" stripe>
            <el-table-column prop="name" label="等级" />
            <el-table-column prop="value" label="数量" width="80" />
          </el-table>
        </div>
      </el-col>
      <el-col :span="8">
        <div class="panel">
          <h3>分类占比</h3>
          <div class="chart-box">
            <v-chart :option="categoryPieOption" autoresize />
          </div>
          <el-table :data="data.byCategory || []" size="small" stripe max-height="220">
            <el-table-column prop="name" label="分类" />
            <el-table-column prop="value" label="数量" width="80" />
          </el-table>
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import dayjs from 'dayjs'
import isoWeek from 'dayjs/plugin/isoWeek'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { PieChart } from 'echarts/charts'
import { TooltipComponent, LegendComponent } from 'echarts/components'
import VChart from 'vue-echarts'
import { ElMessage } from 'element-plus'
import { exportExcel } from '@/utils/exportExcel'
import { getRepairStats } from '@/api/repair'

dayjs.extend(isoWeek)
use([CanvasRenderer, PieChart, TooltipComponent, LegendComponent])

type QuickRange = 'today' | 'week' | 'month' | 'custom'

const loading = ref(false)
const data = ref<Awaited<ReturnType<typeof getRepairStats>> | Record<string, never>>({})
const quickRange = ref<QuickRange>('month')
const dateRange = ref<[string, string] | null>(null)

const PIE_COLORS = ['#1677ff', '#faad14', '#52c41a', '#ff4d4f', '#722ed1', '#13c2c2', '#eb2f96']

function calcQuickDates(q: QuickRange): [string, string] | null {
  const now = dayjs()
  if (q === 'today') {
    const d = now.format('YYYY-MM-DD')
    return [d, d]
  }
  if (q === 'week') {
    return [now.startOf('isoWeek').format('YYYY-MM-DD'), now.format('YYYY-MM-DD')]
  }
  if (q === 'month') {
    return [now.startOf('month').format('YYYY-MM-DD'), now.format('YYYY-MM-DD')]
  }
  return dateRange.value
}

const rangeText = computed(() => {
  const r = calcQuickDates(quickRange.value)
  if (!r?.[0] || !r?.[1]) return ''
  return r[0] === r[1] ? r[0] : `${r[0]} ~ ${r[1]}`
})

function buildPieOption(list: Array<{ name: string; value: number }> | undefined) {
  const rows = (list || []).filter((x) => Number(x.value) > 0)
  return {
    color: PIE_COLORS,
    tooltip: { trigger: 'item', formatter: '{b}：{c}（{d}%）' },
    legend: { bottom: 0, type: 'scroll', textStyle: { fontSize: 11 } },
    series: [
      {
        type: 'pie',
        radius: ['36%', '62%'],
        center: ['50%', '44%'],
        avoidLabelOverlap: true,
        itemStyle: { borderRadius: 4, borderColor: '#fff', borderWidth: 2 },
        label: { formatter: '{b}\n{c}', fontSize: 11 },
        data: rows.length
          ? rows.map((x) => ({ name: x.name, value: Number(x.value) }))
          : [{ name: '暂无数据', value: 1, itemStyle: { color: '#d9d9d9' } }],
      },
    ],
  }
}

const typePieOption = computed(() => buildPieOption(data.value.byType))
const urgencyPieOption = computed(() => buildPieOption(data.value.byUrgency))
const categoryPieOption = computed(() => buildPieOption(data.value.byCategory))

async function load() {
  const range = calcQuickDates(quickRange.value)
  if (quickRange.value === 'custom' && (!range?.[0] || !range?.[1])) {
    ElMessage.warning('请选择自定义起止日期')
    return
  }
  loading.value = true
  try {
    data.value =
      (await getRepairStats({
        startDate: range?.[0],
        endDate: range?.[1],
      })) || {}
  } catch {
    data.value = {}
  } finally {
    loading.value = false
  }
}

function onQuickChange() {
  if (quickRange.value === 'custom') {
    if (!dateRange.value) {
      const now = dayjs()
      dateRange.value = [now.startOf('month').format('YYYY-MM-DD'), now.format('YYYY-MM-DD')]
    }
    load()
    return
  }
  dateRange.value = calcQuickDates(quickRange.value)
  load()
}

function onCustomChange() {
  if (quickRange.value !== 'custom') return
  load()
}

function onExport() {
  const range = calcQuickDates(quickRange.value)
  if (quickRange.value === 'custom' && (!range?.[0] || !range?.[1])) {
    ElMessage.warning('请选择自定义起止日期')
    return
  }
  const period = range?.[0] && range?.[1]
    ? range[0] === range[1]
      ? range[0]
      : `${range[0]}_${range[1]}`
    : '全部'
  const rows = [
    { 指标: '统计区间', 数值: rangeText.value || '全部' },
    { 指标: '工单总数', 数值: data.value.total ?? 0 },
    { 指标: '待处理', 数值: data.value.pending ?? 0 },
    { 指标: '处理中', 数值: data.value.processing ?? 0 },
    { 指标: '已办结', 数值: data.value.done ?? 0 },
    { 指标: '办结率%', 数值: data.value.finishRate ?? 0 },
    { 指标: '平均处理时效(小时)', 数值: data.value.avgHandleHours ?? 0 },
    { 指标: '报修数', 数值: data.value.repairCount ?? 0 },
    { 指标: '报事数', 数值: data.value.reportCount ?? 0 },
    ...(data.value.byType || []).map((r) => ({ 指标: `类型-${r.name}`, 数值: r.value })),
    ...(data.value.byUrgency || []).map((r) => ({ 指标: `紧急-${r.name}`, 数值: r.value })),
    ...(data.value.byCategory || []).map((r) => ({ 指标: `分类-${r.name}`, 数值: r.value })),
  ]
  exportExcel(rows, `工单统计_${period}.xlsx`)
}

onMounted(() => {
  dateRange.value = calcQuickDates('month')
  load()
})
</script>

<style scoped>
.page-header { display: flex; justify-content: space-between; margin-bottom: 12px; }
.page-header h2 { margin: 0; font-size: 18px; }
.sub { margin: 4px 0 0; color: #64748b; font-size: 13px; }
.actions { display: flex; gap: 8px; }
.filter-bar {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
  margin-bottom: 14px;
  padding: 10px 12px;
  background: #f8fafc;
  border: 1px solid #eef2f7;
  border-radius: 8px;
}
.filter-label { color: #64748b; font-size: 13px; }
.range-tip { color: #94a3b8; font-size: 12px; }
.kpi { margin-bottom: 8px; }
.panel {
  background: #fff;
  border: 1px solid #eef2f7;
  border-radius: 10px;
  padding: 12px;
  min-height: 420px;
}
.panel h3 { margin: 0 0 10px; font-size: 14px; }
.chart-box { height: 220px; width: 100%; margin-bottom: 8px; }
</style>
