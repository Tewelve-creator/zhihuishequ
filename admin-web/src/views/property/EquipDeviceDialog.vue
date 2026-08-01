<template>
  <el-dialog
    :model-value="modelValue"
    :title="device?.id ? '编辑设备' : '新增设备'"
    width="560px"
    destroy-on-close
    @update:model-value="emit('update:modelValue', $event)"
  >
    <el-form :model="form" label-width="110px">
      <el-form-item label="设备名称" required>
        <el-input v-model="form.name" maxlength="64" placeholder="如：1号电梯" />
      </el-form-item>
      <el-form-item label="设备位置" required>
        <el-input v-model="form.location" maxlength="120" placeholder="如：1栋电梯机房" />
      </el-form-item>
      <el-form-item label="设备类型" required>
        <el-select v-model="form.deviceType" filterable allow-create style="width: 100%">
          <el-option label="电梯" value="电梯" />
          <el-option label="消防水泵" value="消防水泵" />
          <el-option label="消防栓箱" value="消防栓箱" />
          <el-option label="监控" value="监控" />
          <el-option label="门禁" value="门禁" />
          <el-option label="道闸" value="道闸" />
          <el-option label="充电桩" value="充电桩" />
          <el-option label="其他" value="其他" />
        </el-select>
      </el-form-item>
      <el-form-item label="数量" required>
        <el-input-number v-model="form.quantity" :min="1" :max="9999" style="width: 100%" />
      </el-form-item>
      <el-form-item label="维保周期" required>
        <el-radio-group v-model="form.cycleType" @change="recalcNext">
          <el-radio-button value="MONTH">月度</el-radio-button>
          <el-radio-button value="QUARTER">季度</el-radio-button>
          <el-radio-button value="HALF_YEAR">半年</el-radio-button>
          <el-radio-button value="YEAR">年度</el-radio-button>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="维保服务商">
        <el-input v-model="form.vendorName" maxlength="64" />
      </el-form-item>
      <el-form-item label="联系人">
        <el-input v-model="form.contactName" maxlength="32" />
      </el-form-item>
      <el-form-item label="联系电话">
        <el-input v-model="form.contactPhone" maxlength="20" />
      </el-form-item>
      <el-form-item label="上次维保日期">
        <el-date-picker
          v-model="form.lastMaintDate"
          type="date"
          value-format="YYYY-MM-DD"
          style="width: 100%"
          @change="recalcNext"
        />
      </el-form-item>
      <el-form-item label="下次维保日期">
        <el-date-picker
          v-model="form.nextMaintDate"
          type="date"
          value-format="YYYY-MM-DD"
          style="width: 100%"
          placeholder="根据上次日期+周期自动推算，可改"
        />
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
import { saveEquipDevice, type EquipDevice } from '@/api/equip'

const props = defineProps<{
  modelValue: boolean
  device: EquipDevice | null
}>()

const emit = defineEmits<{
  'update:modelValue': [boolean]
  saved: []
}>()

const saving = ref(false)
const form = reactive({
  id: undefined as number | undefined,
  name: '',
  location: '',
  deviceType: '电梯',
  quantity: 1,
  cycleType: 'QUARTER',
  vendorName: '',
  contactName: '',
  contactPhone: '',
  lastMaintDate: '',
  nextMaintDate: '',
  remark: '',
})

function addMonths(ymd: string, months: number) {
  const d = new Date(ymd.replace(/-/g, '/'))
  d.setMonth(d.getMonth() + months)
  const p = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`
}

function recalcNext() {
  if (!form.lastMaintDate) return
  const m =
    form.cycleType === 'MONTH' ? 1 : form.cycleType === 'HALF_YEAR' ? 6 : form.cycleType === 'YEAR' ? 12 : 3
  form.nextMaintDate = addMonths(form.lastMaintDate, m)
}

function reset() {
  Object.assign(form, {
    id: undefined,
    name: '',
    location: '',
    deviceType: '电梯',
    quantity: 1,
    cycleType: 'QUARTER',
    vendorName: '',
    contactName: '',
    contactPhone: '',
    lastMaintDate: '',
    nextMaintDate: '',
    remark: '',
  })
}

function fill(d: EquipDevice) {
  form.id = d.id
  form.name = d.name || ''
  form.location = d.location || ''
  form.deviceType = d.deviceType || '电梯'
  form.quantity = Number(d.quantity || 1)
  form.cycleType = d.cycleType || 'QUARTER'
  form.vendorName = d.vendorName || ''
  form.contactName = d.contactName || ''
  form.contactPhone = d.contactPhone || ''
  form.lastMaintDate = d.lastMaintDate ? String(d.lastMaintDate).substring(0, 10) : ''
  form.nextMaintDate = d.nextMaintDate ? String(d.nextMaintDate).substring(0, 10) : ''
  form.remark = d.remark || ''
}

watch(
  () => props.modelValue,
  (v) => {
    if (!v) return
    if (props.device?.id) fill(props.device)
    else reset()
  },
)

async function submit() {
  if (!form.name.trim() || !form.location.trim() || !form.deviceType) {
    ElMessage.warning('请填写设备名称、位置、类型')
    return
  }
  saving.value = true
  try {
    await saveEquipDevice({ ...form })
    ElMessage.success('已保存')
    emit('update:modelValue', false)
    emit('saved')
  } finally {
    saving.value = false
  }
}
</script>
