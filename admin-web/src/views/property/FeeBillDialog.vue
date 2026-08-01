<template>
  <el-dialog
    :model-value="modelValue"
    :title="bill?.id ? '编辑账单' : '新增账单'"
    width="560px"
    destroy-on-close
    @update:model-value="emit('update:modelValue', $event)"
  >
    <el-form :model="form" label-width="100px">
      <el-form-item label="房屋" required>
        <el-cascader
          v-model="cascader"
          :options="cascaderOptions"
          :props="{ expandTrigger: 'hover' }"
          filterable
          clearable
          placeholder="社区 / 楼栋 / 单元 / 房号"
          style="width: 100%"
          @change="onHouseChange"
        />
      </el-form-item>
      <el-form-item label="缴费人" required>
        <el-input v-model="form.payerName" placeholder="选择房屋后自动回填，可修改" maxlength="32" />
      </el-form-item>
      <el-form-item label="手机号" required>
        <el-input v-model="form.payerPhone" placeholder="住户手机号" maxlength="11" />
      </el-form-item>
      <el-form-item v-if="payers.length > 1" label="选择住户">
        <el-select
          style="width: 100%"
          placeholder="该房屋有多位住户"
          @change="onPayerPick"
        >
          <el-option
            v-for="p in payers"
            :key="p.id"
            :label="`${p.payerName} ${p.payerPhone}`"
            :value="p.id"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="费用类型" required>
        <el-select v-model="form.feeType" style="width: 100%" @change="onFeeTypeChange">
          <el-option label="物业费" value="PROPERTY" />
          <el-option label="停车费" value="PARKING" />
          <el-option label="公摊水电费" value="SHARE" />
          <el-option label="其他" value="OTHER" />
        </el-select>
      </el-form-item>
      <el-form-item v-if="form.feeType === 'PARKING'" label="关联车牌">
        <el-select
          v-model="form.plateNo"
          filterable
          allow-create
          clearable
          default-first-option
          placeholder="选择房屋下常驻车辆或手输"
          style="width: 100%"
        >
          <el-option v-for="c in houseCars" :key="c.id" :label="c.plateNo" :value="c.plateNo" />
        </el-select>
      </el-form-item>
      <el-form-item label="应收金额" required>
        <el-input-number v-model="form.amount" :min="0.01" :precision="2" :step="10" style="width: 100%" />
      </el-form-item>
      <el-form-item label="缴费周期" required>
        <el-input v-model="form.period" placeholder="例如：2026-07" maxlength="16" />
      </el-form-item>
      <el-form-item label="截止日期">
        <el-date-picker
          v-model="form.dueDate"
          type="date"
          value-format="YYYY-MM-DD"
          placeholder="缴费截止日期"
          style="width: 100%"
        />
      </el-form-item>
      <el-form-item label="备注">
        <el-input v-model="form.remark" type="textarea" :rows="2" maxlength="200" show-word-limit />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="saving" @click="submit">保存</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import {
  createFeeBill,
  listFeePayers,
  updateFeeBill,
  type FeeBill,
  type FeeHouseOption,
  type FeePayer,
} from '@/api/fee'
import { listResidentCars, type ResidentCar } from '@/api/residentCar'

const props = defineProps<{
  modelValue: boolean
  bill: FeeBill | null
  houses: FeeHouseOption[]
}>()

const emit = defineEmits<{
  'update:modelValue': [boolean]
  saved: []
}>()

const saving = ref(false)
const cascader = ref<string[]>([])
const payers = ref<FeePayer[]>([])
const houseCars = ref<ResidentCar[]>([])

const form = reactive({
  id: undefined as number | undefined,
  houseId: undefined as number | undefined,
  userId: undefined as number | undefined,
  payerName: '',
  payerPhone: '',
  feeType: 'PROPERTY',
  amount: 274.4,
  period: '',
  dueDate: '',
  remark: '',
  plateNo: '',
})

const cascaderOptions = computed(() => {
  type Node = { value: string; label: string; children?: Node[] }
  const root: Record<string, Node> = {}
  for (const h of props.houses) {
    const c = h.communityName || '社区'
    const b = h.building || '-'
    const u = h.unit || '-'
    const r = h.roomNo || '-'
    if (!root[c]) root[c] = { value: c, label: c, children: [] }
    let building = root[c].children!.find((x) => x.value === b)
    if (!building) {
      building = { value: b, label: b, children: [] }
      root[c].children!.push(building)
    }
    let unit = building.children!.find((x) => x.value === u)
    if (!unit) {
      unit = { value: u, label: u, children: [] }
      building.children!.push(unit)
    }
    unit.children!.push({ value: String(h.id), label: r })
  }
  return Object.values(root)
})

function resetForm() {
  const now = new Date()
  const ym = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`
  form.id = undefined
  form.houseId = undefined
  form.userId = undefined
  form.payerName = ''
  form.payerPhone = ''
  form.feeType = 'PROPERTY'
  form.amount = 274.4
  form.period = ym
  form.dueDate = `${ym}-25`
  form.remark = ''
  form.plateNo = ''
  cascader.value = []
  payers.value = []
  houseCars.value = []
}

function fillFromBill(b: FeeBill) {
  form.id = b.id
  form.houseId = b.houseId
  form.userId = b.userId
  form.payerName = b.payerName || b.owner || ''
  form.payerPhone = b.payerPhone || ''
  form.feeType = b.feeType || 'PROPERTY'
  form.amount = Number(b.amount || 0)
  form.period = b.period || ''
  form.dueDate = b.dueDate ? String(b.dueDate).substring(0, 10) : ''
  form.remark = b.remark || ''
  form.plateNo = b.plateNo || ''
  const house = props.houses.find((h) => h.id === b.houseId)
  if (house) {
    cascader.value = [
      house.communityName || '社区',
      house.building || '-',
      house.unit || '-',
      String(house.id),
    ]
  } else {
    cascader.value = []
  }
}

watch(
  () => props.modelValue,
  (v) => {
    if (!v) return
    if (props.bill?.id) {
      fillFromBill(props.bill)
      if (props.bill.houseId) loadHouseCars(props.bill.houseId)
    } else resetForm()
  },
)

async function loadHouseCars(houseId: number) {
  houseCars.value = (await listResidentCars({ houseId, status: 'ACTIVE' }).catch(() => [])) || []
}

function onFeeTypeChange() {
  if (form.feeType !== 'PARKING') form.plateNo = ''
  else if (form.houseId) loadHouseCars(form.houseId)
}

async function onHouseChange(val: string[] | undefined) {
  payers.value = []
  houseCars.value = []
  form.payerName = ''
  form.payerPhone = ''
  form.userId = undefined
  form.plateNo = ''
  if (!val || val.length < 4) {
    form.houseId = undefined
    return
  }
  const houseId = Number(val[3])
  form.houseId = houseId
  try {
    payers.value = await listFeePayers(houseId)
    if (!payers.value.length) {
      ElMessage.warning('该房屋无有效住户，不允许生成账单')
      return
    }
    applyPayer(payers.value[0])
    await loadHouseCars(houseId)
  } catch {
    ElMessage.error('加载住户失败')
  }
}

function applyPayer(p: FeePayer) {
  form.payerName = p.payerName || ''
  form.payerPhone = p.payerPhone || ''
  form.userId = p.userId
}

function onPayerPick(id: number) {
  const p = payers.value.find((x) => x.id === id)
  if (p) applyPayer(p)
}

async function submit() {
  if (!form.houseId) {
    ElMessage.warning('请选择房屋')
    return
  }
  if (!form.payerName || !form.payerPhone) {
    ElMessage.warning('请填写缴费人与手机号')
    return
  }
  if (!form.amount || form.amount <= 0) {
    ElMessage.warning('应收金额必须为正数')
    return
  }
  if (!form.period) {
    ElMessage.warning('请填写缴费周期')
    return
  }
  saving.value = true
  try {
    const body = {
      houseId: form.houseId,
      payerName: form.payerName,
      payerPhone: form.payerPhone,
      userId: form.userId,
      feeType: form.feeType,
      amount: form.amount,
      period: form.period,
      dueDate: form.dueDate || undefined,
      remark: form.remark || undefined,
      plateNo: form.feeType === 'PARKING' ? form.plateNo || undefined : undefined,
    }
    if (form.id) {
      await updateFeeBill(form.id, body)
      ElMessage.success('账单已更新')
    } else {
      await createFeeBill(body)
      ElMessage.success('账单已生成（未缴）')
    }
    emit('update:modelValue', false)
    emit('saved')
  } finally {
    saving.value = false
  }
}
</script>
