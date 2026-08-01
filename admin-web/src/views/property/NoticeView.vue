<template>
  <div class="page-card">
    <div class="page-header">
      <div>
        <h2>公告通知</h2>
        <p class="sub">AI 审核 + 摘要 + 画像定向推送；到点后居民 App 自动可见</p>
      </div>
      <div class="actions">
        <el-button :loading="loading" @click="load">刷新</el-button>
        <el-button type="primary" :disabled="!canPublish" @click="openCreate">发布公告</el-button>
      </div>
    </div>

    <el-alert
      v-if="!user.hasRealToken"
      type="warning"
      :closable="false"
      show-icon
      title="请使用物业真实账号登录（如 13800000001 / 123456）后再发布公告"
      style="margin-bottom: 12px"
    />

    <el-table v-loading="loading" :data="list" stripe empty-text="暂无公告">
      <el-table-column prop="title" label="标题" min-width="140" show-overflow-tooltip />
      <el-table-column prop="summary" label="AI摘要" min-width="180" show-overflow-tooltip />
      <el-table-column prop="targetRule" label="定向规则" width="120" show-overflow-tooltip />
      <el-table-column prop="pushCount" label="推送数" width="80" />
      <el-table-column prop="publishTime" label="发布时间" width="170" />
      <el-table-column prop="publishState" label="状态" width="110">
        <template #default="{ row }">
          <el-tag :type="stateTag(row.publishState)" size="small">{{ row.publishState || '-' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="280" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button link type="success" @click="doPush(row)">定向推送</el-button>
          <el-button link @click="showPushLogs(row)">推送记录</el-button>
          <el-button
            v-if="row.status === 1"
            link
            type="warning"
            @click="toggleStatus(row, 0)"
          >
            下架
          </el-button>
          <el-button v-else link type="success" @click="toggleStatus(row, 1)">上架</el-button>
          <el-button link type="danger" @click="onDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog
      v-model="visible"
      :title="editing ? '编辑公告' : '发布公告'"
      width="620px"
      destroy-on-close
    >
      <el-form :model="form" label-width="100px">
        <el-form-item label="标题" required>
          <el-input v-model="form.title" maxlength="64" placeholder="公告标题" />
        </el-form-item>
        <el-form-item label="正文" required>
          <el-input
            v-model="form.content"
            type="textarea"
            :rows="6"
            maxlength="2000"
            placeholder="公告正文"
          />
        </el-form-item>
        <el-form-item label="AI摘要">
          <el-input v-model="form.summary" maxlength="100" placeholder="点击下方按钮自动生成（≤100字）" />
        </el-form-item>
        <el-form-item label="定向规则">
          <el-select v-model="form.targetRule" clearable placeholder="不选则全员" style="width: 100%">
            <el-option label="全员" value="all" />
            <el-option label="老年住户" value="senior" />
            <el-option label="有孩家庭" value="hasChild" />
            <el-option label="亲子活动兴趣" value="亲子" />
          </el-select>
        </el-form-item>
        <el-form-item label="发布方式">
          <el-radio-group v-model="form.mode">
            <el-radio value="now">立即发布</el-radio>
            <el-radio value="schedule">定时发布</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="form.mode === 'schedule'" label="发布时间" required>
          <el-date-picker
            v-model="form.publishTime"
            type="datetime"
            value-format="YYYY-MM-DDTHH:mm:ss"
            format="YYYY-MM-DD HH:mm"
            placeholder="选择发布时间"
            style="width: 100%"
            :disabled-date="disablePastDate"
          />
        </el-form-item>
        <p class="tip">发布前请先「AI 审核+生成摘要」；审核不通过将禁止发布。</p>
      </el-form>
      <template #footer>
        <el-button :loading="auditing" @click="runAudit">AI 审核+生成摘要</el-button>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" :loading="saving" :disabled="!auditOk && !editing" @click="save">
          保存
        </el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="pushVisible" title="推送记录" size="420px">
      <el-table :data="pushLogs" size="small" empty-text="暂无推送">
        <el-table-column prop="userId" label="用户" width="80" />
        <el-table-column prop="reason" label="规则" min-width="120" show-overflow-tooltip />
        <el-table-column prop="createTime" label="时间" width="160" />
      </el-table>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/stores/user'
import {
  createNotice,
  deleteNotice,
  listNotices,
  updateNotice,
  type NoticeInfo,
} from '@/api/notice'
import { auditNoticeContent, listNoticePushLogs, targetPushNotice } from '@/api/aiFeature'

const user = useUserStore()
const loading = ref(false)
const saving = ref(false)
const auditing = ref(false)
const visible = ref(false)
const auditOk = ref(false)
const editing = ref<NoticeInfo | null>(null)
const list = ref<NoticeInfo[]>([])
const pushVisible = ref(false)
const pushLogs = ref<any[]>([])

const canPublish = computed(() => user.hasRealToken && user.role === 'property_admin')

const form = reactive({
  title: '',
  content: '',
  summary: '',
  targetRule: '' as string,
  mode: 'now' as 'now' | 'schedule',
  publishTime: '' as string,
})

function stateTag(s?: string) {
  if (s === '已发布') return 'success'
  if (s === '定时待发') return 'warning'
  return 'info'
}

function disablePastDate(date: Date) {
  const today = new Date()
  today.setHours(0, 0, 0, 0)
  return date.getTime() < today.getTime()
}

function pad(n: number) {
  return n < 10 ? `0${n}` : `${n}`
}

function nowLocalIso() {
  const t = new Date()
  return `${t.getFullYear()}-${pad(t.getMonth() + 1)}-${pad(t.getDate())}T${pad(t.getHours())}:${pad(t.getMinutes())}:${pad(t.getSeconds())}`
}

async function load() {
  if (!user.hasRealToken) {
    list.value = []
    return
  }
  loading.value = true
  try {
    list.value = (await listNotices(true)) || []
  } catch {
    list.value = []
  } finally {
    loading.value = false
  }
}

function openCreate() {
  if (!canPublish.value) {
    ElMessage.warning('仅物业管理员可发布公告')
    return
  }
  editing.value = null
  form.title = ''
  form.content = ''
  form.summary = ''
  form.targetRule = ''
  form.mode = 'now'
  form.publishTime = ''
  auditOk.value = false
  visible.value = true
}

function openEdit(row: NoticeInfo) {
  editing.value = row
  form.title = row.title || ''
  form.content = row.content || ''
  form.summary = row.summary || ''
  form.targetRule = (row as any).targetRule || ''
  const pt = row.publishTime || ''
  const future = pt && new Date(pt).getTime() > Date.now()
  form.mode = future ? 'schedule' : 'now'
  form.publishTime = pt ? String(pt).replace(' ', 'T').slice(0, 19) : ''
  auditOk.value = true
  visible.value = true
}

async function runAudit() {
  const text = `${form.title}\n${form.content}`.trim()
  if (!text) {
    ElMessage.error('请先填写标题和正文')
    return
  }
  auditing.value = true
  try {
    const res = await auditNoticeContent(text)
    if (!res?.pass) {
      auditOk.value = false
      ElMessage.error('审核不通过：' + (res?.hitWords || []).join(','))
      return
    }
    form.summary = (res.summary || '').slice(0, 100)
    auditOk.value = true
    ElMessage.success('审核通过，已生成摘要')
  } catch {
    auditOk.value = false
  } finally {
    auditing.value = false
  }
}

async function save() {
  if (!form.title.trim() || !form.content.trim()) {
    ElMessage.error('请填写标题和正文')
    return
  }
  if (!editing.value && !auditOk.value) {
    ElMessage.error('请先通过 AI 审核')
    return
  }
  let publishTime = nowLocalIso()
  if (form.mode === 'schedule') {
    if (!form.publishTime) {
      ElMessage.error('请选择定时发布时间')
      return
    }
    publishTime = form.publishTime.length === 16 ? `${form.publishTime}:00` : form.publishTime
    if (new Date(publishTime).getTime() <= Date.now()) {
      ElMessage.error('定时发布时间需晚于当前时间')
      return
    }
  }
  saving.value = true
  try {
    const body: any = {
      title: form.title.trim(),
      content: form.content.trim(),
      summary: form.summary.trim() || undefined,
      targetRule: form.targetRule || undefined,
      publishTime,
      status: 1,
    }
    if (editing.value?.id) {
      await updateNotice(editing.value.id, body)
      ElMessage.success('已更新')
    } else {
      const created = await createNotice(body)
      ElMessage.success(form.mode === 'schedule' ? '已创建定时公告' : '已发布')
      if (created?.id && form.targetRule) {
        try {
          const push = await targetPushNotice(created.id, form.targetRule)
          ElMessage.success(`定向推送 ${push?.pushed || 0} 人`)
        } catch {
          /* ignore */
        }
      }
    }
    visible.value = false
    await load()
  } finally {
    saving.value = false
  }
}

async function doPush(row: NoticeInfo) {
  if (!row.id) return
  const rule = (row as any).targetRule || 'all'
  const res = await targetPushNotice(row.id, rule)
  ElMessage.success(`已推送 ${res?.pushed || 0} 人`)
  await load()
}

async function showPushLogs(row: NoticeInfo) {
  if (!row.id) return
  pushLogs.value = (await listNoticePushLogs(row.id, 80)) || []
  pushVisible.value = true
}

async function toggleStatus(row: NoticeInfo, status: number) {
  if (!row.id) return
  await updateNotice(row.id, { status })
  ElMessage.success(status === 1 ? '已上架' : '已下架')
  await load()
}

async function onDelete(row: NoticeInfo) {
  if (!row.id) return
  try {
    await ElMessageBox.confirm(`确认删除公告「${row.title}」？`, '删除确认', { type: 'warning' })
  } catch {
    return
  }
  await deleteNotice(row.id)
  ElMessage.success('已删除')
  await load()
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
.tip {
  margin: 0 0 0 100px;
  font-size: 12px;
  color: #94a3b8;
  line-height: 1.5;
}
</style>
