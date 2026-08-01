<template>
  <div class="page-card">
    <div class="page-header">
      <div>
        <h2>财务与营收统计</h2>
        <p class="sub">
          汇总 SaaS 订单营收 ·
          <router-link to="/admin/saas/orders" class="link">查看订单</router-link>
          ·
          <router-link to="/admin/saas/refunds" class="link">退款对账</router-link>
        </p>
      </div>
      <el-button :loading="loading" @click="load">刷新</el-button>
    </div>

    <el-row :gutter="12" class="kpi-grid">
      <el-col v-for="k in kpis" :key="k.label" :xs="12" :sm="8" :md="6" :lg="4">
        <div class="kpi-card">
          <div class="kpi-label">{{ k.label }}</div>
          <div class="kpi-value">{{ k.value }}</div>
        </div>
      </el-col>
    </el-row>

    <el-row :gutter="16" style="margin-top: 8px">
      <el-col :xs="24" :md="10">
        <div class="chart-card">
          <h3 class="section-title">按订单类型（已付金额）</h3>
          <v-chart v-if="byType.length" class="chart" :option="typeOpt" autoresize />
          <el-empty v-else description="暂无数据" :image-size="64" />
        </div>
      </el-col>
      <el-col :xs="24" :md="14">
        <div class="chart-card">
          <h3 class="section-title">近 6 月趋势</h3>
          <v-chart v-if="monthlyTrend.length" class="chart" :option="trendOpt" autoresize />
          <el-empty v-else description="暂无数据" :image-size="64" />
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { LineChart, PieChart, BarChart } from 'echarts/charts'
import { GridComponent, TooltipComponent, LegendComponent } from 'echarts/components'
import VChart from 'vue-echarts'
import { financeSummary } from '@/api/saas'

use([CanvasRenderer, LineChart, PieChart, BarChart, GridComponent, TooltipComponent, LegendComponent])

const TYPE_LABEL: Record<string, string> = { new: '新购', renew: '续费', upgrade: '升级' }

const loading = ref(false)
const data = ref<Record<string, any>>({})

const byType = computed(() => data.value.byType || [])
const monthlyTrend = computed(() => data.value.monthlyTrend || [])

const kpis = computed(() => {
  const d = data.value
  const paid = Number(d.paidAmount) || 0
  const refund = Number(d.refundAmount) || 0
  return [
    { label: '已付金额', value: fmtMoney(d.paidAmount) },
    { label: '已付笔数', value: d.paidCount ?? 0 },
    { label: '退款金额', value: fmtMoney(d.refundAmount) },
    { label: '退款笔数', value: d.refundCount ?? 0 },
    { label: '待付金额', value: fmtMoney(d.pendingAmount) },
    { label: '净营收', value: fmtMoney(paid - refund) },
  ]
})

const typeOpt = computed(() => ({
  tooltip: { trigger: 'item' },
  legend: { bottom: 0 },
  series: [
    {
      type: 'pie',
      radius: ['40%', '68%'],
      data: byType.value.map((r: any) => ({
        name: TYPE_LABEL[r.type] || r.type || '-',
        value: Number(r.amount) || 0,
      })),
    },
  ],
}))

const trendOpt = computed(() => ({
  tooltip: { trigger: 'axis' },
  legend: { data: ['金额', '笔数'], bottom: 0 },
  grid: { left: 48, right: 48, top: 24, bottom: 40 },
  xAxis: { type: 'category', data: monthlyTrend.value.map((r: any) => r.month) },
  yAxis: [
    { type: 'value', name: '金额' },
    { type: 'value', name: '笔数', splitLine: { show: false } },
  ],
  series: [
    {
      name: '金额',
      type: 'bar',
      data: monthlyTrend.value.map((r: any) => Number(r.paidAmount) || 0),
      barMaxWidth: 28,
      itemStyle: { color: '#1677ff' },
    },
    {
      name: '笔数',
      type: 'line',
      yAxisIndex: 1,
      smooth: true,
      data: monthlyTrend.value.map((r: any) => Number(r.paidCount) || 0),
      itemStyle: { color: '#13c2c2' },
    },
  ],
}))

function fmtMoney(v: unknown) {
  const n = Number(v)
  if (Number.isNaN(n)) return '0'
  return n.toLocaleString('zh-CN', { minimumFractionDigits: 0, maximumFractionDigits: 2 })
}

async function load() {
  loading.value = true
  try {
    data.value = (await financeSummary()) || {}
  } catch {
    data.value = {}
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.sub { margin: 4px 0 0; color: #64748b; font-size: 13px; }
.link { color: #1677ff; margin: 0 2px; }
.kpi-grid { margin-bottom: 4px; }
.kpi-card {
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  padding: 14px 16px;
  margin-bottom: 12px;
}
.kpi-label { font-size: 13px; color: #64748b; }
.kpi-value { margin-top: 6px; font-size: 22px; font-weight: 700; color: #1677ff; }
.section-title { margin: 0 0 8px; font-size: 15px; font-weight: 600; }
.chart-card {
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  padding: 12px 14px;
  margin-bottom: 12px;
  min-height: 300px;
}
.chart { height: 260px; width: 100%; }
</style>
