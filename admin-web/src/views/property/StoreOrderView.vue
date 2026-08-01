<template>
  <div class="page-card">
    <div class="page-header">
      <div>
        <h2>订单管理</h2>
        <p class="sub">查看业主线上购物订单，处理履约状态；支持退款审核与直接退款</p>
      </div>
      <div class="ops">
        <el-button :loading="loading" @click="load">刷新</el-button>
        <el-button @click="exportOrders">导出</el-button>
      </div>
    </div>

    <div class="filter-bar">
      <el-select v-model="orderStatus" clearable placeholder="订单状态" style="width: 140px" @change="load">
        <el-option v-for="s in statusOpts" :key="s.v" :label="s.l" :value="s.v" />
      </el-select>
      <el-select v-model="refundOnly" clearable placeholder="退款筛选" style="width: 140px" @change="load">
        <el-option label="待审退款" value="APPLYING" />
        <el-option label="已退款" value="APPROVED" />
      </el-select>
      <el-input
        v-model="keyword"
        clearable
        placeholder="单号/姓名/手机"
        style="width: 200px"
        @keyup.enter="load"
      />
      <el-button type="primary" @click="load">查询</el-button>
    </div>

    <el-alert
      v-if="!canManage"
      type="info"
      show-icon
      :closable="false"
      style="margin-bottom: 12px"
      title="网格员可接单/配送；退款审核与直接退款仅管理员"
    />

    <el-table v-loading="loading" :data="displayOrders" stripe empty-text="暂无订单">
      <el-table-column prop="orderNo" label="订单号" width="180" show-overflow-tooltip />
      <el-table-column prop="deliveryTypeLabel" label="配送" width="90" />
      <el-table-column prop="payAmount" label="实付" width="90">
        <template #default="{ row }">¥{{ Number(row.payAmount || 0).toFixed(2) }}</template>
      </el-table-column>
      <el-table-column prop="receiverName" label="收货人" width="90" />
      <el-table-column prop="receiverPhone" label="电话" width="120" />
      <el-table-column prop="receiverAddr" label="地址" min-width="140" show-overflow-tooltip />
      <el-table-column prop="statusLabel" label="状态" width="90" />
      <el-table-column label="退款" width="90">
        <template #default="{ row }">
          <el-tag
            v-if="row.refundStatus && row.refundStatus !== 'NONE'"
            :type="refundTag(row.refundStatus)"
            size="small"
          >
            {{ refundLabel(row.refundStatus) }}
          </el-tag>
          <span v-else class="muted">无</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="280" fixed="right">
        <template #default="{ row }">
          <el-button v-if="row.status === 'PENDING_ACCEPT'" link type="primary" @click="act(row, 'accept')">
            接单
          </el-button>
          <el-button v-if="row.status === 'PREPARING'" link type="primary" @click="act(row, 'prepare')">
            备货完成
          </el-button>
          <el-button
            v-if="row.status === 'PREPARING' || row.status === 'DELIVERING'"
            link
            type="primary"
            @click="act(row, 'deliver')"
          >
            配送
          </el-button>
          <el-button v-if="row.status === 'DELIVERING'" link type="success" @click="act(row, 'complete')">
            完成
          </el-button>
          <template v-if="canManage">
            <el-button
              v-if="row.refundStatus === 'APPLYING'"
              link
              type="warning"
              @click="auditRefund(row, true)"
            >
              退款通过
            </el-button>
            <el-button
              v-if="row.refundStatus === 'APPLYING'"
              link
              type="danger"
              @click="auditRefund(row, false)"
            >
              拒绝
            </el-button>
            <el-button
              v-if="canDirectRefund(row)"
              link
              type="danger"
              @click="directRefund(row)"
            >
              直接退款
            </el-button>
          </template>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { exportExcel } from '@/utils/exportExcel'
import {
  auditStoreRefund,
  directStoreRefund,
  listStoreOrders,
  staffStoreAction,
} from '@/api/store'

const user = useUserStore()
const canManage = computed(
  () => user.role === 'property_admin' || user.role === 'platform_admin',
)
const loading = ref(false)
const orders = ref<any[]>([])
const orderStatus = ref('')
const refundOnly = ref('')
const keyword = ref('')

const statusOpts = [
  { v: 'UNPAID', l: '待支付' },
  { v: 'PENDING_ACCEPT', l: '待接单' },
  { v: 'PREPARING', l: '备货中' },
  { v: 'DELIVERING', l: '配送中' },
  { v: 'COMPLETED', l: '已完成' },
  { v: 'CANCELLED', l: '已取消' },
  { v: 'REFUNDED', l: '已退款' },
]

const displayOrders = computed(() => {
  if (!refundOnly.value) return orders.value
  return orders.value.filter(
    (o) => String(o.refundStatus || '').toUpperCase() === refundOnly.value,
  )
})

function refundLabel(status?: string) {
  const map: Record<string, string> = {
    NONE: '无',
    APPLYING: '退款中',
    APPROVED: '已退款',
    REJECTED: '已拒绝',
  }
  if (!status) return '无'
  return map[String(status).toUpperCase()] || status
}

function refundTag(status?: string) {
  const s = String(status || '').toUpperCase()
  if (s === 'APPLYING') return 'warning'
  if (s === 'APPROVED') return 'success'
  if (s === 'REJECTED') return 'danger'
  return 'info'
}

function canDirectRefund(row: any) {
  const st = row.status
  if (st === 'UNPAID' || st === 'CANCELLED' || st === 'REFUNDED') return false
  if (row.refundStatus === 'APPLYING' || row.refundStatus === 'APPROVED') return false
  return true
}

async function load() {
  loading.value = true
  try {
    orders.value =
      (await listStoreOrders({
        status: orderStatus.value || undefined,
        keyword: keyword.value || undefined,
      })) || []
  } finally {
    loading.value = false
  }
}

async function act(row: any, action: string) {
  await staffStoreAction(row.id, action)
  ElMessage.success('已更新')
  load()
}

async function auditRefund(row: any, approve: boolean) {
  await ElMessageBox.confirm(
    approve ? `确认通过订单 ${row.orderNo} 的退款？将按系统退费逻辑退回` : `拒绝订单 ${row.orderNo} 的退款申请？`,
    '退款审核',
    { type: approve ? 'warning' : 'info' },
  )
  await auditStoreRefund(row.id, approve)
  ElMessage.success(approve ? '已退款入账' : '已拒绝')
  load()
}

async function directRefund(row: any) {
  const { value } = await ElMessageBox.prompt('请填写退款原因', `直接退款 · ${row.orderNo}`, {
    inputPlaceholder: '如：缺货 / 协商退款',
    confirmButtonText: '确认退款',
  })
  await directStoreRefund(row.id, value || '物业端退款')
  ElMessage.success('已退款，已写入退费流水')
  load()
}

function exportOrders() {
  const rows = displayOrders.value.map((o) => ({
    订单号: o.orderNo,
    配送: o.deliveryTypeLabel,
    实付: o.payAmount,
    收货人: o.receiverName,
    电话: o.receiverPhone,
    地址: o.receiverAddr,
    状态: o.statusLabel,
    退款: refundLabel(o.refundStatus),
  }))
  exportExcel(rows, `超市订单_${Date.now()}`)
  ElMessage.success('已导出')
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
  margin-bottom: 12px;
  flex-wrap: wrap;
}
.muted {
  color: #94a3b8;
}
</style>
