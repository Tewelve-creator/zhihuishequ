<template>
  <div class="page-card">
    <div class="page-header">
      <div>
        <h2>退款及对账</h2>
        <p class="sub">已付订单退款与退款记录</p>
      </div>
      <el-button :loading="loading" @click="loadAll">刷新</el-button>
    </div>

    <el-alert v-if="finance" type="info" :closable="false" show-icon style="margin-bottom: 12px">
      <template #title>
        对账：已付 {{ fmtMoney(finance.paidAmount) }} − 退款 {{ fmtMoney(finance.refundAmount) }} =
        净营收 {{ fmtMoney(netRevenue) }}
      </template>
    </el-alert>

    <el-tabs v-model="tab">
      <el-tab-pane label="可退款（已支付）" name="refundable">
        <el-table v-loading="loadingPaid" :data="paidList" stripe empty-text="暂无可退款订单">
          <el-table-column prop="orderNo" label="订单号" width="160" />
          <el-table-column prop="tenantId" label="租户ID" width="90" />
          <el-table-column prop="packageName" label="套餐" min-width="120" />
          <el-table-column prop="amount" label="金额" width="100" />
          <el-table-column prop="payTime" label="支付时间" width="170" />
          <el-table-column label="操作" width="100" fixed="right">
            <template #default="{ row }">
              <el-button link type="danger" @click="openRefund(row)">退款</el-button>
            </template>
          </el-table-column>
        </el-table>
        <div class="pager">
          <el-pagination
            v-model:current-page="paidPage"
            background
            layout="total, prev, pager, next"
            :total="paidTotal"
            @current-change="loadPaid"
          />
        </div>
      </el-tab-pane>

      <el-tab-pane label="退款记录" name="refunded">
        <el-table v-loading="loadingRefunded" :data="refundedList" stripe empty-text="暂无退款记录">
          <el-table-column prop="orderNo" label="订单号" width="160" />
          <el-table-column prop="tenantId" label="租户ID" width="90" />
          <el-table-column prop="packageName" label="套餐" min-width="120" />
          <el-table-column prop="amount" label="金额" width="100" />
          <el-table-column prop="refundReason" label="退款原因" min-width="140" show-overflow-tooltip />
          <el-table-column prop="refundTime" label="退款时间" width="170" />
        </el-table>
        <div class="pager">
          <el-pagination
            v-model:current-page="refundedPage"
            background
            layout="total, prev, pager, next"
            :total="refundedTotal"
            @current-change="loadRefunded"
          />
        </div>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="visible" title="订单退款" width="440px" destroy-on-close>
      <el-form label-width="88px">
        <el-form-item label="订单号">{{ refundRow?.orderNo }}</el-form-item>
        <el-form-item label="金额">{{ refundRow?.amount }}</el-form-item>
        <el-form-item label="退款原因">
          <el-input v-model="reason" type="textarea" :rows="3" placeholder="选填" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="danger" :loading="refunding" @click="confirmRefund">确认退款</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { pageOrders, listRefundOrders, refundOrder, financeSummary } from '@/api/saas'

const tab = ref('refundable')
const loadingPaid = ref(false)
const loadingRefunded = ref(false)
const loading = ref(false)
const paidPage = ref(1)
const refundedPage = ref(1)
const paidTotal = ref(0)
const refundedTotal = ref(0)
const paidList = ref<any[]>([])
const refundedList = ref<any[]>([])
const finance = ref<Record<string, any> | null>(null)
const visible = ref(false)
const refundRow = ref<any>(null)
const reason = ref('')
const refunding = ref(false)

const netRevenue = computed(() => {
  if (!finance.value) return 0
  return (Number(finance.value.paidAmount) || 0) - (Number(finance.value.refundAmount) || 0)
})

function fmtMoney(v: unknown) {
  const n = Number(v)
  if (Number.isNaN(n)) return '0'
  return n.toLocaleString('zh-CN', { minimumFractionDigits: 0, maximumFractionDigits: 2 })
}

async function loadFinance() {
  try {
    finance.value = (await financeSummary()) || {}
  } catch {
    finance.value = {}
  }
}

async function loadPaid() {
  loadingPaid.value = true
  try {
    const data = await pageOrders({ page: paidPage.value, size: 20, status: 'paid' })
    paidList.value = data?.records || []
    paidTotal.value = Number(data?.total || 0)
  } catch {
    paidList.value = []
    paidTotal.value = 0
  } finally {
    loadingPaid.value = false
  }
}

async function loadRefunded() {
  loadingRefunded.value = true
  try {
    const data = await listRefundOrders({ page: refundedPage.value, size: 20 })
    refundedList.value = data?.records || []
    refundedTotal.value = Number(data?.total || 0)
  } catch {
    refundedList.value = []
    refundedTotal.value = 0
  } finally {
    loadingRefunded.value = false
  }
}

async function loadAll() {
  loading.value = true
  try {
    await Promise.all([loadFinance(), loadPaid(), loadRefunded()])
  } finally {
    loading.value = false
  }
}

function openRefund(row: any) {
  refundRow.value = row
  reason.value = ''
  visible.value = true
}

async function confirmRefund() {
  if (!refundRow.value) return
  try {
    await ElMessageBox.confirm(`确认退款订单 ${refundRow.value.orderNo}？`, '退款', { type: 'warning' })
  } catch {
    return
  }
  refunding.value = true
  try {
    await refundOrder(refundRow.value.id, reason.value.trim() || undefined)
    ElMessage.success('退款成功')
    visible.value = false
    await loadAll()
  } finally {
    refunding.value = false
  }
}

onMounted(loadAll)
</script>

<style scoped>
.sub { margin: 4px 0 0; color: #64748b; font-size: 13px; }
.pager { margin-top: 16px; display: flex; justify-content: flex-end; }
</style>
