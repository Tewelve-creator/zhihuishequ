<template>
  <div class="mh" v-loading="loading">
    <div class="kpi-row">
      <div class="kpi">
        <div class="kpi-lab">今日订单</div>
        <div class="kpi-val">{{ stats.todayOrders }}</div>
      </div>
      <div class="kpi">
        <div class="kpi-lab">本月销售额</div>
        <div class="kpi-val">{{ money(stats.monthSales) }}<span class="unit">元</span></div>
      </div>
      <div class="kpi">
        <div class="kpi-lab">平台佣金（估）</div>
        <div class="kpi-val">{{ money(stats.commission) }}<span class="unit">元</span></div>
      </div>
      <div class="kpi">
        <div class="kpi-lab">有效销售额</div>
        <div class="kpi-val">{{ money(stats.salesAmount) }}<span class="unit">元</span></div>
      </div>
    </div>

    <div class="mid-row">
      <div class="panel chart-panel">
        <div class="panel-hd">
          <h3>销售趋势数据概览</h3>
          <span class="muted">近 7 日</span>
        </div>
        <v-chart class="chart" :option="trendOption" autoresize />
      </div>
      <div class="panel chart-panel">
        <div class="panel-hd">
          <h3>订单状态分布</h3>
        </div>
        <v-chart class="chart" :option="pieOption" autoresize />
        <div class="pie-legend">
          <span v-for="it in statusPie" :key="it.name">{{ it.name }} {{ it.value }}</span>
        </div>
      </div>
    </div>

    <div class="bot-row">
      <div class="panel">
        <div class="panel-hd">
          <h3>最新订单列表</h3>
          <el-button link type="primary" @click="$router.push('/merchant/orders')">全部</el-button>
        </div>
        <el-table :data="latestOrders" size="small" stripe empty-text="暂无订单" max-height="280">
          <el-table-column prop="orderNo" label="订单号" min-width="120" show-overflow-tooltip />
          <el-table-column prop="receiverName" label="收货人" width="80" />
          <el-table-column label="金额" width="90" align="right">
            <template #default="{ row }">¥{{ money(row.payAmount) }}</template>
          </el-table-column>
          <el-table-column prop="statusLabel" label="状态" width="90" />
          <el-table-column prop="createTime" label="时间" width="150" />
          <el-table-column label="操作" width="70" fixed="right">
            <template #default>
              <el-button link type="primary" @click="$router.push('/merchant/orders')">查看</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
      <div class="panel">
        <div class="panel-hd"><h3>店铺运营提醒</h3></div>
        <ul class="tips">
          <li v-for="(t, i) in tips" :key="i">{{ t }}</li>
          <li v-if="!tips.length" class="empty">暂无提醒，店铺运行正常</li>
        </ul>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import dayjs from 'dayjs'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { LineChart, PieChart } from 'echarts/charts'
import { GridComponent, TooltipComponent, LegendComponent } from 'echarts/components'
import VChart from 'vue-echarts'
import { getMyMallRevenue, getMyMallShop, listMallOrders, listMallProducts } from '@/api/mall'

use([CanvasRenderer, LineChart, PieChart, GridComponent, TooltipComponent, LegendComponent])

const TEAL = '#0d9488'
const loading = ref(false)
const shopName = ref('')
const orders = ref<any[]>([])
const products = ref<any[]>([])
const revenue = reactive<any>({ salesAmount: 0, completedAmount: 0, orderCount: 0 })

const stats = computed(() => {
  const today = dayjs().format('YYYY-MM-DD')
  const monthStart = dayjs().startOf('month').format('YYYY-MM-DD')
  const todayOrders = orders.value.filter((o) => String(o.createTime || '').startsWith(today)).length
  const monthSales = orders.value
    .filter((o) => {
      const t = String(o.createTime || '')
      return t >= monthStart && o.status !== 'CANCELLED'
    })
    .reduce((s, o) => s + Number(o.payAmount || 0), 0)
  const salesAmount = Number(revenue.salesAmount || 0)
  const commission = Math.round(monthSales * 0.08 * 100) / 100
  return { todayOrders, monthSales, commission, salesAmount }
})

const latestOrders = computed(() => orders.value.slice(0, 8))

const statusPie = computed(() => {
  const map: Record<string, number> = {
    待接单: 0,
    配送中: 0,
    已完成: 0,
    已取消: 0,
    备货中: 0,
  }
  for (const o of orders.value) {
    const st = o.status
    if (st === 'PENDING_ACCEPT') map['待接单']++
    else if (st === 'DELIVERING') map['配送中']++
    else if (st === 'COMPLETED') map['已完成']++
    else if (st === 'CANCELLED') map['已取消']++
    else if (st === 'PREPARING') map['备货中']++
  }
  return Object.entries(map)
    .filter(([, v]) => v > 0)
    .map(([name, value]) => ({ name, value }))
})

const trendOption = computed(() => {
  const days: string[] = []
  const sales: number[] = []
  const cnt: number[] = []
  for (let i = 6; i >= 0; i--) {
    const d = dayjs().subtract(i, 'day')
    const key = d.format('YYYY-MM-DD')
    days.push(d.format('MM-DD'))
    const dayOrders = orders.value.filter(
      (o) => String(o.createTime || '').startsWith(key) && o.status !== 'CANCELLED',
    )
    cnt.push(dayOrders.length)
    sales.push(Math.round(dayOrders.reduce((s, o) => s + Number(o.payAmount || 0), 0) * 100) / 100)
  }
  return {
    color: [TEAL, '#14b8a6', '#5eead4'],
    tooltip: { trigger: 'axis' },
    legend: { data: ['销售额', '订单量'], bottom: 0, textStyle: { fontSize: 11 } },
    grid: { left: 48, right: 24, top: 24, bottom: 36 },
    xAxis: { type: 'category', data: days, boundaryGap: false },
    yAxis: { type: 'value', splitLine: { lineStyle: { type: 'dashed', color: '#e2e8f0' } } },
    series: [
      { name: '销售额', type: 'line', smooth: true, data: sales, areaStyle: { opacity: 0.12 } },
      { name: '订单量', type: 'line', smooth: true, data: cnt },
    ],
  }
})

const pieOption = computed(() => ({
  color: [TEAL, '#14b8a6', '#99f6e4', '#f59e0b', '#94a3b8'],
  tooltip: { trigger: 'item', formatter: '{b}：{c}（{d}%）' },
  series: [
    {
      type: 'pie',
      radius: ['42%', '68%'],
      center: ['50%', '48%'],
      itemStyle: { borderRadius: 4, borderColor: '#fff', borderWidth: 2 },
      label: { formatter: '{b}\n{c}', fontSize: 11 },
      data: statusPie.value.length
        ? statusPie.value
        : [{ name: '暂无数据', value: 1, itemStyle: { color: '#d9d9d9' } }],
    },
  ],
}))

const tips = computed(() => {
  const list: string[] = []
  const pending = orders.value.filter((o) => o.status === 'PENDING_ACCEPT').length
  if (pending) list.push(`${pending} 笔待接单，请及时处理`)
  const lowStock = products.value.filter((p) => Number(p.stock || 0) <= 5 && p.status === 1)
  if (lowStock.length) {
    list.push(`库存预警：${lowStock.slice(0, 2).map((p) => p.name).join('、')}${lowStock.length > 2 ? ' 等' : ''}`)
  }
  list.push(shopName.value ? `当前店铺：${shopName.value}` : '请完善店铺信息')
  list.push('营销资金可在「营销资金」查看本店营收对账')
  return list
})

function money(v: unknown) {
  return Number(v || 0).toFixed(v && Number(v) >= 100 ? 0 : 2)
}

async function load() {
  loading.value = true
  try {
    const [shop, ord, prod, rev] = await Promise.all([
      getMyMallShop().catch(() => ({})),
      listMallOrders().catch(() => []),
      listMallProducts().catch(() => []),
      getMyMallRevenue().catch(() => ({})),
    ])
    shopName.value = (shop as any)?.name || ''
    orders.value = (ord as any[]) || []
    products.value = (prod as any[]) || []
    Object.assign(revenue, rev || {})
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.mh { display: flex; flex-direction: column; gap: 14px; }
.kpi-row {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
}
.kpi {
  background: linear-gradient(145deg, #0f766e, #14b8a6);
  color: #fff;
  border-radius: 14px;
  padding: 18px 20px;
  box-shadow: 0 8px 20px rgba(13, 148, 136, 0.22);
}
.kpi-lab { font-size: 13px; opacity: 0.92; }
.kpi-val { margin-top: 8px; font-size: 28px; font-weight: 700; letter-spacing: 0.02em; }
.unit { font-size: 14px; font-weight: 500; margin-left: 4px; opacity: 0.9; }
.mid-row, .bot-row {
  display: grid;
  grid-template-columns: 1.4fr 1fr;
  gap: 12px;
}
.panel {
  background: #fff;
  border-radius: 14px;
  padding: 14px 16px;
  border: 1px solid #e7efed;
  box-shadow: 0 4px 16px rgba(15, 23, 42, 0.04);
}
.panel-hd {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}
.panel-hd h3 { margin: 0; font-size: 15px; color: #0f172a; }
.muted { color: #94a3b8; font-size: 12px; }
.chart { height: 260px; width: 100%; }
.pie-legend {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 14px;
  font-size: 12px;
  color: #64748b;
  justify-content: center;
  margin-top: 4px;
}
.tips { list-style: none; margin: 0; padding: 4px 0 0; }
.tips li {
  padding: 12px 14px;
  margin-bottom: 8px;
  background: #f0fdfa;
  border-radius: 10px;
  color: #0f766e;
  font-size: 13px;
  line-height: 1.5;
}
.tips .empty { background: #f8fafc; color: #94a3b8; }
@media (max-width: 1100px) {
  .kpi-row { grid-template-columns: repeat(2, 1fr); }
  .mid-row, .bot-row { grid-template-columns: 1fr; }
}
</style>
