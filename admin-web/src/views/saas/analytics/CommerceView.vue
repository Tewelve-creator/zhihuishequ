<template>
  <div class="an-page">
    <div class="an-shell">
      <header class="page-hd">
        <div>
          <h1 class="page-title">商业运营</h1>
          <p class="page-desc">
            社区超市成交与社区提成 · 平台不抽成 · 近 {{ filter.days }} 天 · 已完成订单口径
          </p>
        </div>
      </header>

      <AnalyticsFilterBar
        v-model="filter"
        :loading="loading"
        :can-export="!!rows.length"
        :exporting="exporting"
        @query="load"
        @reset="load"
        @export="onExport"
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
            <h2>社区销售排行</h2>
            <span class="meta">成交金额 · 元</span>
          </div>
          <v-chart v-if="rankOpt" class="chart" :option="rankOpt" autoresize />
          <el-empty v-else description="暂无销售数据" :image-size="64" />
        </section>
        <section class="panel">
          <div class="panel-hd">
            <h2>明细表</h2>
            <span class="meta">按社区汇总</span>
          </div>
          <el-table :data="rows" height="280" empty-text="暂无数据">
            <el-table-column prop="tenantName" label="社区" min-width="140" />
            <el-table-column label="订单数" width="100" align="right">
              <template #default="{ row }">{{ formatNum(row.orderCount) }}</template>
            </el-table-column>
            <el-table-column label="成交金额" width="130" align="right">
              <template #default="{ row }">{{ formatMoney(row.gmv) }}</template>
            </el-table-column>
          </el-table>
        </section>
      </div>

      <el-alert
        class="mt"
        type="info"
        :closable="false"
        show-icon
        title="商品分类占比、支付方式、退款原因、商户筛选等需更细粒度接口，当前仅支持成交额 / 订单 / 佣金与社区排行。"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { BarChart } from 'echarts/charts'
import { GridComponent, TooltipComponent } from 'echarts/components'
import VChart from 'vue-echarts'
import AnalyticsFilterBar, { type AnalyticsFilter } from '@/components/analytics/AnalyticsFilterBar.vue'
import { ANALYTICS_COLORS, formatMoney, formatNum } from '@/config/analytics'
import { getStoreReport } from '@/api/platformHub'
import { exportExcel } from '@/utils/exportExcel'

use([CanvasRenderer, BarChart, GridComponent, TooltipComponent])

const loading = ref(false)
const exporting = ref(false)
const filter = reactive<AnalyticsFilter>({ days: 30 })
const data = ref<any>({})

const rows = computed(() => {
  let list = data.value.byTenant || []
  if (filter.tenantId) {
    list = list.filter((r: any) => Number(r.tenantId) === Number(filter.tenantId))
  }
  return list
})

const aov = computed(() => {
  const gmv = Number(data.value.gmv) || 0
  const cnt = Number(data.value.orderCount) || 0
  if (!cnt) return 0
  return gmv / cnt
})

const kpis = computed(() => [
  { label: '成交金额', value: formatMoney(data.value.gmv), sub: '已完成订单 pay_amount' },
  { label: '订单数', value: formatNum(data.value.orderCount), sub: 'COMPLETED' },
  { label: '客单价', value: formatMoney(aov.value), sub: '成交金额 / 订单数' },
  { label: '社区提成', value: formatMoney(data.value.commissionAmount), sub: '近窗提成账单 · 平台抽成¥0' },
  { label: '待结算提成', value: formatMoney(data.value.pendingCommission), sub: 'settle_status=PENDING' },
])

const rankOpt = computed(() => {
  const list = rows.value.slice(0, 12)
  if (!list.length) return null
  return {
    tooltip: {
      trigger: 'axis',
      valueFormatter: (v: number) => formatMoney(v),
    },
    grid: { left: 56, right: 16, top: 16, bottom: 48 },
    xAxis: {
      type: 'category',
      data: list.map((r: any) => r.tenantName),
      axisLabel: { rotate: 28, fontSize: 11, color: '#667085' },
    },
    yAxis: { type: 'value', name: '元' },
    series: [
      {
        type: 'bar',
        data: list.map((r: any) => Number(r.gmv) || 0),
        barMaxWidth: 28,
        itemStyle: { color: ANALYTICS_COLORS.primary, borderRadius: [4, 4, 0, 0] },
      },
    ],
  }
})

async function load() {
  loading.value = true
  try {
    data.value = (await getStoreReport(filter.days)) || {}
  } catch {
    data.value = {}
  } finally {
    loading.value = false
  }
}

function onExport() {
  if (!rows.value.length) {
    ElMessage.warning('暂无数据')
    return
  }
  exporting.value = true
  try {
    exportExcel(
      rows.value.map((r: any) => ({
        社区: r.tenantName,
        订单数: r.orderCount,
        成交金额: r.gmv,
      })),
      '商业运营',
      '商业',
    )
  } finally {
    exporting.value = false
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
.grid-2 {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
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
  height: 260px;
  width: 100%;
}
.mt {
  margin-top: 12px;
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
