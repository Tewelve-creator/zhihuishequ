<template>
  <div class="page-card">
    <div class="page-header">
      <div>
        <h2>角色与菜单权限</h2>
        <p class="sub">配置平台角色可访问的菜单与按钮权限</p>
      </div>
      <div class="actions">
        <el-button :loading="loading" @click="load">刷新</el-button>
        <el-button type="primary" :disabled="!currentRole" :loading="saving" @click="save">保存绑定</el-button>
      </div>
    </div>

    <el-row :gutter="16">
      <el-col :span="8">
        <h3 class="section-title">角色列表</h3>
        <el-table
          v-loading="loading"
          :data="roles"
          highlight-current-row
          stripe
          empty-text="暂无角色"
          @current-change="onSelectRole"
        >
          <el-table-column prop="roleName" label="角色" />
          <el-table-column prop="roleCode" label="编码" width="140" />
        </el-table>
      </el-col>
      <el-col :span="16">
        <div class="tree-head">
          <h3 class="section-title">菜单权限 · {{ currentRole?.roleName || '请选择角色' }}</h3>
          <div v-if="currentRole" class="tree-actions">
            <el-button link type="primary" @click="checkAll">全选</el-button>
            <el-button link @click="clearAll">清空</el-button>
          </div>
        </div>
        <el-tree
          v-if="menuTree.length"
          ref="treeRef"
          :data="menuTree"
          :props="{ label: 'title', children: 'children' }"
          show-checkbox
          node-key="id"
          default-expand-all
          :default-checked-keys="checkedKeys"
        />
        <el-empty v-else description="暂无菜单数据" />

        <h3 class="section-title" style="margin-top: 20px">当前账号权限码（只读）</h3>
        <div class="perm-tags">
          <el-tag v-for="p in permissions" :key="p" size="small" style="margin: 4px">{{ p }}</el-tag>
          <span v-if="!permissions.length" class="muted">暂无权限码</span>
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { nextTick, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import type { ElTree } from 'element-plus'
import {
  listRoles,
  listAllMenus,
  listRoleMenus,
  saveRoleMenus,
  listPermissions,
} from '@/api/saas'

const loading = ref(false)
const saving = ref(false)
const roles = ref<any[]>([])
const menuTree = ref<any[]>([])
const permissions = ref<string[]>([])
const currentRole = ref<any>(null)
const checkedKeys = ref<number[]>([])
const treeRef = ref<InstanceType<typeof ElTree>>()

function collectLeafIds(nodes: any[], out: number[] = []) {
  for (const n of nodes) {
    if (n.children?.length) collectLeafIds(n.children, out)
    else if (n.id != null) out.push(n.id)
  }
  return out
}

function collectAllIds(nodes: any[], out: number[] = []) {
  for (const n of nodes) {
    if (n.id != null) out.push(n.id)
    if (n.children?.length) collectAllIds(n.children, out)
  }
  return out
}

async function onSelectRole(row: any) {
  currentRole.value = row
  if (!row?.id) {
    checkedKeys.value = []
    return
  }
  try {
    const ids = (await listRoleMenus(row.id)) || []
    checkedKeys.value = ids
    await nextTick()
    treeRef.value?.setCheckedKeys(ids)
  } catch {
    checkedKeys.value = []
  }
}

function checkAll() {
  const ids = collectAllIds(menuTree.value)
  treeRef.value?.setCheckedKeys(ids)
}

function clearAll() {
  treeRef.value?.setCheckedKeys([])
}

async function save() {
  if (!currentRole.value?.id) {
    ElMessage.warning('请先选择角色')
    return
  }
  const half = (treeRef.value?.getHalfCheckedKeys?.() || []) as number[]
  const checked = (treeRef.value?.getCheckedKeys(false) || []) as number[]
  const menuIds = [...new Set([...half, ...checked].map(Number).filter(Boolean))]
  saving.value = true
  try {
    await saveRoleMenus(currentRole.value.id, menuIds)
    ElMessage.success('菜单权限已保存')
    checkedKeys.value = checked.length ? checked : collectLeafIds(menuTree.value).filter((id) => menuIds.includes(id))
  } finally {
    saving.value = false
  }
}

async function load() {
  loading.value = true
  try {
    const [r, m, p] = await Promise.allSettled([
      listRoles(),
      listAllMenus('ADMIN'),
      listPermissions(),
    ])
    roles.value = r.status === 'fulfilled' ? r.value || [] : []
    menuTree.value = m.status === 'fulfilled' ? m.value || [] : []
    permissions.value = p.status === 'fulfilled' ? p.value || [] : []
    if (roles.value.length) {
      const keep = currentRole.value
        ? roles.value.find((x) => x.id === currentRole.value.id)
        : roles.value[0]
      currentRole.value = keep || roles.value[0]
      await onSelectRole(currentRole.value)
    }
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.sub { margin: 4px 0 0; color: #64748b; font-size: 13px; }
.actions { display: flex; gap: 8px; }
.section-title { margin: 0 0 8px; font-size: 15px; font-weight: 600; }
.tree-head { display: flex; align-items: center; justify-content: space-between; gap: 8px; }
.tree-actions { display: flex; gap: 4px; }
.perm-tags { display: flex; flex-wrap: wrap; gap: 4px; }
.muted { color: #94a3b8; font-size: 13px; }
</style>
