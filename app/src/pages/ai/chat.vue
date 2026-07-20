<template>
  <view class="ai-page">
    <!-- 居民：智能管家对话 -->
    <template v-if="!store.isStaff">
      <view class="hero-bar">
        <text class="hero-title">智能管家</text>
        <text class="hero-sub">咨询物业费、报修流程、公告活动</text>
      </view>
      <scroll-view scroll-y class="msg-list" :scroll-into-view="scrollInto" scroll-with-animation>
        <view v-if="!messages.length" class="empty-hint">
          <text>暂无对话内容</text>
          <text class="muted">向智能管家提问，例如「物业费怎么交」</text>
        </view>
        <view v-for="(m, i) in messages" :id="'m' + i" :key="i" class="bubble-row" :class="m.role">
          <view class="bubble">{{ m.content }}</view>
        </view>
      </scroll-view>
      <view class="composer">
        <input v-model="question" class="input" confirm-type="send" placeholder="向智能管家提问…" @confirm="send" />
        <button class="send" size="mini" :loading="loading" @click="send">发送</button>
      </view>
    </template>

    <!-- 物业：运维工具（禁止聊天提问） -->
    <scroll-view v-else scroll-y class="staff-scroll">
      <view class="hero-bar staff">
        <text class="hero-title">智能管家 · 物业运维</text>
        <text class="hero-sub">知识库 · 识图记录 · 公告AI · 咨询统计（不可对话）</text>
      </view>

      <view class="tabs">
        <view
          v-for="t in staffTabs"
          :key="t.key"
          class="tab"
          :class="{ on: staffTab === t.key }"
          @click="staffTab = t.key"
        >{{ t.name }}</view>
      </view>

      <!-- 知识库 CRUD -->
      <view v-if="staffTab === 'kb'" class="panel card">
        <view class="card-title">本社区知识库</view>
        <view class="field">
          <text class="label">问题</text>
          <input v-model="kbForm.question" class="field-input" placeholder="居民常问" />
        </view>
        <view class="field col">
          <text class="label">答案</text>
          <textarea v-model="kbForm.answer" class="field-area" placeholder="标准答复" />
        </view>
        <button class="btn-primary" :loading="kbLoading" @click="saveKbItem">保存知识</button>
        <view v-for="k in kbList" :key="k.id" class="kb-item">
          <view class="flex-1">
            <text class="kb-q">{{ k.question }}</text>
            <text class="muted ellipsis">{{ k.answer }}</text>
          </view>
          <button size="mini" class="btn-danger" @click="removeKb(k.id)">删</button>
        </view>
        <view v-if="!kbList.length" class="empty-hint sm">暂无内容</view>
      </view>

      <!-- 报修 AI 识图记录 -->
      <view v-if="staffTab === 'image_log'" class="panel card">
        <view class="card-title">报修 AI 识图记录</view>
        <view v-for="(log, i) in imageLogs" :key="i" class="log-row">
          <text class="log-t">{{ log.scene || 'image' }} · {{ log.success ? '成功' : '失败' }}</text>
          <text class="muted">{{ log.remark || '-' }}</text>
          <text class="muted">{{ log.createTime || '' }}</text>
        </view>
        <view v-if="!imageLogs.length" class="empty-hint sm">暂无识图记录</view>
      </view>

      <!-- 公告 AI -->
      <view v-if="staffTab === 'notice_ai'" class="panel card">
        <view class="card-title">公告 AI 摘要 / 敏感词</view>
        <textarea v-model="auditText" class="field-area tall" placeholder="粘贴待发布公告全文" />
        <button class="btn-primary" :loading="auditLoading" @click="runAudit">审核并摘要</button>
        <view v-if="auditResult" class="result-box">
          <text :class="auditResult.pass ? 'ok' : 'warn'">
            {{ auditResult.pass ? '审核通过' : '含敏感词，禁止发布' }}
          </text>
          <text>摘要：{{ auditResult.summary || '-' }}</text>
          <text v-if="auditResult.hitWords?.length">命中：{{ auditResult.hitWords.join('、') }}</text>
        </view>
      </view>

      <!-- 咨询统计 -->
      <view v-if="staffTab === 'stats'" class="panel card">
        <view class="card-title">业主咨询统计</view>
        <view class="stats-row">
          <view class="stat"><text class="n">{{ chatStat.today }}</text><text class="l">今日咨询</text></view>
          <view class="stat"><text class="n">{{ chatStat.total }}</text><text class="l">累计咨询</text></view>
          <view class="stat"><text class="n">{{ chatStat.transferHuman }}</text><text class="l">转人工</text></view>
        </view>
        <text class="muted tip">仅统计本社区，不含居民对话正文</text>
      </view>

      <view class="safe-bottom" />
    </scroll-view>
  </view>
</template>

<script setup>
import { ref, reactive, computed, nextTick, watch } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import {
  chat, listChatLogs, listKb, saveKb, deleteKb,
  auditNotice, listCallLogs, chatStats
} from '../../api/ai'
import { useUserStore } from '../../store/user'
import { resolveLoginTenantId } from '../../utils/request'
import { staffAiTabs } from '../../utils/roleAccess'

const store = useUserStore()
const question = ref('')
const loading = ref(false)
const messages = ref([])
const scrollInto = ref('')

const staffTabs = computed(() => staffAiTabs(store.role))
const staffTab = ref('kb')
watch(staffTabs, (tabs) => {
  if (tabs.length && !tabs.some((t) => t.key === staffTab.value)) staffTab.value = tabs[0].key
}, { immediate: true })

const kbList = ref([])
const kbForm = reactive({ question: '', answer: '' })
const kbLoading = ref(false)
const imageLogs = ref([])
const auditText = ref('')
const auditLoading = ref(false)
const auditResult = ref(null)
const chatStat = reactive({ today: 0, total: 0, transferHuman: 0 })

function ensureTenant() {
  const tid = resolveLoginTenantId()
  if (tid == null || tid === '') {
    uni.showToast({ title: '缺少社区信息，请重新登录', icon: 'none' })
    return null
  }
  return tid
}

onShow(async () => {
  if (!store.token) return uni.reLaunch({ url: '/pages/login/login' })
  if (!ensureTenant()) return
  if (store.isStaff) {
    messages.value = []
    await Promise.all([loadKb(), loadImageLogs(), loadChatStats()])
  } else {
    await loadChatLogs()
  }
})

async function loadChatLogs() {
  try {
    const logs = (await listChatLogs().catch(() => [])) || []
    const rows = []
    ;(Array.isArray(logs) ? logs : []).slice().reverse().forEach((log) => {
      if (log.question) rows.push({ role: 'user', content: log.question })
      if (log.answer) rows.push({ role: 'assistant', content: log.answer })
    })
    messages.value = rows
    scrollBottom()
  } catch (e) {
    messages.value = []
  }
}

async function send() {
  if (store.isStaff) return
  const q = question.value.trim()
  if (!q || loading.value) return
  if (!ensureTenant()) return
  messages.value.push({ role: 'user', content: q })
  question.value = ''
  loading.value = true
  scrollBottom()
  try {
    const data = await chat(q)
    const answer = typeof data === 'string' ? data : data?.answer || data?.content || '暂无回复'
    messages.value.push({ role: 'assistant', content: answer })
  } catch (e) {
    messages.value.push({ role: 'assistant', content: '暂时无法回答，已建议转人工物业处理。' })
  } finally {
    loading.value = false
    scrollBottom()
  }
}

function scrollBottom() {
  nextTick(() => {
    if (messages.value.length) scrollInto.value = 'm' + (messages.value.length - 1)
  })
}

async function loadKb() {
  kbList.value = (await listKb().catch(() => [])) || []
}

async function saveKbItem() {
  if (!kbForm.question || !kbForm.answer) {
    uni.showToast({ title: '请填写完整', icon: 'none' })
    return
  }
  kbLoading.value = true
  try {
    await saveKb({ ...kbForm })
    kbForm.question = ''
    kbForm.answer = ''
    uni.showToast({ title: '已保存', icon: 'success' })
    await loadKb()
  } finally {
    kbLoading.value = false
  }
}

async function removeKb(id) {
  await deleteKb(id)
  await loadKb()
}

async function loadImageLogs() {
  imageLogs.value = (await listCallLogs('image').catch(() => [])) || []
}

async function runAudit() {
  if (!auditText.value.trim()) {
    uni.showToast({ title: '请输入公告内容', icon: 'none' })
    return
  }
  auditLoading.value = true
  try {
    auditResult.value = (await auditNotice(auditText.value.trim())) || null
  } catch (e) {
    auditResult.value = null
  } finally {
    auditLoading.value = false
  }
}

async function loadChatStats() {
  const data = (await chatStats().catch(() => null)) || {}
  chatStat.today = data.today || 0
  chatStat.total = data.total || 0
  chatStat.transferHuman = data.transferHuman || 0
}
</script>

<style lang="scss" scoped>
$mint: #47b686;
.ai-page { display: flex; flex-direction: column; height: 100vh; background: #e8f5f2; }
.hero-bar {
  padding: 28rpx 32rpx 20rpx;
  background: linear-gradient(160deg, #6ed3a8, $mint); color: #fff;
}
.hero-title { font-size: 36rpx; font-weight: 700; display: block; }
.hero-sub { margin-top: 8rpx; font-size: 24rpx; opacity: .9; display: block; }
.msg-list { flex: 1; padding: 24rpx 28rpx; box-sizing: border-box; }
.empty-hint {
  text-align: center; color: #1f2a28; padding: 80rpx 40rpx; font-size: 28rpx;
  display: flex; flex-direction: column; gap: 12rpx;
}
.empty-hint.sm { padding: 40rpx 16rpx; font-size: 26rpx; color: #8a9693; }
.muted { color: #8a9693; font-size: 24rpx; }
.bubble-row { display: flex; margin-bottom: 20rpx; }
.bubble-row.user { justify-content: flex-end; }
.bubble {
  max-width: 78%; padding: 22rpx 26rpx; border-radius: 20rpx;
  background: #fff; font-size: 28rpx; color: #1f2a28; line-height: 1.5;
}
.bubble-row.user .bubble { background: $mint; color: #fff; }
.composer {
  display: flex; gap: 16rpx; padding: 16rpx 24rpx calc(16rpx + env(safe-area-inset-bottom));
  background: #fff; border-top: 1rpx solid #e8eeeb;
}
.input { flex: 1; background: #f5f7f6; border-radius: 999rpx; padding: 16rpx 28rpx; font-size: 28rpx; }
.send { background: $mint; color: #fff; border: none; }
.staff-scroll { flex: 1; height: 100vh; }
.tabs { display: flex; gap: 12rpx; padding: 20rpx 24rpx; flex-wrap: wrap; }
.tab {
  padding: 12rpx 22rpx; border-radius: 999rpx; background: #fff;
  font-size: 24rpx; color: #5a6562;
}
.tab.on { background: $mint; color: #fff; font-weight: 600; }
.panel { margin: 0 24rpx 24rpx; }
.card {
  background: #fff; border-radius: 24rpx; padding: 28rpx;
  box-shadow: 0 8rpx 24rpx rgba(31,42,40,.04);
}
.card-title { font-size: 30rpx; font-weight: 700; margin-bottom: 20rpx; }
.field { margin-bottom: 16rpx; }
.field.col { display: flex; flex-direction: column; gap: 10rpx; }
.label { font-size: 24rpx; color: #5a6562; font-weight: 600; }
.field-input, .field-area {
  margin-top: 8rpx; background: #f5f7f6; border-radius: 12rpx; padding: 18rpx; width: 100%; box-sizing: border-box;
}
.field-area { min-height: 120rpx; }
.field-area.tall { min-height: 200rpx; }
.btn-primary {
  margin-top: 12rpx; background: $mint; color: #fff; border: none; border-radius: 999rpx;
}
.kb-item {
  display: flex; gap: 12rpx; align-items: center; padding: 20rpx 0;
  border-bottom: 1rpx solid #f0f3f2;
}
.kb-q { display: block; font-weight: 600; font-size: 28rpx; }
.ellipsis {
  display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; max-width: 480rpx;
}
.btn-danger { background: #ffe8e6; color: #e85d4c; border: none; }
.log-row { padding: 16rpx 0; border-bottom: 1rpx solid #f0f3f2; }
.log-t { display: block; font-weight: 600; font-size: 28rpx; }
.result-box { margin-top: 20rpx; display: flex; flex-direction: column; gap: 8rpx; font-size: 26rpx; }
.ok { color: $mint; font-weight: 700; }
.warn { color: #e85d4c; font-weight: 700; }
.stats-row { display: flex; justify-content: space-between; margin: 12rpx 0 20rpx; }
.stat { flex: 1; text-align: center; }
.n { display: block; font-size: 40rpx; font-weight: 700; color: $mint; }
.l { font-size: 22rpx; color: #8a9693; }
.tip { display: block; margin-top: 8rpx; }
.safe-bottom { height: 120rpx; }
.flex-1 { flex: 1; min-width: 0; }
</style>
