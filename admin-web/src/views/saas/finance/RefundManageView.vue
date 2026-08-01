<template>
  <div class="an-page">
    <div class="an-shell">
      <header class="page-hd">
        <div>
          <h1 class="page-title">退款管理</h1>
          <p class="page-desc">SaaS 订单退款 · 状态沿用 pending / paid / refunded / cancelled</p>
        </div>
        <el-button :loading="loading" @click="loadAll">刷新</el-button>
      </header>

      <div class="kpi-row">
        <div class="kpi">
          <span class="kpi-label">已付可退</span>
          <span class="kpi-value">{{ formatNum(paidTotal) }}</span>
        </div>
        <div class="kpi">
          <span class="kpi-label">退款笔数</span>
          <span class="kpi-value">{{ formatNum(refundedTotal) }}</span>
        </div>
        <div class="kpi">
          <span class="kpi-label">退款金额</span>
          <span class="kpi-value">{{ formatMoney(finance?.refundAmount) }}</span>
        </div>
      </div>

      <el-tabs v-model="tab" class="tabs">
        <el-tab-pane label="可退款订单" name="refundable">
          <div class="panel">
            <el-table v-loading="loadingPaid" :data="paidList" empty-text="暂无可退款订单">
              <el-table-column prop="orderNo" label="原订单号" min-width="150" />
              <el-table-column label="租户" min-width="120">
                <template #default="{ row }">{{ tenantName(row.tenantId) }}</template>
              </el-table-column>
              <el-table-column prop="packageName" label="套餐" min-width="110" />
              <el-table-column label="金额" width="120" align="right">
                <template #default="{ row }">{{ formatMoney(row.amount) }}</template>
              </el-table-column>
              <el-table-column label="支付时间" width="170">
                <template #default="{ row }">{{ formatTime(row.payTime) }}</template>
              </el-table-column>
              <el-table-column label="状态" width="90">
                <template #default>
                  <span class="status paid">已支付</span>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="100" fixed="right">
                <template #default="{ row }">
                  <el-button link class="danger-link" @click="openRefund(row)">退款</el-button>
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
          </div>
        </el-tab-pane>

        <el-tab-pane label="退款记录" name="refunded">
          <div class="panel">
            <el-table v-loading="loadingRefunded" :data="refundedList" empty-text="暂无退款记录">
              <el-table-column prop="orderNo" label="退款单号/订单号" min-width="150" />
              <el-table-column label="租户" min-width="120">
                <template #default="{ row }">{{ tenantName(row.tenantId) }}</template>
              </el-table-column>
              <el-table-column prop="refundReason" label="退款原因" min-width="140" show-overflow-tooltip>
                <template #default="{ row }">{{ row.refundReason || '--' }}</template>
              </el-table-column>
              <el-table-column label="退款金额" width="120" align="right">
                <template #default="{ row }">{{ formatMoney(row.amount) }}</template>
              </el-table-column>
              <el-table-column label="申请/退款时间" width="170">
                <template #default="{ row }">{{ formatTime(row.refundTime || row.createTime) }}</template>
              </el-table-column>
              <el-table-column label="状态" width="90">
                <template #default>
                  <span class="status refunded">已退款</span>
                </template>
              </el-table-column>
              <el-table-column label="审核人" width="100">
                <template #default>--</template>
              </el-table-column>
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
          </div>
        </el-tab-pane>
      </el-tabs>
    </div>

    <el-dialog v-model="visible" title="确认退款" width="440px" destroy-on-close>
      <el-form label-position="top">
        <el-form-item label="订单号">{{ refundRow?.orderNo }}</el-form-item>
        <el-form-item label="金额">{{ formatMoney(refundRow?.amount) }}</el-form-item>
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
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { formatMoney, formatNum, formatTime } from '@/config/analytics'
import { pageOrders, listRefundOrders, refundOrder, financeSummary, pageTenants } from '@/api/saas'

const tab = ref('refundable')
const loading = ref(false)
const loadingPaid = ref(false)
const loadingRefunded = ref(false)
const paidPage = ref(1)
const refundedPage = ref(1)
const paidTotal = ref(0)
const refundedTotal = ref(0)
const paidList = ref<any[]>([])
const refundedList = ref<any[]>([])
const finance = ref<any>(null)
const tenants = ref<any[]>([])
const visible = ref(false)
const refundRow = ref<any>(null)
const reason = ref('')
const refunding = ref(false)

function tenantName(id: number) {
  return tenants.value.find((t) => t.id === id)?.name || (id != null ? `租户#${id}` : '--')
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
    await ElMessageBox.confirm(
      `确认对订单 ${refundRow.value.orderNo} 退款 ${formatMoney(refundRow.value.amount)}？此操作不可撤销。`,
      '二次确认',
      { type: 'warning', confirmButtonText: '确认退款', confirmButtonClass: 'el-button--danger' },
    )
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

onMounted(async () => {
  try {
    const res = await pageTenants({ page: 1, size: 500 })
    tenants.value = res?.records || []
  } catch {
    tenants.value = []
  }
  await loadAll()
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
  display: flex;
  justify-content: space-between;
  gap: 12px;
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
  grid-template-columns: repeat(3, 1fr);
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
  font-size: 20px;
  font-weight: 650;
  color: #182230;
}
.panel {
  background: #fff;
  border: 1px solid #e4e7ec;
  border-radius: 8px;
  padding: 14px 16px;
}
.tabs :deep(.el-tabs__item.is-active) {
  color: #2563eb;
}
.status {
  display: inline-flex;
  height: 22px;
  align-items: center;
  padding: 0 8px;
  border-radius: 4px;
  font-size: 12px;
}
.status.paid {
  background: #ecfdf3;
  color: #067647;
}
.status.refunded {
  background: #f2f4f7;
  color: #667085;
}
.danger-link {
  color: #b42318 !important;
}
.pager {
  margin-top: 12px;
  display: flex;
  justify-content: flex-end;
}
@media (max-width: 768px) {
  .an-page {
    padding: 16px 12px;
  }
  .kpi-row {
    grid-template-columns: 1fr;
  }
}
</style>
