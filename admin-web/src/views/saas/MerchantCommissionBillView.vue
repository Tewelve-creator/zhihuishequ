<template>
  <div class="ops-page">
    <div class="ops-shell">
      <header class="page-hd">
        <div>
          <h1 class="page-title">提成明细</h1>
          <p class="page-desc">社区向商户计提的提成明细 · 平台不抽成 · 币种 CNY · 与 SaaS 套餐账务独立</p>
        </div>
        <div class="page-actions">
          <el-button :loading="loading" @click="load">刷新</el-button>
          <el-button @click="exportRows">导出</el-button>
          <el-button
            v-if="selected.length"
            type="success"
            :loading="settling"
            @click="doSettle"
          >
            标记已结算({{ selected.length }})
          </el-button>
        </div>
      </header>

      <div class="kpi-row">
        <div class="kpi">
          <span class="kpi-label">本页成交额</span>
          <span class="kpi-value">{{ money(pageStats.gmv) }}</span>
        </div>
        <div class="kpi">
          <span class="kpi-label">本页社区提成</span>
          <span class="kpi-value">{{ money(pageStats.commission) }}</span>
        </div>
        <div class="kpi">
          <span class="kpi-label">本页待结算</span>
          <span class="kpi-value warn">{{ money(pageStats.pending) }}</span>
        </div>
        <div class="kpi">
          <span class="kpi-label">账单合计</span>
          <span class="kpi-value">{{ total }}</span>
          <span class="kpi-sub">当前筛选结果总数</span>
        </div>
      </div>

      <div class="panel">
        <div class="toolbar">
          <el-select
            v-model="tenantId"
            clearable
            filterable
            placeholder="所属社区"
            style="width: 160px"
            @change="reload"
          >
            <el-option v-for="t in tenants" :key="t.id" :label="t.name" :value="t.id" />
          </el-select>
          <el-input
            v-model="keyword"
            clearable
            placeholder="商户 / 订单号"
            style="width: 180px"
            @keyup.enter="reload"
          />
          <el-select
            v-model="settleStatus"
            clearable
            placeholder="结算状态"
            style="width: 130px"
            @change="reload"
          >
            <el-option label="待结算" value="PENDING" />
            <el-option label="已结算" value="SETTLED" />
          </el-select>
          <el-button type="primary" @click="reload">查询</el-button>
        </div>

        <el-table
          v-loading="loading"
          :data="list"
          empty-text="暂无佣金账单"
          @selection-change="(rows: any[]) => (selected = rows)"
        >
          <el-table-column
            type="selection"
            width="42"
            :selectable="(r: any) => r.settleStatus === 'PENDING'"
          />
          <el-table-column label="账单号" min-width="120" show-overflow-tooltip>
            <template #default="{ row }">{{ row.id ?? row.orderNo ?? '--' }}</template>
          </el-table-column>
          <el-table-column prop="tenantName" label="账期/社区" min-width="120" show-overflow-tooltip>
            <template #default="{ row }">{{ row.tenantName || '--' }}</template>
          </el-table-column>
          <el-table-column prop="merchantName" label="商户" min-width="130" show-overflow-tooltip />
          <el-table-column label="订单金额" width="120" align="right">
            <template #default="{ row }">{{ money(row.gmv) }}</template>
          </el-table-column>
          <el-table-column label="退款金额" width="110" align="right">
            <template #default>
              <span class="muted" title="账单接口未拆退款">--</span>
            </template>
          </el-table-column>
          <el-table-column label="提成比例" width="90" align="right">
            <template #default="{ row }">
              {{ row.commissionRate != null ? `${row.commissionRate}%` : '--' }}
            </template>
          </el-table-column>
          <el-table-column label="社区提成" width="120" align="right">
            <template #default="{ row }">
              <span class="danger">{{ money(row.commissionAmount) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="结算状态" width="100">
            <template #default="{ row }">
              <span class="pill" :class="row.settleStatus === 'SETTLED' ? 'ok' : 'warn'">
                {{ row.settleStatusLabel || row.settleStatus }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="产生时间" width="168">
            <template #default="{ row }">{{ fmtTime(row.createTime) }}</template>
          </el-table-column>
        </el-table>

        <div class="pager">
          <el-pagination
            v-model:current-page="page"
            v-model:page-size="size"
            :total="total"
            layout="total, prev, pager, next"
            @current-change="load"
          />
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { pageTenants } from '@/api/saas'
import { exportExcel } from '@/utils/exportExcel'
import { fmtTime, money } from '@/utils/opsFormat'
import { pageMerchantCommissionBills, settleMerchantCommissionBills } from '@/api/store'

const loading = ref(false)
const settling = ref(false)
const list = ref<any[]>([])
const selected = ref<any[]>([])
const tenants = ref<any[]>([])
const tenantId = ref<number | undefined>()
const keyword = ref('')
const settleStatus = ref<string | undefined>()
const page = ref(1)
const size = ref(20)
const total = ref(0)

const pageStats = computed(() => {
  const rows = list.value
  const gmv = rows.reduce((s, r) => s + (Number(r.gmv) || 0), 0)
  const commission = rows.reduce((s, r) => s + (Number(r.commissionAmount) || 0), 0)
  const pending = rows
    .filter((r) => r.settleStatus === 'PENDING')
    .reduce((s, r) => s + (Number(r.commissionAmount) || 0), 0)
  return { gmv, commission, pending }
})

async function loadTenants() {
  try {
    const res = await pageTenants({ page: 1, size: 500 })
    tenants.value = res?.records || []
  } catch {
    tenants.value = []
  }
}

function reload() {
  page.value = 1
  load()
}

async function load() {
  loading.value = true
  try {
    const res = await pageMerchantCommissionBills({
      tenantId: tenantId.value,
      keyword: keyword.value || undefined,
      settleStatus: settleStatus.value,
      page: page.value,
      size: size.value,
    })
    list.value = res?.records || []
    total.value = res?.total || 0
  } catch {
    list.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function exportRows() {
  if (!list.value.length) {
    ElMessage.warning('暂无数据')
    return
  }
  exportExcel(
    list.value.map((r) => ({
      归属小区: r.tenantName,
      商户名称: r.merchantName,
      订单号: r.orderNo,
      成交额: r.gmv,
      佣金率: r.commissionRate,
      应收佣金: r.commissionAmount,
      结算状态: r.settleStatusLabel,
      产生时间: r.createTime,
      结算时间: r.settleTime || '',
    })),
    '佣金账单',
    '账单',
  )
}

async function doSettle() {
  const ids = selected.value.map((r) => r.id)
  if (!ids.length) return
  try {
    await ElMessageBox.confirm(`确认结算选中的 ${ids.length} 笔账单？`, '结算确认', {
      type: 'warning',
    })
  } catch {
    return
  }
  settling.value = true
  try {
    const res = await settleMerchantCommissionBills(ids)
    ElMessage.success(`已结算 ${res?.settledCount ?? 0} 笔`)
    await load()
  } finally {
    settling.value = false
  }
}

onMounted(async () => {
  await loadTenants()
  await load()
})
</script>

<style scoped>
.ops-page {
  margin: -20px;
  padding: 24px;
  min-height: calc(100% + 40px);
  background: #f5f6f8;
  box-sizing: border-box;
}
.ops-shell {
  max-width: 1440px;
  margin: 0 auto;
}
.page-hd {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
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
.page-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}
.kpi-row {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
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
.kpi-value.warn {
  color: #b54708;
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
.toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 12px;
}
.pill {
  display: inline-flex;
  height: 22px;
  align-items: center;
  padding: 0 8px;
  border-radius: 4px;
  font-size: 12px;
}
.pill.ok {
  background: #ecfdf3;
  color: #067647;
}
.pill.warn {
  background: #fffaeb;
  color: #b54708;
}
.danger {
  color: #b42318;
  font-weight: 550;
}
.muted {
  color: #98a2b3;
}
.pager {
  margin-top: 12px;
  display: flex;
  justify-content: flex-end;
}
@media (max-width: 768px) {
  .ops-page {
    padding: 16px 12px;
  }
  .kpi-row {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>
