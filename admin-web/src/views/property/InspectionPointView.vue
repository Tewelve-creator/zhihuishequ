<template>
  <div class="page-card">
    <div class="page-header">
      <div>
        <h2>巡检点位</h2>
        <p class="sub">录入小区巡查点位，配置位置与类型；任务须关联启用点位</p>
      </div>
      <div class="actions">
        <el-button :loading="loading" @click="load">刷新</el-button>
        <el-button v-if="canManage" type="primary" @click="openCreate">新增点位</el-button>
      </div>
    </div>

    <div class="filter-bar">
      <el-input v-model="keyword" clearable placeholder="名称/位置/类型" style="width: 220px" @keyup.enter="load" />
      <el-select v-model="status" clearable placeholder="状态" style="width: 120px" @change="load">
        <el-option label="启用" :value="1" />
        <el-option label="停用" :value="0" />
      </el-select>
      <el-button type="primary" :loading="loading" @click="load">查询</el-button>
    </div>

    <el-table v-loading="loading" :data="list" stripe empty-text="暂无点位">
      <el-table-column prop="name" label="点位名称" min-width="130" />
      <el-table-column prop="pointTypeLabel" label="点位类型" width="100">
        <template #default="{ row }">{{ row.pointTypeLabel || '其他' }}</template>
      </el-table-column>
      <el-table-column prop="locationDesc" label="位置描述" min-width="150" show-overflow-tooltip />
      <el-table-column prop="address" label="地址" min-width="130" show-overflow-tooltip />
      <el-table-column label="经纬度" width="180">
        <template #default="{ row }">
          <span v-if="row.latitude != null">{{ row.latitude }}, {{ row.longitude }}</span>
          <span v-else class="muted">未设置</span>
        </template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
            {{ row.status === 1 ? '启用' : '停用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column v-if="canManage" label="操作" width="140" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button link type="danger" @click="onDelete(row)">停用</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="visible" :title="form.id ? '编辑点位' : '新增点位'" width="560px" destroy-on-close>
      <el-form :model="form" label-width="100px">
        <el-form-item label="点位名称" required>
          <el-input v-model="form.name" maxlength="64" />
        </el-form-item>
        <el-form-item label="点位类型" required>
          <el-select v-model="form.pointType" style="width: 100%">
            <el-option v-for="t in POINT_TYPES" :key="t.value" :label="t.label" :value="t.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="位置描述">
          <el-input v-model="form.locationDesc" maxlength="120" />
        </el-form-item>
        <el-form-item label="地址">
          <el-input v-model="form.address" maxlength="120" />
        </el-form-item>
        <el-form-item label="纬度">
          <el-input-number v-model="form.latitude" :precision="7" :step="0.0001" style="width: 100%" />
        </el-form-item>
        <el-form-item label="经度">
          <el-input-number v-model="form.longitude" :precision="7" :step="0.0001" style="width: 100%" />
        </el-form-item>
        <el-form-item label="启用状态">
          <el-switch v-model="form.status" :active-value="1" :inactive-value="0" />
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
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/stores/user'
import {
  deleteInspPoint,
  listInspPoints,
  saveInspPoint,
  type InspPoint,
} from '@/api/inspection'

const POINT_TYPES = [
  { value: 'FIRE', label: '消防' },
  { value: 'ELEVATOR', label: '电梯' },
  { value: 'PARKING', label: '车库' },
  { value: 'GARDEN', label: '绿化' },
  { value: 'GATE', label: '门禁' },
  { value: 'PUMP', label: '水泵' },
  { value: 'OTHER', label: '其他' },
]

const user = useUserStore()
const canManage = computed(
  () => user.hasRealToken && (user.role === 'property_admin' || user.role === 'platform_admin'),
)

const loading = ref(false)
const saving = ref(false)
const list = ref<InspPoint[]>([])
const keyword = ref('')
const status = ref<number | undefined>(1)
const visible = ref(false)
const form = reactive({
  id: undefined as number | undefined,
  name: '',
  pointType: 'OTHER',
  locationDesc: '',
  address: '',
  latitude: undefined as number | undefined,
  longitude: undefined as number | undefined,
  status: 1,
  remark: '',
})

async function load() {
  loading.value = true
  try {
    list.value = (await listInspPoints({ keyword: keyword.value || undefined, status: status.value })) || []
  } finally {
    loading.value = false
  }
}

function openCreate() {
  Object.assign(form, {
    id: undefined,
    name: '',
    pointType: 'OTHER',
    locationDesc: '',
    address: '',
    latitude: undefined,
    longitude: undefined,
    status: 1,
    remark: '',
  })
  visible.value = true
}

function openEdit(row: InspPoint) {
  Object.assign(form, {
    id: row.id,
    name: row.name,
    pointType: row.pointType || 'OTHER',
    locationDesc: row.locationDesc || '',
    address: row.address || '',
    latitude: row.latitude,
    longitude: row.longitude,
    status: row.status ?? 1,
    remark: row.remark || '',
  })
  visible.value = true
}

async function save() {
  if (!form.name.trim()) {
    ElMessage.warning('请填写点位名称')
    return
  }
  saving.value = true
  try {
    await saveInspPoint({ ...form })
    ElMessage.success('已保存')
    visible.value = false
    load()
  } finally {
    saving.value = false
  }
}

async function onDelete(row: InspPoint) {
  await ElMessageBox.confirm(`确认停用点位「${row.name}」？停用后不可再被新任务选用。`, '提示')
  await deleteInspPoint(row.id)
  ElMessage.success('已停用')
  load()
}

onMounted(load)
</script>

<style scoped>
.page-header { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 12px; }
.page-header h2 { margin: 0 0 4px; }
.sub { margin: 0; color: #64748b; font-size: 13px; }
.actions { display: flex; gap: 8px; }
.filter-bar { display: flex; gap: 8px; margin-bottom: 12px; flex-wrap: wrap; }
.muted { color: #94a3b8; }
</style>
