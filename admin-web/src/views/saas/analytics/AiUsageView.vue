<template>
  <div class="an-page">
    <div class="an-shell">
      <header class="page-hd">
        <div>
          <h1 class="page-title">AI 用量分析</h1>
          <p class="page-desc">
            调用量与额度监控 · 近 {{ filter.days }} 天 · 阈值：&lt;{{ AI_USAGE_THRESHOLDS.warn }}% 正常 /
            {{ AI_USAGE_THRESHOLDS.warn }}–{{ AI_USAGE_THRESHOLDS.alert }}% 提醒 /
            &gt;{{ AI_USAGE_THRESHOLDS.alert }}% 预警
          </p>
        </div>
      </header>

      <AnalyticsFilterBar
        v-model="filter"
        :loading="loading"
        :can-export="!!exportRows.length"
        :exporting="exporting"
        @query="load"
        @reset="load"
        @export="onExport"
      />

      <el-tabs v-model="tab" class="tabs" @tab-change="onTab">
        <el-tab-pane label="使用概览" name="overview" />
        <el-tab-pane label="租户用量" name="tenants" />
        <el-tab-pane label="功能消耗" name="scenes" />
        <el-tab-pane label="预警记录" name="alerts" />
      </el-tabs>

      <div v-loading="loading" class="kpi-row">
        <div v-for="k in kpis" :key="k.label" class="kpi">
          <span class="kpi-label">{{ k.label }}</span>
          <span class="kpi-value">{{ k.value }}</span>
          <span v-if="k.sub" class="kpi-sub">{{ k.sub }}</span>
        </div>
      </div>

      <section v-if="tab === 'overview'" class="panel">
        <div class="panel-hd">
          <h2>调用趋势</h2>
          <span class="meta">单位：次 · 来自 ai_call_log</span>
        </div>
        <v-chart v-if="trendOpt" class="chart" :option="trendOpt" autoresize />
        <el-empty v-else description="暂无趋势数据" :image-size="64" />
      </section>

      <section v-else-if="tab === 'tenants'" class="panel">
        <el-table :data="tenantRows" empty-text="暂无租户用量">
          <el-table-column prop="name" label="租户" min-width="140" />
          <el-table-column prop="packageName" label="套餐" width="120" show-overflow-tooltip />
          <el-table-column label="额度" width="100" align="right">
            <template #default="{ row }">{{ formatNum(row.aiQuota) }}</template>
          </el-table-column>
          <el-table-column label="已使用（今日）" width="120" align="right">
            <template #default="{ row }">{{ formatNum(usedOf(row)) }}</template>
          </el-table-column>
          <el-table-column label="使用率" min-width="180">
            <template #default="{ row }">
              <div class="rate-cell">
                <el-progress
                  :percentage="Math.min(100, Math.round(usageRaw(row)))"
                  :stroke-width="8"
                  :show-text="false"
                  :color="aiUsageColor(aiUsageBand(usageRaw(row)))"
                />
                <span>{{ Math.round(usageRaw(row)) }}% · {{ aiUsageBandLabel(aiUsageBand(usageRaw(row))) }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="预计耗尽" width="110">
            <template #default>--</template>
          </el-table-column>
          <el-table-column label="预警状态" width="100">
            <template #default="{ row }">
              <span class="band" :class="aiUsageBand(usageRaw(row))">
                {{ aiUsageBandLabel(aiUsageBand(usageRaw(row))) }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="100" fixed="right">
            <template #default="{ row }">
              <el-button link class="ops" @click="goQuota(row)">调整额度</el-button>
            </template>
          </el-table-column>
        </el-table>
      </section>

      <section v-else-if="tab === 'scenes'" class="panel">
        <div class="grid-2">
          <div>
            <div class="panel-hd"><h2>按功能/场景</h2></div>
            <v-chart v-if="sceneOpt" class="chart" :option="sceneOpt" autoresize />
            <el-empty v-else description="暂无场景数据" :image-size="64" />
          </div>
          <el-table :data="sceneRows" height="280" empty-text="暂无数据">
            <el-table-column prop="scene" label="场景" min-width="120" />
            <el-table-column label="调用" width="90" align="right">
              <template #default="{ row }">{{ formatNum(row.total ?? row.cnt) }}</template>
            </el-table-column>
            <el-table-column label="失败" width="90" align="right">
              <template #default="{ row }">{{ formatNum(failOf(row)) }}</template>
            </el-table-column>
          </el-table>
        </div>
      </section>

      <section v-else class="panel">
        <div class="panel-hd">
          <h2>调用明细（预警相关）</h2>
          <span class="meta">失败与全部调用日志</span>
        </div>
        <el-table v-loading="logLoading" :data="logs" empty-text="暂无调用记录">
          <el-table-column prop="tenantName" label="租户" width="140" />
          <el-table-column prop="scene" label="场景" width="120" />
          <el-table-column prop="successLabel" label="结果" width="80" />
          <el-table-column prop="costMs" label="耗时ms" width="90" />
          <el-table-column prop="remark" label="备注" min-width="140" show-overflow-tooltip />
          <el-table-column prop="createTime" label="时间" width="170" />
        </el-table>
        <div class="pager">
          <el-pagination
            v-model:current-page="page"
            v-model:page-size="size"
            background
            layout="total, prev, pager, next"
            :total="total"
            @current-change="loadLogs"
            @size-change="loadLogs"
          />
        </div>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { LineChart, PieChart } from 'echarts/charts'
import { GridComponent, TooltipComponent, LegendComponent } from 'echarts/components'
import VChart from 'vue-echarts'
import AnalyticsFilterBar, { type AnalyticsFilter } from '@/components/analytics/AnalyticsFilterBar.vue'
import {
  AI_USAGE_THRESHOLDS,
  ANALYTICS_COLORS,
  aiUsageBand,
  aiUsageBandLabel,
  aiUsageColor,
  formatNum,
  formatPct,
} from '@/config/analytics'
import { getAiReport } from '@/api/platformHub'
import {
  getAiCallStats,
  listAiQuotaOverview,
  listPackages,
  pageAiCalls,
  pageTenants,
} from '@/api/saas'
import { exportExcel } from '@/utils/exportExcel'

use([CanvasRenderer, LineChart, PieChart, GridComponent, TooltipComponent, LegendComponent])

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const logLoading = ref(false)
const exporting = ref(false)
const filter = reactive<AnalyticsFilter>({ days: 30 })
const tab = ref('overview')
const stats = ref<any>({})
const report = ref<any>({})
const quotaRows = ref<any[]>([])
const logs = ref<any[]>([])
const page = ref(1)
const size = ref(20)
const total = ref(0)

const failRate = computed(() => {
  const t = Number(stats.value.totalCalls || report.value.totalCalls) || 0
  const f = Number(stats.value.failCalls || report.value.failCalls) || 0
  if (!t) return 0
  return (f / t) * 100
})

const quotaKpi = computed(() => {
  const rows = quotaRows.value
  const q = rows.reduce((s, r) => s + (Number(r.aiQuota) || 0), 0)
  const u = rows.reduce((s, r) => s + usedOf(r), 0)
  const over = rows.filter((r) => usageRaw(r) > 100).length
  return {
    quota: q,
    used: u,
    rate: q > 0 ? (u / q) * 100 : 0,
    over,
  }
})

const kpis = computed(() => [
  {
    label: '窗口调用量',
    value: formatNum(stats.value.totalCalls ?? report.value.totalCalls),
    sub: `近 ${filter.days} 天 · 今日 ${formatNum(stats.value.todayCalls)}`,
  },
  {
    label: '已分配额度',
    value: formatNum(quotaKpi.value.quota),
    sub: '各租户 ai_quota 合计',
  },
  {
    label: '整体使用率',
    value: formatPct(quotaKpi.value.rate),
    sub: '今日调用 / 总额度',
  },
  {
    label: '预计超额租户',
    value: formatNum(quotaKpi.value.over),
    sub: '使用率 > 100%',
  },
  {
    label: '调用失败率',
    value: formatPct(failRate.value),
    sub: `失败 ${formatNum(stats.value.failCalls ?? report.value.failCalls)}`,
  },
])

const tenantRows = computed(() => {
  let rows = quotaRows.value
  if (filter.tenantId) rows = rows.filter((r) => Number(r.id) === Number(filter.tenantId))
  return rows
})

const sceneRows = computed(() => stats.value.byScene || report.value.byScene || [])

const exportRows = computed(() => {
  if (tab.value === 'tenants') return tenantRows.value
  if (tab.value === 'scenes') return sceneRows.value
  if (tab.value === 'alerts') return logs.value
  return stats.value.recentTrend || []
})

const trendOpt = computed(() => {
  const rows = stats.value.recentTrend || []
  if (!rows.length) return null
  return {
    color: [...ANALYTICS_COLORS.series],
    tooltip: { trigger: 'axis' },
    legend: { data: ['调用', '成功'], bottom: 0 },
    grid: { left: 48, right: 16, top: 24, bottom: 40 },
    xAxis: { type: 'category', data: rows.map((r: any) => String(r.day).slice(0, 10)) },
    yAxis: { type: 'value', name: '次' },
    series: [
      {
        name: '调用',
        type: 'line',
        smooth: true,
        data: rows.map((r: any) => Number(r.total) || 0),
      },
      {
        name: '成功',
        type: 'line',
        smooth: true,
        data: rows.map((r: any) => Number(r.successCnt) || 0),
      },
    ],
  }
})

const sceneOpt = computed(() => {
  const rows = sceneRows.value
  if (!rows.length) return null
  return {
    color: [...ANALYTICS_COLORS.series],
    tooltip: { trigger: 'item' },
    series: [
      {
        type: 'pie',
        radius: ['40%', '68%'],
        data: rows.map((r: any) => ({
          name: r.scene || 'unknown',
          value: Number(r.total ?? r.cnt) || 0,
        })),
      },
    ],
  }
})

function usedOf(row: any) {
  return Number(row.todayAiCalls ?? row.todayCalls ?? 0)
}
function usageRaw(row: any) {
  const q = Number(row.aiQuota) || 0
  if (q <= 0) return usedOf(row) > 0 ? 101 : 0
  return (usedOf(row) / q) * 100
}
function failOf(row: any) {
  if (row.failCnt != null) return Number(row.failCnt) || 0
  const total = Number(row.total ?? row.cnt) || 0
  const ok = Number(row.successCnt) || 0
  return Math.max(0, total - ok)
}

function goQuota(row: any) {
  router.push({ path: '/admin/saas/ai-quota', query: { keyword: row.name || '' } })
}

function onTab(name: string | number) {
  const t = String(name)
  router.replace({ query: { ...route.query, tab: t } })
  if (t === 'alerts') loadLogs()
}

async function loadLogs() {
  logLoading.value = true
  try {
    const data = await pageAiCalls({
      tenantId: filter.tenantId,
      page: page.value,
      size: size.value,
    })
    logs.value = (data?.records || []).map((r: any) => ({
      ...r,
      successLabel: r.success === 1 || r.success === true ? '成功' : '失败',
    }))
    total.value = Number(data?.total || 0)
  } catch {
    logs.value = []
    total.value = 0
  } finally {
    logLoading.value = false
  }
}

async function load() {
  loading.value = true
  try {
    const [s, a, q, tenants, pkgs] = await Promise.all([
      getAiCallStats(filter.days),
      getAiReport(filter.days),
      listAiQuotaOverview(),
      pageTenants({ page: 1, size: 500 }).catch(() => ({ records: [] })),
      listPackages().catch(() => []),
    ])
    stats.value = s || {}
    report.value = a || {}
    const pkgMap: Record<number, string> = {}
    for (const p of pkgs || []) pkgMap[p.id] = p.name
    const tPkg: Record<number, number> = {}
    for (const t of tenants?.records || []) tPkg[t.id] = t.packageId
    quotaRows.value = (q || []).map((r: any) => ({
      ...r,
      packageName: pkgMap[tPkg[r.id]] || '--',
    }))
    if (tab.value === 'alerts') await loadLogs()
  } catch {
    stats.value = {}
    report.value = {}
    quotaRows.value = []
  } finally {
    loading.value = false
  }
}

function onExport() {
  if (!exportRows.value.length) {
    ElMessage.warning('暂无数据')
    return
  }
  exporting.value = true
  try {
    exportExcel(exportRows.value as any, `AI用量-${tab.value}`, 'AI')
  } finally {
    exporting.value = false
  }
}

onMounted(() => {
  const t = String(route.query.tab || '')
  if (['overview', 'tenants', 'scenes', 'alerts'].includes(t)) tab.value = t
  load()
})
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
.tabs :deep(.el-tabs__item.is-active) {
  color: #2563eb;
}
.tabs :deep(.el-tabs__active-bar) {
  background: #2563eb;
}
.kpi-row {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
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
  font-size: 18px;
  font-weight: 650;
  color: #182230;
}
.kpi-sub {
  display: block;
  margin-top: 4px;
  font-size: 12px;
  color: #98a2b3;
}
.panel {
  background: #fff;
  border: 1px solid #e4e7ec;
  border-radius: 8px;
  padding: 14px 16px;
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
  height: 280px;
  width: 100%;
}
.grid-2 {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}
.rate-cell {
  display: grid;
  gap: 4px;
  font-size: 12px;
  color: #667085;
}
.band {
  font-size: 12px;
  font-weight: 550;
}
.band.ok {
  color: #067647;
}
.band.warn {
  color: #b54708;
}
.band.alert,
.band.over {
  color: #b42318;
}
.ops {
  color: #2563eb !important;
}
.pager {
  margin-top: 12px;
  display: flex;
  justify-content: flex-end;
}
@media (max-width: 1024px) {
  .kpi-row,
  .grid-2 {
    grid-template-columns: 1fr 1fr;
  }
}
@media (max-width: 768px) {
  .an-page {
    padding: 16px 12px;
  }
  .kpi-row,
  .grid-2 {
    grid-template-columns: 1fr;
  }
}
</style>
