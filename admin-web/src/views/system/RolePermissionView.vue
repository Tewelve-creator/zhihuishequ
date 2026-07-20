<template>
  <div class="page-card">
    <div class="page-header">
      <h2>角色权限控制</h2>
      <el-button type="primary" @click="openRole()">新增角色</el-button>
    </div>
    <el-row :gutter="16">
      <el-col :span="10">
        <el-table :data="roles" highlight-current-row @current-change="onSelect" stripe>
          <el-table-column prop="name" label="角色" />
          <el-table-column prop="code" label="编码" width="120" />
          <el-table-column label="操作" width="100">
            <template #default="{ row }">
              <el-button link type="primary" @click="openRole(row)">编辑</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-col>
      <el-col :span="14">
        <h3 style="margin-top: 0">权限配置 · {{ current?.name || '请选择角色' }}</h3>
        <el-tree
          v-if="current"
          ref="treeRef"
          :data="permTree"
          show-checkbox
          node-key="id"
          default-expand-all
          :default-checked-keys="current.permissions"
        />
        <el-button v-if="current" type="primary" style="margin-top: 16px" @click="savePerms">
          保存权限
        </el-button>
      </el-col>
    </el-row>

    <el-dialog v-model="visible" :title="editing ? '编辑角色' : '新增角色'" width="420px">
      <el-form :model="form" label-width="80px">
        <el-form-item label="名称"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="编码"><el-input v-model="form.code" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" @click="saveRole">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { ElMessage, type ElTree } from 'element-plus'

interface Role {
  id: number
  name: string
  code: string
  permissions: string[]
}

const roles = ref<Role[]>([
  { id: 1, name: '超级管理员', code: 'admin', permissions: ['*'] },
  { id: 2, name: '物业运营', code: 'tenant_ops', permissions: ['dashboard:view', 'user:view', 'repair:view', 'notice:view'] },
  { id: 3, name: 'SaaS 运营', code: 'saas_ops', permissions: ['saas:tenant', 'saas:package', 'saas:order', 'screen:revenue'] },
])

const permTree = [
  {
    id: 'tenant',
    label: '物业租户后台',
    children: [
      { id: 'dashboard:view', label: '工作台' },
      { id: 'user:view', label: '用户管理' },
      { id: 'repair:view', label: '工单管理' },
      { id: 'notice:view', label: '公告活动' },
      { id: 'package:renew', label: '续费套餐' },
      { id: 'screen:community', label: '社区大屏' },
    ],
  },
  {
    id: 'saas',
    label: 'SaaS 运营后台',
    children: [
      { id: 'saas:tenant', label: '租户管理' },
      { id: 'saas:package', label: '套餐配置' },
      { id: 'saas:order', label: '订单财务' },
      { id: 'screen:revenue', label: '营收大屏' },
    ],
  },
  {
    id: 'system',
    label: '系统',
    children: [{ id: 'system:role', label: '角色权限' }],
  },
]

const current = ref<Role | null>(roles.value[0])
const treeRef = ref<InstanceType<typeof ElTree>>()
const visible = ref(false)
const editing = ref<Role | null>(null)
const form = reactive({ name: '', code: '' })

function onSelect(row: Role | undefined) {
  if (row) current.value = row
}

function openRole(row?: Role) {
  editing.value = row || null
  form.name = row?.name || ''
  form.code = row?.code || ''
  visible.value = true
}

function saveRole() {
  if (editing.value) {
    Object.assign(editing.value, { ...form })
  } else {
    roles.value.push({ id: Date.now(), ...form, permissions: [] })
  }
  visible.value = false
  ElMessage.success('角色已保存')
}

function savePerms() {
  if (!current.value || !treeRef.value) return
  const keys = treeRef.value.getCheckedKeys(true) as string[]
  current.value.permissions = keys
  // PUT /api/system/roles/{id}/permissions 对接微服务网关
  ElMessage.success('权限已保存')
}
</script>
