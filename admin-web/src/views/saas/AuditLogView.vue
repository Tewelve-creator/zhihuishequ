<template>
  <div class="page-card">
    <div class="page-header">
      <div>
        <h2>登录与操作日志</h2>
        <p class="sub">登录审计、操作记录与在线会话</p>
      </div>
      <el-button :loading="loading" @click="reloadTab">刷新</el-button>
    </div>

    <el-tabs v-model="tab" @tab-change="onTabChange">
      <el-tab-pane label="登录日志" name="login">
        <el-table v-loading="loading" :data="loginList" stripe empty-text="暂无登录日志">
          <el-table-column prop="phone" label="账号/手机" width="130" />
          <el-table-column prop="nickname" label="昵称" width="120" />
          <el-table-column prop="ip" label="IP" width="130" />
          <el-table-column prop="success" label="结果" width="80">
            <template #default="{ row }">
              <el-tag :type="row.success ? 'success' : 'danger'" size="small">{{ row.success ? '成功' : '失败' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="message" label="说明" min-width="140" show-overflow-tooltip />
          <el-table-column prop="createTime" label="时间" width="170" />
        </el-table>
        <div class="pager">
          <el-pagination
            v-model:current-page="loginPage"
            background
            layout="total, prev, pager, next"
            :total="loginTotal"
            @current-change="loadLogin"
          />
        </div>
      </el-tab-pane>

      <el-tab-pane label="操作日志" name="oper">
        <el-table v-loading="loading" :data="operList" stripe empty-text="暂无操作日志">
          <el-table-column prop="operatorName" label="操作人" width="100" />
          <el-table-column prop="module" label="模块" width="100" />
          <el-table-column prop="action" label="动作" width="100" />
          <el-table-column prop="detail" label="详情" min-width="180" show-overflow-tooltip />
          <el-table-column prop="ip" label="IP" width="130" />
          <el-table-column prop="createTime" label="时间" width="170" />
        </el-table>
        <div class="pager">
          <el-pagination
            v-model:current-page="operPage"
            background
            layout="total, prev, pager, next"
            :total="operTotal"
            @current-change="loadOper"
          />
        </div>
      </el-tab-pane>

      <el-tab-pane label="在线会话" name="session">
        <el-table v-loading="loading" :data="sessions" stripe empty-text="暂无在线会话">
          <el-table-column prop="userId" label="用户ID" width="90" />
          <el-table-column prop="nickname" label="昵称" width="120" />
          <el-table-column prop="phone" label="手机号" width="130" />
          <el-table-column prop="role" label="角色" width="130" />
          <el-table-column prop="loginTime" label="登录时间" width="170" />
          <el-table-column label="本机" width="70">
            <template #default="{ row }">
              <el-tag v-if="row.current" type="success" size="small">当前</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="token" label="Token" min-width="160" show-overflow-tooltip />
          <el-table-column label="操作" width="100" fixed="right">
            <template #default="{ row }">
              <el-button link type="danger" :disabled="row.current" @click="onKick(row)">踢下线</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { pageLoginLogs, listOperLogs, listSessions, kickSession } from '@/api/saas'

const tab = ref('login')
const loading = ref(false)
const loginPage = ref(1)
const operPage = ref(1)
const loginTotal = ref(0)
const operTotal = ref(0)
const loginList = ref<any[]>([])
const operList = ref<any[]>([])
const sessions = ref<any[]>([])

async function loadLogin() {
  loading.value = true
  try {
    const data = await pageLoginLogs({ page: loginPage.value, size: 20 })
    loginList.value = data?.records || []
    loginTotal.value = Number(data?.total || 0)
  } catch {
    loginList.value = []
    loginTotal.value = 0
  } finally {
    loading.value = false
  }
}

async function loadOper() {
  loading.value = true
  try {
    const data = await listOperLogs({ page: operPage.value, size: 20 })
    operList.value = data?.records || []
    operTotal.value = Number(data?.total || 0)
  } catch {
    operList.value = []
    operTotal.value = 0
  } finally {
    loading.value = false
  }
}

async function loadSessions() {
  loading.value = true
  try {
    sessions.value = (await listSessions()) || []
  } catch {
    sessions.value = []
  } finally {
    loading.value = false
  }
}

function reloadTab() {
  if (tab.value === 'login') loadLogin()
  else if (tab.value === 'oper') loadOper()
  else loadSessions()
}

function onTabChange(name: string | number) {
  tab.value = String(name)
  reloadTab()
}

async function onKick(row: any) {
  const token = row.token
  if (!token) return
  try {
    await ElMessageBox.confirm(`确认踢下线「${row.nickname || row.userId}」？`, '踢下线', { type: 'warning' })
  } catch {
    return
  }
  try {
    await kickSession(token)
    ElMessage.success('已踢下线')
    await loadSessions()
  } catch {
    /* handled */
  }
}

onMounted(loadLogin)
</script>

<style scoped>
.sub { margin: 4px 0 0; color: #64748b; font-size: 13px; }
.pager { margin-top: 16px; display: flex; justify-content: flex-end; }
</style>
