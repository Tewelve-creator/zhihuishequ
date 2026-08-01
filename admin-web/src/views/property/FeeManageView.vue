<template>
  <div class="page-card">
    <div class="page-header">
      <div>
        <h2>账单管理</h2>
        <p class="sub">新增、批量生成各类费用账单，维护账单状态；居民 App 缴费后自动同步为已缴</p>
      </div>
      <div class="actions">
        <el-button :loading="loading" @click="load">刷新</el-button>
        <el-button @click="onExport">导出Excel</el-button>
        <el-button v-if="canManage" @click="openBatch">批量月度账单</el-button>
        <el-button v-if="canManage" type="primary" @click="openCreate">新增账单</el-button>
      </div>
    </div>

    <el-alert
      v-if="user.role === 'grid_worker'"
      type="info"
      :closable="false"
      show-icon
      title="网格员仅可查看账单列表，不可新增、编辑或删除"
      style="margin-bottom: 12px"
    />

    <div class="filter-bar">
      <el-select v-model="status" clearable placeholder="缴费状态" style="width: 130px" @change="load">
        <el-option label="未缴" value="UNPAID" />
        <el-option label="支付中" value="PAYING" />
        <el-option label="已缴" value="PAID" />
      </el-select>
      <el-input
        v-model="period"
        clearable
        placeholder="缴费周期 如 2026-07"
        style="width: 160px"
        @keyup.enter="load"
      />
      <el-input
        v-model="keyword"
        clearable
        placeholder="房屋/缴费人/手机/费用项"
        style="width: 220px"
        @keyup.enter="load"
      />
      <el-button type="primary" :loading="loading" @click="load">查询</el-button>
    </div>

    <el-table v-loading="loading" :data="list" stripe empty-text="暂无账单">
      <el-table-column prop="room" label="房屋" min-width="200" show-overflow-tooltip />
      <el-table-column prop="owner" label="缴费人" width="100" />
      <el-table-column prop="item" label="费用项" width="110" />
      <el-table-column prop="plateNo" label="关联车牌" width="110">
        <template #default="{ row }">{{ row.plateNo || '—' }}</template>
      </el-table-column>
      <el-table-column prop="amount" label="金额" width="100">
        <template #default="{ row }">¥{{ Number(row.amount || 0).toFixed(2) }}</template>
      </el-table-column>
      <el-table-column prop="period" label="缴费周期" width="110" />
      <el-table-column prop="dueDate" label="截止日期" width="120" />
      <el-table-column prop="paidAt" label="缴费时间" width="170">
        <template #default="{ row }">{{ formatTime(row.paidAt) || '—' }}</template>
      </el-table-column>
      <el-table-column prop="statusLabel" label="缴费状态" width="100">
        <template #default="{ row }">
          <el-tag :type="statusTag(row)" size="small">{{ displayStatus(row) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column v-if="canManage" label="操作" width="140" fixed="right">
        <template #default="{ row }">
          <el-button
            link
            type="primary"
            :disabled="row.rawStatus === 'PAID' || row.status === 'PAID'"
            @click="openEdit(row)"
          >
            编辑
          </el-button>
          <el-button
            link
            type="danger"
            :disabled="row.rawStatus === 'PAID' || row.status === 'PAID'"
            @click="onDelete(row)"
          >
            删除
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <FeeBillDialog
      v-model="dialogVisible"
      :bill="editing"
      :houses="houses"
      @saved="load"
    />

    <el-dialog v-model="batchVisible" title="批量生成月度物业费账单" width="480px" destroy-on-close>
      <el-form :model="batch" label-width="100px">
        <el-form-item label="费用类型" required>
          <el-select v-model="batch.feeType" style="width: 100%">
            <el-option label="物业费" value="PROPERTY" />
            <el-option label="停车费" value="PARKING" />
            <el-option label="公摊水电费" value="SHARE" />
            <el-option label="其他" value="OTHER" />
          </el-select>
        </el-form-item>
        <el-form-item label="应收金额" required>
          <el-input-number v-model="batch.amount" :min="0.01" :precision="2" :step="10" style="width: 100%" />
        </el-form-item>
        <el-form-item label="缴费周期" required>
          <el-input v-model="batch.period" placeholder="如 2026-07" maxlength="16" />
        </el-form-item>
        <el-form-item label="截止日期">
          <el-date-picker
            v-model="batch.dueDate"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="选择日期"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="batch.remark" type="textarea" :rows="2" maxlength="200" />
        </el-form-item>
        <el-alert type="warning" :closable="false" show-icon title="将为本社区全部「正常」住户各生成一条账单；无有效住户的房屋会跳过。" />
      </el-form>
      <template #footer>
        <el-button @click="batchVisible = false">取消</el-button>
        <el-button type="primary" :loading="batchSaving" @click="submitBatch">生成</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { exportExcel } from '@/utils/exportExcel'
import {
  batchCreateFeeBills,
  deleteFeeBill,
  listFeeBills,
  listFeeHouses,
  type FeeBill,
  type FeeHouseOption,
} from '@/api/fee'
import FeeBillDialog from './FeeBillDialog.vue'

const user = useUserStore()
const route = useRoute()
const canManage = computed(
  () => user.hasRealToken && (user.role === 'property_admin' || user.role === 'platform_admin'),
)

const loading = ref(false)
const list = ref<FeeBill[]>([])
const houses = ref<FeeHouseOption[]>([])
const status = ref('')
const keyword = ref('')
const period = ref('')
const dialogVisible = ref(false)
const editing = ref<FeeBill | null>(null)
const batchVisible = ref(false)
const batchSaving = ref(false)
const batch = reactive({
  feeType: 'PROPERTY',
  amount: 274.4,
  period: '',
  dueDate: '',
  remark: '',
})

function displayStatus(row: FeeBill) {
  if (row.status === 'OVERDUE' || row.statusLabel === '已逾期') return '未缴(逾期)'
  if (row.rawStatus === 'PAID' || row.status === 'PAID') return '已缴'
  if (row.rawStatus === 'PAYING' || row.status === 'PAYING') return '支付中'
  return row.statusLabel || '未缴'
}

function statusTag(row: FeeBill) {
  if (row.rawStatus === 'PAID' || row.status === 'PAID') return 'success'
  if (row.status === 'OVERDUE') return 'danger'
  if (row.rawStatus === 'PAYING') return 'warning'
  return 'warning'
}

function formatTime(v?: string) {
  if (!v) return ''
  return String(v).replace('T', ' ').substring(0, 19)
}

async function load() {
  loading.value = true
  try {
    list.value = await listFeeBills({
      status: status.value || undefined,
      keyword: keyword.value || undefined,
      period: period.value || undefined,
    })
  } catch {
    list.value = []
  } finally {
    loading.value = false
  }
}

async function loadHouses() {
  try {
    houses.value = await listFeeHouses()
  } catch {
    houses.value = []
  }
}

function openCreate() {
  editing.value = null
  dialogVisible.value = true
}

function openEdit(row: FeeBill) {
  editing.value = { ...row }
  dialogVisible.value = true
}

async function onDelete(row: FeeBill) {
  if (row.rawStatus === 'PAID' || row.status === 'PAID') {
    ElMessage.warning('已缴账单禁止删除')
    return
  }
  await ElMessageBox.confirm(`确认删除「${row.room || ''} ${row.item || ''}」账单？`, '删除确认', {
    type: 'warning',
  })
  await deleteFeeBill(row.id)
  ElMessage.success('已删除')
  load()
}

function onExport() {
  const rows = list.value.map((r) => ({
    房屋: r.room || r.houseLabel,
    缴费人: r.owner || r.payerName,
    手机号: r.payerPhone,
    费用项: r.item || r.feeTypeName,
    金额: r.amount,
    缴费周期: r.period,
    截止日期: r.dueDate,
    缴费时间: formatTime(r.paidAt) || '',
    状态: displayStatus(r),
    备注: r.remark || '',
  }))
  exportExcel(rows, `缴费账单_${Date.now()}`)
  ElMessage.success(status.value === 'UNPAID' ? '已导出当前筛选（含未缴）账单' : '导出成功')
}

function openBatch() {
  const now = new Date()
  batch.feeType = 'PROPERTY'
  batch.amount = 274.4
  batch.period = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`
  batch.dueDate = `${batch.period}-25`
  batch.remark = '批量月度物业费'
  batchVisible.value = true
}

async function submitBatch() {
  if (!batch.period || !batch.amount || batch.amount <= 0) {
    ElMessage.warning('请填写周期与正数金额')
    return
  }
  batchSaving.value = true
  try {
    const res = await batchCreateFeeBills({
      feeType: batch.feeType,
      amount: batch.amount,
      period: batch.period,
      dueDate: batch.dueDate || undefined,
      remark: batch.remark || undefined,
    })
    ElMessage.success(`已生成 ${res?.count ?? 0} 条账单`)
    batchVisible.value = false
    load()
  } finally {
    batchSaving.value = false
  }
}

onMounted(() => {
  if (route.query.keyword) {
    keyword.value = String(route.query.keyword)
  }
  load()
  loadHouses()
})
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
</style>
