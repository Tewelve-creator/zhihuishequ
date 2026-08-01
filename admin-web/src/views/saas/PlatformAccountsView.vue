<template>
  <div class="page-card">
    <div class="page-header">
      <div>
        <h2>平台账号管理</h2>
        <p class="sub">SaaS 运营管理端 platform_admin 账号</p>
      </div>
      <div class="actions">
        <el-button :loading="loading" @click="load">刷新</el-button>
        <el-button type="primary" @click="openCreate">新增账号</el-button>
      </div>
    </div>

    <div class="filter-bar">
      <el-input v-model="keyword" clearable placeholder="昵称/手机号" style="width: 220px" @keyup.enter="load" />
      <el-button type="primary" :loading="loading" @click="load">查询</el-button>
    </div>

    <el-table v-loading="loading" :data="displayList" stripe empty-text="暂无平台账号">
      <el-table-column prop="nickname" label="昵称" min-width="120" />
      <el-table-column prop="phone" label="手机号" width="140" />
      <el-table-column prop="role" label="角色" width="140" />
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">{{ row.status === 1 ? '正常' : '停用' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="创建时间" width="170" />
      <el-table-column label="操作" width="200" fixed="right">
        <template #default="{ row }">
          <el-button link :type="row.status === 1 ? 'danger' : 'success'" @click="toggleStatus(row)">
            {{ row.status === 1 ? '停用' : '启用' }}
          </el-button>
          <el-button link type="warning" @click="onResetPwd(row)">重置密码</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pager">
      <el-pagination
        v-model:current-page="page"
        background
        layout="total, prev, pager, next"
        :total="total"
        @current-change="load"
      />
    </div>

    <el-dialog v-model="visible" title="新增平台账号" width="440px" destroy-on-close>
      <el-form :model="form" label-width="88px">
        <el-form-item label="手机号" required><el-input v-model="form.phone" maxlength="11" /></el-form-item>
        <el-form-item label="昵称" required><el-input v-model="form.nickname" maxlength="32" /></el-form-item>
        <el-form-item label="密码"><el-input v-model="form.password" show-password placeholder="默认 123456" /></el-form-item>
        <el-form-item label="角色"><el-input :model-value="'platform_admin'" disabled /></el-form-item>
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
import { pageUsers, createUser, updateUserStatus, resetUserPassword } from '@/api/saas'

const loading = ref(false)
const saving = ref(false)
const page = ref(1)
const total = ref(0)
const keyword = ref('')
const list = ref<any[]>([])
const visible = ref(false)
const form = reactive({ phone: '', nickname: '', password: '123456' })

const displayList = computed(() => {
  const kw = keyword.value.trim()
  if (!kw) return list.value
  return list.value.filter(
    (u) => String(u.nickname || '').includes(kw) || String(u.phone || '').includes(kw),
  )
})

async function load() {
  loading.value = true
  try {
    const data = await pageUsers({ page: page.value, size: 50, role: 'platform_admin' })
    list.value = (data?.records || []).filter((u: any) => u.role === 'platform_admin')
    total.value = Number(data?.total || list.value.length)
  } catch {
    list.value = []
    total.value = 0
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

async function save() {
  const phone = form.phone.trim()
  const nickname = form.nickname.trim()
  if (!/^1\d{10}$/.test(phone)) {
    ElMessage.error('请输入正确的手机号')
    return
  }
  if (!nickname) {
    ElMessage.error('请填写昵称')
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
    await load()
  } finally {
    saving.value = false
  }
}

async function toggleStatus(row: any) {
  const next = row.status === 1 ? 0 : 1
  try {
    await ElMessageBox.confirm(`确认${next === 1 ? '启用' : '停用'}「${row.nickname}」？`, '提示', { type: 'warning' })
  } catch {
    return
  }
  try {
    await updateUserStatus(row.id, next)
    ElMessage.success('状态已更新')
    await load()
  } catch {
    /* handled */
  }
}

async function onResetPwd(row: any) {
  try {
    await ElMessageBox.confirm(`重置「${row.nickname}」密码为 123456？`, '重置密码', { type: 'warning' })
  } catch {
    return
  }
  try {
    await resetUserPassword(row.id)
    ElMessage.success('密码已重置')
  } catch {
    /* handled */
  }
}

onMounted(load)
</script>

<style scoped>
.sub { margin: 4px 0 0; color: #64748b; font-size: 13px; }
.actions { display: flex; gap: 8px; }
.filter-bar { display: flex; gap: 8px; margin-bottom: 12px; }
.pager { margin-top: 16px; display: flex; justify-content: flex-end; }
</style>
