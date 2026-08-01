<template>
  <div class="page-card">
    <div class="page-header">
      <div>
        <h2>营销资金</h2>
        <p class="sub">本店营收汇总与订单对账 · 仅本店数据（不含提现申请）</p>
      </div>
      <el-button :loading="loading" @click="load">刷新</el-button>
    </div>

    <div class="filter-bar">
      <el-date-picker
        v-model="dateRange"
        type="daterange"
        value-format="YYYY-MM-DD"
        start-placeholder="开始日期"
        end-placeholder="结束日期"
        style="width: 280px"
        @change="load"
      />
      <el-button type="primary" :loading="loading" @click="load">查询</el-button>
      <el-tag v-if="data.shopName" type="info" effect="plain">{{ data.shopName }}</el-tag>
    </div>

    <div class="stat-grid">
      <div class="stat">
        <div class="lab">有效订单</div>
        <div class="val">{{ data.orderCount || 0 }}</div>
      </div>
      <div class="stat">
        <div class="lab">销售额</div>
        <div class="val">¥{{ money(data.salesAmount) }}</div>
      </div>
      <div class="stat">
        <div class="lab">已完成金额</div>
        <div class="val">¥{{ money(data.completedAmount) }}</div>
      </div>
      <div class="stat">
        <div class="lab">退款金额</div>
        <div class="val warn">¥{{ money(data.refundAmount) }}</div>
      </div>
    </div>

    <el-table v-loading="loading" :data="data.orders || []" stripe empty-text="该时段暂无订单">
      <el-table-column prop="orderNo" label="订单号" min-width="150" show-overflow-tooltip />
      <el-table-column prop="receiverName" label="收货人" width="90" />
      <el-table-column label="实付" width="100" align="right">
        <template #default="{ row }">¥{{ money(row.payAmount) }}</template>
      </el-table-column>
      <el-table-column prop="statusLabel" label="状态" width="100" />
      <el-table-column prop="payTime" label="支付时间" width="170">
        <template #default="{ row }">{{ row.payTime || row.createTime || '-' }}</template>
      </el-table-column>
      <el-table-column prop="createTime" label="下单时间" width="170" />
    </el-table>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { getMyMallRevenue } from '@/api/mall'

const loading = ref(false)
const dateRange = ref<[string, string] | null>(null)
const data = reactive<any>({
  shopName: '',
  orderCount: 0,
  salesAmount: 0,
  completedAmount: 0,
  refundAmount: 0,
  orders: [],
})

function money(v: unknown) {
  return Number(v || 0).toFixed(2)
}

async function load() {
  loading.value = true
  try {
    const res =
      (await getMyMallRevenue({
        startDate: dateRange.value?.[0],
        endDate: dateRange.value?.[1],
      })) || {}
    Object.assign(data, {
      shopName: res.shopName || '',
      orderCount: res.orderCount || 0,
      salesAmount: res.salesAmount || 0,
      completedAmount: res.completedAmount || 0,
      refundAmount: res.refundAmount || 0,
      orders: res.orders || [],
    })
  } catch {
    Object.assign(data, {
      orderCount: 0,
      salesAmount: 0,
      completedAmount: 0,
      refundAmount: 0,
      orders: [],
    })
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.sub { margin: 4px 0 0; color: #64748b; font-size: 13px; }
.filter-bar { display: flex; align-items: center; gap: 10px; margin-bottom: 16px; flex-wrap: wrap; }
.stat-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(140px, 1fr));
  gap: 12px;
  margin-bottom: 16px;
}
.stat {
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  padding: 14px 16px;
}
.stat .lab { font-size: 13px; color: #64748b; }
.stat .val { margin-top: 6px; font-size: 22px; font-weight: 700; color: #0f172a; }
.stat .val.warn { color: #b45309; }
@media (max-width: 900px) {
  .stat-grid { grid-template-columns: repeat(2, 1fr); }
}
</style>
