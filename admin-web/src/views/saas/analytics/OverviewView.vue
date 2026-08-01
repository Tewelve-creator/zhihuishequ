<template>
  <div class="an-page">
    <div class="an-shell">
      <header class="page-hd">
        <div>
          <h1 class="page-title">运营总览</h1>
          <p class="page-desc">
            统计周期近 {{ filter.days }} 天 · 口径：运营报表 + 财务汇总 + 风险摘要
          </p>
        </div>
      </header>

      <AnalyticsFilterBar
        v-model="filter"
        :loading="loading"
        :show-export="false"
        show-compare
        @query="load"
        @reset="load"
      />

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
            <h2>社区报修排行</h2>
            <span class="meta">单位：单 · 近 {{ filter.days }} 天</span>
          </div>
          <v-chart
            v-if="rankOpt"
            class="chart"
            :option="rankOpt"
            autoresize
          />
          <el-empty v-else description="暂无报修排行数据" :image-size="64" />
        </section>

        <section class="panel">
          <div class="panel-hd">
            <h2>营收月度趋势</h2>
            <span class="meta">单位：元 · SaaS 已付订单</span>
          </div>
          <v-chart
            v-if="trendOpt"
            class="chart"
            :option="trendOpt"
            autoresize
          />
          <el-empty v-else description="暂无营收趋势" :image-size="64" />
        </section>
      </div>

      <div class="grid-2">
        <section class="panel">
          <div class="panel-hd">
            <h2>租户健康度</h2>
            <span class="meta">来自运营报表活跃状态</span>
          </div>
          <div class="health">
            <div class="health-item">
              <span>租户总数</span>
              <strong>{{ formatNum(ops.tenantCount) }}</strong>
            </div>
            <div class="health-item">
              <span>活跃租户</span>
              <strong class="ok">{{ formatNum(ops.activeTenantCount) }}</strong>
            </div>
            <div class="health-item">
              <span>活跃占比</span>
              <strong>{{ activeRate }}</strong>
            </div>
          </div>
        </section>

        <section class="panel">
          <div class="panel-hd">
            <h2>异常事项</h2>
            <span class="meta">风险摘要 · 只读</span>
          </div>
          <ul class="issues">
            <li>
              <span>在办工单</span>
              <strong>{{ formatNum(risk.openOrders) }}</strong>
            </li>
            <li>
              <span>超时工单</span>
              <strong class="danger">{{ formatNum(risk.overdueOrders) }}</strong>
            </li>
            <li>
              <span>30 日内到期租户</span>
              <strong class="warn">{{ formatNum(risk.expiringTenants) }}</strong>
            </li>
            <li>
              <span>AI 调用失败（近 {{ filter.days }} 天）</span>
              <strong>{{ formatNum(ai.failCalls) }}</strong>
            </li>
          </ul>
        </section>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { BarChart, LineChart } from 'echarts/charts'
import { GridComponent, TooltipComponent, LegendComponent } from 'echarts/components'
import VChart from 'vue-echarts'
import AnalyticsFilterBar, { type AnalyticsFilter } from '@/components/analytics/AnalyticsFilterBar.vue'
import { ANALYTICS_COLORS, formatMoney, formatNum, formatPct, momChange } from '@/config/analytics'
import { getOpsReport, getAiReport, getRiskSummary } from '@/api/platformHub'
import { financeSummary, listAiQuotaOverview } from '@/api/saas'

use([CanvasRenderer, BarChart, LineChart, GridComponent, TooltipComponent, LegendComponent])

const loading = ref(false)
const filter = reactive<AnalyticsFilter>({ days: 30 })
const ops = ref<any>({})
const finance = ref<any>({})
const risk = ref<any>({})
const ai = ref<any>({})
const quotaRows = ref<any[]>([])

const activeRate = computed(() => {
  const t = Number(ops.value.tenantCount) || 0
  const a = Number(ops.value.activeTenantCount) || 0
  if (!t) return '0%'
  return formatPct((a / t) * 100)
})

const mom = computed(() => momChange(finance.value.monthlyTrend || []))

const quotaUsage = computed(() => {
  const rows = quotaRows.value
  const q = rows.reduce((s, r) => s + (Number(r.aiQuota) || 0), 0)
  const u = rows.reduce((s, r) => s + (Number(r.todayAiCalls ?? r.todayCalls) || 0), 0)
  if (!q) return null
  return formatPct((u / q) * 100)
})

const kpis = computed(() => {
  const paid = Number(finance.value.paidAmount) || 0
  const refund = Number(finance.value.refundAmount) || 0
  const items = [
    { label: '租户总数', value: formatNum(ops.value.tenantCount), sub: `活跃 ${formatNum(ops.value.activeTenantCount)}` },
    { label: '报修工单', value: formatNum(ops.value.repairOrderCount), sub: `近 ${filter.days} 天新增` },
    {
      label: '本期营收（已付）',
      value: formatMoney(paid),
      sub:
        filter.compare === 'mom' && mom.value != null
          ? `环比 ${mom.value > 0 ? '+' : ''}${mom.value}%`
          : `净收入 ${formatMoney(paid - refund)}`,
    },
    {
      label: 'AI 调用量',
      value: formatNum(ai.value.totalCalls),
      sub: quotaUsage.value ? `额度使用率 ${quotaUsage.value}（今日/配额）` : `失败 ${formatNum(ai.value.failCalls)}`,
    },
    {
      label: '风险事项',
      value: formatNum((Number(risk.value.overdueOrders) || 0) + (Number(risk.value.expiringTenants) || 0)),
      sub: `超时工单 ${formatNum(risk.value.overdueOrders)}`,
    },
    {
      label: '公告 / 活动',
      value: `${formatNum(ops.value.noticeCount)} / ${formatNum(ops.value.activityCount)}`,
      sub: `近 ${filter.days} 天`,
    },
  ]
  return items
})

const filteredRank = computed(() => {
  let rows = ops.value.byTenantRepair || []
  if (filter.tenantId) {
    rows = rows.filter((r: any) => Number(r.tenantId) === Number(filter.tenantId))
  }
  return rows.slice(0, 10)
})

const rankOpt = computed(() => {
  const rows = filteredRank.value
  if (!rows.length) return null
  return {
    color: [...ANALYTICS_COLORS.series],
    tooltip: { trigger: 'axis' },
    grid: { left: 48, right: 16, top: 16, bottom: 48 },
    xAxis: {
      type: 'category',
      data: rows.map((r: any) => r.tenantName),
      axisLabel: { rotate: 30, color: '#667085', fontSize: 11 },
    },
    yAxis: { type: 'value', name: '单', nameTextStyle: { color: '#667085' } },
    series: [
      {
        type: 'bar',
        data: rows.map((r: any) => Number(r.cnt) || 0),
        barMaxWidth: 28,
        itemStyle: { color: ANALYTICS_COLORS.primary, borderRadius: [4, 4, 0, 0] },
      },
    ],
  }
})

const trendOpt = computed(() => {
  const rows = finance.value.monthlyTrend || []
  if (!rows.length) return null
  return {
    color: [...ANALYTICS_COLORS.series],
    tooltip: { trigger: 'axis' },
    grid: { left: 56, right: 16, top: 24, bottom: 32 },
    xAxis: { type: 'category', data: rows.map((r: any) => r.month) },
    yAxis: { type: 'value', name: '元' },
    series: [
      {
        name: '已付金额',
        type: 'line',
        smooth: true,
        data: rows.map((r: any) => Number(r.paidAmount) || 0),
        itemStyle: { color: ANALYTICS_COLORS.primary },
      },
    ],
  }
})

async function load() {
  loading.value = true
  try {
    const [o, f, r, a, q] = await Promise.all([
      getOpsReport(filter.days),
      financeSummary(),
      getRiskSummary(),
      getAiReport(filter.days),
      listAiQuotaOverview().catch(() => []),
    ])
    ops.value = o || {}
    finance.value = f || {}
    risk.value = r || {}
    ai.value = a || {}
    quotaRows.value = q || []
  } catch {
    ops.value = {}
    finance.value = {}
    risk.value = {}
    ai.value = {}
    quotaRows.value = []
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
.kpi-row {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: 10px;
  margin-bottom: 12px;
}
.kpi {
  background: #fff;
  border: 1px solid #e4e7ec;
  border-radius: 8px;
  padding: 12px 14px;
  min-width: 0;
}
.kpi-label {
  display: block;
  font-size: 12px;
  color: #667085;
}
.kpi-value {
  display: block;
  margin-top: 6px;
  font-size: 18px;
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
  min-height: 280px;
}
.panel-hd {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  gap: 8px;
  margin-bottom: 8px;
}
.panel-hd h2 {
  margin: 0;
  font-size: 14px;
  font-weight: 600;
  color: #182230;
}
.meta {
  font-size: 12px;
  color: #98a2b3;
}
.chart {
  height: 240px;
  width: 100%;
}
.health {
  display: grid;
  gap: 12px;
  padding-top: 8px;
}
.health-item {
  display: flex;
  justify-content: space-between;
  padding: 12px 0;
  border-bottom: 1px solid #eef1f4;
  font-size: 13px;
  color: #667085;
}
.health-item strong {
  color: #182230;
  font-size: 16px;
}
.health-item strong.ok {
  color: #067647;
}
.issues {
  list-style: none;
  margin: 0;
  padding: 0;
}
.issues li {
  display: flex;
  justify-content: space-between;
  padding: 12px 0;
  border-bottom: 1px solid #eef1f4;
  font-size: 13px;
  color: #667085;
}
.issues strong {
  color: #182230;
}
.issues .danger {
  color: #b42318;
}
.issues .warn {
  color: #b54708;
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
@media (max-width: 375px) {
  .kpi-row {
    grid-template-columns: 1fr;
  }
}
</style>
