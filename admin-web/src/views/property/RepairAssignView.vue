<template>
  <div class="page-card">
    <div class="page-header">
      <div>
        <h2>{{ pageTitle }}</h2>
        <p class="sub">手动指派 / 改派待办{{ typeLabel }}负责人（不再使用自动分派）</p>
      </div>
      <el-button :loading="orderLoading" @click="loadOrders">刷新</el-button>
    </div>

    <div class="filter-bar">
      <el-radio-group v-model="orderFilter" @change="loadOrders">
        <el-radio-button value="all">全部待办</el-radio-button>
        <el-radio-button value="unassigned">仅未指派</el-radio-button>
        <el-radio-button value="assigned">已指派</el-radio-button>
      </el-radio-group>
    </div>

    <el-table v-loading="orderLoading" :data="orders" stripe empty-text="暂无符合条件的工单">
      <el-table-column label="工单号" width="110">
        <template #default="{ row }">{{ orderNo(row) }}</template>
      </el-table-column>
      <el-table-column v-if="!fixedOrderType" label="类型" width="80">
        <template #default="{ row }">{{ row.orderType === 'report' ? '报事' : '报修' }}</template>
      </el-table-column>
      <el-table-column prop="title" label="标题" min-width="160" show-overflow-tooltip />
      <el-table-column prop="urgency" label="紧急" width="70" />
      <el-table-column prop="damageLevel" label="损坏" width="80">
        <template #default="{ row }">{{ row.damageLevel || '—' }}</template>
      </el-table-column>
      <el-table-column prop="handlerName" label="当前负责人" width="120">
        <template #default="{ row }">
          <span :class="{ muted: !row.handlerName }">{{ row.handlerName || '未指派' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="90">
        <template #default="{ row }">{{ statusLabel(row.status) }}</template>
      </el-table-column>
      <el-table-column label="提交时间" width="170">
        <template #default="{ row }">{{ fmtTime(row.createTime) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="100" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openAssign(row)">
            {{ row.handlerId ? '改派' : '指派' }}
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog
      v-model="assignVisible"
      :title="assignRow?.handlerId ? '改派负责人' : '指派负责人'"
      width="460px"
      destroy-on-close
    >
      <p v-if="assignRow" class="assign-tip">
        工单 {{ orderNo(assignRow) }}「{{ assignRow.title || '工单' }}」
        · 当前：{{ assignRow.handlerName || '未指派' }}
      </p>
      <el-select v-model="assignHandlerId" filterable placeholder="选择工作人员" style="width: 100%">
        <el-option
          v-for="s in staff"
          :key="s.id"
          :label="`${s.nickname || s.phone}（${roleLabel(s.role)}）`"
          :value="s.id"
        />
      </el-select>
      <template #footer>
        <el-button @click="assignVisible = false">取消</el-button>
        <el-button type="primary" :loading="assigning" @click="confirmAssign">确认</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { listStaffUsers, type StaffUser } from '@/api/users'
import { assignRepairOrder, listRepairOrders, type RepairOrder } from '@/api/repair'

const route = useRoute()
const orderLoading = ref(false)
const assigning = ref(false)
const orders = ref<RepairOrder[]>([])
const staff = ref<StaffUser[]>([])
const orderFilter = ref<'all' | 'unassigned' | 'assigned'>('all')
const assignVisible = ref(false)
const assignRow = ref<RepairOrder | null>(null)
const assignHandlerId = ref<number | undefined>()

const fixedOrderType = computed(() => {
  const t = route.meta.orderType
  return t === 'repair' || t === 'report' ? t : ''
})
const typeLabel = computed(() => (fixedOrderType.value === 'report' ? '报事工单' : '报修工单'))
const pageTitle = computed(() => (fixedOrderType.value === 'report' ? '报事分派' : '报修分派'))

function statusLabel(s?: string) {
  if (s === 'pending') return '待处理'
  if (s === 'processing') return '处理中'
  if (s === 'done') return '已完成'
  return s || '—'
}
function roleLabel(r?: string) {
  if (r === 'property_admin') return '物业管理员'
  if (r === 'grid_worker') return '网格员'
  return r || '员工'
}
/** 与报修列表一致：报事 BS + id，报修 BX + id */
function orderNo(row: RepairOrder) {
  if (!row?.id) return '—'
  return row.orderType === 'report' ? `BS${row.id}` : `BX${row.id}`
}
function fmtTime(t?: string) {
  if (!t) return '—'
  return String(t).replace('T', ' ').slice(0, 19)
}

async function loadOrders() {
  orderLoading.value = true
  try {
    const list =
      (await listRepairOrders({
        orderType: fixedOrderType.value || undefined,
        unassigned: orderFilter.value === 'unassigned' ? true : undefined,
      })) || []
    orders.value = list.filter((o) => {
      if (o.status === 'done') return false
      if (fixedOrderType.value && (o.orderType || 'repair') !== fixedOrderType.value) return false
      if (orderFilter.value === 'unassigned') return !o.handlerId
      if (orderFilter.value === 'assigned') return !!o.handlerId
      return true
    })
  } catch {
    orders.value = []
  } finally {
    orderLoading.value = false
  }
}

async function loadStaff() {
  const users = await listStaffUsers({ page: 1, size: 100, staffOnly: true }).catch(() => ({ records: [] }))
  staff.value = (users?.records || []).filter((s) => s.status === 1)
}

function openAssign(row: RepairOrder) {
  assignRow.value = row
  assignHandlerId.value = row.handlerId
  assignVisible.value = true
}

async function confirmAssign() {
  if (!assignRow.value || !assignHandlerId.value) {
    ElMessage.warning('请选择负责人')
    return
  }
  assigning.value = true
  try {
    await assignRepairOrder(assignRow.value.id, assignHandlerId.value)
    ElMessage.success('已指派')
    assignVisible.value = false
    await loadOrders()
  } finally {
    assigning.value = false
  }
}

watch(
  () => route.meta.orderType,
  () => {
    loadOrders()
  },
)

onMounted(async () => {
  await loadStaff()
  await loadOrders()
})
</script>

<style scoped>
.page-header { display: flex; justify-content: space-between; gap: 12px; margin-bottom: 12px; }
.filter-bar { display: flex; align-items: center; gap: 12px; margin-bottom: 12px; flex-wrap: wrap; }
.muted { color: #94a3b8; }
.assign-tip { margin: 0 0 12px; color: #64748b; font-size: 13px; }
</style>
