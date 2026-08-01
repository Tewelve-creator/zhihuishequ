<template>
  <div class="an-page">
    <div class="an-shell">
      <header class="page-hd">
        <div>
          <h1 class="page-title">财务分析</h1>
          <p class="page-desc">
            币种 CNY · 口径：SaaS 订单已付 / 待付 / 已退款 ·
            <router-link class="link" to="/admin/saas/finance/refunds">退款管理</router-link>
            ·
            <router-link class="link" to="/admin/saas/finance/reconcile">对账管理</router-link>
          </p>
        </div>
      </header>

      <AnalyticsFilterBar
        v-model="filter"
        :loading="loading"
        :show-tenant="false"
        :show-export="false"
        show-compare
        @query="load"
        @reset="load"
      />
      <p class="filter-note">财务汇总接口暂不支持按天数/租户切片；时间筛选仅用于展示周期说明，环比取自月度趋势末两月。</p>

      <div v-loading="loading" class="kpi-row">
        <div v-for="k in kpis" :key="k.label" class="kpi">
          <span class="kpi-label">{{ k.label }}</span>
          <span class="kpi-value">{{ k.value }}</span>
          <span v-if="k.sub" class="kpi-sub">{{ k.sub }}</span>
        </div>
      </div>

      <div class="grid-2">
        <section class="panel">
          <div class="panel-hd">
            <h2>新购 / 续费 / 升级收入占比</h2>
            <span class="meta">已付金额 · 元</span>
          </div>
          <v-chart v-if="typeOpt" class="chart" :option="typeOpt" autoresize />
          <el-empty v-else description="暂无类型构成" :image-size="64" />
        </section>
        <section class="panel">
          <div class="panel-hd">
            <h2>月度汇总趋势</h2>
            <span class="meta">近 6 月已付</span>
          </div>
          <v-chart v-if="trendOpt" class="chart" :option="trendOpt" autoresize />
          <el-empty v-else description="暂无月度数据" :image-size="64" />
        </section>
      </div>

      <section class="panel">
        <div class="panel-hd">
          <h2>月度明细</h2>
          <span class="meta">pay_time 按月聚合</span>
        </div>
        <el-table :data="monthly" empty-text="暂无数据">
          <el-table-column prop="month" label="账期" width="120" />
          <el-table-column label="已付笔数" width="120" align="right">
            <template #default="{ row }">{{ formatNum(row.paidCount) }}</template>
          </el-table-column>
          <el-table-column label="已付金额" min-width="140" align="right">
            <template #default="{ row }">{{ formatMoney(row.paidAmount) }}</template>
          </el-table-column>
        </el-table>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { LineChart, PieChart, BarChart } from 'echarts/charts'
import { GridComponent, TooltipComponent, LegendComponent } from 'echarts/components'
import VChart from 'vue-echarts'
import AnalyticsFilterBar, { type AnalyticsFilter } from '@/components/analytics/AnalyticsFilterBar.vue'
import { ANALYTICS_COLORS, formatMoney, formatNum, momChange } from '@/config/analytics'
import { financeSummary } from '@/api/saas'

use([CanvasRenderer, LineChart, PieChart, BarChart, GridComponent, TooltipComponent, LegendComponent])

const TYPE_LABEL: Record<string, string> = { new: '新购', renew: '续费', upgrade: '升级' }

const loading = ref(false)
const filter = reactive<AnalyticsFilter>({ days: 30 })
const data = ref<any>({})

const monthly = computed(() => data.value.monthlyTrend || [])
const byType = computed(() => data.value.byType || [])
const mom = computed(() => momChange(monthly.value))

const kpis = computed(() => {
  const paid = Number(data.value.paidAmount) || 0
  const refund = Number(data.value.refundAmount) || 0
  const pending = Number(data.value.pendingAmount) || 0
  return [
    { label: '本期收入（已付）', value: formatMoney(paid), sub: `笔数 ${formatNum(data.value.paidCount)}` },
    { label: '实收金额', value: formatMoney(paid), sub: '等同已付（无渠道拆分）' },
    { label: '待收金额', value: formatMoney(pending), sub: `待付笔数 ${formatNum(data.value.pendingCount)}` },
    { label: '退款金额', value: formatMoney(refund), sub: `笔数 ${formatNum(data.value.refundCount)}` },
    { label: '净收入', value: formatMoney(paid - refund), sub: '已付 − 退款' },
    {
      label: '环比变化',
      value: mom.value == null ? '--' : `${mom.value > 0 ? '+' : ''}${mom.value}%`,
      sub: filter.compare === 'mom' || mom.value != null ? '末两月已付金额' : '数据不足',
    },
  ]
})

const typeOpt = computed(() => {
  if (!byType.value.length) return null
  return {
    color: [...ANALYTICS_COLORS.series],
    tooltip: { trigger: 'item', valueFormatter: (v: number) => formatMoney(v) },
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
  }
})

const trendOpt = computed(() => {
  if (!monthly.value.length) return null
  return {
    color: [...ANALYTICS_COLORS.series],
    tooltip: { trigger: 'axis' },
    legend: { data: ['金额', '笔数'], bottom: 0 },
    grid: { left: 56, right: 48, top: 24, bottom: 40 },
    xAxis: { type: 'category', data: monthly.value.map((r: any) => r.month) },
    yAxis: [
      { type: 'value', name: '元' },
      { type: 'value', name: '笔', splitLine: { show: false } },
    ],
    series: [
      {
        name: '金额',
        type: 'bar',
        data: monthly.value.map((r: any) => Number(r.paidAmount) || 0),
        barMaxWidth: 28,
      },
      {
        name: '笔数',
        type: 'line',
        yAxisIndex: 1,
        smooth: true,
        data: monthly.value.map((r: any) => Number(r.paidCount) || 0),
      },
    ],
  }
})

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
.an-page {
  margin: -20px;
  padding: 24px;
  min-height: calc(100% + 40px);
  background: #f5f6f8;
  box-sizing: border-box;
}
.an-shell {
  max-width: 1440px;
  margin: 0 auto;
}
.page-hd {
  margin-bottom: 12px;
}
.page-title {
  margin: 0;
  font-size: 20px;
  font-weight: 650;
  color: #182230;
}
.page-desc {
  margin: 4px 0 0;
  font-size: 13px;
  color: #667085;
}
.link {
  color: #2563eb;
  text-decoration: none;
}
.filter-note {
  margin: -6px 0 12px;
  font-size: 12px;
  color: #98a2b3;
}
.kpi-row {
  display: grid;
  grid-template-columns: repeat(6, 1fr);
  gap: 10px;
  margin-bottom: 12px;
}
.kpi {
  background: #fff;
  border: 1px solid #e4e7ec;
  border-radius: 8px;
  padding: 12px 14px;
}
.kpi-label {
  display: block;
  font-size: 12px;
  color: #667085;
}
.kpi-value {
  display: block;
  margin-top: 6px;
  font-size: 17px;
  font-weight: 650;
  color: #182230;
  word-break: break-all;
}
.kpi-sub {
  display: block;
  margin-top: 4px;
  font-size: 12px;
  color: #98a2b3;
}
.grid-2 {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  margin-bottom: 12px;
}
.panel {
  background: #fff;
  border: 1px solid #e4e7ec;
  border-radius: 8px;
  padding: 14px 16px;
  margin-bottom: 12px;
}
.panel-hd {
  display: flex;
  justify-content: space-between;
  margin-bottom: 8px;
}
.panel-hd h2 {
  margin: 0;
  font-size: 14px;
  color: #182230;
}
.meta {
  font-size: 12px;
  color: #98a2b3;
}
.chart {
  height: 260px;
  width: 100%;
}
@media (max-width: 1024px) {
  .kpi-row {
    grid-template-columns: repeat(3, 1fr);
  }
  .grid-2 {
    grid-template-columns: 1fr;
  }
}
@media (max-width: 768px) {
  .an-page {
    padding: 16px 12px;
  }
  .kpi-row {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>
