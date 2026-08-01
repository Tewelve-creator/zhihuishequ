<template>
  <div class="page-card">
    <div class="page-header">
      <div>
        <h2>业主车辆</h2>
        <p class="sub">登记业主常驻车辆，绑定房屋与车位；长期欠费可限制进出</p>
      </div>
      <div class="actions">
        <el-button :loading="loading" @click="load">刷新</el-button>
        <el-button @click="onExport">导出Excel</el-button>
        <el-button v-if="canManage" type="primary" @click="openCreate">新增车辆</el-button>
      </div>
    </div>

    <el-alert
      v-if="!canManage"
      type="info"
      :closable="false"
      show-icon
      title="网格员仅可查看车辆，不可新增/编辑/限行"
      style="margin-bottom: 12px"
    />

    <div class="filter-bar">
      <el-select v-model="status" clearable placeholder="状态" style="width: 120px" @change="load">
        <el-option label="正常" value="ACTIVE" />
        <el-option label="已停用" value="DISABLED" />
      </el-select>
      <el-input
        v-model="keyword"
        clearable
        placeholder="车牌/车主/房屋/车位"
        style="width: 220px"
        @keyup.enter="load"
      />
      <el-button type="primary" :loading="loading" @click="load">查询</el-button>
    </div>

    <el-table v-loading="loading" :data="list" stripe empty-text="暂无车辆">
      <el-table-column prop="plateNo" label="车牌号" width="120" />
      <el-table-column prop="houseLabel" label="绑定房屋" min-width="140" show-overflow-tooltip />
      <el-table-column prop="spaceNo" label="车位" width="100">
        <template #default="{ row }">{{ row.spaceNo || '—' }}</template>
      </el-table-column>
      <el-table-column prop="ownerName" label="车主" width="100" />
      <el-table-column prop="ownerPhone" label="手机号" width="120" />
      <el-table-column prop="brandModel" label="车型" width="110" />
      <el-table-column label="通行" width="100">
        <template #default="{ row }">
          <el-tag v-if="row.accessRestricted === 1" type="danger" size="small">已限行</el-tag>
          <el-tag v-else type="success" size="small">正常</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'" size="small">
            {{ row.status === 'ACTIVE' ? '正常' : '已停用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column v-if="canManage" label="操作" width="220" fixed="right">
        <template #default="{ row }">
          <el-button v-if="row.status === 'ACTIVE'" link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button
            v-if="row.status === 'ACTIVE'"
            link
            :type="row.accessRestricted === 1 ? 'success' : 'warning'"
            @click="onRestrict(row)"
          >{{ row.accessRestricted === 1 ? '恢复通行' : '限制进出' }}</el-button>
          <el-button v-if="row.status === 'ACTIVE'" link type="danger" @click="onDisable(row)">停用</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="visible" :title="form.id ? '编辑车辆' : '新增车辆'" width="520px" destroy-on-close>
      <el-form :model="form" label-width="96px">
        <el-form-item label="绑定房屋" required>
          <el-select v-model="form.houseId" filterable style="width: 100%" placeholder="选择房屋">
            <el-option
              v-for="h in catalog"
              :key="h.id"
              :label="h.label || `${h.building}${h.unit}${h.roomNo}`"
              :value="h.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="车牌号" required>
          <el-input v-model="form.plateNo" placeholder="如：京A12345" maxlength="10" />
        </el-form-item>
        <el-form-item label="绑定车位">
          <el-select v-model="form.spaceId" clearable filterable style="width: 100%" placeholder="可选车位" @change="onSpaceChange">
            <el-option
              v-for="s in spaces"
              :key="s.id"
              :label="`${s.spaceNo}（${s.spaceType === 'OWNED' ? '产权' : s.spaceType === 'RENT' ? '租赁' : '公共'}）`"
              :value="s.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="车主姓名">
          <el-input v-model="form.ownerName" maxlength="32" />
        </el-form-item>
        <el-form-item label="手机号">
          <el-input v-model="form.ownerPhone" maxlength="11" />
        </el-form-item>
        <el-form-item label="品牌型号">
          <el-input v-model="form.brandModel" maxlength="32" />
        </el-form-item>
        <el-form-item label="颜色">
          <el-input v-model="form.color" maxlength="16" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" maxlength="100" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { exportExcel } from '@/utils/exportExcel'
import { listHouseCatalog, type HouseCatalogItem } from '@/api/household'
import { listParkSpaces, type ParkSpace } from '@/api/parking'
import {
  listResidentCars,
  removeResidentCar,
  saveResidentCar,
  setResidentCarAccessRestrict,
  type ResidentCar,
} from '@/api/residentCar'

const user = useUserStore()
const canManage = computed(
  () => user.hasRealToken && (user.role === 'property_admin' || user.role === 'platform_admin'),
)

const loading = ref(false)
const saving = ref(false)
const list = ref<ResidentCar[]>([])
const catalog = ref<HouseCatalogItem[]>([])
const spaces = ref<ParkSpace[]>([])
const status = ref('ACTIVE')
const keyword = ref('')
const visible = ref(false)
const form = reactive({
  id: undefined as number | undefined,
  houseId: undefined as number | undefined,
  plateNo: '',
  spaceId: undefined as number | undefined,
  spaceNo: '',
  ownerName: '',
  ownerPhone: '',
  brandModel: '',
  color: '',
  remark: '',
})

async function load() {
  loading.value = true
  try {
    list.value =
      (await listResidentCars({
        status: status.value || undefined,
        keyword: keyword.value || undefined,
      })) || []
  } finally {
    loading.value = false
  }
}

async function loadMeta() {
  try {
    catalog.value = (await listHouseCatalog()) || []
  } catch {
    catalog.value = []
  }
  try {
    spaces.value = (await listParkSpaces({})) || []
  } catch {
    spaces.value = []
  }
}

function onSpaceChange(id?: number) {
  const s = spaces.value.find((x) => x.id === id)
  form.spaceNo = s?.spaceNo || ''
}

function openCreate() {
  Object.assign(form, {
    id: undefined,
    houseId: undefined,
    plateNo: '',
    spaceId: undefined,
    spaceNo: '',
    ownerName: '',
    ownerPhone: '',
    brandModel: '',
    color: '',
    remark: '',
  })
  visible.value = true
}

function openEdit(row: ResidentCar) {
  Object.assign(form, {
    id: row.id,
    houseId: row.houseId,
    plateNo: row.plateNo,
    spaceId: row.spaceId,
    spaceNo: row.spaceNo || '',
    ownerName: row.ownerName || '',
    ownerPhone: row.ownerPhone || '',
    brandModel: row.brandModel || '',
    color: row.color || '',
    remark: row.remark || '',
  })
  visible.value = true
}

async function save() {
  if (!form.houseId || !form.plateNo.trim()) {
    ElMessage.warning('请填写房屋与车牌')
    return
  }
  saving.value = true
  try {
    await saveResidentCar({
      ...form,
      houseId: form.houseId,
      plateNo: form.plateNo.trim(),
    })
    ElMessage.success('已保存')
    visible.value = false
    load()
  } finally {
    saving.value = false
  }
}

async function onDisable(row: ResidentCar) {
  await ElMessageBox.confirm(`确认停用车辆「${row.plateNo}」？`, '提示')
  await removeResidentCar(row.id)
  ElMessage.success('已停用')
  load()
}

async function onRestrict(row: ResidentCar) {
  const next = row.accessRestricted !== 1
  await ElMessageBox.confirm(
    next
      ? `确认限制「${row.plateNo}」进出闸？欠费结清后可恢复。`
      : `确认恢复「${row.plateNo}」通行权限？`,
    next ? '限制进出' : '恢复通行',
  )
  await setResidentCarAccessRestrict(row.id, next)
  ElMessage.success(next ? '已限制进出' : '已恢复通行')
  load()
}

function onExport() {
  exportExcel(
    (list.value || []).map((r) => ({
      车牌号: r.plateNo,
      房屋: r.houseLabel || '',
      车位: r.spaceNo || '',
      车主: r.ownerName || '',
      手机: r.ownerPhone || '',
      车型: r.brandModel || '',
      通行: r.accessRestricted === 1 ? '已限行' : '正常',
      状态: r.status === 'ACTIVE' ? '正常' : '已停用',
    })),
    '业主车辆.xlsx',
  )
}

onMounted(async () => {
  await loadMeta()
  await load()
})
</script>

<style scoped>
.page-header { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 12px; }
.page-header h2 { margin: 0 0 4px; }
.sub { margin: 0; color: #64748b; font-size: 13px; }
.actions { display: flex; gap: 8px; }
.filter-bar { display: flex; gap: 8px; margin-bottom: 12px; flex-wrap: wrap; }
</style>
