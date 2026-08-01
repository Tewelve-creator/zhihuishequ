<template>
  <div class="page-card">
    <div class="page-header">
      <div>
        <h2>商圈佣金应收</h2>
        <p class="sub">商圈交易订单平台服务费/佣金台账（≠ SaaS 租户套餐订购账单）· 可按日期筛选导出对账</p>
      </div>
      <div class="ops">
        <el-button :loading="loading" @click="load">刷新</el-button>
        <el-button @click="exportLedger">导出台账</el-button>
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
      <el-button type="primary" :loading="loading" @click="load">查询</el-button>
    </div>

    <div class="stat-grid">
      <div class="stat">
        <div class="lab">有效订单</div>
        <div class="val">{{ data.orderCount || 0 }}</div>
      </div>
      <div class="stat">
        <div class="lab">营业额(GMV)</div>
        <div class="val">¥{{ num(data.gmv) }}</div>
      </div>
      <div class="stat">
        <div class="lab">平台佣金({{ data.commissionRate || 0 }}%)</div>
        <div class="val warn">¥{{ num(data.commissionAmount) }}</div>
      </div>
      <div class="stat">
        <div class="lab">退款金额</div>
        <div class="val danger">¥{{ num(data.refundAmount) }}</div>
      </div>
      <div class="stat primary">
        <div class="lab">应结金额</div>
        <div class="val">¥{{ num(data.settleAmount) }}</div>
      </div>
    </div>

    <h3 class="section-title">结算台账 · {{ data.storeName || '社区商户' }}</h3>
    <el-table v-loading="loading" :data="data.ledger || []" stripe empty-text="暂无结算明细" max-height="420">
      <el-table-column prop="orderNo" label="订单号" min-width="170" show-overflow-tooltip />
      <el-table-column prop="receiverName" label="客户" width="90" />
      <el-table-column prop="statusLabel" label="状态" width="90" />
      <el-table-column prop="payAmount" label="实付" width="100">
        <template #default="{ row }">¥{{ num(row.payAmount) }}</template>
      </el-table-column>
      <el-table-column prop="commission" label="佣金" width="90">
        <template #default="{ row }">¥{{ num(row.commission) }}</template>
      </el-table-column>
      <el-table-column prop="settleAmount" label="可结" width="100">
        <template #default="{ row }">¥{{ num(row.settleAmount) }}</template>
      </el-table-column>
      <el-table-column prop="payTime" label="支付时间" width="170">
        <template #default="{ row }">{{ fmt(row.payTime) }}</template>
      </el-table-column>
    </el-table>

    <h3 class="section-title" style="margin-top: 24px">退款流水</h3>
    <el-table v-loading="refundLoading" :data="refunds" stripe empty-text="暂无退款记录" max-height="320">
      <el-table-column prop="orderNo" label="订单号" min-width="160" show-overflow-tooltip />
      <el-table-column prop="refundNo" label="退款单号" min-width="150" show-overflow-tooltip />
      <el-table-column prop="amount" label="金额" width="100">
        <template #default="{ row }">¥{{ num(row.amount) }}</template>
      </el-table-column>
      <el-table-column prop="reason" label="原因" min-width="140" show-overflow-tooltip />
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }">
          <el-tag size="small" :type="row.status === 'APPROVED' ? 'success' : row.status === 'APPLYING' ? 'warning' : 'info'">
            {{ refundStatus(row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="auditTime" label="审核时间" width="170">
        <template #default="{ row }">{{ fmt(row.auditTime || row.createTime) }}</template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { exportExcel } from '@/utils/exportExcel'
import { getStoreSettlement, listStoreRefunds } from '@/api/store'

const loading = ref(false)
const refundLoading = ref(false)
const dateRange = ref<[string, string] | null>(null)
const refunds = ref<any[]>([])
const data = reactive<any>({
  storeName: '',
  commissionRate: 5,
  orderCount: 0,
  gmv: 0,
  commissionAmount: 0,
  refundCount: 0,
  refundAmount: 0,
  settleAmount: 0,
  ledger: [],
})

function num(v: any) {
  return Number(v || 0).toFixed(2)
}

function fmt(v?: string) {
  if (!v) return '—'
  return String(v).replace('T', ' ').substring(0, 19)
}

function refundStatus(s?: string) {
  if (s === 'APPROVED') return '已退款'
  if (s === 'APPLYING') return '审核中'
  if (s === 'REJECTED') return '已拒绝'
  return s || '—'
}

async function load() {
  loading.value = true
  try {
    const res = await getStoreSettlement({
      startDate: dateRange.value?.[0],
      endDate: dateRange.value?.[1],
    })
    Object.assign(data, res || {})
  } catch {
    Object.assign(data, { ledger: [], orderCount: 0, gmv: 0 })
  } finally {
    loading.value = false
  }
  loadRefunds()
}

async function loadRefunds() {
  refundLoading.value = true
  try {
    refunds.value = (await listStoreRefunds()) || []
  } catch {
    refunds.value = []
  } finally {
    refundLoading.value = false
  }
}

function exportLedger() {
  const rows = (data.ledger || []).map((r: any) => ({
    订单号: r.orderNo,
    客户: r.receiverName,
    状态: r.statusLabel,
    实付: r.payAmount,
    佣金: r.commission,
    可结: r.settleAmount,
    支付时间: fmt(r.payTime),
  }))
  exportExcel(rows, `商户结算台账_${Date.now()}`)
  ElMessage.success(`已导出 ${rows.length} 条`)
}

onMounted(load)
</script>

<style scoped>
.page-header {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}
.page-header h2 {
  margin: 0 0 4px;
}
.sub {
  margin: 0;
  color: #64748b;
  font-size: 13px;
}
.ops {
  display: flex;
  gap: 8px;
}
.filter-bar {
  display: flex;
  gap: 8px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}
.stat-grid {
  display: grid;
  grid-template-columns: repeat(5, minmax(120px, 1fr));
  gap: 12px;
  margin-bottom: 20px;
}
.stat {
  background: #f8fafc;
  border-radius: 12px;
  padding: 14px 16px;
  border: 1px solid #e2e8f0;
}
.stat.primary {
  background: linear-gradient(135deg, #ecfdf5, #f0fdf4);
  border-color: #a7f3d0;
}
.lab {
  font-size: 12px;
  color: #64748b;
}
.val {
  margin-top: 6px;
  font-size: 20px;
  font-weight: 700;
  color: #0f172a;
}
.val.warn {
  color: #d97706;
}
.val.danger {
  color: #e11d48;
}
.section-title {
  margin: 0 0 12px;
  font-size: 15px;
  font-weight: 600;
}
@media (max-width: 1100px) {
  .stat-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>
