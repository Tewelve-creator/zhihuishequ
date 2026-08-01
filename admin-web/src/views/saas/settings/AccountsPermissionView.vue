<template>
  <div class="saas-page">
    <div class="saas-shell">
      <header class="page-hd">
        <div class="page-hd-text">
          <h1 class="page-title">账号与权限</h1>
          <p class="page-desc">平台运营账号、角色与菜单权限 · 仅展示现有权限模型能力</p>
        </div>
        <div class="page-actions">
          <el-button class="icon-btn" :loading="loading" aria-label="刷新" @click="reload">
            <el-icon v-if="!loading"><Refresh /></el-icon>
          </el-button>
          <el-button v-if="tab === 'accounts'" type="primary" class="primary-btn" @click="openCreate">
            新增账号
          </el-button>
          <el-button
            v-if="tab === 'perms'"
            type="primary"
            class="primary-btn"
            :disabled="!currentRole || isProtectedRole"
            :loading="savingPerm"
            @click="confirmSavePerm"
          >
            保存权限
          </el-button>
        </div>
      </header>

      <el-tabs v-model="tab" class="tabs" @tab-change="onTab">
        <el-tab-pane label="平台账号" name="accounts" />
        <el-tab-pane label="角色管理" name="roles" />
        <el-tab-pane label="权限配置" name="perms" />
      </el-tabs>

      <!-- 平台账号 -->
      <div v-show="tab === 'accounts'" class="panel">
        <div class="toolbar">
          <el-input
            v-model="keyword"
            clearable
            placeholder="姓名 / 手机号"
            class="kw"
            @keyup.enter="loadAccounts"
            @clear="loadAccounts"
          />
          <el-button type="primary" :loading="loading" @click="loadAccounts">查询</el-button>
        </div>
        <el-table v-loading="loading" :data="displayAccounts" empty-text="暂无平台账号">
          <el-table-column label="账号" min-width="120" show-overflow-tooltip>
            <template #default="{ row }">{{ maskPhone(row.phone) }}</template>
          </el-table-column>
          <el-table-column prop="nickname" label="姓名" min-width="110" show-overflow-tooltip />
          <el-table-column label="所属部门" width="110">
            <template #default>
              <span class="muted" title="用户模型无部门字段">—</span>
            </template>
          </el-table-column>
          <el-table-column label="角色" width="130">
            <template #default="{ row }">{{ roleLabel(row.role) }}</template>
          </el-table-column>
          <el-table-column label="手机号" width="120">
            <template #default="{ row }">{{ maskPhone(row.phone) }}</template>
          </el-table-column>
          <el-table-column label="状态" width="88">
            <template #default="{ row }">
              <span class="pill" :class="row.status === 1 ? 'ok' : 'off'">
                {{ row.status === 1 ? '启用' : '停用' }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="最近登录" width="120">
            <template #default>
              <span class="muted" title="用户列表未返回最近登录">—</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="120" fixed="right">
            <template #default="{ row }">
              <div class="ops">
                <el-button link class="ops-link" @click="toggleStatus(row)">
                  {{ row.status === 1 ? '停用' : '启用' }}
                </el-button>
                <el-dropdown trigger="click" @command="(c: string) => onAccountMore(row, c)">
                  <el-button link class="ops-more"><el-icon><MoreFilled /></el-icon></el-button>
                  <template #dropdown>
                    <el-dropdown-menu>
                      <el-dropdown-item command="reset">重置密码</el-dropdown-item>
                      <el-dropdown-item command="sessions">强制退出（会话）</el-dropdown-item>
                      <el-dropdown-item command="logs">查看操作记录</el-dropdown-item>
                    </el-dropdown-menu>
                  </template>
                </el-dropdown>
              </div>
            </template>
          </el-table-column>
        </el-table>
        <div class="pager">
          <el-pagination
            v-model:current-page="page"
            background
            layout="total, prev, pager, next"
            :total="total"
            @current-change="loadAccounts"
          />
        </div>
      </div>

      <!-- 角色管理 -->
      <div v-show="tab === 'roles'" class="panel">
        <el-table v-loading="loading" :data="roles" empty-text="暂无角色" highlight-current-row>
          <el-table-column prop="roleName" label="角色名称" min-width="140" />
          <el-table-column prop="roleCode" label="角色编码" min-width="140" />
          <el-table-column label="账号数量" width="100">
            <template #default>
              <span class="muted" title="角色列表接口未返回账号数">—</span>
            </template>
          </el-table-column>
          <el-table-column label="权限范围" min-width="140">
            <template #default="{ row }">菜单权限 · {{ roleLabel(row.roleCode) }}</template>
          </el-table-column>
          <el-table-column label="状态" width="88">
            <template #default>
              <span class="pill ok">启用</span>
            </template>
          </el-table-column>
          <el-table-column label="创建时间" width="120">
            <template #default>
              <span class="muted" title="角色实体无创建时间字段">—</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="120" fixed="right">
            <template #default="{ row }">
              <div class="ops">
                <el-button link class="ops-link" @click="gotoPerm(row)">配置权限</el-button>
                <el-dropdown trigger="click" @command="(c: string) => onRoleMore(row, c)">
                  <el-button link class="ops-more"><el-icon><MoreFilled /></el-icon></el-button>
                  <template #dropdown>
                    <el-dropdown-menu>
                      <el-dropdown-item command="view">查看说明</el-dropdown-item>
                      <el-dropdown-item disabled command="del">删除（接口不支持）</el-dropdown-item>
                    </el-dropdown-menu>
                  </template>
                </el-dropdown>
              </div>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <!-- 权限配置 -->
      <div v-show="tab === 'perms'" class="panel">
        <el-alert
          type="info"
          :closable="false"
          show-icon
          class="mb"
          title="当前权限模型仅支持「菜单权限」绑定。页面操作权限码只读展示；数据范围与敏感字段权限暂无独立接口。"
        />
        <el-row :gutter="16">
          <el-col :xs="24" :md="8">
            <h3 class="sec-title">选择角色</h3>
            <el-table
              v-loading="loading"
              :data="roles"
              highlight-current-row
              empty-text="暂无角色"
              max-height="480"
              @current-change="onSelectRole"
            >
              <el-table-column prop="roleName" label="角色" />
              <el-table-column prop="roleCode" label="编码" width="120" />
            </el-table>
          </el-col>
          <el-col :xs="24" :md="16">
            <div class="tree-head">
              <h3 class="sec-title">菜单权限 · {{ currentRole?.roleName || '请选择角色' }}</h3>
              <div v-if="currentRole && !isProtectedRole" class="tree-actions">
                <el-button link type="primary" @click="checkAll">全选</el-button>
                <el-button link @click="clearAll">清空</el-button>
              </div>
            </div>
            <el-alert
              v-if="isProtectedRole"
              type="warning"
              :closable="false"
              show-icon
              class="mb"
              title="系统关键角色请谨慎调整菜单；清空全部权限可能导致无法进入后台。"
            />
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

            <h3 class="sec-title mt">页面操作权限（只读）</h3>
            <div class="perm-tags">
              <el-tag v-for="p in permissions" :key="p" size="small" effect="plain">{{ p }}</el-tag>
              <span v-if="!permissions.length" class="muted">暂无权限码</span>
            </div>

            <h3 class="sec-title mt">数据范围 / 敏感字段</h3>
            <p class="muted">后端未提供数据范围与敏感字段矩阵接口，此处不提供可编辑开关。</p>
          </el-col>
        </el-row>
      </div>
    </div>

    <el-dialog v-model="visible" title="新增平台账号" width="440px" destroy-on-close>
      <el-form :model="form" label-width="88px">
        <el-form-item label="手机号" required>
          <el-input v-model="form.phone" maxlength="11" />
        </el-form-item>
        <el-form-item label="姓名" required>
          <el-input v-model="form.nickname" maxlength="32" />
        </el-form-item>
        <el-form-item label="初始密码">
          <el-input v-model="form.password" show-password placeholder="默认 123456" />
        </el-form-item>
        <el-form-item label="角色">
          <el-input model-value="platform_admin（平台运营）" disabled />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveAccount">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { MoreFilled, Refresh } from '@element-plus/icons-vue'
import type { ElTree } from 'element-plus'
import {
  pageUsers,
  createUser,
  updateUserStatus,
  resetUserPassword,
  listRoles,
  listAllMenus,
  listRoleMenus,
  saveRoleMenus,
  listPermissions,
  listSessions,
  kickSession,
} from '@/api/saas'

const route = useRoute()
const router = useRouter()
const tab = ref('accounts')
const loading = ref(false)
const saving = ref(false)
const savingPerm = ref(false)
const page = ref(1)
const total = ref(0)
const keyword = ref('')
const accounts = ref<any[]>([])
const roles = ref<any[]>([])
const menuTree = ref<any[]>([])
const permissions = ref<string[]>([])
const currentRole = ref<any>(null)
const checkedKeys = ref<number[]>([])
const initialMenuIds = ref<number[]>([])
const treeRef = ref<InstanceType<typeof ElTree>>()
const visible = ref(false)
const form = reactive({ phone: '', nickname: '', password: '123456' })

const PROTECTED_CODES = new Set(['platform_admin', 'super_admin', 'admin'])

const displayAccounts = computed(() => {
  const kw = keyword.value.trim()
  if (!kw) return accounts.value
  return accounts.value.filter(
    (u) => String(u.nickname || '').includes(kw) || String(u.phone || '').includes(kw),
  )
})

const isProtectedRole = computed(() => {
  const code = String(currentRole.value?.roleCode || '')
  return PROTECTED_CODES.has(code)
})

function roleLabel(code?: string) {
  const map: Record<string, string> = {
    platform_admin: '平台运营',
    property_admin: '物业管理员',
    staff: '物业员工',
    resident: '业主',
  }
  return map[String(code || '')] || code || '—'
}

function maskPhone(phone?: string) {
  const p = String(phone || '')
  if (p.includes('*')) return p
  if (p.length < 7) return p || '—'
  return p.slice(0, 3) + '****' + p.slice(-4)
}

function onTab() {
  if (tab.value === 'accounts') loadAccounts()
  else if (tab.value === 'roles' || tab.value === 'perms') loadRolesAndMenus()
}

function gotoPerm(row: any) {
  tab.value = 'perms'
  nextTick(() => onSelectRole(row))
}

async function loadAccounts() {
  loading.value = true
  try {
    const data = await pageUsers({ page: page.value, size: 50, role: 'platform_admin' })
    accounts.value = (data?.records || []).filter((u: any) => u.role === 'platform_admin')
    total.value = Number(data?.total || accounts.value.length)
  } catch {
    accounts.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

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

function titleOfId(id: number, nodes: any[] = menuTree.value): string {
  for (const n of nodes) {
    if (n.id === id) return n.title || String(id)
    if (n.children?.length) {
      const t = titleOfId(id, n.children)
      if (t) return t
    }
  }
  return String(id)
}

async function onSelectRole(row: any) {
  currentRole.value = row
  if (!row?.id) {
    checkedKeys.value = []
    initialMenuIds.value = []
    return
  }
  try {
    const ids = (await listRoleMenus(row.id)) || []
    checkedKeys.value = ids
    initialMenuIds.value = [...ids]
    await nextTick()
    treeRef.value?.setCheckedKeys(ids)
  } catch {
    checkedKeys.value = []
    initialMenuIds.value = []
  }
}

function checkAll() {
  treeRef.value?.setCheckedKeys(collectAllIds(menuTree.value))
}

function clearAll() {
  if (isProtectedRole.value) {
    ElMessage.warning('系统角色不建议清空全部菜单权限')
    return
  }
  treeRef.value?.setCheckedKeys([])
}

async function confirmSavePerm() {
  if (!currentRole.value?.id) {
    ElMessage.warning('请先选择角色')
    return
  }
  const half = (treeRef.value?.getHalfCheckedKeys?.() || []) as number[]
  const checked = (treeRef.value?.getCheckedKeys(false) || []) as number[]
  const menuIds = [...new Set([...half, ...checked].map(Number).filter(Boolean))]
  const prev = new Set(initialMenuIds.value)
  const next = new Set(menuIds)
  const added = [...next].filter((id) => !prev.has(id))
  const removed = [...prev].filter((id) => !next.has(id))

  if (isProtectedRole.value && menuIds.length === 0) {
    ElMessage.error('系统角色不能保存为空权限')
    return
  }

  const lines = [
    `角色：${currentRole.value.roleName}（${currentRole.value.roleCode}）`,
    `菜单数：${prev.size} → ${next.size}`,
    added.length ? `新增：${added.slice(0, 8).map((id) => titleOfId(id)).join('、')}${added.length > 8 ? '…' : ''}` : '新增：无',
    removed.length
      ? `移除：${removed.slice(0, 8).map((id) => titleOfId(id)).join('、')}${removed.length > 8 ? '…' : ''}`
      : '移除：无',
  ]
  try {
    await ElMessageBox.confirm(lines.join('\n'), '权限变更摘要', {
      type: 'warning',
      confirmButtonText: '确认保存',
      distinguishCancelAndClose: true,
    })
  } catch {
    return
  }

  savingPerm.value = true
  try {
    await saveRoleMenus(currentRole.value.id, menuIds)
    ElMessage.success('菜单权限已保存')
    initialMenuIds.value = [...menuIds]
    checkedKeys.value = checked.length
      ? checked
      : collectLeafIds(menuTree.value).filter((id) => menuIds.includes(id))
  } finally {
    savingPerm.value = false
  }
}

async function loadRolesAndMenus() {
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
    if (roles.value.length && tab.value === 'perms') {
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

function openCreate() {
  form.phone = ''
  form.nickname = ''
  form.password = '123456'
  visible.value = true
}

async function saveAccount() {
  const phone = form.phone.trim()
  const nickname = form.nickname.trim()
  if (!/^1\d{10}$/.test(phone)) {
    ElMessage.error('请输入正确的手机号')
    return
  }
  if (!nickname) {
    ElMessage.error('请填写姓名')
    return
  }
  saving.value = true
  try {
    await createUser({
      phone,
      nickname,
      password: form.password.trim() || '123456',
      role: 'platform_admin',
    })
    ElMessage.success('账号已创建')
    visible.value = false
    await loadAccounts()
  } finally {
    saving.value = false
  }
}

async function toggleStatus(row: any) {
  const next = row.status === 1 ? 0 : 1
  try {
    await ElMessageBox.confirm(`确认${next === 1 ? '启用' : '停用'}「${row.nickname}」？`, '提示', {
      type: 'warning',
    })
  } catch {
    return
  }
  await updateUserStatus(row.id, next)
  ElMessage.success('状态已更新')
  await loadAccounts()
}

async function onAccountMore(row: any, cmd: string) {
  if (cmd === 'reset') {
    try {
      await ElMessageBox.confirm(`重置「${row.nickname}」密码为 123456？`, '重置密码', {
        type: 'warning',
      })
    } catch {
      return
    }
    await resetUserPassword(row.id)
    ElMessage.success('密码已重置')
    return
  }
  if (cmd === 'sessions') {
    try {
      const sessions = (await listSessions()) || []
      const mine = sessions.filter((s: any) => Number(s.userId) === Number(row.id) && !s.current)
      if (!mine.length) {
        ElMessage.info('该账号暂无可踢下线的其他会话（或仅有当前会话）')
        return
      }
      await ElMessageBox.confirm(
        `将强制退出该账号的 ${mine.length} 个会话，确认继续？`,
        '强制退出',
        { type: 'warning' },
      )
      for (const s of mine) {
        if (s.token) await kickSession(s.token)
      }
      ElMessage.success('已强制退出相关会话')
    } catch {
      /* cancel or error */
    }
    return
  }
  if (cmd === 'logs') {
    router.push({ path: '/admin/saas/settings/security', query: { tab: 'oper', keyword: row.nickname || '' } })
  }
}

function onRoleMore(row: any, cmd: string) {
  if (cmd === 'view') {
    ElMessageBox.alert(
      `角色「${row.roleName}」编码 ${row.roleCode}。账号数量与创建时间接口未返回；删除角色接口暂不可用。`,
      '角色说明',
    )
  }
}

function reload() {
  onTab()
}

watch(
  () => route.query.tab,
  (v) => {
    if (v === 'roles' || v === 'perms' || v === 'accounts') tab.value = String(v)
  },
  { immediate: true },
)

onMounted(() => {
  onTab()
})
</script>

<style scoped>
.saas-page {
  margin: -20px;
  padding: 24px;
  min-height: calc(100% + 40px);
  background: #f5f6f8;
  box-sizing: border-box;
}
.saas-shell {
  max-width: 1440px;
  margin: 0 auto;
}
.page-hd {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}
.page-title {
  margin: 0;
  font-size: 20px;
  font-weight: 650;
  color: #182230;
}
.page-desc {
  margin: 4px 0 0;
  font-size: 13px;
  color: #667085;
}
.page-actions {
  display: flex;
  gap: 8px;
  align-items: flex-start;
}
.icon-btn {
  width: 36px;
  padding: 8px;
}
.primary-btn {
  background: #2563eb;
  border-color: #2563eb;
}
.tabs :deep(.el-tabs__item.is-active) {
  color: #2563eb;
}
.tabs :deep(.el-tabs__active-bar) {
  background: #2563eb;
}
.panel {
  margin-top: 12px;
  background: #fff;
  border: 1px solid #e4e7ec;
  border-radius: 8px;
  padding: 16px 20px;
}
.toolbar {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
  flex-wrap: wrap;
}
.kw {
  width: 220px;
  max-width: 100%;
}
.ops {
  display: flex;
  align-items: center;
  gap: 4px;
}
.ops-link {
  color: #2563eb !important;
}
.ops-more {
  color: #667085 !important;
  padding: 4px !important;
}
.pill {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 12px;
}
.pill.ok {
  background: #ecfdf3;
  color: #027a48;
}
.pill.off {
  background: #f2f4f7;
  color: #667085;
}
.muted {
  color: #667085;
  font-size: 13px;
}
.pager {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
.sec-title {
  margin: 0 0 8px;
  font-size: 14px;
  font-weight: 600;
  color: #182230;
}
.sec-title.mt {
  margin-top: 20px;
}
.tree-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
}
.mb {
  margin-bottom: 12px;
}
.perm-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
@media (max-width: 768px) {
  .saas-page {
    margin: -12px;
    padding: 16px;
  }
  .kw {
    width: 100%;
  }
}
</style>
