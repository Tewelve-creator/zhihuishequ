<template>
  <div class="page-card">
    <div class="page-header">
      <div>
        <h2>缴费流水</h2>
        <p class="sub">展示全部实收缴费记录，支持时间段筛选与导出对账</p>
      </div>
      <div class="actions">
        <el-button :loading="loading" @click="load">刷新</el-button>
        <el-button type="primary" @click="onExport">导出对账Excel</el-button>
      </div>
    </div>

    <div class="filter-bar">
      <el-date-picker
        v-model="dateRange"
        type="daterange"
        value-format="YYYY-MM-DD"
        start-placeholder="开始日期"
        end-placeholder="结束日期"
        style="width: 260px"
        @change="load"
      />
      <el-select v-model="payChannel" clearable placeholder="支付渠道" style="width: 130px" @change="load">
        <el-option label="微信支付" value="WECHAT" />
        <el-option label="支付宝" value="ALIPAY" />
      </el-select>
      <el-input
        v-model="keyword"
        clearable
        placeholder="订单号/房屋/缴费人/手机"
        style="width: 220px"
        @keyup.enter="load"
      />
      <el-button type="primary" :loading="loading" @click="load">查询</el-button>
    </div>

    <div class="summary">
      <span>实收笔数 <b>{{ list.length }}</b></span>
      <span>实收合计 <b>¥{{ totalAmount.toFixed(2) }}</b></span>
    </div>

    <el-table v-loading="loading" :data="list" stripe empty-text="暂无实收流水">
      <el-table-column prop="orderNo" label="订单号" min-width="170" show-overflow-tooltip />
      <el-table-column prop="room" label="房屋" min-width="180" show-overflow-tooltip>
        <template #default="{ row }">{{ row.room || row.houseLabel || '—' }}</template>
      </el-table-column>
      <el-table-column prop="payerName" label="缴费人" width="100">
        <template #default="{ row }">{{ row.payerName || '—' }}</template>
      </el-table-column>
      <el-table-column prop="payerPhone" label="手机号" width="120">
        <template #default="{ row }">{{ row.payerPhone || '—' }}</template>
      </el-table-column>
      <el-table-column prop="amount" label="实收金额" width="110">
        <template #default="{ row }">¥{{ Number(row.amount || 0).toFixed(2) }}</template>
      </el-table-column>
      <el-table-column prop="payChannelLabel" label="支付渠道" width="100" />
      <el-table-column prop="payTradeNo" label="支付流水号" min-width="150" show-overflow-tooltip>
        <template #default="{ row }">{{ row.payTradeNo || '—' }}</template>
      </el-table-column>
      <el-table-column prop="paidAt" label="缴费时间" width="170">
        <template #default="{ row }">{{ formatTime(row.paidAt) || '—' }}</template>
      </el-table-column>
      <el-table-column prop="statusLabel" label="状态" width="90">
        <template #default="{ row }">
          <el-tag type="success" size="small">{{ row.statusLabel || '已支付' }}</el-tag>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { exportExcel } from '@/utils/exportExcel'
import { listFeePayments, type FeePayment } from '@/api/fee'

const loading = ref(false)
const list = ref<FeePayment[]>([])
const dateRange = ref<[string, string] | null>(null)
const keyword = ref('')
const payChannel = ref('')

const totalAmount = computed(() =>
  list.value.reduce((s, r) => s + Number(r.amount || 0), 0),
)

function formatTime(v?: string) {
  if (!v) return ''
  return String(v).replace('T', ' ').substring(0, 19)
}

async function load() {
  loading.value = true
  try {
    list.value = await listFeePayments({
      startDate: dateRange.value?.[0],
      endDate: dateRange.value?.[1],
      keyword: keyword.value || undefined,
      payChannel: payChannel.value || undefined,
    })
  } catch {
    list.value = []
  } finally {
    loading.value = false
  }
}

function onExport() {
  const rows = list.value.map((r) => ({
    订单号: r.orderNo,
    房屋: r.room || r.houseLabel,
    缴费人: r.payerName,
    手机号: r.payerPhone,
    实收金额: r.amount,
    支付渠道: r.payChannelLabel || r.payChannel,
    支付流水号: r.payTradeNo,
    缴费时间: formatTime(r.paidAt),
    状态: r.statusLabel || '已支付',
  }))
  exportExcel(rows, `缴费流水对账_${Date.now()}`)
  ElMessage.success(`已导出 ${rows.length} 笔实收记录`)
}

onMounted(load)
</script>

<style scoped>
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 16px;
  gap: 12px;
}
.page-header h2 {
  margin: 0 0 4px;
  font-size: 20px;
}
.sub {
  margin: 0;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}
.actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.filter-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 12px;
}
.summary {
  display: flex;
  gap: 24px;
  margin-bottom: 12px;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}
.summary b {
  color: var(--el-color-primary);
  font-size: 15px;
  margin-left: 4px;
}
</style>
