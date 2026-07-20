<template>
  <div class="page-card">
    <div class="page-header">
      <h2>SaaS 租户管理</h2>
      <div>
        <el-button type="primary" @click="openDialog()">开通租户</el-button>
        <el-button @click="onExport">导出 Excel</el-button>
      </div>
    </div>
    <div class="filter-bar">
      <el-input v-model="keyword" placeholder="租户名称" clearable style="width: 200px" />
      <el-select v-model="status" placeholder="状态" clearable style="width: 140px">
        <el-option label="正常" value="正常" />
        <el-option label="已到期" value="已到期" />
        <el-option label="停用" value="停用" />
      </el-select>
      <el-button type="primary">查询</el-button>
    </div>
    <el-table :data="filtered" stripe>
      <el-table-column prop="name" label="租户名称" />
      <el-table-column prop="contact" label="联系人" width="100" />
      <el-table-column prop="phone" label="电话" width="130" />
      <el-table-column prop="packageName" label="套餐" width="120" />
      <el-table-column prop="expireAt" label="到期日" width="120" />
      <el-table-column prop="status" label="状态" width="100" />
      <el-table-column label="操作" width="140">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDialog(row)">编辑</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="visible" :title="editing ? '编辑租户' : '开通租户'" width="480px">
      <el-form :model="form" label-width="80px">
        <el-form-item label="名称"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="联系人"><el-input v-model="form.contact" /></el-form-item>
        <el-form-item label="电话"><el-input v-model="form.phone" /></el-form-item>
        <el-form-item label="套餐">
          <el-select v-model="form.packageName" style="width: 100%">
            <el-option label="基础版" value="基础版" />
            <el-option label="专业版" value="专业版" />
            <el-option label="旗舰版" value="旗舰版" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { exportExcel } from '@/utils/exportExcel'

interface TenantRow {
  id: number
  name: string
  contact: string
  phone: string
  packageName: string
  expireAt: string
  status: string
}

const keyword = ref('')
const status = ref('')
const list = ref<TenantRow[]>([
  { id: 1, name: '阳光花园物业', contact: '陈总', phone: '13811112222', packageName: '专业版', expireAt: '2027-01-01', status: '正常' },
  { id: 2, name: '湖畔名邸', contact: '刘经理', phone: '13922223333', packageName: '基础版', expireAt: '2026-08-01', status: '正常' },
  { id: 3, name: '星河湾社区', contact: '周主任', phone: '13733334444', packageName: '旗舰版', expireAt: '2026-06-30', status: '已到期' },
])

const filtered = computed(() =>
  list.value.filter((t) => {
    const hitKw = !keyword.value || t.name.includes(keyword.value)
    const hitStatus = !status.value || t.status === status.value
    return hitKw && hitStatus
  }),
)

const visible = ref(false)
const editing = ref<TenantRow | null>(null)
const form = reactive({ name: '', contact: '', phone: '', packageName: '基础版' })

function openDialog(row?: TenantRow) {
  editing.value = row || null
  form.name = row?.name || ''
  form.contact = row?.contact || ''
  form.phone = row?.phone || ''
  form.packageName = row?.packageName || '基础版'
  visible.value = true
}

function save() {
  if (editing.value) {
    Object.assign(editing.value, { ...form })
  } else {
    list.value.push({
      id: Date.now(),
      ...form,
      expireAt: '2027-07-17',
      status: '正常',
    })
  }
  visible.value = false
  ElMessage.success('已保存')
}

function onExport() {
  exportExcel(filtered.value, `租户列表_${Date.now()}`)
}
</script>
