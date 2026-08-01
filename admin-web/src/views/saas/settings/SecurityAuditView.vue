<template>
  <div class="saas-page">
    <div class="saas-shell">
      <header class="page-hd">
        <div class="page-hd-text">
          <h1 class="page-title">安全与审计</h1>
          <p class="page-desc">登录审计、操作记录与在线会话 · 敏感字段已过滤</p>
        </div>
        <div class="page-actions">
          <el-button class="icon-btn" :loading="loading" aria-label="刷新" @click="reloadTab">
            <el-icon v-if="!loading"><Refresh /></el-icon>
          </el-button>
        </div>
      </header>

      <el-tabs v-model="tab" class="tabs" @tab-change="onTabChange">
        <el-tab-pane label="登录日志" name="login" />
        <el-tab-pane label="操作日志" name="oper" />
        <el-tab-pane label="在线会话" name="session" />
        <el-tab-pane label="安全策略" name="policy" />
      </el-tabs>

      <div v-show="tab === 'login'" class="panel">
        <div class="toolbar">
          <el-input v-model="loginKw" clearable placeholder="账号 / IP" class="f-input" @keyup.enter="filterLogin" />
          <el-select v-model="loginResult" clearable placeholder="登录结果" class="f-select" @change="filterLogin">
            <el-option label="成功" :value="1" />
            <el-option label="失败" :value="0" />
          </el-select>
          <el-button type="primary" :loading="loading" @click="loadLogin">查询</el-button>
        </div>
        <el-table v-loading="loading" :data="filteredLogin" empty-text="暂无登录日志">
          <el-table-column label="账号" width="130">
            <template #default="{ row }">{{ row.phone || '—' }}</template>
          </el-table-column>
          <el-table-column label="登录时间" width="170">
            <template #default="{ row }">{{ row.createTime || '—' }}</template>
          </el-table-column>
          <el-table-column prop="ip" label="IP 地址" width="140" />
          <el-table-column label="设备 / 客户端" min-width="140">
            <template #default="{ row }">
              {{ [row.clientType, row.loginType].filter(Boolean).join(' · ') || '—' }}
            </template>
          </el-table-column>
          <el-table-column label="登录结果" width="90">
            <template #default="{ row }">
              <span class="pill" :class="row.success ? 'ok' : 'danger'">
                {{ row.success ? '成功' : '失败' }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="失败原因" min-width="160" show-overflow-tooltip>
            <template #default="{ row }">{{ row.success ? '—' : sanitizeText(row.msg || row.message) }}</template>
          </el-table-column>
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
      </div>

      <div v-show="tab === 'oper'" class="panel">
        <div class="toolbar">
          <el-input
            v-model="operKw"
            clearable
            placeholder="操作人 / 模块 / IP"
            class="f-input"
            @keyup.enter="filterOper"
          />
          <el-input v-model="operModule" clearable placeholder="业务模块" class="f-select" @keyup.enter="filterOper" />
          <el-button type="primary" :loading="loading" @click="loadOper">查询</el-button>
        </div>
        <el-table v-loading="loading" :data="filteredOper" empty-text="暂无操作日志">
          <el-table-column prop="operatorName" label="操作人" width="110" />
          <el-table-column prop="module" label="业务模块" width="110" />
          <el-table-column prop="action" label="操作类型" width="110" />
          <el-table-column label="操作对象 / 摘要" min-width="180" show-overflow-tooltip>
            <template #default="{ row }">
              <el-button link class="ops-link" @click="openDetail(row)">{{ summarize(row.detail) }}</el-button>
            </template>
          </el-table-column>
          <el-table-column label="操作结果" width="90">
            <template #default>
              <span class="pill ok">已记录</span>
            </template>
          </el-table-column>
          <el-table-column prop="ip" label="IP 地址" width="130" />
          <el-table-column prop="createTime" label="操作时间" width="170" />
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
      </div>

      <div v-show="tab === 'session'" class="panel">
        <el-alert
          type="info"
          :closable="false"
          show-icon
          class="mb"
          title="会话 Token 已脱敏展示。强制退出将调用踢下线接口。"
        />
        <el-table v-loading="loading" :data="sessions" empty-text="暂无在线会话">
          <el-table-column prop="userId" label="用户 ID" width="90" />
          <el-table-column prop="nickname" label="姓名" width="120" />
          <el-table-column label="账号" width="130">
            <template #default="{ row }">{{ maskPhone(row.phone) }}</template>
          </el-table-column>
          <el-table-column prop="role" label="角色" width="130" />
          <el-table-column prop="loginTime" label="登录时间" width="170" />
          <el-table-column label="会话标识" min-width="160">
            <template #default="{ row }">{{ maskToken(row.token) }}</template>
          </el-table-column>
          <el-table-column label="本机" width="70">
            <template #default="{ row }">
              <span v-if="row.current" class="pill ok">当前</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="100" fixed="right">
            <template #default="{ row }">
              <el-button link type="danger" :disabled="row.current" @click="onKick(row)">强制退出</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <div v-show="tab === 'policy'" class="panel policy">
        <el-alert
          type="warning"
          :closable="false"
          show-icon
          title="以下安全策略当前没有独立后端配置接口，不提供可伪造开关。会话强制退出请使用「在线会话」页签。"
        />
        <ul class="policy-list">
          <li>密码强度与有效期 — 未提供配置接口</li>
          <li>登录失败锁定 — 未提供配置接口</li>
          <li>会话有效期 — 依赖 Sa-Token 服务端配置，非本页可改</li>
          <li>多设备登录 — 可通过踢会话限制，无白名单策略接口</li>
          <li>二次验证 — 未提供配置接口</li>
          <li>IP 白名单 — 未提供配置接口</li>
        </ul>
      </div>
    </div>

    <el-drawer v-model="detailVisible" title="操作日志详情" size="480px" destroy-on-close>
      <template v-if="current">
        <dl class="detail-dl">
          <div><dt>操作人</dt><dd>{{ current.operatorName || '—' }}</dd></div>
          <div><dt>模块</dt><dd>{{ current.module || '—' }}</dd></div>
          <div><dt>类型</dt><dd>{{ current.action || '—' }}</dd></div>
          <div><dt>IP</dt><dd>{{ current.ip || '—' }}</dd></div>
          <div><dt>时间</dt><dd>{{ current.createTime || '—' }}</dd></div>
          <div><dt>请求标识</dt><dd>{{ current.id != null ? `LOG-${current.id}` : '—' }}</dd></div>
        </dl>
        <h4 class="sec">变更摘要（已过滤敏感字段）</h4>
        <pre class="body">{{ sanitizeText(current.detail) || '—' }}</pre>
      </template>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { pageLoginLogs, listOperLogs, listSessions, kickSession } from '@/api/saas'

const SENSITIVE_RE =
  /(password|passwd|pwd|token|cookie|authorization|api[_-]?key|secret|身份证|银行卡|id[_-]?card|bank[_-]?card)/gi

const route = useRoute()
const tab = ref('login')
const loading = ref(false)
const loginPage = ref(1)
const operPage = ref(1)
const loginTotal = ref(0)
const operTotal = ref(0)
const loginList = ref<any[]>([])
const operList = ref<any[]>([])
const sessions = ref<any[]>([])
const loginKw = ref('')
const loginResult = ref<number | undefined>()
const operKw = ref('')
const operModule = ref('')
const detailVisible = ref(false)
const current = ref<any>(null)

function sanitizeText(raw?: string) {
  if (!raw) return ''
  let text = String(raw)
  text = text.replace(
    /("?(?:password|passwd|pwd|token|cookie|authorization|api[_-]?key|secret)"?\s*[:=]\s*")([^"]*)(")/gi,
    '$1******$3',
  )
  text = text.replace(
    /("?(?:password|passwd|pwd|token|cookie|authorization|api[_-]?key|secret)"?\s*[:=]\s*)([^\s,;|&}]+)/gi,
    '$1******',
  )
  if (SENSITIVE_RE.test(text) && text.length > 200) {
    SENSITIVE_RE.lastIndex = 0
    return text.replace(SENSITIVE_RE, '[filtered]')
  }
  SENSITIVE_RE.lastIndex = 0
  return text
}

function summarize(detail?: string) {
  const t = sanitizeText(detail).replace(/\s+/g, ' ').trim()
  if (!t) return '查看详情'
  return t.length > 48 ? t.slice(0, 48) + '…' : t
}

function maskPhone(phone?: string) {
  const p = String(phone || '')
  if (!p) return '—'
  if (p.includes('*')) return p
  if (p.length < 7) return p
  return p.slice(0, 3) + '****' + p.slice(-4)
}

function maskToken(token?: string) {
  const t = String(token || '')
  if (!t) return '—'
  if (t.length <= 10) return '********'
  return t.slice(0, 4) + '****' + t.slice(-4)
}

const filteredLogin = computed(() => {
  let rows = loginList.value
  const kw = loginKw.value.trim()
  if (kw) {
    rows = rows.filter(
      (r) => String(r.phone || '').includes(kw) || String(r.ip || '').includes(kw),
    )
  }
  if (loginResult.value === 0 || loginResult.value === 1) {
    rows = rows.filter((r) => Number(r.success) === loginResult.value)
  }
  return rows
})

const filteredOper = computed(() => {
  let rows = operList.value
  const kw = operKw.value.trim()
  const mod = operModule.value.trim()
  if (kw) {
    rows = rows.filter(
      (r) =>
        String(r.operatorName || '').includes(kw) ||
        String(r.module || '').includes(kw) ||
        String(r.ip || '').includes(kw) ||
        String(r.detail || '').includes(kw),
    )
  }
  if (mod) rows = rows.filter((r) => String(r.module || '').includes(mod))
  return rows
})

function filterLogin() {
  /* client-side on current page */
}

function filterOper() {
  /* client-side on current page */
}

function openDetail(row: any) {
  current.value = row
  detailVisible.value = true
}

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

async function onKick(row: any) {
  try {
    await ElMessageBox.confirm(`确认强制退出「${row.nickname || row.userId}」的该会话？`, '强制退出', {
      type: 'warning',
    })
  } catch {
    return
  }
  await kickSession(row.token)
  ElMessage.success('已踢下线')
  await loadSessions()
}

function onTabChange() {
  reloadTab()
}

function reloadTab() {
  if (tab.value === 'login') loadLogin()
  else if (tab.value === 'oper') loadOper()
  else if (tab.value === 'session') loadSessions()
}

watch(
  () => route.query.tab,
  (v) => {
    if (v === 'login' || v === 'oper' || v === 'session' || v === 'policy') tab.value = String(v)
  },
  { immediate: true },
)

watch(
  () => route.query.keyword,
  (v) => {
    if (v) {
      operKw.value = String(v)
      tab.value = 'oper'
    }
  },
  { immediate: true },
)

onMounted(reloadTab)
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
.icon-btn {
  width: 36px;
  padding: 8px;
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
.f-input {
  width: 200px;
  max-width: 100%;
}
.f-select {
  width: 140px;
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
.pill.danger {
  background: #fef3f2;
  color: #b42318;
}
.ops-link {
  color: #2563eb !important;
}
.pager {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
.mb {
  margin-bottom: 12px;
}
.policy-list {
  margin: 16px 0 0;
  padding-left: 18px;
  color: #667085;
  line-height: 1.9;
  font-size: 13px;
}
.detail-dl {
  display: grid;
  gap: 10px;
  margin: 0 0 16px;
}
.detail-dl div {
  display: grid;
  grid-template-columns: 88px 1fr;
  gap: 8px;
  font-size: 13px;
}
.detail-dl dt {
  color: #667085;
  margin: 0;
}
.detail-dl dd {
  margin: 0;
  color: #182230;
  word-break: break-all;
}
.sec {
  margin: 0 0 8px;
  font-size: 14px;
  color: #182230;
}
.body {
  margin: 0;
  padding: 12px;
  background: #f9fafb;
  border: 1px solid #e4e7ec;
  border-radius: 8px;
  white-space: pre-wrap;
  word-break: break-word;
  font-size: 12px;
  color: #182230;
  max-height: 360px;
  overflow: auto;
}
@media (max-width: 768px) {
  .saas-page {
    margin: -12px;
    padding: 16px;
  }
  .f-input,
  .f-select {
    width: 100%;
  }
}
</style>
