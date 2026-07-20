<template>
  <div class="page-card">
    <div class="page-header">
      <h2>订单财务管理</h2>
      <el-button @click="onExport">导出 Excel</el-button>
    </div>
    <div class="filter-bar">
      <el-select v-model="payStatus" placeholder="支付状态" clearable style="width: 140px">
        <el-option label="已支付" value="已支付" />
        <el-option label="待支付" value="待支付" />
        <el-option label="已退款" value="已退款" />
      </el-select>
      <el-date-picker
        v-model="range"
        type="daterange"
        value-format="YYYY-MM-DD"
        start-placeholder="开始"
        end-placeholder="结束"
      />
      <el-button type="primary">查询</el-button>
    </div>
    <el-row :gutter="16" style="margin-bottom: 16px">
      <el-col :span="8"><el-statistic title="订单总额(元)" :value="summary.total" :precision="2" /></el-col>
      <el-col :span="8"><el-statistic title="已收款(元)" :value="summary.paid" :precision="2" /></el-col>
      <el-col :span="8"><el-statistic title="退款(元)" :value="summary.refund" :precision="2" /></el-col>
    </el-row>
    <el-table :data="filtered" stripe>
      <el-table-column prop="orderNo" label="订单号" width="160" />
      <el-table-column prop="tenantName" label="租户" />
      <el-table-column prop="packageName" label="套餐" width="120" />
      <el-table-column prop="amount" label="金额" width="100" />
      <el-table-column prop="createdAt" label="下单时间" width="120" />
      <el-table-column prop="status" label="状态" width="100" />
    </el-table>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { exportExcel } from '@/utils/exportExcel'

interface OrderRow {
  orderNo: string
  tenantName: string
  packageName: string
  amount: number
  createdAt: string
  status: string
}

const payStatus = ref('')
const range = ref<[string, string] | null>(null)
const list = ref<OrderRow[]>([
  { orderNo: 'ORD20260715001', tenantName: '阳光花园物业', packageName: '专业版', amount: 5999, createdAt: '2026-07-15', status: '已支付' },
  { orderNo: 'ORD20260716002', tenantName: '湖畔名邸', packageName: '基础版', amount: 2999, createdAt: '2026-07-16', status: '待支付' },
  { orderNo: 'ORD20260710003', tenantName: '星河湾社区', packageName: '旗舰版', amount: 9999, createdAt: '2026-07-10', status: '已退款' },
])

const filtered = computed(() =>
  list.value.filter((o) => {
    const hitStatus = !payStatus.value || o.status === payStatus.value
    const hitRange = !range.value || (o.createdAt >= range.value[0] && o.createdAt <= range.value[1])
    return hitStatus && hitRange
  }),
)

const summary = computed(() => {
  const rows = filtered.value
  return {
    total: rows.reduce((s, r) => s + r.amount, 0),
    paid: rows.filter((r) => r.status === '已支付').reduce((s, r) => s + r.amount, 0),
    refund: rows.filter((r) => r.status === '已退款').reduce((s, r) => s + r.amount, 0),
  }
})

function onExport() {
  exportExcel(filtered.value, `订单财务_${Date.now()}`)
}
</script>
