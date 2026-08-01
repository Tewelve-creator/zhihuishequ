<template>
  <div class="page-card">
    <div class="page-header">
      <div>
        <h2>停车收费台账</h2>
        <p class="sub">汇总月租与临时停车缴费记录，支持筛选导出对账；长期欠费可同步限行</p>
      </div>
      <div class="actions">
        <el-button :loading="loading" @click="load">刷新</el-button>
        <el-button v-if="canManage" type="warning" :loading="syncing" @click="onSync">同步欠费限行</el-button>
        <el-button @click="onExport">导出Excel</el-button>
      </div>
    </div>

    <div class="filter-bar">
      <el-select v-model="feeKind" clearable placeholder="费用类型" style="width: 130px" @change="load">
        <el-option label="全部" value="ALL" />
        <el-option label="月租" value="MONTHLY" />
        <el-option label="临时停车" value="TEMP" />
      </el-select>
      <el-select v-model="status" clearable placeholder="状态" style="width: 120px" @change="load">
        <el-option label="待缴费" value="UNPAID" />
        <el-option label="已缴费" value="PAID" />
        <el-option label="已逾期" value="OVERDUE" />
        <el-option label="在场计费" value="PARKING" />
      </el-select>
      <el-input
        v-model="keyword"
        clearable
        placeholder="车牌/房屋/账期"
        style="width: 200px"
        @keyup.enter="load"
      />
      <el-button type="primary" :loading="loading" @click="load">查询</el-button>
    </div>

    <el-table v-loading="loading" :data="list" stripe empty-text="暂无收费记录">
      <el-table-column prop="feeKindLabel" label="类型" width="100" />
      <el-table-column prop="plateNo" label="车牌" width="120" />
      <el-table-column prop="houseLabel" label="房屋" min-width="140" show-overflow-tooltip>
        <template #default="{ row }">{{ row.houseLabel || '—' }}</template>
      </el-table-column>
      <el-table-column prop="period" label="账期/进场" width="160" show-overflow-tooltip />
      <el-table-column prop="amount" label="金额" width="100">
        <template #default="{ row }">¥{{ row.amount ?? 0 }}</template>
      </el-table-column>
      <el-table-column prop="statusLabel" label="状态" width="100">
        <template #default="{ row }">
          <el-tag
            :type="row.status === 'PAID' ? 'success' : row.status === 'UNPAID' || row.status === 'OVERDUE' ? 'danger' : 'info'"
            size="small"
          >{{ row.statusLabel }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="orderNo" label="订单号" min-width="140" show-overflow-tooltip>
        <template #default="{ row }">{{ row.orderNo || '—' }}</template>
      </el-table-column>
      <el-table-column prop="createTime" label="时间" width="170">
        <template #default="{ row }">{{ fmt(row.payTime || row.createTime) }}</template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { exportExcel } from '@/utils/exportExcel'
import { listParkFeeLedger, syncParkArrearsRestrict, type ParkFeeLedgerRow } from '@/api/parking'

const user = useUserStore()
const canManage = computed(
  () => user.hasRealToken && (user.role === 'property_admin' || user.role === 'platform_admin'),
)

const loading = ref(false)
const syncing = ref(false)
const list = ref<ParkFeeLedgerRow[]>([])
const feeKind = ref('ALL')
const status = ref('')
const keyword = ref('')

function fmt(v?: string) {
  if (!v) return '—'
  return String(v).replace('T', ' ').substring(0, 19)
}

async function load() {
  loading.value = true
  try {
    list.value =
      (await listParkFeeLedger({
        feeKind: feeKind.value || 'ALL',
        status: status.value || undefined,
        keyword: keyword.value || undefined,
      })) || []
  } finally {
    loading.value = false
  }
}

async function onSync() {
  await ElMessageBox.confirm(
    '将把欠费超过 30 天的月租停车费车牌同步为限行（禁止进场），确认继续？',
    '同步欠费限行',
  )
  syncing.value = true
  try {
    const r = await syncParkArrearsRestrict()
    ElMessage.success(r?.message || '已同步')
  } finally {
    syncing.value = false
  }
}

function onExport() {
  exportExcel(
    (list.value || []).map((r) => ({
      类型: r.feeKindLabel || '',
      车牌: r.plateNo || '',
      房屋: r.houseLabel || '',
      账期进场: r.period || '',
      金额: r.amount ?? 0,
      状态: r.statusLabel || '',
      订单号: r.orderNo || '',
      时间: fmt(r.payTime || r.createTime),
    })),
    '停车收费台账.xlsx',
  )
}

onMounted(load)
</script>

<style scoped>
.page-header { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 12px; }
.page-header h2 { margin: 0 0 4px; }
.sub { margin: 0; color: #64748b; font-size: 13px; }
.actions { display: flex; gap: 8px; flex-wrap: wrap; }
.filter-bar { display: flex; gap: 8px; margin-bottom: 12px; flex-wrap: wrap; }
</style>
