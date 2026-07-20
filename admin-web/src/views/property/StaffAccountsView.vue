<template>
  <div class="page-card">
    <div class="page-header">
      <div>
        <h2>账号管理</h2>
        <p class="sub">管理本社区 App 物业端工作人员（物业管理员 / 网格员），可新增账号</p>
      </div>
      <div class="actions">
        <el-button :loading="loading" @click="load">刷新</el-button>
        <el-button type="primary" :disabled="!canManage" @click="openCreate">添加账号</el-button>
      </div>
    </div>

    <el-alert
      v-if="!user.hasRealToken"
      type="warning"
      :closable="false"
      show-icon
      title="请使用物业真实账号登录（如 13800000001 / 123456）后再管理工作人员"
      style="margin-bottom: 12px"
    />
    <el-alert
      v-else-if="!canManage"
      type="info"
      :closable="false"
      show-icon
      title="仅物业管理员可新增/启停账号；网格员可查看列表"
      style="margin-bottom: 12px"
    />

    <div class="filter-bar">
      <el-select v-model="role" clearable placeholder="角色" style="width: 160px" @change="onSearch">
        <el-option label="物业管理员" value="property_admin" />
        <el-option label="网格员" value="grid_worker" />
      </el-select>
      <el-input
        v-model="keyword"
        clearable
        placeholder="搜索姓名/手机号"
        style="width: 220px"
        @keyup.enter="onSearch"
      />
      <el-button type="primary" :loading="loading" @click="onSearch">查询</el-button>
    </div>

    <el-table v-loading="loading" :data="filtered" stripe empty-text="暂无工作人员账号">
      <el-table-column prop="nickname" label="姓名" min-width="120" />
      <el-table-column prop="phone" label="手机号" width="140" />
      <el-table-column prop="roleLabel" label="角色" width="120" />
      <el-table-column prop="statusLabel" label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">{{ row.statusLabel }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="创建时间" width="170" />
      <el-table-column label="操作" width="220" fixed="right">
        <template #default="{ row }">
          <el-button
            v-if="canManage"
            link
            :type="row.status === 1 ? 'danger' : 'success'"
            :loading="actingId === row.id"
            @click="toggleStatus(row)"
          >
            {{ row.status === 1 ? '停用' : '启用' }}
          </el-button>
          <el-button
            v-if="canManage"
            link
            type="warning"
            :loading="actingId === row.id"
            @click="onResetPwd(row)"
          >
            重置密码
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pager">
      <el-pagination
        v-model:current-page="page"
        v-model:page-size="size"
        background
        layout="total, prev, pager, next"
        :total="total"
        @current-change="load"
        @size-change="load"
      />
    </div>

    <el-dialog v-model="visible" title="添加工作人员账号" width="480px" destroy-on-close>
      <el-form :model="form" label-width="96px">
        <el-form-item label="姓名" required>
          <el-input v-model="form.nickname" maxlength="32" placeholder="显示名称" />
        </el-form-item>
        <el-form-item label="手机号" required>
          <el-input v-model="form.phone" maxlength="11" placeholder="登录用手机号" />
        </el-form-item>
        <el-form-item label="初始密码">
          <el-input v-model="form.password" maxlength="32" placeholder="默认 123456" show-password />
        </el-form-item>
        <el-form-item label="角色" required>
          <el-select v-model="form.role" style="width: 100%">
            <el-option label="物业管理员" value="property_admin" />
            <el-option label="网格员" value="grid_worker" />
          </el-select>
        </el-form-item>
        <p class="form-tip">创建后可用该手机号在 App / 物业工作端登录，角色决定可见功能。</p>
      </el-form>
      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/stores/user'
import {
  createStaffUser,
  listStaffUsers,
  resetStaffPassword,
  updateUserStatus,
  type StaffUser,
} from '@/api/users'

interface Row extends StaffUser {
  roleLabel: string
  statusLabel: string
}

const ROLE_LABEL: Record<string, string> = {
  property_admin: '物业管理员',
  grid_worker: '网格员',
}

const user = useUserStore()
const loading = ref(false)
const saving = ref(false)
const actingId = ref<number | null>(null)
const visible = ref(false)
const page = ref(1)
const size = ref(20)
const total = ref(0)
const role = ref('')
const keyword = ref('')
const list = ref<Row[]>([])

const canManage = computed(
  () => user.hasRealToken && user.role === 'property_admin',
)

const filtered = computed(() => {
  const k = keyword.value.trim().toLowerCase()
  if (!k) return list.value
  return list.value.filter((r) => {
    const blob = `${r.nickname || ''} ${r.phone || ''}`.toLowerCase()
    return blob.includes(k)
  })
})

const form = reactive({
  nickname: '',
  phone: '',
  password: '123456',
  role: 'grid_worker',
})

function mapRow(u: StaffUser): Row {
  return {
    ...u,
    roleLabel: ROLE_LABEL[u.role || ''] || u.role || '-',
    statusLabel: u.status === 1 ? '正常' : '停用',
  }
}

async function load() {
  if (!user.hasRealToken) {
    list.value = []
    total.value = 0
    return
  }
  loading.value = true
  try {
    const data = await listStaffUsers({
      page: page.value,
      size: size.value,
      role: role.value || undefined,
      staffOnly: true,
    })
    list.value = (data?.records || []).map(mapRow)
    total.value = Number(data?.total || 0)
  } catch {
    list.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function onSearch() {
  page.value = 1
  load()
}

function openCreate() {
  if (!canManage.value) {
    ElMessage.warning('仅物业管理员可添加账号')
    return
  }
  form.nickname = ''
  form.phone = ''
  form.password = '123456'
  form.role = 'grid_worker'
  visible.value = true
}

async function save() {
  const phone = form.phone.trim()
  const nickname = form.nickname.trim()
  if (!/^1\d{10}$/.test(phone)) {
    ElMessage.error('请输入正确的 11 位手机号')
    return
  }
  if (!nickname) {
    ElMessage.error('请填写姓名')
    return
  }
  saving.value = true
  try {
    await createStaffUser({
      phone,
      nickname,
      password: form.password?.trim() || '123456',
      role: form.role,
    })
    ElMessage.success('账号已创建，可立即登录 App 物业端')
    visible.value = false
    await load()
  } finally {
    saving.value = false
  }
}

async function toggleStatus(row: Row) {
  const next = row.status === 1 ? 0 : 1
  const tip = next === 0 ? `确认停用「${row.nickname}」？停用后无法登录` : `确认启用「${row.nickname}」？`
  try {
    await ElMessageBox.confirm(tip, '提示', { type: 'warning' })
  } catch {
    return
  }
  actingId.value = row.id
  try {
    await updateUserStatus(row.id, next)
    ElMessage.success(next === 1 ? '已启用' : '已停用')
    await load()
  } finally {
    actingId.value = null
  }
}

async function onResetPwd(row: Row) {
  try {
    await ElMessageBox.confirm(
      `将「${row.nickname}」密码重置为 123456，是否继续？`,
      '重置密码',
      { type: 'warning' },
    )
  } catch {
    return
  }
  actingId.value = row.id
  try {
    await resetStaffPassword(row.id)
    ElMessage.success('密码已重置为 123456')
  } finally {
    actingId.value = null
  }
}

onMounted(load)
</script>

<style scoped>
.page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}
.page-header h2 {
  margin: 0;
  font-size: 18px;
}
.sub {
  margin: 4px 0 0;
  color: #64748b;
  font-size: 13px;
}
.actions {
  display: flex;
  gap: 8px;
}
.filter-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 12px;
}
.pager {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
.form-tip {
  margin: 0 0 0 96px;
  font-size: 12px;
  color: #94a3b8;
  line-height: 1.5;
}
</style>
