<template>
  <div class="page-card">
    <div class="page-header">
      <div>
        <h2>报修分类管理</h2>
        <p class="sub">维护报修 / 报事类目，供居民提交与 AI 识别匹配；按小区隔离</p>
      </div>
      <el-button type="primary" @click="open()">新增类目</el-button>
    </div>

    <div class="filter-bar">
      <el-select v-model="orderType" clearable placeholder="类型" style="width: 140px" @change="load">
        <el-option label="报修" value="repair" />
        <el-option label="报事" value="report" />
      </el-select>
      <el-button :loading="loading" @click="load">刷新</el-button>
    </div>

    <el-table v-loading="loading" :data="list" stripe empty-text="暂无类目">
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="name" label="类目名称" min-width="140" />
      <el-table-column label="类型" width="90">
        <template #default="{ row }">
          <el-tag :type="row.orderType === 'report' ? 'warning' : 'primary'" size="small">
            {{ row.orderType === 'report' ? '报事' : '报修' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="urgencyDefault" label="默认紧急" width="100" />
      <el-table-column prop="sortNo" label="排序" width="80" />
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
            {{ row.status === 1 ? '启用' : '停用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="remark" label="备注" min-width="120" show-overflow-tooltip />
      <el-table-column label="操作" width="140" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="open(row)">编辑</el-button>
          <el-button link type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="visible" :title="form.id ? '编辑类目' : '新增类目'" width="480px" destroy-on-close>
      <el-form label-width="100px">
        <el-form-item label="名称" required>
          <el-input v-model="form.name" maxlength="32" />
        </el-form-item>
        <el-form-item label="类型" required>
          <el-radio-group v-model="form.orderType">
            <el-radio value="repair">报修</el-radio>
            <el-radio value="report">报事</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="默认紧急">
          <el-select v-model="form.urgencyDefault" style="width: 100%">
            <el-option label="高" value="高" />
            <el-option label="中" value="中" />
            <el-option label="低" value="低" />
          </el-select>
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sortNo" :min="0" />
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="enabled" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" />
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
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  deleteRepairCategory,
  listRepairCategories,
  saveRepairCategory,
  type RepairCategory,
} from '@/api/repair'

const loading = ref(false)
const saving = ref(false)
const list = ref<RepairCategory[]>([])
const orderType = ref('')
const visible = ref(false)
const enabled = ref(true)
const form = reactive<Partial<RepairCategory>>({
  name: '',
  orderType: 'repair',
  urgencyDefault: '中',
  sortNo: 0,
  remark: '',
})

async function load() {
  loading.value = true
  try {
    list.value = (await listRepairCategories({ orderType: orderType.value || undefined })) || []
  } catch {
    list.value = []
  } finally {
    loading.value = false
  }
}

function open(row?: RepairCategory) {
  Object.assign(form, {
    id: undefined,
    name: '',
    orderType: 'repair',
    urgencyDefault: '中',
    sortNo: 0,
    remark: '',
  })
  if (row) Object.assign(form, row)
  enabled.value = row ? row.status !== 0 : true
  visible.value = true
}

async function save() {
  if (!form.name?.trim()) {
    ElMessage.warning('请填写名称')
    return
  }
  saving.value = true
  try {
    await saveRepairCategory({ ...form, status: enabled.value ? 1 : 0 })
    ElMessage.success('已保存')
    visible.value = false
    await load()
  } finally {
    saving.value = false
  }
}

async function remove(row: RepairCategory) {
  try {
    await ElMessageBox.confirm(`删除类目「${row.name}」？`, '确认', { type: 'warning' })
  } catch {
    return
  }
  await deleteRepairCategory(row.id!)
  ElMessage.success('已删除')
  await load()
}

onMounted(load)
</script>

<style scoped>
.page-header { display: flex; justify-content: space-between; margin-bottom: 12px; }
.page-header h2 { margin: 0; font-size: 18px; }
.sub { margin: 4px 0 0; color: #64748b; font-size: 13px; }
.filter-bar { display: flex; gap: 8px; margin-bottom: 12px; }
</style>
