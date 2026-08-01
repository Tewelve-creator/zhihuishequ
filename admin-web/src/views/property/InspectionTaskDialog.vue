<template>
  <el-dialog
    :model-value="modelValue"
    :title="task?.id ? '编辑巡检任务' : '新增巡检任务'"
    width="640px"
    destroy-on-close
    @update:model-value="emit('update:modelValue', $event)"
  >
    <el-form :model="form" label-width="100px">
      <el-form-item label="任务名称" required>
        <el-input v-model="form.title" maxlength="64" placeholder="如：今日消防巡检" />
      </el-form-item>
      <el-form-item label="执行人" required>
        <el-select
          v-model="form.assigneeId"
          filterable
          style="width: 100%"
          placeholder="选择物业管理员/网格员"
          @change="onAssigneeChange"
        >
          <el-option
            v-for="s in staff"
            :key="s.id"
            :label="`${s.nickname || s.phone}（${roleLabel(s.role)}）`"
            :value="s.id"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="巡检点位" required>
        <el-select
          v-model="form.pointIds"
          multiple
          filterable
          style="width: 100%"
          placeholder="选择多个巡检点位"
        >
          <el-option v-for="p in points" :key="p.id" :label="`${p.name} · ${p.locationDesc || ''}`" :value="p.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="巡检周期" required>
        <el-radio-group v-model="form.cycleType">
          <el-radio-button value="ONCE">单次</el-radio-button>
          <el-radio-button value="DAILY">每日</el-radio-button>
          <el-radio-button value="WEEKLY">每周</el-radio-button>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="开始时间">
        <el-date-picker
          v-model="form.startTime"
          type="datetime"
          value-format="YYYY-MM-DD HH:mm:ss"
          style="width: 100%"
        />
      </el-form-item>
      <el-form-item label="截止时间">
        <el-date-picker
          v-model="form.endTime"
          type="datetime"
          value-format="YYYY-MM-DD HH:mm:ss"
          style="width: 100%"
        />
      </el-form-item>
      <el-form-item label="巡检要求">
        <el-input v-model="form.requirement" type="textarea" :rows="2" maxlength="300" />
      </el-form-item>
      <el-form-item label="备注">
        <el-input v-model="form.remark" type="textarea" :rows="2" maxlength="200" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="saving" @click="submit">保存</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { listStaffUsers, type StaffUser } from '@/api/users'
import { listInspPoints, saveInspTask, type InspPoint, type InspTask } from '@/api/inspection'

const props = defineProps<{
  modelValue: boolean
  task: InspTask | null
}>()

const emit = defineEmits<{
  'update:modelValue': [boolean]
  saved: []
}>()

const saving = ref(false)
const staff = ref<StaffUser[]>([])
const points = ref<InspPoint[]>([])
const form = reactive({
  id: undefined as number | undefined,
  title: '',
  assigneeId: undefined as number | undefined,
  assigneeName: '',
  cycleType: 'ONCE',
  startTime: '',
  endTime: '',
  requirement: '',
  remark: '',
  pointIds: [] as number[],
})

function roleLabel(r?: string) {
  if (r === 'property_admin') return '物业管理员'
  if (r === 'grid_worker') return '网格员'
  return r || '员工'
}

function reset() {
  const now = new Date()
  const pad = (n: number) => String(n).padStart(2, '0')
  const fmt = (d: Date) =>
    `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:00`
  const end = new Date(now.getTime() + 8 * 3600 * 1000)
  Object.assign(form, {
    id: undefined,
    title: '',
    assigneeId: undefined,
    assigneeName: '',
    cycleType: 'ONCE',
    startTime: fmt(now),
    endTime: fmt(end),
    requirement: '',
    remark: '',
    pointIds: [],
  })
}

function fill(t: InspTask) {
  form.id = t.id
  form.title = t.title || t.task || ''
  form.assigneeId = t.assigneeId
  form.assigneeName = t.assigneeName || t.owner || ''
  form.cycleType = t.cycleType || 'ONCE'
  form.startTime = t.startTime ? String(t.startTime).replace('T', ' ').substring(0, 19) : ''
  form.endTime = t.endTime ? String(t.endTime).replace('T', ' ').substring(0, 19) : ''
  form.requirement = t.requirement || ''
  form.remark = t.remark || ''
  form.pointIds = [...(t.pointIds || [])]
}

async function loadMeta() {
  const [s, p] = await Promise.all([
    listStaffUsers({ page: 1, size: 100, staffOnly: true }).catch(() => ({ records: [] })),
    listInspPoints({ status: 1 }).catch(() => []),
  ])
  staff.value = s.records || []
  points.value = p || []
}

watch(
  () => props.modelValue,
  async (v) => {
    if (!v) return
    await loadMeta()
    if (props.task?.id) fill(props.task)
    else reset()
  },
)

function onAssigneeChange(id: number) {
  const s = staff.value.find((x) => x.id === id)
  form.assigneeName = s?.nickname || s?.phone || ''
}

async function submit() {
  if (!form.title.trim()) return ElMessage.warning('请填写任务名称')
  if (!form.assigneeId) return ElMessage.warning('请选择执行人')
  if (!form.pointIds.length) return ElMessage.warning('请选择巡检点位')
  saving.value = true
  try {
    await saveInspTask({
      id: form.id,
      title: form.title.trim(),
      assigneeId: form.assigneeId,
      assigneeName: form.assigneeName,
      cycleType: form.cycleType,
      startTime: form.startTime || undefined,
      endTime: form.endTime || undefined,
      requirement: form.requirement || undefined,
      remark: form.remark || undefined,
      pointIds: form.pointIds,
    })
    ElMessage.success(form.id ? '任务已更新' : '任务已创建（未开始）')
    emit('update:modelValue', false)
    emit('saved')
  } finally {
    saving.value = false
  }
}
</script>
