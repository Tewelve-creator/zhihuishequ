<template>
  <div class="page-card">
    <div class="page-header">
      <div>
        <h2>设备清单</h2>
        <p class="sub">小区设施设备台账 · 维保周期与到期状态，支撑设备维保工单</p>
      </div>
      <div class="actions">
        <el-button :loading="loading" @click="load">刷新</el-button>
        <el-button @click="onExport">导出</el-button>
        <el-button v-if="canManage" type="primary" @click="openEdit()">新增设备</el-button>
      </div>
    </div>

    <div class="stat-row">
      <div class="stat-card">
        <div class="stat-label">台账条目</div>
        <div class="stat-value">{{ stats.rows }}</div>
        <div class="stat-hint">种</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">设备总数量</div>
        <div class="stat-value">{{ stats.qty }}</div>
        <div class="stat-hint">台/套</div>
      </div>
      <div class="stat-card ok">
        <div class="stat-label">正常</div>
        <div class="stat-value">{{ stats.normalQty }}</div>
      </div>
      <div class="stat-card warn">
        <div class="stat-label">待维保</div>
        <div class="stat-value">{{ stats.dueQty }}</div>
      </div>
      <div class="stat-card danger">
        <div class="stat-label">已逾期</div>
        <div class="stat-value">{{ stats.overdueQty }}</div>
      </div>
    </div>

    <div class="filter-bar">
      <el-select v-model="status" clearable placeholder="维保状态" style="width: 130px" @change="load">
        <el-option label="正常" value="NORMAL" />
        <el-option label="待维保" value="DUE" />
        <el-option label="已逾期" value="OVERDUE" />
      </el-select>
      <el-select v-model="deviceType" clearable placeholder="设备类型" style="width: 130px" @change="applyLocal">
        <el-option v-for="t in typeOptions" :key="t" :label="t" :value="t" />
      </el-select>
      <el-input
        v-model="keyword"
        clearable
        placeholder="名称/位置/服务商"
        style="width: 220px"
        @keyup.enter="load"
      />
      <el-button type="primary" :loading="loading" @click="load">查询</el-button>
    </div>

    <el-table v-loading="loading" :data="filtered" stripe empty-text="暂无设备，请先新增">
      <el-table-column prop="name" label="设备名称" min-width="140" show-overflow-tooltip />
      <el-table-column prop="deviceType" label="类型" width="100" />
      <el-table-column label="数量" width="80" align="center">
        <template #default="{ row }">{{ Number(row.quantity || 1) }}</template>
      </el-table-column>
      <el-table-column prop="location" label="位置" min-width="140" show-overflow-tooltip />
      <el-table-column prop="cycleLabel" label="维保周期" width="100">
        <template #default="{ row }">{{ row.cycleLabel || cycleText(row.cycleType) }}</template>
      </el-table-column>
      <el-table-column prop="vendorName" label="服务商" width="120" show-overflow-tooltip>
        <template #default="{ row }">{{ row.vendorName || '—' }}</template>
      </el-table-column>
      <el-table-column prop="contactName" label="联系人" width="100">
        <template #default="{ row }">{{ row.contactName || '—' }}</template>
      </el-table-column>
      <el-table-column prop="contactPhone" label="电话" width="120">
        <template #default="{ row }">{{ row.contactPhone || '—' }}</template>
      </el-table-column>
      <el-table-column prop="lastMaintDate" label="上次维保" width="120">
        <template #default="{ row }">{{ fmtDate(row.lastMaintDate) }}</template>
      </el-table-column>
      <el-table-column prop="nextMaintDate" label="下次维保" width="120">
        <template #default="{ row }">{{ fmtDate(row.nextMaintDate) }}</template>
      </el-table-column>
      <el-table-column prop="statusLabel" label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="statusTag(row.status)" size="small" round>
            {{ row.statusLabel || statusText(row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="220" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="goMaint(row)">建维保单</el-button>
          <el-button v-if="canManage" link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button v-if="canManage" link type="danger" @click="onDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <EquipDeviceDialog v-model="dlg" :device="editing" @saved="load" />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { exportExcel } from '@/utils/exportExcel'
import {
  deleteEquipDevice,
  listEquipDevices,
  type EquipDevice,
} from '@/api/equip'
import EquipDeviceDialog from './EquipDeviceDialog.vue'

const user = useUserStore()
const router = useRouter()
const canManage = computed(
  () => user.hasRealToken && (user.role === 'property_admin' || user.role === 'platform_admin'),
)

const loading = ref(false)
const list = ref<EquipDevice[]>([])
const status = ref('')
const deviceType = ref('')
const keyword = ref('')
const dlg = ref(false)
const editing = ref<EquipDevice | null>(null)

const typeOptions = computed(() => {
  const set = new Set<string>()
  list.value.forEach((d) => {
    if (d.deviceType) set.add(d.deviceType)
  })
  ;['电梯', '消防水泵', '监控', '门禁', '充电桩', '其他'].forEach((t) => set.add(t))
  return [...set]
})

const filtered = computed(() => {
  let rows = list.value
  if (deviceType.value) {
    rows = rows.filter((r) => r.deviceType === deviceType.value)
  }
  return rows
})

const stats = computed(() => {
  const rows = list.value
  const qtyOf = (r: EquipDevice) => Number(r.quantity || 1)
  const sum = (pred: (r: EquipDevice) => boolean) =>
    rows.filter(pred).reduce((s, r) => s + qtyOf(r), 0)
  return {
    rows: rows.length,
    qty: rows.reduce((s, r) => s + qtyOf(r), 0),
    normalQty: sum((r) => r.status === 'NORMAL'),
    dueQty: sum((r) => r.status === 'DUE'),
    overdueQty: sum((r) => r.status === 'OVERDUE'),
  }
})

function statusTag(s?: string) {
  if (s === 'OVERDUE') return 'danger'
  if (s === 'DUE') return 'warning'
  return 'success'
}
function statusText(s?: string) {
  if (s === 'OVERDUE') return '已逾期'
  if (s === 'DUE') return '即将到期'
  return '正常'
}
function cycleText(c?: string) {
  if (c === 'MONTH') return '月度'
  if (c === 'HALF_YEAR') return '半年'
  if (c === 'YEAR') return '年度'
  return '季度'
}
function fmtDate(v?: string) {
  if (!v) return '—'
  return String(v).substring(0, 10)
}
function applyLocal() {
  /* deviceType 本地过滤，list 已含全部 */
}

async function load() {
  loading.value = true
  try {
    list.value =
      (await listEquipDevices({
        status: status.value || undefined,
        keyword: keyword.value || undefined,
      })) || []
  } catch (e: any) {
    list.value = []
    ElMessage.error(e?.msg || e?.message || '加载设备失败')
  } finally {
    loading.value = false
  }
}

function openEdit(row?: EquipDevice) {
  editing.value = row || null
  dlg.value = true
}

async function onDelete(row: EquipDevice) {
  await ElMessageBox.confirm(`确认删除设备「${row.name}」？`, '提示')
  await deleteEquipDevice(row.id)
  ElMessage.success('已删除')
  load()
}

function goMaint(row: EquipDevice) {
  router.push({
    path: '/property/maintenance',
    query: {
      deviceId: String(row.id),
      deviceName: row.name,
      location: row.location || '',
    },
  })
}

function onExport() {
  const rows = filtered.value.map((r) => ({
    设备名称: r.name,
    类型: r.deviceType || '',
    数量: Number(r.quantity || 1),
    位置: r.location || '',
    维保周期: r.cycleLabel || cycleText(r.cycleType),
    服务商: r.vendorName || '',
    联系人: r.contactName || '',
    电话: r.contactPhone || '',
    上次维保: fmtDate(r.lastMaintDate),
    下次维保: fmtDate(r.nextMaintDate),
    状态: r.statusLabel || statusText(r.status),
  }))
  if (!rows.length) {
    ElMessage.warning('暂无数据可导出')
    return
  }
  exportExcel(rows, `设备清单_${Date.now()}`)
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
.actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}
.stat-row {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 12px;
}
.stat-card {
  background: #f8faf9;
  border: 1px solid #e8eeeb;
  border-radius: 12px;
  padding: 14px 16px;
}
.stat-card.ok .stat-value { color: #2f9e6f; }
.stat-card.warn .stat-value { color: #d97706; }
.stat-card.danger .stat-value { color: #e85d4c; }
.stat-label {
  font-size: 13px;
  color: #64748b;
}
.stat-value {
  margin-top: 6px;
  font-size: 28px;
  font-weight: 800;
  color: #1f2a28;
}
.stat-hint {
  margin-top: 2px;
  font-size: 12px;
  color: #94a3b8;
}
.filter-bar {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
  flex-wrap: wrap;
}
@media (max-width: 1100px) {
  .stat-row {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}
@media (max-width: 700px) {
  .stat-row {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
