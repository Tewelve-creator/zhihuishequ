<template>
  <view class="ai-page">
    <!-- 居民 / 物业共用：对话式智能管家 -->
    <view class="chat-shell">
      <scroll-view
        scroll-y
        class="chat-scroll"
        :scroll-into-view="scrollInto"
        scroll-with-animation
      >
        <view class="welcome">
          <view class="hello-card">
            <image class="avatar-img" :src="butlerAvatar" mode="aspectFill" />
            <view class="hello-body">
              <text class="hello-hi">哈喽~ 我是「小智」</text>
              <text class="hello-desc">可问制度规定，也可直接办事：访客预约、报事报修、查欠费、查车辆。办理前都会请您确认。</text>
            </view>
          </view>
          <text class="chips-label">试试这样问</text>
          <view class="chips">
            <view
              v-for="c in quickChips"
              :key="c"
              class="chip"
              @click="sendQuick(c)"
            >{{ c }}</view>
          </view>
        </view>

        <view v-if="messages.length" class="time-sep">{{ todayLabel }}</view>

        <view
          v-for="(m, i) in messages"
          :id="'m' + i"
          :key="i"
          class="bubble-row"
          :class="m.role"
        >
          <image
            v-if="m.role === 'assistant'"
            class="mini-avatar"
            :src="butlerAvatar"
            mode="aspectFill"
          />
          <view class="bubble-wrap">
            <view class="bubble">{{ m.content }}</view>
            <view v-if="m.needPhoto && !m.done" class="photo-card">
              <button class="btn-photo" size="mini" :disabled="loading" @click="onUploadRepairPhoto(m)">
                拍照上传
              </button>
              <button class="btn-skip" size="mini" :disabled="loading" @click="sendSkipPhoto">跳过</button>
            </view>
            <view v-if="m.needConfirm && m.actionToken && !m.done" class="confirm-card">
              <view v-if="m.preview?.length" class="preview">
                <view v-for="(p, pi) in m.preview" :key="pi" class="preview-row">
                  <text class="pl">{{ p.label }}</text>
                  <text class="pv">{{ p.value }}</text>
                </view>
              </view>
              <view class="confirm-btns">
                <button class="btn-cancel" size="mini" :disabled="loading" @click="onCancel(m)">取消</button>
                <button
                  class="btn-ok"
                  size="mini"
                  :disabled="loading"
                  :loading="loading && pendingToken === m.actionToken"
                  @click="onConfirm(m)"
                >确认办理</button>
              </view>
            </view>
            <!-- 访客预约成功：业主转发通行链接（链接二维码，非门岗短时码） -->
            <view v-if="m.passUrl" class="pass-card">
              <image class="pass-qr" :src="linkQrSrc(m.passUrl)" mode="aspectFit" />
              <text class="pass-tip">扫码打开通行页 · 转发给访客</text>
              <text class="pass-link" @longpress="copyPass(m.passUrl)">{{ m.passUrl }}</text>
              <view class="pass-actions">
                <button class="btn-copy" size="mini" @click="copyPass(m.passUrl)">复制链接</button>
                <button
                  v-if="m.passToken"
                  class="btn-open"
                  size="mini"
                  @click="goVisitShare(m.passToken)"
                >打开分享页</button>
              </view>
            </view>
          </view>
        </view>

        <view v-if="loading" id="m-loading" class="bubble-row assistant">
          <image class="mini-avatar" :src="butlerAvatar" mode="aspectFill" />
          <view class="bubble typing">正在思考…</view>
        </view>

        <view class="scroll-pad" />
      </scroll-view>

      <view class="composer">
        <view class="composer-inner">
          <input
            v-model="question"
            class="input"
            type="text"
            confirm-type="send"
            placeholder="开始探索社区问题吧"
            :disabled="loading"
            @confirm="send"
          />
          <view class="send-btn" :class="{ on: !!question.trim() && !loading }" @click="send">
            <text class="send-ico">发送</text>
          </view>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref, computed, nextTick } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { chat, listChatLogs, confirmBiz, cancelBiz } from '../../api/ai'
import { uploadFile, resolveLoginTenantId } from '../../utils/request'
import { useUserStore } from '../../store/user'

const store = useUserStore()
const question = ref('')
const loading = ref(false)
const messages = ref([])
const scrollInto = ref('')
const pendingToken = ref('')
const butlerAvatar = '/static/ai/butler-avatar.png'

const quickChips = [
  '你好',
  '我要报修',
  '物业费怎么交',
  '查一下欠费',
  '帮我登记访客',
  '查一下车辆'
]

const todayLabel = computed(() => {
  const d = new Date()
  return `${d.getMonth() + 1}月${d.getDate()}日 ${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
})

function ensureTenant() {
  const tid = resolveLoginTenantId()
  if (tid == null || tid === '') {
    uni.showToast({ title: '缺少社区信息，请重新登录', icon: 'none' })
    return null
  }
  return tid
}

function pushAssistant(data) {
  const answer = typeof data === 'string' ? data : data?.answer || data?.content || '暂无回复'
  const missing = Array.isArray(data?.missingFields) ? data.missingFields : []
  const needPhoto = data?.mode === 'BIZ_ASK' && missing.includes('imageUrl')
  const payload = data && typeof data === 'object' ? data.data : null
  const visitPass = extractVisitPass(data?.intent, payload)
  messages.value.push({
    role: 'assistant',
    content: answer,
    needConfirm: !!data?.needConfirm,
    needPhoto,
    actionToken: data?.actionToken || '',
    preview: data?.preview || [],
    intent: data?.intent || '',
    logId: data?.logId || null,
    passUrl: visitPass.passUrl,
    passToken: visitPass.passToken,
    done: false
  })
}

/** 从业务结果里抽出访客通行链接（业主转发用） */
function extractVisitPass(intent, payload) {
  if (intent !== 'VISIT_CREATE' || !payload || typeof payload !== 'object') {
    return { passUrl: '', passToken: '' }
  }
  const token = payload.passToken || ''
  let url = payload.passUrl || ''
  if ((!url || url.startsWith('/')) && token) {
    url = buildPassPageUrl(token)
  } else if (url && url.startsWith('/#')) {
    // #ifdef H5
    url = `${window.location.origin}${window.location.pathname || '/'}${url.replace(/^\//, '')}`
    // #endif
  }
  return { passUrl: url || '', passToken: token || '' }
}

function buildPassPageUrl(token) {
  // #ifdef H5
  return `${window.location.origin}${window.location.pathname || '/'}#/pages/visitor/pass?token=${encodeURIComponent(token)}`
  // #endif
  // #ifndef H5
  return `http://localhost:5180/#/pages/visitor/pass?token=${encodeURIComponent(token)}`
  // #endif
}

function linkQrSrc(url) {
  if (!url) return ''
  return `https://api.qrserver.com/v1/create-qr-code/?size=220x220&data=${encodeURIComponent(url)}`
}

function copyPass(url) {
  if (!url) return
  uni.setClipboardData({
    data: url,
    success: () => uni.showToast({ title: '链接已复制', icon: 'success' })
  })
}

function goVisitShare(token) {
  if (!token) return
  uni.navigateTo({ url: `/pages/visitor/share?token=${encodeURIComponent(token)}` })
}

function sendQuick(text) {
  question.value = text
  send()
}

onShow(async () => {
  if (!store.token) return uni.reLaunch({ url: '/pages/login/login' })
  if (!store.guardFeature('ai_chat')) {
    uni.switchTab({ url: '/pages/home/index' })
    return
  }
  if (!ensureTenant()) return
  await loadChatLogs()
})

async function loadChatLogs() {
  try {
    const logs = (await listChatLogs().catch(() => [])) || []
    const rows = []
    ;(Array.isArray(logs) ? logs : []).slice().reverse().forEach((log) => {
      if (log.question) rows.push({ role: 'user', content: log.question })
      if (log.answer) {
        rows.push({
          role: 'assistant',
          content: log.answer,
          needConfirm: false,
          logId: log.id
        })
      }
    })
    messages.value = rows
    scrollBottom()
  } catch (e) {
    messages.value = []
  }
}

async function send() {
  const q = question.value.trim()
  if (!q || loading.value) return
  if (!ensureTenant()) return
  messages.value.forEach((m) => {
    if (m.needPhoto) m.done = true
  })
  messages.value.push({ role: 'user', content: q })
  question.value = ''
  loading.value = true
  scrollBottom()
  try {
    const data = await chat(q)
    pushAssistant(data)
  } catch (e) {
    messages.value.push({ role: 'assistant', content: '暂时无法回答，请稍后重试。' })
  } finally {
    loading.value = false
    scrollBottom()
  }
}

function sendSkipPhoto() {
  question.value = '跳过'
  send()
}

async function onUploadRepairPhoto(m) {
  if (loading.value) return
  uni.chooseImage({
    count: 1,
    sizeType: ['compressed'],
    sourceType: ['camera', 'album'],
    success: async (res) => {
      const path = res.tempFilePaths[0]
      uni.showLoading({ title: '上传中' })
      try {
        const url = await uploadFile(path)
        if (m) m.done = true
        uni.hideLoading()
        messages.value.push({ role: 'user', content: '[已上传现场照片]', imageUrl: url })
        loading.value = true
        scrollBottom()
        try {
          const data = await chat(url)
          pushAssistant(data)
        } catch (e) {
          messages.value.push({ role: 'assistant', content: '照片已上传，但提交失败，请重试或回复「跳过」。' })
        } finally {
          loading.value = false
          scrollBottom()
        }
      } catch (e) {
        uni.hideLoading()
        uni.showToast({ title: '上传失败', icon: 'none' })
      }
    }
  })
}

async function onConfirm(m) {
  if (!m?.actionToken || m.done || loading.value) return
  uni.showModal({
    title: '确认办理',
    content: '确认后将提交业务，是否继续？',
    success: async (res) => {
      if (!res.confirm) return
      loading.value = true
      pendingToken.value = m.actionToken
      try {
        const data = await confirmBiz(m.actionToken)
        m.done = true
        m.needConfirm = false
        pushAssistant(data)
      } catch (e) {
        messages.value.push({ role: 'assistant', content: '办理失败，请稍后重试或前往对应业务页操作。' })
      } finally {
        loading.value = false
        pendingToken.value = ''
        scrollBottom()
      }
    }
  })
}

async function onCancel(m) {
  if (m.done || loading.value) return
  loading.value = true
  try {
    const data = await cancelBiz(m.actionToken)
    m.done = true
    m.needConfirm = false
    pushAssistant(data)
  } catch (e) {
    m.done = true
    m.needConfirm = false
    messages.value.push({ role: 'assistant', content: '已取消本次操作。' })
  } finally {
    loading.value = false
    scrollBottom()
  }
}

function scrollBottom() {
  nextTick(() => {
    if (loading.value) {
      scrollInto.value = 'm-loading'
    } else if (messages.value.length) {
      scrollInto.value = 'm' + (messages.value.length - 1)
    }
  })
}
</script>

<style lang="scss" scoped>
$lime: #c6f23a;
$lime-deep: #a8d92a;
$ink: #1a1f1c;
$bubble-ai: #2b2f2d;
$bg: #f3f6f4;

/* 勿用 absolute top:0，H5 预览框下会顶进导航栏，欢迎卡顶部会被裁切 */
.ai-page {
  position: relative;
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  background: $bg;
  box-sizing: border-box;
  overflow: hidden;
}

.chat-shell {
  width: 100%;
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  box-sizing: border-box;
}

.chat-scroll {
  flex: 1;
  height: 0;
  width: 100%;
  padding: 20rpx 28rpx 0;
  box-sizing: border-box;
}

.welcome {
  background: #fff;
  border-radius: 28rpx;
  padding: 20rpx;
  margin-bottom: 24rpx;
  box-shadow: 0 8rpx 24rpx rgba(26, 31, 28, 0.04);
  box-sizing: border-box;
}
.hello-card {
  display: flex;
  flex-direction: row;
  align-items: center;
  gap: 18rpx;
  padding: 20rpx;
  border-radius: 22rpx;
  background: linear-gradient(145deg, #e9f9c8 0%, #d4f08a 100%);
  box-sizing: border-box;
}
.avatar-img {
  width: 96rpx;
  height: 96rpx;
  border-radius: 28rpx;
  flex-shrink: 0;
  background: #fff;
  box-shadow: 0 6rpx 16rpx rgba(0, 0, 0, 0.08);
}
.hello-body {
  flex: 1;
  min-width: 0;
}
.hello-hi {
  display: block;
  font-size: 28rpx;
  font-weight: 800;
  color: $ink;
  margin-bottom: 8rpx;
  line-height: 1.35;
}
.hello-desc {
  display: block;
  font-size: 22rpx;
  line-height: 1.55;
  color: #3d4a38;
  word-break: break-word;
}

.chips-label {
  display: block;
  margin: 20rpx 4rpx 12rpx;
  font-size: 22rpx;
  color: #8a948c;
  line-height: 1.2;
}
.chips {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  row-gap: 14rpx;
}
.chip {
  width: calc(50% - 7rpx);
  box-sizing: border-box;
  padding: 18rpx 12rpx;
  border-radius: 16rpx;
  background: #f7faf7;
  font-size: 24rpx;
  color: #3d4a38;
  border: 1rpx solid #e6eee8;
  line-height: 1.3;
  text-align: center;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.time-sep {
  text-align: center;
  font-size: 22rpx;
  color: #9aa39e;
  margin: 8rpx 0 24rpx;
}

.bubble-row {
  display: flex;
  align-items: flex-end;
  gap: 12rpx;
  margin-bottom: 24rpx;
  width: 100%;
  box-sizing: border-box;
}
.bubble-row.user { justify-content: flex-end; }
.bubble-row.assistant { justify-content: flex-start; }

.mini-avatar {
  width: 64rpx;
  height: 64rpx;
  border-radius: 20rpx;
  flex-shrink: 0;
  background: #e9f9c8;
  margin-bottom: 4rpx;
}

.bubble-wrap {
  max-width: calc(100% - 88rpx);
  min-width: 0;
}
.bubble-row.user .bubble-wrap { max-width: 78%; }
.bubble {
  padding: 22rpx 26rpx;
  border-radius: 24rpx;
  font-size: 28rpx;
  line-height: 1.55;
  white-space: pre-wrap;
  word-break: break-word;
  box-sizing: border-box;
}
.bubble-row.user .bubble {
  background: $lime;
  color: $ink;
  border-bottom-right-radius: 8rpx;
  font-weight: 500;
}
.bubble-row.assistant .bubble {
  background: $bubble-ai;
  color: #f5f7f6;
  border-bottom-left-radius: 8rpx;
}
.bubble.typing { opacity: 0.85; font-size: 26rpx; }

.pass-card {
  margin-top: 12rpx;
  background: #fff;
  border-radius: 20rpx;
  padding: 20rpx;
  border: 2rpx solid #dcefc0;
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  align-items: center;
}
.pass-qr {
  width: 280rpx;
  height: 280rpx;
  background: #f7faf7;
  border-radius: 12rpx;
}
.pass-tip {
  margin-top: 12rpx;
  font-size: 22rpx;
  color: #5a6562;
}
.pass-link {
  margin-top: 10rpx;
  width: 100%;
  font-size: 20rpx;
  color: #3d4a38;
  word-break: break-all;
  line-height: 1.4;
  padding: 12rpx;
  background: #f5f7f6;
  border-radius: 12rpx;
  box-sizing: border-box;
}
.pass-actions {
  margin-top: 14rpx;
  display: flex;
  gap: 16rpx;
  width: 100%;
}
.btn-copy,
.btn-open {
  flex: 1;
  border: none !important;
  font-weight: 700;
}
.btn-copy {
  background: $lime !important;
  color: $ink !important;
}
.btn-open {
  background: #f0f3f2 !important;
  color: #3d4a38 !important;
}

.confirm-card {
  margin-top: 12rpx;
  background: #fff;
  border-radius: 20rpx;
  padding: 20rpx;
  border: 2rpx solid #dcefc0;
  box-sizing: border-box;
}
.preview { margin-bottom: 16rpx; }
.preview-row {
  display: flex;
  justify-content: space-between;
  gap: 16rpx;
  padding: 10rpx 0;
  font-size: 26rpx;
  border-bottom: 1rpx solid #f0f3f2;
}
.pl { color: #8a9693; flex-shrink: 0; }
.pv { color: $ink; text-align: right; flex: 1; min-width: 0; word-break: break-all; }
.confirm-btns { display: flex; justify-content: flex-end; gap: 16rpx; }
.btn-ok {
  background: $lime-deep !important;
  color: $ink !important;
  border: none;
  font-weight: 700;
}
.btn-cancel { background: #f0f3f2 !important; color: #5a6562 !important; border: none; }

.photo-card {
  margin-top: 12rpx;
  display: flex;
  gap: 16rpx;
  align-items: center;
}
.btn-photo {
  background: $lime-deep !important;
  color: $ink !important;
  border: none;
  font-weight: 700;
}
.btn-skip {
  background: #f0f3f2 !important;
  color: #5a6562 !important;
  border: none;
}

.composer {
  flex-shrink: 0;
  width: 100%;
  z-index: 10;
  padding: 12rpx 24rpx 16rpx;
  background: #ffffff;
  border-top: 1rpx solid #e6ece8;
  box-sizing: border-box;
}
.composer-inner {
  display: flex;
  align-items: center;
  gap: 16rpx;
  background: #f5f7f6;
  border-radius: 999rpx;
  padding: 8rpx 12rpx 8rpx 28rpx;
  min-height: 80rpx;
  box-sizing: border-box;
}
.input {
  flex: 1;
  min-width: 0;
  font-size: 28rpx;
  height: 72rpx;
  line-height: 72rpx;
  color: $ink;
  background: transparent;
}
.send-btn {
  min-width: 100rpx;
  height: 64rpx;
  padding: 0 24rpx;
  border-radius: 999rpx;
  background: #dce5e0;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.send-btn.on { background: $lime; }
.send-ico {
  font-size: 26rpx;
  font-weight: 700;
  color: #5a6562;
  line-height: 1;
}
.send-btn.on .send-ico { color: $ink; }

.scroll-pad { height: 24rpx; }
</style>

<style>
page,
uni-page-body {
  height: 100%;
  background: #f3f6f4;
}
</style>
