<template>
  <div class="page-card">
    <div class="page-header">
      <div>
        <h2>车位档案</h2>
        <p class="sub">维护小区车位信息，区分产权车位、租赁车位，记录车位状态</p>
      </div>
      <div class="actions">
        <el-button :loading="loading" @click="refreshAll">刷新</el-button>
        <el-button @click="onExport">导出Excel</el-button>
        <el-button v-if="canManage" type="primary" @click="openCreate">新增车位</el-button>
      </div>
    </div>

    <div class="stat-row">
      <div class="stat-card">
        <div class="stat-label">总车位</div>
        <div class="stat-value">{{ stats.total }}</div>
      </div>
      <div class="stat-card free">
        <div class="stat-label">空闲车位</div>
        <div class="stat-value">{{ stats.free }}</div>
      </div>
      <div class="stat-card occupied">
        <div class="stat-label">已占用车位</div>
        <div class="stat-value">{{ stats.occupied }}</div>
      </div>
    </div>

    <div class="filter-bar">
      <el-select v-model="spaceType" clearable placeholder="车位类型" style="width: 140px" @change="onSearch">
        <el-option label="产权车位" value="OWNED" />
        <el-option label="租赁车位" value="RENT" />
        <el-option label="公共临停" value="PUBLIC" />
      </el-select>
      <el-select v-model="status" clearable placeholder="状态" style="width: 120px" @change="onSearch">
        <el-option label="空闲" value="FREE" />
        <el-option label="占用" value="OCCUPIED" />
        <el-option label="预留" value="RESERVED" />
        <el-option label="故障" value="FAULT" />
      </el-select>
      <el-input v-model="areaCode" clearable placeholder="区域编码" style="width: 140px" @keyup.enter="onSearch" />
      <el-button type="primary" :loading="loading" @click="onSearch">查询</el-button>
    </div>

    <el-table v-loading="loading" :data="pagedList" stripe empty-text="暂无车位">
      <el-table-column prop="spaceNo" label="车位编号" width="120" />
      <el-table-column prop="areaCode" label="区域" width="100">
        <template #default="{ row }">{{ row.areaCode || '—' }}</template>
      </el-table-column>
      <el-table-column label="车位类型" width="110">
        <template #default="{ row }">{{ typeLabel(row.spaceType) }}</template>
      </el-table-column>
      <el-table-column label="车位状态" width="100">
        <template #default="{ row }">
          <el-tag :type="statusTag(row.status)" size="small" effect="light">
            {{ statusLabel(row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="bindPlateNo" label="绑定车牌" width="120">
        <template #default="{ row }">{{ row.bindPlateNo || '—' }}</template>
      </el-table-column>
      <el-table-column label="占用业主" width="120">
        <template #default="{ row }">{{ row.ownerName || '—' }}</template>
      </el-table-column>
      <el-table-column prop="remark" label="备注" min-width="140" show-overflow-tooltip />
      <el-table-column v-if="canManage" label="操作" width="220" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openBind(row)">绑定车辆</el-button>
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button link type="danger" @click="onDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pager">
      <el-pagination
        v-model:current-page="page"
        v-model:page-size="pageSize"
        background
        layout="total, sizes, prev, pager, next"
        :page-sizes="[10, 20, 50]"
        :total="list.length"
      />
    </div>

    <el-dialog v-model="visible" :title="form.id ? '编辑车位' : '新增车位'" width="520px" destroy-on-close>
      <el-form label-width="100px">
        <el-form-item label="车位编号" required>
          <el-input v-model="form.spaceNo" placeholder="如 A-101" maxlength="32" />
        </el-form-item>
        <el-form-item label="区域编码">
          <el-input v-model="form.areaCode" placeholder="如 A区" maxlength="32" />
        </el-form-item>
        <el-form-item label="车位类型" required>
          <el-select v-model="form.spaceType" style="width: 100%">
            <el-option label="产权车位" value="OWNED" />
            <el-option label="租赁车位" value="RENT" />
            <el-option label="公共临停" value="PUBLIC" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="form.status" style="width: 100%">
            <el-option label="空闲" value="FREE" />
            <el-option label="占用" value="OCCUPIED" />
            <el-option label="预留" value="RESERVED" />
            <el-option label="故障" value="FAULT" />
          </el-select>
        </el-form-item>
        <el-form-item label="绑定车牌">
          <el-input v-model="form.bindPlateNo" placeholder="产权/租赁固定车牌" maxlength="10" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="bindVisible"
      :title="`绑定车辆 · ${bindSpace?.spaceNo || ''}`"
      width="560px"
      destroy-on-close
    >
      <el-input
        v-model="carKeyword"
        clearable
        placeholder="搜索车牌 / 业主姓名 / 房号"
        style="margin-bottom: 12px"
        @keyup.enter="loadCars"
      >
        <template #append>
          <el-button :loading="carsLoading" @click="loadCars">搜索</el-button>
        </template>
      </el-input>
      <el-table
        v-loading="carsLoading"
        :data="cars"
        highlight-current-row
        max-height="360"
        empty-text="暂无业主车辆"
        @current-change="onPickCar"
      >
        <el-table-column width="48">
          <template #default="{ row }">
            <el-radio v-model="selectedCarId" :value="row.id">&nbsp;</el-radio>
          </template>
        </el-table-column>
        <el-table-column prop="plateNo" label="车牌" width="120" />
        <el-table-column prop="ownerName" label="业主" width="100">
          <template #default="{ row }">{{ row.ownerName || '—' }}</template>
        </el-table-column>
        <el-table-column prop="houseLabel" label="房屋" min-width="140" show-overflow-tooltip>
          <template #default="{ row }">{{ row.houseLabel || '—' }}</template>
        </el-table-column>
        <el-table-column label="已绑车位" width="100">
          <template #default="{ row }">{{ row.spaceNo || '—' }}</template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="bindVisible = false">取消</el-button>
        <el-button type="primary" :loading="binding" :disabled="!selectedCarId" @click="confirmBind">
          确认绑定
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { exportExcel } from '@/utils/exportExcel'
import {
  bindParkSpaceCar,
  deleteParkSpace,
  getParkSpaceStats,
  listParkSpaces,
  saveParkSpace,
  type ParkSpace,
} from '@/api/parking'
import { listResidentCars, type ResidentCar } from '@/api/residentCar'

const user = useUserStore()
const canManage = computed(
  () => user.hasRealToken && (user.role === 'property_admin' || user.role === 'platform_admin'),
)

const loading = ref(false)
const saving = ref(false)
const binding = ref(false)
const carsLoading = ref(false)
const list = ref<ParkSpace[]>([])
const spaceType = ref('')
const status = ref('')
const areaCode = ref('')
const page = ref(1)
const pageSize = ref(10)
const visible = ref(false)
const bindVisible = ref(false)
const bindSpace = ref<ParkSpace | null>(null)
const cars = ref<ResidentCar[]>([])
const carKeyword = ref('')
const selectedCarId = ref<number | undefined>()

const stats = reactive({ total: 0, free: 0, occupied: 0 })

const form = reactive({
  id: undefined as number | undefined,
  spaceNo: '',
  areaCode: '',
  spaceType: 'OWNED',
  status: 'FREE',
  bindPlateNo: '',
  remark: '',
})

const pagedList = computed(() => {
  const start = (page.value - 1) * pageSize.value
  return list.value.slice(start, start + pageSize.value)
})

function typeLabel(t?: string) {
  return t === 'OWNED' ? '产权车位' : t === 'RENT' ? '租赁车位' : t === 'PUBLIC' ? '公共临停' : t || '—'
}
function statusLabel(s?: string) {
  return ({ FREE: '空闲', OCCUPIED: '占用', RESERVED: '预留', FAULT: '故障' } as Record<string, string>)[s || ''] || s || '—'
}
function statusTag(s?: string) {
  if (s === 'FREE') return 'success'
  if (s === 'OCCUPIED') return 'warning'
  if (s === 'FAULT') return 'danger'
  return 'info'
}

function onSearch() {
  page.value = 1
  load()
}

async function loadStats() {
  try {
    const s = await getParkSpaceStats()
    stats.total = Number(s?.total || 0)
    stats.free = Number(s?.free || 0)
    stats.occupied = Number(s?.occupied || 0)
  } catch {
    stats.total = list.value.length
    stats.free = list.value.filter((r) => r.status === 'FREE').length
    stats.occupied = list.value.filter((r) => r.status === 'OCCUPIED').length
  }
}

async function load() {
  loading.value = true
  try {
    list.value =
      (await listParkSpaces({
        spaceType: spaceType.value || undefined,
        status: status.value || undefined,
        areaCode: areaCode.value || undefined,
      })) || []
    const maxPage = Math.max(1, Math.ceil(list.value.length / pageSize.value) || 1)
    if (page.value > maxPage) page.value = maxPage
  } finally {
    loading.value = false
  }
}

async function refreshAll() {
  await Promise.all([load(), loadStats()])
}

function openCreate() {
  Object.assign(form, {
    id: undefined,
    spaceNo: '',
    areaCode: '',
    spaceType: 'OWNED',
    status: 'FREE',
    bindPlateNo: '',
    remark: '',
  })
  visible.value = true
}

function openEdit(row: ParkSpace) {
  Object.assign(form, {
    id: row.id,
    spaceNo: row.spaceNo,
    areaCode: row.areaCode || '',
    spaceType: row.spaceType || 'OWNED',
    status: row.status || 'FREE',
    bindPlateNo: row.bindPlateNo || '',
    remark: row.remark || '',
  })
  visible.value = true
}

async function save() {
  if (!form.spaceNo.trim()) {
    ElMessage.warning('请填写车位编号')
    return
  }
  saving.value = true
  try {
    await saveParkSpace({ ...form })
    ElMessage.success('已保存')
    visible.value = false
    await refreshAll()
  } finally {
    saving.value = false
  }
}

async function onDelete(row: ParkSpace) {
  await ElMessageBox.confirm(`确认删除车位「${row.spaceNo}」？`, '提示')
  await deleteParkSpace(row.id)
  ElMessage.success('已删除')
  await refreshAll()
}

async function openBind(row: ParkSpace) {
  bindSpace.value = row
  selectedCarId.value = row.bindCarId
  carKeyword.value = ''
  bindVisible.value = true
  await loadCars()
}

async function loadCars() {
  carsLoading.value = true
  try {
    cars.value =
      (await listResidentCars({
        status: 'ACTIVE',
        keyword: carKeyword.value || undefined,
      })) || []
  } catch {
    cars.value = []
  } finally {
    carsLoading.value = false
  }
}

function onPickCar(row: ResidentCar | null) {
  selectedCarId.value = row?.id
}

async function confirmBind() {
  if (!bindSpace.value?.id || !selectedCarId.value) {
    ElMessage.warning('请选择要绑定的车辆')
    return
  }
  binding.value = true
  try {
    await bindParkSpaceCar(bindSpace.value.id, selectedCarId.value)
    ElMessage.success('绑定成功，车位已更新为占用')
    bindVisible.value = false
    await refreshAll()
  } finally {
    binding.value = false
  }
}

function onExport() {
  exportExcel(
    (list.value || []).map((r) => ({
      车位编号: r.spaceNo,
      区域: r.areaCode || '',
      车位类型: typeLabel(r.spaceType),
      车位状态: statusLabel(r.status),
      绑定车牌: r.bindPlateNo || '',
      占用业主: r.ownerName || '',
      备注: r.remark || '',
    })),
    '车位档案.xlsx',
  )
}

onMounted(refreshAll)
</script>

<style scoped>
.page-header { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 12px; }
.page-header h2 { margin: 0 0 4px; }
.sub { margin: 0; color: #64748b; font-size: 13px; }
.actions { display: flex; gap: 8px; }
.filter-bar { display: flex; gap: 8px; margin-bottom: 12px; flex-wrap: wrap; }

.stat-row {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
  margin-bottom: 14px;
}
.stat-card {
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  padding: 14px 16px;
}
.stat-card.free {
  background: #f0fdf4;
  border-color: #bbf7d0;
}
.stat-card.occupied {
  background: #fff7ed;
  border-color: #fed7aa;
}
.stat-label {
  font-size: 13px;
  color: #64748b;
  margin-bottom: 6px;
}
.stat-value {
  font-size: 24px;
  font-weight: 700;
  color: #0f172a;
  line-height: 1.2;
}
.stat-card.free .stat-value { color: #16a34a; }
.stat-card.occupied .stat-value { color: #ea580c; }

.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

@media (max-width: 768px) {
  .stat-row { grid-template-columns: 1fr; }
}
</style>
