<template>
  <div class="page-card">
    <div class="page-header">
      <div>
        <h2>住户管理</h2>
        <p class="sub">支持录入常驻车辆；一套房屋可绑多车。住户迁出后车辆自动停用。</p>
      </div>
      <div class="actions">
        <el-button :loading="loading" @click="load">刷新</el-button>
        <el-button v-if="canManage" type="primary" @click="openCreate">新增住户</el-button>
      </div>
    </div>

    <el-alert
      v-if="!canManage"
      type="info"
      :closable="false"
      show-icon
      title="网格员仅可查看住户与车辆，不可编辑"
      style="margin-bottom: 12px"
    />

    <div class="filter-bar">
      <el-select v-model="status" clearable placeholder="全部状态" style="width: 140px" @change="load">
        <el-option label="正常" value="ACTIVE" />
        <el-option label="待审核" value="PENDING" />
        <el-option label="迁出中" value="MOVING_OUT" />
        <el-option label="已迁出" value="MOVED_OUT" />
      </el-select>
      <el-input
        v-model="keyword"
        clearable
        placeholder="姓名/手机/房屋"
        style="width: 220px"
        @keyup.enter="load"
      />
      <el-button type="primary" :loading="loading" @click="load">查询</el-button>
    </div>

    <el-table v-loading="loading" :data="list" stripe empty-text="暂无住户">
      <el-table-column prop="name" label="姓名" width="100" />
      <el-table-column prop="phone" label="手机号" width="130" />
      <el-table-column prop="room" label="房屋位置" min-width="200" />
      <el-table-column prop="type" label="身份" width="90" />
      <el-table-column prop="statusLabel" label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="tagType(row.status)" size="small">{{ row.statusLabel }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="source" label="来源" width="90">
        <template #default="{ row }">
          {{ row.source === 'MANUAL' ? '手录' : '审批同步' }}
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="创建时间" width="170" />
      <el-table-column label="操作" :width="canManage ? 220 : 100" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openCars(row)">车辆</el-button>
          <template v-if="canManage">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button
              v-if="row.status !== 'MOVED_OUT'"
              link
              type="danger"
              @click="onRemove(row)"
            >
              迁出
            </el-button>
          </template>
        </template>
      </el-table-column>
    </el-table>

    <div class="pager">
      <el-pagination
        v-model:current-page="page"
        v-model:page-size="size"
        background
        layout="total, prev, pager, next"
        :total="total"
        @current-change="load"
        @size-change="load"
      />
    </div>

    <el-dialog v-model="visible" :title="form.id ? '编辑住户' : '新增住户'" width="480px" destroy-on-close>
      <el-form :model="form" label-width="88px">
        <el-form-item label="姓名" required>
          <el-input v-model="form.name" maxlength="32" />
        </el-form-item>
        <el-form-item label="手机号" required>
          <el-input v-model="form.phone" maxlength="11" />
        </el-form-item>
        <el-form-item label="房屋" required>
          <el-select v-model="form.houseId" filterable style="width: 100%" placeholder="选择本社区房屋">
            <el-option
              v-for="h in catalog"
              :key="h.id"
              :label="h.label || `${h.building}${h.unit}${h.roomNo}`"
              :value="h.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="身份" required>
          <el-select v-model="form.identityType" style="width: 100%">
            <el-option label="业主" value="OWNER" />
            <el-option label="租客" value="TENANT" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="form.id" label="状态">
          <el-select v-model="form.status" style="width: 100%">
            <el-option label="正常" value="ACTIVE" />
            <el-option label="迁出中" value="MOVING_OUT" />
            <el-option label="已迁出" value="MOVED_OUT" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="carVisible" :title="`车辆管理 · ${carResident?.name || ''}`" size="520px">
      <div v-if="canManage" class="car-actions">
        <el-button type="primary" @click="openCarForm()">新增车辆</el-button>
      </div>
      <el-table v-loading="carLoading" :data="cars" size="small" empty-text="暂无车辆">
        <el-table-column prop="plateNo" label="车牌" width="110" />
        <el-table-column prop="brandModel" label="车型" />
        <el-table-column prop="color" label="颜色" width="70" />
        <el-table-column prop="status" label="状态" width="80">
          <template #default="{ row }">
            {{ row.status === 'ACTIVE' ? '正常' : '已停用' }}
          </template>
        </el-table-column>
        <el-table-column v-if="canManage" label="操作" width="120">
          <template #default="{ row }">
            <el-button
              v-if="row.status === 'ACTIVE'"
              link
              type="primary"
              @click="openCarForm(row)"
            >
              编辑
            </el-button>
            <el-button
              v-if="row.status === 'ACTIVE'"
              link
              type="danger"
              @click="onDisableCar(row)"
            >
              停用
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-drawer>

    <el-dialog v-model="carFormVisible" :title="carForm.id ? '编辑车辆' : '新增车辆'" width="440px" destroy-on-close>
      <el-form :model="carForm" label-width="80px">
        <el-form-item label="车牌号" required>
          <el-input v-model="carForm.plateNo" placeholder="如：京A12345" maxlength="10" />
        </el-form-item>
        <el-form-item label="品牌型号">
          <el-input v-model="carForm.brandModel" maxlength="32" />
        </el-form-item>
        <el-form-item label="颜色">
          <el-input v-model="carForm.color" maxlength="16" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="carForm.remark" maxlength="100" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="carFormVisible = false">取消</el-button>
        <el-button type="primary" :loading="carSaving" @click="saveCar">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/stores/user'
import {
  listHouseCatalog,
  listHouseholds,
  removeHousehold,
  saveHousehold,
  type HouseCatalogItem,
  type Household,
} from '@/api/household'
import {
  listResidentCars,
  removeResidentCar,
  saveResidentCar,
  type ResidentCar,
} from '@/api/residentCar'

const user = useUserStore()
const canManage = computed(
  () => user.hasRealToken && (user.role === 'property_admin' || user.role === 'platform_admin'),
)

const loading = ref(false)
const saving = ref(false)
const list = ref<Household[]>([])
const catalog = ref<HouseCatalogItem[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const status = ref('')
const keyword = ref('')
const visible = ref(false)

const carVisible = ref(false)
const carLoading = ref(false)
const carSaving = ref(false)
const cars = ref<ResidentCar[]>([])
const carResident = ref<Household | null>(null)
const carFormVisible = ref(false)
const carForm = reactive({
  id: undefined as number | undefined,
  plateNo: '',
  brandModel: '',
  color: '',
  remark: '',
})

const form = reactive({
  id: undefined as number | undefined,
  name: '',
  phone: '',
  houseId: undefined as number | undefined,
  identityType: 'OWNER',
  status: 'ACTIVE',
})

function tagType(s?: string) {
  if (s === 'ACTIVE') return 'success'
  if (s === 'PENDING' || s === 'MOVING_OUT') return 'warning'
  return 'info'
}

async function load() {
  loading.value = true
  try {
    const data = await listHouseholds({
      status: status.value || undefined,
      keyword: keyword.value || undefined,
      page: page.value,
      size: size.value,
    })
    list.value = data?.records || []
    total.value = Number(data?.total || 0)
  } finally {
    loading.value = false
  }
}

async function loadCatalog() {
  catalog.value = (await listHouseCatalog().catch(() => [])) || []
}

function openCreate() {
  form.id = undefined
  form.name = ''
  form.phone = ''
  form.houseId = undefined
  form.identityType = 'OWNER'
  form.status = 'ACTIVE'
  visible.value = true
  loadCatalog()
}

function openEdit(row: Household) {
  form.id = row.id
  form.name = row.name
  form.phone = row.phone
  form.houseId = row.houseId
  form.identityType = row.identityType || 'OWNER'
  form.status = row.status || 'ACTIVE'
  visible.value = true
  loadCatalog()
}

async function save() {
  if (!form.name || !form.phone || !form.houseId) {
    ElMessage.warning('请完善姓名、手机号和房屋')
    return
  }
  saving.value = true
  try {
    await saveHousehold({
      id: form.id,
      name: form.name,
      phone: form.phone,
      houseId: form.houseId,
      identityType: form.identityType,
      status: form.status,
    })
    ElMessage.success('已保存')
    visible.value = false
    await load()
  } finally {
    saving.value = false
  }
}

async function onRemove(row: Household) {
  await ElMessageBox.confirm(`确认将「${row.name}」标记为已迁出？其名下车辆将停用。`, '迁出住户', {
    type: 'warning',
  })
  await removeHousehold(row.id)
  ElMessage.success('已迁出')
  await load()
}

async function openCars(row: Household) {
  carResident.value = row
  carVisible.value = true
  await loadCars()
}

async function loadCars() {
  if (!carResident.value) return
  carLoading.value = true
  try {
    cars.value = await listResidentCars({
      houseId: carResident.value.houseId,
      residentId: carResident.value.id,
    })
  } catch {
    cars.value = []
  } finally {
    carLoading.value = false
  }
}

function openCarForm(row?: ResidentCar) {
  carForm.id = row?.id
  carForm.plateNo = row?.plateNo || ''
  carForm.brandModel = row?.brandModel || ''
  carForm.color = row?.color || ''
  carForm.remark = row?.remark || ''
  carFormVisible.value = true
}

async function saveCar() {
  if (!carResident.value?.houseId) return
  if (!carForm.plateNo.trim()) {
    ElMessage.warning('请填写车牌号')
    return
  }
  carSaving.value = true
  try {
    await saveResidentCar({
      id: carForm.id,
      houseId: carResident.value.houseId,
      residentId: carResident.value.id,
      userId: carResident.value.userId,
      ownerName: carResident.value.name,
      ownerPhone: carResident.value.phone,
      plateNo: carForm.plateNo.trim(),
      brandModel: carForm.brandModel,
      color: carForm.color,
      remark: carForm.remark,
    })
    ElMessage.success('车辆已保存')
    carFormVisible.value = false
    loadCars()
  } finally {
    carSaving.value = false
  }
}

async function onDisableCar(row: ResidentCar) {
  await ElMessageBox.confirm(`停用车牌 ${row.plateNo}？`, '确认', { type: 'warning' })
  await removeResidentCar(row.id)
  ElMessage.success('已停用')
  loadCars()
}

onMounted(() => {
  load()
  loadCatalog()
})
</script>

<style scoped>
.page-card {
  background: #fff;
  border-radius: 12px;
  padding: 20px 24px;
}
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 16px;
}
.page-header h2 {
  margin: 0;
  font-size: 20px;
}
.sub {
  margin: 6px 0 0;
  color: #64748b;
  font-size: 13px;
}
.actions {
  display: flex;
  gap: 8px;
}
.filter-bar {
  display: flex;
  gap: 10px;
  margin-bottom: 14px;
}
.pager {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
.car-actions {
  margin-bottom: 12px;
}
</style>
