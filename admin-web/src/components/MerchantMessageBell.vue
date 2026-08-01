<template>
  <div class="msg-bell">
    <el-popover
      v-model:visible="panelOpen"
      placement="bottom-end"
      :width="380"
      trigger="click"
      popper-class="merchant-msg-popper"
      @show="load"
    >
      <template #reference>
        <button class="bell-btn" type="button" aria-label="消息通知">
          <el-badge :value="unreadCount" :hidden="unreadCount <= 0" :max="99" class="bell-badge">
            <el-icon :size="20"><Bell /></el-icon>
          </el-badge>
        </button>
      </template>

      <div class="msg-panel">
        <div class="panel-hd">
          <span>消息通知</span>
          <el-tag v-if="unreadCount > 0" size="small" type="danger" effect="plain">
            {{ unreadCount }} 未读
          </el-tag>
        </div>
        <div v-loading="loading" class="panel-body">
          <div v-if="!messages.length" class="empty">暂无订阅消息</div>
          <button
            v-for="m in messages"
            :key="m.id"
            type="button"
            class="msg-item"
            :class="{ unread: isUnread(m) }"
            @click="onClickMsg(m)"
          >
            <div class="msg-top">
              <el-tag size="small" :type="tagType(m.msgType)" effect="light">
                {{ m.typeLabel || typeLabel(m.msgType) }}
              </el-tag>
              <span class="time">{{ formatTime(m.createTime) }}</span>
            </div>
            <div class="title">{{ m.title }}</div>
            <div class="content">{{ m.content }}</div>
          </button>
        </div>
        <div class="panel-ft">
          <el-button
            type="primary"
            plain
            size="small"
            :disabled="!unreadCount"
            :loading="marking"
            @click="markAll"
          >
            全部标为已读
          </el-button>
        </div>
      </div>
    </el-popover>

    <el-dialog
      v-model="noticeVisible"
      :title="noticeTitle"
      width="480px"
      append-to-body
      destroy-on-close
    >
      <div class="notice-body">{{ noticeContent }}</div>
      <template #footer>
        <el-button type="primary" @click="noticeVisible = false">知道了</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Bell } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { listMessagesApi, readMessageApi } from '@/api/auth'

const MERCHANT_TYPES = new Set(['store_order', 'store_order_cancel', 'platform_notice'])

const router = useRouter()
const loading = ref(false)
const marking = ref(false)
const panelOpen = ref(false)
const list = ref<any[]>([])
const noticeVisible = ref(false)
const noticeTitle = ref('')
const noticeContent = ref('')

let timer: ReturnType<typeof setInterval> | null = null

const messages = computed(() =>
  list.value.filter((m) => MERCHANT_TYPES.has(String(m.msgType || ''))),
)

function isUnread(m: any) {
  return m.unread === true || m.readFlag === 0 || m.readFlag == null
}

const unreadCount = computed(() => messages.value.filter(isUnread).length)

function typeLabel(t: string) {
  return (
    {
      store_order: '新订单通知',
      store_order_cancel: '订单取消提醒',
      platform_notice: '平台公告',
    } as Record<string, string>
  )[t] || '系统消息'
}

function tagType(t: string): 'success' | 'warning' | 'info' | 'primary' | 'danger' {
  if (t === 'store_order') return 'success'
  if (t === 'store_order_cancel') return 'warning'
  if (t === 'platform_notice') return 'primary'
  return 'info'
}

function formatTime(v: any) {
  if (!v) return ''
  const s = String(v).replace('T', ' ')
  return s.length >= 16 ? s.slice(0, 16) : s
}

async function load() {
  loading.value = true
  try {
    const rows = (await listMessagesApi()) || []
    list.value = rows.map((m) => ({
      ...m,
      unread: m.unread === true || m.readFlag === 0 || m.readFlag == null,
    }))
  } catch {
    list.value = []
  } finally {
    loading.value = false
  }
}

async function markReadLocal(m: any) {
  if (!m?.id || !isUnread(m)) return
  try {
    await readMessageApi(Number(m.id))
    m.unread = false
    m.readFlag = 1
  } catch {
    /* ignore */
  }
}

async function onClickMsg(m: any) {
  await markReadLocal(m)
  panelOpen.value = false
  if (m.msgType === 'platform_notice') {
    noticeTitle.value = m.title || '平台公告'
    noticeContent.value = m.content || ''
    noticeVisible.value = true
    return
  }
  router.push('/merchant/orders')
}

async function markAll() {
  marking.value = true
  try {
    const unread = messages.value.filter(isUnread)
    await Promise.all(unread.map((m) => readMessageApi(Number(m.id)).catch(() => null)))
    unread.forEach((m) => {
      m.unread = false
      m.readFlag = 1
    })
    ElMessage.success('已全部标为已读')
  } finally {
    marking.value = false
  }
}

onMounted(() => {
  load()
  timer = setInterval(load, 30000)
})

onUnmounted(() => {
  if (timer) clearInterval(timer)
})
</script>

<style scoped>
.msg-bell { display: inline-flex; align-items: center; }
.bell-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border: none;
  border-radius: 10px;
  background: #f0fdfa;
  color: #0f766e;
  cursor: pointer;
  transition: background .15s;
}
.bell-btn:hover { background: #ccfbf1; }
.bell-badge :deep(.el-badge__content) {
  background: #ef4444;
  border: none;
  transform: translateY(-2px) translateX(2px);
}

.msg-panel { margin: -4px -8px; }
.panel-hd {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 4px 8px 10px;
  font-weight: 650;
  color: #0f766e;
  border-bottom: 1px solid #e7efed;
}
.panel-body {
  max-height: 360px;
  overflow: auto;
  padding: 6px 0;
  min-height: 80px;
}
.empty {
  padding: 28px 12px;
  text-align: center;
  color: #94a3b8;
  font-size: 13px;
}
.msg-item {
  display: block;
  width: 100%;
  text-align: left;
  border: none;
  background: transparent;
  padding: 10px 10px;
  border-radius: 8px;
  cursor: pointer;
  transition: background .15s;
}
.msg-item:hover { background: #f0fdfa; }
.msg-item.unread {
  background: #f0fdfa;
  box-shadow: inset 3px 0 0 #14b8a6;
}
.msg-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 4px;
}
.time { color: #94a3b8; font-size: 12px; flex-shrink: 0; }
.title {
  font-size: 13px;
  font-weight: 600;
  color: #0f172a;
  margin-bottom: 2px;
  line-height: 1.4;
}
.content {
  font-size: 12px;
  color: #64748b;
  line-height: 1.45;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.panel-ft {
  display: flex;
  justify-content: flex-end;
  padding: 10px 8px 2px;
  border-top: 1px solid #e7efed;
}
.notice-body {
  white-space: pre-wrap;
  line-height: 1.7;
  color: #334155;
  font-size: 14px;
}
</style>
