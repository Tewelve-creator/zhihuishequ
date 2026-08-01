<template>
  <div class="page-card">
    <div class="page-header">
      <div>
        <h2>设备维保</h2>
        <p class="sub">设施维修/保养工单：支持巡检异常自动生成或手动新建；待派单 → 维修中 → 已完工</p>
      </div>
      <div class="actions">
        <el-button :loading="loading" @click="load">刷新</el-button>
        <el-button @click="onExport">导出Excel</el-button>
        <el-button v-if="canManage" type="primary" @click="openCreate">新建工单</el-button>
      </div>
    </div>

    <div class="filter-bar">
      <el-select v-model="status" clearable placeholder="工单状态" style="width: 130px" @change="load">
        <el-option label="待派单" value="PENDING" />
        <el-option label="维修中" value="DOING" />
        <el-option label="已完工" value="DONE" />
      </el-select>
      <el-select v-model="source" clearable placeholder="来源" style="width: 130px" @change="load">
        <el-option label="手动新建" value="MANUAL" />
        <el-option label="巡检上报" value="INSPECTION" />
      </el-select>
      <el-input
        v-model="keyword"
        clearable
        placeholder="标题/设备/位置/故障/执行人"
        style="width: 240px"
        @keyup.enter="load"
      />
      <el-button type="primary" :loading="loading" @click="load">查询</el-button>
    </div>

    <el-table v-loading="loading" :data="list" stripe empty-text="暂无维保工单">
      <el-table-column prop="title" label="工单标题" min-width="160" show-overflow-tooltip />
      <el-table-column prop="deviceName" label="设备/设施" width="120" show-overflow-tooltip>
        <template #default="{ row }">{{ row.deviceName || '—' }}</template>
      </el-table-column>
      <el-table-column prop="location" label="位置" width="120" show-overflow-tooltip />
      <el-table-column prop="sourceLabel" label="来源" width="100" />
      <el-table-column prop="faultDesc" label="故障描述" min-width="160" show-overflow-tooltip />
      <el-table-column prop="assigneeName" label="维修人" width="100">
        <template #default="{ row }">{{ row.assigneeName || '—' }}</template>
      </el-table-column>
      <el-table-column prop="statusLabel" label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="statusTag(row.status)" size="small">{{ row.statusLabel }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="创建时间" width="170" />
      <el-table-column label="操作" width="260" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDetail(row)">详情</el-button>
          <el-button
            v-if="canManage && row.status === 'PENDING'"
            link
            type="warning"
            @click="openAssign(row)"
          >派单</el-button>
          <el-button
            v-if="canManage && (row.status === 'PENDING' || row.status === 'DOING')"
            link
            type="success"
            @click="openComplete(row)"
          >完工</el-button>
          <el-button
            v-if="canManage && row.status !== 'DONE'"
            link
            type="danger"
            @click="onDelete(row)"
          >删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 新建 -->
    <el-dialog v-model="createDlg" title="新建维保工单" width="560px" destroy-on-close>
      <el-form label-width="100px">
        <el-form-item label="工单标题" required>
          <el-input v-model="form.title" maxlength="80" />
        </el-form-item>
        <el-form-item label="关联设备">
          <el-select
            v-model="form.deviceId"
            clearable
            filterable
            placeholder="从设备清单选择（可选）"
            style="width: 100%"
            @change="onPickDevice"
          >
            <el-option
              v-for="d in devices"
              :key="d.id"
              :label="`${d.name} · ${d.location || ''}`"
              :value="d.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="设备/设施">
          <el-input v-model="form.deviceName" placeholder="如：1号电梯 / 消防水泵" />
        </el-form-item>
        <el-form-item label="位置">
          <el-input v-model="form.location" />
        </el-form-item>
        <el-form-item label="故障描述">
          <el-input v-model="form.faultDesc" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item label="指定维修人">
          <el-select
            v-model="form.assigneeId"
            clearable
            filterable
            placeholder="可选：创建后直接派给此人"
            style="width: 100%"
            :loading="staffLoading"
          >
            <el-option
              v-for="s in staffOptions"
              :key="s.id"
              :label="`${s.nickname || s.phone}（${roleLabel(s.role)}）`"
              :value="s.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDlg = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitCreate">提交</el-button>
      </template>
    </el-dialog>

    <!-- 派单 -->
    <el-dialog v-model="assignDlg" title="派单指定维修人" width="460px" destroy-on-close>
      <p v-if="cur" class="assign-tip">工单「{{ cur.title }}」</p>
      <el-form label-width="90px">
        <el-form-item label="维修人员" required>
          <el-select
            v-model="assignForm.assigneeId"
            filterable
            placeholder="选择物业管理员/网格员"
            style="width: 100%"
            :loading="staffLoading"
          >
            <el-option
              v-for="s in staffOptions"
              :key="s.id"
              :label="`${s.nickname || s.phone}（${roleLabel(s.role)}）`"
              :value="s.id"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="assignDlg = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitAssign">确认派单</el-button>
      </template>
    </el-dialog>

    <!-- 完工 -->
    <el-dialog v-model="completeDlg" title="完工验收" width="560px">
      <el-form label-width="100px">
        <el-form-item label="维修内容" required>
          <el-input v-model="completeForm.repairContent" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item label="更换配件">
          <el-input v-model="completeForm.partsReplaced" placeholder="配件名称、数量等" />
        </el-form-item>
        <el-form-item label="维修照片URL">
          <el-input v-model="completeForm.repairPhotos" placeholder="多张用逗号分隔" />
        </el-form-item>
        <el-form-item label="验收图片URL">
          <el-input v-model="completeForm.acceptPhotos" placeholder="多张用逗号分隔" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="completeForm.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="completeDlg = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitComplete">确认完工</el-button>
      </template>
    </el-dialog>

    <!-- 详情 -->
    <el-drawer v-model="detailDlg" title="工单详情" size="520px">
      <el-descriptions v-if="cur" :column="1" border>
        <el-descriptions-item label="标题">{{ cur.title }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{ cur.statusLabel }}</el-descriptions-item>
        <el-descriptions-item label="来源">{{ cur.sourceLabel }}</el-descriptions-item>
        <el-descriptions-item label="设备">{{ cur.deviceName || '—' }}</el-descriptions-item>
        <el-descriptions-item label="位置">{{ cur.location || '—' }}</el-descriptions-item>
        <el-descriptions-item label="故障描述">{{ cur.faultDesc || '—' }}</el-descriptions-item>
        <el-descriptions-item label="维修人">{{ cur.assigneeName || '—' }}</el-descriptions-item>
        <el-descriptions-item label="维修内容">{{ cur.repairContent || '—' }}</el-descriptions-item>
        <el-descriptions-item label="更换配件">{{ cur.partsReplaced || '—' }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ cur.createTime }}</el-descriptions-item>
        <el-descriptions-item label="派单时间">{{ cur.assignTime || '—' }}</el-descriptions-item>
        <el-descriptions-item label="完工时间">{{ cur.finishTime || '—' }}</el-descriptions-item>
      </el-descriptions>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { exportExcel } from '@/utils/exportExcel'
import {
  assignEquipWorkOrder,
  completeEquipWorkOrder,
  deleteEquipWorkOrder,
  listEquipDevices,
  listEquipWorkOrders,
  saveEquipWorkOrder,
  type EquipDevice,
  type EquipWorkOrder,
} from '@/api/equip'
import { listStaffUsers, type StaffUser } from '@/api/users'

const user = useUserStore()
const route = useRoute()
const canManage = computed(
  () => user.hasRealToken && (user.role === 'property_admin' || user.role === 'platform_admin'),
)

const loading = ref(false)
const saving = ref(false)
const staffLoading = ref(false)
const list = ref<EquipWorkOrder[]>([])
const devices = ref<EquipDevice[]>([])
const staffOptions = ref<StaffUser[]>([])
const status = ref('')
const source = ref('')
const keyword = ref('')

const createDlg = ref(false)
const assignDlg = ref(false)
const completeDlg = ref(false)
const detailDlg = ref(false)
const cur = ref<EquipWorkOrder | null>(null)
const form = reactive({
  title: '',
  deviceId: undefined as number | undefined,
  deviceName: '',
  location: '',
  faultDesc: '',
  assigneeId: undefined as number | undefined,
  remark: '',
})
const assignForm = reactive({ assigneeId: undefined as number | undefined })
const completeForm = reactive({
  repairContent: '',
  partsReplaced: '',
  repairPhotos: '',
  acceptPhotos: '',
  remark: '',
})

function roleLabel(r?: string) {
  if (r === 'property_admin') return '物业管理员'
  if (r === 'grid_worker') return '网格员'
  return r || '员工'
}

function statusTag(s?: string) {
  if (s === 'DONE') return 'success'
  if (s === 'DOING') return 'warning'
  return 'danger'
}

async function loadStaff() {
  staffLoading.value = true
  try {
    const data = await listStaffUsers({ page: 1, size: 100, staffOnly: true })
    staffOptions.value = (data?.records || []).filter((s) => s.status === 1)
  } catch {
    staffOptions.value = []
  } finally {
    staffLoading.value = false
  }
}

function staffName(id?: number) {
  if (!id) return ''
  const s = staffOptions.value.find((x) => x.id === id)
  return s?.nickname || s?.phone || ''
}

async function load() {
  loading.value = true
  try {
    list.value =
      (await listEquipWorkOrders({
        status: status.value || undefined,
        source: source.value || undefined,
        keyword: keyword.value || undefined,
      })) || []
  } finally {
    loading.value = false
  }
}

function onPickDevice(id?: number) {
  const d = devices.value.find((x) => x.id === id)
  if (!d) return
  form.deviceName = d.name
  form.location = d.location || ''
  if (!form.title.trim()) form.title = `${d.name}维保`
}

async function openCreate(prefill?: Partial<typeof form>) {
  Object.assign(form, {
    title: '',
    deviceId: undefined,
    deviceName: '',
    location: '',
    faultDesc: '',
    assigneeId: undefined,
    remark: '',
    ...prefill,
  })
  createDlg.value = true
  await loadStaff()
}

async function submitCreate() {
  if (!form.title.trim()) {
    ElMessage.warning('请填写工单标题')
    return
  }
  saving.value = true
  try {
    const { assigneeId, ...payload } = form
    const created = await saveEquipWorkOrder({ ...payload })
    if (assigneeId && created?.id) {
      await assignEquipWorkOrder(created.id, {
        assigneeId,
        assigneeName: staffName(assigneeId),
      })
      ElMessage.success('工单已创建并派单')
    } else {
      ElMessage.success('工单已创建（待派单）')
    }
    createDlg.value = false
    load()
  } finally {
    saving.value = false
  }
}

async function openAssign(row: EquipWorkOrder) {
  cur.value = row
  assignForm.assigneeId = row.assigneeId
  assignDlg.value = true
  await loadStaff()
}

async function submitAssign() {
  if (!cur.value || !assignForm.assigneeId) {
    ElMessage.warning('请选择维修人员')
    return
  }
  saving.value = true
  try {
    await assignEquipWorkOrder(cur.value.id, {
      assigneeId: assignForm.assigneeId,
      assigneeName: staffName(assignForm.assigneeId),
    })
    ElMessage.success('已派单，状态变为维修中')
    assignDlg.value = false
    load()
  } finally {
    saving.value = false
  }
}

function openComplete(row: EquipWorkOrder) {
  cur.value = row
  Object.assign(completeForm, {
    repairContent: '',
    partsReplaced: '',
    repairPhotos: '',
    acceptPhotos: '',
    remark: '',
  })
  completeDlg.value = true
}

async function submitComplete() {
  if (!cur.value || !completeForm.repairContent.trim()) {
    ElMessage.warning('请填写维修内容')
    return
  }
  saving.value = true
  try {
    await completeEquipWorkOrder(cur.value.id, { ...completeForm })
    ElMessage.success('已完工验收')
    completeDlg.value = false
    load()
  } finally {
    saving.value = false
  }
}

function openDetail(row: EquipWorkOrder) {
  cur.value = row
  detailDlg.value = true
}

async function onDelete(row: EquipWorkOrder) {
  await ElMessageBox.confirm(`确认删除工单「${row.title}」？`, '提示')
  await deleteEquipWorkOrder(row.id)
  ElMessage.success('已删除')
  load()
}

function onExport() {
  exportExcel(
    (list.value || []).map((r) => ({
      工单标题: r.title,
      设备设施: r.deviceName || '',
      位置: r.location || '',
      来源: r.sourceLabel || '',
      故障描述: r.faultDesc || '',
      维修人: r.assigneeName || '',
      状态: r.statusLabel || '',
      创建时间: r.createTime || '',
      完工时间: r.finishTime || '',
    })),
    '设备维保工单.xlsx',
  )
}

onMounted(async () => {
  devices.value = (await listEquipDevices().catch(() => [])) || []
  await load()
  const q = route.query
  if (q.deviceName || q.deviceId) {
    openCreate({
      deviceId: q.deviceId ? Number(q.deviceId) : undefined,
      deviceName: q.deviceName ? String(q.deviceName) : '',
      location: q.location ? String(q.location) : '',
      title: q.deviceName ? `${q.deviceName}维保` : '',
    })
  }
})
</script>

<style scoped>
.page-header { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 12px; }
.page-header h2 { margin: 0 0 4px; }
.sub { margin: 0; color: #64748b; font-size: 13px; }
.actions { display: flex; gap: 8px; }
.filter-bar { display: flex; gap: 8px; margin-bottom: 12px; flex-wrap: wrap; }
.assign-tip { margin: 0 0 12px; color: #64748b; font-size: 13px; }
</style>
