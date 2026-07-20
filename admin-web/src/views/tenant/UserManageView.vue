<template>
  <div class="page-card">
    <div class="page-header">
      <h2>用户管理</h2>
      <div>
        <el-button type="primary" @click="openDialog()">新增用户</el-button>
        <el-button @click="onExport">导出 Excel</el-button>
      </div>
    </div>
    <div class="filter-bar">
      <el-input v-model="keyword" placeholder="姓名/手机号" clearable style="width: 220px" />
      <el-select v-model="role" placeholder="角色" clearable style="width: 140px">
        <el-option label="业主" value="owner" />
        <el-option label="租户" value="renter" />
        <el-option label="物业员" value="staff" />
      </el-select>
      <el-button type="primary" @click="loadData">查询</el-button>
    </div>
    <el-table :data="filtered" stripe>
      <el-table-column prop="name" label="姓名" />
      <el-table-column prop="phone" label="手机号" />
      <el-table-column prop="building" label="楼栋房号" />
      <el-table-column prop="role" label="角色" />
      <el-table-column prop="status" label="状态" />
      <el-table-column label="操作" width="160">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDialog(row)">编辑</el-button>
          <el-button link type="danger" @click="remove(row.id)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="visible" :title="editing ? '编辑用户' : '新增用户'" width="480px">
      <el-form :model="form" label-width="80px">
        <el-form-item label="姓名"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="手机号"><el-input v-model="form.phone" /></el-form-item>
        <el-form-item label="房号"><el-input v-model="form.building" /></el-form-item>
        <el-form-item label="角色">
          <el-select v-model="form.role" style="width: 100%">
            <el-option label="业主" value="业主" />
            <el-option label="租户" value="租户" />
            <el-option label="物业员" value="物业员" />
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

interface UserRow {
  id: number
  name: string
  phone: string
  building: string
  role: string
  status: string
}

const keyword = ref('')
const role = ref('')
const list = ref<UserRow[]>([
  { id: 1, name: '王芳', phone: '13800001111', building: '1栋502', role: '业主', status: '正常' },
  { id: 2, name: '李强', phone: '13900002222', building: '2栋301', role: '租户', status: '正常' },
  { id: 3, name: '赵敏', phone: '13700003333', building: '物业中心', role: '物业员', status: '正常' },
])

const filtered = computed(() =>
  list.value.filter((u) => {
    const hitKw = !keyword.value || u.name.includes(keyword.value) || u.phone.includes(keyword.value)
    const hitRole = !role.value || u.role.includes(role.value === 'owner' ? '业主' : role.value === 'renter' ? '租户' : role.value === 'staff' ? '物业' : '')
    return hitKw && hitRole
  }),
)

const visible = ref(false)
const editing = ref<UserRow | null>(null)
const form = reactive({ name: '', phone: '', building: '', role: '业主' })

function openDialog(row?: UserRow) {
  editing.value = row || null
  form.name = row?.name || ''
  form.phone = row?.phone || ''
  form.building = row?.building || ''
  form.role = row?.role || '业主'
  visible.value = true
}

function save() {
  if (editing.value) {
    Object.assign(editing.value, { ...form })
  } else {
    list.value.push({
      id: Date.now(),
      ...form,
      status: '正常',
    })
  }
  visible.value = false
  ElMessage.success('已保存')
}

function remove(id: number) {
  list.value = list.value.filter((u) => u.id !== id)
  ElMessage.success('已删除')
}

function loadData() {
  // GET /api/tenant/users
}

function onExport() {
  exportExcel(filtered.value, `用户列表_${Date.now()}`)
  ElMessage.success('导出成功')
}
</script>
